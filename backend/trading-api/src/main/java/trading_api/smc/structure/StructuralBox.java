package trading_api.smc.structure;

import java.math.BigDecimal;

public record StructuralBox(
        SwingPoint majorHigh,
        SwingPoint majorLow
) {
    public boolean present() {
        return majorHigh != null && majorLow != null
                && majorHigh.price().compareTo(majorLow.price()) > 0;
    }

    public BigDecimal range() {
        return present() ? majorHigh.price().subtract(majorLow.price()) : BigDecimal.ZERO;
    }
}