package br.com.contratoai.service;

import br.com.contratoai.config.InputSanitizer;
import br.com.contratoai.domain.entity.Document;
import br.com.contratoai.domain.entity.Template;
import br.com.contratoai.domain.entity.User;
import br.com.contratoai.domain.enums.AuditAction;
import br.com.contratoai.domain.enums.DocumentStatus;
import br.com.contratoai.dto.DocumentGenerationMessage;
import br.com.contratoai.dto.DocumentRequestDTO;
import br.com.contratoai.dto.DocumentResponseDTO;
import br.com.contratoai.dto.DocumentStatusDTO;
import br.com.contratoai.exception.DocumentNotFoundException;
import br.com.contratoai.exception.PlanLimitExceededException;
import br.com.contratoai.repository.DocumentRepository;
import br.com.contratoai.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<DocumentStatus> EXPORTABLE_STATUSES = Set.of(
            DocumentStatus.DRAFT, DocumentStatus.FINALIZED, DocumentStatus.SIGNING,
            DocumentStatus.SIGNED, DocumentStatus.ARCHIVED
    );

    private final DocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final ClaudeService claudeService;
    private final UserService userService;
    private final PdfGenerationService pdfGenerationService;
    private final DocxGenerationService docxGenerationService;
    private final S3StorageService s3StorageService;
    private final DocumentQueuePublisher documentQueuePublisher;
    private final AuditService auditService;
    private final InputSanitizer inputSanitizer;
    private final ContentIntegrityService contentIntegrityService;

    /**
     * Cria um documento em estado GENERATING e publica na fila SQS.
     * O processamento real (Claude API + PDF/DOCX + S3) acontece no worker.
     * Retorna imediatamente para o frontend fazer polling via GET /{id}/status.
     */
    @Transactional
    public DocumentResponseDTO generate(DocumentRequestDTO request, Jwt jwt) {
        // Sanitiza e valida input contra prompt injection
        String sanitizedDescription = inputSanitizer.validateAndSanitize(request.description());
        String sanitizedTitle = request.title() != null ? inputSanitizer.sanitize(request.title()) : null;

        User user = userService.getOrCreateUser(jwt);

        // Verifica limite do plano gratuito
        long docsThisMonth = documentRepository.countDocumentsSince(
            user.getId(),
            LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
        );

        if (!userService.canCreateDocument(user, docsThisMonth)) {
            auditService.logDocumentAction(AuditAction.PLAN_LIMIT_REACHED, user.getId(), null,
                Map.of("plan", user.getPlan().name(), "docsThisMonth", docsThisMonth));
            throw new PlanLimitExceededException(
                "Limite de 3 documentos/mês do plano gratuito atingido. Faça upgrade para o plano Pro."
            );
        }

        // Busca template se informado
        Template template = null;
        UUID templateId = null;
        if (request.templateId() != null) {
            template = templateRepository.findById(request.templateId()).orElse(null);
            templateId = request.templateId();
        }

        // Cria o documento em estado GENERATING (usa input sanitizado)
        Document document = Document.builder()
            .user(user)
            .template(template)
            .title(sanitizedTitle != null ? sanitizedTitle : generateTitle(sanitizedDescription))
            .userDescription(sanitizedDescription)
            .status(DocumentStatus.GENERATING)
            .build();

        document = documentRepository.save(document);

        // Publica na fila SQS para processamento assíncrono (usa input sanitizado)
        DocumentGenerationMessage message = DocumentGenerationMessage.of(
            document.getId(), user.getId(), sanitizedDescription, templateId
        );
        documentQueuePublisher.publishGenerationRequest(message);

        auditService.logDocumentAction(AuditAction.DOCUMENT_GENERATION_REQUESTED, user.getId(),
            document.getId(), Map.of("title", document.getTitle(),
                "hasTemplate", request.templateId() != null));

        log.info("Documento {} enfileirado para geração. userId={}", document.getId(), user.getId());
        return toResponseDTO(document);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponseDTO> listUserDocuments(Jwt jwt, Pageable pageable) {
        User user = userService.getOrCreateUser(jwt);
        return documentRepository
            .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
            .map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public DocumentResponseDTO getDocument(UUID documentId, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        Document doc = documentRepository.findByIdAndUserId(documentId, user.getId())
            .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado: " + documentId));
        return toResponseDTO(doc);
    }

    /**
     * Gera o PDF do documento.
     * Se já existe no S3, gera nova presigned URL. Caso contrário, gera on-the-fly.
     */
    @Transactional(readOnly = true)
    public byte[] exportPdf(UUID documentId, Jwt jwt) {
        Document doc = getExportableDocument(documentId, jwt);
        auditService.logDocumentAction(AuditAction.DOCUMENT_EXPORTED_PDF,
            doc.getUser().getId(), documentId, Map.of("status", doc.getStatus().name()));
        return pdfGenerationService.generate(doc.getGeneratedContent(), doc.getTitle(), doc.getId());
    }

    /**
     * Gera o DOCX do documento.
     */
    @Transactional(readOnly = true)
    public byte[] exportDocx(UUID documentId, Jwt jwt) {
        Document doc = getExportableDocument(documentId, jwt);
        auditService.logDocumentAction(AuditAction.DOCUMENT_EXPORTED_DOCX,
            doc.getUser().getId(), documentId, Map.of("status", doc.getStatus().name()));
        return docxGenerationService.generate(doc.getGeneratedContent(), doc.getTitle(), doc.getId());
    }

    /**
     * Retorna presigned URL do PDF no S3 (se existir), null caso contrário.
     */
    @Transactional(readOnly = true)
    public String getPdfPresignedUrl(UUID documentId, Jwt jwt) {
        Document doc = getExportableDocument(documentId, jwt);
        if (doc.getPdfS3Key() != null) {
            return s3StorageService.generatePresignedUrl(doc.getPdfS3Key()).toString();
        }
        return null;
    }

    /**
     * Retorna presigned URL do DOCX no S3 (se existir), null caso contrário.
     */
    @Transactional(readOnly = true)
    public String getDocxPresignedUrl(UUID documentId, Jwt jwt) {
        Document doc = getExportableDocument(documentId, jwt);
        if (doc.getDocxS3Key() != null) {
            return s3StorageService.generatePresignedUrl(doc.getDocxS3Key()).toString();
        }
        return null;
    }

    /**
     * Retorna o status atual do documento (endpoint leve para polling).
     */
    @Transactional(readOnly = true)
    public DocumentStatusDTO getDocumentStatus(UUID documentId, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        Document doc = documentRepository.findByIdAndUserId(documentId, user.getId())
            .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado: " + documentId));
        return new DocumentStatusDTO(doc.getId(), doc.getStatus(), doc.getPdfUrl(), doc.getDocxUrl(), doc.getUpdatedAt());
    }

    /**
     * Gera PDF e DOCX, faz upload para S3 e salva as keys no documento.
     * Se o upload falhar, o documento permanece em DRAFT sem arquivos no S3
     * (o usuário ainda pode exportar sob demanda).
     */
    private void uploadDocumentFiles(Document document, UUID userId) {
        try {
            // Gera PDF
            byte[] pdfBytes = pdfGenerationService.generate(
                    document.getGeneratedContent(), document.getTitle(), document.getId());
            String pdfKey = s3StorageService.uploadDocument(
                    userId, document.getId(), pdfBytes, "application/pdf", "pdf");
            document.setPdfS3Key(pdfKey);
            document.setPdfUrl(s3StorageService.generatePresignedUrl(pdfKey).toString());

            // Gera DOCX
            byte[] docxBytes = docxGenerationService.generate(
                    document.getGeneratedContent(), document.getTitle(), document.getId());
            String docxKey = s3StorageService.uploadDocument(
                    userId, document.getId(), docxBytes,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
            document.setDocxS3Key(docxKey);
            document.setDocxUrl(s3StorageService.generatePresignedUrl(docxKey).toString());

            log.info("Upload PDF/DOCX concluído para documento {}", document.getId());

        } catch (Exception e) {
            // Upload falhou mas o documento já está em DRAFT — não bloqueia o fluxo
            log.warn("Falha no upload S3 para documento {}. Export sob demanda continua disponível. Erro: {}",
                    document.getId(), e.getMessage(), e);
        }
    }

    private Document getExportableDocument(UUID documentId, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        Document doc = documentRepository.findByIdAndUserId(documentId, user.getId())
                .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado: " + documentId));

        if (!EXPORTABLE_STATUSES.contains(doc.getStatus())) {
            throw new IllegalStateException(
                    "Documento com status " + doc.getStatus() + " não pode ser exportado. Aguarde a geração ser concluída.");
        }

        if (doc.getGeneratedContent() == null || doc.getGeneratedContent().isBlank()) {
            throw new IllegalStateException("Documento sem conteúdo gerado para exportação.");
        }

        // Verifica integridade do conteúdo (se hash existir)
        if (doc.getContentHash() != null) {
            if (!contentIntegrityService.verifyIntegrity(doc.getGeneratedContent(), doc.getContentHash())) {
                log.error("Falha na verificação de integridade do documento {}!", documentId);
                auditService.logDocumentAction(AuditAction.DOCUMENT_EXPORTED_PDF,
                    user.getId(), documentId,
                    Map.of("integrityCheckFailed", true, "action", "export_blocked"));
                throw new IllegalStateException(
                    "Falha na verificação de integridade do documento. O conteúdo pode ter sido alterado.");
            }
        }

        return doc;
    }

    private String generateTitle(String description) {
        String trimmed = description.trim();
        if (trimmed.length() <= 50) return trimmed;
        return trimmed.substring(0, 50).trim() + "...";
    }

    private DocumentResponseDTO toResponseDTO(Document doc) {
        return new DocumentResponseDTO(
            doc.getId(),
            doc.getTitle(),
            doc.getGeneratedContent(),
            doc.getStatus(),
            doc.getPdfUrl(),
            doc.getDocxUrl(),
            doc.getCreatedAt(),
            doc.getUpdatedAt()
        );
    }
}
