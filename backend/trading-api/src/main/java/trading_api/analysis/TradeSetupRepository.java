package trading_api.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradeSetupRepository extends JpaRepository<TradeSetup, Long> {
    List<TradeSetup> findByAnalysisIdAndUserId(Long analysisId, Long userId);
}