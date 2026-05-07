package br.com.contratoai.repository;

import br.com.contratoai.domain.entity.AuditLog;
import br.com.contratoai.domain.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** Busca auditoria por usuário (paginado, mais recente primeiro) */
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Busca auditoria por recurso específico */
    List<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, UUID resourceId);

    /** Busca auditoria por ação e período */
    List<AuditLog> findByActionAndCreatedAtBetween(AuditAction action, LocalDateTime start, LocalDateTime end);

    /**
     * Anonimiza IPs e PII nos audit logs de um usuário.
     * LGPD Art. 18, VI — direito à eliminação, mantendo a integridade do trail.
     */
    @Modifying
    @Query(value = "UPDATE audit_logs SET client_ip = '0.0.0.0', " +
           "details = COALESCE(details, '{}'::jsonb) - 'email' - 'signer_email' - 'name' " +
           "WHERE user_id = :userId", nativeQuery = true)
    int anonymizeByUserId(UUID userId);
}
