package trading_api.market;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreakoutServiceTest {

    private final BreakoutService breakoutService =
            new BreakoutService();

    private static final BigDecimal ATR =
            BigDecimal.valueOf(2.0);


    // =========================================================
    // NORMAL BREAKOUT
    // =========================================================

    @Test
    void shouldDetectBullishBreakout() {

        List<Candle> candles =
                createBaseDataset();

        /*
         * Resistance khoảng 110.
         *
         * ATR = 2
         * Buffer = 0.4
         *
         * Breakout cần close > 110.4
         */

        candles.add(
                createCandle(
                        109,
                        113,
                        108,
                        112
                )
        );

        BreakoutService.BreakoutResult result =
                breakoutService.analyze(
                        candles,
                        ATR
                );

        System.out.println();
        System.out.println(
                "========== BULLISH BREAKOUT =========="
        );

        System.out.println(
                "Support    = " + result.support()
        );

        System.out.println(
                "Resistance = " + result.resistance()
        );

        System.out.println(
                "ATR Buffer = " + result.atrBuffer()
        );

        System.out.println(
                "Close      = " + result.close()
        );

        System.out.println(
                "Result     = " + result.type()
        );

        assertEquals(
                BreakoutService.BreakoutType.BULLISH_BREAKOUT,
                result.type()
        );
    }


    // =========================================================
    // FAKE BREAKOUT
    // =========================================================

    @Test
    void shouldDetectFakeBullishBreakout() {

        List<Candle> candles =
                createBaseDataset();

        /*
         * Resistance khoảng 110.
         *
         * High chọc lên trên vùng breakout.
         * Nhưng Close quay xuống dưới resistance.
         */

        candles.add(
                createCandle(
                        109,
                        111,
                        107,
                        109
                )
        );

        BreakoutService.BreakoutResult result =
                breakoutService.analyze(
                        candles,
                        ATR
                );

        System.out.println();
        System.out.println(
                "========== FAKE BREAKOUT =========="
        );

        System.out.println(
                "Support    = " + result.support()
        );

        System.out.println(
                "Resistance = " + result.resistance()
        );

        System.out.println(
                "ATR Buffer = " + result.atrBuffer()
        );

        System.out.println(
                "Close      = " + result.close()
        );

        System.out.println(
                "Result     = " + result.type()
        );

        assertEquals(
                BreakoutService.BreakoutType
                        .FAKE_BULLISH_BREAKOUT,
                result.type()
        );
    }


    // =========================================================
    // NO BREAKOUT
    // =========================================================

    @Test
    void shouldReturnNoBreakout() {

        List<Candle> candles =
                createBaseDataset();

        /*
         * Giá vẫn nằm trong range.
         */

        candles.add(
                createCandle(
                        105,
                        107,
                        103,
                        106
                )
        );

        BreakoutService.BreakoutResult result =
                breakoutService.analyze(
                        candles,
                        ATR
                );

        System.out.println();
        System.out.println(
                "========== NO BREAKOUT =========="
        );

        System.out.println(
                "Support    = " + result.support()
        );

        System.out.println(
                "Resistance = " + result.resistance()
        );

        System.out.println(
                "ATR Buffer = " + result.atrBuffer()
        );

        System.out.println(
                "Close      = " + result.close()
        );

        System.out.println(
                "Result     = " + result.type()
        );

        assertEquals(
                BreakoutService.BreakoutType.NO_BREAKOUT,
                result.type()
        );
    }


    // =========================================================
    // BASE DATASET
    // =========================================================

    private List<Candle> createBaseDataset() {

        List<Candle> candles =
                new ArrayList<>();

        /*
         * 20 candle tạo range:
         *
         * Support    ≈ 90
         * Resistance ≈ 110
         */
        for (int i = 0; i < 20; i++) {

            candles.add(
                    createCandle(
                            100,
                            110,
                            90,
                            100
                    )
            );
        }

        /*
         * Candle trước candle hiện tại.
         *
         * Giúp engine biết giá đang ở trong range
         * trước khi breakout.
         */
        candles.add(
                createCandle(
                        108,
                        110,
                        107,
                        109
                )
        );

        return candles;
    }


    // =========================================================
    // CANDLE FACTORY
    // =========================================================

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