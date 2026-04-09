package br.com.contratoai.controller;

import br.com.contratoai.dto.DocumentRequestDTO;
import br.com.contratoai.dto.DocumentResponseDTO;
import br.com.contratoai.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/generate")
    public ResponseEntity<DocumentResponseDTO> generate(
        @Valid @RequestBody DocumentRequestDTO request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        DocumentResponseDTO response = documentService.generate(request, jwt);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponseDTO>> listDocuments(
        @AuthenticationPrincipal Jwt jwt,
        @PageableDefault(size = 10, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(documentService.listUserDocuments(jwt, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponseDTO> getDocument(
        @PathVariable UUID id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(documentService.getDocument(id, jwt));
    }
}
