package trading_api.smc.structure;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class M15MarketStructureServiceTest {
    private final M15MarketStructureService service = new M15MarketStructureService();
    private final M15MarketStructureConfig testConfig = new M15MarketStructureConfig(8, 2, 2, 0.5, 1);

    @Test
    void detectsBullishBiasAndBullishBosFromMajorStructure() {
        M15MarketStructureResult result = service.analyze(bullishCandles(116), testConfig);

        assertEquals(DirectionalBias.BULLISH, result.directionalBias());
        assertTrue(result.structuralBox().present());
        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.HH));
        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.HL));
        assertTrue(result.breaks().stream().anyMatch(breakEvent -> breakEvent.type() == StructureBreak.BreakType.BOS
                && breakEvent.direction() == SwingPoint.SwingDirection.HIGH));
    }

    @Test
    void detectsBearishBiasAndBearishBosFromMajorStructure() {
        M15MarketStructureResult result = service.analyze(bearishCandles(84), testConfig);

        assertEquals(DirectionalBias.BEARISH, result.directionalBias());
        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.LH));
        assertTrue(result.labels().stream().anyMatch(label -> label.type() == StructureType.LL));
        assertTrue(result.breaks().stream().anyMatch(breakEvent -> breakEvent.type() == StructureBreak.BreakType.BOS
                && breakEvent.direction() == SwingPoint.SwingDirection.LOW));
    }

    @Test
    void oppositeCloseBreakIsChoch() {
        M15MarketStructureResult result = service.analyze(bullishCandles(90), testConfig);

        assertEquals(DirectionalBias.BULLISH, result.directionalBias());
        assertTrue(result.breaks().stream().anyMatch(breakEvent -> breakEvent.type() == StructureBreak.BreakType.CHOCH
                && breakEvent.direction() == SwingPoint.SwingDirection.LOW));
    }

    @Test
    void wickThroughMajorLevelWithoutCloseIsNotBreak() {
        M15MarketStructureResult result = service.analyze(bullishCandles(116, 114.5), testConfig);

        assertTrue(result.breaks().isEmpty());
    }

    @Test
    void internalBreakDoesNotBecomeMajorBosOrChoch() {
        M15MarketStructureResult result = service.analyze(internalBreakCandles(),
                new M15MarketStructureConfig(8, 2, 2, 3.0, 1));

        assertEquals(DirectionalBias.BULLISH, result.directionalBias());
        assertTrue(result.swings().stream().anyMatch(swing -> !swing.external()));
        assertTrue(result.breaks().isEmpty());
    }

    @Test
    void insufficientCandlesReturnRangeWithoutStructure() {
        List<Candle> candles = List.of(candle(100, 95, 98));

        M15MarketStructureResult result = service.analyze(candles, testConfig);

        assertEquals(DirectionalBias.RANGE, result.directionalBias());
        assertFalse(result.structuralBox().present());
        assertTrue(result.breaks().isEmpty());
    }

    private List<Candle> bullishCandles(double finalClose) {
        return bullishCandles(finalClose, finalClose);
    }

    private List<Candle> bullishCandles(double finalHigh, double finalClose) {
        double[][] values = {
                {100, 95, 98}, {103, 97, 100}, {110, 99, 108}, {105, 96, 100},
                {104, 94, 98}, {108, 97, 102}, {109, 98, 104}, {115, 99, 112},
                {111, 101, 106}, {110, 98, 104}, {112, 100, 108}, {113, 101, 110},
                {finalHigh, 102, finalClose}
        };
        return candles(values);
    }

    private List<Candle> bearishCandles(double finalClose) {
        double[][] values = {
                {115, 108, 112}, {113, 105, 109}, {116, 106, 112}, {110, 100, 104},
                {112, 96, 100}, {103, 98, 100}, {102, 99, 100}, {104, 90, 94},
                {103, 94, 98}, {102, 92, 96}, {103, 93, 97}, {100, 88, 92},
                {95, 84, finalClose}
        };
        return candles(values);
    }

    private List<Candle> internalBreakCandles() {
        double[][] values = {
                {100, 95, 98}, {103, 97, 100}, {110, 99, 108}, {105, 96, 100},
                {104, 94, 98}, {105, 97, 101}, {106, 98, 103}, {115, 99, 112},
                {111, 101, 106}, {110, 95, 98}, {113, 100, 108}, {112, 101, 110},
                {112, 102, 111}, {114, 102, 113}
        };
        return candles(values);
    }

    private List<Candle> candles(double[][] values) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            candles.add(candle(values[i][0], values[i][1], values[i][2], i));
        }
        return candles;
    }

    private Candle candle(double high, double low, double close) {
        return candle(high, low, close, 0);
    }

    private Candle candle(double high, double low, double close, int index) {
        Candle candle = new Candle();
        candle.setSymbol("BTCUSDT");
        candle.setTimeframe("15m");
        candle.setTimestamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index * 900L));
        candle.setOpen(BigDecimal.valueOf((high + low) / 2));
        candle.setHigh(BigDecimal.valueOf(high));
        candle.setLow(BigDecimal.valueOf(low));
        candle.setClose(BigDecimal.valueOf(close));
        candle.setVolume(BigDecimal.ONE);
        return candle;
    }
}