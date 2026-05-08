package br.com.contratoai.service;

import br.com.contratoai.domain.entity.Document;
import br.com.contratoai.domain.entity.Signature;
import br.com.contratoai.domain.entity.User;
import br.com.contratoai.domain.enums.DocumentStatus;
import br.com.contratoai.domain.enums.SignatureStatus;
import br.com.contratoai.dto.SendForSignatureRequestDTO;
import br.com.contratoai.dto.SignatureRequestDTO;
import br.com.contratoai.dto.SignatureResponseDTO;
import br.com.contratoai.exception.DocumentNotFoundException;
import br.com.contratoai.repository.DocumentRepository;
import br.com.contratoai.repository.SignatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignatureServiceTest {

    @Mock private SignatureRepository signatureRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private UserService userService;
    @Mock private AuditService auditService;

    private SignatureService signatureService;
    private Jwt jwt;
    private User user;
    private Document document;

    @BeforeEach
    void setUp() {
        signatureService = new SignatureService(signatureRepository, documentRepository, userService, auditService);

        jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        user = User.builder().id(UUID.randomUUID()).keycloakId("user-123").build();
        document = Document.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("Contrato de Teste")
                .userDescription("Teste")
                .status(DocumentStatus.DRAFT)
                .build();
    }

    @Test
    void sendForSignature_shouldCreateSignaturesAndUpdateDocumentStatus() {
        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(document.getId(), user.getId()))
                .thenReturn(Optional.of(document));
        when(signatureRepository.existsByDocumentIdAndSignerEmail(any(), any())).thenReturn(false);
        when(signatureRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var request = new SendForSignatureRequestDTO(List.of(
                new SignatureRequestDTO("joao@email.com", "João"),
                new SignatureRequestDTO("maria@email.com", "Maria")
        ));

        List<SignatureResponseDTO> result = signatureService.sendForSignature(document.getId(), request, jwt);

        assertThat(result).hasSize(2);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.SIGNING);
        verify(documentRepository).save(document);
        verify(signatureRepository).saveAll(anyList());
    }

    @Test
    void sendForSignature_shouldRejectWhenDocumentNotDraftOrFinalized() {
        document.setStatus(DocumentStatus.GENERATING);
        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(document.getId(), user.getId()))
                .thenReturn(Optional.of(document));

        var request = new SendForSignatureRequestDTO(List.of(
                new SignatureRequestDTO("joao@email.com", "João")
        ));

        assertThatThrownBy(() -> signatureService.sendForSignature(document.getId(), request, jwt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("não pode ser enviado para assinatura");
    }

    @Test
    void sendForSignature_shouldRejectDuplicateSigner() {
        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(document.getId(), user.getId()))
                .thenReturn(Optional.of(document));
        when(signatureRepository.existsByDocumentIdAndSignerEmail(document.getId(), "joao@email.com"))
                .thenReturn(true);

        var request = new SendForSignatureRequestDTO(List.of(
                new SignatureRequestDTO("joao@email.com", "João")
        ));

        assertThatThrownBy(() -> signatureService.sendForSignature(document.getId(), request, jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já foi adicionado");
    }

    @Test
    void sign_shouldUpdateSignatureStatusAndCheckDocumentCompletion() {
        document.setStatus(DocumentStatus.SIGNING);
        Signature signature = Signature.builder()
                .id(UUID.randomUUID())
                .document(document)
                .signerEmail("joao@email.com")
                .status(SignatureStatus.PENDING)
                .build();

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(document.getId(), user.getId()))
                .thenReturn(Optional.of(document));
        when(signatureRepository.findByIdAndDocumentId(signature.getId(), document.getId()))
                .thenReturn(Optional.of(signature));
        when(signatureRepository.findByDocumentId(document.getId()))
                .thenReturn(List.of(signature));
        when(documentRepository.findById(document.getId()))
                .thenReturn(Optional.of(document));

        SignatureResponseDTO result = signatureService.sign(document.getId(), signature.getId(), jwt);

        assertThat(result.status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(signature.getSignedAt()).isNotNull();
        // Single signer → all signed → document should be SIGNED
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.SIGNED);
    }

    @Test
    void sign_shouldRejectAlreadySignedSignature() {
        Signature signature = Signature.builder()
                .id(UUID.randomUUID())
                .document(document)
                .signerEmail("joao@email.com")
                .status(SignatureStatus.SIGNED)
                .build();

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(document.getId(), user.getId()))
                .thenReturn(Optional.of(document));
        when(signatureRepository.findByIdAndDocumentId(signature.getId(), document.getId()))
                .thenReturn(Optional.of(signature));

        assertThatThrownBy(() -> signatureService.sign(document.getId(), signature.getId(), jwt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("não pode ser assinada");
    }

    @Test
    void cancelSignature_shouldRemovePendingAndRevertDocumentToDraft() {
        document.setStatus(DocumentStatus.SIGNING);
        Signature sig = Signature.builder()
                .id(UUID.randomUUID())
                .document(document)
                .signerEmail("joao@email.com")
                .status(SignatureStatus.PENDING)
                .build();

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(document.getId(), user.getId()))
                .thenReturn(Optional.of(document));
        when(signatureRepository.countByDocumentIdAndStatus(document.getId(), SignatureStatus.SIGNED))
                .thenReturn(0L);
        when(signatureRepository.findByDocumentId(document.getId()))
                .thenReturn(List.of(sig));

        signatureService.cancelSignature(document.getId(), jwt);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        verify(signatureRepository).deleteAll(anyList());
    }

    @Test
    void cancelSignature_shouldRejectWhenAlreadySigned() {
        document.setStatus(DocumentStatus.SIGNING);

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(document.getId(), user.getId()))
                .thenReturn(Optional.of(document));
        when(signatureRepository.countByDocumentIdAndStatus(document.getId(), SignatureStatus.SIGNED))
                .thenReturn(1L);

        assertThatThrownBy(() -> signatureService.cancelSignature(document.getId(), jwt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("já foram completadas");
    }

    @Test
    void listSignatures_shouldReturnSignaturesForOwnedDocument() {
        Signature sig = Signature.builder()
                .id(UUID.randomUUID())
                .document(document)
                .signerEmail("joao@email.com")
                .signerName("João")
                .status(SignatureStatus.PENDING)
                .build();

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(document.getId(), user.getId()))
                .thenReturn(Optional.of(document));
        when(signatureRepository.findByDocumentId(document.getId()))
                .thenReturn(List.of(sig));

        List<SignatureResponseDTO> result = signatureService.listSignatures(document.getId(), jwt);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).signerEmail()).isEqualTo("joao@email.com");
    }

    @Test
    void sendForSignature_shouldRejectForNonOwnedDocument() {
        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(any(), eq(user.getId())))
                .thenReturn(Optional.empty());

        var request = new SendForSignatureRequestDTO(List.of(
                new SignatureRequestDTO("joao@email.com", "João")
        ));

        assertThatThrownBy(() -> signatureService.sendForSignature(UUID.randomUUID(), request, jwt))
                .isInstanceOf(DocumentNotFoundException.class);
    }
}
