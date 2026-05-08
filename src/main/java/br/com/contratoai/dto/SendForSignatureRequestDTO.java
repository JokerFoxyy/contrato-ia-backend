package br.com.contratoai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SendForSignatureRequestDTO(
    @NotEmpty(message = "Pelo menos um signatário é obrigatório")
    @Valid
    List<SignatureRequestDTO> signers
) {}
