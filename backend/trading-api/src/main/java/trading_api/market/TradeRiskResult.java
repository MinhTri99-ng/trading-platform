package trading_api.market;

import java.math.BigDecimal;

public record TradeRiskResult(
        Direction direction,
        BigDecimal entry,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        BigDecimal risk,
        BigDecimal reward,
        BigDecimal riskRewardRatio
) {
    public enum Direction {
        LONG,
        SHORT
    }
}
