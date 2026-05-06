package br.com.contratoai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Serviço de integridade do conteúdo dos contratos.
 * Gera e verifica hashes SHA-256 para garantir que o conteúdo
 * gerado pela IA não foi adulterado.
 */
@Slf4j
@Service
public class ContentIntegrityService {

    private static final String ALGORITHM = "SHA-256";

    /**
     * Gera o hash SHA-256 do conteúdo do contrato.
     * @param content conteúdo gerado pela IA
     * @return hash em hexadecimal (64 caracteres)
     */
    public String generateHash(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Conteúdo não pode ser vazio para gerar hash");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é garantido em qualquer JVM
            throw new RuntimeException("Algoritmo " + ALGORITHM + " não disponível", e);
        }
    }

    /**
     * Verifica se o conteúdo corresponde ao hash armazenado.
     * @param content conteúdo atual do documento
     * @param storedHash hash armazenado no banco
     * @return true se o conteúdo é íntegro
     */
    public boolean verifyIntegrity(String content, String storedHash) {
        if (content == null || storedHash == null) return false;

        String currentHash = generateHash(content);
        boolean matches = currentHash.equals(storedHash);

        if (!matches) {
            log.warn("Falha na verificação de integridade! Hash esperado={}, calculado={}",
                    storedHash, currentHash);
        }

        return matches;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
