package br.com.contratoai.domain.enums;

/**
 * Ações rastreáveis no audit log.
 * Cada ação representa um evento relevante para compliance e rastreabilidade.
 */
public enum AuditAction {

    // Usuário
    USER_CREATED,
    USER_PLAN_CHANGED,

    // Documento — ciclo de vida
    DOCUMENT_GENERATION_REQUESTED,
    DOCUMENT_GENERATION_STARTED,
    DOCUMENT_GENERATION_COMPLETED,
    DOCUMENT_GENERATION_FAILED,
    DOCUMENT_FINALIZED,
    DOCUMENT_DELETED,

    // Documento — exports
    DOCUMENT_EXPORTED_PDF,
    DOCUMENT_EXPORTED_DOCX,

    // Documento — S3
    DOCUMENT_UPLOADED_S3,
    DOCUMENT_SOFT_DELETED_S3,

    // Plano e limites
    PLAN_LIMIT_REACHED,

    // Autenticação
    AUTH_ACCESS_DENIED,

    // LGPD — Direitos do titular
    USER_DATA_EXPORT_REQUESTED,
    USER_DELETION_REQUESTED,
    USER_DATA_ANONYMIZED,
    USER_CONSENT_RECORDED
}
