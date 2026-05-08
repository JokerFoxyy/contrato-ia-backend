package br.com.contratoai.service;

import br.com.contratoai.domain.entity.Document;
import br.com.contratoai.domain.entity.Signature;
import br.com.contratoai.domain.entity.User;
import br.com.contratoai.domain.enums.AuditAction;
import br.com.contratoai.domain.enums.DocumentStatus;
import br.com.contratoai.domain.enums.SignatureStatus;
import br.com.contratoai.dto.SendForSignatureRequestDTO;
import br.com.contratoai.dto.SignatureRequestDTO;
import br.com.contratoai.dto.SignatureResponseDTO;
import br.com.contratoai.exception.DocumentNotFoundException;
import br.com.contratoai.repository.DocumentRepository;
import br.com.contratoai.repository.SignatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureService {

    private static final Set<DocumentStatus> SIGNABLE_STATUSES = Set.of(
            DocumentStatus.DRAFT, DocumentStatus.FINALIZED
    );

    private final SignatureRepository signatureRepository;
    private final DocumentRepository documentRepository;
    private final UserService userService;
    private final AuditService auditService;

    /**
     * Envia documento para assinatura — cria registros de Signature para cada signatário.
     * O documento muda para status SIGNING.
     */
    @Transactional
    public List<SignatureResponseDTO> sendForSignature(UUID documentId, SendForSignatureRequestDTO request, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        Document document = getOwnedDocument(documentId, user.getId());

        // Valida que o documento pode ser enviado para assinatura
        if (!SIGNABLE_STATUSES.contains(document.getStatus())) {
            throw new IllegalStateException(
                    "Documento com status " + document.getStatus() + " não pode ser enviado para assinatura. " +
                    "Apenas documentos em DRAFT ou FINALIZED podem ser assinados.");
        }

        // Cria as assinaturas
        List<Signature> signatures = request.signers().stream()
                .map(signerReq -> createSignature(document, signerReq))
                .toList();

        signatureRepository.saveAll(signatures);

        // Atualiza status do documento
        document.setStatus(DocumentStatus.SIGNING);
        documentRepository.save(document);

        auditService.logDocumentAction(AuditAction.DOCUMENT_SENT_FOR_SIGNATURE, user.getId(), documentId,
                Map.of("signersCount", signatures.size(),
                       "signerEmails", signatures.stream().map(Signature::getSignerEmail).toList()));

        log.info("Documento {} enviado para assinatura com {} signatários. userId={}",
                documentId, signatures.size(), user.getId());

        return signatures.stream().map(this::toResponseDTO).toList();
    }

    /**
     * Lista todas as assinaturas de um documento.
     */
    @Transactional(readOnly = true)
    public List<SignatureResponseDTO> listSignatures(UUID documentId, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        getOwnedDocument(documentId, user.getId()); // Valida acesso
        return signatureRepository.findByDocumentId(documentId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    /**
     * Registra a assinatura de um signatário (simulado — em produção viria via webhook).
     * Quando todos assinam, o documento muda para SIGNED.
     */
    @Transactional
    public SignatureResponseDTO sign(UUID documentId, UUID signatureId, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        getOwnedDocument(documentId, user.getId()); // Valida acesso

        Signature signature = signatureRepository.findByIdAndDocumentId(signatureId, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(
                        "Assinatura não encontrada: " + signatureId));

        if (signature.getStatus() != SignatureStatus.PENDING) {
            throw new IllegalStateException(
                    "Assinatura com status " + signature.getStatus() + " não pode ser assinada.");
        }

        signature.setStatus(SignatureStatus.SIGNED);
        signature.setSignedAt(LocalDateTime.now());
        signatureRepository.save(signature);

        auditService.logDocumentAction(AuditAction.SIGNATURE_COMPLETED, user.getId(), documentId,
                Map.of("signatureId", signatureId, "signerEmail", signature.getSignerEmail()));

        log.info("Assinatura {} completada para documento {}. signerEmail={}",
                signatureId, documentId, signature.getSignerEmail());

        // Verifica se todas as assinaturas foram completadas
        checkAndUpdateDocumentStatus(documentId);

        return toResponseDTO(signature);
    }

    /**
     * Cancela o envio para assinatura — remove assinaturas pendentes e volta o documento para DRAFT.
     */
    @Transactional
    public void cancelSignature(UUID documentId, Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        Document document = getOwnedDocument(documentId, user.getId());

        if (document.getStatus() != DocumentStatus.SIGNING) {
            throw new IllegalStateException(
                    "Documento não está em processo de assinatura.");
        }

        // Verifica se alguma assinatura já foi completada
        long signedCount = signatureRepository.countByDocumentIdAndStatus(documentId, SignatureStatus.SIGNED);
        if (signedCount > 0) {
            throw new IllegalStateException(
                    "Não é possível cancelar — " + signedCount + " assinatura(s) já foram completadas.");
        }

        // Remove assinaturas pendentes
        List<Signature> pendingSignatures = signatureRepository.findByDocumentId(documentId);
        signatureRepository.deleteAll(pendingSignatures);

        // Volta status do documento
        document.setStatus(DocumentStatus.DRAFT);
        documentRepository.save(document);

        auditService.logDocumentAction(AuditAction.SIGNATURE_CANCELLED, user.getId(), documentId,
                Map.of("removedSignatures", pendingSignatures.size()));

        log.info("Assinaturas canceladas para documento {}. userId={}", documentId, user.getId());
    }

    /**
     * Verifica se todas as assinaturas de um documento foram completadas.
     * Se sim, atualiza o documento para SIGNED.
     */
    private void checkAndUpdateDocumentStatus(UUID documentId) {
        List<Signature> signatures = signatureRepository.findByDocumentId(documentId);

        boolean allSigned = signatures.stream()
                .allMatch(s -> s.getStatus() == SignatureStatus.SIGNED);

        if (allSigned && !signatures.isEmpty()) {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado: " + documentId));
            document.setStatus(DocumentStatus.SIGNED);
            documentRepository.save(document);

            log.info("Todas as assinaturas completadas — documento {} atualizado para SIGNED", documentId);
        }
    }

    private Signature createSignature(Document document, SignatureRequestDTO signerReq) {
        // Verifica duplicata
        if (signatureRepository.existsByDocumentIdAndSignerEmail(document.getId(), signerReq.signerEmail())) {
            throw new IllegalArgumentException(
                    "Signatário " + signerReq.signerEmail() + " já foi adicionado a este documento.");
        }

        return Signature.builder()
                .document(document)
                .signerEmail(signerReq.signerEmail())
                .signerName(signerReq.signerName())
                .status(SignatureStatus.PENDING)
                .build();
    }

    private Document getOwnedDocument(UUID documentId, UUID userId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException("Documento não encontrado: " + documentId));
    }

    private SignatureResponseDTO toResponseDTO(Signature sig) {
        return new SignatureResponseDTO(
                sig.getId(),
                sig.getDocument().getId(),
                sig.getSignerEmail(),
                sig.getSignerName(),
                sig.getStatus(),
                sig.getSignatureUrl(),
                sig.getSignedAt(),
                sig.getCreatedAt()
        );
    }
}
