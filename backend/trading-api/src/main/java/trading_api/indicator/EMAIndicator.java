package trading_api.indicator;

import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class EMAIndicator {

    private static final MathContext MC =
            new MathContext(20, RoundingMode.HALF_UP);

    public BigDecimal calculate(
            List<Candle> candles,
            int period
    ) {

        if (candles == null || candles.size() < period) {
            return null;
        }

        /*
         * EMA đầu tiên được seed bằng SMA.
         */

        BigDecimal sum = BigDecimal.ZERO;

        for (int i = 0; i < period; i++) {
            sum = sum.add(
                    candles.get(i).getClose(),
                    MC
            );
        }

        BigDecimal ema = sum.divide(
                BigDecimal.valueOf(period),
                MC
        );

        BigDecimal multiplier = BigDecimal.valueOf(2)
                .divide(
                        BigDecimal.valueOf(period + 1),
                        MC
                );

        for (int i = period; i < candles.size(); i++) {

            BigDecimal close = candles.get(i).getClose();

            ema = close
                    .subtract(ema, MC)
                    .multiply(multiplier, MC)
                    .add(ema, MC);
        }

        return ema;
    }
}