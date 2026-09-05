package trading_api.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    List<Analysis> findByUserIdOrderByCreatedAtDesc(Long userId);
    java.util.Optional<Analysis> findByIdAndUserId(Long id, Long userId);
}