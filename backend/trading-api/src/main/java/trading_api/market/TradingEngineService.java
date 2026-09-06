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
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

@Service
public class TradingEngineService {
    private static final Logger log = LoggerFactory.getLogger(TradingEngineService.class);

    private final PriceServiceRegistry priceServiceRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, BigDecimal> fallbackPrices;

    public TradingEngineService(
            PriceServiceRegistry priceServiceRegistry,
            MarketProperties marketProperties,
            SimpMessagingTemplate messagingTemplate) {
        this.priceServiceRegistry = priceServiceRegistry;
        this.messagingTemplate = messagingTemplate;
        this.fallbackPrices = marketProperties.getFallbackPrices();
    }

    public TradingSignalPayload processSignal(ChartImageMetadata metadata, ChartImageVerificationResult verificationResult) {
        if (metadata == null || verificationResult == null || !verificationResult.valid()) {
            log.warn("[Trading engine] Ignored invalid chart signal metadata");
            return null;
        }

        String symbol = normalizeSymbol(verificationResult.verifiedSymbol() != null ? verificationResult.verifiedSymbol() : metadata.symbol());
        String timeframe = verificationResult.verifiedTimeframe() != null ? verificationResult.verifiedTimeframe() : metadata.timeframe();

        log.info("[STRATEGY] symbol={} timeframe={}", symbol, timeframe);
        BigDecimal entry = imagePrice(metadata)
                .or(() -> priceServiceRegistry.getLivePrice(symbol))
                .or(() -> Optional.ofNullable(fallbackPrices.get(symbol)))
                .orElseGet(() -> {
                    log.warn("[Trading engine] No reliable price available: symbol={}, timeframe={}", symbol, timeframe);
                    return null;
                });
        if (entry == null || entry.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        String direction = inferDirection(metadata.pattern());
        BigDecimal stopLoss = "LONG".equals(direction)
                ? entry.multiply(BigDecimal.valueOf(0.98))
                : entry.multiply(BigDecimal.valueOf(1.02));
        BigDecimal takeProfit = "LONG".equals(direction)
                ? entry.multiply(BigDecimal.valueOf(1.05))
                : entry.multiply(BigDecimal.valueOf(0.95));

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

    private Optional<BigDecimal> imagePrice(ChartImageMetadata metadata) {
        if (metadata.estimatedPriceLow() == null || metadata.estimatedPriceHigh() == null
                || metadata.estimatedPriceLow() <= 0 || metadata.estimatedPriceHigh() <= 0
                || metadata.estimatedPriceHigh() < metadata.estimatedPriceLow()) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(metadata.estimatedPriceLow())
                .add(BigDecimal.valueOf(metadata.estimatedPriceHigh()))
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
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
