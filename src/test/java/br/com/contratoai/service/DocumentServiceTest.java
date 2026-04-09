package br.com.contratoai.service;

import br.com.contratoai.domain.entity.Document;
import br.com.contratoai.domain.entity.Template;
import br.com.contratoai.domain.entity.User;
import br.com.contratoai.domain.enums.DocumentStatus;
import br.com.contratoai.domain.enums.Plan;
import br.com.contratoai.dto.DocumentRequestDTO;
import br.com.contratoai.dto.DocumentResponseDTO;
import br.com.contratoai.repository.DocumentRepository;
import br.com.contratoai.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private ClaudeService claudeService;

    @Mock
    private UserService userService;

    @InjectMocks
    private DocumentService documentService;

    private Jwt jwt;
    private User user;

    @BeforeEach
    void setUp() {
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
            .build();
    }

    @Test
    @DisplayName("generate - happy path should create document and return DTO")
    void generate_happyPath() {
        DocumentRequestDTO request = new DocumentRequestDTO(
            "Preciso de um contrato de prestacao de servicos de TI",
            "Contrato TI",
            null
        );

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.countDocumentsSince(eq(user.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(userService.canCreateDocument(user, 0L)).thenReturn(true);
        when(claudeService.generateDocument(request.description())).thenReturn("CONTRATO DE PRESTACAO DE SERVICOS...");

        UUID docId = UUID.randomUUID();
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(docId);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            return doc;
        });

        DocumentResponseDTO result = documentService.generate(request, jwt);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(docId);
        assertThat(result.title()).isEqualTo("Contrato TI");
        assertThat(result.generatedContent()).isEqualTo("CONTRATO DE PRESTACAO DE SERVICOS...");
        assertThat(result.status()).isEqualTo(DocumentStatus.DRAFT);
        verify(documentRepository, times(2)).save(any(Document.class));
    }

    @Test
    @DisplayName("generate - should auto-generate title when title is null")
    void generate_autoGenerateTitle() {
        DocumentRequestDTO request = new DocumentRequestDTO(
            "Preciso de um contrato de prestacao de servicos de TI para minha empresa de desenvolvimento",
            null,
            null
        );

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.countDocumentsSince(eq(user.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(userService.canCreateDocument(user, 0L)).thenReturn(true);
        when(claudeService.generateDocument(request.description())).thenReturn("Contrato gerado");

        UUID docId = UUID.randomUUID();
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(docId);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            return doc;
        });

        DocumentResponseDTO result = documentService.generate(request, jwt);

        assertThat(result).isNotNull();
        // Title should be truncated to 50 chars + "..."
        assertThat(result.title()).endsWith("...");
        assertThat(result.title().length()).isLessThanOrEqualTo(53);
    }

    @Test
    @DisplayName("generate - should auto-generate short title without truncation")
    void generate_autoGenerateShortTitle() {
        DocumentRequestDTO request = new DocumentRequestDTO(
            "Contrato de aluguel residencial",
            null,
            null
        );

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.countDocumentsSince(eq(user.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(userService.canCreateDocument(user, 0L)).thenReturn(true);
        when(claudeService.generateDocument(request.description())).thenReturn("Contrato gerado");

        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(UUID.randomUUID());
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            return doc;
        });

        DocumentResponseDTO result = documentService.generate(request, jwt);

        assertThat(result.title()).isEqualTo("Contrato de aluguel residencial");
    }

    @Test
    @DisplayName("generate - FREE plan limit exceeded should throw exception")
    void generate_freePlanLimitExceeded() {
        DocumentRequestDTO request = new DocumentRequestDTO(
            "Preciso de um contrato de prestacao de servicos",
            "Contrato",
            null
        );

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.countDocumentsSince(eq(user.getId()), any(LocalDateTime.class))).thenReturn(3L);
        when(userService.canCreateDocument(user, 3L)).thenReturn(false);

        assertThatThrownBy(() -> documentService.generate(request, jwt))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Limite de 3 documentos/mês do plano gratuito atingido");
    }

    @Test
    @DisplayName("generate - with template should associate template to document")
    void generate_withTemplate() {
        UUID templateId = UUID.randomUUID();
        DocumentRequestDTO request = new DocumentRequestDTO(
            "Contrato de prestacao de servicos com template",
            "Contrato Servicos",
            templateId
        );

        Template template = Template.builder()
            .id(templateId)
            .name("Prestacao de Servicos")
            .category("SERVICOS")
            .systemPrompt("Voce e um especialista...")
            .build();

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.countDocumentsSince(eq(user.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(userService.canCreateDocument(user, 0L)).thenReturn(true);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(claudeService.generateDocument(request.description())).thenReturn("Contrato com template");

        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(UUID.randomUUID());
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());
            return doc;
        });

        DocumentResponseDTO result = documentService.generate(request, jwt);

        assertThat(result).isNotNull();
        verify(templateRepository).findById(templateId);
    }

    @Test
    @DisplayName("generate - Claude API failure should throw with wrapped message")
    void generate_claudeApiFailure() {
        DocumentRequestDTO request = new DocumentRequestDTO(
            "Preciso de um contrato de prestacao de servicos",
            "Contrato",
            null
        );

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.countDocumentsSince(eq(user.getId()), any(LocalDateTime.class))).thenReturn(0L);
        when(userService.canCreateDocument(user, 0L)).thenReturn(true);
        when(claudeService.generateDocument(anyString())).thenThrow(new RuntimeException("API timeout"));

        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(UUID.randomUUID());
            return doc;
        });

        assertThatThrownBy(() -> documentService.generate(request, jwt))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Erro ao gerar documento com IA");
    }

    @Test
    @DisplayName("listUserDocuments - should return paginated documents for user")
    void listUserDocuments_success() {
        Pageable pageable = PageRequest.of(0, 10);

        Document doc = Document.builder()
            .id(UUID.randomUUID())
            .user(user)
            .title("Contrato Teste")
            .userDescription("descricao")
            .generatedContent("conteudo gerado")
            .status(DocumentStatus.DRAFT)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        Page<Document> page = new PageImpl<>(List.of(doc), pageable, 1);

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)).thenReturn(page);

        Page<DocumentResponseDTO> result = documentService.listUserDocuments(jwt, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Contrato Teste");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("listUserDocuments - should return empty page when no documents")
    void listUserDocuments_empty() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Document> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)).thenReturn(emptyPage);

        Page<DocumentResponseDTO> result = documentService.listUserDocuments(jwt, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("getDocument - should return document when found for user")
    void getDocument_found() {
        UUID docId = UUID.randomUUID();
        Document doc = Document.builder()
            .id(docId)
            .user(user)
            .title("Contrato Encontrado")
            .userDescription("descricao")
            .generatedContent("conteudo")
            .status(DocumentStatus.DRAFT)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(docId, user.getId())).thenReturn(Optional.of(doc));

        DocumentResponseDTO result = documentService.getDocument(docId, jwt);

        assertThat(result.id()).isEqualTo(docId);
        assertThat(result.title()).isEqualTo("Contrato Encontrado");
        assertThat(result.status()).isEqualTo(DocumentStatus.DRAFT);
    }

    @Test
    @DisplayName("getDocument - should throw when document not found")
    void getDocument_notFound() {
        UUID docId = UUID.randomUUID();

        when(userService.getOrCreateUser(jwt)).thenReturn(user);
        when(documentRepository.findByIdAndUserId(docId, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDocument(docId, jwt))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Documento não encontrado");
    }
}
