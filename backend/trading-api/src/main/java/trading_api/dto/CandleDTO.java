package trading_api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CandleDTO(
    String symbol,
    String timeframe,
    Instant timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) {}
