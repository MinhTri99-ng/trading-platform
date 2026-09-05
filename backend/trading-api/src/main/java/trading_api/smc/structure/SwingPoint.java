package trading_api.smc.structure;

import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.time.Instant;

public record SwingPoint(
        int index,
        BigDecimal price,
        SwingDirection type,
        BigDecimal strength,
        boolean external,
        Instant timestamp,
        String timeframe
) {
    public Candle toCandleSnapshot() {
        return null;
    }

    public enum SwingDirection {
        HIGH,
        LOW
    }
}
