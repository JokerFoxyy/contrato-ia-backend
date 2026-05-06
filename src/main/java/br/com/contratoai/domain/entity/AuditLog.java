package br.com.contratoai.domain.entity;

import br.com.contratoai.domain.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Registro de auditoria imutável.
 * Cada entrada registra uma ação relevante do sistema para compliance,
 * rastreabilidade e debugging. Nunca deve ser atualizado ou deletado.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_resource", columnList = "resource_type, resource_id"),
    @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Ação realizada */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    /** ID do usuário que realizou a ação (pode ser null para ações do sistema) */
    @Column(name = "user_id")
    private UUID userId;

    /** Tipo do recurso afetado (ex: "DOCUMENT", "USER") */
    @Column(name = "resource_type", length = 30)
    private String resourceType;

    /** ID do recurso afetado */
    @Column(name = "resource_id")
    private UUID resourceId;

    /** Detalhes adicionais em JSON (flexível para cada tipo de ação) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    /** IP do cliente que originou a ação */
    @Column(name = "client_ip", length = 45)
    private String clientIp;

    /** ID do request HTTP para correlação com logs */
    @Column(name = "request_id", length = 8)
    private String requestId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
