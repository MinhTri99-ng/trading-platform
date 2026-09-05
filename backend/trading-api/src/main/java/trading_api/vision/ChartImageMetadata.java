package trading_api.vision;

import java.util.List;

public record ChartImageMetadata(
        String symbol,
        String timeframe,
        String pattern,
        List<String> indicatorsVisible,
        String keyPriceLevels,
        Double estimatedPriceHigh,
        Double estimatedPriceLow,
        Double confidence,
        String visionStatus,
        List<String> detectedText,
        List<String> detectionWarnings
) {
    public ChartImageMetadata(
            String symbol,
            String timeframe,
            String pattern,
            List<String> indicatorsVisible,
            String keyPriceLevels,
            Double estimatedPriceHigh,
            Double estimatedPriceLow,
            Double confidence
    ) {
        this(symbol, timeframe, pattern, indicatorsVisible, keyPriceLevels,
                estimatedPriceHigh, estimatedPriceLow, confidence,
                "VISION_UNAVAILABLE", List.of(), List.of());
    }
}
