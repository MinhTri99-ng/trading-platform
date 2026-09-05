package trading_api.strategy;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record StrategyParametersRequest(
    @NotBlank(message = "strategyName is required") String strategyName,
    @NotBlank(message = "strategyVersion is required") String strategyVersion,
    @NotNull @DecimalMin("1") @DecimalMax("200") BigDecimal emaFast,
    @NotNull @DecimalMin("2") @DecimalMax("300") BigDecimal emaSlow,
    @NotNull @DecimalMin("2") @DecimalMax("100") BigDecimal rsiPeriod,
    @NotNull @DecimalMin("50") @DecimalMax("90") BigDecimal rsiOverbought,
    @NotNull @DecimalMin("10") @DecimalMax("50") BigDecimal rsiOversold,
    @NotNull @DecimalMin("0.1") @DecimalMax("10") BigDecimal atrMultiplier,
    @NotNull @DecimalMin("1.0") @DecimalMax("10") BigDecimal riskReward,
    @NotNull @DecimalMin("0.01") @DecimalMax("10") BigDecimal riskPercent,
    @NotNull Boolean active
) {}
