package trading_api.market;

import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class MarketStructureService {

    private static final int SWING_LENGTH = 2;

    public MarketStructureResult analyze(List<Candle> candles) {

        if (candles == null || candles.size() < 5) {
            return new MarketStructureResult(
                    List.of(),
                    List.of(),
                    List.of(),
                    MarketTrend.RANGE
            );
        }

        List<SwingPoint> swingHighs = new ArrayList<>();
        List<SwingPoint> swingLows = new ArrayList<>();

        detectSwingPoints(candles, swingHighs, swingLows);

        List<StructureLabel> labels = classifyStructure(
                swingHighs,
                swingLows
        );

        MarketTrend trend = determineTrend(labels);

        return new MarketStructureResult(
                swingHighs,
                swingLows,
                labels,
                trend
        );
    }

    private void detectSwingPoints(
            List<Candle> candles,
            List<SwingPoint> swingHighs,
            List<SwingPoint> swingLows
    ) {

        for (int i = SWING_LENGTH;
             i < candles.size() - SWING_LENGTH;
             i++) {

            Candle current = candles.get(i);

            boolean swingHigh = isSwingHigh(candles, i);
            boolean swingLow = isSwingLow(candles, i);

            if (swingHigh) {

                swingHighs.add(
                        new SwingPoint(
                                i,
                                current.getHigh(),
                                SwingType.HIGH
                        )
                );
            }

            if (swingLow) {

                swingLows.add(
                        new SwingPoint(
                                i,
                                current.getLow(),
                                SwingType.LOW
                        )
                );
            }
        }
    }

    private boolean isSwingHigh(
            List<Candle> candles,
            int index
    ) {

        BigDecimal currentHigh =
                candles.get(index).getHigh();

        for (int i = 1; i <= SWING_LENGTH; i++) {

            BigDecimal leftHigh =
                    candles.get(index - i).getHigh();

            BigDecimal rightHigh =
                    candles.get(index + i).getHigh();

            if (currentHigh.compareTo(leftHigh) <= 0) {
                return false;
            }

            if (currentHigh.compareTo(rightHigh) <= 0) {
                return false;
            }
        }

        return true;
    }

    private boolean isSwingLow(
            List<Candle> candles,
            int index
    ) {

        BigDecimal currentLow =
                candles.get(index).getLow();

        for (int i = 1; i <= SWING_LENGTH; i++) {

            BigDecimal leftLow =
                    candles.get(index - i).getLow();

            BigDecimal rightLow =
                    candles.get(index + i).getLow();

            if (currentLow.compareTo(leftLow) >= 0) {
                return false;
            }

            if (currentLow.compareTo(rightLow) >= 0) {
                return false;
            }
        }

        return true;
    }

    private List<StructureLabel> classifyStructure(
            List<SwingPoint> swingHighs,
            List<SwingPoint> swingLows
    ) {

        List<StructureLabel> labels = new ArrayList<>();

        for (int i = 1; i < swingHighs.size(); i++) {

            SwingPoint previous =
                    swingHighs.get(i - 1);

            SwingPoint current =
                    swingHighs.get(i);

            int comparison =
                    current.price().compareTo(previous.price());

            if (comparison > 0) {

                labels.add(
                        new StructureLabel(
                                current.index(),
                                current.price(),
                                StructureType.HH
                        )
                );

            } else {

                labels.add(
                        new StructureLabel(
                                current.index(),
                                current.price(),
                                StructureType.LH
                        )
                );
            }
        }

        for (int i = 1; i < swingLows.size(); i++) {

            SwingPoint previous =
                    swingLows.get(i - 1);

            SwingPoint current =
                    swingLows.get(i);

            int comparison =
                    current.price().compareTo(previous.price());

            if (comparison > 0) {

                labels.add(
                        new StructureLabel(
                                current.index(),
                                current.price(),
                                StructureType.HL
                        )
                );

            } else {

                labels.add(
                        new StructureLabel(
                                current.index(),
                                current.price(),
                                StructureType.LL
                        )
                );
            }
        }

        labels.sort(
                (a, b) ->
                        Integer.compare(
                                a.index(),
                                b.index()
                        )
        );

        return labels;
    }

    private MarketTrend determineTrend(
            List<StructureLabel> labels
    ) {

        boolean hasHH = false;
        boolean hasHL = false;
        boolean hasLH = false;
        boolean hasLL = false;

        for (StructureLabel label : labels) {

            switch (label.type()) {

                case HH -> hasHH = true;
                case HL -> hasHL = true;
                case LH -> hasLH = true;
                case LL -> hasLL = true;
            }
        }

        if (hasHH && hasHL && !hasLH) {
            return MarketTrend.BULLISH;
        }

        if (hasLH && hasLL && !hasHH) {
            return MarketTrend.BEARISH;
        }

        return MarketTrend.RANGE;
    }

    public enum SwingType {
        HIGH,
        LOW
    }

    public enum StructureType {
        HH,
        HL,
        LH,
        LL
    }

    public enum MarketTrend {
        BULLISH,
        BEARISH,
        RANGE
    }

    public record SwingPoint(
            int index,
            BigDecimal price,
            SwingType type
    ) {}

    public record StructureLabel(
            int index,
            BigDecimal price,
            StructureType type
    ) {}

    public record MarketStructureResult(
            List<SwingPoint> swingHighs,
            List<SwingPoint> swingLows,
            List<StructureLabel> labels,
            MarketTrend trend
    ) {}
}