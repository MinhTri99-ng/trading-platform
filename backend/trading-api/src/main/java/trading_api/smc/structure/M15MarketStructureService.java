package trading_api.smc.structure;

import org.springframework.stereotype.Service;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class M15MarketStructureService {
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);

    public M15MarketStructureResult analyze(List<Candle> candles) {
        return analyze(candles, M15MarketStructureConfig.defaultConfig());
    }

    public M15MarketStructureResult analyze(List<Candle> candles, M15MarketStructureConfig config) {
        if (candles == null || config == null || candles.size() < config.minCandles()) {
            return M15MarketStructureResult.empty();
        }

        List<SwingPoint> swings = detectSwings(candles, config);
        List<StructureLabel> labels = classifyMajorStructure(swings);
        StructuralBox box = buildStructuralBox(swings);
        DirectionalBias bias = determineBias(labels);
        List<StructureBreak> breaks = detectBreaks(candles, box, bias);

        return new M15MarketStructureResult(swings, labels, box, breaks, bias);
    }

    private List<SwingPoint> detectSwings(List<Candle> candles, M15MarketStructureConfig config) {
        List<SwingPoint> swings = new ArrayList<>();
        BigDecimal averageRange = averageRange(candles);

        for (int index = config.lookback(); index < candles.size() - config.lookforward(); index++) {
            Candle current = candles.get(index);
            if (isSwingHigh(candles, index, config)) {
                BigDecimal prominence = highProminence(candles, index, config);
                swings.add(toSwingPoint(current, index, SwingPoint.SwingDirection.HIGH,
                        prominence, averageRange, config));
            }
            if (isSwingLow(candles, index, config)) {
                BigDecimal prominence = lowProminence(candles, index, config);
                swings.add(toSwingPoint(current, index, SwingPoint.SwingDirection.LOW,
                        prominence, averageRange, config));
            }
        }
        swings.sort(Comparator.comparingInt(SwingPoint::index));
        return swings;
    }

    private boolean isSwingHigh(List<Candle> candles, int index, M15MarketStructureConfig config) {
        BigDecimal value = candles.get(index).getHigh();
        for (int offset = 1; offset <= config.lookback(); offset++) {
            if (value.compareTo(candles.get(index - offset).getHigh()) <= 0) return false;
        }
        for (int offset = 1; offset <= config.lookforward(); offset++) {
            if (value.compareTo(candles.get(index + offset).getHigh()) <= 0) return false;
        }
        return true;
    }

    private boolean isSwingLow(List<Candle> candles, int index, M15MarketStructureConfig config) {
        BigDecimal value = candles.get(index).getLow();
        for (int offset = 1; offset <= config.lookback(); offset++) {
            if (value.compareTo(candles.get(index - offset).getLow()) >= 0) return false;
        }
        for (int offset = 1; offset <= config.lookforward(); offset++) {
            if (value.compareTo(candles.get(index + offset).getLow()) >= 0) return false;
        }
        return true;
    }

    private BigDecimal highProminence(List<Candle> candles, int index, M15MarketStructureConfig config) {
        BigDecimal surroundingHigh = candles.get(index - config.lookback()).getHigh()
                .max(candles.get(index + config.lookforward()).getHigh());
        return candles.get(index).getHigh().subtract(surroundingHigh, MATH_CONTEXT);
    }

    private BigDecimal lowProminence(List<Candle> candles, int index, M15MarketStructureConfig config) {
        BigDecimal surroundingLow = candles.get(index - config.lookback()).getLow()
                .min(candles.get(index + config.lookforward()).getLow());
        return surroundingLow.subtract(candles.get(index).getLow(), MATH_CONTEXT);
    }

    private SwingPoint toSwingPoint(Candle candle, int index, SwingPoint.SwingDirection direction,
                                    BigDecimal prominence, BigDecimal averageRange,
                                    M15MarketStructureConfig config) {
        BigDecimal strength = averageRange.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : prominence.divide(averageRange, MATH_CONTEXT).multiply(BigDecimal.valueOf(100));
        boolean major = prominencePercent(candle, prominence).compareTo(BigDecimal.valueOf(config.minMajorProminencePercent())) >= 0
                && strength.compareTo(BigDecimal.valueOf(config.minMajorStrength())) >= 0;
        return new SwingPoint(index, direction == SwingPoint.SwingDirection.HIGH ? candle.getHigh() : candle.getLow(),
                direction, strength, major, candle.getTimestamp(), candle.getTimeframe());
    }

    private BigDecimal prominencePercent(Candle candle, BigDecimal prominence) {
        BigDecimal base = candle.getClose() == null || candle.getClose().compareTo(BigDecimal.ZERO) == 0
                ? candle.getHigh()
                : candle.getClose().abs();
        return prominence.abs().divide(base, MATH_CONTEXT).multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal averageRange(List<Candle> candles) {
        BigDecimal total = BigDecimal.ZERO;
        for (Candle candle : candles) total = total.add(candle.getHigh().subtract(candle.getLow()), MATH_CONTEXT);
        return total.divide(BigDecimal.valueOf(candles.size()), MATH_CONTEXT);
    }

    private List<StructureLabel> classifyMajorStructure(List<SwingPoint> swings) {
        List<StructureLabel> labels = new ArrayList<>();
        List<SwingPoint> highs = swings.stream().filter(s -> s.external() && s.type() == SwingPoint.SwingDirection.HIGH).toList();
        List<SwingPoint> lows = swings.stream().filter(s -> s.external() && s.type() == SwingPoint.SwingDirection.LOW).toList();
        for (int i = 1; i < highs.size(); i++) {
            SwingPoint previous = highs.get(i - 1), current = highs.get(i);
            labels.add(new StructureLabel(current.index(), current.price(),
                    current.price().compareTo(previous.price()) > 0 ? StructureType.HH : StructureType.LH,
                    current.timestamp(), current.timeframe()));
        }
        for (int i = 1; i < lows.size(); i++) {
            SwingPoint previous = lows.get(i - 1), current = lows.get(i);
            labels.add(new StructureLabel(current.index(), current.price(),
                    current.price().compareTo(previous.price()) > 0 ? StructureType.HL : StructureType.LL,
                    current.timestamp(), current.timeframe()));
        }
        labels.sort(Comparator.comparingInt(StructureLabel::index));
        return labels;
    }

    private StructuralBox buildStructuralBox(List<SwingPoint> swings) {
        SwingPoint high = swings.stream().filter(s -> s.external() && s.type() == SwingPoint.SwingDirection.HIGH)
                .max(Comparator.comparingInt(SwingPoint::index)).orElse(null);
        SwingPoint low = swings.stream().filter(s -> s.external() && s.type() == SwingPoint.SwingDirection.LOW)
                .max(Comparator.comparingInt(SwingPoint::index)).orElse(null);
        return new StructuralBox(high, low);
    }

    private DirectionalBias determineBias(List<StructureLabel> labels) {
        boolean bullishHigh = labels.stream().anyMatch(label -> label.type() == StructureType.HH);
        boolean bullishLow = labels.stream().anyMatch(label -> label.type() == StructureType.HL);
        boolean bearishHigh = labels.stream().anyMatch(label -> label.type() == StructureType.LH);
        boolean bearishLow = labels.stream().anyMatch(label -> label.type() == StructureType.LL);
        if (bullishHigh && bullishLow && !bearishHigh && !bearishLow) return DirectionalBias.BULLISH;
        if (bearishHigh && bearishLow && !bullishHigh && !bullishLow) return DirectionalBias.BEARISH;
        return DirectionalBias.RANGE;
    }

    private List<StructureBreak> detectBreaks(List<Candle> candles, StructuralBox box, DirectionalBias bias) {
        if (!box.present()) return List.of();
        List<StructureBreak> breaks = new ArrayList<>();
        detectHighBreak(candles, box.majorHigh(), bias, breaks);
        detectLowBreak(candles, box.majorLow(), bias, breaks);
        breaks.sort(Comparator.comparing(StructureBreak::breakTimestamp, Comparator.nullsLast(Comparator.naturalOrder())));
        return breaks;
    }

    private void detectHighBreak(List<Candle> candles, SwingPoint level, DirectionalBias bias, List<StructureBreak> breaks) {
        for (int i = level.index() + 1; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            if (candle.getClose().compareTo(level.price()) > 0) {
                StructureBreak.BreakType type = bias == DirectionalBias.BEARISH ? StructureBreak.BreakType.CHOCH : StructureBreak.BreakType.BOS;
                breaks.add(new StructureBreak(type, level, candle.getClose(), candle.getTimestamp(), true, SwingPoint.SwingDirection.HIGH));
                return;
            }
        }
    }

    private void detectLowBreak(List<Candle> candles, SwingPoint level, DirectionalBias bias, List<StructureBreak> breaks) {
        for (int i = level.index() + 1; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            if (candle.getClose().compareTo(level.price()) < 0) {
                StructureBreak.BreakType type = bias == DirectionalBias.BULLISH ? StructureBreak.BreakType.CHOCH : StructureBreak.BreakType.BOS;
                breaks.add(new StructureBreak(type, level, candle.getClose(), candle.getTimestamp(), true, SwingPoint.SwingDirection.LOW));
                return;
            }
        }
    }
}