package br.com.contratoai.controller;

import br.com.contratoai.config.RateLimitFilter;
import br.com.contratoai.config.RequestLoggingFilter;
import br.com.contratoai.domain.enums.Plan;
import br.com.contratoai.dto.UserDataExportDTO;
import br.com.contratoai.service.LgpdService;
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

@WebMvcTest(value = LgpdController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {RateLimitFilter.class, RequestLoggingFilter.class}
    ))
@AutoConfigureMockMvc(addFilters = false)
class LgpdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LgpdService lgpdService;

    @Test
    @DisplayName("GET /v1/user/data - should return user data export")
    void exportData_shouldReturnUserData_whenAuthenticated() throws Exception {
        UserDataExportDTO export = new UserDataExportDTO(
            new UserDataExportDTO.PersonalData(
                UUID.randomUUID(),
                "Victor",
                "victor@email.com",
                Plan.FREE,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
            ),
            List.of(),
            new UserDataExportDTO.ExportMetadata(
                LocalDateTime.now(),
                "JSON",
                "1.0"
            )
        );
        when(lgpdService.exportUserData(any())).thenReturn(export);

        mockMvc.perform(get("/v1/user/data"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.personalData.name").value("Victor"))
            .andExpect(jsonPath("$.personalData.email").value("victor@email.com"))
            .andExpect(jsonPath("$.documents").isArray())
            .andExpect(jsonPath("$.metadata.format").value("JSON"));
    }

    @Test
    @DisplayName("POST /v1/user/consent - should record consent and return confirmation")
    void recordConsent_shouldReturnConfirmation_whenBothAccepted() throws Exception {
        doNothing().when(lgpdService).recordConsent(any(), eq(true), eq(true));

        Map<String, Object> requestBody = Map.of(
            "privacyPolicy", true,
            "termsOfService", true
        );

        mockMvc.perform(post("/v1/user/consent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Consentimento registrado com sucesso"))
            .andExpect(jsonPath("$.privacyPolicy").value(true))
            .andExpect(jsonPath("$.termsOfService").value(true));

        verify(lgpdService).recordConsent(any(), eq(true), eq(true));
    }

    @Test
    @DisplayName("POST /v1/user/consent - should handle partial consent")
    void recordConsent_shouldAcceptPartialConsent_whenOnlyPrivacyAccepted() throws Exception {
        doNothing().when(lgpdService).recordConsent(any(), eq(true), eq(false));

        Map<String, Object> requestBody = Map.of(
            "privacyPolicy", true,
            "termsOfService", false
        );

        mockMvc.perform(post("/v1/user/consent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.privacyPolicy").value(true))
            .andExpect(jsonPath("$.termsOfService").value(false));
    }

    @Test
    @DisplayName("DELETE /v1/user/data - should request data deletion and return confirmation")
    void requestDeletion_shouldReturnCompleted_whenDeletionSucceeds() throws Exception {
        doNothing().when(lgpdService).requestDataDeletion(any());

        mockMvc.perform(delete("/v1/user/data"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.message").exists());

        verify(lgpdService).requestDataDeletion(any());
    }
}
