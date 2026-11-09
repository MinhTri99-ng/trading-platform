package trading_api.analysis;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import trading_api.auth.User;
import java.util.List;

@Service
public class AnalysisHistoryService {
    private final AnalysisRepository analyses;
    private final TradeSetupRepository setups;
    public AnalysisHistoryService(AnalysisRepository analyses, TradeSetupRepository setups) { this.analyses = analyses; this.setups = setups; }
    @Transactional
    public Analysis saveAnalysis(Analysis analysis, TradeSetup tradeSetup) {
        if (tradeSetup != null) {
            analysis.addTradeSetup(tradeSetup);
        }
        Analysis saved = analyses.save(analysis);
        if (tradeSetup != null) {
            setups.save(tradeSetup);
        }
        return saved;
    }
    public List<AnalysisDtos.AnalysisResponse> history(User user) {
        return analyses.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(analysis -> toResponse(analysis, user.getId())).toList();
    }
    public AnalysisDtos.AnalysisResponse detail(Long id, User user) {
        return analyses.findByIdAndUserId(id, user.getId()).map(analysis -> toResponse(analysis, user.getId())).orElseThrow(() -> new AnalysisNotFoundException());
    }
    private AnalysisDtos.AnalysisResponse toResponse(Analysis analysis, Long userId) {
        List<AnalysisDtos.TradeSetupResponse> tradeSetups = setups.findByAnalysisIdAndUserId(analysis.getId(), userId).stream().map(setup -> new AnalysisDtos.TradeSetupResponse(setup.getId(), setup.getType(), setup.getEntryPrice(), setup.getStopLoss(), setup.getTakeProfit(), setup.getStatus(), setup.getCreatedAt())).toList();
        return new AnalysisDtos.AnalysisResponse(analysis.getId(), analysis.getSymbol(), analysis.getTimeframe(), analysis.getResultJson(), analysis.getCreatedAt(), tradeSetups);
    }
    public static class AnalysisNotFoundException extends RuntimeException {}
}