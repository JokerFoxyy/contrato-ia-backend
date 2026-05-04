package br.com.contratoai.service;

import br.com.contratoai.dto.DocumentGenerationMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
@Service
public class DocumentQueuePublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.document-generation-queue-url}")
    private String queueUrl;

    public DocumentQueuePublisher(SqsClient sqsClient, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Publica uma mensagem na fila SQS para geração assíncrona do documento.
     */
    public void publishGenerationRequest(DocumentGenerationMessage message) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageGroupId(message.userId().toString())
                    .build();

            sqsClient.sendMessage(request);

            log.info("Mensagem de geração publicada na fila. documentId={}, userId={}",
                    message.documentId(), message.userId());

        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar mensagem de geração. documentId={}", message.documentId(), e);
            throw new RuntimeException("Erro ao publicar na fila de geração: " + e.getMessage(), e);
        }
    }
}
