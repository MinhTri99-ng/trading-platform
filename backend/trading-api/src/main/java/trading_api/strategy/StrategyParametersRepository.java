package trading_api.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StrategyParametersRepository extends JpaRepository<StrategyParameters, Long> {
    Optional<StrategyParameters> findFirstByStrategyNameOrderByUpdatedAtDesc(String strategyName);
    List<StrategyParameters> findByStrategyNameOrderByUpdatedAtDesc(String strategyName);
    Optional<StrategyParameters> findFirstByStrategyNameAndActiveTrueOrderByUpdatedAtDesc(String strategyName);
}
