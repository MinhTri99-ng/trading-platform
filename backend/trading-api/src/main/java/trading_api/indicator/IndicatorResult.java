package trading_api.indicator;

import java.math.BigDecimal;
import java.time.Instant;

public record IndicatorResult(
        String symbol,
        String timeframe,
        Instant timestamp,
        BigDecimal ema50,
        BigDecimal ema200,
        BigDecimal atr14,
        BigDecimal volumeMa20
) {
}