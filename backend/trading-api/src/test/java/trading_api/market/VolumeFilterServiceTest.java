package trading_api.market;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VolumeFilterServiceTest {

    private final VolumeFilterService service =
            new VolumeFilterService();

    @Test
    void shouldReturnHighVolumeWhenCurrentCandleExceedsThreshold() {
        List<Candle> candles = createVolumeSeries(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(180)
        );

        VolumeFilterService.VolumeFilterResult result =
                service.analyze(candles);

        assertEquals(
                VolumeFilterService.VolumeStatus.HIGH_VOLUME,
                result.status()
        );
        assertEquals(
                BigDecimal.valueOf(180),
                result.currentVolume()
        );
        assertEquals(
                BigDecimal.valueOf(100),
                result.averageVolume()
        );
        assertEquals(
                0,
                result.threshold().compareTo(new BigDecimal("150.0"))
        );
    }

    @Test
    void shouldReturnLowVolumeWhenCurrentCandleFailsThreshold() {
        List<Candle> candles = createVolumeSeries(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(130)
        );

        VolumeFilterService.VolumeFilterResult result =
                service.analyze(candles);

        assertEquals(
                VolumeFilterService.VolumeStatus.LOW_VOLUME,
                result.status()
        );
        assertEquals(
                BigDecimal.valueOf(100),
                result.averageVolume()
        );
        assertEquals(
                0,
                result.threshold().compareTo(new BigDecimal("150.0"))
        );
    }

    @Test
    void shouldReturnHighVolumeForAbnormalSpike() {
        List<Candle> candles = createVolumeSeries(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(5000)
        );

        VolumeFilterService.VolumeFilterResult result =
                service.analyze(candles);

        assertEquals(
                VolumeFilterService.VolumeStatus.HIGH_VOLUME,
                result.status()
        );
        assertEquals(
                BigDecimal.valueOf(5000),
                result.currentVolume()
        );
        assertEquals(
                BigDecimal.valueOf(100),
                result.averageVolume()
        );
    }

    @Test
    void shouldExcludeCurrentCandleFromAverageVolumeCalculation() {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            candles.add(createCandle(BigDecimal.valueOf(50 + i)));
        }

        candles.add(createCandle(BigDecimal.valueOf(9999)));

        VolumeFilterService.VolumeFilterResult result =
                service.analyze(candles);

        assertEquals(
                BigDecimal.valueOf(9999),
                result.currentVolume()
        );

        BigDecimal expectedAverage = new BigDecimal("59.5");

        assertEquals(
                0,
                result.averageVolume().compareTo(expectedAverage)
        );
    }

    @Test
    void shouldThrowForInsufficientCandles() {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            candles.add(createCandle(BigDecimal.valueOf(100)));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyze(candles)
        );
    }

    private List<Candle> createVolumeSeries(
            BigDecimal previousVolume,
            BigDecimal currentVolume
    ) {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            candles.add(createCandle(previousVolume));
        }

        candles.add(createCandle(currentVolume));

        return candles;
    }

    private Candle createCandle(BigDecimal volume) {
        Candle candle = new Candle();
        candle.setOpen(BigDecimal.ONE);
        candle.setHigh(BigDecimal.valueOf(2));
        candle.setLow(BigDecimal.valueOf(0.5));
        candle.setClose(BigDecimal.valueOf(1.5));
        candle.setVolume(volume);
        return candle;
    }
}
