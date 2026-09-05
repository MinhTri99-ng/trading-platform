package trading_api.market;

import java.time.Instant;

public class MarketTrade {
    private String symbol;
    private double price;
    private Instant timestamp;
    private double quantity;
    private long tradeId;
    public MarketTrade(long tradeId,String symbol, double price, double quantity, Instant timestamp) {
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.price = price;
        this.timestamp = timestamp;
        this.quantity = quantity;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    public long getTradeId() {
        return tradeId;
    }
    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }
}
