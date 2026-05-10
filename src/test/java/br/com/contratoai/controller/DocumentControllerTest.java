package br.com.contratoai.controller;

import br.com.contratoai.config.RateLimitFilter;
import br.com.contratoai.config.RequestLoggingFilter;
import br.com.contratoai.domain.enums.DocumentStatus;
import br.com.contratoai.dto.DocumentResponseDTO;
import br.com.contratoai.exception.DocumentNotFoundException;
import br.com.contratoai.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = DocumentController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {RateLimitFilter.class, RequestLoggingFilter.class}
    ))
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentService documentService;

    private DocumentResponseDTO sampleGeneratingResponse() {
        return new DocumentResponseDTO(
            UUID.randomUUID(),
            "Contrato de Servicos",
            null, // Conteúdo ainda não gerado — está na fila
            DocumentStatus.GENERATING,
            null,
            null,
            LocalDateTime.of(2025, 6, 15, 10, 30),
            LocalDateTime.of(2025, 6, 15, 10, 30)
        );
    }

    private DocumentResponseDTO sampleDraftResponse() {
        return new DocumentResponseDTO(
            UUID.randomUUID(),
            "Contrato de Servicos",
            "CONTRATO DE PRESTACAO DE SERVICOS...",
            DocumentStatus.DRAFT,
            null,
            null,
            LocalDateTime.of(2025, 6, 15, 10, 30),
            LocalDateTime.of(2025, 6, 15, 10, 30)
        );
    }

    @Test
    @DisplayName("POST /v1/documents/generate - should return 202 Accepted with GENERATING status")
    void generate_validRequest() throws Exception {
        DocumentResponseDTO response = sampleGeneratingResponse();
        when(documentService.generate(any(), any())).thenReturn(response);

        Map<String, Object> requestBody = Map.of(
            "description", "Preciso de um contrato de prestacao de servicos de TI para minha empresa"
        );

        mockMvc.perform(post("/v1/documents/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.title").value("Contrato de Servicos"))
            .andExpect(jsonPath("$.generatedContent").doesNotExist())
            .andExpect(jsonPath("$.status").value("GENERATING"));
    }

    @Test
    @DisplayName("POST /v1/documents/generate - should return 400 for too short description")
    void generate_tooShortDescription() throws Exception {
        Map<String, Object> requestBody = Map.of(
            "description", "Contrato curto"
        );

        mockMvc.perform(post("/v1/documents/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /v1/documents/generate - should return 400 for blank description")
    void generate_blankDescription() throws Exception {
        Map<String, Object> requestBody = Map.of(
            "description", ""
        );

        mockMvc.perform(post("/v1/documents/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /v1/documents/generate - should return 400 for missing description")
    void generate_missingDescription() throws Exception {
        mockMvc.perform(post("/v1/documents/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /v1/documents/generate - should accept request with title and templateId")
    void generate_withOptionalFields() throws Exception {
        DocumentResponseDTO response = sampleGeneratingResponse();
        when(documentService.generate(any(), any())).thenReturn(response);

        UUID templateId = UUID.randomUUID();
        Map<String, Object> requestBody = Map.of(
            "description", "Preciso de um contrato de prestacao de servicos de TI completo",
            "title", "Meu Contrato Personalizado",
            "templateId", templateId.toString()
        );

        mockMvc.perform(post("/v1/documents/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.title").value("Contrato de Servicos"));
    }

    @Test
    @DisplayName("GET /v1/documents - should return paginated documents")
    void listDocuments_success() throws Exception {
        DocumentResponseDTO response = sampleDraftResponse();
        PageImpl<DocumentResponseDTO> page = new PageImpl<>(
            List.of(response), PageRequest.of(0, 10), 1
        );

        when(documentService.listUserDocuments(any(), any())).thenReturn(page);

        mockMvc.perform(get("/v1/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].title").value("Contrato de Servicos"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /v1/documents - should return empty page when no documents")
    void listDocuments_empty() throws Exception {
        PageImpl<DocumentResponseDTO> emptyPage = new PageImpl<>(
            List.of(), PageRequest.of(0, 10), 0
        );

        when(documentService.listUserDocuments(any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/v1/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /v1/documents/{id} - should return document by id")
    void getDocument_found() throws Exception {
        DocumentResponseDTO response = sampleDraftResponse();
        when(documentService.getDocument(any(UUID.class), any())).thenReturn(response);

        mockMvc.perform(get("/v1/documents/{id}", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Contrato de Servicos"))
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /v1/documents/{id} - should return 404 when document not found")
    void getDocument_notFound() throws Exception {
        when(documentService.getDocument(any(UUID.class), any()))
            .thenThrow(new DocumentNotFoundException("Documento não encontrado"));

        mockMvc.perform(get("/v1/documents/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /v1/documents/{id}/status - should return document status")
    void getDocumentStatus_success() throws Exception {
        UUID docId = UUID.randomUUID();
        var statusDTO = new br.com.contratoai.dto.DocumentStatusDTO(
            docId, DocumentStatus.DRAFT, null, null, LocalDateTime.now()
        );
        when(documentService.getDocumentStatus(eq(docId), any())).thenReturn(statusDTO);

        mockMvc.perform(get("/v1/documents/{id}/status", docId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(docId.toString()))
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /v1/documents/{id}/pdf - should return PDF bytes with correct headers")
    void downloadPdf_success() throws Exception {
        UUID docId = UUID.randomUUID();
        byte[] pdfBytes = new byte[]{37, 80, 68, 70}; // %PDF
        when(documentService.exportPdf(eq(docId), any())).thenReturn(pdfBytes);

        mockMvc.perform(get("/v1/documents/{id}/pdf", docId))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"contrato-" + docId + ".pdf\""))
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(content().bytes(pdfBytes));
    }

    @Test
    @DisplayName("GET /v1/documents/{id}/docx - should return DOCX bytes with correct headers")
    void downloadDocx_success() throws Exception {
        UUID docId = UUID.randomUUID();
        byte[] docxBytes = new byte[]{80, 75, 3, 4}; // PK
        when(documentService.exportDocx(eq(docId), any())).thenReturn(docxBytes);

        mockMvc.perform(get("/v1/documents/{id}/docx", docId))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"contrato-" + docId + ".docx\""))
            .andExpect(content().bytes(docxBytes));
    }

    @Test
    @DisplayName("GET /v1/documents/{id}/pdf - should return 404 when document not found")
    void downloadPdf_notFound() throws Exception {
        when(documentService.exportPdf(any(UUID.class), any()))
            .thenThrow(new DocumentNotFoundException("Documento não encontrado"));

        mockMvc.perform(get("/v1/documents/{id}/pdf", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }
}
