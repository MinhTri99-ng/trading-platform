package trading_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import trading_api.entity.Candle;
import trading_api.repository.CandleRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BinanceHistoricalCandleService {
    private static final int BINANCE_BATCH_SIZE = 1000;
    private final CandleRepository candleRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BinanceHistoricalCandleService(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    @Transactional
    public ImportResult importCandles(String symbol, String timeframe, Instant from, Instant to) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String interval = normalizeInterval(timeframe);
        long intervalMillis = intervalMillis(interval);
        if (normalizedSymbol.isBlank() || interval.isBlank()) {
            throw new IllegalArgumentException("Unsupported symbol or timeframe");
        }
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }

        long nextStart = from.toEpochMilli();
        long endMillis = to.toEpochMilli();
        int fetched = 0;
        int inserted = 0;

        while (nextStart < endMillis) {
            List<Candle> batch = fetchBatch(normalizedSymbol, interval, nextStart, endMillis);
            if (batch.isEmpty()) break;

            fetched += batch.size();
            Instant batchFrom = batch.get(0).getTimestamp();
            Instant batchTo = batch.get(batch.size() - 1).getTimestamp();
            Set<Instant> existing = new HashSet<>(candleRepository
                    .findBySymbolAndTimeframeAndTimestampBetween(normalizedSymbol, interval, batchFrom, batchTo)
                    .stream()
                    .map(Candle::getTimestamp)
                    .toList());
            List<Candle> newCandles = batch.stream()
                    .filter(candle -> existing.add(candle.getTimestamp()))
                    .toList();
            if (!newCandles.isEmpty()) {
                candleRepository.saveAll(newCandles);
                inserted += newCandles.size();
            }

            long lastOpen = batch.get(batch.size() - 1).getTimestamp().toEpochMilli();
            long candidateNext = lastOpen + intervalMillis;
            if (candidateNext <= nextStart) break;
            nextStart = candidateNext;
        }

        return new ImportResult(normalizedSymbol, timeframe, fetched, inserted, from, to);
    }

    private List<Candle> fetchBatch(String symbol, String interval, long startMillis, long endMillis) {
        String url = "https://api.binance.com/api/v3/klines?symbol=" + symbol
                + "&interval=" + interval
                + "&startTime=" + startMillis
                + "&endTime=" + endMillis
                + "&limit=" + BINANCE_BATCH_SIZE;
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Binance historical request failed: HTTP " + response.statusCode());
            }
            JsonNode rows = objectMapper.readTree(response.body());
            List<Candle> candles = new ArrayList<>();
            Instant now = Instant.now();
            for (JsonNode row : rows) {
                Instant timestamp = Instant.ofEpochMilli(row.get(0).asLong());
                Instant closeTime = Instant.ofEpochMilli(row.get(6).asLong());
                if (!closeTime.isBefore(now)) continue;
                Candle candle = new Candle();
                candle.setSymbol(symbol);
                candle.setTimeframe(normalizeTimeframeForStorage(interval));
                candle.setTimestamp(timestamp);
                candle.setOpen(decimal(row, 1));
                candle.setHigh(decimal(row, 2));
                candle.setLow(decimal(row, 3));
                candle.setClose(decimal(row, 4));
                candle.setVolume(decimal(row, 5));
                candles.add(candle);
            }
            return candles;
        } catch (IOException ex) {
            throw new IllegalStateException("Binance historical response could not be parsed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Binance historical request interrupted", ex);
        }
    }

    private BigDecimal decimal(JsonNode row, int index) {
        return new BigDecimal(row.get(index).asText());
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.replace("/", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeInterval(String timeframe) {
        if (timeframe == null) return "";
        return switch (timeframe.toLowerCase(Locale.ROOT)) {
            case "1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "8h", "12h", "1d", "3d", "1w" -> timeframe.toLowerCase(Locale.ROOT);
            default -> "";
        };
    }

    private String normalizeTimeframeForStorage(String interval) {
        return interval;
    }

    private long intervalMillis(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "3m" -> 180_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "30m" -> 1_800_000L;
            case "1h" -> 3_600_000L;
            case "2h" -> 7_200_000L;
            case "4h" -> 14_400_000L;
            case "6h" -> 21_600_000L;
            case "8h" -> 28_800_000L;
            case "12h" -> 43_200_000L;
            case "1d" -> 86_400_000L;
            case "3d" -> 259_200_000L;
            case "1w" -> 604_800_000L;
            default -> throw new IllegalArgumentException("Unsupported Binance interval: " + interval);
        };
    }

    public record ImportResult(String symbol, String timeframe, int fetched, int inserted, Instant from, Instant to) {
    }
}