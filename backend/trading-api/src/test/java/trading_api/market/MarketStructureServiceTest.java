package trading_api.market;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketStructureServiceTest {

    @Test
    void shouldDetectBullishStructure() {

        MarketStructureService service =
                new MarketStructureService();

        List<Candle> candles =
                createBullishCandles();

        MarketStructureService.MarketStructureResult result =
                service.analyze(candles);

        System.out.println(
                "TREND = " + result.trend()
        );

        result.labels().forEach(label ->
                System.out.println(
                        label.type()
                                + " | price="
                                + label.price()
                )
        );

        assertEquals(
                MarketStructureService.MarketTrend.BULLISH,
                result.trend()
        );
    }

    private List<Candle> createBullishCandles() {

        List<Candle> candles =
                new ArrayList<>();

        double[] highs = {
                102,
                103,
                105,
                103,
                102,

                108,
                110,
                108,
                107,
                106,

                114,
                116,
                114,
                113,
                112
        };

        double[] lows = {
                98,
                99,
                100,
                99,
                98,

                101,
                103,
                102,
                101,
                100,

                106,
                108,
                107,
                106,
                105
        };

        for (int i = 0; i < highs.length; i++) {

            candles.add(
                    createCandle(
                            highs[i],
                            lows[i]
                    )
            );
        }

        return candles;
    }

    private Candle createCandle(
            double high,
            double low
    ) {

        Candle candle = new Candle();

        candle.setOpen(
                BigDecimal.valueOf(low + 0.5)
        );

        candle.setHigh(
                BigDecimal.valueOf(high)
        );

        candle.setLow(
                BigDecimal.valueOf(low)
        );

        candle.setClose(
                BigDecimal.valueOf(high - 0.5)
        );

        candle.setVolume(
                BigDecimal.ONE
        );

        return candle;
    }
}