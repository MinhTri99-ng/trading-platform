package trading_api.market;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtrServiceTest {

    private final AtrService atrService = new AtrService();

    @Test
    void shouldCalculateAtr14() {
        List<Candle> candles = createAtrCandles();

        BigDecimal atr = atrService.calculate(candles);

        assertEquals(
                0,
                atr.compareTo(new BigDecimal("4"))
        );
    }

    @Test
    void shouldThrowForInsufficientCandles() {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < 14; i++) {
            candles.add(createCandle(
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(100 + i)
            ));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> atrService.calculate(candles)
        );
    }

    @Test
    void shouldThrowForInvalidCandleValues() {
        Candle candle = createCandle(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(110),
                BigDecimal.valueOf(120),
                BigDecimal.valueOf(105)
        );

        candle.setHigh(BigDecimal.valueOf(90));

        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < 15; i++) {
            candles.add(candle);
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> atrService.calculate(candles)
        );
    }

    private List<Candle> createAtrCandles() {
        List<Candle> candles = new ArrayList<>();

        BigDecimal previousClose = BigDecimal.valueOf(100);

        for (int i = 0; i < 15; i++) {
            Candle candle = new Candle();
            candle.setOpen(previousClose);
            candle.setHigh(previousClose.add(BigDecimal.valueOf(2)));
            candle.setLow(previousClose.subtract(BigDecimal.valueOf(2)));
            candle.setClose(previousClose.add(BigDecimal.valueOf(1)));
            candles.add(candle);
            previousClose = candle.getClose();
        }

        return candles;
    }

    private Candle createCandle(
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close
    ) {
        Candle candle = new Candle();
        candle.setOpen(open);
        candle.setHigh(high);
        candle.setLow(low);
        candle.setClose(close);
        candle.setVolume(BigDecimal.valueOf(1000));
        return candle;
    }
}
