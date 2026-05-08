package br.com.contratoai.repository;

import br.com.contratoai.domain.entity.Signature;
import br.com.contratoai.domain.enums.SignatureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SignatureRepository extends JpaRepository<Signature, UUID> {

    List<Signature> findByDocumentId(UUID documentId);

    Optional<Signature> findByIdAndDocumentId(UUID signatureId, UUID documentId);

    long countByDocumentIdAndStatus(UUID documentId, SignatureStatus status);

    boolean existsByDocumentIdAndSignerEmail(UUID documentId, String signerEmail);

    Optional<Signature> findByExternalEnvelopeId(String externalEnvelopeId);
}
