package trading_api.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TradingAnalysisService {
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, Deque<BigDecimal>> ema50Map = new HashMap<>();
    private final Map<String, Deque<BigDecimal>> ema200Map = new HashMap<>();
    private final Map<String, Deque<BigDecimal>> rsiMap = new HashMap<>();

    public TradingAnalysisService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void process(String symbol, KlineStreamPayload payload) {
        if (payload == null || !payload.isFinal()) {
            return;
        }

        String normalizedSymbol = symbol.toUpperCase(Locale.ROOT);
        Deque<BigDecimal> ema50 = ema50Map.computeIfAbsent(normalizedSymbol, key -> new ArrayDeque<>());
        Deque<BigDecimal> ema200 = ema200Map.computeIfAbsent(normalizedSymbol, key -> new ArrayDeque<>());
        Deque<BigDecimal> rsi = rsiMap.computeIfAbsent(normalizedSymbol, key -> new ArrayDeque<>());

        BigDecimal currentClose = payload.close();

        BigDecimal ema50Value = calculateEma(ema50, currentClose, 50);
        BigDecimal ema200Value = calculateEma(ema200, currentClose, 200);
        BigDecimal rsiValue = calculateRsi(rsi, payload.close(), payload.open());

        String direction = null;
        if (ema50Value != null && ema200Value != null && ema50Value.compareTo(ema200Value) > 0) {
            direction = "LONG";
        } else if (ema50Value != null && ema200Value != null && ema50Value.compareTo(ema200Value) < 0) {
            direction = "SHORT";
        }

        if (direction == null) {
            return;
        }

        BigDecimal entry = payload.close();
        BigDecimal stopLoss = direction.equals("LONG") ? payload.low().multiply(BigDecimal.valueOf(0.9995)) : payload.high().multiply(BigDecimal.valueOf(1.0005));
        BigDecimal takeProfit = direction.equals("LONG") ? payload.close().multiply(BigDecimal.valueOf(1.018)) : payload.close().multiply(BigDecimal.valueOf(0.982));

        BigDecimal riskReward = takeProfit.subtract(entry).divide(entry.subtract(stopLoss).abs(), 4, RoundingMode.HALF_UP);
        int confidence = Math.max(50, Math.min(95, 55 + (direction.equals("LONG") ? 15 : 10) + rsiValue.intValue() / 3));

        TradingSignalPayload signal = new TradingSignalPayload(
                normalizedSymbol,
                direction,
                entry,
                stopLoss,
                takeProfit,
                "1:" + riskReward.abs().setScale(1, RoundingMode.HALF_UP),
                confidence,
                payload.timestamp()
        );

        messagingTemplate.convertAndSend("/topic/signals", signal);
    }

    private BigDecimal calculateEma(Deque<BigDecimal> history, BigDecimal current, int period) {
        if (history.size() >= period) {
            history.removeFirst();
        }
        history.addLast(current);

        BigDecimal multiplier = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(period + 1), 10, RoundingMode.HALF_UP);
        BigDecimal ema = null;

        for (BigDecimal value : history) {
            ema = ema == null
                    ? value
                    : value.multiply(multiplier).add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
        }

        return ema;
    }

    private BigDecimal calculateRsi(Deque<BigDecimal> history, BigDecimal currentClose, BigDecimal currentOpen) {
        history.addLast(currentClose.subtract(currentOpen).abs());
        if (history.size() > 14) {
            history.removeFirst();
        }
        BigDecimal sum = history.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(Math.max(history.size(), 1)), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }
}
