package br.com.contratoai.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    private MethodParameter dummyMethodParameter() throws NoSuchMethodException {
        // Use any method to get a valid MethodParameter
        return new MethodParameter(
            GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1
        );
    }

    @Test
    @DisplayName("handleValidation - should return 400 with field errors")
    void handleValidation_fieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError(
            "request", "description", "Descrição deve ter entre 20 e 2000 caracteres"
        ));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(400);
        assertThat(response.getBody().get("error")).isEqualTo("Validation Failed");

        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).containsEntry("description", "Descrição deve ter entre 20 e 2000 caracteres");
    }

    @Test
    @DisplayName("handleValidation - should return 400 with multiple field errors")
    void handleValidation_multipleFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "description", "Campo obrigatorio"));
        bindingResult.addError(new FieldError("request", "title", "Titulo invalido"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).hasSize(2);
        assertThat(fields).containsEntry("description", "Campo obrigatorio");
        assertThat(fields).containsEntry("title", "Titulo invalido");
    }

    @Test
    @DisplayName("handleRuntime - should return 404 for 'nao encontrado' messages")
    void handleRuntime_notFound() {
        RuntimeException ex = new RuntimeException("Documento não encontrado");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntime(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(404);
        assertThat(response.getBody().get("error")).isEqualTo("Documento não encontrado");
    }

    @Test
    @DisplayName("handleRuntime - should return 404 for user not found")
    void handleRuntime_userNotFound() {
        RuntimeException ex = new RuntimeException("Usuário não encontrado: some-id");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntime(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("status")).isEqualTo(404);
    }

    @Test
    @DisplayName("handleRuntime - should return 500 for generic runtime errors")
    void handleRuntime_internalServerError() {
        RuntimeException ex = new RuntimeException("Erro ao gerar documento com IA: API timeout");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntime(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(500);
        assertThat(response.getBody().get("error")).isEqualTo("Erro ao gerar documento com IA: API timeout");
    }

    @Test
    @DisplayName("handleRuntime - should return 500 for plan limit error")
    void handleRuntime_planLimitError() {
        RuntimeException ex = new RuntimeException(
            "Limite de 3 documentos/mês do plano gratuito atingido. Faça upgrade para o plano Pro."
        );

        ResponseEntity<Map<String, Object>> response = handler.handleRuntime(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("status")).isEqualTo(500);
    }

    @Test
    @DisplayName("handleRuntime - response body should contain timestamp")
    void handleRuntime_containsTimestamp() {
        RuntimeException ex = new RuntimeException("Algum erro");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntime(ex);

        assertThat(response.getBody()).containsKey("timestamp");
        assertThat(response.getBody().get("timestamp")).isNotNull();
    }
}
