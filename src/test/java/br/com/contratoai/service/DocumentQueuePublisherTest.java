package br.com.contratoai.service;

import br.com.contratoai.dto.DocumentGenerationMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentQueuePublisherTest {

    @Mock
    private SqsClient sqsClient;

    private ObjectMapper objectMapper = new ObjectMapper();

    private DocumentQueuePublisher publisher;

    private static final String QUEUE_URL = "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/contrato-ia-generation.fifo";

    @BeforeEach
    void setUp() throws Exception {
        objectMapper.findAndRegisterModules();
        publisher = new DocumentQueuePublisher(sqsClient, objectMapper);
        // Injeta o queueUrl via reflection (é @Value no código real)
        Field queueUrlField = DocumentQueuePublisher.class.getDeclaredField("queueUrl");
        queueUrlField.setAccessible(true);
        queueUrlField.set(publisher, QUEUE_URL);
    }

    @Test
    @DisplayName("publishGenerationRequest - should send message to SQS with correct parameters")
    void publishGenerationRequest_success() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DocumentGenerationMessage message = DocumentGenerationMessage.of(docId, userId, "Contrato de servicos", null);

        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
            .thenReturn(SendMessageResponse.builder().messageId("msg-123").build());

        publisher.publishGenerationRequest(message);

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());

        SendMessageRequest sentRequest = captor.getValue();
        assertThat(sentRequest.queueUrl()).isEqualTo(QUEUE_URL);
        assertThat(sentRequest.messageGroupId()).isEqualTo(userId.toString());
        assertThat(sentRequest.messageBody()).contains(docId.toString());
        assertThat(sentRequest.messageBody()).contains(userId.toString());
        assertThat(sentRequest.messageBody()).contains("Contrato de servicos");
    }

    @Test
    @DisplayName("publishGenerationRequest - should include templateId in message when provided")
    void publishGenerationRequest_withTemplate() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        DocumentGenerationMessage message = DocumentGenerationMessage.of(docId, userId, "Contrato", templateId);

        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
            .thenReturn(SendMessageResponse.builder().messageId("msg-456").build());

        publisher.publishGenerationRequest(message);

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());
        assertThat(captor.getValue().messageBody()).contains(templateId.toString());
    }

    @Test
    @DisplayName("publishGenerationRequest - should throw when SQS fails")
    void publishGenerationRequest_sqsFailure() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DocumentGenerationMessage message = DocumentGenerationMessage.of(docId, userId, "Contrato", null);

        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
            .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> publisher.publishGenerationRequest(message))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("publishGenerationRequest - should throw wrapped exception on serialization failure")
    void publishGenerationRequest_serializationFailure() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DocumentGenerationMessage message = DocumentGenerationMessage.of(docId, userId, "Contrato", null);

        // Usa um ObjectMapper mockado que falha na serialização
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        when(brokenMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization error") {});

        DocumentQueuePublisher brokenPublisher = new DocumentQueuePublisher(sqsClient, brokenMapper);
        Field queueUrlField = DocumentQueuePublisher.class.getDeclaredField("queueUrl");
        queueUrlField.setAccessible(true);
        queueUrlField.set(brokenPublisher, QUEUE_URL);

        assertThatThrownBy(() -> brokenPublisher.publishGenerationRequest(message))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Erro ao publicar na fila de geração");
    }
}
