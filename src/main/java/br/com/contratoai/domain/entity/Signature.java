package br.com.contratoai.domain.entity;

import br.com.contratoai.domain.enums.SignatureStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "signatures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    // Email do signatário (pode ser externo)
    @Column(name = "signer_email", nullable = false)
    private String signerEmail;

    @Column(name = "signer_name")
    private String signerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SignatureStatus status = SignatureStatus.PENDING;

    // ID do envelope na D4Sign ou ZapSign
    @Column(name = "external_envelope_id")
    private String externalEnvelopeId;

    // Link de assinatura para enviar ao signatário
    @Column(name = "signature_url")
    private String signatureUrl;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
