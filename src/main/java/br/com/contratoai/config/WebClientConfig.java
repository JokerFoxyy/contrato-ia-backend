package br.com.contratoai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${claude.api.key}")
    private String claudeApiKey;

    @Value("${claude.api.base-url}")
    private String claudeBaseUrl;

    @Bean
    public WebClient claudeWebClient() {
        return WebClient.builder()
            .baseUrl(claudeBaseUrl)
            .defaultHeader("x-api-key", claudeApiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("content-type", "application/json")
            .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .build();
    }
}
