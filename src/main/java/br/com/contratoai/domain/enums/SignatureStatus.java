package br.com.contratoai.domain.enums;

public enum SignatureStatus {
    PENDING,   // Aguardando assinatura
    SIGNED,    // Assinado
    REJECTED,  // Rejeitado pelo signatário
    EXPIRED    // Link de assinatura expirado
}
