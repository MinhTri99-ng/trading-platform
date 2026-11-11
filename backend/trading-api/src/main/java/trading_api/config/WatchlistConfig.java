package trading_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class WatchlistConfig {
    private final List<String> symbols;
    private final List<String> timeframes;

    public WatchlistConfig(
            @Value("${app.watchlist.symbols:BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT,XAUUSD}") String symbols,
            @Value("${app.watchlist.timeframes:15m,1h}") String timeframes
    ) {
        this.symbols = parse(symbols);
        this.timeframes = parse(timeframes);
    }

    public List<String> symbols() {
        return symbols;
    }

    public List<String> timeframes() {
        return timeframes;
    }

    public boolean contains(String symbol) {
        return symbol != null && symbols.contains(symbol.trim().toUpperCase(Locale.ROOT));
    }

    private List<String> parse(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
