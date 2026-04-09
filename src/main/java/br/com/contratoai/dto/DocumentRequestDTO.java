package br.com.contratoai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DocumentRequestDTO(

    @NotBlank(message = "Descreva o que você precisa no contrato")
    @Size(min = 20, max = 2000, message = "Descrição deve ter entre 20 e 2000 caracteres")
    String description,

    // Opcional: título personalizado
    String title,

    // Opcional: ID do template a usar como base
    UUID templateId
) {}
