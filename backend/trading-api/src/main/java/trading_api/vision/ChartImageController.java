package trading_api.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import trading_api.analysis.Analysis;
import trading_api.analysis.AnalysisHistoryService;
import trading_api.analysis.TradeSetup;
import trading_api.auth.User;
import trading_api.auth.UserRepository;
import trading_api.market.TradingEngineService;
import trading_api.websocket.TradingSignalPayload;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/vision")
public class ChartImageController {
    private static final Logger log = LoggerFactory.getLogger(ChartImageController.class);

    private final VisionService visionService;
    private final ChartImageVerificationService verificationService;
    private final TradingEngineService tradingEngineService;
    private final AnalysisHistoryService historyService;
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public ChartImageController(VisionService visionService, ChartImageVerificationService verificationService,
                                TradingEngineService tradingEngineService, AnalysisHistoryService historyService,
                                UserRepository userRepository) {
        this.visionService = visionService;
        this.verificationService = verificationService;
        this.tradingEngineService = tradingEngineService;
        this.historyService = historyService;
        this.userRepository = userRepository;
    }

    public ChartImageController(VisionService visionService, ChartImageVerificationService verificationService,
                                TradingEngineService tradingEngineService) {
        this(visionService, verificationService, tradingEngineService, null, null);
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
                response.put("status", result.status());
            response.put("valid", result.valid());
                response.put("reason", result.reason());
            response.put("symbol", result.verifiedSymbol());
            response.put("market", metadata.market());
            response.put("timeframe", result.verifiedTimeframe());
            response.put("pattern", metadata.pattern());
            response.put("indicatorsVisible", metadata.indicatorsVisible());
            response.put("keyPriceLevels", metadata.keyPriceLevels());
            response.put("detectedText", metadata.detectedText());
            response.put("detectionWarnings", metadata.detectionWarnings());
            response.put("smc", Map.of(
                    "pattern", metadata.pattern(),
                    "keyPriceLevels", metadata.keyPriceLevels(),
                    "indicatorsVisible", metadata.indicatorsVisible()
            ));
            response.put("marketSnapshot", result.marketSnapshot());
            response.put("verification", result.status());
            response.put("sourceOfTruth", "Vision metadata / chart analysis");
                response.put("vision", Map.of(
                    "status", metadata.visionStatus(),
                    "confidence", metadata.confidence(),
                    "market", metadata.market(),
                    "detectedText", metadata.detectedText(),
                    "warnings", metadata.detectionWarnings()
                ));
            response.put("direction", generatedSignal != null ? generatedSignal.direction() : null);
            response.put("entry", generatedSignal != null ? generatedSignal.entry() : null);
            response.put("stopLoss", generatedSignal != null ? generatedSignal.stopLoss() : null);
            response.put("takeProfit", generatedSignal != null ? generatedSignal.takeProfit() : null);
            response.put("riskReward", generatedSignal != null ? generatedSignal.riskReward() : null);
            response.put("confidence", generatedSignal != null ? generatedSignal.confidence() : null);

            saveAnalysisIfAuthenticated(result, metadata, generatedSignal, response);
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

    private void saveAnalysisIfAuthenticated(ChartImageVerificationResult result, ChartImageMetadata metadata,
                                             TradingSignalPayload signal, Map<String, Object> response) {
        if (historyService == null || userRepository == null || "ERROR".equalsIgnoreCase(result.status())) {
            return;
        }
        Optional<User> user = authenticatedUser();
        if (user.isEmpty()) {
            return;
        }
        String symbol = result.verifiedSymbol() != null ? result.verifiedSymbol() : metadata.symbol();
        String timeframe = result.verifiedTimeframe() != null ? result.verifiedTimeframe() : metadata.timeframe();
        if (symbol == null || symbol.isBlank() || timeframe == null || timeframe.isBlank()) {
            log.warn("Skipping analysis history because symbol or timeframe is unavailable");
            return;
        }
        try {
            Analysis analysis = new Analysis();
            analysis.setUser(user.get());
            analysis.setSymbol(symbol);
            analysis.setTimeframe(timeframe);
            analysis.setMarket(metadata.market());
            analysis.setPattern(metadata.pattern());
            analysis.setConfidence(metadata.confidence());
            analysis.setAnalysisText(String.join("\n", metadata.detectedText() == null ? java.util.List.of() : metadata.detectedText()));
            analysis.setResultJson(response.toString());

            TradeSetup setup = signal == null ? null : toTradeSetup(analysis, user.get(), signal);
            Analysis saved = historyService.saveAnalysis(analysis, setup);
            response.put("analysisId", saved.getId());
            log.info("Analysis saved to DB with ID: {}", saved.getId());
        } catch (RuntimeException ex) {
            log.error("Failed to save chart analysis history", ex);
        }
    }

    private TradeSetup toTradeSetup(Analysis analysis, User user, TradingSignalPayload signal) {
        TradeSetup setup = new TradeSetup();
        setup.setAnalysis(analysis);
        setup.setUser(user);
        setup.setType("SMC");
        setup.setEntryPrice(signal.entry());
        setup.setStopLoss(signal.stopLoss());
        setup.setTakeProfit(signal.takeProfit());
        setup.setRiskRewardRatio(parseRiskReward(signal.riskReward()));
        setup.setDirection("LONG".equalsIgnoreCase(signal.direction()) ? "BUY" : "SELL");
        setup.setStatus("OPEN");
        return setup;
    }

    private BigDecimal parseRiskReward(String value) {
        if (value == null || !value.contains(":")) {
            return null;
        }
        try {
            return new BigDecimal(value.substring(value.indexOf(':') + 1).trim());
        } catch (NumberFormatException ex) {
            log.warn("Unable to parse risk-reward ratio: {}", value);
            return null;
        }
    }

    private Optional<User> authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return userRepository.findById(user.getId());
        }
        String username = principal instanceof UserDetails details ? details.getUsername() : authentication.getName();
        return username == null || username.isBlank() ? Optional.empty() : userRepository.findByEmailIgnoreCase(username);
    }
}
