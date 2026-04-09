package br.com.contratoai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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

        Map<String, Object> response = claudeWebClient.post()
            .uri("/v1/messages")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        return extractContent(response);
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
            throw new RuntimeException("Resposta vazia da Claude API");
        }

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("Conteúdo vazio na resposta da Claude API");
        }

        return (String) content.get(0).get("text");
    }
}
