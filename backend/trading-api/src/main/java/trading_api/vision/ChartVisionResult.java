package trading_api.vision;

import java.util.List;

public record ChartVisionResult(
        String status,
        String symbol,
        String timeframe,
        List<String> visibleIndicators,
        List<String> visiblePriceLevels,
        String chartType,
        Double confidence,
        List<String> detectedText,
        List<String> detectedRegions,
        Double estimatedPriceHigh,
        Double estimatedPriceLow,
        List<String> detectionWarnings,
        String market
) {
    public ChartVisionResult(
            String status,
            String symbol,
            String timeframe,
            List<String> visibleIndicators,
            List<String> visiblePriceLevels,
            String chartType,
            Double confidence,
            List<String> detectedText,
            List<String> detectedRegions,
            Double estimatedPriceHigh,
            Double estimatedPriceLow,
            List<String> detectionWarnings
    ) {
        this(status, symbol, timeframe, visibleIndicators, visiblePriceLevels, chartType,
                confidence, detectedText, detectedRegions, estimatedPriceHigh, estimatedPriceLow,
                detectionWarnings, null);
    }

    public static ChartVisionResult unavailable(String reason) {
        return new ChartVisionResult(
                "VISION_UNAVAILABLE",
                null,
                null,
                List.of(),
                List.of(),
                null,
                0d,
                List.of(),
                List.of(),
                null,
                null,
                List.of(reason),
                null
        );
    }
}