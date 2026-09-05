package trading_api.smc.structure;

public record M15MarketStructureConfig(
        int minCandles,
        int lookback,
        int lookforward,
        double minMajorProminencePercent,
        double minMajorStrength
) {
    public M15MarketStructureConfig {
        if (minCandles < 3) throw new IllegalArgumentException("minCandles must be at least 3");
        if (lookback < 1 || lookforward < 1) throw new IllegalArgumentException("pivot windows must be positive");
        if (minMajorProminencePercent < 0 || minMajorStrength < 0) {
            throw new IllegalArgumentException("major swing thresholds cannot be negative");
        }
    }

    public static M15MarketStructureConfig defaultConfig() {
        return new M15MarketStructureConfig(8, 2, 2, 0.5, 25.0);
    }
}