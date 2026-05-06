package br.com.contratoai.service;

import br.com.contratoai.domain.entity.Document;
import br.com.contratoai.domain.enums.AuditAction;
import br.com.contratoai.domain.enums.DocumentStatus;
import br.com.contratoai.dto.DocumentGenerationMessage;
import br.com.contratoai.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DocumentGenerationWorker {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final DocumentRepository documentRepository;
    private final ClaudeService claudeService;
    private final PdfGenerationService pdfGenerationService;
    private final DocxGenerationService docxGenerationService;
    private final S3StorageService s3StorageService;
    private final AuditService auditService;

    @Value("${aws.sqs.document-generation-queue-url}")
    private String queueUrl;

    public DocumentGenerationWorker(SqsClient sqsClient, ObjectMapper objectMapper,
                                     DocumentRepository documentRepository, ClaudeService claudeService,
                                     PdfGenerationService pdfGenerationService,
                                     DocxGenerationService docxGenerationService,
                                     S3StorageService s3StorageService,
                                     AuditService auditService) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.documentRepository = documentRepository;
        this.claudeService = claudeService;
        this.pdfGenerationService = pdfGenerationService;
        this.docxGenerationService = docxGenerationService;
        this.s3StorageService = s3StorageService;
        this.auditService = auditService;
    }

    /**
     * Faz polling da fila SQS a cada 2 segundos.
     * Processa cada mensagem: gera conteúdo via Claude, cria PDF/DOCX, faz upload S3.
     */
    @Scheduled(fixedDelay = 2000)
    public void pollQueue() {
        try {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(5)
                    .waitTimeSeconds(5) // Long polling
                    .visibilityTimeout(120) // 2 minutos para processar
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

            for (Message message : messages) {
                processMessage(message);
            }
        } catch (Exception e) {
            log.error("Erro no polling da fila SQS: {}", e.getMessage(), e);
        }
    }

    private void processMessage(Message message) {
        DocumentGenerationMessage genMessage = null;
        try {
            genMessage = objectMapper.readValue(message.body(), DocumentGenerationMessage.class);

            // Injeta contexto no MDC para correlacionar logs do worker
            MDC.put("documentId", genMessage.documentId().toString());
            MDC.put("userId", genMessage.userId().toString());
            MDC.put("correlationId", "worker-" + genMessage.documentId().toString().substring(0, 8));

            log.info("Processando geração assíncrona. documentId={}, userId={}",
                    genMessage.documentId(), genMessage.userId());

            final UUID documentId = genMessage.documentId();
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException(
                            "Documento não encontrado para geração: " + documentId));

            // Verifica se ainda está em GENERATING (evita reprocessamento)
            if (document.getStatus() != DocumentStatus.GENERATING) {
                log.warn("Documento {} não está em GENERATING (status={}). Ignorando.",
                        document.getId(), document.getStatus());
                deleteMessage(message);
                return;
            }

            auditService.logDocumentAction(AuditAction.DOCUMENT_GENERATION_STARTED,
                genMessage.userId(), documentId, Map.of("description", genMessage.description()));

            // Gera conteúdo via Claude
            String generatedContent = claudeService.generateDocument(genMessage.description());
            document.setGeneratedContent(generatedContent);
            document.setStatus(DocumentStatus.DRAFT);
            documentRepository.save(document);

            auditService.logDocumentAction(AuditAction.DOCUMENT_GENERATION_COMPLETED,
                genMessage.userId(), documentId,
                Map.of("contentLength", generatedContent.length()));

            // Gera e faz upload do PDF/DOCX
            try {
                byte[] pdfBytes = pdfGenerationService.generate(
                        generatedContent, document.getTitle(), document.getId());
                String pdfKey = s3StorageService.uploadDocument(
                        genMessage.userId(), document.getId(), pdfBytes, "application/pdf", "pdf");
                document.setPdfS3Key(pdfKey);
                document.setPdfUrl(s3StorageService.generatePresignedUrl(pdfKey).toString());

                byte[] docxBytes = docxGenerationService.generate(
                        generatedContent, document.getTitle(), document.getId());
                String docxKey = s3StorageService.uploadDocument(
                        genMessage.userId(), document.getId(), docxBytes,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
                document.setDocxS3Key(docxKey);
                document.setDocxUrl(s3StorageService.generatePresignedUrl(docxKey).toString());

                documentRepository.save(document);

                auditService.logDocumentAction(AuditAction.DOCUMENT_UPLOADED_S3,
                    genMessage.userId(), documentId,
                    Map.of("pdfKey", pdfKey, "docxKey", docxKey));

                log.info("Upload S3 concluído para documento {}", document.getId());
            } catch (Exception e) {
                log.warn("Falha no upload S3 para documento {}. Documento em DRAFT disponível. Erro: {}",
                        document.getId(), e.getMessage());
            }

            // Remove mensagem da fila após sucesso
            deleteMessage(message);
            log.info("Geração assíncrona concluída com sucesso. documentId={}", document.getId());

        } catch (Exception e) {
            String docId = genMessage != null ? genMessage.documentId().toString() : "desconhecido";
            log.error("Falha na geração assíncrona. documentId={}. Erro: {}", docId, e.getMessage(), e);

            // Marca documento como FAILED se possível
            if (genMessage != null) {
                try {
                    final UUID failedDocId = genMessage.documentId();
                    final UUID failedUserId = genMessage.userId();
                    documentRepository.findById(failedDocId).ifPresent(doc -> {
                        if (doc.getStatus() == DocumentStatus.GENERATING) {
                            doc.setStatus(DocumentStatus.FAILED);
                            documentRepository.save(doc);

                            auditService.logDocumentAction(AuditAction.DOCUMENT_GENERATION_FAILED,
                                failedUserId, failedDocId,
                                Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown"));
                        }
                    });
                } catch (Exception inner) {
                    log.error("Falha ao marcar documento como FAILED: {}", inner.getMessage());
                }
            }
            // Não deleta a mensagem — SQS vai tentar de novo (até ir para DLQ)
        } finally {
            MDC.clear();
        }
    }

    private void deleteMessage(Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
