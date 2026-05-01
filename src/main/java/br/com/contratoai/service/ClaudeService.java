package br.com.contratoai.service;

import br.com.contratoai.exception.ClaudeApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ClaudeService {

    private final WebClient claudeWebClient;

    @Value("${claude.api.model}")
    private String model;

    @Value("${claude.api.max-tokens}")
    private int maxTokens;

    public ClaudeService(WebClient claudeWebClient) {
        this.claudeWebClient = claudeWebClient;
    }

    public String generateDocument(String userDescription) {
        String systemPrompt = buildSystemPrompt();
        String userMessage = buildUserMessage(userDescription);

        Map<String, Object> requestBody = Map.of(
            "model", model,
            "max_tokens", maxTokens,
            "system", systemPrompt,
            "messages", List.of(
                Map.of("role", "user", "content", userMessage)
            )
        );

        try {
            Map<String, Object> response = claudeWebClient.post()
                .uri("/v1/messages")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                    clientResponse.bodyToMono(String.class)
                        .map(body -> new ClaudeApiException("Claude API erro 4xx: " + body))
                )
                .bodyToMono(Map.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                    .filter(throwable -> throwable instanceof WebClientResponseException wcre
                        && wcre.getStatusCode().is5xxServerError())
                    .onRetryExhaustedThrow((spec, signal) ->
                        new ClaudeApiException("Claude API indisponível após " + spec.maxAttempts + " tentativas", signal.failure()))
                    .doBeforeRetry(signal ->
                        log.warn("Retry #{} para Claude API após erro: {}", signal.totalRetries() + 1, signal.failure().getMessage()))
                )
                .block();

            return extractContent(response);

        } catch (ClaudeApiException e) {
            throw e; // Já é nossa exceção tipada
        } catch (Exception e) {
            throw new ClaudeApiException("Falha na comunicação com a Claude API: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt() {
        return """
            Você é um especialista jurídico brasileiro com mais de 20 anos de experiência em contratos.
            Sua função é gerar contratos completos e juridicamente válidos para o mercado brasileiro.

            REGRAS OBRIGATÓRIAS:
            1. Sempre use linguagem formal e juridicamente precisa
            2. Inclua todas as cláusulas essenciais: partes, objeto, obrigações, prazo, valor, rescisão, foro
            3. Base todos os contratos na legislação brasileira vigente (Código Civil, CLT quando aplicável, Lei 13.709/LGPD, etc.)
            4. Formate o contrato com numeração de cláusulas e parágrafos
            5. Inclua campos a preencher no formato [DADO A PREENCHER] para dados específicos
            6. Use o formato: CONTRATO DE [TIPO], seguido das partes e cláusulas numeradas
            7. Ao final, inclua espaços para assinaturas com local, data e campos para nome/CPF/RG

            IMPORTANTE: Gere apenas o texto do contrato. Não inclua explicações ou comentários externos ao documento.
            """;
    }

    private String buildUserMessage(String description) {
        return String.format("""
            Gere um contrato completo com base na seguinte descrição:

            %s

            O contrato deve estar pronto para uso, com todas as cláusulas necessárias para proteger ambas as partes.
            """, description);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            throw new ClaudeApiException("Resposta vazia da Claude API");
        }

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            throw new ClaudeApiException("Conteúdo vazio na resposta da Claude API");
        }

        return (String) content.get(0).get("text");
    }
}
