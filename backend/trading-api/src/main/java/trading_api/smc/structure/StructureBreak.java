package trading_api.smc.structure;

import java.math.BigDecimal;
import java.time.Instant;

public record StructureBreak(
        BreakType type,
        SwingPoint brokenSwing,
        BigDecimal breakPrice,
        Instant breakTimestamp,
        boolean closeConfirmed,
        SwingPoint.SwingDirection direction
) {
    public enum BreakType {
        BOS,
        CHOCH
    }
}