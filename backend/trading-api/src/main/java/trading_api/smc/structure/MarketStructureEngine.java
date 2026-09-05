package trading_api.smc.structure;

import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class MarketStructureEngine {

    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);

    public SMCMarketStructureResult analyze(List<Candle> candles) {
        return analyze(candles, MarketStructureConfig.defaultConfig());
    }

    public SMCMarketStructureResult analyze(List<Candle> candles, MarketStructureConfig config) {
        if (candles == null || candles.isEmpty()) {
            return emptyResult();
        }

        MarketStructureConfig effectiveConfig = config == null ? MarketStructureConfig.defaultConfig() : config;

        if (candles.size() < effectiveConfig.minCandles()) {
            return emptyResult();
        }

        List<SwingPoint> swings = detectSwings(candles, effectiveConfig);
        List<StructureLabel> labels = classifyStructure(swings, effectiveConfig);
        List<SwingPoint> externalStructure = detectExternalStructure(swings);
        List<SwingPoint> internalStructure = swings.stream()
                .filter(swing -> !externalStructure.contains(swing))
                .toList();

        List<ProtectedLevel> protectedHighs = detectProtectedHighs(swings, effectiveConfig);
        List<ProtectedLevel> protectedLows = detectProtectedLows(swings, effectiveConfig);

        String trend = determineTrend(labels);

        return new SMCMarketStructureResult(
                swings,
                labels,
                internalStructure,
                externalStructure,
                protectedHighs,
                protectedLows,
                trend
        );
    }

    private List<SwingPoint> detectSwings(List<Candle> candles, MarketStructureConfig config) {
        List<SwingPoint> swings = new ArrayList<>();
        if (candles == null || candles.size() < config.minCandles()) {
            return swings;
        }

        BigDecimal averageRange = calculateAverageRange(candles);
        BigDecimal noiseTolerance = config.noiseTolerance(averageRange).max(BigDecimal.valueOf(0.05));

        for (int i = 1; i < candles.size() - 1; i++) {
            Candle previous = candles.get(i - 1);
            Candle current = candles.get(i);
            Candle next = candles.get(i + 1);

            BigDecimal prevHigh = previous.getHigh();
            BigDecimal currHigh = current.getHigh();
            BigDecimal nextHigh = next.getHigh();
            BigDecimal prevLow = previous.getLow();
            BigDecimal currLow = current.getLow();
            BigDecimal nextLow = next.getLow();

            boolean isSwingHigh = currHigh.compareTo(prevHigh) >= 0
                    && currHigh.compareTo(nextHigh) >= 0
                    && currHigh.compareTo(prevHigh) != 0
                    && currHigh.compareTo(nextHigh) != 0;

            boolean isSwingLow = currLow.compareTo(prevLow) <= 0
                    && currLow.compareTo(nextLow) <= 0
                    && currLow.compareTo(prevLow) != 0
                    && currLow.compareTo(nextLow) != 0;

            if (isSwingHigh) {
                swings.add(new SwingPoint(
                        i,
                        currHigh,
                        SwingPoint.SwingDirection.HIGH,
                        calculateSwingStrength(currHigh, prevHigh, nextHigh, averageRange),
                        false,
                        current.getTimestamp(),
                        current.getTimeframe()
                ));
            }

            if (isSwingLow) {
                swings.add(new SwingPoint(
                        i,
                        currLow,
                        SwingPoint.SwingDirection.LOW,
                        calculateSwingStrength(currLow, prevLow, nextLow, averageRange),
                        false,
                        current.getTimestamp(),
                        current.getTimeframe()
                ));
            }
        }

        return mergeAndSortSwings(swings);
    }

    private BigDecimal calculateAverageRange(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalRange = BigDecimal.ZERO;
        for (Candle candle : candles) {
            totalRange = totalRange.add(candle.getHigh().subtract(candle.getLow(), MATH_CONTEXT));
        }

        return totalRange.divide(BigDecimal.valueOf(candles.size()), MATH_CONTEXT);
    }

    private BigDecimal findMaxHigh(List<Candle> candles, int start, int end) {
        if (start < 0) {
            start = 0;
        }
        if (end >= candles.size()) {
            end = candles.size() - 1;
        }
        if (start > end) {
            return BigDecimal.ZERO;
        }

        BigDecimal max = candles.get(start).getHigh();
        for (int i = start + 1; i <= end; i++) {
            BigDecimal candidate = candles.get(i).getHigh();
            if (candidate.compareTo(max) > 0) {
                max = candidate;
            }
        }
        return max;
    }

    private BigDecimal findMinLow(List<Candle> candles, int start, int end) {
        if (start < 0) {
            start = 0;
        }
        if (end >= candles.size()) {
            end = candles.size() - 1;
        }
        if (start > end) {
            return BigDecimal.ZERO;
        }

        BigDecimal min = candles.get(start).getLow();
        for (int i = start + 1; i <= end; i++) {
            BigDecimal candidate = candles.get(i).getLow();
            if (candidate.compareTo(min) < 0) {
                min = candidate;
            }
        }
        return min;
    }

    private BigDecimal calculateSwingStrength(BigDecimal price, BigDecimal leftSide, BigDecimal rightSide, BigDecimal averageRange) {
        BigDecimal span = (leftSide.compareTo(rightSide) >= 0 ? leftSide.subtract(rightSide) : rightSide.subtract(leftSide));
        if (averageRange.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.valueOf(50);
        }

        BigDecimal normalized = span.divide(averageRange, MATH_CONTEXT).multiply(BigDecimal.valueOf(100));
        BigDecimal capped = normalized.min(BigDecimal.valueOf(100));
        return capped.max(BigDecimal.ZERO);
    }

    private boolean isExternalSwingIndex(int swingCount, int totalCandles) {
        return swingCount < 2 || swingCount > Math.max(2, totalCandles / 2);
    }

    private List<SwingPoint> mergeAndSortSwings(List<SwingPoint> swings) {
        swings.sort(Comparator.comparingInt(SwingPoint::index));
        List<SwingPoint> merged = new ArrayList<>();
        for (SwingPoint swing : swings) {
            boolean duplicate = merged.stream().anyMatch(existing ->
                    existing.index() == swing.index() && existing.type() == swing.type());
            if (!duplicate) {
                merged.add(swing);
            }
        }
        return merged;
    }

    private List<SwingPoint> detectExternalStructure(List<SwingPoint> swings) {
        if (swings == null || swings.isEmpty()) {
            return List.of();
        }

        if (swings.size() <= 2) {
            return List.copyOf(swings);
        }

        List<SwingPoint> external = new ArrayList<>();
        external.add(swings.get(0));
        external.add(swings.get(swings.size() - 1));
        return external;
    }

    private List<StructureLabel> classifyStructure(List<SwingPoint> swings, MarketStructureConfig config) {
        if (swings == null || swings.isEmpty()) {
            return List.of();
        }

        List<StructureLabel> labels = new ArrayList<>();
        List<SwingPoint> highs = swings.stream()
                .filter(s -> s.type() == SwingPoint.SwingDirection.HIGH)
                .sorted(Comparator.comparingInt(SwingPoint::index))
                .toList();
        List<SwingPoint> lows = swings.stream()
                .filter(s -> s.type() == SwingPoint.SwingDirection.LOW)
                .sorted(Comparator.comparingInt(SwingPoint::index))
                .toList();

        BigDecimal tolerance = BigDecimal.valueOf(config.noiseTolerancePercent()).multiply(BigDecimal.valueOf(10));

        for (int i = 1; i < highs.size(); i++) {
            SwingPoint previous = highs.get(i - 1);
            SwingPoint current = highs.get(i);
            BigDecimal delta = current.price().subtract(previous.price()).abs();
            if (delta.compareTo(tolerance) <= 0) {
                continue;
            }
            if (current.price().compareTo(previous.price()) > 0) {
                labels.add(new StructureLabel(current.index(), current.price(), StructureType.HH, current.timestamp(), current.timeframe()));
            } else {
                labels.add(new StructureLabel(current.index(), current.price(), StructureType.LH, current.timestamp(), current.timeframe()));
            }
        }

        for (int i = 1; i < lows.size(); i++) {
            SwingPoint previous = lows.get(i - 1);
            SwingPoint current = lows.get(i);
            BigDecimal delta = current.price().subtract(previous.price()).abs();
            if (delta.compareTo(tolerance) <= 0) {
                continue;
            }
            if (current.price().compareTo(previous.price()) > 0) {
                labels.add(new StructureLabel(current.index(), current.price(), StructureType.HL, current.timestamp(), current.timeframe()));
            } else {
                labels.add(new StructureLabel(current.index(), current.price(), StructureType.LL, current.timestamp(), current.timeframe()));
            }
        }

        labels.sort(Comparator.comparingInt(StructureLabel::index));
        return labels;
    }

    private String determineTrend(List<StructureLabel> labels) {
        boolean hasBullish = labels.stream().anyMatch(label -> label.type() == StructureType.HH || label.type() == StructureType.HL);
        boolean hasBearish = labels.stream().anyMatch(label -> label.type() == StructureType.LH || label.type() == StructureType.LL);

        if (hasBullish && !hasBearish) {
            return "BULLISH";
        }
        if (hasBearish && !hasBullish) {
            return "BEARISH";
        }
        return "RANGE";
    }

    private List<ProtectedLevel> detectProtectedHighs(List<SwingPoint> swings, MarketStructureConfig config) {
        List<ProtectedLevel> protectedHighs = new ArrayList<>();
        if (swings == null || swings.isEmpty()) {
            return protectedHighs;
        }

        List<SwingPoint> highs = swings.stream()
                .filter(s -> s.type() == SwingPoint.SwingDirection.HIGH)
                .sorted(Comparator.comparingInt(SwingPoint::index))
                .toList();

        for (SwingPoint high : highs) {
            protectedHighs.add(new ProtectedLevel(high.price(), "PROTECTED_HIGH", high.timestamp(), high.strength(), high.timeframe()));
        }

        if (config.protectedLookback() > 0 && protectedHighs.size() > config.protectedLookback()) {
            return protectedHighs.subList(Math.max(0, protectedHighs.size() - config.protectedLookback()), protectedHighs.size());
        }

        return protectedHighs;
    }

    private List<ProtectedLevel> detectProtectedLows(List<SwingPoint> swings, MarketStructureConfig config) {
        List<ProtectedLevel> protectedLows = new ArrayList<>();
        if (swings == null || swings.isEmpty()) {
            return protectedLows;
        }

        List<SwingPoint> lows = swings.stream()
                .filter(s -> s.type() == SwingPoint.SwingDirection.LOW)
                .sorted(Comparator.comparingInt(SwingPoint::index))
                .toList();

        for (SwingPoint low : lows) {
            protectedLows.add(new ProtectedLevel(low.price(), "PROTECTED_LOW", low.timestamp(), low.strength(), low.timeframe()));
        }

        if (config.protectedLookback() > 0 && protectedLows.size() > config.protectedLookback()) {
            return protectedLows.subList(Math.max(0, protectedLows.size() - config.protectedLookback()), protectedLows.size());
        }

        return protectedLows;
    }

    private SMCMarketStructureResult emptyResult() {
        return new SMCMarketStructureResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "RANGE");
    }

}
