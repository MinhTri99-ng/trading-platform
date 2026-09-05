package trading_api.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MarketDataService {
    private final MarketDataNormalizer normalizer;
    private final CandleAggregator candleAggregator;
    private long lastLogTime = 0;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MarketDataService(MarketDataNormalizer normalizer, CandleAggregator candleAggregator) {
        this.normalizer = normalizer;
        this.candleAggregator = candleAggregator;
    }

    public void handleTrade(MarketTrade trade) {
        NormalizedTrade normalizedTrade = normalizer.normalize(trade);

        long now = System.currentTimeMillis();
        if (now - lastLogTime > 2000) {
            System.out.println(
                    "[NORMALIZED] "
                            + normalizedTrade.getSymbol()
                            + " | price: "
                            + normalizedTrade.getPrice()
                            + " | volume: "
                            + normalizedTrade.getVolume()
                            + " | timestamp: "
                            + normalizedTrade.getTimestamp()
            );
            lastLogTime = now;
        }

        candleAggregator.process(normalizedTrade);
    }

    public List<BigDecimal> getLatestPriceRange(String symbol, String timeframe) {
        try {
            String symbolCode = normalizeSymbol(symbol);
            String interval = normalizeInterval(timeframe);
            if (interval == null || interval.isBlank()) {
                return List.of();
            }
            HttpClient client = HttpClient.newHttpClient();
            String requestUrl = "https://api.binance.com/api/v3/klines?symbol=" + symbolCode + "&interval=" + interval + "&limit=20";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) {
                return List.of();
            }

            JsonNode latestCandle = root.get(root.size() - 1);
            BigDecimal low = new BigDecimal(latestCandle.get(3).asText());
            BigDecimal high = new BigDecimal(latestCandle.get(2).asText());
            return new ArrayList<>(List.of(low, high));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }
        return symbol.replace("/", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeInterval(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return "";
        }
        return switch (timeframe.toLowerCase(Locale.ROOT)) {
            case "1m" -> "1m";
            case "5m" -> "5m";
            case "15m" -> "15m";
            case "30m" -> "30m";
            case "1h" -> "1h";
            case "2h" -> "2h";
            case "3h" -> "3h";
            case "4h" -> "4h";
            case "12h" -> "12h";
            case "1d" -> "1d";
            case "1w" -> "1w";
            default -> "";
        };
    }
}
