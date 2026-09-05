package trading_api.smc.structure;

import java.math.BigDecimal;

public record MarketStructureConfig(
        int minCandles,
        int lookback,
        int lookforward,
        double noiseTolerancePercent,
        double minSwingDistancePercent,
        double externalStrengthThreshold,
        int minExternalSwingCount,
        int protectedLookback
) {
    public MarketStructureConfig(
            int minCandles,
            double noiseTolerancePercent,
            double minSwingDistancePercent,
            double externalStrengthThreshold,
            int minExternalSwingCount,
            int protectedLookback
    ) {
        this(
                minCandles,
                3,
                3,
                noiseTolerancePercent,
                minSwingDistancePercent,
                externalStrengthThreshold,
                minExternalSwingCount,
                protectedLookback
        );
    }

    public static MarketStructureConfig defaultConfig() {
        return new MarketStructureConfig(
                8,
                3,
                3,
                0.08,
                0.10,
                65.0,
                2,
                3
        );
    }

    public BigDecimal noiseTolerance(BigDecimal averageRange) {
        if (averageRange == null || averageRange.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return averageRange.multiply(BigDecimal.valueOf(noiseTolerancePercent));
    }

    public BigDecimal minimumSwingDistance(BigDecimal averageRange) {
        if (averageRange == null || averageRange.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return averageRange.multiply(BigDecimal.valueOf(minSwingDistancePercent));
    }
}
