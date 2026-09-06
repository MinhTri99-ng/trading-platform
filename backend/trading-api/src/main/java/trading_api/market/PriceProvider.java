package trading_api.market;

import java.math.BigDecimal;
import java.util.Optional;

public interface PriceProvider {
    boolean supports(String symbol);

    Optional<BigDecimal> getLivePrice(String symbol);
}
