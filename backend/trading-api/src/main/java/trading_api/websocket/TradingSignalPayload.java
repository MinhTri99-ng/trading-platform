package trading_api.websocket;

import java.math.BigDecimal;

public record TradingSignalPayload(
        String symbol,
        String direction,
        BigDecimal entry,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        String riskReward,
        int confidence,
        long timestamp
) {}
