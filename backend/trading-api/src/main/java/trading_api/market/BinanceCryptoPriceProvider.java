package trading_api.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Optional;

@Service
public class BinanceCryptoPriceProvider implements PriceProvider {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BinanceCryptoPriceProvider() {
        this(HttpClient.newHttpClient(), new ObjectMapper());
    }

    BinanceCryptoPriceProvider(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String symbol) {
        String normalized = normalizeSymbol(symbol);
        return normalized.endsWith("USDT")
                || normalized.endsWith("USDC")
                || normalized.endsWith("BUSD")
                || normalized.endsWith("BTC")
                || normalized.endsWith("ETH")
                || normalized.endsWith("BNB");
    }

    @Override
    public Optional<BigDecimal> getLivePrice(String symbol) {
        if (!supports(symbol)) {
            return Optional.empty();
        }
        try {
            String normalizedSymbol = normalizeSymbol(symbol);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.binance.com/api/v3/ticker/price?symbol=" + normalizedSymbol))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode price = objectMapper.readTree(response.body()).get("price");
            return price == null ? Optional.empty() : Optional.of(new BigDecimal(price.asText()));
        } catch (IOException ex) {
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
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
