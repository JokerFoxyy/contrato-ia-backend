package br.com.contratoai.dto;

import br.com.contratoai.domain.enums.DocumentStatus;
import br.com.contratoai.domain.enums.Plan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO para exportação de todos os dados pessoais do titular.
 * Atende ao direito de portabilidade (LGPD Art. 18, V).
 */
public record UserDataExportDTO(
    // Dados pessoais
    PersonalData personalData,
    // Documentos gerados
    List<DocumentExport> documents,
    // Metadados da exportação
    ExportMetadata metadata
) {

    public record PersonalData(
        UUID id,
        String name,
        String email,
        Plan plan,
        LocalDateTime privacyConsentAt,
        LocalDateTime termsAcceptedAt,
        LocalDateTime createdAt
    ) {}

    public record DocumentExport(
        UUID id,
        String title,
        String userDescription,
        String generatedContent,
        DocumentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    public record ExportMetadata(
        LocalDateTime exportedAt,
        String format,
        String version
    ) {}
}
