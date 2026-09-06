package trading_api.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import trading_api.vision.ChartImageMetadata;
import trading_api.vision.ChartImageVerificationResult;
import trading_api.websocket.TradingSignalPayload;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class TradingEngineService {
    private static final Logger log = LoggerFactory.getLogger(TradingEngineService.class);

    private final MarketDataService marketDataService;
    private final SimpMessagingTemplate messagingTemplate;

    public TradingEngineService(MarketDataService marketDataService, SimpMessagingTemplate messagingTemplate) {
        this.marketDataService = marketDataService;
        this.messagingTemplate = messagingTemplate;
    }

    public TradingSignalPayload processSignal(ChartImageMetadata metadata, ChartImageVerificationResult verificationResult) {
        if (metadata == null || verificationResult == null || !verificationResult.valid()) {
            log.warn("[Trading engine] Ignored invalid chart signal metadata");
            return null;
        }

        String symbol = normalizeSymbol(verificationResult.verifiedSymbol() != null ? verificationResult.verifiedSymbol() : metadata.symbol());
        String timeframe = verificationResult.verifiedTimeframe() != null ? verificationResult.verifiedTimeframe() : metadata.timeframe();

        log.info("[STRATEGY] symbol={} timeframe={}", symbol, timeframe);
        List<BigDecimal> priceRange;
        try {
            priceRange = marketDataService.getLatestPriceRange(symbol, timeframe);
        } catch (RuntimeException ex) {
            log.warn("[Trading engine] Market data unavailable; using image-analysis fallback: symbol={}, timeframe={}", symbol, timeframe, ex);
            priceRange = List.of();
        }
        if (priceRange == null) {
            priceRange = List.of();
        }
        BigDecimal marketLow = priceRange.size() > 0 ? priceRange.get(0) : BigDecimal.valueOf(metadata.estimatedPriceLow() != null ? metadata.estimatedPriceLow() : 1000);
        BigDecimal marketHigh = priceRange.size() > 1 ? priceRange.get(1) : BigDecimal.valueOf(metadata.estimatedPriceHigh() != null ? metadata.estimatedPriceHigh() : 1100);

        if (marketLow.compareTo(BigDecimal.ZERO) <= 0) {
            marketLow = BigDecimal.valueOf(1);
        }
        if (marketHigh.compareTo(marketLow) <= 0) {
            marketHigh = marketLow.multiply(BigDecimal.valueOf(1.02));
        }

        String direction = inferDirection(metadata.pattern());
        BigDecimal entry = marketLow.add(marketHigh).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        BigDecimal stopLoss = "LONG".equals(direction)
                ? marketLow.multiply(BigDecimal.valueOf(0.995))
                : marketHigh.multiply(BigDecimal.valueOf(1.005));
        BigDecimal takeProfit = "LONG".equals(direction)
                ? entry.multiply(BigDecimal.valueOf(1.018))
                : entry.multiply(BigDecimal.valueOf(0.982));

        BigDecimal riskDistance = entry.subtract(stopLoss).abs();
        BigDecimal rewardDistance = takeProfit.subtract(entry).abs();
        BigDecimal riskReward = riskDistance.compareTo(BigDecimal.ZERO) > 0
                ? rewardDistance.divide(riskDistance, 4, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        int confidence = computeConfidence(metadata, direction);

        TradingSignalPayload payload = new TradingSignalPayload(
                symbol,
                direction,
                entry,
                stopLoss,
                takeProfit,
                "1:" + riskReward.setScale(2, RoundingMode.HALF_UP),
                confidence,
                System.currentTimeMillis()
        );

        messagingTemplate.convertAndSend("/topic/signals", payload);
        log.info("[Trading engine] Broadcast {} signal for {} @ {} | entry={} stopLoss={} takeProfit={} confidence={}",
                direction, symbol, timeframe, entry, stopLoss, takeProfit, confidence);
        return payload;
    }

    private String inferDirection(String pattern) {
        if (pattern == null) {
            return "LONG";
        }
        String normalized = pattern.toLowerCase(Locale.ROOT);
        if (normalized.contains("bear") || normalized.contains("down") || normalized.contains("short")) {
            return "SHORT";
        }
        return "LONG";
    }

    private int computeConfidence(ChartImageMetadata metadata, String direction) {
        int base = 65;
        if (metadata != null && metadata.confidence() != null) {
            base = (int) Math.min(95, Math.max(55, metadata.confidence() * 100));
        }
        if (metadata != null && metadata.indicatorsVisible() != null) {
            base += Math.min(10, metadata.indicatorsVisible().size() * 2);
        }
        if ("SHORT".equals(direction)) {
            base = Math.min(96, base + 2);
        }
        return Math.max(60, Math.min(97, base));
    }

    private String normalizeSymbol(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            return "";
        }
        return rawSymbol.replace("/", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }
}
