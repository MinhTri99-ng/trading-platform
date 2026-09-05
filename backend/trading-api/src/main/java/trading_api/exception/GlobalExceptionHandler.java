package trading_api.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import trading_api.auth.AuthService;
import trading_api.analysis.AnalysisHistoryService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed");

        log.error("[Validation failure] {}", message, ex);
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "BAD_REQUEST",
                message,
                "VALIDATION_ERROR",
                Instant.now()
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.error("[Validation failure] {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "BAD_REQUEST",
                ex.getMessage(),
                "CONSTRAINT_VIOLATION",
                Instant.now()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("[Illegal argument] {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "BAD_REQUEST",
                ex.getMessage(),
                "INVALID_INPUT",
                Instant.now()
        ));
    }

    @ExceptionHandler(AuthService.EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(AuthService.EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse("CONFLICT", "Email already exists", "EMAIL_EXISTS", Instant.now()));
    }

    @ExceptionHandler(AuthService.InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(AuthService.InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiErrorResponse("UNAUTHORIZED", "Invalid email or password", "INVALID_CREDENTIALS", Instant.now()));
    }

    @ExceptionHandler(AnalysisHistoryService.AnalysisNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAnalysisNotFound(AnalysisHistoryService.AnalysisNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse("NOT_FOUND", "Analysis not found", "ANALYSIS_NOT_FOUND", Instant.now()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex) {
        log.error("[Runtime exception] {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(
                "INTERNAL_SERVER_ERROR",
                ex.getMessage(),
                "RUNTIME_EXCEPTION",
                Instant.now()
        ));
    }
}
