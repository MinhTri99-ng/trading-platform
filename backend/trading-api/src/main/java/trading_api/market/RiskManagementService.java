package trading_api.market;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class RiskManagementService {

    public static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);

    private static final MathContext RISK_MATH_CONTEXT =
            new MathContext(20, RoundingMode.HALF_UP);

    private static final MathContext POSITION_SIZE_CONTEXT =
            new MathContext(20, RoundingMode.FLOOR);

    public enum Direction {
        LONG,
        SHORT
    }

    public RiskManagementResult calculatePositionSize(
            BigDecimal accountBalance,
            BigDecimal riskPercent,
            BigDecimal entry,
            BigDecimal stopLoss
    ) {
        Direction direction = determineDirection(entry, stopLoss);
        return calculatePositionSize(
                direction,
                accountBalance,
                riskPercent,
                entry,
                stopLoss
        );
    }

    public RiskManagementResult calculatePositionSize(
            Direction direction,
            BigDecimal accountBalance,
            BigDecimal riskPercent,
            BigDecimal entry,
            BigDecimal stopLoss
    ) {
        validateAccountBalance(accountBalance);
        validateRiskPercent(riskPercent);
        validatePrice(entry, "Entry");
        validatePrice(stopLoss, "Stop loss");

        if (direction == null) {
            throw new IllegalArgumentException(
                    "Direction cannot be null"
            );
        }

        validateStopLoss(direction, entry, stopLoss);

        BigDecimal riskAmount = accountBalance
                .multiply(riskPercent, RISK_MATH_CONTEXT)
                .divide(HUNDRED, RISK_MATH_CONTEXT);

        if (riskAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Risk amount must be greater than zero"
            );
        }

        BigDecimal riskPerUnit = direction == Direction.LONG
                ? entry.subtract(stopLoss, RISK_MATH_CONTEXT).abs()
                : stopLoss.subtract(entry, RISK_MATH_CONTEXT).abs();

        if (riskPerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Risk per unit must be greater than zero"
            );
        }

        BigDecimal positionSize = riskAmount
                .divide(
                        riskPerUnit,
                        POSITION_SIZE_CONTEXT
                );

        if (positionSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Position size must be greater than zero"
            );
        }

        BigDecimal plannedLoss = positionSize
                .multiply(riskPerUnit, RISK_MATH_CONTEXT);

        if (plannedLoss.compareTo(riskAmount) > 0) {
            throw new IllegalArgumentException(
                    "Planned loss cannot exceed the allowed risk amount"
            );
        }

        return new RiskManagementResult(
                normalizePlainDecimal(riskAmount),
                normalizePlainDecimal(riskPerUnit),
                normalizePlainDecimal(positionSize)
        );
    }

    private BigDecimal normalizePlainDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }

        return new BigDecimal(value.toPlainString());
    }

    private Direction determineDirection(
            BigDecimal entry,
            BigDecimal stopLoss
    ) {
        int comparison = stopLoss.compareTo(entry);

        if (comparison < 0) {
            return Direction.LONG;
        }

        if (comparison > 0) {
            return Direction.SHORT;
        }

        throw new IllegalArgumentException(
                "Stop loss must be either above or below entry depending on direction"
        );
    }

    private void validateAccountBalance(BigDecimal accountBalance) {
        if (accountBalance == null) {
            throw new IllegalArgumentException(
                    "Account balance cannot be null"
            );
        }

        if (accountBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Account balance must be greater than zero"
            );
        }
    }

    private void validateRiskPercent(BigDecimal riskPercent) {
        if (riskPercent == null) {
            throw new IllegalArgumentException(
                    "Risk percentage cannot be null"
            );
        }

        if (riskPercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Risk percentage must be greater than zero"
            );
        }

        if (riskPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "Risk percentage cannot exceed 100%"
            );
        }
    }

    private void validatePrice(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(
                    name + " cannot be null"
            );
        }

        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    name + " must be greater than zero"
            );
        }
    }

    private void validateStopLoss(
            Direction direction,
            BigDecimal entry,
            BigDecimal stopLoss
    ) {
        if (direction == Direction.LONG) {
            if (stopLoss.compareTo(entry) >= 0) {
                throw new IllegalArgumentException(
                        "LONG stop loss must be lower than entry"
                );
            }
            return;
        }

        if (direction == Direction.SHORT) {
            if (stopLoss.compareTo(entry) <= 0) {
                throw new IllegalArgumentException(
                        "SHORT stop loss must be higher than entry"
                );
            }
            return;
        }

        throw new IllegalArgumentException(
                "Unsupported direction: " + direction
        );
    }

    public record RiskManagementResult(
            BigDecimal riskAmount,
            BigDecimal riskPerUnit,
            BigDecimal positionSize
    ) {
    }
}
