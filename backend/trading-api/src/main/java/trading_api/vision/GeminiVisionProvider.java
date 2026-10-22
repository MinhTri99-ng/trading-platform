package trading_api.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GeminiVisionProvider implements ChartVisionProvider {
    private static final Logger log = LoggerFactory.getLogger(GeminiVisionProvider.class);
    private static final String DEFAULT_KEY = "AQ_YOUR_API_KEY_HERE";

    private final List<String> apiKeys;
    private final AtomicInteger nextKeyIndex = new AtomicInteger();
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiVisionProvider(
            @Value("${gemini.api.key:AQ_YOUR_API_KEY_HERE}") String apiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String model,
            ObjectMapper objectMapper
    ) {
        this.apiKeys = parseApiKeys(apiKey);
        this.model = model;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ChartVisionResult analyze(byte[] imageBytes, String fileName) {
        if (apiKeys.isEmpty()) {
            return ChartVisionResult.unavailable("Gemini API key is not configured.");
        }

        try {
            String requestBody = objectMapper.writeValueAsString(new GeminiRequest(
                    List.of(new Content(List.of(
                            new Part("""
                                    Analyze this trading chart image. Return JSON only with these fields:
                                    symbol, timeframe, market, chartType, confidence, visibleIndicators,
                                    visiblePriceLevels, detectedText, detectedRegions, estimatedPriceHigh,
                                    estimatedPriceLow. Detect the ticker and timeframe from chart labels,
                                    and classify market as Crypto, Stocks, Forex, Commodities, or Custom.
                                    """),
                            new Part(imageMimeType(imageBytes), Base64.getEncoder().encodeToString(imageBytes))
                    )))
            ));

            int startIndex = Math.floorMod(nextKeyIndex.getAndIncrement(), apiKeys.size());
            for (int attempt = 0; attempt < apiKeys.size(); attempt++) {
                String key = apiKeys.get((startIndex + attempt) % apiKeys.size());
                HttpResponse<String> response = sendRequest(requestBody, key);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String text = extractText(response.body());
                    if (!text.isBlank()) {
                        return parseResult(text);
                    }
                    log.warn("[Gemini Vision] Key attempt returned empty content; trying next key");
                    continue;
                }
                if (isKeyFailoverResponse(response)) {
                    log.warn("[Gemini Vision] Key attempt failed with retryable status {}; trying next key",
                            response.statusCode());
                    continue;
                }
                log.warn("[Gemini Vision] API request failed with non-retryable status {}", response.statusCode());
                return ChartVisionResult.unavailable("Gemini Vision API request failed.");
            }
            return ChartVisionResult.unavailable("All configured Gemini Vision API keys failed.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[Gemini Vision] Request interrupted");
            return ChartVisionResult.unavailable("Gemini Vision request interrupted.");
        } catch (IOException | RuntimeException ex) {
            log.warn("[Gemini Vision] Request or response parsing failed: {}", ex.getMessage());
            return ChartVisionResult.unavailable("Gemini Vision request failed.");
        }
    }

    private HttpResponse<String> sendRequest(String requestBody, String apiKey)
            throws IOException, InterruptedException {
        URI endpoint = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean isKeyFailoverResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 401 || status == 403 || status == 429) {
            return true;
        }
        String body = response.body() == null ? "" : response.body().toLowerCase(Locale.ROOT);
        return body.contains("quota") || body.contains("rate limit")
                || body.contains("resource_exhausted") || body.contains("resource exhausted");
    }

    private ChartVisionResult parseResult(String responseText) throws IOException {
        String json = responseText.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        JsonNode root = objectMapper.readTree(json);
        return new ChartVisionResult(
                "GEMINI_ANALYSIS",
                text(root, "symbol"),
                text(root, "timeframe"),
                strings(root, "visibleIndicators"),
                strings(root, "visiblePriceLevels"),
                text(root, "chartType"),
                number(root, "confidence"),
                strings(root, "detectedText"),
                strings(root, "detectedRegions"),
                number(root, "estimatedPriceHigh"),
                number(root, "estimatedPriceLow"),
                List.of(),
                text(root, "market")
        );
    }

    private String extractText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return "";
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.has("text")) {
                result.append(part.get("text").asText());
            }
        }
        return result.toString();
    }

    private List<String> strings(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (value.isArray()) {
            return java.util.stream.StreamSupport.stream(value.spliterator(), false)
                    .map(JsonNode::asText)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        String text = value.asText("");
        return text.isBlank() ? List.of() : List.of(text);
    }

    private String text(JsonNode root, String field) {
        return root.path(field).asText(null);
    }

    private Double number(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isNumber() ? value.doubleValue() : null;
    }

    private boolean isPlaceholderKey(String value) {
        return value == null || value.isBlank()
                || DEFAULT_KEY.equals(value.trim())
                || value.trim().toUpperCase(Locale.ROOT).contains("YOUR_API_KEY");
    }

    private List<String> parseApiKeys(String rawKeys) {
        if (rawKeys == null || rawKeys.isBlank()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (String rawKey : rawKeys.split(",")) {
            String key = rawKey.trim();
            if (!isPlaceholderKey(key) && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    private String imageMimeType(byte[] bytes) {
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        return "image/jpeg";
    }

    private record GeminiRequest(List<Content> contents) {
    }

    private record Content(List<Part> parts) {
    }

    private record Part(String text, InlineData inlineData) {
        private Part(String text) {
            this(text, (InlineData) null);
        }

        private Part(String mimeType, String data) {
            this(null, new InlineData(mimeType, data));
        }
    }

    private record InlineData(String mimeType, String data) {
    }
}
