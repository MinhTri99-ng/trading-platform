package trading_api.market;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class StopLossService {

    public static final BigDecimal ATR_BUFFER_MULTIPLIER =
            BigDecimal.ONE;

    private static final MathContext MC =
            new MathContext(20, RoundingMode.HALF_UP);

    public BigDecimal calculate(
            BigDecimal entry,
            BigDecimal structureInvalidation,
            BigDecimal atr,
            Direction direction
    ) {
        validateInput(entry, "Entry");
        validateInput(structureInvalidation, "Structure invalidation");
        validateInput(atr, "ATR");

        if (direction == null) {
            throw new IllegalArgumentException(
                    "Direction cannot be null"
            );
        }

        BigDecimal adjustedBuffer = atr.multiply(
                ATR_BUFFER_MULTIPLIER,
                MC
        );

        BigDecimal stopLoss;

        if (direction == Direction.LONG) {
            stopLoss = structureInvalidation.subtract(adjustedBuffer, MC);

            if (stopLoss.compareTo(entry) >= 0) {
                throw new IllegalArgumentException(
                        "LONG stop loss must be below entry"
                );
            }

        } else if (direction == Direction.SHORT) {
            stopLoss = structureInvalidation.add(adjustedBuffer, MC);

            if (stopLoss.compareTo(entry) <= 0) {
                throw new IllegalArgumentException(
                        "SHORT stop loss must be above entry"
                );
            }

        } else {
            throw new IllegalArgumentException(
                    "Unsupported direction: " + direction
            );
        }

        return stopLoss;
    }

    private void validateInput(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(
                    name + " cannot be null"
            );
        }

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    name + " cannot be negative"
            );
        }
    }

    public enum Direction {
        LONG,
        SHORT
    }
}
