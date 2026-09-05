package trading_api.market;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CandleAggregatorTest {

    private CandleAggregator aggregator;

    @BeforeEach
    void setUp() {
        // Instantiate without a repository for pure unit testing
        aggregator = new CandleAggregator(null);
    }

    private void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual, "Actual BigDecimal is null");
        assertEquals(0, expected.compareTo(actual), "Expected " + expected + " but got " + actual);
    }

    // 1. Open candle
    @Test
    void testOpenCandle() {
        NormalizedTrade trade = new NormalizedTrade(
                1L, "BTCUSDT", BigDecimal.valueOf(77400.0), BigDecimal.valueOf(1.5), Instant.parse("2026-08-23T10:00:05Z")
        );

        aggregator.process(trade);

        Candle candle1m = aggregator.getActiveCandle("BTCUSDT", "1m");
        assertNotNull(candle1m);
        assertEquals("BTCUSDT", candle1m.getSymbol());
        assertEquals("1m", candle1m.getTimeframe());
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), candle1m.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(77400.0), candle1m.getOpen());
        assertBigDecimalEquals(BigDecimal.valueOf(77400.0), candle1m.getHigh());
        assertBigDecimalEquals(BigDecimal.valueOf(77400.0), candle1m.getLow());
        assertBigDecimalEquals(BigDecimal.valueOf(77400.0), candle1m.getClose());
        assertBigDecimalEquals(BigDecimal.valueOf(1.5), candle1m.getVolume());
    }

    // 2. Update OHLCV
    @Test
    void testUpdateOHLCV() {
        Instant baseTime = Instant.parse("2026-08-23T10:00:00Z");

        aggregator.process(new NormalizedTrade(1L, "BTCUSDT", BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.0), baseTime.plusSeconds(5)));
        aggregator.process(new NormalizedTrade(2L, "BTCUSDT", BigDecimal.valueOf(105.0), BigDecimal.valueOf(2.0), baseTime.plusSeconds(10)));
        aggregator.process(new NormalizedTrade(3L, "BTCUSDT", BigDecimal.valueOf(95.0), BigDecimal.valueOf(3.0), baseTime.plusSeconds(15)));
        aggregator.process(new NormalizedTrade(4L, "BTCUSDT", BigDecimal.valueOf(102.0), BigDecimal.valueOf(1.5), baseTime.plusSeconds(20)));

        Candle candle = aggregator.getActiveCandle("BTCUSDT", "1m");
        assertNotNull(candle);
        assertBigDecimalEquals(BigDecimal.valueOf(100.0), candle.getOpen());
        assertBigDecimalEquals(BigDecimal.valueOf(105.0), candle.getHigh());
        assertBigDecimalEquals(BigDecimal.valueOf(95.0), candle.getLow());
        assertBigDecimalEquals(BigDecimal.valueOf(102.0), candle.getClose());
        assertBigDecimalEquals(BigDecimal.valueOf(7.5), candle.getVolume());
    }

    // 3. Close candle
    @Test
    void testCloseCandle() {
        NormalizedTrade trade1 = new NormalizedTrade(
                1L, "BTCUSDT", BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T10:00:50Z")
        );
        NormalizedTrade trade2 = new NormalizedTrade(
                2L, "BTCUSDT", BigDecimal.valueOf(102.0), BigDecimal.valueOf(2.0), Instant.parse("2026-08-23T10:01:05Z")
        );

        aggregator.process(trade1);
        Candle activeBefore = aggregator.getActiveCandle("BTCUSDT", "1m");
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), activeBefore.getTimestamp());

        // Processing trade2 should close 10:00:00 and open 10:01:00
        aggregator.process(trade2);

        Candle activeAfter = aggregator.getActiveCandle("BTCUSDT", "1m");
        assertNotNull(activeAfter);
        assertEquals(Instant.parse("2026-08-23T10:01:00Z"), activeAfter.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(102.0), activeAfter.getOpen());
    }

    // 4. New candle
    @Test
    void testNewCandle() {
        aggregator.process(new NormalizedTrade(1L, "BTCUSDT", BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T10:00:30Z")));
        aggregator.process(new NormalizedTrade(2L, "BTCUSDT", BigDecimal.valueOf(105.0), BigDecimal.valueOf(2.0), Instant.parse("2026-08-23T10:01:10Z")));

        Candle candle = aggregator.getActiveCandle("BTCUSDT", "1m");
        assertNotNull(candle);
        assertEquals(Instant.parse("2026-08-23T10:01:00Z"), candle.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(105.0), candle.getOpen());
    }

    // 5. Duplicate trade
    @Test
    void testDuplicateTrade() {
        NormalizedTrade trade1 = new NormalizedTrade(
                12345L, "BTCUSDT", BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.5), Instant.parse("2026-08-23T10:00:10Z")
        );
        NormalizedTrade trade2 = new NormalizedTrade(
                12345L, "BTCUSDT", BigDecimal.valueOf(105.0), BigDecimal.valueOf(2.5), Instant.parse("2026-08-23T10:00:15Z")
        );

        aggregator.process(trade1);
        aggregator.process(trade2); // duplicate trade ID, should be ignored

        Candle candle = aggregator.getActiveCandle("BTCUSDT", "1m");
        assertNotNull(candle);
        assertBigDecimalEquals(BigDecimal.valueOf(100.0), candle.getOpen());
        assertBigDecimalEquals(BigDecimal.valueOf(100.0), candle.getClose());
        assertBigDecimalEquals(BigDecimal.valueOf(1.5), candle.getVolume());
    }

    // 6. Timestamp
    @Test
    void testTimestamp() {
        Instant t = Instant.parse("2026-08-23T10:34:56Z");
        assertEquals(Instant.parse("2026-08-23T10:34:00Z"), CandleAggregator.calculateCandleStart(t, "1m"));
        assertEquals(Instant.parse("2026-08-23T10:30:00Z"), CandleAggregator.calculateCandleStart(t, "5m"));
        assertEquals(Instant.parse("2026-08-23T10:30:00Z"), CandleAggregator.calculateCandleStart(t, "15m"));
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), CandleAggregator.calculateCandleStart(t, "1h"));
    }

    // 7. Out-of-order trade
    @Test
    void testOutOfOrderTrade() {
        // A. Out-of-order within the same candle:
        // Processing order: trade at 10:00:20 (price 101) -> 10:00:30 (price 103) -> 10:00:10 (price 100) -> 10:00:25 (price 102)
        // Earliest is 10:00:10 (price 100), latest is 10:00:30 (price 103).
        Instant baseTime = Instant.parse("2026-08-23T10:00:00Z");
        aggregator.process(new NormalizedTrade(1L, "BTCUSDT", BigDecimal.valueOf(101.0), BigDecimal.valueOf(1.0), baseTime.plusSeconds(20)));
        aggregator.process(new NormalizedTrade(2L, "BTCUSDT", BigDecimal.valueOf(103.0), BigDecimal.valueOf(1.0), baseTime.plusSeconds(30)));
        aggregator.process(new NormalizedTrade(3L, "BTCUSDT", BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.0), baseTime.plusSeconds(10)));
        aggregator.process(new NormalizedTrade(4L, "BTCUSDT", BigDecimal.valueOf(102.0), BigDecimal.valueOf(1.0), baseTime.plusSeconds(25)));

        Candle candle = aggregator.getActiveCandle("BTCUSDT", "1m");
        assertNotNull(candle);
        assertBigDecimalEquals(BigDecimal.valueOf(100.0), candle.getOpen());
        assertBigDecimalEquals(BigDecimal.valueOf(103.0), candle.getHigh());
        assertBigDecimalEquals(BigDecimal.valueOf(100.0), candle.getLow());
        assertBigDecimalEquals(BigDecimal.valueOf(103.0), candle.getClose());
        assertBigDecimalEquals(BigDecimal.valueOf(4.0), candle.getVolume());

        // B. Late trade belonging to an already closed candle:
        // Current active candle is 10:01:00 (since we processed trade at 10:01:05)
        aggregator.process(new NormalizedTrade(5L, "BTCUSDT", BigDecimal.valueOf(105.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T10:01:05Z")));
        
        // Late trade belonging to 10:00:00 candle (already closed)
        aggregator.process(new NormalizedTrade(6L, "BTCUSDT", BigDecimal.valueOf(110.0), BigDecimal.valueOf(10.0), Instant.parse("2026-08-23T10:00:15Z")));

        // Verify the 10:01:00 candle remains active and is not affected
        Candle active = aggregator.getActiveCandle("BTCUSDT", "1m");
        assertEquals(Instant.parse("2026-08-23T10:01:00Z"), active.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(105.0), active.getOpen());
    }

    // 8. 1m aggregation
    @Test
    void test1mAggregation() {
        aggregator.process(new NormalizedTrade(1L, "BTCUSDT", BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T10:00:00Z")));
        aggregator.process(new NormalizedTrade(2L, "BTCUSDT", BigDecimal.valueOf(101.0), BigDecimal.valueOf(2.0), Instant.parse("2026-08-23T10:00:59Z")));

        Candle candle = aggregator.getActiveCandle("BTCUSDT", "1m");
        assertNotNull(candle);
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), candle.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(100.0), candle.getOpen());
        assertBigDecimalEquals(BigDecimal.valueOf(101.0), candle.getClose());
    }

    // 9. 5m aggregation
    @Test
    void test5mAggregation() {
        aggregator.process(new NormalizedTrade(1L, "BTCUSDT", BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T10:00:00Z")));
        aggregator.process(new NormalizedTrade(2L, "BTCUSDT", BigDecimal.valueOf(102.0), BigDecimal.valueOf(2.0), Instant.parse("2026-08-23T10:04:59Z")));

        Candle candle = aggregator.getActiveCandle("BTCUSDT", "5m");
        assertNotNull(candle);
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), candle.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(100.0), candle.getOpen());
        assertBigDecimalEquals(BigDecimal.valueOf(102.0), candle.getClose());

        // Next 5m period
        aggregator.process(new NormalizedTrade(3L, "BTCUSDT", BigDecimal.valueOf(105.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T10:05:00Z")));
        Candle nextCandle = aggregator.getActiveCandle("BTCUSDT", "5m");
        assertNotNull(nextCandle);
        assertEquals(Instant.parse("2026-08-23T10:05:00Z"), nextCandle.getTimestamp());
    }

    // 10. 15m aggregation
    @Test
    void test15mAggregation() {
        aggregator.process(new NormalizedTrade(1L, "BTCUSDT", BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T10:00:00Z")));
        aggregator.process(new NormalizedTrade(2L, "BTCUSDT", BigDecimal.valueOf(103.0), BigDecimal.valueOf(2.0), Instant.parse("2026-08-23T10:14:59Z")));

        Candle candle = aggregator.getActiveCandle("BTCUSDT", "15m");
        assertNotNull(candle);
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), candle.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(100.0), candle.getOpen());
        assertBigDecimalEquals(BigDecimal.valueOf(103.0), candle.getClose());

        // Next 15m period
        aggregator.process(new NormalizedTrade(3L, "BTCUSDT", BigDecimal.valueOf(106.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T10:15:00Z")));
        Candle nextCandle = aggregator.getActiveCandle("BTCUSDT", "15m");
        assertNotNull(nextCandle);
        assertEquals(Instant.parse("2026-08-23T10:15:00Z"), nextCandle.getTimestamp());
    }

    // 11. 1h aggregation
    @Test
    void test1hAggregation() {
        aggregator.process(new NormalizedTrade(1L, "BTCUSDT", BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T10:00:00Z")));
        aggregator.process(new NormalizedTrade(2L, "BTCUSDT", BigDecimal.valueOf(104.0), BigDecimal.valueOf(2.0), Instant.parse("2026-08-23T10:59:59Z")));

        Candle candle = aggregator.getActiveCandle("BTCUSDT", "1h");
        assertNotNull(candle);
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), candle.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(100.0), candle.getOpen());
        assertBigDecimalEquals(BigDecimal.valueOf(104.0), candle.getClose());

        // Next 1h period
        aggregator.process(new NormalizedTrade(3L, "BTCUSDT", BigDecimal.valueOf(107.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T11:00:00Z")));
        Candle nextCandle = aggregator.getActiveCandle("BTCUSDT", "1h");
        assertNotNull(nextCandle);
        assertEquals(Instant.parse("2026-08-23T11:00:00Z"), nextCandle.getTimestamp());
    }

    @Test
    void test30mAnd4hAnd1dAggregation() {
        aggregator.process(new NormalizedTrade(1L, "BTCUSDT", BigDecimal.valueOf(100.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T10:15:00Z")));
        aggregator.process(new NormalizedTrade(2L, "BTCUSDT", BigDecimal.valueOf(103.0), BigDecimal.valueOf(2.0), Instant.parse("2026-08-23T10:29:59Z")));

        Candle candle30m = aggregator.getActiveCandle("BTCUSDT", "30m");
        assertNotNull(candle30m);
        assertEquals(Instant.parse("2026-08-23T10:00:00Z"), candle30m.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(100.0), candle30m.getOpen());
        assertBigDecimalEquals(BigDecimal.valueOf(103.0), candle30m.getClose());

        aggregator.process(new NormalizedTrade(3L, "BTCUSDT", BigDecimal.valueOf(106.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-23T08:00:00Z")));
        Candle candle4h = aggregator.getActiveCandle("BTCUSDT", "4h");
        assertNotNull(candle4h);
        assertEquals(Instant.parse("2026-08-23T08:00:00Z"), candle4h.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(106.0), candle4h.getOpen());

        aggregator.process(new NormalizedTrade(4L, "BTCUSDT", BigDecimal.valueOf(109.0), BigDecimal.valueOf(1.0), Instant.parse("2026-08-24T00:00:00Z")));
        Candle candle1d = aggregator.getActiveCandle("BTCUSDT", "1d");
        assertNotNull(candle1d);
        assertEquals(Instant.parse("2026-08-24T00:00:00Z"), candle1d.getTimestamp());
        assertBigDecimalEquals(BigDecimal.valueOf(109.0), candle1d.getOpen());
    }
}