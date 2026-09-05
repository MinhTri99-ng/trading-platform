package trading_api.market;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class WebSocketMarketDataClient implements WebSocket.Listener {
    private static final String BINANCE_WS_URL ="wss://stream.binance.com:9443/ws/btcusdt@trade";

    private WebSocket webSocket;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MarketDataService marketDataService;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    private enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }

    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private final Object stateLock = new Object();
    private long lastTradeLogTime = 0;

    public WebSocketMarketDataClient(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public void connect() {
        synchronized (stateLock) {
            if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING) {
                return;
            }
            if (connectionState == ConnectionState.DISCONNECTED) {
                connectionState = ConnectionState.CONNECTING;
                System.out.println("Connecting to Binance...");
            } else if (connectionState == ConnectionState.RECONNECTING) {
                System.out.println("[RECONNECTING] Attempting to reconnect to Binance...");
            }
        }

        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(
                        URI.create(BINANCE_WS_URL), this
                )
                .thenAccept(ws -> {
                    synchronized (stateLock) {
                        this.webSocket = ws;
                        if (connectionState == ConnectionState.RECONNECTING) {
                            System.out.println("[RECONNECTED]");
                        } else {
                            System.out.println("[CONNECTED]");
                        }
                        connectionState = ConnectionState.CONNECTED;
                    }
                })
                .exceptionally(error -> {
                    System.err.println("WebSocket connection failed: " + error.getMessage());
                    handleFailure(null);
                    return null;
                });
    }

    private void handleFailure(WebSocket ws) {
        synchronized (stateLock) {
            if (ws != null && ws != this.webSocket) {
                return;
            }
            if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING) {
                connectionState = ConnectionState.DISCONNECTED;
                System.out.println("[DISCONNECTED]");
            }
            if (connectionState != ConnectionState.RECONNECTING) {
                connectionState = ConnectionState.RECONNECTING;
                System.out.println("[RECONNECTING] in 3 seconds...");
                scheduler.schedule(this::connect, 3, TimeUnit.SECONDS);
            } else {
                System.out.println("[RECONNECTING] attempt failed. Retrying in 3 seconds...");
                scheduler.schedule(this::connect, 3, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("Binance WebSocket opened");
        webSocket.request(1);
        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(
            WebSocket webSocket,
            CharSequence data,
            boolean last
    ) {
        try {
            JsonNode json = objectMapper.readTree(data.toString());

            String symbol = json.get("s").asText();
            double price = json.get("p").asDouble();
            double quantity = json.get("q").asDouble();
            long timestamp = json.get("T").asLong();
            long tradeId = json.get("t").asLong();

            long now = System.currentTimeMillis();
            if (now - lastTradeLogTime > 2000) {
                System.out.println("[TRADE] " + symbol + " | price: " + price + " | qty: " + quantity + " | id: " + tradeId);
                lastTradeLogTime = now;
            }

            MarketTrade trade = new MarketTrade(tradeId, symbol, price, quantity,
                    Instant.ofEpochMilli(timestamp)
            );

            marketDataService.handleTrade(trade);

        } catch (Exception e) {
            System.err.println("Failed to parse Binance message: " + e.getMessage());
        }

        webSocket.request(1);

        return WebSocket.Listener.super.onText(
                webSocket,
                data,
                last
        );
    }

    @Override
    public CompletionStage<?> onClose(
            WebSocket webSocket,
            int statusCode,
            String reason
    ) {
        System.out.println("WebSocket closed: " + statusCode + " - " + reason);
        handleFailure(webSocket);
        return WebSocket.Listener.super.onClose(
                webSocket,
                statusCode,
                reason
        );
    }

    @Override
    public void onError(
            WebSocket webSocket,
            Throwable error
    ) {
        System.err.println("WebSocket error: " + error.getMessage());
        handleFailure(webSocket);
        WebSocket.Listener.super.onError(
                webSocket,
                error
        );
    }

    @PostConstruct
    public void start() {
        connect();
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
