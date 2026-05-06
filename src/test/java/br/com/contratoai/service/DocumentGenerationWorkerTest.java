package br.com.contratoai.service;

import br.com.contratoai.domain.entity.Document;
import br.com.contratoai.domain.entity.User;
import br.com.contratoai.domain.enums.DocumentStatus;
import br.com.contratoai.domain.enums.Plan;
import br.com.contratoai.dto.DocumentGenerationMessage;
import br.com.contratoai.exception.ClaudeApiException;
import br.com.contratoai.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.lang.reflect.Field;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentGenerationWorkerTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ClaudeService claudeService;

    @Mock
    private PdfGenerationService pdfGenerationService;

    @Mock
    private DocxGenerationService docxGenerationService;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private AuditService auditService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private DocumentGenerationWorker worker;

    private static final String QUEUE_URL = "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/contrato-ia-generation.fifo";

    @BeforeEach
    void setUp() throws Exception {
        objectMapper.findAndRegisterModules();
        worker = new DocumentGenerationWorker(
            sqsClient, objectMapper, documentRepository, claudeService,
            pdfGenerationService, docxGenerationService, s3StorageService, auditService
        );
        Field queueUrlField = DocumentGenerationWorker.class.getDeclaredField("queueUrl");
        queueUrlField.setAccessible(true);
        queueUrlField.set(worker, QUEUE_URL);
    }

    private Message buildSqsMessage(DocumentGenerationMessage genMessage) throws Exception {
        return Message.builder()
            .body(objectMapper.writeValueAsString(genMessage))
            .receiptHandle("receipt-123")
            .build();
    }

    @Test
    @DisplayName("pollQueue - happy path: generates content, uploads to S3, deletes message")
    void pollQueue_happyPath() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DocumentGenerationMessage genMessage = DocumentGenerationMessage.of(docId, userId, "Contrato de servicos", null);

        User user = User.builder().id(userId).keycloakId("kc-123").email("test@test.com").name("Test").plan(Plan.FREE).build();
        Document document = Document.builder()
            .id(docId).user(user).title("Contrato").userDescription("Contrato de servicos")
            .status(DocumentStatus.GENERATING).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();

        // Mock SQS receive
        ReceiveMessageResponse receiveResponse = ReceiveMessageResponse.builder()
            .messages(buildSqsMessage(genMessage))
            .build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResponse);

        // Mock document lookup
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        // Mock Claude
        when(claudeService.generateDocument("Contrato de servicos")).thenReturn("CONTRATO GERADO...");

        // Mock S3 uploads
        when(s3StorageService.uploadDocument(eq(userId), eq(docId), any(), eq("application/pdf"), eq("pdf")))
            .thenReturn("documents/" + userId + "/" + docId + "/pdf.pdf");
        when(s3StorageService.uploadDocument(eq(userId), eq(docId), any(), contains("wordprocessingml"), eq("docx")))
            .thenReturn("documents/" + userId + "/" + docId + "/docx.docx");
        when(s3StorageService.generatePresignedUrl(anyString()))
            .thenReturn(new URL("https://s3.amazonaws.com/presigned"));

        // Mock PDF/DOCX generation
        when(pdfGenerationService.generate(anyString(), anyString(), any(UUID.class)))
            .thenReturn(new byte[]{37, 80, 68, 70});
        when(docxGenerationService.generate(anyString(), anyString(), any(UUID.class)))
            .thenReturn(new byte[]{80, 75, 3, 4});

        worker.pollQueue();

        // Verifica que o conteúdo foi gerado
        verify(claudeService).generateDocument("Contrato de servicos");

        // Verifica que o documento foi salvo com status DRAFT e depois com S3 keys
        verify(documentRepository, atLeast(2)).save(argThat(doc -> {
            // Em pelo menos um save, o status deve ser DRAFT
            return doc.getStatus() == DocumentStatus.DRAFT;
        }));

        // Verifica que a mensagem foi deletada da fila
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("pollQueue - empty queue should do nothing")
    void pollQueue_emptyQueue() {
        ReceiveMessageResponse emptyResponse = ReceiveMessageResponse.builder()
            .messages(List.of())
            .build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(emptyResponse);

        worker.pollQueue();

        verifyNoInteractions(documentRepository);
        verifyNoInteractions(claudeService);
    }

    @Test
    @DisplayName("pollQueue - document not in GENERATING status should skip and delete message")
    void pollQueue_documentNotGenerating() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DocumentGenerationMessage genMessage = DocumentGenerationMessage.of(docId, userId, "Contrato", null);

        Document document = Document.builder()
            .id(docId).title("Contrato").userDescription("desc")
            .status(DocumentStatus.DRAFT) // Já processado
            .build();

        ReceiveMessageResponse receiveResponse = ReceiveMessageResponse.builder()
            .messages(buildSqsMessage(genMessage))
            .build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResponse);
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        worker.pollQueue();

        // Não deve chamar Claude
        verifyNoInteractions(claudeService);
        // Deve deletar a mensagem (já processada)
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("pollQueue - Claude API failure should set FAILED status and NOT delete message")
    void pollQueue_claudeFailure() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DocumentGenerationMessage genMessage = DocumentGenerationMessage.of(docId, userId, "Contrato", null);

        Document document = Document.builder()
            .id(docId).title("Contrato").userDescription("desc")
            .status(DocumentStatus.GENERATING)
            .build();

        ReceiveMessageResponse receiveResponse = ReceiveMessageResponse.builder()
            .messages(buildSqsMessage(genMessage))
            .build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResponse);
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(claudeService.generateDocument(anyString())).thenThrow(new ClaudeApiException("API timeout"));

        worker.pollQueue();

        // Deve marcar como FAILED
        verify(documentRepository).save(argThat(doc -> doc.getStatus() == DocumentStatus.FAILED));

        // NÃO deve deletar a mensagem (SQS vai retentar, depois vai para DLQ)
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("pollQueue - S3 upload failure should keep DRAFT status and still delete message")
    void pollQueue_s3UploadFailure() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DocumentGenerationMessage genMessage = DocumentGenerationMessage.of(docId, userId, "Contrato", null);

        Document document = Document.builder()
            .id(docId).title("Contrato").userDescription("desc")
            .status(DocumentStatus.GENERATING)
            .build();

        ReceiveMessageResponse receiveResponse = ReceiveMessageResponse.builder()
            .messages(buildSqsMessage(genMessage))
            .build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResponse);
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(claudeService.generateDocument(anyString())).thenReturn("CONTRATO GERADO");
        when(pdfGenerationService.generate(anyString(), anyString(), any(UUID.class)))
            .thenThrow(new RuntimeException("S3 upload failed"));

        worker.pollQueue();

        // Documento deve ter sido salvo como DRAFT (antes do upload)
        verify(documentRepository).save(argThat(doc -> doc.getStatus() == DocumentStatus.DRAFT));

        // Mensagem deve ser deletada (geração teve sucesso, só o upload falhou)
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("pollQueue - uses long polling with correct parameters")
    void pollQueue_correctSqsParameters() {
        ReceiveMessageResponse emptyResponse = ReceiveMessageResponse.builder()
            .messages(List.of())
            .build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(emptyResponse);

        worker.pollQueue();

        ArgumentCaptor<ReceiveMessageRequest> captor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsClient).receiveMessage(captor.capture());

        ReceiveMessageRequest request = captor.getValue();
        assertThat(request.queueUrl()).isEqualTo(QUEUE_URL);
        assertThat(request.maxNumberOfMessages()).isEqualTo(5);
        assertThat(request.waitTimeSeconds()).isEqualTo(5);
        assertThat(request.visibilityTimeout()).isEqualTo(120);
    }
}
