package trading_api.vision;

public record ChartImageVerificationResult(
        boolean valid,
        String status,
        String reason,
        ChartImageMetadata metadata,
        String verifiedSymbol,
        String verifiedTimeframe,
        String marketSnapshot
) {
}
