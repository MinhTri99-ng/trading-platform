package trading_api.market;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "market")
public class MarketProperties {
    private Map<String, BigDecimal> fallbackPrices = new LinkedHashMap<>();

    public Map<String, BigDecimal> getFallbackPrices() {
        return fallbackPrices;
    }

    public void setFallbackPrices(Map<String, BigDecimal> fallbackPrices) {
        this.fallbackPrices = fallbackPrices;
    }
}
