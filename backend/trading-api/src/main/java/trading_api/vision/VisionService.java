package trading_api.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
            "(?i)\\b(?:BTC|ETH|SOL|XRP|BNB|ADA|DOGE|LINK|AVAX|TRX|XAU|XAG|EUR|GBP|USD|JPY|AUD|NZD|CHF|CAD|AAPL|MSFT|TSLA|NVDA|AMZN|META|GOOGL|OIL)(?:[/\\-_ ]?(?:USD|USDT|BUSD|USDC|BTC))?\\b"
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

    @Autowired
    public VisionService(
            GeminiVisionProvider geminiVisionProvider,
            @Value("${vision.provider:gemini}") String provider
    ) {
        this("gemini".equalsIgnoreCase(provider) ? geminiVisionProvider : new LocalChartVisionProvider());
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
        ChartVisionResult visionResult;
        try {
            visionResult = chartVisionProvider.analyze(imageBytes, fileName);
        } catch (RuntimeException ex) {
            log.warn("[VISION FALLBACK] Provider failed; using filename/OCR fallback: {}", ex.getMessage());
            visionResult = ChartVisionResult.unavailable("Vision provider failed: " + ex.getMessage());
        }
        if (visionResult == null) {
            visionResult = ChartVisionResult.unavailable("Vision provider returned no result.");
        }
        String detectedText = visionResult.detectedText() == null
                ? ""
                : String.join(" ", visionResult.detectedText());
        normalizedText = (normalizedText + " " + detectedText).trim();
        String rawSymbol = normalizeSymbol(visionResult.symbol());
        String rawTimeframe = normalizeTimeframe(visionResult.timeframe());
        String filenameSymbol = extractSymbol(normalizedText.replace('_', ' '));
        String filenameTimeframe = extractTimeframe(normalizedText);
        String pattern = visionResult.chartType() == null || visionResult.chartType().isBlank()
                ? "Phát hiện vùng tích lũy / SMC Setup"
                : visionResult.chartType();
        List<String> indicators = visionResult.visibleIndicators() == null ? List.of() : visionResult.visibleIndicators();
        String priceLevels = visionResult.visiblePriceLevels() == null
                ? ""
                : String.join(", ", visionResult.visiblePriceLevels());
        if (priceLevels.isBlank()) {
            priceLevels = "Hỗ trợ/Kháng cự trích xuất từ ảnh";
        }

        log.info("[TIMEFRAME INPUT] requested={}", requestedTimeframe);
        log.info("[VISION] status={} confidence={} warnings={}",
                visionResult.status(), visionResult.confidence(), visionResult.detectionWarnings());
        log.info("[VISION RAW] rawSymbol={} rawTimeframe={} textSource={}", rawSymbol, rawTimeframe, textSource);

        String symbol = normalizeSymbol(rawSymbol);
        if (symbol.isBlank()) {
            symbol = normalizeSymbol(requestedSymbol);
        }
        if (symbol == null || symbol.isBlank()) {
            symbol = normalizeSymbol(filenameSymbol);
        }
        if (symbol == null || symbol.isBlank()) {
            symbol = "CHART_ANALYSIS";
        }

        String filenameFallbackTimeframe = filenameTimeframe == null ? inferTimeframeFromFileName(fileName) : filenameTimeframe;
        TimeframeResolver.Resolution resolution;
        try {
            resolution = new TimeframeResolver().resolveTimeframe(
                    requestedTimeframe,
                    rawTimeframe,
                    filenameFallbackTimeframe
            );
        } catch (IllegalArgumentException ex) {
            log.info("[TIMEFRAME FALLBACK] No timeframe detected; using 1d for chart analysis");
            resolution = new TimeframeResolver.Resolution("1d", TimeframeResolver.Source.FILENAME);
        }
        log.info("[TIMEFRAME RESOLUTION] requested={} vision={} filename={} resolved={} source={}",
                requestedTimeframe, rawTimeframe, filenameFallbackTimeframe, resolution.timeframe(), resolution.source());

        String normalizedSymbol = normalizeSymbol(symbol);
        String normalizedTimeframe = resolution.timeframe();

        if (normalizedSymbol == null || normalizedSymbol.isBlank()) {
            normalizedSymbol = "CHART_ANALYSIS";
        }
        if (normalizedTimeframe == null || normalizedTimeframe.isBlank()) {
            normalizedTimeframe = "1d";
        }

        String market = classifyMarket(normalizedSymbol);

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
                visionResult.estimatedPriceHigh(),
                visionResult.estimatedPriceLow(),
                visionResult.confidence(),
                visionResult.status(),
                visionResult.detectedText() == null ? List.of() : visionResult.detectedText(),
                visionResult.detectionWarnings() == null ? List.of() : visionResult.detectionWarnings(),
                market
        );
    }

    private String normalizeMarket(String detectedMarket, String symbol) {
        if (detectedMarket != null && !detectedMarket.isBlank()) {
            String normalized = detectedMarket.trim().toUpperCase(Locale.ROOT);
            if (normalized.startsWith("CRYPTO")) return "Crypto";
            if (normalized.startsWith("FOREX") || normalized.startsWith("FX")) return "Forex";
            if (normalized.startsWith("COMMOD")) return "Commodities";
            if (normalized.startsWith("STOCK") || normalized.startsWith("EQUIT")) return "Stocks";
        }
        if ("CHART_ANALYSIS".equals(symbol)) return "Custom";
        if (symbol.startsWith("XAU") || symbol.startsWith("XAG") || symbol.equals("GC")) return "Commodities";
        if (symbol.matches("^(EUR|GBP|JPY|AUD|NZD|CHF|CAD).*(USD|USDT)$")) return "Forex";
        if (symbol.matches("^[A-Z]{1,5}$")) return "Stocks";
        return "Crypto";
    }

    private String classifyMarket(String symbol) {
        String normalized = symbol == null ? "" : symbol.toUpperCase(Locale.ROOT);
        if ("CHART_ANALYSIS".equals(normalized)) {
            return "Custom";
        }
        if (normalized.contains("USDT") || normalized.endsWith("BTC")
                || normalized.equals("BTC") || normalized.equals("ETH")
                || normalized.equals("SOL") || normalized.equals("XRP")
                || normalized.equals("BNB") || normalized.equals("ADA")
                || normalized.equals("DOGE")) {
            return "Crypto";
        }
        if (normalized.matches("^(EUR|GBP|USD|JPY|AUD|NZD|CHF|CAD)(USD|USDT)?$")
                || normalized.matches("^(EUR|GBP|USD|JPY|AUD|NZD|CHF|CAD).*(USD)$")) {
            return "Forex";
        }
        if (normalized.equals("XAUUSD") || normalized.equals("XAGUSD")
                || normalized.equals("OIL") || normalized.equals("WTI")
                || normalized.equals("BRENT")) {
            return "Commodities";
        }
        if (normalized.matches("^(AAPL|MSFT|TSLA|NVDA|AMZN|META|GOOGL)$")
                || normalized.matches("^[A-Z]{1,5}$")) {
            return "Stocks";
        }
        return normalizeMarket(null, normalized);
    }

    private BufferedImage decodeImage(byte[] imageBytes) {
        if (isWebp(imageBytes)) {
            // Java's standard ImageIO does not decode WEBP, but the upload contract accepts it.
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        }
        if (!isPng(imageBytes) && !isJpeg(imageBytes)) {
            return null;
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(imageBytes)) {
            return ImageIO.read(input);
        } catch (IOException ex) {
            log.warn("[Metadata extraction] Image decode failed: {}", ex.getMessage());
            return null;
        }
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
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
        if ("CHART_ANALYSIS".equalsIgnoreCase(symbol.trim())) {
            return "CHART_ANALYSIS";
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

        boolean knownNonCrypto = normalized.startsWith("XAU") || normalized.startsWith("XAG")
                || normalized.matches("^(EUR|GBP|USD|JPY|AUD|NZD|CHF|CAD).*(USD)$")
                || normalized.matches("^[A-Z]{1,5}$");
        if (normalized.endsWith("USD") && !normalized.endsWith("USDT") && !knownNonCrypto) {
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
