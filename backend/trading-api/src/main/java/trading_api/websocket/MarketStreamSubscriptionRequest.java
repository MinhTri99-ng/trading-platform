package trading_api.websocket;

public record MarketStreamSubscriptionRequest(String symbol, String interval) {
    public MarketStreamSubscriptionRequest {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (interval == null || interval.isBlank()) {
            interval = "1h";
        }
    }
}
