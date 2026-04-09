package br.com.contratoai.domain.enums;

public enum DocumentStatus {
    GENERATING,  // IA gerando o documento
    DRAFT,       // Gerado, aguardando revisão
    FINALIZED,   // Finalizado pelo usuário
    SIGNING,     // Enviado para assinatura
    SIGNED,      // Assinado por todas as partes
    ARCHIVED     // Arquivado
}
