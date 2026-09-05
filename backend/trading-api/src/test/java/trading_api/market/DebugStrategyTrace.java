package trading_api.market;

import org.junit.jupiter.api.Test;
import trading_api.entity.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

class DebugStrategyTrace {
    @Test
    void traceLongDataset() {
        List<Candle> candles = new ArrayList<>();
        double[] highs = {102,103,105,103,102,108,110,108,107,106,114,116,114,113,112,118,119,120,122,123};
        double[] lows = {98,99,100,99,98,101,103,102,101,100,106,108,107,106,105,111,112,113,115,116};
        for (int i = 0; i < highs.length; i++) {
            Candle c = new Candle();
            c.setOpen(BigDecimal.valueOf(lows[i] + 0.5));
            c.setHigh(BigDecimal.valueOf(highs[i]));
            c.setLow(BigDecimal.valueOf(lows[i]));
            c.setClose(BigDecimal.valueOf(highs[i] - 0.5));
            c.setVolume(BigDecimal.valueOf(1000));
            candles.add(c);
        }

        Candle breakout = new Candle();
        breakout.setOpen(BigDecimal.valueOf(126.0));
        breakout.setHigh(BigDecimal.valueOf(132.0));
        breakout.setLow(BigDecimal.valueOf(124.5));
        breakout.setClose(BigDecimal.valueOf(131.0));
        breakout.setVolume(BigDecimal.valueOf(4000));
        candles.add(breakout);

        Candle retest = new Candle();
        retest.setOpen(BigDecimal.valueOf(129.5));
        retest.setHigh(BigDecimal.valueOf(132.2));
        retest.setLow(BigDecimal.valueOf(127.8));
        retest.setClose(BigDecimal.valueOf(131.4));
        retest.setVolume(BigDecimal.valueOf(4000));
        candles.add(retest);

        TrendService trendService = new TrendService();
        System.out.println("TREND=" + trendService.analyze(candles).trend());
        MarketStructureService.MarketStructureResult struct = new MarketStructureService().analyze(candles);
        System.out.println("STRUCTURE=" + struct.labels());
        BigDecimal atr = new AtrService().calculate(candles);
        System.out.println("ATR=" + atr);
        for (int i = 20; i < candles.size(); i++) {
            List<Candle> window = candles.subList(0, i + 1);
            BreakoutService.BreakoutResult br = new BreakoutService().analyze(window, atr);
            System.out.println("i=" + i + " type=" + br.type() + " support=" + br.support() + " resistance=" + br.resistance() + " close=" + br.close());
            if (br.type() == BreakoutService.BreakoutType.BULLISH_BREAKOUT) {
                RetestService.RetestResult rr = new RetestService().analyzeAfterBreakout(candles, i, RetestService.BreakoutDirection.BULLISH, br.support(), br.resistance(), atr);
                System.out.println("retestState=" + rr.state() + " reason=" + rr.reason());
            }
        }
        TradingStrategyService s = new TradingStrategyService();
        System.out.println("SIGNAL=" + s.analyze(candles, BigDecimal.valueOf(1000), BigDecimal.valueOf(1)));
    }
}
