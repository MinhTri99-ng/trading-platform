package trading_api.worker;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import trading_api.config.WatchlistConfig;
import trading_api.entity.Candle;
import trading_api.market.KlineClosedEvent;
import trading_api.market.TradingStrategyService;
import trading_api.repository.CandleRepository;
import trading_api.websocket.BinanceKlineProxyService;
import trading_api.websocket.TradingSignalPayload;

import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WatchlistSignalScannerWorker {
    private static final int CANDLE_HISTORY_SIZE = 200;

    private final CandleRepository candleRepository;
    private final TradingStrategyService strategyService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WatchlistConfig watchlistConfig;
    private final Map<String, Instant> processedCandles = new ConcurrentHashMap<>();

    public WatchlistSignalScannerWorker(
            CandleRepository candleRepository,
            TradingStrategyService strategyService,
            SimpMessagingTemplate messagingTemplate,
            WatchlistConfig watchlistConfig,
            BinanceKlineProxyService binanceKlineProxyService
    ) {
        this.candleRepository = candleRepository;
        this.strategyService = strategyService;
        this.messagingTemplate = messagingTemplate;
        this.watchlistConfig = watchlistConfig;
        watchlistConfig.symbols().stream()
                .filter(symbol -> symbol.endsWith("USDT"))
                .forEach(symbol -> watchlistConfig.timeframes().forEach(timeframe ->
                        binanceKlineProxyService.subscribe(symbol, timeframe)));
    }

    @Async("signalScannerExecutor")
    @EventListener
    public void onKlineClosed(KlineClosedEvent event) {
        Candle closedCandle = event.candle();
        if (!watchlistConfig.contains(closedCandle.getSymbol())
                || !watchlistConfig.timeframes().contains(closedCandle.getTimeframe())) {
            return;
        }
        String eventKey = closedCandle.getSymbol() + ":" + closedCandle.getTimeframe()
                + ":" + closedCandle.getTimestamp();
        if (processedCandles.putIfAbsent(eventKey, Instant.now()) != null) {
            return;
        }
        Instant expiry = Instant.now().minusSeconds(2 * 24 * 60 * 60);
        processedCandles.entrySet().removeIf(entry -> entry.getValue().isBefore(expiry));

        List<Candle> candles = new ArrayList<>(candleRepository.findLatestCandles(
                closedCandle.getSymbol(),
                closedCandle.getTimeframe(),
                PageRequest.of(0, CANDLE_HISTORY_SIZE)
        ));
        if (candles.stream().noneMatch(candle ->
                closedCandle.getTimestamp().equals(candle.getTimestamp()))) {
            candles.add(closedCandle);
        }
        candles.sort((left, right) -> left.getTimestamp().compareTo(right.getTimestamp()));

        if (candles.size() < 21) {
            return;
        }

        TradingStrategyService.TradingSignal signal = strategyService.analyze(
                candles,
                BigDecimal.valueOf(10_000),
                TradingStrategyService.DEFAULT_RISK_PERCENT
        );
        if (signal.status() != TradingStrategyService.SignalStatus.VALID
                || signal.direction() == TradingStrategyService.SignalDirection.NONE) {
            return;
        }

        messagingTemplate.convertAndSend(
                "/topic/signals",
                new TradingSignalPayload(
                        closedCandle.getSymbol(),
                        signal.direction().name(),
                        signal.entry(),
                        signal.stopLoss(),
                        signal.takeProfit(),
                        "1:" + signal.riskReward(),
                        75,
                        Instant.now().toEpochMilli()
                )
        );
    }
}
