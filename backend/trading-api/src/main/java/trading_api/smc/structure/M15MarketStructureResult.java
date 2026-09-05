package trading_api.smc.structure;

import java.util.List;

public record M15MarketStructureResult(
        List<SwingPoint> swings,
        List<StructureLabel> labels,
        StructuralBox structuralBox,
        List<StructureBreak> breaks,
        DirectionalBias directionalBias
) {
    public static M15MarketStructureResult empty() {
        return new M15MarketStructureResult(List.of(), List.of(), new StructuralBox(null, null), List.of(), DirectionalBias.RANGE);
    }
}