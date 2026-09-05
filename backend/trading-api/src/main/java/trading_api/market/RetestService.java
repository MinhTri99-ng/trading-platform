package trading_api.market;

import org.springframework.stereotype.Service;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RetestService {

    /*
     * Giá phải quay lại trong vùng này
     * để được xem là retest.
     *
     * Ví dụ:
     *
     * Resistance = 110
     * ATR = 2
     * Buffer = 0.4
     *
     * Retest zone bullish:
     *
     * 109.6 -> 110.4
     */
    private static final BigDecimal ATR_BUFFER_MULTIPLIER =
            BigDecimal.valueOf(0.2);

    private final BreakoutService breakoutService;

    public RetestService() {
        this.breakoutService =
                new BreakoutService();
    }

    /**
     * Phân tích setup hiện tại.
     *
     * Lưu ý:
     * candles phải chứa lịch sử candle theo thứ tự thời gian.
     */
    public RetestResult analyze(
            List<Candle> candles,
            BigDecimal atr
    ) {

        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Candle list cannot be empty"
            );
        }

        if (atr == null || atr.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "ATR cannot be null or negative"
            );
        }

        /*
         * Cần ít nhất 21 candle vì BreakoutService
         * yêu cầu LOOKBACK 20 + current candle.
         */
        if (candles.size() < 21) {
            throw new IllegalArgumentException(
                    "At least 21 candles are required"
            );
        }

        /*
         * -------------------------------------------------
         * BƯỚC 1
         * -------------------------------------------------
         *
         * Kiểm tra candle cuối cùng có breakout hay không.
         */
        BreakoutService.BreakoutResult breakout =
                breakoutService.analyze(
                        candles,
                        atr
                );

        /*
         * Nếu không có breakout:
         *
         * NORMAL
         * FAKE BREAKOUT
         * đều chưa tạo setup retest.
         */
        if (breakout.type()
                == BreakoutService.BreakoutType.NO_BREAKOUT) {

            return createResult(
                    SetupState.NONE,
                    null,
                    breakout
            );
        }

        /*
         * Fake breakout cũng không được phép
         * tiến vào retest setup.
         */
        if (breakout.type()
                == BreakoutService.BreakoutType.FAKE_BULLISH_BREAKOUT
                ||
                breakout.type()
                        == BreakoutService.BreakoutType.FAKE_BEARISH_BREAKOUT) {

            return createResult(
                    SetupState.NONE,
                    null,
                    breakout
            );
        }

        /*
         * -------------------------------------------------
         * BƯỚC 2
         * -------------------------------------------------
         *
         * Có breakout.
         */
        BreakoutDirection direction;

        if (breakout.type()
                == BreakoutService.BreakoutType.BULLISH_BREAKOUT) {

            direction = BreakoutDirection.BULLISH;

        } else {

            direction = BreakoutDirection.BEARISH;
        }

        /*
         * Candle cuối chính là candle breakout.
         *
         * Chưa có candle sau breakout để retest.
         *
         * Vì vậy state:
         *
         * BREAKOUT
         */
        return createResult(
                SetupState.BREAKOUT,
                direction,
                breakout
        );
    }

    /**
     * Phân tích trạng thái sau khi breakout đã xảy ra.
     *
     * breakoutIndex:
     * index của candle breakout.
     *
     * Ví dụ:
     *
     * 0 ... 19 = history
     * 20 = breakout
     * 21 = candle sau breakout
     * 22 = candle retest
     */
    public RetestResult analyzeAfterBreakout(
            List<Candle> candles,
            int breakoutIndex,
            BreakoutDirection direction,
            BigDecimal support,
            BigDecimal resistance,
            BigDecimal atr
    ) {

        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Candle list cannot be empty"
            );
        }

        if (direction == null) {
            throw new IllegalArgumentException(
                    "Breakout direction cannot be null"
            );
        }

        if (support == null || resistance == null) {
            throw new IllegalArgumentException(
                    "Support and resistance cannot be null"
            );
        }

        if (atr == null || atr.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "ATR cannot be null or negative"
            );
        }

        if (breakoutIndex < 0 ||
                breakoutIndex >= candles.size()) {

            throw new IllegalArgumentException(
                    "Invalid breakout index"
            );
        }

        /*
         * Chưa có candle sau breakout.
         */
        if (candles.size() <= breakoutIndex + 1) {

            return new RetestResult(
                    SetupState.BREAKOUT,
                    direction,
                    support,
                    resistance,
                    getRetestLevel(
                            direction,
                            support,
                            resistance
                    ),
                    atr,
                    "Breakout detected"
            );
        }

        BigDecimal buffer =
                atr.multiply(
                        ATR_BUFFER_MULTIPLIER
                );

        BigDecimal retestLevel =
                getRetestLevel(
                        direction,
                        support,
                        resistance
                );

        /*
         * Duyệt tất cả candle sau breakout.
         */
        Candle breakoutCandle = candles.get(breakoutIndex);

        for (
                int i = breakoutIndex + 1;
                i < candles.size();
                i++
        ) {

            Candle candle =
                    candles.get(i);

            /*
             * ---------------------------------------------
             * BULLISH
             * ---------------------------------------------
             */
            if (direction == BreakoutDirection.BULLISH) {

                /*
                 * Nếu giá đóng cửa xuyên mạnh xuống dưới
                 * resistance - buffer:
                 *
                 * BREAKOUT INVALID
                 */
                if (candle.getClose().compareTo(
                        resistance.subtract(buffer)
                ) < 0) {

                    return new RetestResult(
                            SetupState.NONE,
                            direction,
                            support,
                            resistance,
                            retestLevel,
                            atr,
                            "Bullish breakout invalidated"
                    );
                }

                /*
                 * Kiểm tra giá có chạm vùng retest không.
                 *
                 * Candle low <= resistance + buffer
                 *
                 * và
                 *
                 * Candle high >= resistance - buffer
                 */
                boolean insideRetestZone =
                        candle.getLow().compareTo(
                                resistance.add(buffer)
                        ) <= 0
                                &&
                                candle.getHigh().compareTo(
                                        resistance.subtract(buffer)
                                ) >= 0;

                if (insideRetestZone) {
                    boolean confirmed =
                            candle.getClose().compareTo(
                                    resistance
                            ) > 0
                                    &&
                                    candle.getClose().compareTo(
                                            candle.getOpen()
                                    ) > 0;

                    if (confirmed) {

                        return new RetestResult(
                                SetupState.CONFIRMED,
                                direction,
                                support,
                                resistance,
                                retestLevel,
                                atr,
                                "Bullish retest confirmed"
                        );
                    }

                    return new RetestResult(
                            SetupState.RETEST,
                            direction,
                            support,
                            resistance,
                            retestLevel,
                            atr,
                            "Bullish retest detected"
                    );
                }
            }

            /*
             * ---------------------------------------------
             * BEARISH
             * ---------------------------------------------
             */
            else {

                /*
                 * Nếu giá đóng cửa xuyên mạnh lên trên
                 * support + buffer:
                 *
                 * BREAKOUT INVALID
                 */
                if (candle.getClose().compareTo(
                        support.add(buffer)
                ) > 0) {

                    return new RetestResult(
                            SetupState.NONE,
                            direction,
                            support,
                            resistance,
                            retestLevel,
                            atr,
                            "Bearish breakout invalidated"
                    );
                }

                /*
                 * Kiểm tra giá có chạm vùng retest không.
                 */
                boolean insideRetestZone =
                        candle.getLow().compareTo(
                                support.add(buffer)
                        ) <= 0
                                &&
                                candle.getHigh().compareTo(
                                        support.subtract(buffer)
                                ) >= 0;

                if (insideRetestZone) {
                    boolean confirmed =
                            candle.getClose().compareTo(
                                    support
                            ) < 0
                                    &&
                                    candle.getClose().compareTo(
                                            candle.getOpen()
                                    ) < 0;

                    if (confirmed) {

                        return new RetestResult(
                                SetupState.CONFIRMED,
                                direction,
                                support,
                                resistance,
                                retestLevel,
                                atr,
                                "Bearish retest confirmed"
                        );
                    }

                    return new RetestResult(
                            SetupState.RETEST,
                            direction,
                            support,
                            resistance,
                            retestLevel,
                            atr,
                            "Bearish retest detected"
                    );
                }
            }
        }

        /*
         * Breakout vẫn còn hiệu lực
         * nhưng chưa retest.
         */
        return new RetestResult(
                SetupState.WAITING_RETEST,
                direction,
                support,
                resistance,
                retestLevel,
                atr,
                "Waiting for retest"
        );
    }

    private BigDecimal getRetestLevel(
            BreakoutDirection direction,
            BigDecimal support,
            BigDecimal resistance
    ) {

        if (direction == BreakoutDirection.BULLISH) {
            return resistance;
        }

        return support;
    }

    private RetestResult createResult(
            SetupState state,
            BreakoutDirection direction,
            BreakoutService.BreakoutResult breakout
    ) {

        BigDecimal retestLevel = null;

        if (direction != null) {

            retestLevel =
                    getRetestLevel(
                            direction,
                            breakout.support(),
                            breakout.resistance()
                    );
        }

        return new RetestResult(
                state,
                direction,
                breakout.support(),
                breakout.resistance(),
                retestLevel,
                null,
                "Breakout analysis"
        );
    }

    public enum BreakoutDirection {
        BULLISH,
        BEARISH
    }

    public record RetestResult(
            SetupState state,
            BreakoutDirection direction,
            BigDecimal support,
            BigDecimal resistance,
            BigDecimal retestLevel,
            BigDecimal atr,
            String reason
    ) {
    }
}