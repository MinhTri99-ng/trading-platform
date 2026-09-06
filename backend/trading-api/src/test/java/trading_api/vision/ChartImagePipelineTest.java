package trading_api.vision;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import trading_api.market.MarketDataService;
import trading_api.market.TradingEngineService;
import trading_api.vision.ChartImageController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChartImagePipelineTest {

    @Test
    void resolverHonorsRequestedTimeframePriority() {
        TimeframeResolver resolver = new TimeframeResolver();

        assertThat(resolver.resolveTimeframe("1m", "1d", "15m").timeframe()).isEqualTo("1m");
        assertThat(resolver.resolveTimeframe("15m", null, null).timeframe()).isEqualTo("15m");
        assertThat(resolver.resolveTimeframe("4h", null, null).timeframe()).isEqualTo("4h");
        assertThat(resolver.resolveTimeframe(null, "1m", "1d").timeframe()).isEqualTo("1m");
        assertThat(resolver.resolveTimeframe(null, null, "15m").timeframe()).isEqualTo("15m");
        assertThatThrownBy(() -> resolver.resolveTimeframe(null, null, "60b88e3f8c7b0c25556a.jpg"))
                .hasMessageContaining("TIMEFRAME_UNDETECTED");
    }

    @Test
    void visionTimeframeIsUsedWhenRequestAndFilenameAreMissing() throws IOException {
        VisionService visionService = new VisionService(imageBytes -> java.util.Optional.of("1m"));

        ChartImageMetadata metadata = visionService.extractMetadata(
            createPngBytes(), "60b88e3f8c7b0c25556a.jpg", "BTCUSDT", null);

        assertThat(metadata.timeframe()).isEqualTo("1m");
    }

    @Test
    void requestedTimeframeVariantsArePreserved() throws IOException {
        VisionService visionService = new VisionService();

        assertThat(visionService.extractMetadata(createPngBytes(), "random.jpg", "BTCUSDT", "15m").timeframe()).isEqualTo("15m");
        assertThat(visionService.extractMetadata(createPngBytes(), "random.jpg", "BTCUSDT", "4h").timeframe()).isEqualTo("4h");
    }

    @Test
    void validPngChartImageIsAccepted() throws IOException {
        VisionService visionService = new VisionService();

        byte[] pngBytes = createPngBytes();
        ChartImageMetadata metadata = visionService.extractMetadata(pngBytes, "BTCUSD_1D.png");

        assertThat(metadata.symbol()).isEqualTo("BTCUSDT");
        assertThat(metadata.timeframe()).isEqualTo("1d");
        assertThat(metadata.pattern()).isNotBlank();
    }

    @Test
    void btcUsdAndDailyNormalizeForMarketVerification() throws IOException {
        VisionService visionService = new VisionService();

        ChartImageMetadata metadata = visionService.extractMetadata(createPngBytes(), "BTC/USD_1D.png");

        assertThat(metadata.symbol()).isEqualTo("BTCUSDT");
        assertThat(metadata.timeframe()).isEqualTo("1d");
    }

    @Test
    void testScreenshot1mPreservesTimeframe() throws IOException {
        VisionService visionService = new VisionService();
        ChartImageMetadata metadata = visionService.extractMetadata(createPngBytes(), "BTCUSDT_1m.png");

        assertThat(metadata.symbol()).isEqualTo("BTCUSDT");
        assertThat(metadata.timeframe()).isEqualTo("1m");
    }

    @Test
    void testScreenshot5mPreservesTimeframe() throws IOException {
        VisionService visionService = new VisionService();
        ChartImageMetadata metadata = visionService.extractMetadata(createPngBytes(), "BTCUSDT_5m.png");

        assertThat(metadata.symbol()).isEqualTo("BTCUSDT");
        assertThat(metadata.timeframe()).isEqualTo("5m");
    }

    @Test
    void testScreenshot15mPreservesTimeframe() throws IOException {
        VisionService visionService = new VisionService();
        ChartImageMetadata metadata = visionService.extractMetadata(createPngBytes(), "SOLUSDT_15m.png");

        assertThat(metadata.symbol()).isEqualTo("SOLUSDT");
        assertThat(metadata.timeframe()).isEqualTo("15m");
    }

    @Test
    void testScreenshot30mPreservesTimeframe() throws IOException {
        VisionService visionService = new VisionService();
        ChartImageMetadata metadata = visionService.extractMetadata(createPngBytes(), "ETHUSDT_30m.png");

        assertThat(metadata.symbol()).isEqualTo("ETHUSDT");
        assertThat(metadata.timeframe()).isEqualTo("30m");
    }

    @Test
    void testScreenshot1hPreservesTimeframe() throws IOException {
        VisionService visionService = new VisionService();
        ChartImageMetadata metadata = visionService.extractMetadata(createPngBytes(), "BTCUSDT_1h.png");

        assertThat(metadata.symbol()).isEqualTo("BTCUSDT");
        assertThat(metadata.timeframe()).isEqualTo("1h");
    }

    @Test
    void testScreenshot1dPreservesTimeframe() throws IOException {
        VisionService visionService = new VisionService();
        ChartImageMetadata metadata = visionService.extractMetadata(createPngBytes(), "BTCUSDT_1D.png");

        assertThat(metadata.symbol()).isEqualTo("BTCUSDT");
        assertThat(metadata.timeframe()).isEqualTo("1d");
    }

    @Test
    void testScreenshot4hPreservesTimeframe() throws IOException {
        VisionService visionService = new VisionService();
        ChartImageMetadata metadata = visionService.extractMetadata(createPngBytes(), "ETHUSDT_4H.png");

        assertThat(metadata.symbol()).isEqualTo("ETHUSDT");
        assertThat(metadata.timeframe()).isEqualTo("4h");
    }

    @Test
    void marketDataUnavailableUsesMockVerifiedFallback() {
        MarketDataService marketDataService = Mockito.mock(MarketDataService.class);
        Mockito.when(marketDataService.getLatestPriceRange("BTCUSDT", "1d")).thenReturn(List.of());

        ChartImageVerificationService verificationService = new ChartImageVerificationService(marketDataService);
        ChartImageMetadata metadata = new ChartImageMetadata("BTCUSDT", "1d", "Trend", List.of("volume"), "Support zone detected", 68000.0, 66000.0, 0.82);

        ChartImageVerificationResult result = verificationService.verify(metadata);

        assertThat(result.status()).isEqualTo("MOCK_VERIFIED");
        assertThat(result.valid()).isTrue();
        assertThat(result.verifiedSymbol()).isEqualTo("BTCUSDT");
        assertThat(result.marketSnapshot()).contains("source=IMAGE_ANALYSIS_FALLBACK");
    }

    @Test
    void marketDataIsUsedForVerificationWhenRangeMatches() {
        MarketDataService marketDataService = Mockito.mock(MarketDataService.class);
        Mockito.when(marketDataService.getLatestPriceRange("BTCUSDT", "1d")).thenReturn(List.of(BigDecimal.valueOf(66000), BigDecimal.valueOf(68000)));

        ChartImageVerificationService verificationService = new ChartImageVerificationService(marketDataService);
        ChartImageMetadata metadata = new ChartImageMetadata("BTCUSD", "1d", "Trend", List.of("volume"), "Support zone detected", 67000.0, 66500.0, 0.82);

        ChartImageVerificationResult result = verificationService.verify(metadata);

        assertThat(result.valid()).isTrue();
        assertThat(result.verifiedSymbol()).isEqualTo("BTCUSDT");
        assertThat(result.verifiedTimeframe()).isEqualTo("1d");
    }

    @Test
    void testMissingTimeframeDoesNotFallbackTo1d() {
        VisionService visionService = new VisionService();

        assertThatThrownBy(() -> visionService.extractMetadata(createPngBytes(), "BTCUSD_unknown.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TIMEFRAME_UNDETECTED");

        MarketDataService marketDataService = Mockito.mock(MarketDataService.class);
        ChartImageVerificationService verificationService = new ChartImageVerificationService(marketDataService);
        ChartImageVerificationResult result = verificationService.verify(new ChartImageMetadata("BTCUSDT", "unknown", "Trend", List.of("volume"), "Support zone detected", 67000.0, 66500.0, 0.82));

        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo("TIMEFRAME_UNDETECTED");
    }

    @Test
    void testRequestedTimeframeHasPriorityOverRandomFilename() throws IOException {
        VisionService visionService = new VisionService();

        ChartImageMetadata metadata = visionService.extractMetadata(
            createPngBytes(), "60b88e3f8c7b0c25556a.jpg", "BTCUSDT", "1m");

        assertThat(metadata.symbol()).isEqualTo("BTCUSDT");
        assertThat(metadata.timeframe()).isEqualTo("1m");
    }

    @Test
    void testControllerUsesRequestedTimeframeWhenFilenameIsRandom() throws IOException {
        MarketDataService marketDataService = Mockito.mock(MarketDataService.class);
        Mockito.when(marketDataService.getLatestPriceRange("BTCUSDT", "1m")).thenReturn(List.of(BigDecimal.valueOf(78123.09), BigDecimal.valueOf(79220.61)));

        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        TradingEngineService tradingEngineService = new TradingEngineService(marketDataService, messagingTemplate);
        ChartImageController controller = new ChartImageController(new VisionService(), new ChartImageVerificationService(marketDataService), tradingEngineService);

        MockMultipartFile file = new MockMultipartFile("file", "60b88e3f8c7b0c25556a.jpg", "image/jpeg", createPngBytes());
        ResponseEntity<?> response = controller.analyzeChart(file, "BTCUSDT", "1m");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("timeframe")).isEqualTo("1m");
        Mockito.verify(marketDataService, Mockito.atLeastOnce()).getLatestPriceRange("BTCUSDT", "1m");
    }

        @Test
        void realWorldJpegRegressionKeepsOneMinuteTimeframe() throws IOException {
        MarketDataService marketDataService = Mockito.mock(MarketDataService.class);
        Mockito.when(marketDataService.getLatestPriceRange("BTCUSDT", "1m"))
            .thenReturn(List.of(BigDecimal.valueOf(78123.09), BigDecimal.valueOf(79220.61)));

        TradingEngineService tradingEngineService = new TradingEngineService(
            marketDataService,
            Mockito.mock(SimpMessagingTemplate.class)
        );
        ChartImageController controller = new ChartImageController(
            new VisionService(),
            new ChartImageVerificationService(marketDataService),
            tradingEngineService
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "60b88e3f8c7b0c25556a.jpg",
            "image/jpeg",
            createJpegBytes(1920, 1020)
        );

        ResponseEntity<?> response = controller.analyzeChart(file, "BTCUSDT", "1m");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("VERIFIED");
        assertThat(body.get("symbol")).isEqualTo("BTCUSDT");
        assertThat(body.get("timeframe")).isEqualTo("1m");
        Mockito.verify(marketDataService, Mockito.atLeastOnce()).getLatestPriceRange("BTCUSDT", "1m");
        }

        @Test
        void randomFilenameWithoutRequestedTimeframeReturnsUndetected() throws IOException {
        MarketDataService marketDataService = Mockito.mock(MarketDataService.class);
        ChartImageController controller = new ChartImageController(
            new VisionService(),
            new ChartImageVerificationService(marketDataService),
            Mockito.mock(TradingEngineService.class)
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "60b88e3f8c7b0c25556a.jpg",
            "image/jpeg",
            createJpegBytes(1920, 1020)
        );

        ResponseEntity<?> response = controller.analyzeChart(file, null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("TIMEFRAME_UNDETECTED");
        assertThat(body).doesNotContainKey("timeframe");
        }

    @Test
    void testMarketVerificationReceivesDetectedTimeframe() {
        MarketDataService marketDataService = Mockito.mock(MarketDataService.class);
        Mockito.when(marketDataService.getLatestPriceRange("SOLUSDT", "15m")).thenReturn(List.of(BigDecimal.valueOf(102.74), BigDecimal.valueOf(104.38)));

        ChartImageVerificationService verificationService = new ChartImageVerificationService(marketDataService);
        ChartImageVerificationResult result = verificationService.verify(new ChartImageMetadata("SOLUSDT", "15m", "Trend", List.of("volume"), "Support zone detected", 103.5, 102.8, 0.82));

        assertThat(result.valid()).isTrue();
        assertThat(result.verifiedTimeframe()).isEqualTo("15m");
        Mockito.verify(marketDataService).getLatestPriceRange("SOLUSDT", "15m");
    }

    @Test
    void testFinalResponseUsesDetectedTimeframe() throws IOException {
        MarketDataService marketDataService = Mockito.mock(MarketDataService.class);
        Mockito.when(marketDataService.getLatestPriceRange("BTCUSDT", "1m")).thenReturn(List.of(BigDecimal.valueOf(78123.09), BigDecimal.valueOf(79220.61)));

        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
        TradingEngineService tradingEngineService = new TradingEngineService(marketDataService, messagingTemplate);
        ChartImageController controller = new ChartImageController(new VisionService(), new ChartImageVerificationService(marketDataService), tradingEngineService);

        MockMultipartFile file = new MockMultipartFile("file", "BTCUSDT_1m.png", "image/png", createPngBytes());
        ResponseEntity<?> response = controller.analyzeChart(file, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("symbol")).isEqualTo("BTCUSDT");
        assertThat(body.get("timeframe")).isEqualTo("1m");
        Mockito.verify(marketDataService, Mockito.atLeastOnce()).getLatestPriceRange("BTCUSDT", "1m");
    }

    private byte[] createPngBytes() throws IOException {
        return createImageBytes(300, 200, "png");
    }

    private byte[] createJpegBytes(int width, int height) throws IOException {
        return createImageBytes(width, height, "jpg");
    }

    private byte[] createImageBytes(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
