package br.com.contratoai.repository;

import br.com.contratoai.domain.entity.AuditLog;
import br.com.contratoai.domain.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
