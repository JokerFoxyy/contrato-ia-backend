package br.com.contratoai.service;

import br.com.contratoai.domain.entity.AuditLog;
import br.com.contratoai.domain.enums.AuditAction;
import br.com.contratoai.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    @DisplayName("log - should save audit entry with all fields")
    void log_savesAuditEntry() {
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        Map<String, Object> details = Map.of("title", "Contrato TI", "hasTemplate", false);

        MDC.put("clientIp", "192.168.1.1");
        MDC.put("requestId", "abc12345");

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        auditService.log(AuditAction.DOCUMENT_GENERATION_REQUESTED, userId,
            "DOCUMENT", resourceId, details);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo(AuditAction.DOCUMENT_GENERATION_REQUESTED);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getResourceType()).isEqualTo("DOCUMENT");
        assertThat(saved.getResourceId()).isEqualTo(resourceId);
        assertThat(saved.getDetails()).containsEntry("title", "Contrato TI");
        assertThat(saved.getClientIp()).isEqualTo("192.168.1.1");
        assertThat(saved.getRequestId()).isEqualTo("abc12345");

        MDC.clear();
    }

    @Test
    @DisplayName("log - should not throw when repository fails")
    void log_repositoryFailure_doesNotThrow() {
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        // Não deve lançar exceção — audit nunca derruba o request principal
        auditService.log(AuditAction.USER_CREATED, UUID.randomUUID(),
            "USER", UUID.randomUUID(), Map.of());

        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("logDocumentAction - should use DOCUMENT as resource type")
    void logDocumentAction_setsResourceType() {
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        auditService.logDocumentAction(AuditAction.DOCUMENT_EXPORTED_PDF, userId, docId,
            Map.of("status", "DRAFT"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getResourceType()).isEqualTo("DOCUMENT");
        assertThat(captor.getValue().getResourceId()).isEqualTo(docId);
    }

    @Test
    @DisplayName("logUserAction - should use USER as resource type with userId as resourceId")
    void logUserAction_setsUserAsResource() {
        UUID userId = UUID.randomUUID();

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        auditService.logUserAction(AuditAction.USER_CREATED, userId,
            Map.of("email", "test@test.com"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getResourceType()).isEqualTo("USER");
        assertThat(captor.getValue().getResourceId()).isEqualTo(userId);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("log - should handle null MDC values gracefully")
    void log_nullMdcValues() {
        MDC.clear();

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        auditService.log(AuditAction.DOCUMENT_GENERATION_STARTED, UUID.randomUUID(),
            "DOCUMENT", UUID.randomUUID(), Map.of());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getClientIp()).isNull();
        assertThat(captor.getValue().getRequestId()).isNull();
    }
}
