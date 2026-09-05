package trading_api.market;

import org.springframework.stereotype.Service;
import trading_api.indicator.EMAIndicator;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TrendService {

    private final EMAIndicator emaIndicator;
    private final MarketStructureService marketStructureService;

    public TrendService() {
        this.emaIndicator = new EMAIndicator();
        this.marketStructureService =
                new MarketStructureService();
    }

    public TrendResult analyze(List<Candle> candles) {

        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Candle list cannot be empty"
            );
        }

        BigDecimal ema50 =
                emaIndicator.calculate(candles, 50);

        BigDecimal ema200 =
                emaIndicator.calculate(candles, 200);

        MarketStructureService.MarketStructureResult structure =
                marketStructureService.analyze(candles);

        TrendDirection trend =
                determineTrend(
                        ema50,
                        ema200,
                        structure
                );

        return new TrendResult(
                trend,
                ema50,
                ema200,
                structure
        );
    }

    private TrendDirection determineTrend(
            BigDecimal ema50,
            BigDecimal ema200,
            MarketStructureService.MarketStructureResult structure
    ) {

        /*
         * Không đủ dữ liệu để tính EMA200.
         */
        if (ema50 == null || ema200 == null) {
            return TrendDirection.SIDEWAYS;
        }

        boolean bullishEMA =
                ema50.compareTo(ema200) > 0;

        boolean bearishEMA =
                ema50.compareTo(ema200) < 0;

        boolean bullishStructure =
                hasStructure(
                        structure,
                        MarketStructureService.StructureType.HH
                )
                        &&
                        hasStructure(
                                structure,
                                MarketStructureService.StructureType.HL
                        );

        boolean bearishStructure =
                hasStructure(
                        structure,
                        MarketStructureService.StructureType.LH
                )
                        &&
                        hasStructure(
                                structure,
                                MarketStructureService.StructureType.LL
                        );

        /*
         * V1:
         *
         * EMA50 > EMA200
         * +
         * HH + HL
         * =
         * BULLISH
         */
        if (bullishEMA && bullishStructure) {
            return TrendDirection.BULLISH;
        }

        /*
         * V1:
         *
         * EMA50 < EMA200
         * +
         * LH + LL
         * =
         * BEARISH
         */
        if (bearishEMA && bearishStructure) {
            return TrendDirection.BEARISH;
        }

        return TrendDirection.SIDEWAYS;
    }

    private boolean hasStructure(
            MarketStructureService.MarketStructureResult structure,
            MarketStructureService.StructureType type
    ) {

        return structure.labels()
                .stream()
                .anyMatch(label ->
                        label.type() == type
                );
    }

    public enum TrendDirection {
        BULLISH,
        BEARISH,
        SIDEWAYS
    }

    public record TrendResult(
            TrendDirection trend,
            BigDecimal ema50,
            BigDecimal ema200,
            MarketStructureService.MarketStructureResult structure
    ) {
    }
}