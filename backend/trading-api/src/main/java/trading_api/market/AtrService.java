package trading_api.market;

import trading_api.entity.Candle;
import trading_api.indicator.ATRIndicator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class AtrService {

    public static final int ATR_PERIOD = 14;

    private static final MathContext MC =
            new MathContext(20, RoundingMode.HALF_UP);

    private final ATRIndicator atrIndicator;

    public AtrService() {
        this.atrIndicator = new ATRIndicator();
    }

    public BigDecimal calculate(List<Candle> candles) {
        validateCandles(candles);

        BigDecimal atr = atrIndicator.calculate(candles, ATR_PERIOD);

        if (atr == null || atr.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "ATR must be positive"
            );
        }

        return atr;
    }

    private void validateCandles(List<Candle> candles) {
        if (candles == null) {
            throw new IllegalArgumentException(
                    "Candles cannot be null"
            );
        }

        if (candles.size() < ATR_PERIOD + 1) {
            throw new IllegalArgumentException(
                    "Not enough candles for ATR calculation"
            );
        }

        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            if (candle == null) {
                throw new IllegalArgumentException(
                        "Candle at index " + i + " cannot be null"
                );
            }

            validatePrice(candle.getOpen(), "Open", i);
            validatePrice(candle.getHigh(), "High", i);
            validatePrice(candle.getLow(), "Low", i);
            validatePrice(candle.getClose(), "Close", i);

            if (candle.getHigh().compareTo(candle.getLow()) < 0) {
                throw new IllegalArgumentException(
                        "High cannot be lower than low for candle " + i
                );
            }
        }
    }

    private void validatePrice(
            BigDecimal price,
            String fieldName,
            int index
    ) {
        if (price == null) {
            throw new IllegalArgumentException(
                    fieldName + " for candle " + index + " cannot be null"
            );
        }

        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    fieldName + " for candle " + index + " cannot be negative"
            );
        }
    }
}
