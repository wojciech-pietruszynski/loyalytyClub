package pl.pietruszynski.loyaltyclub.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAuthException_shouldReturn401() {
        ResponseEntity<ProblemDetail> response = handler.handleAuthException(
                new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Unauthorized");
    }

    @Test
    void handleAccessDenied_shouldReturn403() {
        ResponseEntity<ProblemDetail> response = handler.handleAccessDenied(
                new AccessDeniedException("Access denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Forbidden");
    }

    @Test
    void handleResourceNotFound_shouldReturn404WithMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Customer not found with id: 99");

        ResponseEntity<ProblemDetail> response = handler.handleResourceNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Customer not found with id: 99");
    }

    @Test
    void handleIllegalArgumentException_shouldReturn400WithMessage() {
        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("sourceTransactionNumber is required"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("sourceTransactionNumber is required");
    }

    @Test
    void handleRuntimeException_shouldReturn400WithMessage() {
        ResponseEntity<ProblemDetail> response = handler.handleRuntimeException(
                new RuntimeException("Customer with this email already exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Customer with this email already exists");
    }

    @Test
    void handleValidationException_shouldReturn400WithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "email", "Email is required");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ProblemDetail> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Validation failed");

        @SuppressWarnings("unchecked")
        var errors = (java.util.Map<String, String>) response.getBody().getProperties().get("errors");
        assertThat(errors).containsEntry("email", "Email is required");
    }

    @Test
    void handleValidationException_multipleFieldErrors_shouldIncludeAll() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        List<FieldError> fieldErrors = List.of(
                new FieldError("request", "email", "Email is required"),
                new FieldError("request", "firstName", "First name is required")
        );

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        ResponseEntity<ProblemDetail> response = handler.handleValidationException(ex);

        @SuppressWarnings("unchecked")
        var errors = (java.util.Map<String, String>) response.getBody().getProperties().get("errors");
        assertThat(errors).hasSize(2);
        assertThat(errors).containsKey("email");
        assertThat(errors).containsKey("firstName");
    }
}
