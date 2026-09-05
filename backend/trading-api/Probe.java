import java.math.*;
import java.util.*;
import trading_api.entity.Candle;
import trading_api.market.*;
public class Probe {
  static Candle c(double o,double h,double l,double cl,double v){ Candle x = new Candle(); x.setOpen(BigDecimal.valueOf(o)); x.setHigh(BigDecimal.valueOf(h)); x.setLow(BigDecimal.valueOf(l)); x.setClose(BigDecimal.valueOf(cl)); x.setVolume(BigDecimal.valueOf(v)); return x; }
  static List<Candle> buildWait(){
    List<Candle> candles = new ArrayList<>();
    double[] highs = {102,104,103,106,107,105,109,110,108,112};
    double[] lows = {98,100,101,102,103,104,105,106,107,108};
    for(int i=0;i<highs.length;i++){
      candles.add(c(lows[i]+0.4, highs[i], lows[i], highs[i]-0.4, 1000));
    }
    for(int i=0;i<20;i++) candles.add(c(100,110,90,100,1000));
    candles.add(c(109,113,108,112,4000));
    candles.add(c(112.2,113.2,111.2,112.5,4000));
    return candles;
  }
  public static void main(String[] args){
    List<Candle> candles = buildWait();
    TrendService.TrendResult tr = new TrendService().analyze(candles); System.out.println("trend="+tr.trend()+" labels="+tr.structure().labels());
    BigDecimal atr = new AtrService().calculate(candles); System.out.println("atr="+atr);
    for(int i=0;i<candles.size();i++){
      List<Candle> w=candles.subList(0,i+1);
      try { BreakoutService.BreakoutResult br = new BreakoutService().analyze(w, atr); 
        if(br.type()!=BreakoutService.BreakoutType.NO_BREAKOUT) System.out.println("window="+i+" type="+br.type()+" support="+br.support()+" resistance="+br.resistance()+" close="+br.close());
      } catch(Exception e) { }
    }
    System.out.println("signal="+new TradingStrategyService().analyze(candles, BigDecimal.valueOf(1000), BigDecimal.valueOf(1)));
  }
}
