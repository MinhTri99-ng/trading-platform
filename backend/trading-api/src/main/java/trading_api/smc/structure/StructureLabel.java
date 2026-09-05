package trading_api.smc.structure;

import java.math.BigDecimal;
import java.time.Instant;

public record StructureLabel(
        int index,
        BigDecimal price,
        StructureType type,
        Instant timestamp,
        String timeframe
) {
}
