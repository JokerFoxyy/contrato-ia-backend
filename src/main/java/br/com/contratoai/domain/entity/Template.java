package br.com.contratoai.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Categoria: SERVICOS, TRABALHO, LOCACAO, NDA, PARCERIA, etc.
    @Column(nullable = false)
    private String category;

    // Prompt base que será enviado à Claude API
    @Column(name = "system_prompt", columnDefinition = "TEXT", nullable = false)
    private String systemPrompt;

    // Campos variáveis do template em formato JSON
    // Ex: [{"field": "valor_contrato", "label": "Valor do contrato", "type": "currency"}]
    @Column(name = "fields_schema", columnDefinition = "TEXT")
    private String fieldsSchema;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    // Apenas plano PRO e acima
    @Column(name = "requires_pro")
    @Builder.Default
    private boolean requiresPro = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
