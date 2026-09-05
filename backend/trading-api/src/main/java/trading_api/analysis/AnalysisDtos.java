package trading_api.analysis;

import java.time.Instant;
import java.util.List;

public final class AnalysisDtos {
    private AnalysisDtos() {}
    public record TradeSetupResponse(Long id, String type, java.math.BigDecimal entryPrice, java.math.BigDecimal stopLoss, java.math.BigDecimal takeProfit, String status, Instant createdAt) {}
    public record AnalysisResponse(Long id, String symbol, String timeframe, String resultJson, Instant createdAt, List<TradeSetupResponse> tradeSetups) {}
}