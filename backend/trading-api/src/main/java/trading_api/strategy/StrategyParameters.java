package trading_api.strategy;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "strategy_parameters",
    indexes = {
        @Index(name = "idx_strategy_name_version", columnList = "strategy_name, strategy_version"),
        @Index(name = "idx_strategy_active", columnList = "is_active")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_strategy_active", columnNames = {"strategy_name", "strategy_version"})
    }
)
public class StrategyParameters {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_name", nullable = false, length = 100)
    @NotBlank
    private String strategyName = "default";

    @Column(name = "strategy_version", nullable = false, length = 50)
    @NotBlank
    private String strategyVersion = "v1";

    @Column(name = "ema_fast", nullable = false, precision = 10, scale = 2)
    @NotNull @DecimalMin("1") @DecimalMax("200")
    private BigDecimal emaFast = BigDecimal.valueOf(12);

    @Column(name = "ema_slow", nullable = false, precision = 10, scale = 2)
    @NotNull @DecimalMin("2") @DecimalMax("300")
    private BigDecimal emaSlow = BigDecimal.valueOf(26);

    @Column(name = "rsi_period", nullable = false, precision = 10, scale = 2)
    @NotNull @DecimalMin("2") @DecimalMax("100")
    private BigDecimal rsiPeriod = BigDecimal.valueOf(14);

    @Column(name = "rsi_overbought", nullable = false, precision = 10, scale = 2)
    @NotNull @DecimalMin("50") @DecimalMax("90")
    private BigDecimal rsiOverbought = BigDecimal.valueOf(70);

    @Column(name = "rsi_oversold", nullable = false, precision = 10, scale = 2)
    @NotNull @DecimalMin("10") @DecimalMax("50")
    private BigDecimal rsiOversold = BigDecimal.valueOf(30);

    @Column(name = "atr_multiplier", nullable = false, precision = 10, scale = 4)
    @NotNull @DecimalMin("0.1") @DecimalMax("10")
    private BigDecimal atrMultiplier = BigDecimal.valueOf(1.5);

    @Column(name = "risk_reward", nullable = false, precision = 10, scale = 4)
    @NotNull @DecimalMin("1.0") @DecimalMax("10")
    private BigDecimal riskReward = BigDecimal.valueOf(1.5);

    @Column(name = "risk_percent", nullable = false, precision = 10, scale = 4)
    @NotNull @DecimalMin("0.01") @DecimalMax("10")
    private BigDecimal riskPercent = BigDecimal.valueOf(1.0);

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }
    public String getStrategyVersion() { return strategyVersion; }
    public void setStrategyVersion(String strategyVersion) { this.strategyVersion = strategyVersion; }
    public BigDecimal getEmaFast() { return emaFast; }
    public void setEmaFast(BigDecimal emaFast) { this.emaFast = emaFast; }
    public BigDecimal getEmaSlow() { return emaSlow; }
    public void setEmaSlow(BigDecimal emaSlow) { this.emaSlow = emaSlow; }
    public BigDecimal getRsiPeriod() { return rsiPeriod; }
    public void setRsiPeriod(BigDecimal rsiPeriod) { this.rsiPeriod = rsiPeriod; }
    public BigDecimal getRsiOverbought() { return rsiOverbought; }
    public void setRsiOverbought(BigDecimal rsiOverbought) { this.rsiOverbought = rsiOverbought; }
    public BigDecimal getRsiOversold() { return rsiOversold; }
    public void setRsiOversold(BigDecimal rsiOversold) { this.rsiOversold = rsiOversold; }
    public BigDecimal getAtrMultiplier() { return atrMultiplier; }
    public void setAtrMultiplier(BigDecimal atrMultiplier) { this.atrMultiplier = atrMultiplier; }
    public BigDecimal getRiskReward() { return riskReward; }
    public void setRiskReward(BigDecimal riskReward) { this.riskReward = riskReward; }
    public BigDecimal getRiskPercent() { return riskPercent; }
    public void setRiskPercent(BigDecimal riskPercent) { this.riskPercent = riskPercent; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
