package br.com.contratoai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Filtro de rate limiting per-user baseado no JWT subject.
 *
 * Classifica cada request em um tier (GENERATE, EXPORT, READ)
 * e aplica um rate limiter específico para a combinação userId + tier.
 *
 * Roda APÓS o filtro de segurança (para ter acesso ao JWT) e APÓS
 * o RequestLoggingFilter (para ter o MDC populado).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final RateLimiterConfig generateRateLimiterConfig;
    private final RateLimiterConfig exportRateLimiterConfig;
    private final RateLimiterConfig readRateLimiterConfig;
    private final ObjectMapper objectMapper;

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/actuator/health",
        "/actuator/info",
        "/v3/api-docs",
        "/swagger-ui"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Não aplica rate limit em paths públicos
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extrai userId do JWT
        String userId = extractUserId();
        if (userId == null) {
            // Sem autenticação → o Spring Security vai bloquear antes
            filterChain.doFilter(request, response);
            return;
        }

        // Classifica o tier e obtém o rate limiter per-user
        String tier = classifyTier(request.getMethod(), path);
        RateLimiterConfig config = getConfigForTier(tier);
        String rateLimiterKey = userId + ":" + tier;

        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterKey, config);

        try {
            RateLimiter.waitForPermission(rateLimiter);
            filterChain.doFilter(request, response);
        } catch (RequestNotPermitted e) {
            log.warn("Rate limit excedido. userId={}, tier={}, path={}", userId, tier, path);
            writeRateLimitResponse(response, tier);
        }
    }

    /**
     * Classifica a request em um dos 3 tiers baseado no method + path.
     */
    String classifyTier(String method, String path) {
        // POST /v1/documents/generate → GENERATE (alto custo)
        if ("POST".equals(method) && path.contains("/documents/generate")) {
            return "GENERATE";
        }

        // GET /v1/documents/{id}/pdf ou /docx → EXPORT (CPU-intensive)
        if ("GET".equals(method) && (path.endsWith("/pdf") || path.endsWith("/docx"))) {
            return "EXPORT";
        }

        // Tudo mais → READ (leitura leve)
        return "READ";
    }

    private RateLimiterConfig getConfigForTier(String tier) {
        return switch (tier) {
            case "GENERATE" -> generateRateLimiterConfig;
            case "EXPORT" -> exportRateLimiterConfig;
            default -> readRateLimiterConfig;
        };
    }

    private String extractUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                return jwt.getSubject();
            }
        } catch (Exception e) {
            // Silencioso
        }
        return null;
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private void writeRateLimitResponse(HttpServletResponse response, String tier) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");

        Map<String, Object> body = Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status", 429,
            "error", "Taxa de requisições excedida. Aguarde antes de tentar novamente.",
            "tier", tier
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
