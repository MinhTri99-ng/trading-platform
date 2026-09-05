package trading_api.vision;

import org.springframework.stereotype.Component;

@Component
public class LocalChartVisionProvider implements ChartVisionProvider {
    @Override
    public ChartVisionResult analyze(byte[] imageBytes, String fileName) {
        return ChartVisionResult.unavailable(
                "No OCR or chart vision engine is configured; image pixels were not interpreted."
        );
    }
}