package br.com.contratoai.dto;

import br.com.contratoai.domain.enums.DocumentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponseDTO(
    UUID id,
    String title,
    String generatedContent,
    DocumentStatus status,
    String pdfUrl,
    String docxUrl,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
