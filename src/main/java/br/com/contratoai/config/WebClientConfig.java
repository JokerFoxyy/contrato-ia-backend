package br.com.contratoai.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${claude.api.key}")
    private String claudeApiKey;

    @Value("${claude.api.base-url}")
    private String claudeBaseUrl;

    @Bean
    public WebClient claudeWebClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)  // 10s para conectar
            .responseTimeout(Duration.ofSeconds(60));               // 60s para resposta

        return WebClient.builder()
            .baseUrl(claudeBaseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("x-api-key", claudeApiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("content-type", "application/json")
            .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .build();
    }
}
