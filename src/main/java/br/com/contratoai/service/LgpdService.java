package br.com.contratoai.service;

import br.com.contratoai.domain.entity.Document;
import br.com.contratoai.domain.entity.User;
import br.com.contratoai.domain.enums.AuditAction;
import br.com.contratoai.dto.UserDataExportDTO;
import br.com.contratoai.repository.AuditLogRepository;
import br.com.contratoai.repository.DocumentRepository;
import br.com.contratoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serviço que implementa os direitos do titular de dados conforme a LGPD.
 *
 * Direitos atendidos:
 * - Art. 18, V: Portabilidade (exportação de dados)
 * - Art. 18, VI: Eliminação (anonimização de dados pessoais)
 * - Art. 7: Consentimento (registro de aceite)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LgpdService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final UserService userService;
    private final S3StorageService s3StorageService;

    /**
     * Exporta todos os dados pessoais do titular em formato estruturado (JSON).
     * Atende ao direito de portabilidade (LGPD Art. 18, V).
     */
    @Transactional(readOnly = true)
    public UserDataExportDTO exportUserData(Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);

        // Coleta todos os documentos do usuário (sem paginação — exportação total)
        List<UserDataExportDTO.DocumentExport> documentExports = new ArrayList<>();
        int page = 0;
        Page<Document> docPage;
        do {
            docPage = documentRepository.findByUserIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(page, 100));
            for (Document doc : docPage.getContent()) {
                documentExports.add(new UserDataExportDTO.DocumentExport(
                    doc.getId(),
                    doc.getTitle(),
                    doc.getUserDescription(),
                    doc.getGeneratedContent(),
                    doc.getStatus(),
                    doc.getCreatedAt(),
                    doc.getUpdatedAt()
                ));
            }
            page++;
        } while (docPage.hasNext());

        // Monta o DTO de exportação
        UserDataExportDTO export = new UserDataExportDTO(
            new UserDataExportDTO.PersonalData(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPlan(),
                user.getPrivacyConsentAt(),
                user.getTermsAcceptedAt(),
                user.getCreatedAt()
            ),
            documentExports,
            new UserDataExportDTO.ExportMetadata(
                LocalDateTime.now(),
                "JSON",
                "1.0"
            )
        );

        auditService.logUserAction(AuditAction.USER_DATA_EXPORT_REQUESTED, user.getId(),
            Map.of("documentsExported", documentExports.size()));

        log.info("Exportação de dados LGPD realizada. userId={}, docs={}", user.getId(), documentExports.size());

        return export;
    }

    /**
     * Solicita a exclusão da conta e anonimização dos dados pessoais.
     * Atende ao direito de eliminação (LGPD Art. 18, VI).
     *
     * O processo:
     * 1. Marca a conta como "deletion requested"
     * 2. Anonimiza dados pessoais (nome, email) no banco
     * 3. Remove conteúdo dos documentos (mantém metadados para auditoria)
     * 4. Faz soft delete dos arquivos no S3
     * 5. Anonimiza o IP nos audit logs do usuário
     *
     * Nota: O registro de auditoria é mantido com dados anonimizados
     * para cumprir obrigações legais de retenção (LGPD Art. 16, I).
     */
    @Transactional
    public void requestDataDeletion(Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);

        if (user.isDeleted()) {
            log.warn("Exclusão já realizada para userId={}", user.getId());
            return;
        }

        UUID userId = user.getId();

        // 1. Marca a solicitação
        user.setDeletionRequestedAt(LocalDateTime.now());

        // 2. Anonimiza dados pessoais
        user.setName("USUÁRIO REMOVIDO");
        user.setEmail("deleted-" + userId + "@anonimizado.local");
        user.setKeycloakId("deleted-" + userId);
        user.setStripeCustomerId(null);
        user.setStripeSubscriptionId(null);
        user.setDataDeletedAt(LocalDateTime.now());

        userRepository.save(user);

        // 3. Remove conteúdo sensível dos documentos (mantém id, status, timestamps)
        Page<Document> docPage;
        int page = 0;
        int docsAnonymized = 0;
        do {
            docPage = documentRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, 100));
            for (Document doc : docPage.getContent()) {
                // Soft delete no S3 (se existir)
                if (doc.getPdfS3Key() != null) {
                    try {
                        s3StorageService.softDelete(doc.getPdfS3Key());
                    } catch (Exception e) {
                        log.warn("Falha ao soft-delete S3 PDF: key={}", doc.getPdfS3Key());
                    }
                }
                if (doc.getDocxS3Key() != null) {
                    try {
                        s3StorageService.softDelete(doc.getDocxS3Key());
                    } catch (Exception e) {
                        log.warn("Falha ao soft-delete S3 DOCX: key={}", doc.getDocxS3Key());
                    }
                }

                // Anonimiza conteúdo
                doc.setUserDescription("[DADOS REMOVIDOS POR SOLICITAÇÃO DO TITULAR]");
                doc.setGeneratedContent(null);
                doc.setTitle("Documento removido");
                doc.setPdfS3Key(null);
                doc.setDocxS3Key(null);
                doc.setPdfUrl(null);
                doc.setDocxUrl(null);
                doc.setContentHash(null);
                documentRepository.save(doc);
                docsAnonymized++;
            }
            page++;
        } while (docPage.hasNext());

        // 4. Anonimiza IPs nos audit logs do usuário
        anonymizeAuditLogs(userId);

        // 5. Registra a ação (com dados já anonimizados)
        auditService.logUserAction(AuditAction.USER_DATA_ANONYMIZED, userId,
            Map.of("documentsAnonymized", docsAnonymized, "reason", "USER_REQUEST"));

        log.info("Dados pessoais anonimizados. userId={}, docs={}", userId, docsAnonymized);
    }

    /**
     * Registra o consentimento do titular com a política de privacidade e termos.
     * Atende ao Art. 7 da LGPD (base legal: consentimento).
     */
    @Transactional
    public void recordConsent(Jwt jwt, boolean privacyPolicy, boolean termsOfService) {
        User user = userService.getOrCreateUser(jwt);
        LocalDateTime now = LocalDateTime.now();

        if (privacyPolicy) {
            user.setPrivacyConsentAt(now);
        }
        if (termsOfService) {
            user.setTermsAcceptedAt(now);
        }

        userRepository.save(user);

        auditService.logUserAction(AuditAction.USER_CONSENT_RECORDED, user.getId(),
            Map.of("privacyPolicy", privacyPolicy, "termsOfService", termsOfService));

        log.info("Consentimento registrado. userId={}, privacy={}, terms={}",
            user.getId(), privacyPolicy, termsOfService);
    }

    /**
     * Anonimiza os IPs e PII nos audit logs do usuário via query nativa.
     * Mantém o registro de auditoria mas remove dados que identificam o titular.
     */
    private void anonymizeAuditLogs(UUID userId) {
        int updated = auditLogRepository.anonymizeByUserId(userId);
        log.info("Audit logs anonimizados. userId={}, registros={}", userId, updated);
    }
}
