package trading_api.indicator;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EMAIndicatorTest {
    private final EMAIndicator indicator = new EMAIndicator();

    @Test
    void usesSmaSeedAndStandardAlpha() {
        assertEquals(0, indicator.calculate(candles(10, 20, 30, 40, 50), 3).compareTo(BigDecimal.valueOf(40)));
    }

    @Test
    void calculatesEma50FromAscendingCandles() {
        List<Candle> candles = candlesFrom(60);

        BigDecimal value = indicator.calculate(candles, 50);

        assertEquals(0, value.compareTo(BigDecimal.valueOf(134.5)));
    }

    @Test
    void calculatesEma200FromAscendingCandles() {
        List<Candle> candles = candlesFrom(220);

        BigDecimal value = indicator.calculate(candles, 200);

        assertEquals(0, value.compareTo(BigDecimal.valueOf(219.5)));
    }

    @Test
    void returnsNullWhenWarmupHistoryIsInsufficient() {
        assertNull(indicator.calculate(candlesFrom(199), 200));
    }

    private List<Candle> candlesFrom(int count) {
        double[] values = new double[count];
        for (int index = 0; index < count; index++) values[index] = 100 + index;
        return candles(values);
    }

    private List<Candle> candles(double... closes) {
        List<Candle> candles = new ArrayList<>();
        for (double close : closes) {
            Candle candle = new Candle();
            candle.setOpen(BigDecimal.valueOf(close));
            candle.setHigh(BigDecimal.valueOf(close + 1));
            candle.setLow(BigDecimal.valueOf(close - 1));
            candle.setClose(BigDecimal.valueOf(close));
            candle.setVolume(BigDecimal.ONE);
            candles.add(candle);
        }
        return candles;
    }
}