package br.com.contratoai.service;

import br.com.contratoai.domain.entity.AuditLog;
import br.com.contratoai.domain.enums.AuditAction;
import br.com.contratoai.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Serviço de auditoria.
 * Registra ações relevantes do sistema de forma assíncrona
 * para não impactar a performance do request principal.
 *
 * Automaticamente captura requestId e clientIp do MDC
 * (injetados pelo RequestLoggingFilter).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Registra um evento de auditoria.
     *
     * @param action       Ação realizada
     * @param userId       ID do usuário (pode ser null para ações do sistema)
     * @param resourceType Tipo do recurso ("DOCUMENT", "USER", etc.)
     * @param resourceId   ID do recurso afetado
     * @param details      Detalhes adicionais em Map (será JSON no DB)
     */
    @Async
    public void log(AuditAction action, UUID userId, String resourceType,
                    UUID resourceId, Map<String, Object> details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .action(action)
                .userId(userId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details)
                .clientIp(MDC.get("clientIp"))
                .requestId(MDC.get("requestId"))
                .build();

            auditLogRepository.save(auditLog);

            log.debug("Audit: {} userId={} resourceType={} resourceId={}",
                action, userId, resourceType, resourceId);

        } catch (Exception e) {
            // Audit log NUNCA deve derrubar o request principal
            log.error("Falha ao registrar audit log: action={}, userId={}, error={}",
                action, userId, e.getMessage(), e);
        }
    }

    /** Atalho para ações sobre documentos */
    public void logDocumentAction(AuditAction action, UUID userId, UUID documentId,
                                   Map<String, Object> details) {
        log(action, userId, "DOCUMENT", documentId, details);
    }

    /** Atalho para ações sobre usuários */
    public void logUserAction(AuditAction action, UUID userId, Map<String, Object> details) {
        log(action, userId, "USER", userId, details);
    }
}
