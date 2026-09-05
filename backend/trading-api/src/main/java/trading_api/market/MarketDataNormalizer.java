package trading_api.market;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class MarketDataNormalizer {

    public NormalizedTrade normalize(MarketTrade trade) {

        return new NormalizedTrade(
                trade.getTradeId(),
                trade.getSymbol(),
                BigDecimal.valueOf(trade.getPrice()),
                BigDecimal.valueOf(trade.getQuantity()),
                trade.getTimestamp()
        );
    }
}
