package trading_api.websocket;

import java.math.BigDecimal;

public record KlineStreamPayload(
        String eventId,
        String symbol,
        String interval,
        long openTime,
        long closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean isFinal,
        long timestamp
) {}
