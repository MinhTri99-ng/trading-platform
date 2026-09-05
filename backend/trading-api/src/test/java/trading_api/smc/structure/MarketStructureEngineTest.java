package trading_api.smc.structure;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketStructureEngineTest {

    @Test
    void shouldDetectBullishStructure() {
        MarketStructureEngine engine = new MarketStructureEngine();

        List<Candle> candles = createBullishSequence();
        SMCMarketStructureResult result = engine.analyze(candles);

        assertEquals("BULLISH", result.trend());
        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.HH));
        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.HL));
    }

    @Test
    void shouldDetectBearishStructure() {
        MarketStructureEngine engine = new MarketStructureEngine();

        List<Candle> candles = createBearishSequence();
        SMCMarketStructureResult result = engine.analyze(candles);

        assertEquals("BEARISH", result.trend());
        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.LH));
        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.LL));
    }

    @Test
    void shouldClassifyHHAndHL() {
        MarketStructureEngine engine = new MarketStructureEngine();

        List<Candle> candles = createBullishSequence();
        SMCMarketStructureResult result = engine.analyze(candles);

        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.HH));
        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.HL));
    }

    @Test
    void shouldClassifyLHAndLL() {
        MarketStructureEngine engine = new MarketStructureEngine();

        List<Candle> candles = createBearishSequence();
        SMCMarketStructureResult result = engine.analyze(candles);

        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.LH));
        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.LL));
    }

    @Test
    void shouldIgnoreNoiseCandles() {
        MarketStructureEngine engine = new MarketStructureEngine();
        List<Candle> candles = createNoisySequence();

        SMCMarketStructureResult result = engine.analyze(candles, new MarketStructureConfig(3, 0.35, 0.4, 0.2, 5, 2));

        assertFalse(result.swings().isEmpty());
        assertTrue(result.swings().stream().allMatch(swing -> swing.strength().compareTo(BigDecimal.ZERO) >= 0));
    }

    @Test
    void shouldReturnEmptyForInsufficientCandles() {
        MarketStructureEngine engine = new MarketStructureEngine();

        List<Candle> candles = createShortSequence();
        SMCMarketStructureResult result = engine.analyze(candles);

        assertTrue(result.swings().isEmpty());
        assertTrue(result.labels().isEmpty());
        assertEquals("RANGE", result.trend());
    }

    @Test
    void shouldHandleEqualHighsAndLows() {
        MarketStructureEngine engine = new MarketStructureEngine();

        List<Candle> candles = createEqualSequence();
        SMCMarketStructureResult result = engine.analyze(candles);

        assertNotNull(result);
        assertTrue(result.swings().stream().allMatch(swing -> swing.price().compareTo(BigDecimal.ZERO) > 0));
    }

    @Test
    void shouldDifferentiateStrongAndWeakSwing() {
        MarketStructureEngine engine = new MarketStructureEngine();

        List<Candle> candles = createStrongAndWeakSwingSequence();
        SMCMarketStructureResult result = engine.analyze(candles);

        assertTrue(result.swings().stream().anyMatch(swing -> swing.strength().compareTo(BigDecimal.valueOf(50)) > 0));
        assertTrue(result.swings().stream().anyMatch(swing -> swing.strength().compareTo(BigDecimal.valueOf(80)) < 0));
    }

    @Test
    void shouldDetectInternalAndExternalStructure() {
        MarketStructureEngine engine = new MarketStructureEngine();

        List<Candle> candles = createInternalExternalSequence();
        SMCMarketStructureResult result = engine.analyze(candles);

        assertFalse(result.externalStructure().isEmpty());
        assertFalse(result.internalStructure().isEmpty());
    }

    @Test
    void shouldDetectProtectedHigh() {
        MarketStructureEngine engine = new MarketStructureEngine();

        List<Candle> candles = createProtectedHighSequence();
        SMCMarketStructureResult result = engine.analyze(candles);

        assertTrue(result.protectedHighs().stream().anyMatch(level -> level.price().compareTo(BigDecimal.ZERO) > 0));
    }

    @Test
    void shouldDetectProtectedLow() {
        MarketStructureEngine engine = new MarketStructureEngine();

        List<Candle> candles = createProtectedLowSequence();
        SMCMarketStructureResult result = engine.analyze(candles);

        assertTrue(result.protectedLows().stream().anyMatch(level -> level.price().compareTo(BigDecimal.ZERO) > 0));
    }

    private List<Candle> createBullishSequence() {
        List<Candle> candles = new ArrayList<>();
        double[] highs = {102, 104, 101, 105, 103, 109, 106, 112, 108, 116, 110, 118};
        double[] lows = {98, 100, 97, 101, 99, 104, 102, 107, 103, 111, 105, 113};
        for (int i = 0; i < highs.length; i++) {
            candles.add(createCandle(Instant.now().plusSeconds(i * 60L), highs[i], lows[i]));
        }
        return candles;
    }

    private List<Candle> createBearishSequence() {
        List<Candle> candles = new ArrayList<>();
        double[] highs = {118, 114, 116, 109, 112, 106, 109, 102, 104, 98, 101, 96};
        double[] lows = {113, 109, 111, 103, 107, 100, 104, 96, 99, 92, 95, 90};
        for (int i = 0; i < highs.length; i++) {
            candles.add(createCandle(Instant.now().plusSeconds(i * 60L), highs[i], lows[i]));
        }
        return candles;
    }

    private List<Candle> createNoisySequence() {
        List<Candle> candles = new ArrayList<>();
        double[] highs = {100, 100.2, 99.7, 100.4, 100.1, 100.5, 100.3, 100.2, 99.9, 100.6, 100.8, 100.2};
        double[] lows = {99.1, 99.2, 98.9, 99.3, 99.0, 99.5, 99.2, 99.1, 99.0, 99.4, 99.8, 99.1};
        for (int i = 0; i < highs.length; i++) {
            candles.add(createCandle(Instant.now().plusSeconds(i * 60L), highs[i], lows[i]));
        }
        return candles;
    }

    private List<Candle> createShortSequence() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            candles.add(createCandle(Instant.now().plusSeconds(i * 60L), 100 + i, 95 + i));
        }
        return candles;
    }

    private List<Candle> createEqualSequence() {
        List<Candle> candles = new ArrayList<>();
        double[] highs = {100, 100.5, 101, 101, 100.7, 101, 100.8, 101};
        double[] lows = {99, 99.4, 100, 100, 99.5, 100, 99.7, 100};
        for (int i = 0; i < highs.length; i++) {
            candles.add(createCandle(Instant.now().plusSeconds(i * 60L), highs[i], lows[i]));
        }
        return candles;
    }

    private List<Candle> createStrongAndWeakSwingSequence() {
        List<Candle> candles = new ArrayList<>();
        double[] highs = {100, 102, 99, 106, 98, 110, 99, 108, 100, 114, 101, 112};
        double[] lows = {96, 98, 95, 101, 94, 105, 95, 103, 96, 109, 97, 107};
        for (int i = 0; i < highs.length; i++) {
            candles.add(createCandle(Instant.now().plusSeconds(i * 60L), highs[i], lows[i]));
        }
        return candles;
    }

    private List<Candle> createInternalExternalSequence() {
        List<Candle> candles = new ArrayList<>();
        double[] highs = {100, 103, 101, 106, 102, 109, 104, 110, 108, 112, 109, 115};
        double[] lows = {95, 98, 96, 101, 98, 104, 100, 105, 103, 107, 104, 110};
        for (int i = 0; i < highs.length; i++) {
            candles.add(createCandle(Instant.now().plusSeconds(i * 60L), highs[i], lows[i]));
        }
        return candles;
    }

    private List<Candle> createProtectedHighSequence() {
        List<Candle> candles = new ArrayList<>();
        double[] highs = {100, 101, 103, 102, 105, 104, 108, 107, 110, 109, 111, 108};
        double[] lows = {95, 96, 98, 97, 100, 99, 103, 102, 105, 104, 106, 103};
        for (int i = 0; i < highs.length; i++) {
            candles.add(createCandle(Instant.now().plusSeconds(i * 60L), highs[i], lows[i]));
        }
        return candles;
    }

    private List<Candle> createProtectedLowSequence() {
        List<Candle> candles = new ArrayList<>();
        double[] highs = {110, 111, 109, 113, 112, 108, 106, 105, 107, 104, 102, 101};
        double[] lows = {100, 101, 99, 103, 102, 98, 96, 95, 97, 94, 92, 90};
        for (int i = 0; i < highs.length; i++) {
            candles.add(createCandle(Instant.now().plusSeconds(i * 60L), highs[i], lows[i]));
        }
        return candles;
    }

    private Candle createCandle(Instant timestamp, double high, double low) {
        Candle candle = new Candle();
        candle.setTimestamp(timestamp);
        candle.setSymbol("BTCUSDT");
        candle.setTimeframe("1h");
        candle.setOpen(BigDecimal.valueOf((high + low) / 2.0 - 0.5));
        candle.setHigh(BigDecimal.valueOf(high));
        candle.setLow(BigDecimal.valueOf(low));
        candle.setClose(BigDecimal.valueOf((high + low) / 2.0));
        candle.setVolume(BigDecimal.valueOf(1000));
        return candle;
    }
}
