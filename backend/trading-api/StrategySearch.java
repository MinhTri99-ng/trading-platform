import trading_api.entity.Candle;
import trading_api.market.*;
import java.math.*;
import java.util.*;

public class StrategySearch {
    static Candle c(double o, double h, double l, double cl, double v) {
        Candle candle = new Candle();
        candle.setOpen(BigDecimal.valueOf(o));
        candle.setHigh(BigDecimal.valueOf(h));
        candle.setLow(BigDecimal.valueOf(l));
        candle.setClose(BigDecimal.valueOf(cl));
        candle.setVolume(BigDecimal.valueOf(v));
        return candle;
    }
    static List<Candle> buildBase(double baseHigh, double baseLow, double step, double[] highs, double[] lows, int blocks) {
        List<Candle> list = new ArrayList<>();
        for (int block = 0; block < blocks; block++) {
            double offset = block * step;
            for (int i = 0; i < highs.length; i++) {
                double high = baseHigh - offset - highs[i];
                double low = baseLow - offset - lows[i];
                list.add(c(high - 0.5, high, low, high - 0.5, 1000));
            }
        }
        return list;
    }
    public static void main(String[] args) {
        double[] highs = {102,103,105,103,102,108,110,108,107,106,114,116,114,113,112};
        double[] lows = {98,99,100,99,98,101,103,102,101,100,106,108,107,106,105};
        for (double baseHigh = 180; baseHigh <= 260; baseHigh += 10) {
            for (double baseLow = 150; baseLow <= 230; baseLow += 10) {
                for (double step = 6.0; step <= 12.0; step += 1.0) {
                    for (double breakoutClose = 30; breakoutClose <= 120; breakoutClose += 2.0) {
                        List<Candle> candles = buildBase(baseHigh, baseLow, step, highs, lows, 15);
                        Candle breakout = c(breakoutClose + 3.0, breakoutClose + 5.0, breakoutClose - 2.0, breakoutClose, 4000);
                        Candle retest = c(breakoutClose + 2.0, breakoutClose + 6.0, breakoutClose - 1.5, breakoutClose + 1.0, 4000);
                        candles.add(breakout); candles.add(retest);
                        try {
                            TrendService trendService = new TrendService();
                            AtrService atrService = new AtrService();
                            BreakoutService breakoutService = new BreakoutService();
                            RetestService retestService = new RetestService();
                            TrendService.TrendResult trend = trendService.analyze(candles);
                            BigDecimal atr = atrService.calculate(candles);
                            BreakoutService.BreakoutResult br = breakoutService.analyze(candles, atr);
                            RetestService.RetestResult rr = retestService.analyze(candles, atr);
                            if (trend.trend() == TrendService.TrendDirection.BEARISH && br.type() == BreakoutService.BreakoutType.BEARISH_BREAKOUT && rr.state() == SetupState.BREAKOUT) {
                                System.out.println("FOUND baseHigh=" + baseHigh + " baseLow=" + baseLow + " step=" + step + " breakoutClose=" + breakoutClose + " atr=" + atr);
                                System.out.println("trend=" + trend.trend() + " breakout=" + br.type() + " state=" + rr.state() + " support=" + br.support() + " resistance=" + br.resistance());
                                System.exit(0);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        System.out.println("NONE");
    }
}
