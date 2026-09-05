package trading_api.market;

import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class VolumeFilterService {

    public static final int LOOKBACK = 20;
    public static final BigDecimal MULTIPLIER =
            new BigDecimal("1.5");

    private static final MathContext MATH_CONTEXT =
            new MathContext(20, RoundingMode.HALF_UP);

    private final BigDecimal multiplier;

    public VolumeFilterService() {
        this(MULTIPLIER);
    }

    public VolumeFilterService(BigDecimal multiplier) {
        if (multiplier == null || multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Multiplier must be positive"
            );
        }

        this.multiplier = multiplier;
    }

    public VolumeFilterResult analyze(List<Candle> candles) {
        validateCandles(candles);

        Candle currentCandle = candles.get(candles.size() - 1);
        BigDecimal currentVolume = currentCandle.getVolume();

        validateVolume(currentVolume, "Current candle volume");

        List<Candle> previousCandles = candles.subList(
                candles.size() - LOOKBACK - 1,
                candles.size() - 1
        );

        BigDecimal averageVolume = calculateAverageVolume(previousCandles);
        BigDecimal threshold = averageVolume.multiply(
                multiplier,
                MATH_CONTEXT
        );

        VolumeStatus status = currentVolume.compareTo(threshold) > 0
                ? VolumeStatus.HIGH_VOLUME
                : VolumeStatus.LOW_VOLUME;

        return new VolumeFilterResult(
                status,
                currentVolume,
                averageVolume,
                threshold
        );
    }

    private void validateCandles(List<Candle> candles) {
        if (candles == null) {
            throw new IllegalArgumentException(
                    "Candles cannot be null"
            );
        }

        if (candles.size() < LOOKBACK + 1) {
            throw new IllegalArgumentException(
                    "Candles must contain at least LOOKBACK + 1 candles"
            );
        }

        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            if (candle == null) {
                throw new IllegalArgumentException(
                        "Candle at index " + i + " cannot be null"
                );
            }

            BigDecimal volume = candle.getVolume();
            validateVolume(volume, "Candle at index " + i + " volume");
        }
    }

    private void validateVolume(BigDecimal volume, String fieldName) {
        if (volume == null) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be null"
            );
        }

        if (volume.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be negative"
            );
        }
    }

    private BigDecimal calculateAverageVolume(List<Candle> candles) {
        BigDecimal totalVolume = BigDecimal.ZERO;

        for (Candle candle : candles) {
            totalVolume = totalVolume.add(
                    candle.getVolume(),
                    MATH_CONTEXT
            );
        }

        return totalVolume.divide(
                BigDecimal.valueOf(candles.size()),
                MATH_CONTEXT
        );
    }

    public enum VolumeStatus {
        HIGH_VOLUME,
        LOW_VOLUME
    }

    public record VolumeFilterResult(
            VolumeStatus status,
            BigDecimal currentVolume,
            BigDecimal averageVolume,
            BigDecimal threshold
    ) {
    }
}
