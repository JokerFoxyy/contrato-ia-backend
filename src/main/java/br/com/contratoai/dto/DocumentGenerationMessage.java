package br.com.contratoai.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem publicada na fila SQS para geração assíncrona de documentos.
 */
public record DocumentGenerationMessage(
    UUID documentId,
    UUID userId,
    String description,
    UUID templateId,
    Instant timestamp
) {
    public static DocumentGenerationMessage of(UUID documentId, UUID userId,
                                                String description, UUID templateId) {
        return new DocumentGenerationMessage(documentId, userId, description, templateId, Instant.now());
    }
}
