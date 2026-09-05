package trading_api.market;

import org.springframework.stereotype.Component;
import trading_api.entity.Candle;
import trading_api.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CandleAggregator {

    private final CandleRepository candleRepository;

    private record CandleKey(String symbol, String timeframe) {}

    private static class CandleState {
        private final Candle candle;
        private Instant earliestTradeTime;
        private Instant latestTradeTime;

        public CandleState(Candle candle, Instant tradeTime) {
            this.candle = candle;
            this.earliestTradeTime = tradeTime;
            this.latestTradeTime = tradeTime;
        }
    }

    private final Map<CandleKey, CandleState> activeCandles = new ConcurrentHashMap<>();

    private final Set<Long> processedTradeIds = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<Long, Boolean>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                    return size() > 10000;
                }
            })
    );

    private final Map<CandleKey, Long> lastUpdateLogTime = new ConcurrentHashMap<>();

    public CandleAggregator(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    public void process(NormalizedTrade trade) {
        if (trade == null) {
            return;
        }

        // 1. Duplicate Trade Check
        if (!processedTradeIds.add(trade.getTradeId())) {
            return;
        }

        // 2. Aggregate for the supported realtime chart timeframes.
        for (String timeframe : List.of("1m", "5m", "15m", "30m", "1h", "4h", "1d")) {
            processTimeframe(trade, timeframe);
        }
    }

    private void processTimeframe(NormalizedTrade trade, String timeframe) {
        CandleKey key = new CandleKey(trade.getSymbol(), timeframe);
        Instant candleTimestamp = calculateCandleStart(trade.getTimestamp(), timeframe);

        synchronized (activeCandles) {
            CandleState state = activeCandles.get(key);

            if (state == null) {
                // Open new candle
                Candle newCandle = createNewCandle(trade, timeframe, candleTimestamp);
                activeCandles.put(key, new CandleState(newCandle, trade.getTimestamp()));
                System.out.println("[CANDLE OPEN] " + timeframe + " | " + candleTimestamp + " | O: " + newCandle.getOpen());
                return;
            }

            Instant currentTimestamp = state.candle.getTimestamp();

            if (candleTimestamp.equals(currentTimestamp)) {
                // Update active candle
                updateCandle(state, trade);
            } else if (candleTimestamp.isAfter(currentTimestamp)) {
                // Close old candle, save to DB, and open new candle
                Candle closedCandle = state.candle;
                
                System.out.println("[CANDLE CLOSED] " + timeframe + " | " + closedCandle.getTimestamp()
                        + " | O: " + closedCandle.getOpen()
                        + " | H: " + closedCandle.getHigh()
                        + " | L: " + closedCandle.getLow()
                        + " | C: " + closedCandle.getClose()
                        + " | V: " + closedCandle.getVolume());
                
                saveClosedCandle(closedCandle);

                Candle newCandle = createNewCandle(trade, timeframe, candleTimestamp);
                activeCandles.put(key, new CandleState(newCandle, trade.getTimestamp()));
                System.out.println("[CANDLE OPEN] " + timeframe + " | " + candleTimestamp + " | O: " + newCandle.getOpen());
            } else {
                // Out-of-order trade for an already closed candle period.
                System.out.println("⚠️ LATE TRADE IGNORED FOR CLOSED CANDLE: tradeId=" + trade.getTradeId()
                        + ", timeframe=" + timeframe
                        + ", tradeTime=" + trade.getTimestamp()
                        + ", activeCandleStart=" + currentTimestamp);
            }
        }
    }

    private Candle createNewCandle(NormalizedTrade trade, String timeframe, Instant timestamp) {
        Candle candle = new Candle();
        candle.setSymbol(trade.getSymbol());
        candle.setTimeframe(timeframe);
        candle.setTimestamp(timestamp);

        BigDecimal price = trade.getPrice();
        BigDecimal volume = trade.getVolume();

        candle.setOpen(price);
        candle.setHigh(price);
        candle.setLow(price);
        candle.setClose(price);
        candle.setVolume(volume);

        return candle;
    }

    private void updateCandle(CandleState state, NormalizedTrade trade) {
        BigDecimal price = trade.getPrice();
        BigDecimal volume = trade.getVolume();
        Candle candle = state.candle;

        candle.setHigh(candle.getHigh().max(price));
        candle.setLow(candle.getLow().min(price));
        candle.setVolume(candle.getVolume().add(volume));

        // If the trade timestamp is strictly before the earliest trade time we recorded for this candle, update the open price
        if (trade.getTimestamp().isBefore(state.earliestTradeTime)) {
            state.earliestTradeTime = trade.getTimestamp();
            candle.setOpen(price);
        }

        // If the trade timestamp is strictly at or after the latest trade time we recorded, update the close price
        if (!trade.getTimestamp().isBefore(state.latestTradeTime)) {
            state.latestTradeTime = trade.getTimestamp();
            candle.setClose(price);
        }

        // Log [CANDLE UPDATE] throttled to once every 5 seconds per timeframe
        CandleKey key = new CandleKey(candle.getSymbol(), candle.getTimeframe());
        long now = System.currentTimeMillis();
        long lastLog = lastUpdateLogTime.getOrDefault(key, 0L);
        if (now - lastLog > 5000) {
            System.out.println("[CANDLE UPDATE] " + candle.getTimeframe() + " | C: " + candle.getClose() + " | V: " + candle.getVolume());
            lastUpdateLogTime.put(key, now);
        }
    }

    private void saveClosedCandle(Candle candle) {
        if (candleRepository != null) {
            try {
                boolean exists = candleRepository.existsBySymbolAndTimeframeAndTimestamp(
                        candle.getSymbol(),
                        candle.getTimeframe(),
                        candle.getTimestamp()
                );
                if (!exists) {
                    candleRepository.save(candle);
                    System.out.println("[CANDLE SAVED] " + candle.getSymbol() + " | " + candle.getTimeframe() + " | timestamp: " + candle.getTimestamp());
                } else {
                    System.out.println("⚠️ [CANDLE ALREADY EXISTS] " + candle.getSymbol() + " | " + candle.getTimeframe() + " | timestamp: " + candle.getTimestamp());
                }
            } catch (Exception e) {
                System.err.println("Failed to save candle to DB: " + e.getMessage());
            }
        } else {
            System.out.println("[Mock mode] Closed candle logged: " + candle.getSymbol() + " " + candle.getTimeframe() + " at " + candle.getTimestamp());
        }
    }

    public static Instant calculateCandleStart(Instant timestamp, String timeframe) {
        long epochSec = timestamp.getEpochSecond();
        long secondsInTimeframe = switch (timeframe) {
            case "1m" -> 60;
            case "5m" -> 300;
            case "15m" -> 900;
            case "30m" -> 1800;
            case "1h" -> 3600;
            case "4h" -> 14400;
            case "1d" -> 86400;
            default -> throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        };

        long alignedSec = (epochSec / secondsInTimeframe) * secondsInTimeframe;

        if ("1d".equals(timeframe)) {
            Instant utc = Instant.ofEpochSecond(epochSec).minusSeconds(0);
            ZonedDateTime zoned = utc.atZone(ZoneOffset.UTC);
            return ZonedDateTime.of(zoned.toLocalDate(), LocalTime.MIDNIGHT, ZoneOffset.UTC).toInstant();
        }

        return Instant.ofEpochSecond(alignedSec);
    }

    public Candle getActiveCandle(String symbol, String timeframe) {
        synchronized (activeCandles) {
            CandleState state = activeCandles.get(new CandleKey(symbol, timeframe));
            return state != null ? state.candle : null;
        }
    }

    public void clearState() {
        processedTradeIds.clear();
        synchronized (activeCandles) {
            activeCandles.clear();
            lastUpdateLogTime.clear();
        }
    }
}