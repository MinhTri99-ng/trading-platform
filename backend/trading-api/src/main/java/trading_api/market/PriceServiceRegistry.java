package trading_api.market;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PriceServiceRegistry {
    private final List<PriceProvider> providers;

    public PriceServiceRegistry(List<PriceProvider> providers) {
        this.providers = providers;
    }

    public Optional<BigDecimal> getLivePrice(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        return providers.stream()
                .filter(provider -> provider.supports(normalizedSymbol))
                .map(provider -> livePrice(provider, normalizedSymbol))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<BigDecimal> livePrice(PriceProvider provider, String symbol) {
        try {
            return provider.getLivePrice(symbol)
                    .filter(price -> price.compareTo(BigDecimal.ZERO) > 0);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }
        return symbol.replace("/", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }
}
