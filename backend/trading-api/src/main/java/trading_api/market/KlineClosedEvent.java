package trading_api.market;

import trading_api.entity.Candle;

public record KlineClosedEvent(Candle candle) {
    public KlineClosedEvent {
        if (candle == null) {
            throw new IllegalArgumentException("Closed candle cannot be null");
        }
    }
}
