package br.com.contratoai.controller;

import br.com.contratoai.dto.UserDataExportDTO;
import br.com.contratoai.service.LgpdService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller para exercício dos direitos do titular de dados (LGPD).
 *
 * Endpoints:
 * - GET  /v1/user/data          → Exportação de dados (portabilidade, Art. 18 V)
 * - POST /v1/user/consent       → Registro de consentimento (Art. 7)
 * - DELETE /v1/user/data        → Exclusão de dados (Art. 18 VI)
 */
@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
public class LgpdController {

    private final LgpdService lgpdService;

    /**
     * Exporta todos os dados pessoais do titular em formato JSON.
     * Atende ao direito de portabilidade (LGPD Art. 18, V).
     */
    @GetMapping("/data")
    public ResponseEntity<UserDataExportDTO> exportData(@AuthenticationPrincipal Jwt jwt) {
        UserDataExportDTO export = lgpdService.exportUserData(jwt);
        return ResponseEntity.ok(export);
    }

    /**
     * Registra o consentimento do titular com a política de privacidade e termos de uso.
     * Atende ao Art. 7 da LGPD (base legal: consentimento).
     */
    @PostMapping("/consent")
    public ResponseEntity<Map<String, Object>> recordConsent(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody ConsentRequest request
    ) {
        lgpdService.recordConsent(jwt, request.privacyPolicy(), request.termsOfService());
        return ResponseEntity.ok(Map.of(
            "message", "Consentimento registrado com sucesso",
            "privacyPolicy", request.privacyPolicy(),
            "termsOfService", request.termsOfService()
        ));
    }

    /**
     * Solicita a exclusão e anonimização de todos os dados pessoais do titular.
     * Atende ao direito de eliminação (LGPD Art. 18, VI).
     *
     * Nota: Esta ação é IRREVERSÍVEL. Os dados pessoais são anonimizados
     * e o conteúdo dos documentos é removido permanentemente.
     */
    @DeleteMapping("/data")
    public ResponseEntity<Map<String, Object>> requestDeletion(@AuthenticationPrincipal Jwt jwt) {
        lgpdService.requestDataDeletion(jwt);
        return ResponseEntity.ok(Map.of(
            "message", "Seus dados pessoais foram anonimizados conforme solicitado (LGPD Art. 18, VI)",
            "status", "COMPLETED"
        ));
    }

    record ConsentRequest(boolean privacyPolicy, boolean termsOfService) {}
}
