package trading_api.market;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TakeProfitServiceTest {

    private final TakeProfitService takeProfitService =
            new TakeProfitService();

    @Test
    void shouldCalculateLongTakeProfit() {
        TradeRiskResult result = takeProfitService.calculate(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(93),
                TradeRiskResult.Direction.LONG,
                BigDecimal.valueOf(2)
        );

        assertEquals(
                BigDecimal.valueOf(100),
                result.entry()
        );
        assertEquals(
                BigDecimal.valueOf(93),
                result.stopLoss()
        );
        assertEquals(
                BigDecimal.valueOf(114),
                result.takeProfit()
        );
        assertEquals(
                BigDecimal.valueOf(7),
                result.risk()
        );
        assertEquals(
                BigDecimal.valueOf(14),
                result.reward()
        );
        assertEquals(
                BigDecimal.valueOf(2),
                result.riskRewardRatio()
        );
    }

    @Test
    void shouldCalculateShortTakeProfit() {
        TradeRiskResult result = takeProfitService.calculate(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(107),
                TradeRiskResult.Direction.SHORT,
                BigDecimal.valueOf(2)
        );

        assertEquals(
                BigDecimal.valueOf(100),
                result.entry()
        );
        assertEquals(
                BigDecimal.valueOf(107),
                result.stopLoss()
        );
        assertEquals(
                BigDecimal.valueOf(86),
                result.takeProfit()
        );
        assertEquals(
                BigDecimal.valueOf(7),
                result.risk()
        );
        assertEquals(
                BigDecimal.valueOf(14),
                result.reward()
        );
        assertEquals(
                BigDecimal.valueOf(2),
                result.riskRewardRatio()
        );
    }

    @Test
    void shouldRejectInvalidLongTakeProfit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> takeProfitService.calculate(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(100),
                        TradeRiskResult.Direction.LONG,
                        BigDecimal.valueOf(2)
                )
        );
    }

    @Test
    void shouldRejectInvalidShortTakeProfit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> takeProfitService.calculate(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(100),
                        TradeRiskResult.Direction.SHORT,
                        BigDecimal.valueOf(2)
                )
        );
    }

    @Test
    void shouldRejectInvalidRiskRewardRatio() {
        assertThrows(
                IllegalArgumentException.class,
                () -> takeProfitService.calculate(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(95),
                        TradeRiskResult.Direction.LONG,
                        BigDecimal.valueOf(0)
                )
        );
    }
}
