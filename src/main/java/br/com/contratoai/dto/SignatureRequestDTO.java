package br.com.contratoai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignatureRequestDTO(
    @NotBlank(message = "Email do signatário é obrigatório")
    @Email(message = "Email inválido")
    String signerEmail,

    String signerName
) {}
