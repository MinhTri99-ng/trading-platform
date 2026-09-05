package trading_api.market;

import org.springframework.stereotype.Service;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BreakoutService {

    /*
     * Số candle dùng để xác định Support / Resistance.
     */
    private static final int LOOKBACK = 20;

    /*
     * ATR dùng để tạo buffer chống breakout giả
     * do giá chỉ chọc nhẹ qua vùng S/R.
     */
    private static final BigDecimal ATR_BUFFER_MULTIPLIER =
            BigDecimal.valueOf(0.2);

    public BreakoutResult analyze(
            List<Candle> candles,
            BigDecimal atr
    ) {

        if (candles == null || candles.size() < LOOKBACK + 1) {
            throw new IllegalArgumentException(
                    "Not enough candles"
            );
        }

        if (atr == null || atr.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "ATR cannot be null or negative"
            );
        }

        /*
         * Lấy 20 candle trước candle hiện tại
         * để xác định vùng Support / Resistance.
         *
         * Không tính candle hiện tại vào S/R.
         */
        int currentIndex = candles.size() - 1;

        int startIndex =
                currentIndex - LOOKBACK;

        BigDecimal support = null;
        BigDecimal resistance = null;

        for (int i = startIndex; i < currentIndex; i++) {

            Candle candle = candles.get(i);

            if (support == null ||
                    candle.getLow().compareTo(support) < 0) {

                support = candle.getLow();
            }

            if (resistance == null ||
                    candle.getHigh().compareTo(resistance) > 0) {

                resistance = candle.getHigh();
            }
        }

        Candle current =
                candles.get(currentIndex);

        BigDecimal close =
                current.getClose();

        BigDecimal previousClose =
                candles.get(currentIndex - 1)
                        .getClose();

        /*
         * ATR buffer.
         *
         * Ví dụ:
         *
         * Resistance = 100
         * ATR = 2
         * buffer = 0.4
         *
         * Breakout phải vượt:
         *
         * 100 + 0.4 = 100.4
         */
        BigDecimal buffer =
                atr.multiply(
                        ATR_BUFFER_MULTIPLIER
                );

        BigDecimal breakoutResistance =
                resistance.add(buffer);

        BigDecimal breakoutSupport =
                support.subtract(buffer);

        /*
         * NORMAL BULLISH BREAKOUT
         *
         * Giá trước đó còn dưới resistance
         * và candle hiện tại đóng trên resistance + buffer.
         */
        boolean bullishBreakout =
                previousClose.compareTo(resistance) <= 0
                        &&
                        close.compareTo(
                                breakoutResistance
                        ) > 0;

        /*
         * NORMAL BEARISH BREAKOUT
         *
         * Giá trước đó còn trên support
         * và candle hiện tại đóng dưới support - buffer.
         */
        boolean bearishBreakout =
                previousClose.compareTo(support) >= 0
                        &&
                        close.compareTo(
                                breakoutSupport
                        ) < 0;

        /*
         * FAKE BULLISH BREAKOUT
         *
         * Giá vượt resistance
         * nhưng đóng trở lại bên dưới resistance.
         */
        boolean fakeBullishBreakout =
                current.getHigh().compareTo(
                        breakoutResistance
                ) > 0
                        &&
                        close.compareTo(resistance) < 0;

        /*
         * FAKE BEARISH BREAKOUT
         *
         * Giá xuyên support
         * nhưng đóng trở lại phía trên support.
         */
        boolean fakeBearishBreakout =
                current.getLow().compareTo(
                        breakoutSupport
                ) < 0
                        &&
                        close.compareTo(support) > 0;

        BreakoutType type;

        if (bullishBreakout) {

            type = BreakoutType.BULLISH_BREAKOUT;

        } else if (bearishBreakout) {

            type = BreakoutType.BEARISH_BREAKOUT;

        } else if (fakeBullishBreakout) {

            type = BreakoutType.FAKE_BULLISH_BREAKOUT;

        } else if (fakeBearishBreakout) {

            type = BreakoutType.FAKE_BEARISH_BREAKOUT;

        } else {

            type = BreakoutType.NO_BREAKOUT;
        }

        return new BreakoutResult(
                type,
                support,
                resistance,
                buffer,
                close
        );
    }

    public enum BreakoutType {

        BULLISH_BREAKOUT,

        BEARISH_BREAKOUT,

        FAKE_BULLISH_BREAKOUT,

        FAKE_BEARISH_BREAKOUT,

        NO_BREAKOUT
    }

    public record BreakoutResult(
            BreakoutType type,
            BigDecimal support,
            BigDecimal resistance,
            BigDecimal atrBuffer,
            BigDecimal close
    ) {
    }
}