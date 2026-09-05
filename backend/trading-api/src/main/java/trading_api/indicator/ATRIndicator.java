package trading_api.indicator;

import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class ATRIndicator {

    private static final MathContext MC =
            new MathContext(20, RoundingMode.HALF_UP);

    public BigDecimal calculate(
            List<Candle> candles,
            int period
    ) {

        if (candles == null || candles.size() < period + 1) {
            return null;
        }

        /*
         * True Range đầu tiên cần previous close.
         */

        BigDecimal trSum = BigDecimal.ZERO;

        for (int i = 1; i <= period; i++) {

            BigDecimal high = candles.get(i).getHigh();
            BigDecimal low = candles.get(i).getLow();
            BigDecimal previousClose =
                    candles.get(i - 1).getClose();

            BigDecimal tr = trueRange(
                    high,
                    low,
                    previousClose
            );

            trSum = trSum.add(tr, MC);
        }

        /*
         * Initial ATR = SMA của 14 TR.
         */

        BigDecimal atr = trSum.divide(
                BigDecimal.valueOf(period),
                MC
        );

        /*
         * Wilder's smoothing.
         *
         * ATR = ((Previous ATR × 13) + Current TR) / 14
         */

        for (int i = period + 1; i < candles.size(); i++) {

            BigDecimal high = candles.get(i).getHigh();
            BigDecimal low = candles.get(i).getLow();
            BigDecimal previousClose =
                    candles.get(i - 1).getClose();

            BigDecimal tr = trueRange(
                    high,
                    low,
                    previousClose
            );

            atr = atr
                    .multiply(
                            BigDecimal.valueOf(period - 1),
                            MC
                    )
                    .add(tr, MC)
                    .divide(
                            BigDecimal.valueOf(period),
                            MC
                    );
        }

        return atr;
    }

    private BigDecimal trueRange(
            BigDecimal high,
            BigDecimal low,
            BigDecimal previousClose
    ) {

        BigDecimal highLow =
                high.subtract(low, MC).abs();

        BigDecimal highPreviousClose =
                high.subtract(previousClose, MC).abs();

        BigDecimal lowPreviousClose =
                low.subtract(previousClose, MC).abs();

        return highLow
                .max(highPreviousClose)
                .max(lowPreviousClose);
    }
}