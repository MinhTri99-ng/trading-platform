package trading_api.smc.structure;

import java.math.BigDecimal;
import java.time.Instant;

public record ProtectedLevel(
        BigDecimal price,
        String type,
        Instant timestamp,
        BigDecimal strength,
        String timeframe
) {
}
