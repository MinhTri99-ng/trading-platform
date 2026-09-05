package trading_api.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StrategyParametersService {
    private static final Logger log = LoggerFactory.getLogger(StrategyParametersService.class);
    private final StrategyParametersRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, StrategyParameters> cache = new ConcurrentHashMap<>();

    public StrategyParametersService(StrategyParametersRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void initialize() {
        try {
            ClassPathResource resource = new ClassPathResource("strategy_v1.json");
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    StrategyParameters payload = objectMapper.readValue(inputStream, StrategyParameters.class);
                    upsert(payload);
                }
            } else {
                StrategyParameters defaults = new StrategyParameters();
                upsert(defaults);
            }
        } catch (Exception ex) {
            log.error("Failed to initialize strategy cache", ex);
        }
    }

    @Transactional
    public StrategyParameters upsert(StrategyParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Strategy parameters cannot be null");
        }

        parameters.setUpdatedAt(Instant.now());
        if (parameters.getCreatedAt() == null) {
            parameters.setCreatedAt(Instant.now());
        }

        StrategyParameters saved = repository.save(parameters);
        cache.put(saved.getStrategyName(), saved);
        log.info("[Strategy updated] strategyName={}, version={}, active={}", saved.getStrategyName(), saved.getStrategyVersion(), saved.isActive());
        return saved;
    }

    @Transactional
    public StrategyParameters applyRequest(StrategyParametersRequest request) {
        StrategyParameters parameters = new StrategyParameters();
        parameters.setStrategyName(request.strategyName());
        parameters.setStrategyVersion(request.strategyVersion());
        parameters.setEmaFast(request.emaFast());
        parameters.setEmaSlow(request.emaSlow());
        parameters.setRsiPeriod(request.rsiPeriod());
        parameters.setRsiOverbought(request.rsiOverbought());
        parameters.setRsiOversold(request.rsiOversold());
        parameters.setAtrMultiplier(request.atrMultiplier());
        parameters.setRiskReward(request.riskReward());
        parameters.setRiskPercent(request.riskPercent());
        parameters.setActive(Boolean.TRUE.equals(request.active()));
        return upsert(parameters);
    }

    public StrategyParameters getActiveStrategy() {
        Optional<StrategyParameters> cached = Optional.ofNullable(cache.get("default"));
        if (cached.isPresent()) {
            return cached.get();
        }

        Optional<StrategyParameters> stored = repository.findFirstByStrategyNameAndActiveTrueOrderByUpdatedAtDesc("default");
        if (stored.isPresent()) {
            cache.put("default", stored.get());
            return stored.get();
        }

        StrategyParameters fallback = new StrategyParameters();
        cache.put("default", fallback);
        return fallback;
    }

    public StrategyParameters getActiveStrategy(String strategyName) {
        String normalized = strategyName == null || strategyName.isBlank() ? "default" : strategyName;
        return cache.computeIfAbsent(normalized, key -> repository.findFirstByStrategyNameAndActiveTrueOrderByUpdatedAtDesc(normalized)
            .orElseGet(() -> {
                StrategyParameters fallback = new StrategyParameters();
                fallback.setStrategyName(normalized);
                return fallback;
            }));
    }
}
