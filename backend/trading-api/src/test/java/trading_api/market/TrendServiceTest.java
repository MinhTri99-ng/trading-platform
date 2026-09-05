package trading_api.market;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrendServiceTest {

    private final TrendService trendService =
            new TrendService();

    // =========================================================
    // BULLISH
    // =========================================================

    @Test
    void shouldReturnBullish() {

        List<Candle> candles = createBullishDataset();

        TrendService.TrendResult result =
                trendService.analyze(candles);

        System.out.println();
        System.out.println("========== BULLISH TEST ==========");

        System.out.println(
                "EMA50  = " + result.ema50()
        );

        System.out.println(
                "EMA200 = " + result.ema200()
        );

        System.out.println(
                "Trend  = " + result.trend()
        );

        assertEquals(
                TrendService.TrendDirection.BULLISH,
                result.trend()
        );
    }

    private List<Candle> createBullishDataset() {

        List<Candle> candles =
                new ArrayList<>();

        /*
         * Cấu trúc tăng thật sự theo kiểu:
         * swing highs mới cao hơn highs trước đó
         * swing lows mới cao hơn lows trước đó
         *
         * Điều này đảm bảo MarketStructureService phát hiện
         * HH + HL và TrendService trả về BULLISH.
         */
        double[] highs = {
                102, 103, 105, 103, 102,
                108, 110, 108, 107, 106,
                114, 116, 114, 113, 112
        };

        double[] lows = {
                98, 99, 100, 99, 98,
                101, 103, 102, 101, 100,
                106, 108, 107, 106, 105
        };

        for (int block = 0; block < 15; block++) {

            double offset = block * 8.0;

            for (int i = 0; i < highs.length; i++) {

                candles.add(
                        createCandle(
                                highs[i] + offset,
                                lows[i] + offset
                        )
                );
            }
        }

        return candles;
    }


    // =========================================================
    // BEARISH
    // =========================================================

    @Test
    void shouldReturnBearish() {

        List<Candle> candles =
                createBearishDataset();

        TrendService.TrendResult result =
                trendService.analyze(candles);

        System.out.println();
        System.out.println("========== BEARISH TEST ==========");

        System.out.println(
                "EMA50  = " + result.ema50()
        );

        System.out.println(
                "EMA200 = " + result.ema200()
        );

        System.out.println(
                "Trend  = " + result.trend()
        );

        assertEquals(
                TrendService.TrendDirection.BEARISH,
                result.trend()
        );
    }

    private List<Candle> createBearishDataset() {

        List<Candle> candles =
                new ArrayList<>();

        /*
         * Cấu trúc giảm thật sự theo kiểu:
         * swing highs mới thấp hơn highs trước đó
         * swing lows mới thấp hơn lows trước đó
         *
         * Điều này đảm bảo MarketStructureService phát hiện
         * LH + LL và TrendService trả về BEARISH.
         */
        double[] highs = {
                102, 103, 105, 103, 102,
                108, 110, 108, 107, 106,
                114, 116, 114, 113, 112
        };

        double[] lows = {
                98, 99, 100, 99, 98,
                101, 103, 102, 101, 100,
                106, 108, 107, 106, 105
        };

        double baseHigh = 220.0;
        double baseLow = 200.0;

        for (int block = 0; block < 15; block++) {

            double offset = block * 8.0;

            for (int i = 0; i < highs.length; i++) {

                candles.add(
                        createCandle(
                                baseHigh - offset - highs[i],
                                baseLow - offset - lows[i]
                        )
                );
            }
        }

        return candles;
    }


    // =========================================================
    // SIDEWAYS
    // =========================================================

    @Test
    void shouldReturnSideways() {

        List<Candle> candles =
                createSidewaysDataset();

        TrendService.TrendResult result =
                trendService.analyze(candles);

        System.out.println();
        System.out.println("========== SIDEWAYS TEST ==========");

        System.out.println(
                "EMA50  = " + result.ema50()
        );

        System.out.println(
                "EMA200 = " + result.ema200()
        );

        System.out.println(
                "Trend  = " + result.trend()
        );

        assertEquals(
                TrendService.TrendDirection.SIDEWAYS,
                result.trend()
        );
    }

    private List<Candle> createSidewaysDataset() {

        List<Candle> candles =
                new ArrayList<>();

        /*
         * Giá đi ngang quanh vùng 100.
         *
         * Không tạo higher high / higher low.
         * Không tạo lower high / lower low.
         *
         * EMA50 và EMA200 gần như bằng nhau.
         */
        for (int i = 0; i < 220; i++) {

            double price = 100.0;

            candles.add(
                    createCandle(
                            price,
                            price + 1,
                            price - 1,
                            price
                    )
            );
        }

        return candles;
    }


    // =========================================================
    // CANDLE FACTORY
    // =========================================================

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

    private Candle createCandle(
            double open,
            double high,
            double low,
            double close
    ) {

        Candle candle = new Candle();

        candle.setOpen(
                BigDecimal.valueOf(open)
        );

        candle.setHigh(
                BigDecimal.valueOf(high)
        );

        candle.setLow(
                BigDecimal.valueOf(low)
        );

        candle.setClose(
                BigDecimal.valueOf(close)
        );

        candle.setVolume(
                BigDecimal.ONE
        );

        return candle;
    }
}