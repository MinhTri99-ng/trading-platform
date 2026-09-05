package trading_api.indicator;

import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class VolumeMAIndicator {

    private static final MathContext MC =
            new MathContext(20, RoundingMode.HALF_UP);

    public BigDecimal calculate(
            List<Candle> candles,
            int period
    ) {

        if (candles == null || candles.size() < period) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;

        int start = candles.size() - period;

        for (int i = start; i < candles.size(); i++) {

            sum = sum.add(
                    candles.get(i).getVolume(),
                    MC
            );
        }

        return sum.divide(
                BigDecimal.valueOf(period),
                MC
        );
    }
}