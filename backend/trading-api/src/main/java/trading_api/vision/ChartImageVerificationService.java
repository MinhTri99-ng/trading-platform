package trading_api.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import trading_api.market.MarketDataService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class ChartImageVerificationService {
    private static final Logger log = LoggerFactory.getLogger(ChartImageVerificationService.class);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final MarketDataService marketDataService;

    public ChartImageVerificationService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public ChartImageVerificationResult verify(ChartImageMetadata metadata) {
        if (metadata == null) {
            log.error("[Market verification error cause] Missing chart metadata");
            return new ChartImageVerificationResult(false, "INVALID INPUT", "Missing chart metadata", null, null, null, null);
        }

        String symbol = normalizeSymbol(metadata.symbol());
        String timeframe = normalizeTimeframe(metadata.timeframe());

        if (symbol == null || symbol.isBlank()) {
            log.error("[Market verification error cause] Missing trading pair/symbol for chart metadata");
            return new ChartImageVerificationResult(false, "INVALID INPUT", "Missing trading pair/symbol", metadata, null, null, null);
        }

        if (timeframe == null || timeframe.isBlank()) {
            log.error("[Market verification error cause] Missing timeframe for chart metadata: symbol={}", symbol);
            return new ChartImageVerificationResult(false, "TIMEFRAME_UNDETECTED", "Missing timeframe", metadata, symbol, null, null);
        }

        log.info("[MARKET VERIFICATION] symbol={} timeframe={}", symbol, timeframe);
        List<BigDecimal> marketRange = marketDataService.getLatestPriceRange(symbol, timeframe);
        if (marketRange == null || marketRange.size() < 2) {
            log.error("[Market verification error cause] Market data unavailable for verification: symbol={}, timeframe={}", symbol, timeframe);
            return new ChartImageVerificationResult(false, "MARKET_DATA_UNAVAILABLE", "Market data unavailable for verification", metadata, symbol, timeframe, null);
        }

        BigDecimal low = marketRange.get(0);
        BigDecimal high = marketRange.get(1);

        if (metadata.estimatedPriceLow() != null && metadata.estimatedPriceHigh() != null) {
            BigDecimal chartLow = BigDecimal.valueOf(metadata.estimatedPriceLow());
            BigDecimal chartHigh = BigDecimal.valueOf(metadata.estimatedPriceHigh());

            BigDecimal minAllowedLow = low.multiply(BigDecimal.ONE.subtract(TOLERANCE));
            BigDecimal maxAllowedHigh = high.multiply(BigDecimal.ONE.add(TOLERANCE));

            if (chartLow.compareTo(minAllowedLow) < 0 || chartHigh.compareTo(maxAllowedHigh) > 0) {
                String reason = "Price data on image does not match live market data within tolerance";
                log.error("[Market verification error cause] symbol={}, timeframe={}, imageRange={} - {}, marketRange={} - {}, tolerance={}%, reason={}",
                        symbol, timeframe, chartLow, chartHigh, low, high, TOLERANCE.multiply(new BigDecimal("100")), reason);
                return new ChartImageVerificationResult(false, "INVALID INPUT", reason, metadata, symbol, timeframe,
                        "Range mismatch: image=" + chartLow + "-" + chartHigh + ", market=" + low + "-" + high + ", tolerance=" + TOLERANCE);
            }
        }

        String marketSnapshot = "symbol=" + symbol + ", timeframe=" + timeframe + ", range=" + low + "-" + high + ", tolerance=" + TOLERANCE;
        log.info("[Market verification success] symbol={}, timeframe={}, range={}, tolerance={}", symbol, timeframe, marketSnapshot, TOLERANCE);

        return new ChartImageVerificationResult(
                true,
                "VERIFIED",
                "Verified with live market data",
                metadata,
                symbol,
                timeframe,
                marketSnapshot
        );
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }

        String normalized = symbol.trim().replace("-", "/").replace("_", "/").replace(" ", "").toUpperCase(Locale.ROOT);
        if (normalized.contains("/")) {
            normalized = normalized.replace("/", "");
        }
        if (normalized.endsWith("USD") && !normalized.endsWith("USDT")) {
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
