package br.com.contratoai.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configura rate limiters globais por tier de endpoint.
 * O RateLimitFilter aplica rate limiting per-user usando o userId (JWT subject)
 * como chave para obter um rate limiter individual.
 *
 * Tiers:
 * - GENERATE: endpoints que disparam Claude API (alto custo $$)
 * - EXPORT: endpoints que geram PDF/DOCX (custo de CPU)
 * - READ: endpoints de leitura (baixo custo, mas protege contra scraping)
 */
@Configuration
public class RateLimitConfig {

    @Value("${rate-limit.generate:5}")
    private int generateLimit;

    @Value("${rate-limit.export:20}")
    private int exportLimit;

    @Value("${rate-limit.read:60}")
    private int readLimit;

    /**
     * Registry central — gerencia e cacheia rate limiters por chave (userId:tier).
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        return RateLimiterRegistry.ofDefaults();
    }

    /**
     * Tier GENERATE: N requests por minuto por usuário.
     * Protege contra abuso da Claude API (cada chamada custa $$).
     */
    @Bean
    public RateLimiterConfig generateRateLimiterConfig() {
        return RateLimiterConfig.custom()
            .limitForPeriod(generateLimit)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();
    }

    /**
     * Tier EXPORT: N requests por minuto por usuário.
     * Protege contra download abusivo de PDF/DOCX (CPU-intensive).
     */
    @Bean
    public RateLimiterConfig exportRateLimiterConfig() {
        return RateLimiterConfig.custom()
            .limitForPeriod(exportLimit)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();
    }

    /**
     * Tier READ: N requests por minuto por usuário.
     * Protege contra scraping e polling excessivo.
     */
    @Bean
    public RateLimiterConfig readRateLimiterConfig() {
        return RateLimiterConfig.custom()
            .limitForPeriod(readLimit)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build();
    }
}
