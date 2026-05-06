package br.com.contratoai.config;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Sanitiza e valida inputs do usuário antes de enviar ao Claude API.
 * Protege contra prompt injection, XSS e conteúdo malicioso.
 */
@Component
public class InputSanitizer {

    // Padrões comuns de prompt injection
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        // Tentativas de mudar o role/persona do modelo
        Pattern.compile("(?i)(ignore|disregard|forget)\\s+(all\\s+)?(the\\s+)?(previous|above|prior)\\s+(instructions|rules|prompts)"),
        Pattern.compile("(?i)(ignore|disregard|forget)\\s+(the\\s+)?rules\\s+above"),
        Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an|the)\\s+"),
        Pattern.compile("(?i)(act|behave|pretend|respond)\\s+(as|like)\\s+(if\\s+)?(you\\s+)?(are|were)"),
        Pattern.compile("(?i)new\\s+(instructions|rules|system\\s+prompt)"),
        Pattern.compile("(?i)(system|admin|developer)\\s*(:|prompt|message|override)"),
        // Tentativas de exfiltrar o system prompt
        Pattern.compile("(?i)(reveal|show|display|print|repeat|output)\\s+(me\\s+)?(your|the|system)\\s+(system\\s+)?(prompt|instructions|rules)"),
        Pattern.compile("(?i)what\\s+(are|is)\\s+your\\s+(system\\s+)?(prompt|instructions|rules)"),
        // Delimitadores usados para injetar contexto
        Pattern.compile("(?i)\\[\\s*(SYSTEM|INST|ADMIN)\\s*\\]"),
        Pattern.compile("(?i)<\\s*(system|instruction|admin)\\s*>"),
        // Tentativas de executar código
        Pattern.compile("(?i)(execute|run|eval)\\s+(the\\s+following|this|the|following)\\s+(code|script|command)"),
        Pattern.compile("(?i)```\\s*(bash|shell|python|javascript|sql)")
    );

    // Caracteres de controle que não deveriam estar em descrições de contrato
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    // HTML/Script tags
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]*script[^>]*>|<[^>]*on\\w+\\s*=", Pattern.CASE_INSENSITIVE);

    /**
     * Sanitiza o input do usuário removendo caracteres perigosos.
     * @param input texto original do usuário
     * @return texto sanitizado
     */
    public String sanitize(String input) {
        if (input == null) return null;

        // Remove caracteres de controle (exceto newline, tab, carriage return)
        String sanitized = CONTROL_CHARS.matcher(input).replaceAll("");

        // Remove tags HTML/script
        sanitized = HTML_TAGS.matcher(sanitized).replaceAll("");

        // Normaliza espaços excessivos (mais de 3 newlines seguidas → 2)
        sanitized = sanitized.replaceAll("\\n{4,}", "\n\n\n");

        // Remove espaços no início e fim
        return sanitized.trim();
    }

    /**
     * Detecta se o input contém padrões de prompt injection.
     * @param input texto do usuário
     * @return true se detectou tentativa de injection
     */
    public boolean containsInjectionAttempt(String input) {
        if (input == null || input.isBlank()) return false;

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Valida e sanitiza o input, retornando o texto limpo.
     * Lança exceção se detectar tentativa de injection.
     * @param input texto do usuário
     * @return texto sanitizado e validado
     * @throws PromptInjectionException se detectar tentativa de injection
     */
    public String validateAndSanitize(String input) {
        String sanitized = sanitize(input);

        if (containsInjectionAttempt(sanitized)) {
            throw new PromptInjectionException(
                "A descrição contém padrões não permitidos. Por favor, descreva o contrato de forma objetiva."
            );
        }

        return sanitized;
    }

    /**
     * Exceção lançada quando uma tentativa de prompt injection é detectada.
     */
    public static class PromptInjectionException extends RuntimeException {
        public PromptInjectionException(String message) {
            super(message);
        }
    }
}
