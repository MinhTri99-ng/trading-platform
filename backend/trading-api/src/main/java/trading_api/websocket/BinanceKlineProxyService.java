package trading_api.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class BinanceKlineProxyService {
    private static final String BINANCE_WS_BASE = "wss://stream.binance.com:9443/ws/";
    private static final Duration DUPLICATE_TTL = Duration.ofMinutes(1);

    private final SimpMessagingTemplate messagingTemplate;
    private final TradingAnalysisService tradingAnalysisService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, WebSocket> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, String> activeSymbolSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Long> dedupCache = new ConcurrentHashMap<>();

    public BinanceKlineProxyService(
            SimpMessagingTemplate messagingTemplate,
            TradingAnalysisService tradingAnalysisService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.tradingAnalysisService = tradingAnalysisService;
    }

    public void subscribe(String symbol, String interval) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String normalizedInterval = normalizeInterval(interval);
        if (normalizedSymbol.isBlank()) {
            return;
        }

        String key = normalizedSymbol + "@" + normalizedInterval;
        String previousSubscription = activeSymbolSubscriptions.put(normalizedSymbol, normalizedInterval);
        if (previousSubscription != null && !previousSubscription.equals(normalizedInterval)) {
            unsubscribe(normalizedSymbol, previousSubscription);
        }

        if (activeConnections.containsKey(key)) {
            System.out.println("[WS CONNECTED] symbol=" + normalizedSymbol + " timeframe=" + normalizedInterval + " (already active)");
            return;
        }

        String url = BINANCE_WS_BASE + normalizedSymbol.toLowerCase(Locale.ROOT) + "@kline_" + normalizedInterval;
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    @Override
                    public java.util.concurrent.CompletionStage<?> onText(
                            WebSocket webSocket,
                            CharSequence data,
                            boolean last
                    ) {
                        try {
                            handleMessage(data.toString(), normalizedSymbol, normalizedInterval);
                        } catch (Exception ex) {
                            System.err.println("Failed to process Binance kline payload: " + ex.getMessage());
                        }
                        webSocket.request(1);
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.err.println("Binance kline websocket error for " + key + ": " + error.getMessage());
                        reconnect(key, normalizedSymbol, normalizedInterval);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        System.out.println("Binance kline websocket closed for " + key + " -> " + statusCode + " / " + reason);
                        reconnect(key, normalizedSymbol, normalizedInterval);
                        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                    }
                })
                .thenAccept(webSocket -> {
                    activeConnections.put(key, webSocket);
                    System.out.println("[WS CONNECTED] symbol=" + normalizedSymbol + " timeframe=" + normalizedInterval);
                })
                .exceptionally(error -> {
                    System.err.println("Failed to connect Binance kline stream for " + key + ": " + error.getMessage());
                    reconnect(key, normalizedSymbol, normalizedInterval);
                    return null;
                });
    }

    public void unsubscribe(String symbol, String interval) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String normalizedInterval = normalizeInterval(interval);
        if (normalizedSymbol.isBlank()) {
            return;
        }

        String key = normalizedSymbol + "@" + normalizedInterval;
        WebSocket existing = activeConnections.remove(key);
        if (existing != null) {
            existing.abort();
            System.out.println("[WS DISCONNECTED] symbol=" + normalizedSymbol + " timeframe=" + normalizedInterval);
        }
        activeSymbolSubscriptions.remove(normalizedSymbol, normalizedInterval);
    }

    private void reconnect(String key, String symbol, String interval) {
        WebSocket existing = activeConnections.remove(key);
        if (existing != null) {
            existing.abort();
        }
        scheduler.schedule(() -> subscribe(symbol, interval), 3, TimeUnit.SECONDS);
    }

    private void handleMessage(String rawJson, String symbol, String interval) throws IOException {
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode klineNode = root.path("k");
        if (klineNode.isMissingNode() || klineNode.isNull()) {
            return;
        }

        String streamSymbol = root.path("s").asText(symbol);
        String streamInterval = klineNode.path("i").asText(interval);
        long eventTime = root.path("E").asLong(0L);
        long openTime = klineNode.path("t").asLong(0L);
        String dedupKey = streamSymbol + ":" + streamInterval + ":" + openTime + ":" + eventTime;

        Long existingStamp = dedupCache.putIfAbsent(dedupKey, System.currentTimeMillis());
        if (existingStamp != null) {
            return;
        }
        dedupCache.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > DUPLICATE_TTL.toMillis());

        long closeTime = klineNode.path("T").asLong(0L);
        BigDecimal open = new BigDecimal(klineNode.path("o").asText("0"));
        BigDecimal high = new BigDecimal(klineNode.path("h").asText("0"));
        BigDecimal low = new BigDecimal(klineNode.path("l").asText("0"));
        BigDecimal close = new BigDecimal(klineNode.path("c").asText("0"));
        BigDecimal volume = new BigDecimal(klineNode.path("v").asText("0"));
        boolean isFinal = klineNode.path("x").asBoolean(false);

        KlineStreamPayload payload = new KlineStreamPayload(
                dedupKey,
                streamSymbol.toUpperCase(Locale.ROOT),
                streamInterval,
                openTime,
                closeTime,
                open,
                high,
                low,
                close,
                volume,
                isFinal,
                System.currentTimeMillis() / 1000L
        );

        messagingTemplate.convertAndSend("/topic/klines/" + streamSymbol.toUpperCase(Locale.ROOT), payload);
        if (isFinal) {
            tradingAnalysisService.process(streamSymbol.toUpperCase(Locale.ROOT), payload);
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        String normalized = symbol.replace("/", "").replace(" ", "").toUpperCase(Locale.ROOT);
        return normalized;
    }

    private String normalizeInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return "1h";
        }
        return switch (interval.toLowerCase(Locale.ROOT)) {
            case "1m" -> "1m";
            case "5m" -> "5m";
            case "15m" -> "15m";
            case "30m" -> "30m";
            case "1h", "1H" -> "1h";
            case "4h", "4H" -> "4h";
            case "1d", "1D" -> "1d";
            case "1w", "1W" -> "1w";
            default -> "1h";
        };
    }
}
