package br.com.contratoai.exception;

public class ClaudeApiException extends RuntimeException {
    public ClaudeApiException(String message) {
        super(message);
    }

    public ClaudeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
