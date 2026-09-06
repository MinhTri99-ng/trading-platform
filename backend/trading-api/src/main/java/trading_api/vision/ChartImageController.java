package trading_api.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import trading_api.market.TradingEngineService;
import trading_api.websocket.TradingSignalPayload;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/vision")
public class ChartImageController {
    private static final Logger log = LoggerFactory.getLogger(ChartImageController.class);

    private final VisionService visionService;
    private final ChartImageVerificationService verificationService;
    private final TradingEngineService tradingEngineService;

    public ChartImageController(VisionService visionService, ChartImageVerificationService verificationService, TradingEngineService tradingEngineService) {
        this.visionService = visionService;
        this.verificationService = verificationService;
        this.tradingEngineService = tradingEngineService;
    }

    public ResponseEntity<?> analyzeChart(MultipartFile file, String requestedTimeframe) {
        return analyzeChart(file, null, requestedTimeframe);
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeChart(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "symbol", required = false) String requestedSymbol,
                                        @RequestParam(value = "timeframe", required = false) String requestedTimeframe) {
        try {
            if (file == null || file.isEmpty()) {
                log.warn("[Upload debug] file=null or empty");
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "INVALID INPUT",
                        "reason", "No image uploaded"
                ));
            }

            String resolvedRequestedTimeframe = requestedTimeframe == null || requestedTimeframe.isBlank() ? null : requestedTimeframe.trim();
                String resolvedRequestedSymbol = requestedSymbol == null || requestedSymbol.isBlank() ? null : requestedSymbol.trim();

                log.info("[IMAGE INPUT] filename={}, contentType={}, size={}, endpoint=/api/vision/analyze, requestedSymbol={}, requestedTimeframe={}",
                    file.getOriginalFilename(), file.getContentType(), file.getSize(), resolvedRequestedSymbol, resolvedRequestedTimeframe);

                ChartImageMetadata metadata = visionService.extractMetadata(
                    file.getBytes(), file.getOriginalFilename(), resolvedRequestedSymbol, resolvedRequestedTimeframe);
            log.info("[METADATA] symbol={} timeframe={} pattern={} indicators={} priceLevels={}",
                    metadata.symbol(), metadata.timeframe(), metadata.pattern(), metadata.indicatorsVisible(), metadata.keyPriceLevels());

            log.info("[VERIFICATION REQUEST] symbol={} timeframe={}", metadata.symbol(), metadata.timeframe());
            ChartImageVerificationResult result = verificationService.verify(metadata);
            if (!result.valid()) {
                log.error("[IMAGE VERIFICATION FAILED] status={} reason={} symbol={} timeframe={} marketSnapshot={}",
                        result.status(), result.reason(), result.verifiedSymbol(), result.verifiedTimeframe(), result.marketSnapshot());
            }

            TradingSignalPayload generatedSignal = null;
            if (result.valid()) {
                generatedSignal = tradingEngineService.processSignal(metadata, result);
            }

            Map<String, Object> response = new LinkedHashMap<>();
                String responseStatus = result.valid() && generatedSignal == null
                    ? "NO_PRICE_DATA"
                    : result.status();
                response.put("status", responseStatus);
            response.put("valid", result.valid());
                response.put("reason", generatedSignal == null && result.valid()
                    ? "No reliable price data available from image, live providers, or configured fallback"
                    : result.reason());
            response.put("symbol", result.verifiedSymbol());
            response.put("timeframe", result.verifiedTimeframe());
            response.put("pattern", metadata.pattern());
            response.put("indicatorsVisible", metadata.indicatorsVisible());
            response.put("marketSnapshot", result.marketSnapshot());
                response.put("verification", result.valid() && !"MOCK_VERIFIED".equals(result.status())
                    ? "Verified with Live Market Data"
                    : result.status());
            response.put("sourceOfTruth", "Binance / Market API");
                response.put("vision", Map.of(
                    "status", metadata.visionStatus(),
                    "confidence", metadata.confidence(),
                    "detectedText", metadata.detectedText(),
                    "warnings", metadata.detectionWarnings()
                ));
            response.put("direction", generatedSignal != null ? generatedSignal.direction() : null);
            response.put("entry", generatedSignal != null ? generatedSignal.entry() : null);
            response.put("stopLoss", generatedSignal != null ? generatedSignal.stopLoss() : null);
            response.put("takeProfit", generatedSignal != null ? generatedSignal.takeProfit() : null);
            response.put("riskReward", generatedSignal != null ? generatedSignal.riskReward() : null);
            response.put("confidence", generatedSignal != null ? generatedSignal.confidence() : null);

            log.info("[RESPONSE] symbol={} timeframe={}", result.verifiedSymbol(), result.verifiedTimeframe());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
                String status = ex.getMessage() != null && ex.getMessage().contains("TIMEFRAME_UNDETECTED")
                    ? "TIMEFRAME_UNDETECTED"
                    : ex.getMessage() != null && ex.getMessage().contains("MISSING_SYMBOL")
                    ? "VISION_UNAVAILABLE"
                    : "INVALID INPUT";
            log.error("[Upload debug] invalid input: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", status,
                    "reason", ex.getMessage(),
                    "sourceOfTruth", "Binance / Market API"
            ));
        } catch (IOException ex) {
            log.error("[Upload debug] image processing failed", ex);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "INVALID INPUT",
                    "reason", "Image processing failed",
                    "sourceOfTruth", "Binance / Market API"
            ));
        }
    }
}
