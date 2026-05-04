package br.com.contratoai.dto;

import br.com.contratoai.domain.enums.DocumentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO leve retornado pelo endpoint de polling de status.
 */
public record DocumentStatusDTO(
    UUID id,
    DocumentStatus status,
    String pdfUrl,
    String docxUrl,
    LocalDateTime updatedAt
) {}
