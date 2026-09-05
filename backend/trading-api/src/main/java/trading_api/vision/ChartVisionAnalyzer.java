package trading_api.vision;

import java.util.Optional;

@FunctionalInterface
public interface ChartVisionAnalyzer {
    Optional<String> detectTimeframe(byte[] imageBytes);
}