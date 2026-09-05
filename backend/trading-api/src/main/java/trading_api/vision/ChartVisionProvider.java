package trading_api.vision;

public interface ChartVisionProvider {
    ChartVisionResult analyze(byte[] imageBytes, String fileName);
}