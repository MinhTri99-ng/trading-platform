package trading_api.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import trading_api.market.MarketDataService;

import java.util.Locale;

@Service
public class ChartImageVerificationService {
    private static final Logger log = LoggerFactory.getLogger(ChartImageVerificationService.class);
    private final MarketDataService marketDataService;

    public ChartImageVerificationService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public ChartImageVerificationResult verify(ChartImageMetadata metadata) {
        if (metadata == null) {
            log.error("[Chart analysis] Missing chart metadata");
            return new ChartImageVerificationResult(false, "INVALID INPUT", "Missing chart metadata", null, null, null, null);
        }

        String symbol = normalizeSymbol(metadata.symbol());
        String timeframe = normalizeTimeframe(metadata.timeframe());
        if (symbol == null || symbol.isBlank()) {
            symbol = "CHART_ANALYSIS";
        }
        if (timeframe == null || timeframe.isBlank()) {
            timeframe = "1d";
        }

        String marketSnapshot = "symbol=" + symbol + ", market=" + normalizedMarket(metadata.market())
                + ", timeframe=" + timeframe + ", source=IMAGE_ANALYSIS";
        log.info("[CHART ANALYSIS ACCEPTED] symbol={} market={} timeframe={}",
                symbol, normalizedMarket(metadata.market()), timeframe);
        return new ChartImageVerificationResult(
                true,
                "ANALYSIS_SUCCESSFUL",
                "Chart image accepted; strict market verification is disabled",
                metadata,
                symbol,
                timeframe,
                marketSnapshot
        );
    }

    private String normalizedMarket(String market) {
        return market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }
        if ("CHART_ANALYSIS".equalsIgnoreCase(symbol.trim())) {
            return "CHART_ANALYSIS";
        }

        String normalized = symbol.trim().replace("-", "/").replace("_", "/").replace(" ", "").toUpperCase(Locale.ROOT);
        if (normalized.contains("/")) {
            normalized = normalized.replace("/", "");
        }
        boolean knownNonCrypto = normalized.startsWith("XAU") || normalized.startsWith("XAG")
                || normalized.matches("^(EUR|GBP|USD|JPY|AUD|NZD|CHF|CAD).*(USD)$")
                || normalized.matches("^[A-Z]{1,5}$");
        if (normalized.endsWith("USD") && !normalized.endsWith("USDT") && !knownNonCrypto) {
            normalized = normalized.substring(0, normalized.length() - 3) + "USDT";
        }
        if (normalized.endsWith("BUSD") || normalized.endsWith("USDC")) {
            normalized = normalized.substring(0, normalized.length() - 4) + "USDT";
        }
        return normalized;
    }

    private String normalizeTimeframe(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return "";
        }
        String normalized = timeframe.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        return switch (normalized) {
            case "1m", "5m", "15m", "30m", "1h", "2h", "3h", "4h", "12h", "1d", "1w" -> normalized;
            case "d", "day", "daily" -> "1d";
            case "h" -> "1h";
            case "w", "week", "weekly" -> "1w";
            default -> "";
        };
    }
}
