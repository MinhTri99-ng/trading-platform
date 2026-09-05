package trading_api.smc.structure;

import java.util.List;

public record SMCMarketStructureResult(
        List<SwingPoint> swings,
        List<StructureLabel> labels,
        List<SwingPoint> internalStructure,
        List<SwingPoint> externalStructure,
        List<ProtectedLevel> protectedHighs,
        List<ProtectedLevel> protectedLows,
        String trend
) {
}
