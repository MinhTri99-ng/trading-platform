package trading_api.market;

import java.math.BigDecimal;
import java.time.Instant;

public class NormalizedTrade {
    private final long tradeId;
    private final String symbol;
    private final BigDecimal price;
    private final BigDecimal volume;
    private final Instant timestamp;

    public NormalizedTrade(
            long tradeId,
            String symbol,
            BigDecimal price,
            BigDecimal volume,
            Instant timestamp
    ) {
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
    }
    public long getTradeId() {
        return tradeId;
    }
    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

}