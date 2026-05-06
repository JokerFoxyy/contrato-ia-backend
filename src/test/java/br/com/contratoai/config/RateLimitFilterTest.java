package br.com.contratoai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private RateLimiterRegistry registry;
    private ObjectMapper objectMapper;

    // Configs mais restritivos para testes rápidos
    private RateLimiterConfig generateConfig;
    private RateLimiterConfig exportConfig;
    private RateLimiterConfig readConfig;

    @BeforeEach
    void setUp() {
        registry = RateLimiterRegistry.ofDefaults();
        objectMapper = new ObjectMapper();

        generateConfig = RateLimiterConfig.custom()
            .limitForPeriod(2)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();

        exportConfig = RateLimiterConfig.custom()
            .limitForPeriod(3)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();

        readConfig = RateLimiterConfig.custom()
            .limitForPeriod(5)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();

        filter = new RateLimitFilter(registry, generateConfig, exportConfig, readConfig, objectMapper);
    }

    private void authenticateAs(String userId) {
        Jwt jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .subject(userId)
            .claim("email", "test@test.com")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken(jwt, null));
    }

    @Test
    @DisplayName("classifyTier - POST /generate should be GENERATE tier")
    void classifyTier_generate() {
        assertThat(filter.classifyTier("POST", "/api/v1/documents/generate")).isEqualTo("GENERATE");
    }

    @Test
    @DisplayName("classifyTier - GET /pdf should be EXPORT tier")
    void classifyTier_exportPdf() {
        assertThat(filter.classifyTier("GET", "/api/v1/documents/123/pdf")).isEqualTo("EXPORT");
    }

    @Test
    @DisplayName("classifyTier - GET /docx should be EXPORT tier")
    void classifyTier_exportDocx() {
        assertThat(filter.classifyTier("GET", "/api/v1/documents/123/docx")).isEqualTo("EXPORT");
    }

    @Test
    @DisplayName("classifyTier - GET /documents should be READ tier")
    void classifyTier_read() {
        assertThat(filter.classifyTier("GET", "/api/v1/documents")).isEqualTo("READ");
    }

    @Test
    @DisplayName("classifyTier - GET /status should be READ tier")
    void classifyTier_status() {
        assertThat(filter.classifyTier("GET", "/api/v1/documents/123/status")).isEqualTo("READ");
    }

    @Test
    @DisplayName("filter - should allow requests within rate limit")
    void filter_withinLimit() throws Exception {
        authenticateAs("user-1");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents/generate");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("filter - should return 429 when rate limit exceeded for GENERATE")
    void filter_generateLimitExceeded() throws Exception {
        authenticateAs("user-rate-gen");

        // Esgota o limite (2 requests)
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/documents/generate");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilterInternal(req, resp, new MockFilterChain());
            assertThat(resp.getStatus()).isEqualTo(200);
        }

        // 3a request deve ser bloqueada
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents/generate");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("429");
        assertThat(response.getContentAsString()).contains("GENERATE");

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("filter - should apply separate limits per user")
    void filter_separateLimitsPerUser() throws Exception {
        // User A esgota o limite
        authenticateAs("user-a");
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/documents/generate");
            filter.doFilterInternal(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // User A bloqueado
        MockHttpServletResponse blockedResp = new MockHttpServletResponse();
        filter.doFilterInternal(
            new MockHttpServletRequest("POST", "/api/v1/documents/generate"),
            blockedResp, new MockFilterChain());
        assertThat(blockedResp.getStatus()).isEqualTo(429);

        // User B ainda pode usar
        authenticateAs("user-b");
        MockHttpServletResponse allowedResp = new MockHttpServletResponse();
        filter.doFilterInternal(
            new MockHttpServletRequest("POST", "/api/v1/documents/generate"),
            allowedResp, new MockFilterChain());
        assertThat(allowedResp.getStatus()).isEqualTo(200);

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("filter - should apply separate limits per tier for same user")
    void filter_separateLimitsPerTier() throws Exception {
        authenticateAs("user-tier");

        // Esgota GENERATE (2 requests)
        for (int i = 0; i < 2; i++) {
            filter.doFilterInternal(
                new MockHttpServletRequest("POST", "/api/v1/documents/generate"),
                new MockHttpServletResponse(), new MockFilterChain());
        }

        // GENERATE bloqueado
        MockHttpServletResponse genResp = new MockHttpServletResponse();
        filter.doFilterInternal(
            new MockHttpServletRequest("POST", "/api/v1/documents/generate"),
            genResp, new MockFilterChain());
        assertThat(genResp.getStatus()).isEqualTo(429);

        // READ ainda funciona (limite diferente)
        MockHttpServletResponse readResp = new MockHttpServletResponse();
        filter.doFilterInternal(
            new MockHttpServletRequest("GET", "/api/v1/documents"),
            readResp, new MockFilterChain());
        assertThat(readResp.getStatus()).isEqualTo(200);

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("filter - should skip public paths")
    void filter_skipPublicPaths() throws Exception {
        // Sem autenticação — path público
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("filter - should skip unauthenticated requests (Spring Security handles auth)")
    void filter_skipUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents/generate");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        // Não bloqueia — deixa passar para o Spring Security negar
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
