package br.com.contratoai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaudeServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ClaudeService claudeService;

    @BeforeEach
    void setUp() {
        claudeService = new ClaudeService(webClient);
        ReflectionTestUtils.setField(claudeService, "model", "claude-sonnet-4-20250514");
        ReflectionTestUtils.setField(claudeService, "maxTokens", 4096);
    }

    @SuppressWarnings("unchecked")
    private void mockWebClientPost(Map<String, Object> response) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.justOrEmpty(response));
    }

    @Test
    @DisplayName("generateDocument - should return document text on success")
    void generateDocument_success() {
        Map<String, Object> response = Map.of(
            "content", List.of(
                Map.of("type", "text", "text", "CONTRATO DE PRESTACAO DE SERVICOS...")
            )
        );
        mockWebClientPost(response);

        String result = claudeService.generateDocument("Preciso de um contrato de servicos");

        assertThat(result).isEqualTo("CONTRATO DE PRESTACAO DE SERVICOS...");
    }

    @Test
    @DisplayName("generateDocument - should throw when response is null")
    void generateDocument_nullResponse() {
        mockWebClientPost(null);

        assertThatThrownBy(() -> claudeService.generateDocument("descricao qualquer"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Resposta vazia da Claude API");
    }

    @Test
    @DisplayName("generateDocument - should throw when content list is empty")
    void generateDocument_emptyContentList() {
        Map<String, Object> response = Map.of(
            "content", Collections.emptyList()
        );
        mockWebClientPost(response);

        assertThatThrownBy(() -> claudeService.generateDocument("descricao qualquer"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Conteúdo vazio na resposta da Claude API");
    }

    @Test
    @DisplayName("generateDocument - should throw when content key is null")
    void generateDocument_nullContentKey() {
        // Map.of doesn't allow null values, so use a HashMap
        java.util.HashMap<String, Object> response = new java.util.HashMap<>();
        response.put("id", "msg_123");
        response.put("content", null);
        mockWebClientPost(response);

        assertThatThrownBy(() -> claudeService.generateDocument("descricao qualquer"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Conteúdo vazio na resposta da Claude API");
    }
}
