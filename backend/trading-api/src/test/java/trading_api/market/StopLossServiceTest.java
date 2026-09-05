package trading_api.market;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StopLossServiceTest {

    private final StopLossService stopLossService =
            new StopLossService();

    @Test
    void shouldCalculateLongStopLoss() {
        BigDecimal entry = BigDecimal.valueOf(100);
        BigDecimal structureInvalidation = BigDecimal.valueOf(95);
        BigDecimal atr = BigDecimal.valueOf(2);

        BigDecimal stopLoss = stopLossService.calculate(
                entry,
                structureInvalidation,
                atr,
                StopLossService.Direction.LONG
        );

        assertEquals(
                new BigDecimal("93"),
                stopLoss
        );
    }

    @Test
    void shouldCalculateShortStopLoss() {
        BigDecimal entry = BigDecimal.valueOf(100);
        BigDecimal structureInvalidation = BigDecimal.valueOf(105);
        BigDecimal atr = BigDecimal.valueOf(2);

        BigDecimal stopLoss = stopLossService.calculate(
                entry,
                structureInvalidation,
                atr,
                StopLossService.Direction.SHORT
        );

        assertEquals(
                new BigDecimal("107"),
                stopLoss
        );
    }

    @Test
    void shouldRejectInvalidLongStopLoss() {
        assertThrows(
                IllegalArgumentException.class,
                () -> stopLossService.calculate(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(100),
                        BigDecimal.ZERO,
                        StopLossService.Direction.LONG
                )
        );
    }

    @Test
    void shouldRejectInvalidShortStopLoss() {
        assertThrows(
                IllegalArgumentException.class,
                () -> stopLossService.calculate(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(100),
                        BigDecimal.ZERO,
                        StopLossService.Direction.SHORT
                )
        );
    }
}
