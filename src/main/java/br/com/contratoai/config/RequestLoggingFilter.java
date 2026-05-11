package br.com.contratoai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Filtro que injeta contexto no MDC para todos os logs de uma request.
 * Loga method, path, status e duration de cada request HTTP.
 * Omite paths de healthcheck e actuator para não poluir os logs.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Set<String> IGNORED_PATHS = Set.of(
        "/actuator/health",
        "/actuator/info",
        "/favicon.ico"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String method = request.getMethod();
        String path = request.getRequestURI();
        String clientIp = extractClientIp(request);

        // Injeta no MDC — disponível em todos os logs desta thread
        MDC.put("requestId", requestId);
        MDC.put("method", method);
        MDC.put("path", path);
        MDC.put("clientIp", clientIp);

        // Adiciona requestId no response header para rastreabilidade do frontend
        response.setHeader("X-Request-Id", requestId);

        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Tenta extrair userId do JWT (se autenticado)
            extractAndSetUserId();

            long duration = System.currentTimeMillis() - start;

            if (!isIgnoredPath(path)) {
                log.info("HTTP {} {} {} {}ms [ip={}]",
                    method, sanitizeLogValue(path), response.getStatus(), duration, sanitizeLogValue(clientIp));
            }

            // Limpa o MDC para evitar leak entre requests (thread pool)
            MDC.clear();
        }
    }

    private void extractAndSetUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                MDC.put("userId", jwt.getSubject());
            }
        } catch (Exception e) {
            // Silencioso — nem toda request tem JWT
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isIgnoredPath(String path) {
        return IGNORED_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Sanitiza valores antes de logar para prevenir log injection.
     * Remove caracteres de controle (newlines, tabs, carriage returns).
     */
    private String sanitizeLogValue(String value) {
        if (value == null) return "null";
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}
