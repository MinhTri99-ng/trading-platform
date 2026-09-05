package trading_api.indicator;

import org.springframework.stereotype.Service;
import trading_api.entity.Candle;
import trading_api.repository.CandleRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
public class IndicatorService {

    private final CandleRepository candleRepository;

    private final EMAIndicator emaIndicator;
    private final ATRIndicator atrIndicator;
    private final VolumeMAIndicator volumeMAIndicator;

    public IndicatorService(
            CandleRepository candleRepository
    ) {

        this.candleRepository = candleRepository;

        this.emaIndicator = new EMAIndicator();
        this.atrIndicator = new ATRIndicator();
        this.volumeMAIndicator =
                new VolumeMAIndicator();
    }

    public IndicatorResult calculate(
            String symbol,
            String timeframe
    ) {

        List<Candle> candles =
                candleRepository
                        .findBySymbolAndTimeframeOrderByTimestampAsc(
                                symbol,
                                timeframe
                        );

        if (candles.isEmpty()) {
            throw new IllegalArgumentException(
                    "No candles found for "
                            + symbol
                            + " "
                            + timeframe
            );
        }

        BigDecimal ema50 =
                emaIndicator.calculate(
                        candles,
                        50
                );

        BigDecimal ema200 =
                emaIndicator.calculate(
                        candles,
                        200
                );

        BigDecimal atr14 =
                atrIndicator.calculate(
                        candles,
                        14
                );

        BigDecimal volumeMA20 =
                volumeMAIndicator.calculate(
                        candles,
                        20
                );

        Candle latest =
                candles.get(candles.size() - 1);

        return new IndicatorResult(
                symbol,
                timeframe,
                latest.getTimestamp(),
                ema50,
                ema200,
                atr14,
                volumeMA20
        );
    }
}