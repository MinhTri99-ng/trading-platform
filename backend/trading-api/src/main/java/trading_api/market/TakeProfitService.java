package trading_api.market;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class TakeProfitService {

    public static final BigDecimal RISK_REWARD_RATIO =
            BigDecimal.valueOf(2.0);

    private static final MathContext MC =
            new MathContext(20, RoundingMode.HALF_UP);

    public TradeRiskResult calculate(
            BigDecimal entry,
            BigDecimal stopLoss,
            TradeRiskResult.Direction direction,
            BigDecimal riskRewardRatio
    ) {
        validateInput(entry, "Entry");
        validateInput(stopLoss, "Stop loss");

        if (direction == null) {
            throw new IllegalArgumentException(
                    "Direction cannot be null"
            );
        }

        if (riskRewardRatio == null || riskRewardRatio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Risk/reward ratio must be positive"
            );
        }

        BigDecimal risk = direction == TradeRiskResult.Direction.LONG
                ? entry.subtract(stopLoss, MC).abs()
                : stopLoss.subtract(entry, MC).abs();

        BigDecimal reward = risk.multiply(
                riskRewardRatio,
                MC
        );

        BigDecimal takeProfit;

        if (direction == TradeRiskResult.Direction.LONG) {
            if (stopLoss.compareTo(entry) >= 0) {
                throw new IllegalArgumentException(
                        "LONG stop loss must be below entry"
                );
            }

            takeProfit = entry.add(reward, MC);

            if (takeProfit.compareTo(entry) <= 0) {
                throw new IllegalArgumentException(
                        "LONG take profit must be above entry"
                );
            }

        } else if (direction == TradeRiskResult.Direction.SHORT) {
            if (stopLoss.compareTo(entry) <= 0) {
                throw new IllegalArgumentException(
                        "SHORT stop loss must be above entry"
                );
            }

            takeProfit = entry.subtract(reward, MC);

            if (takeProfit.compareTo(entry) >= 0) {
                throw new IllegalArgumentException(
                        "SHORT take profit must be below entry"
                );
            }

        } else {
            throw new IllegalArgumentException(
                    "Unsupported direction: " + direction
            );
        }

        return new TradeRiskResult(
                direction,
                entry,
                stopLoss,
                takeProfit,
                risk,
                reward,
                riskRewardRatio
        );
    }

    private void validateInput(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(
                    name + " cannot be null"
            );
        }

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    name + " cannot be negative"
            );
        }
    }
}
