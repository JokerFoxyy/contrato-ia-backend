package br.com.contratoai.domain.entity;

import br.com.contratoai.domain.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private Template template;

    @Column(nullable = false)
    private String title;

    // Descrição livre que o usuário digitou para gerar o documento
    @Column(name = "user_description", columnDefinition = "TEXT", nullable = false)
    private String userDescription;

    // Conteúdo gerado pela IA (Markdown/texto)
    @Column(name = "generated_content", columnDefinition = "TEXT")
    private String generatedContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.GENERATING;

    // URL do arquivo PDF no Cloudflare R2
    @Column(name = "pdf_url")
    private String pdfUrl;

    // URL do arquivo DOCX no Cloudflare R2
    @Column(name = "docx_url")
    private String docxUrl;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Signature> signatures = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
