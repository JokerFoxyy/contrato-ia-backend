package br.com.contratoai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste de contexto Spring — requer PostgreSQL e Keycloak rodando.
 * Executado apenas em CI (que tem o service container Postgres).
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class ContratoIaApplicationTests {

    @Test
    void contextLoads() {
    }
}
