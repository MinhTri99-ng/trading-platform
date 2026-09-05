package trading_api.market;

import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class TradingStrategyService {

    public static final BigDecimal DEFAULT_RISK_PERCENT =
            BigDecimal.valueOf(1);

    public static final BigDecimal DEFAULT_RISK_REWARD_RATIO =
            BigDecimal.valueOf(2);

    private static final MathContext MC =
            new MathContext(20, RoundingMode.HALF_UP);

    private final TrendService trendService;
    private final AtrService atrService;
    private final BreakoutService breakoutService;
    private final RetestService retestService;
    private final VolumeFilterService volumeFilterService;
    private final StopLossService stopLossService;
    private final TakeProfitService takeProfitService;
    private final RiskManagementService riskManagementService;

    public TradingStrategyService() {
        this.trendService = new TrendService();
        this.atrService = new AtrService();
        this.breakoutService = new BreakoutService();
        this.retestService = new RetestService();
        this.volumeFilterService = new VolumeFilterService();
        this.stopLossService = new StopLossService();
        this.takeProfitService = new TakeProfitService();
        this.riskManagementService = new RiskManagementService();
    }

    public TradingSignal analyze(
            List<Candle> candles,
            BigDecimal accountBalance,
            BigDecimal riskPercent
    ) {
        return analyze(
                new StrategyInput(
                        candles,
                        accountBalance,
                        riskPercent
                )
        );
    }

    public TradingSignal analyze(StrategyInput input) {
        if (input == null) {
            throw new IllegalArgumentException(
                    "Strategy input cannot be null"
            );
        }

        List<Candle> candles = input.candles();
        BigDecimal accountBalance = input.accountBalance();
        BigDecimal riskPercent = input.riskPercent();

        if (candles == null) {
            throw new IllegalArgumentException(
                    "Candles cannot be null"
            );
        }

        if (candles.size() < 21) {
            throw new IllegalArgumentException(
                    "At least 21 candles are required"
            );
        }

        if (accountBalance == null || accountBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Account balance must be greater than zero"
            );
        }

        if (riskPercent == null || riskPercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Risk percentage must be greater than zero"
            );
        }

        if (riskPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "Risk percentage cannot exceed 100%"
            );
        }

        try {
            TrendService.TrendResult trendResult =
                    trendService.analyze(candles);

            TrendService.TrendDirection trend =
                    trendResult.trend();

            if (trend == TrendService.TrendDirection.SIDEWAYS) {
                return noTrade(
                        SignalDirection.NONE,
                        SignalStatus.NO_TRADE,
                        trend,
                        null,
                        SetupState.NONE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }

            BigDecimal atr = atrService.calculate(candles);

            if (atr == null || atr.compareTo(BigDecimal.ZERO) <= 0) {
                return noTrade(
                        SignalDirection.NONE,
                        SignalStatus.INVALID,
                        trend,
                        null,
                        SetupState.NONE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }

            BreakoutAnalysis analysis = findValidBreakout(
                    candles,
                    trend,
                    atr
            );

            if (analysis == null) {
                return noTrade(
                        SignalDirection.NONE,
                        SignalStatus.NO_TRADE,
                        trend,
                        null,
                        SetupState.NONE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }

            if (analysis.setupState() != SetupState.CONFIRMED) {
                SignalDirection signalDirection =
                        analysis.direction() == RetestService.BreakoutDirection.BULLISH
                                ? SignalDirection.LONG
                                : SignalDirection.SHORT;

                return noTrade(
                        signalDirection,
                        SignalStatus.WAITING_RETEST,
                        trend,
                        analysis.breakoutType(),
                        analysis.setupState(),
                        null,
                        null,
                        null,
                        null,
                        atr,
                        null
                );
            }

            VolumeFilterService.VolumeFilterResult volumeResult =
                    volumeFilterService.analyze(candles);

            if (volumeResult.status() != VolumeFilterService.VolumeStatus.HIGH_VOLUME) {
                SignalDirection signalDirection =
                        analysis.direction() == RetestService.BreakoutDirection.BULLISH
                                ? SignalDirection.LONG
                                : SignalDirection.SHORT;

                return noTrade(
                        signalDirection,
                        SignalStatus.NO_TRADE,
                        trend,
                        analysis.breakoutType(),
                        analysis.setupState(),
                        null,
                        null,
                        null,
                        null,
                        atr,
                        null
                );
            }

            BigDecimal breakoutClose = analysis.breakout() != null
                    ? analysis.breakout().close()
                    : candles.get(candles.size() - 1).getClose();
            BigDecimal entry = breakoutClose != null
                    ? breakoutClose
                    : candles.get(candles.size() - 1).getClose();

            SignalDirection signalDirection =
                    analysis.direction() == RetestService.BreakoutDirection.BULLISH
                            ? SignalDirection.LONG
                            : SignalDirection.SHORT;

            StopLossService.Direction stopLossDirection =
                    signalDirection == SignalDirection.LONG
                            ? StopLossService.Direction.LONG
                            : StopLossService.Direction.SHORT;

            TradeRiskResult.Direction tradeDirection =
                    signalDirection == SignalDirection.LONG
                            ? TradeRiskResult.Direction.LONG
                            : TradeRiskResult.Direction.SHORT;

            RiskManagementService.Direction riskDirection =
                    signalDirection == SignalDirection.LONG
                            ? RiskManagementService.Direction.LONG
                            : RiskManagementService.Direction.SHORT;

            BigDecimal invalidationLevel =
                    signalDirection == SignalDirection.LONG
                            ? analysis.breakout().support()
                            : analysis.breakout().resistance();

            BigDecimal stopLoss = stopLossService.calculate(
                    entry,
                    invalidationLevel,
                    atr,
                    stopLossDirection
            );

            TradeRiskResult takeProfitResult = takeProfitService.calculate(
                    entry,
                    stopLoss,
                    tradeDirection,
                    DEFAULT_RISK_REWARD_RATIO
            );

            RiskManagementService.RiskManagementResult riskManagementResult =
                    riskManagementService.calculatePositionSize(
                            riskDirection,
                            accountBalance,
                            riskPercent,
                            entry,
                            stopLoss
                    );

            BigDecimal plannedLoss = riskManagementResult.positionSize()
                    .multiply(
                            riskManagementResult.riskPerUnit(),
                            MC
                    );

            if (plannedLoss.compareTo(riskManagementResult.riskAmount()) > 0) {
                return noTrade(
                        signalDirection,
                        SignalStatus.INVALID,
                        trend,
                        analysis.breakoutType(),
                        analysis.setupState(),
                        entry,
                        stopLoss,
                        takeProfitResult.takeProfit(),
                        takeProfitResult.riskRewardRatio(),
                        atr,
                        riskManagementResult.positionSize()
                );
            }

            return new TradingSignal(
                    signalDirection,
                    SignalStatus.VALID,
                    entry,
                    stopLoss,
                    takeProfitResult.takeProfit(),
                    takeProfitResult.riskRewardRatio(),
                    riskManagementResult.positionSize(),
                    riskManagementResult.riskAmount(),
                    atr,
                    trend,
                    analysis.breakoutType(),
                    analysis.setupState()
            );

        } catch (IllegalArgumentException ex) {
            return new TradingSignal(
                    SignalDirection.NONE,
                    SignalStatus.INVALID,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    private BreakoutAnalysis findValidBreakout(
            List<Candle> candles,
            TrendService.TrendDirection trend,
            BigDecimal atr
    ) {
        BreakoutAnalysis best = null;
        int bestEnd = -1;

        for (int start = 0;
             start <= candles.size() - 21;
             start++) {

            for (int end = start + 20;
                 end < candles.size();
                 end++) {

                List<Candle> window =
                        candles.subList(start, end + 1);

                BreakoutService.BreakoutResult breakoutResult =
                        breakoutService.analyze(window, atr);

                BreakoutService.BreakoutType type =
                        breakoutResult.type();

                boolean bullishMatch =
                    trend == TrendService.TrendDirection.BULLISH
                            && type == BreakoutService.BreakoutType.BULLISH_BREAKOUT;

                boolean bearishMatch =
                    trend == TrendService.TrendDirection.BEARISH
                            && type == BreakoutService.BreakoutType.BEARISH_BREAKOUT;
                if (bullishMatch || bearishMatch) {
                    RetestService.BreakoutDirection direction =
                            bullishMatch
                                    ? RetestService.BreakoutDirection.BULLISH
                                    : RetestService.BreakoutDirection.BEARISH;

                    RetestService.RetestResult retestResult =
                            retestService.analyzeAfterBreakout(
                                    candles,
                                    end,
                                    direction,
                                    breakoutResult.support(),
                                    breakoutResult.resistance(),
                                    atr
                            );

                    BreakoutAnalysis candidate = new BreakoutAnalysis(
                            direction,
                            type,
                            retestResult.state(),
                            breakoutResult
                    );

                    int candidatePriority = statePriority(candidate.setupState());
                    int bestPriority = best == null ? -1 : statePriority(best.setupState());
                    int candidateRecency = candles.size() - 1 - end;
                    int bestRecency = best == null ? Integer.MAX_VALUE : candles.size() - 1 - bestEnd;

                    if (best == null
                            || candidateRecency < bestRecency
                            || (candidateRecency == bestRecency && candidatePriority > bestPriority)
                            || (candidateRecency == bestRecency && candidatePriority == bestPriority && end > bestEnd)) {
                        best = candidate;
                        bestEnd = end;
                    }
                }
            }
        }

        return best != null ? best : fallbackBreakout(candles, trend, atr);
    }

    private int statePriority(SetupState state) {
        if (state == null) {
            return 0;
        }
        if (state == SetupState.CONFIRMED) {
            return 3;
        }
        if (state == SetupState.RETEST) {
            return 2;
        }
        if (state == SetupState.WAITING_RETEST) {
            return 1;
        }
        return 0;
    }

    private BreakoutAnalysis fallbackBreakout(
            List<Candle> candles,
            TrendService.TrendDirection trend,
            BigDecimal atr
    ) {
        if (candles == null || candles.size() < 21) {
            return null;
        }

        int lastIndex = candles.size() - 1;
        Candle current = candles.get(lastIndex);
        Candle previous = candles.get(lastIndex - 1);

        int windowStart = Math.max(0, lastIndex - 20);
        BigDecimal localSupport = candles.subList(windowStart, lastIndex).stream()
                .map(Candle::getLow)
                .min(BigDecimal::compareTo)
                .orElse(current.getLow());
        BigDecimal localResistance = candles.subList(windowStart, lastIndex).stream()
                .map(Candle::getHigh)
                .max(BigDecimal::compareTo)
                .orElse(current.getHigh());

        BigDecimal buffer = atr.multiply(BigDecimal.valueOf(0.2));

        if (trend == TrendService.TrendDirection.BULLISH
                && previous.getClose().compareTo(localResistance) <= 0
                && current.getClose().compareTo(localResistance.add(buffer)) > 0) {
            return new BreakoutAnalysis(
                    RetestService.BreakoutDirection.BULLISH,
                    BreakoutService.BreakoutType.BULLISH_BREAKOUT,
                    SetupState.WAITING_RETEST,
                    new BreakoutService.BreakoutResult(
                            BreakoutService.BreakoutType.BULLISH_BREAKOUT,
                            localSupport,
                            localResistance,
                            buffer,
                            current.getClose()
                    )
            );
        }

        if (trend == TrendService.TrendDirection.BEARISH) {
            BigDecimal recentHigh = candles.subList(Math.max(0, lastIndex - 4), lastIndex + 1).stream()
                    .map(Candle::getHigh)
                    .max(BigDecimal::compareTo)
                    .orElse(localResistance);
            BigDecimal recentLow = candles.subList(Math.max(0, lastIndex - 4), lastIndex + 1).stream()
                    .map(Candle::getLow)
                    .min(BigDecimal::compareTo)
                    .orElse(localSupport);

            if (current.getClose().compareTo(recentHigh.subtract(buffer)) < 0
                    && current.getClose().compareTo(recentLow) > 0
                    && current.getClose().compareTo(current.getOpen()) < 0) {
                return new BreakoutAnalysis(
                        RetestService.BreakoutDirection.BEARISH,
                        BreakoutService.BreakoutType.BEARISH_BREAKOUT,
                        SetupState.WAITING_RETEST,
                        new BreakoutService.BreakoutResult(
                                BreakoutService.BreakoutType.BEARISH_BREAKOUT,
                                localSupport,
                                localResistance,
                                buffer,
                                current.getClose()
                        )
                );
            }
        }

        return null;
    }

    private TradingSignal noTrade(
            SignalDirection direction,
            SignalStatus status,
            TrendService.TrendDirection trend,
            BreakoutService.BreakoutType breakout,
            SetupState setupState,
            BigDecimal entry,
            BigDecimal stopLoss,
            BigDecimal takeProfit,
            BigDecimal riskReward,
            BigDecimal atr,
            BigDecimal positionSize
    ) {
        return new TradingSignal(
                direction,
                status,
                entry,
                stopLoss,
                takeProfit,
                riskReward,
                positionSize,
                null,
                atr,
                trend,
                breakout,
                setupState
        );
    }

    public record StrategyInput(
            List<Candle> candles,
            BigDecimal accountBalance,
            BigDecimal riskPercent
    ) {
    }

    public record TradingSignal(
            SignalDirection direction,
            SignalStatus status,
            BigDecimal entry,
            BigDecimal stopLoss,
            BigDecimal takeProfit,
            BigDecimal riskReward,
            BigDecimal positionSize,
            BigDecimal riskAmount,
            BigDecimal atr,
            TrendService.TrendDirection trend,
            BreakoutService.BreakoutType breakout,
            SetupState setupState
    ) {
    }

    public enum SignalDirection {
        LONG,
        SHORT,
        NONE
    }

    public enum SignalStatus {
        VALID,
        NO_TRADE,
        WAITING_RETEST,
        INVALID
    }

    private record BreakoutAnalysis(
            RetestService.BreakoutDirection direction,
            BreakoutService.BreakoutType breakoutType,
            SetupState setupState,
            BreakoutService.BreakoutResult breakout
    ) {
    }
}
