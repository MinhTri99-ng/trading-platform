package trading_api.market;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetestServiceTest {

    private final RetestService retestService =
            new RetestService();

    private static final BigDecimal SUPPORT =
            BigDecimal.valueOf(90);

    private static final BigDecimal RESISTANCE =
            BigDecimal.valueOf(110);

    private static final BigDecimal ATR =
            BigDecimal.valueOf(2);


    // =========================================================
    // NONE
    // =========================================================

    @Test
    void shouldReturnNoneWhenNoBreakout() {

        List<Candle> candles =
                createNoBreakoutCandles();

        RetestService.RetestResult result =
                retestService.analyze(
                        candles,
                        ATR
                );

        System.out.println(
                "NONE = " + result.state()
        );

        assertEquals(
                SetupState.NONE,
                result.state()
        );
    }


    // =========================================================
    // BREAKOUT
    // =========================================================

    @Test
    void shouldDetectBullishBreakout() {

        List<Candle> candles =
                createBaseCandles();

        /*
         * Resistance = 110
         * ATR buffer = 0.4
         *
         * Close 112 > 110.4
         */

        candles.add(
                candle(
                        109,
                        113,
                        108,
                        112
                )
        );

        RetestService.RetestResult result =
                retestService.analyze(
                        candles,
                        ATR
                );

        System.out.println(
                "BREAKOUT = " + result.state()
        );

        assertEquals(
                SetupState.BREAKOUT,
                result.state()
        );
    }


    // =========================================================
    // WAITING RETEST
    // =========================================================

    @Test
    void shouldWaitForRetest() {

        List<Candle> candles =
                createBaseCandles();

        /*
         * Candle 20 = breakout
         */
        candles.add(
                candle(
                        109,
                        113,
                        108,
                        112
                )
        );

        /*
         * Candle 21:
         * giá vẫn ở trên resistance,
         * chưa quay lại test.
         */
        candles.add(
                candle(
                        112,
                        113.2,
                        111.2,
                        112.5
                )
        );

        RetestService.RetestResult result =
                retestService.analyzeAfterBreakout(
                        candles,
                        20,
                        RetestService.BreakoutDirection.BULLISH,
                        SUPPORT,
                        RESISTANCE,
                        ATR
                );

        System.out.println(
                "WAITING = " + result.state()
        );

        assertEquals(
                SetupState.WAITING_RETEST,
                result.state()
        );
    }


    // =========================================================
    // RETEST
    // =========================================================

    @Test
    void shouldDetectBullishRetest() {

        List<Candle> candles =
                createBaseCandles();

        /*
         * Breakout candle
         */
        candles.add(
                candle(
                        109,
                        113,
                        108,
                        112
                )
        );

        /*
         * Retest:
         *
         * Resistance = 110
         * Buffer = 0.4
         *
         * Low = 109.8
         * Close = 109.9
         *
         * => nằm trong vùng retest.
         */
        candles.add(
                candle(
                        110.8,
                        111.0,
                        109.6,
                        109.8
                )
        );

        RetestService.RetestResult result =
                retestService.analyzeAfterBreakout(
                        candles,
                        20,
                        RetestService.BreakoutDirection.BULLISH,
                        SUPPORT,
                        RESISTANCE,
                        ATR
                );

        System.out.println(
                "RETEST = " + result.state()
        );

        assertEquals(
                SetupState.RETEST,
                result.state()
        );
    }


    // =========================================================
    // CONFIRMED
    // =========================================================

    @Test
    void shouldConfirmBullishRetest() {

        List<Candle> candles =
                createBaseCandles();

        /*
         * Breakout
         */
        candles.add(
                candle(
                        109,
                        113,
                        108,
                        112
                )
        );

        /*
         * Retest + bullish confirmation
         *
         * Low chạm vùng 110
         * Close > Resistance
         * Close > Open
         */
        candles.add(
                candle(
                        109.8,
                        112,
                        109.7,
                        111.5
                )
        );

        RetestService.RetestResult result =
                retestService.analyzeAfterBreakout(
                        candles,
                        20,
                        RetestService.BreakoutDirection.BULLISH,
                        SUPPORT,
                        RESISTANCE,
                        ATR
                );

        System.out.println(
                "CONFIRMED = " + result.state()
        );

        assertEquals(
                SetupState.CONFIRMED,
                result.state()
        );
    }


    // =========================================================
    // INVALIDATED
    // =========================================================

    @Test
    void shouldInvalidateBullishBreakout() {

        List<Candle> candles =
                createBaseCandles();

        /*
         * Breakout
         */
        candles.add(
                candle(
                        109,
                        113,
                        108,
                        112
                )
        );

        /*
         * Giá rơi mạnh dưới:
         *
         * 110 - 0.4 = 109.6
         *
         * Close = 108
         */
        candles.add(
                candle(
                        110,
                        111,
                        107,
                        108
                )
        );

        RetestService.RetestResult result =
                retestService.analyzeAfterBreakout(
                        candles,
                        20,
                        RetestService.BreakoutDirection.BULLISH,
                        SUPPORT,
                        RESISTANCE,
                        ATR
                );

        System.out.println(
                "INVALIDATED = " + result.state()
        );

        assertEquals(
                SetupState.NONE,
                result.state()
        );
    }


    // =========================================================
    // BEARISH BREAKOUT
    // =========================================================

    @Test
    void shouldDetectBearishBreakout() {

        List<Candle> candles =
                createBearishBaseCandles();

        candles.add(
                candle(
                        91,
                        92,
                        86,
                        87
                )
        );

        RetestService.RetestResult result =
                retestService.analyze(
                        candles,
                        ATR
                );

        System.out.println(
                "BEARISH BREAKOUT = "
                        + result.state()
        );

        assertEquals(
                SetupState.BREAKOUT,
                result.state()
        );

        assertEquals(
                RetestService.BreakoutDirection.BEARISH,
                result.direction()
        );
    }


    // =========================================================
    // BEARISH RETEST
    // =========================================================

    @Test
    void shouldDetectBearishRetest() {

        List<Candle> candles =
                createBearishBaseCandles();

        /*
         * Breakout
         */
        candles.add(
                candle(
                        91,
                        92,
                        86,
                        87
                )
        );

        /*
         * Retest Support = 90
         *
         * Giá quay lên vùng 90.
         */
        candles.add(
                candle(
                        88.5,
                        90.2,
                        88,
                        90.1
                )
        );

        RetestService.RetestResult result =
                retestService.analyzeAfterBreakout(
                        candles,
                        20,
                        RetestService.BreakoutDirection.BEARISH,
                        SUPPORT,
                        RESISTANCE,
                        ATR
                );

        System.out.println(
                "BEARISH RETEST = "
                        + result.state()
        );

        assertEquals(
                SetupState.RETEST,
                result.state()
        );
    }


    // =========================================================
    // BEARISH CONFIRMED
    // =========================================================

    @Test
    void shouldConfirmBearishRetest() {

        List<Candle> candles =
                createBearishBaseCandles();

        /*
         * Breakout
         */
        candles.add(
                candle(
                        91,
                        92,
                        86,
                        87
                )
        );

        /*
         * Retest + bearish confirmation
         */
        candles.add(
                candle(
                        90.2,
                        90.3,
                        87,
                        88
                )
        );

        RetestService.RetestResult result =
                retestService.analyzeAfterBreakout(
                        candles,
                        20,
                        RetestService.BreakoutDirection.BEARISH,
                        SUPPORT,
                        RESISTANCE,
                        ATR
                );

        System.out.println(
                "BEARISH CONFIRMED = "
                        + result.state()
        );

        assertEquals(
                SetupState.CONFIRMED,
                result.state()
        );
    }


    // =========================================================
    // HELPERS
    // =========================================================

    private List<Candle> createBaseCandles() {

        List<Candle> candles =
                new ArrayList<>();

        for (int i = 0; i < 20; i++) {

            candles.add(
                    candle(
                            100,
                            110,
                            90,
                            100
                    )
            );
        }

        return candles;
    }

    private List<Candle> createNoBreakoutCandles() {

        List<Candle> candles =
                createBaseCandles();

        candles.add(
                candle(
                        100,
                        110,
                        90,
                        100
                )
        );

        return candles;
    }


    private List<Candle> createBearishBaseCandles() {

        List<Candle> candles =
                new ArrayList<>();

        for (int i = 0; i < 20; i++) {

            candles.add(
                    candle(
                            100,
                            110,
                            90,
                            100
                    )
            );
        }

        return candles;
    }


    private Candle candle(
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