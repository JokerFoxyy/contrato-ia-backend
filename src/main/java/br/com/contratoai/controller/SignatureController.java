package br.com.contratoai.controller;

import br.com.contratoai.dto.SendForSignatureRequestDTO;
import br.com.contratoai.dto.SignatureResponseDTO;
import br.com.contratoai.service.SignatureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/documents/{documentId}/signatures")
@RequiredArgsConstructor
public class SignatureController {

    private final SignatureService signatureService;

    /**
     * Envia documento para assinatura com lista de signatários.
     * POST /v1/documents/{id}/signatures
     */
    @PostMapping
    public ResponseEntity<List<SignatureResponseDTO>> sendForSignature(
            @PathVariable UUID documentId,
            @Valid @RequestBody SendForSignatureRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        List<SignatureResponseDTO> signatures = signatureService.sendForSignature(documentId, request, jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(signatures);
    }

    /**
     * Lista assinaturas de um documento.
     * GET /v1/documents/{id}/signatures
     */
    @GetMapping
    public ResponseEntity<List<SignatureResponseDTO>> listSignatures(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(signatureService.listSignatures(documentId, jwt));
    }

    /**
     * Registra assinatura de um signatário.
     * POST /v1/documents/{id}/signatures/{signatureId}/sign
     */
    @PostMapping("/{signatureId}/sign")
    public ResponseEntity<SignatureResponseDTO> sign(
            @PathVariable UUID documentId,
            @PathVariable UUID signatureId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(signatureService.sign(documentId, signatureId, jwt));
    }

    /**
     * Cancela envio para assinatura (remove pendentes, volta para DRAFT).
     * DELETE /v1/documents/{id}/signatures
     */
    @DeleteMapping
    public ResponseEntity<Void> cancelSignature(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal Jwt jwt) {
        signatureService.cancelSignature(documentId, jwt);
        return ResponseEntity.noContent().build();
    }
}
