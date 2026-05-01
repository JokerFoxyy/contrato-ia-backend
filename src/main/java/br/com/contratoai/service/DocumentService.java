package br.com.contratoai.service;

import br.com.contratoai.domain.entity.Document;
import br.com.contratoai.domain.entity.Template;
import br.com.contratoai.domain.entity.User;
import br.com.contratoai.domain.enums.DocumentStatus;
import br.com.contratoai.dto.DocumentRequestDTO;
import br.com.contratoai.dto.DocumentResponseDTO;
import br.com.contratoai.exception.ClaudeApiException;
import br.com.contratoai.exception.DocumentNotFoundException;
import br.com.contratoai.exception.PlanLimitExceededException;
import br.com.contratoai.repository.DocumentRepository;
import br.com.contratoai.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final ClaudeService claudeService;
    private final UserService userService;

    @Transactional
    public DocumentResponseDTO generate(DocumentRequestDTO request, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);

        // Verifica limite do plano gratuito
        long docsThisMonth = documentRepository.countDocumentsSince(
            user.getId(),
            LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
        );

        if (!userService.canCreateDocument(user, docsThisMonth)) {
            throw new PlanLimitExceededException(
                "Limite de 3 documentos/mês do plano gratuito atingido. Faça upgrade para o plano Pro."
            );
        }

        // Busca template se informado
        Template template = null;
        if (request.templateId() != null) {
            template = templateRepository.findById(request.templateId()).orElse(null);
        }

        // Cria o documento em estado GENERATING
        Document document = Document.builder()
            .user(user)
            .template(template)
            .title(request.title() != null ? request.title() : generateTitle(request.description()))
            .userDescription(request.description())
            .status(DocumentStatus.GENERATING)
            .build();

        document = documentRepository.save(document);

        // Chama a Claude API para gerar o conteúdo
        try {
            String generatedContent = claudeService.generateDocument(request.description());
            document.setGeneratedContent(generatedContent);
            document.setStatus(DocumentStatus.DRAFT);
            document = documentRepository.save(document);
        } catch (Exception e) {
            log.error("Falha ao gerar documento via IA. userId={}, documentId={}, erro={}",
                user.getId(), document.getId(), e.getMessage(), e);
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            throw new ClaudeApiException("Erro ao gerar documento com IA: " + e.getMessage(), e);
        }

        return toResponseDTO(document);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponseDTO> listUserDocuments(Jwt jwt, Pageable pageable) {
        User user = userService.getOrCreateUser(jwt);
        return documentRepository
            .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
            .map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public DocumentResponseDTO getDocument(UUID documentId, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        Document doc = documentRepository.findByIdAndUserId(documentId, user.getId())
            .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado: " + documentId));
        return toResponseDTO(doc);
    }

    private String generateTitle(String description) {
        String trimmed = description.trim();
        if (trimmed.length() <= 50) return trimmed;
        return trimmed.substring(0, 50).trim() + "...";
    }

    private DocumentResponseDTO toResponseDTO(Document doc) {
        return new DocumentResponseDTO(
            doc.getId(),
            doc.getTitle(),
            doc.getGeneratedContent(),
            doc.getStatus(),
            doc.getPdfUrl(),
            doc.getDocxUrl(),
            doc.getCreatedAt(),
            doc.getUpdatedAt()
        );
    }
}
