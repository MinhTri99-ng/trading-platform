package trading_api.market;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskManagementServiceTest {

    private final RiskManagementService service =
            new RiskManagementService();

    @Test
    void shouldCalculateLongPositionSize() {
        RiskManagementService.RiskManagementResult result =
                service.calculatePositionSize(
                        RiskManagementService.Direction.LONG,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(1),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(95)
                );

        assertEquals(
                BigDecimal.valueOf(1),
                result.riskAmount()
        );
        assertEquals(
                BigDecimal.valueOf(5),
                result.riskPerUnit()
        );
        assertEquals(
                BigDecimal.valueOf(0.2),
                result.positionSize()
        );
        assertEquals(
                0,
                result.positionSize().multiply(result.riskPerUnit()).compareTo(result.riskAmount())
        );
    }

    @Test
    void shouldCalculateShortPositionSize() {
        RiskManagementService.RiskManagementResult result =
                service.calculatePositionSize(
                        RiskManagementService.Direction.SHORT,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(1),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(105)
                );

        assertEquals(
                BigDecimal.valueOf(1),
                result.riskAmount()
        );
        assertEquals(
                BigDecimal.valueOf(5),
                result.riskPerUnit()
        );
        assertEquals(
                BigDecimal.valueOf(0.2),
                result.positionSize()
        );
    }

    @Test
    void shouldCalculateDifferentBalancePositionSize() {
        RiskManagementService.RiskManagementResult result =
                service.calculatePositionSize(
                        RiskManagementService.Direction.LONG,
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(2),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(95)
                );

        assertEquals(
                BigDecimal.valueOf(20),
                result.riskAmount()
        );
        assertEquals(
                BigDecimal.valueOf(5),
                result.riskPerUnit()
        );
        assertEquals(
                BigDecimal.valueOf(4),
                result.positionSize()
        );
    }

    @Test
    void shouldRejectInvalidLongStopLoss() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculatePositionSize(
                        RiskManagementService.Direction.LONG,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(1),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(105)
                )
        );
    }

    @Test
    void shouldRejectInvalidShortStopLoss() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculatePositionSize(
                        RiskManagementService.Direction.SHORT,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(1),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(95)
                )
        );
    }

    @Test
    void shouldRejectZeroRiskPercent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculatePositionSize(
                        RiskManagementService.Direction.LONG,
                        BigDecimal.valueOf(100),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(95)
                )
        );
    }

    @Test
    void shouldRejectNegativeRiskPercent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculatePositionSize(
                        RiskManagementService.Direction.LONG,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(-1),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(95)
                )
        );
    }

    @Test
    void shouldRejectRiskAboveHundredPercent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculatePositionSize(
                        RiskManagementService.Direction.LONG,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(101),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(95)
                )
        );
    }

    @Test
    void shouldRejectZeroBalance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculatePositionSize(
                        RiskManagementService.Direction.LONG,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(1),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(95)
                )
        );
    }

    @Test
    void shouldHandleVerySmallStopDistanceSafely() {
        RiskManagementService.RiskManagementResult result =
                service.calculatePositionSize(
                        RiskManagementService.Direction.LONG,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(1),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(99.9)
                );

        assertEquals(
                BigDecimal.valueOf(1),
                result.riskAmount()
        );
        assertEquals(
                BigDecimal.valueOf(0.1),
                result.riskPerUnit()
        );
        assertEquals(
                BigDecimal.valueOf(10),
                result.positionSize()
        );
    }

    @Test
    void shouldMaintainRiskInvariantForLongAndShort() {
        RiskManagementService.RiskManagementResult longResult =
                service.calculatePositionSize(
                        RiskManagementService.Direction.LONG,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(1),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(95)
                );

        RiskManagementService.RiskManagementResult shortResult =
                service.calculatePositionSize(
                        RiskManagementService.Direction.SHORT,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(1),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(105)
                );

        assertEquals(
                0,
                longResult.positionSize().multiply(longResult.riskPerUnit()).compareTo(longResult.riskAmount())
        );

        assertEquals(
                0,
                shortResult.positionSize().multiply(shortResult.riskPerUnit()).compareTo(shortResult.riskAmount())
        );
    }
}
