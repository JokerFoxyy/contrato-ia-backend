package br.com.contratoai.service;

import br.com.contratoai.domain.entity.Document;
import br.com.contratoai.domain.entity.User;
import br.com.contratoai.domain.enums.DocumentStatus;
import br.com.contratoai.domain.enums.Plan;
import br.com.contratoai.dto.UserDataExportDTO;
import br.com.contratoai.repository.AuditLogRepository;
import br.com.contratoai.repository.DocumentRepository;
import br.com.contratoai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LgpdServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditService auditService;
    @Mock private UserService userService;
    @Mock private S3StorageService s3StorageService;

    private LgpdService lgpdService;

    private Jwt jwt;
    private User user;

    @BeforeEach
    void setUp() {
        lgpdService = new LgpdService(
            userRepository, documentRepository, auditLogRepository,
            auditService, userService, s3StorageService
        );

        jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .subject("kc-123")
            .claim("email", "victor@email.com")
            .claim("name", "Victor")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

        user = User.builder()
            .id(UUID.randomUUID())
            .keycloakId("kc-123")
            .email("victor@email.com")
            .name("Victor")
            .plan(Plan.FREE)
            .privacyConsentAt(LocalDateTime.now().minusDays(30))
            .termsAcceptedAt(LocalDateTime.now().minusDays(30))
            .createdAt(LocalDateTime.now().minusDays(60))
            .build();
    }

    // === exportUserData ===

    @Test
    @DisplayName("exportUserData - should return complete user data with documents")
    void exportUserData_withDocuments() {
        Document doc = Document.builder()
            .id(UUID.randomUUID())
            .user(user)
            .title("Contrato de Servicos")
            .userDescription("Preciso de um contrato de TI")
            .generatedContent("CONTRATO DE PRESTACAO...")
            .status(DocumentStatus.DRAFT)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByUserIdOrderByCreatedAtDesc(eq(user.getId()), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(doc)));

        UserDataExportDTO result = lgpdService.exportUserData(jwt);

        assertThat(result.personalData().name()).isEqualTo("Victor");
        assertThat(result.personalData().email()).isEqualTo("victor@email.com");
        assertThat(result.personalData().plan()).isEqualTo(Plan.FREE);
        assertThat(result.documents()).hasSize(1);
        assertThat(result.documents().get(0).title()).isEqualTo("Contrato de Servicos");
        assertThat(result.documents().get(0).generatedContent()).isEqualTo("CONTRATO DE PRESTACAO...");
        assertThat(result.metadata().format()).isEqualTo("JSON");
        assertThat(result.metadata().version()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("exportUserData - should return empty documents list when user has none")
    void exportUserData_noDocuments() {
        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByUserIdOrderByCreatedAtDesc(eq(user.getId()), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        UserDataExportDTO result = lgpdService.exportUserData(jwt);

        assertThat(result.personalData().email()).isEqualTo("victor@email.com");
        assertThat(result.documents()).isEmpty();
    }

    // === requestDataDeletion ===

    @Test
    @DisplayName("requestDataDeletion - should anonymize user and documents")
    void requestDataDeletion_success() {
        Document doc = Document.builder()
            .id(UUID.randomUUID())
            .user(user)
            .title("Contrato")
            .userDescription("descricao")
            .generatedContent("conteudo")
            .status(DocumentStatus.DRAFT)
            .pdfS3Key("docs/pdf.pdf")
            .docxS3Key("docs/docx.docx")
            .build();

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByUserIdOrderByCreatedAtDesc(eq(user.getId()), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(doc)));
        when(auditLogRepository.anonymizeByUserId(user.getId())).thenReturn(5);

        lgpdService.requestDataDeletion(jwt);

        // Verifica que o user foi anonimizado
        verify(userRepository).save(argThat(u -> {
            assertThat(u.getName()).isEqualTo("USUÁRIO REMOVIDO");
            assertThat(u.getEmail()).contains("anonimizado.local");
            assertThat(u.getStripeCustomerId()).isNull();
            assertThat(u.getDataDeletedAt()).isNotNull();
            assertThat(u.getDeletionRequestedAt()).isNotNull();
            return true;
        }));

        // Verifica que o documento foi anonimizado
        verify(documentRepository).save(argThat(d -> {
            assertThat(d.getGeneratedContent()).isNull();
            assertThat(d.getTitle()).isEqualTo("Documento removido");
            assertThat(d.getUserDescription()).contains("DADOS REMOVIDOS");
            assertThat(d.getPdfS3Key()).isNull();
            assertThat(d.getDocxS3Key()).isNull();
            return true;
        }));

        // Verifica soft delete no S3
        verify(s3StorageService).softDelete("docs/pdf.pdf");
        verify(s3StorageService).softDelete("docs/docx.docx");

        // Verifica anonimização de audit logs
        verify(auditLogRepository).anonymizeByUserId(user.getId());
    }

    @Test
    @DisplayName("requestDataDeletion - should skip if already deleted")
    void requestDataDeletion_alreadyDeleted() {
        user.setDataDeletedAt(LocalDateTime.now().minusDays(1));
        when(userService.getOrCreateUser(jwt)).thenReturn(user);

        lgpdService.requestDataDeletion(jwt);

        // Não deve fazer nada
        verify(userRepository, never()).save(any());
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestDataDeletion - S3 failure should not block anonymization")
    void requestDataDeletion_s3Failure() {
        Document doc = Document.builder()
            .id(UUID.randomUUID())
            .user(user)
            .title("Contrato")
            .userDescription("desc")
            .generatedContent("conteudo")
            .status(DocumentStatus.DRAFT)
            .pdfS3Key("docs/pdf.pdf")
            .build();

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByUserIdOrderByCreatedAtDesc(eq(user.getId()), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(doc)));
        doThrow(new RuntimeException("S3 error")).when(s3StorageService).softDelete(anyString());
        when(auditLogRepository.anonymizeByUserId(user.getId())).thenReturn(0);

        // Não deve lançar exceção
        lgpdService.requestDataDeletion(jwt);

        // Documento ainda deve ser anonimizado no banco
        verify(documentRepository).save(argThat(d -> d.getGeneratedContent() == null));
        verify(userRepository).save(argThat(u -> u.getDataDeletedAt() != null));
    }

    // === recordConsent ===

    @Test
    @DisplayName("recordConsent - should record privacy and terms consent")
    void recordConsent_both() {
        when(userService.getOrCreateUser(jwt)).thenReturn(user);

        lgpdService.recordConsent(jwt, true, true);

        verify(userRepository).save(argThat(u -> {
            assertThat(u.getPrivacyConsentAt()).isNotNull();
            assertThat(u.getTermsAcceptedAt()).isNotNull();
            return true;
        }));
    }

    @Test
    @DisplayName("recordConsent - should record only privacy consent")
    void recordConsent_privacyOnly() {
        user.setPrivacyConsentAt(null);
        user.setTermsAcceptedAt(null);
        when(userService.getOrCreateUser(jwt)).thenReturn(user);

        lgpdService.recordConsent(jwt, true, false);

        verify(userRepository).save(argThat(u -> {
            assertThat(u.getPrivacyConsentAt()).isNotNull();
            // termsAcceptedAt mantém null pois não foi consentido
            return true;
        }));
    }
}
