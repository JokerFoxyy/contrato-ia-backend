package br.com.contratoai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentIntegrityServiceTest {

    private ContentIntegrityService service;

    @BeforeEach
    void setUp() {
        service = new ContentIntegrityService();
    }

    @Test
    @DisplayName("generateHash - should produce consistent 64-char hex hash")
    void generateHash_consistent() {
        String content = "CONTRATO DE PRESTACAO DE SERVICOS";
        String hash1 = service.generateHash(content);
        String hash2 = service.generateHash(content);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
        assertThat(hash1).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("generateHash - different content produces different hashes")
    void generateHash_differentContent() {
        String hash1 = service.generateHash("Contrato A");
        String hash2 = service.generateHash("Contrato B");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("generateHash - should throw for null content")
    void generateHash_null() {
        assertThatThrownBy(() -> service.generateHash(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateHash - should throw for blank content")
    void generateHash_blank() {
        assertThatThrownBy(() -> service.generateHash("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("verifyIntegrity - should return true for matching content")
    void verifyIntegrity_match() {
        String content = "CONTRATO DE LOCACAO";
        String hash = service.generateHash(content);

        assertThat(service.verifyIntegrity(content, hash)).isTrue();
    }

    @Test
    @DisplayName("verifyIntegrity - should return false for tampered content")
    void verifyIntegrity_tampered() {
        String original = "CONTRATO DE LOCACAO";
        String hash = service.generateHash(original);
        String tampered = "CONTRATO DE LOCACAO ADULTERADO";

        assertThat(service.verifyIntegrity(tampered, hash)).isFalse();
    }

    @Test
    @DisplayName("verifyIntegrity - should return false for null inputs")
    void verifyIntegrity_nullInputs() {
        assertThat(service.verifyIntegrity(null, "abc")).isFalse();
        assertThat(service.verifyIntegrity("abc", null)).isFalse();
    }

    @Test
    @DisplayName("verifyIntegrity - even single char change should fail")
    void verifyIntegrity_singleCharChange() {
        String content = "Clausula 1: O contratante se compromete a pagar R$ 5.000,00";
        String hash = service.generateHash(content);
        String modified = "Clausula 1: O contratante se compromete a pagar R$ 50000,00";

        assertThat(service.verifyIntegrity(modified, hash)).isFalse();
    }
}
