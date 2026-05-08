package br.com.contratoai.dto;

import br.com.contratoai.domain.enums.SignatureStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SignatureResponseDTO(
    UUID id,
    UUID documentId,
    String signerEmail,
    String signerName,
    SignatureStatus status,
    String signatureUrl,
    LocalDateTime signedAt,
    LocalDateTime createdAt
) {}
