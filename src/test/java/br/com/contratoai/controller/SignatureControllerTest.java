package br.com.contratoai.controller;

import br.com.contratoai.config.RateLimitFilter;
import br.com.contratoai.config.RequestLoggingFilter;
import br.com.contratoai.domain.enums.SignatureStatus;
import br.com.contratoai.dto.SignatureResponseDTO;
import br.com.contratoai.service.SignatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = SignatureController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {RateLimitFilter.class, RequestLoggingFilter.class}
    ))
@AutoConfigureMockMvc(addFilters = false)
class SignatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SignatureService signatureService;

    private SignatureResponseDTO samplePendingSignature(UUID documentId) {
        return new SignatureResponseDTO(
            UUID.randomUUID(),
            documentId,
            "signatario@email.com",
            "Signatario Teste",
            SignatureStatus.PENDING,
            null,
            null,
            LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("POST /v1/documents/{id}/signatures - should return 201 Created")
    void sendForSignature_shouldReturn201_whenValidRequest() throws Exception {
        UUID documentId = UUID.randomUUID();
        SignatureResponseDTO response = samplePendingSignature(documentId);
        when(signatureService.sendForSignature(eq(documentId), any(), any()))
            .thenReturn(List.of(response));

        Map<String, Object> requestBody = Map.of(
            "signers", List.of(
                Map.of("email", "signatario@email.com", "name", "Signatario Teste")
            )
        );

        mockMvc.perform(post("/v1/documents/{id}/signatures", documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].signerEmail").value("signatario@email.com"))
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /v1/documents/{id}/signatures - should return signatures list")
    void listSignatures_shouldReturnList_whenDocumentExists() throws Exception {
        UUID documentId = UUID.randomUUID();
        SignatureResponseDTO response = samplePendingSignature(documentId);
        when(signatureService.listSignatures(eq(documentId), any()))
            .thenReturn(List.of(response));

        mockMvc.perform(get("/v1/documents/{id}/signatures", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /v1/documents/{id}/signatures - should return empty list when no signatures")
    void listSignatures_shouldReturnEmptyList_whenNoSignatures() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(signatureService.listSignatures(eq(documentId), any()))
            .thenReturn(List.of());

        mockMvc.perform(get("/v1/documents/{id}/signatures", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("POST /v1/documents/{id}/signatures/{signatureId}/sign - should return signed")
    void sign_shouldReturnSigned_whenValidSignature() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID signatureId = UUID.randomUUID();
        SignatureResponseDTO response = new SignatureResponseDTO(
            signatureId,
            documentId,
            "signatario@email.com",
            "Signatario Teste",
            SignatureStatus.SIGNED,
            "https://signature-url.com",
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        when(signatureService.sign(eq(documentId), eq(signatureId), any()))
            .thenReturn(response);

        mockMvc.perform(post("/v1/documents/{id}/signatures/{signatureId}/sign",
                documentId, signatureId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SIGNED"))
            .andExpect(jsonPath("$.signedAt").exists());
    }

    @Test
    @DisplayName("DELETE /v1/documents/{id}/signatures - should return 204 No Content")
    void cancelSignature_shouldReturn204_whenCancelled() throws Exception {
        UUID documentId = UUID.randomUUID();
        doNothing().when(signatureService).cancelSignature(eq(documentId), any());

        mockMvc.perform(delete("/v1/documents/{id}/signatures", documentId))
            .andExpect(status().isNoContent());

        verify(signatureService).cancelSignature(eq(documentId), any());
    }
}
