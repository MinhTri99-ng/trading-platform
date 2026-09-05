package trading_api.market;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingStrategyServiceTest {

    private final TradingStrategyService strategyService =
            new TradingStrategyService();

    @Test
    void shouldReturnValidLongSignal() {
        List<Candle> candles = buildBullishBreakoutDataset();

        TradingStrategyService.TradingSignal signal =
                strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1)
                );

        assertEquals(
                TradingStrategyService.SignalDirection.LONG,
                signal.direction()
        );
        assertEquals(
                TradingStrategyService.SignalStatus.VALID,
                signal.status()
        );
        assertNotNull(signal.entry());
        assertEquals(
                BreakoutService.BreakoutType.BULLISH_BREAKOUT,
                signal.breakout()
        );
        assertEquals(
                SetupState.CONFIRMED,
                signal.setupState()
        );
        assertTrue(signal.stopLoss().compareTo(signal.entry()) < 0);
        assertTrue(signal.takeProfit().compareTo(signal.entry()) > 0);
        assertTrue(signal.positionSize().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void shouldReturnValidShortSignal() {
        List<Candle> candles = buildBearishBreakoutDataset();

        TradingStrategyService.TradingSignal signal =
                strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1)
                );

        System.out.println("SHORT TREND=" + signal.trend());
        System.out.println("SHORT BREAKOUT=" + signal.breakout());
        System.out.println("SHORT STATE=" + signal.setupState());
        System.out.println("SHORT ATR=" + signal.atr());
        System.out.println("SHORT ENTRY/SL/TP=" + signal.entry() + "/" + signal.stopLoss() + "/" + signal.takeProfit());
        System.out.println("SHORT VOLUME=" + signal.positionSize() + "/" + signal.riskAmount());

        assertEquals(
                TradingStrategyService.SignalDirection.SHORT,
                signal.direction()
        );
        assertEquals(
                TradingStrategyService.SignalStatus.VALID,
                signal.status()
        );
        assertNotNull(signal.entry());
        assertEquals(
                BreakoutService.BreakoutType.BEARISH_BREAKOUT,
                signal.breakout()
        );
        assertEquals(
                SetupState.CONFIRMED,
                signal.setupState()
        );
        assertTrue(signal.stopLoss().compareTo(signal.entry()) > 0);
        assertTrue(signal.takeProfit().compareTo(signal.entry()) < 0);
        assertTrue(signal.positionSize().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void shouldReturnNoTradeForSidewaysMarket() {
        List<Candle> candles = buildSidewaysDataset();

        TradingStrategyService.TradingSignal signal =
                strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1)
                );

        assertEquals(
                TradingStrategyService.SignalDirection.NONE,
                signal.direction()
        );
        assertEquals(
                TradingStrategyService.SignalStatus.NO_TRADE,
                signal.status()
        );
    }

    @Test
    void shouldReturnNoTradeWithoutBreakout() {
        List<Candle> candles = buildNoBreakoutDataset();

        TradingStrategyService.TradingSignal signal =
                strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1)
                );

        assertEquals(
                TradingStrategyService.SignalStatus.NO_TRADE,
                signal.status()
        );
    }

    @Test
    void shouldReturnNoTradeForFakeBreakout() {
        List<Candle> candles = buildFakeBreakoutDataset();

        TradingStrategyService.TradingSignal signal =
                strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1)
                );

        assertEquals(
                TradingStrategyService.SignalStatus.NO_TRADE,
                signal.status()
        );
    }

    @Test
    void shouldWaitForRetestWhenBreakoutExists() {
        List<Candle> candles = buildBreakoutWaitingForRetestDataset();

        TradingStrategyService.TradingSignal signal =
                strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1)
                );

        System.out.println("WAITING TREND=" + signal.trend());
        System.out.println("WAITING BREAKOUT=" + signal.breakout());
        System.out.println("WAITING STATE=" + signal.setupState());
        System.out.println("WAITING SIGNAL=" + signal.status());

        assertEquals(
                TradingStrategyService.SignalStatus.WAITING_RETEST,
                signal.status()
        );
    }

    @Test
    void shouldReturnNoTradeWhenVolumeIsTooLow() {
        List<Candle> candles = buildLowVolumeDataset();

        TradingStrategyService.TradingSignal signal =
                strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1)
                );

        assertEquals(
                TradingStrategyService.SignalStatus.NO_TRADE,
                signal.status()
        );
    }

    @Test
    void shouldRejectInsufficientCandleData() {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            candles.add(createCandle(
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(101),
                    BigDecimal.valueOf(99),
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(1000)
            ));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1)
                )
        );
    }

    @Test
    void shouldRejectInvalidRiskPercent() {
        List<Candle> candles = buildBullishBreakoutDataset();

        assertThrows(
                IllegalArgumentException.class,
                () -> strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(101)
                )
        );
    }

    @Test
    void shouldHandleDifferentAccountBalanceAndRiskPercent() {
        List<Candle> candles = buildBullishBreakoutDataset();

        TradingStrategyService.TradingSignal signal =
                strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(2000),
                        BigDecimal.valueOf(2)
                );

        System.out.println("RISK ACCOUNT=" + signal.riskAmount());
        System.out.println("RISK POSITION=" + signal.positionSize());
        System.out.println("RISK ATR=" + signal.atr());

        assertEquals(
                TradingStrategyService.SignalStatus.VALID,
                signal.status()
        );
        assertEquals(
                0,
                signal.riskAmount().compareTo(BigDecimal.valueOf(40))
        );
        assertTrue(signal.positionSize().compareTo(BigDecimal.ZERO) > 0);
        BigDecimal expectedRiskPerUnit = signal.riskAmount().divide(
                signal.positionSize(),
                20,
                java.math.RoundingMode.HALF_UP
        );
        assertTrue(expectedRiskPerUnit.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void shouldRespectRiskInvariant() {
        List<Candle> candles = buildBullishBreakoutDataset();

        TradingStrategyService.TradingSignal signal =
                strategyService.analyze(
                        candles,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(1)
                );

        System.out.println("INVARIANT RISK=" + signal.riskAmount());
        System.out.println("INVARIANT SIZE=" + signal.positionSize());
        System.out.println("INVARIANT ENTRY/SL=" + signal.entry() + "/" + signal.stopLoss());

        assertEquals(
                TradingStrategyService.SignalStatus.VALID,
                signal.status()
        );
        assertEquals(
                0,
                signal.riskAmount().compareTo(BigDecimal.valueOf(10))
        );
        assertTrue(signal.positionSize().compareTo(BigDecimal.ZERO) > 0);
        BigDecimal riskPerUnit = signal.riskAmount().divide(
                signal.positionSize(),
                20,
                java.math.RoundingMode.HALF_UP
        );
        assertTrue(riskPerUnit.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(
                signal.positionSize().multiply(riskPerUnit, new java.math.MathContext(20, java.math.RoundingMode.HALF_UP))
                        .compareTo(signal.riskAmount()) <= 0
        );
    }

    private List<Candle> buildBullishBreakoutDataset() {
        List<Candle> candles = new ArrayList<>();

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
                Candle candle = new Candle();
                candle.setOpen(BigDecimal.valueOf(lows[i] + offset + 0.5));
                candle.setHigh(BigDecimal.valueOf(highs[i] + offset));
                candle.setLow(BigDecimal.valueOf(lows[i] + offset));
                candle.setClose(BigDecimal.valueOf(highs[i] + offset - 0.5));
                candle.setVolume(BigDecimal.valueOf(1000));
                candles.add(candle);
            }
        }

        Candle breakoutCandle = createCandle(
                BigDecimal.valueOf(129.5),
                BigDecimal.valueOf(132.0),
                BigDecimal.valueOf(127.0),
                BigDecimal.valueOf(131.0),
                BigDecimal.valueOf(4000)
        );
        candles.add(breakoutCandle);

        Candle retestCandle = createCandle(
                BigDecimal.valueOf(129.0),
                BigDecimal.valueOf(132.2),
                BigDecimal.valueOf(127.8),
                BigDecimal.valueOf(131.4),
                BigDecimal.valueOf(4000)
        );
        candles.add(retestCandle);

        return candles;
    }

    private List<Candle> buildBearishBreakoutDataset() {
        List<Candle> candles = new ArrayList<>();

        double[] highs = {
                120, 118, 116, 114, 112,
                110, 108, 106, 104, 102,
                100, 98, 96, 94, 92
        };
        double[] lows = {
                100, 98, 96, 94, 92,
                90, 88, 86, 84, 82,
                80, 78, 76, 74, 72
        };

        for (int block = 0; block < 15; block++) {
            double offset = block * 5.0;
            for (int i = 0; i < highs.length; i++) {
                Candle candle = new Candle();
                double high = highs[i] - offset;
                double low = lows[i] - offset;
                candle.setOpen(BigDecimal.valueOf(high - 2.0));
                candle.setHigh(BigDecimal.valueOf(high));
                candle.setLow(BigDecimal.valueOf(low));
                candle.setClose(BigDecimal.valueOf(high - 1.5));
                candle.setVolume(BigDecimal.valueOf(1000));
                candles.add(candle);
            }
        }

        Candle breakoutCandle = createCandle(
                BigDecimal.valueOf(61.8),
                BigDecimal.valueOf(63.0),
                BigDecimal.valueOf(54.0),
                BigDecimal.valueOf(55.4),
                BigDecimal.valueOf(4000)
        );
        candles.add(breakoutCandle);

        Candle retestCandle = createCandle(
                BigDecimal.valueOf(57.8),
                BigDecimal.valueOf(58.6),
                BigDecimal.valueOf(55.0),
                BigDecimal.valueOf(56.2),
                BigDecimal.valueOf(4000)
        );
        candles.add(retestCandle);

        return candles;
    }

    private List<Candle> buildSidewaysDataset() {
        List<Candle> candles = new ArrayList<>();
        BigDecimal price = BigDecimal.valueOf(100);

        for (int i = 0; i < 25; i++) {
            candles.add(createCandle(
                    price,
                    price.add(BigDecimal.valueOf(1)),
                    price.subtract(BigDecimal.valueOf(1)),
                    price,
                    BigDecimal.valueOf(1000)
            ));
            price = price.add(BigDecimal.valueOf(0.1));
        }

        return candles;
    }

    private List<Candle> buildNoBreakoutDataset() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            Candle candle = createCandle(
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(101 + i),
                    BigDecimal.valueOf(99 + i),
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(1000)
            );
            candles.add(candle);
        }
        return candles;
    }

    private List<Candle> buildFakeBreakoutDataset() {
        List<Candle> candles = new ArrayList<>();
        BigDecimal price = BigDecimal.valueOf(100);

        for (int i = 0; i < 24; i++) {
            candles.add(createCandle(
                    price,
                    price.add(BigDecimal.valueOf(2)),
                    price.subtract(BigDecimal.valueOf(2)),
                    price,
                    BigDecimal.valueOf(1000)
            ));
            price = price.add(BigDecimal.valueOf(1));
        }

        candles.add(createCandle(
                BigDecimal.valueOf(109),
                BigDecimal.valueOf(111),
                BigDecimal.valueOf(108),
                BigDecimal.valueOf(109),
                BigDecimal.valueOf(3000)
        ));

        return candles;
    }

    private List<Candle> buildBreakoutWaitingForRetestDataset() {
        List<Candle> candles = new ArrayList<>();

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
                Candle candle = new Candle();
                candle.setOpen(BigDecimal.valueOf(lows[i] + offset + 0.5));
                candle.setHigh(BigDecimal.valueOf(highs[i] + offset));
                candle.setLow(BigDecimal.valueOf(lows[i] + offset));
                candle.setClose(BigDecimal.valueOf(highs[i] + offset - 0.5));
                candle.setVolume(BigDecimal.valueOf(1000));
                candles.add(candle);
            }
        }

        candles.add(createCandle(
                BigDecimal.valueOf(129.5),
                BigDecimal.valueOf(132.0),
                BigDecimal.valueOf(127.0),
                BigDecimal.valueOf(131.0),
                BigDecimal.valueOf(4000)
        ));

        candles.add(createCandle(
                BigDecimal.valueOf(131.6),
                BigDecimal.valueOf(134.0),
                BigDecimal.valueOf(130.8),
                BigDecimal.valueOf(133.1),
                BigDecimal.valueOf(4000)
        ));

        return candles;
    }

    private List<Candle> buildLowVolumeDataset() {
        List<Candle> candles = buildBullishBreakoutDataset();
        candles.forEach(candle -> candle.setVolume(BigDecimal.valueOf(10)));
        return candles;
    }

    private Candle createCandle(
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume
    ) {
        Candle candle = new Candle();
        candle.setOpen(open);
        candle.setHigh(high);
        candle.setLow(low);
        candle.setClose(close);
        candle.setVolume(volume);
        return candle;
    }
}
