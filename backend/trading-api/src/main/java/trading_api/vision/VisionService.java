package trading_api.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VisionService {
    private static final Logger log = LoggerFactory.getLogger(VisionService.class);
    private final ChartVisionProvider chartVisionProvider;

    private static final Pattern SYMBOL_PATTERN = Pattern.compile(
            "(?i)\\b(?:BTC|ETH|SOL|XRP|BNB|ADA|DOGE|LINK|AVAX|TRX)(?:[/\\-_ ]?(?:USD|USDT|BUSD|USDC|BTC))?\\b"
    );
    private static final Pattern TIMEFRAME_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:12h|15m|30m|1w|1d|4h|3h|2h|1h|5m|1m)\\b|\\b(?:d|day|daily|w|week|weekly)\\b)"
    );
    private static final List<String> VALID_TIMEFRAMES = List.of("1w", "1d", "12h", "4h", "3h", "2h", "1h", "30m", "15m", "5m", "1m");
    private static final Map<String, String> TIMEFRAME_MAP = Map.ofEntries(
            Map.entry("1m", "1m"),
            Map.entry("5m", "5m"),
            Map.entry("15m", "15m"),
            Map.entry("30m", "30m"),
            Map.entry("1h", "1h"),
            Map.entry("2h", "2h"),
            Map.entry("3h", "3h"),
            Map.entry("4h", "4h"),
            Map.entry("12h", "12h"),
            Map.entry("1d", "1d"),
            Map.entry("1w", "1w"),
            Map.entry("d", "1d"),
            Map.entry("day", "1d"),
            Map.entry("daily", "1d"),
            Map.entry("w", "1w"),
            Map.entry("week", "1w"),
            Map.entry("weekly", "1w")
    );

    public VisionService() {
        this(new LocalChartVisionProvider());
    }

    public VisionService(ChartVisionProvider chartVisionProvider) {
        this.chartVisionProvider = chartVisionProvider;
    }

    public VisionService(ChartVisionAnalyzer chartVisionAnalyzer) {
        this((ChartVisionProvider) (imageBytes, fileName) -> chartVisionAnalyzer.detectTimeframe(imageBytes)
                .map(timeframe -> new ChartVisionResult(
                        "PARTIAL_DETECTION", null, timeframe, List.of(), List.of(), null,
                        0d, List.of(), List.of(), null, null, List.of("Timeframe-only analyzer"))
                )
                .orElseGet(() -> ChartVisionResult.unavailable("No chart vision result was produced.")));
    }

    public ChartImageMetadata extractMetadata(byte[] imageBytes) {
        return extractMetadata(imageBytes, null, null);
    }

    public ChartImageMetadata extractMetadata(byte[] imageBytes, String fileName) {
        return extractMetadata(imageBytes, fileName, null, null);
    }

    public ChartImageMetadata extractMetadata(byte[] imageBytes, String fileName, String requestedTimeframe) {
        return extractMetadata(imageBytes, fileName, null, requestedTimeframe);
    }

    public ChartImageMetadata extractMetadata(
            byte[] imageBytes,
            String fileName,
            String requestedSymbol,
            String requestedTimeframe
    ) {
        if (imageBytes == null || imageBytes.length == 0) {
            log.error("[Metadata extraction] Empty image payload");
            throw new IllegalArgumentException("INVALID INPUT: Empty image payload");
        }

        BufferedImage decodedImage = decodeImage(imageBytes);
        if (decodedImage == null) {
            log.error("[Metadata extraction] Unsupported image payload. bytes={}, filename={}", imageBytes.length, fileName);
            throw new IllegalArgumentException("INVALID INPUT: Unsupported image format");
        }

        String textSource = buildTextSource(fileName);
        String normalizedText = textSource == null ? "" : textSource.trim();
        ChartVisionResult visionResult = chartVisionProvider.analyze(imageBytes, fileName);
        String rawSymbol = normalizeSymbol(visionResult.symbol());
        String rawTimeframe = normalizeTimeframe(visionResult.timeframe());
        String filenameSymbol = extractSymbol(normalizedText.replace('_', ' '));
        String filenameTimeframe = extractTimeframe(normalizedText);
        String pattern = visionResult.chartType() == null || visionResult.chartType().isBlank()
                ? "UNAVAILABLE"
                : visionResult.chartType();
        List<String> indicators = visionResult.visibleIndicators() == null ? List.of() : visionResult.visibleIndicators();
        String priceLevels = visionResult.visiblePriceLevels() == null
                ? ""
                : String.join(", ", visionResult.visiblePriceLevels());

        log.info("[TIMEFRAME INPUT] requested={}", requestedTimeframe);
        log.info("[VISION] status={} confidence={} warnings={}",
                visionResult.status(), visionResult.confidence(), visionResult.detectionWarnings());
        log.info("[VISION RAW] rawSymbol={} rawTimeframe={} textSource={}", rawSymbol, rawTimeframe, textSource);

        String symbol = normalizeSymbol(requestedSymbol);
        if (symbol.isBlank()) {
            symbol = rawSymbol;
        }
        if (symbol == null || symbol.isBlank()) {
            symbol = filenameSymbol;
        }

        String filenameFallbackTimeframe = filenameTimeframe == null ? inferTimeframeFromFileName(fileName) : filenameTimeframe;
        TimeframeResolver.Resolution resolution = new TimeframeResolver().resolveTimeframe(
                requestedTimeframe,
                rawTimeframe,
                filenameFallbackTimeframe
        );
        log.info("[TIMEFRAME RESOLUTION] requested={} vision={} filename={} resolved={} source={}",
                requestedTimeframe, rawTimeframe, filenameFallbackTimeframe, resolution.timeframe(), resolution.source());

        String normalizedSymbol = normalizeSymbol(symbol);
        String normalizedTimeframe = resolution.timeframe();

        if (normalizedSymbol == null || normalizedSymbol.isBlank()) {
            log.error("[Metadata extraction] Unable to determine symbol from image bytes or filename. width={}, height={}, filename={}",
                    decodedImage.getWidth(), decodedImage.getHeight(), fileName);
            throw new IllegalArgumentException("INVALID INPUT: MISSING_SYMBOL");
        }

        if (normalizedTimeframe == null || normalizedTimeframe.isBlank()) {
            log.error("[Metadata extraction] Unable to determine timeframe from image bytes or filename. width={}, height={}, filename={}",
                    decodedImage.getWidth(), decodedImage.getHeight(), fileName);
            throw new IllegalArgumentException("INVALID INPUT: TIMEFRAME_UNDETECTED");
        }

        log.info("[SCREENSHOT] detected symbol={} timeframe={}", normalizedSymbol, normalizedTimeframe);
        log.info("[NORMALIZED] symbol={} timeframe={}", normalizedSymbol, normalizedTimeframe);
        log.info("[Metadata extracted] symbol={}, timeframe={}, width={}, height={}, pattern={}, indicators={}, priceLevels={}",
                normalizedSymbol, normalizedTimeframe, decodedImage.getWidth(), decodedImage.getHeight(), pattern, indicators, priceLevels);

        return new ChartImageMetadata(
                normalizedSymbol,
                normalizedTimeframe,
                pattern,
                indicators,
                priceLevels,
                null,
                null,
                visionResult.confidence(),
                visionResult.status(),
                visionResult.detectedText(),
                visionResult.detectionWarnings()
        );
    }

    private BufferedImage decodeImage(byte[] imageBytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(imageBytes)) {
            return ImageIO.read(input);
        } catch (IOException ex) {
            log.warn("[Metadata extraction] Image decode failed: {}", ex.getMessage());
            return null;
        }
    }

    private String buildTextSource(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        return fileName.toLowerCase(Locale.ROOT);
    }

    private String inferSymbolFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.contains("btc")) return "BTCUSDT";
        if (lower.contains("eth")) return "ETHUSDT";
        if (lower.contains("sol")) return "SOLUSDT";
        if (lower.contains("xrp")) return "XRPUSDT";
        if (lower.contains("bnb")) return "BNBUSDT";
        return null;
    }

    private String inferTimeframeFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.contains("12h")) return "12h";
        if (lower.contains("15m")) return "15m";
        if (lower.contains("30m")) return "30m";
        if (lower.contains("1d") || lower.contains("daily") || lower.contains("day")) return "1d";
        if (lower.contains("1w") || lower.contains("weekly") || lower.contains("week")) return "1w";
        if (lower.contains("4h")) return "4h";
        if (lower.contains("3h")) return "3h";
        if (lower.contains("2h")) return "2h";
        if (lower.contains("1h")) return "1h";
        if (lower.contains("5m")) return "5m";
        if (lower.contains("1m")) return "1m";
        return null;
    }

    private String extractSymbol(String text) {
        Matcher matcher = SYMBOL_PATTERN.matcher(text);
        if (matcher.find()) {
            String raw = matcher.group(0).trim();
            return raw.replace("-", "/").replace("_", "/").replace(" ", "");
        }
        return null;
    }

    private String extractTimeframe(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = TIMEFRAME_PATTERN.matcher(text);
        if (matcher.find()) {
            String match = matcher.group(0).trim();
            String normalized = normalizeTimeframe(match);
            if (normalized != null && !normalized.isBlank()) {
                return normalized;
            }
        }

        String lower = text.toLowerCase(Locale.ROOT);
        for (String candidate : VALID_TIMEFRAMES) {
            if (lower.contains(candidate) && isTimeframeTokenBoundary(lower, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isTimeframeTokenBoundary(String text, String candidate) {
        int index = text.indexOf(candidate);
        if (index < 0) {
            return false;
        }

        char start = index == 0 ? 0 : text.charAt(index - 1);
        char end = index + candidate.length() >= text.length() ? 0 : text.charAt(index + candidate.length());

        return (index == 0 || !Character.isLetterOrDigit(start))
                && (index + candidate.length() >= text.length() || !Character.isLetterOrDigit(end));
    }

    private String detectPattern(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("breakout") || lower.contains("bullish breakout")) return "Breakout";
        if (lower.contains("retest") || lower.contains("pullback")) return "Retest";
        if (lower.contains("trend") || lower.contains("uptrend")) return "Trend Up";
        if (lower.contains("downtrend") || lower.contains("bearish")) return "Trend Down";
        if (lower.contains("range") || lower.contains("sideways")) return "Range";
        return "Trend";
    }

    private List<String> extractIndicators(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return Arrays.stream(new String[] {"ema", "rsi", "volume", "macd", "bollinger", "support", "resistance"})
                .filter(lower::contains)
                .toList();
    }

    private String extractKeyLevels(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("support")) return "Support zone detected";
        if (lower.contains("resistance")) return "Resistance zone detected";
        return "Price levels estimated from chart";
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }

        String cleaned = symbol.trim().replace("-", "/").replace("_", "/").replace(" ", "");
        String normalized = cleaned.toUpperCase(Locale.ROOT);

        if (normalized.equals("BTC") || normalized.equals("ETH") || normalized.equals("SOL") || normalized.equals("XRP")
                || normalized.equals("BNB") || normalized.equals("ADA") || normalized.equals("DOGE")
                || normalized.equals("LINK") || normalized.equals("AVAX") || normalized.equals("TRX")) {
            return normalized + "USDT";
        }

        if (normalized.contains("/")) {
            normalized = normalized.replace("/", "");
        }

        if (normalized.endsWith("USD") && !normalized.endsWith("USDT")) {
            normalized = normalized.substring(0, normalized.length() - 3) + "USDT";
        }

        if (normalized.endsWith("BUSD") || normalized.endsWith("USDC")) {
            normalized = normalized.substring(0, normalized.length() - 4) + "USDT";
        }

        if (normalized.endsWith("BTC") && normalized.length() > 3) {
            normalized = normalized.substring(0, normalized.length() - 3) + "USDT";
        }

        return normalized;
    }

    private String normalizeTimeframe(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return "";
        }

        String normalized = timeframe.trim().toLowerCase(Locale.ROOT).replace(" ", "");

        if (TIMEFRAME_MAP.containsKey(normalized)) {
            return TIMEFRAME_MAP.get(normalized);
        }

        return VALID_TIMEFRAMES.contains(normalized) ? normalized : "";
    }
}
