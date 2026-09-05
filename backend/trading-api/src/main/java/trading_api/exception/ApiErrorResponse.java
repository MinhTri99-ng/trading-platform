package trading_api.exception;

import java.time.Instant;

public record ApiErrorResponse(
        String status,
        String message,
        String errorCode,
        Instant timestamp
) {
}
