package trading_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import trading_api.dto.CandleDTO;
import trading_api.entity.Candle;
import trading_api.exception.ErrorResponse;
import trading_api.repository.CandleRepository;
import trading_api.service.CandleService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketController {
    private final CandleRepository candleRepository;
    private final CandleService candleService;

    public MarketController(CandleRepository candleRepository, CandleService candleService) {
        this.candleRepository = candleRepository;
        this.candleService = candleService;
    }

    @GetMapping("/candles")
    public List<CandleDTO> getCandles(
            @RequestParam("symbol") String symbol,
            @RequestParam("timeframe") String timeframe,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit
    ) {
        Instant fromInstant = parseInstant(from);
        Instant toInstant = parseInstant(to);
        return candleService.getCandles(symbol, timeframe, fromInstant, toInstant, limit);
    }

    @GetMapping("/candles/history")
    public List<CandleDTO> getAllCandles(
            @RequestParam("symbol") String symbol,
            @RequestParam("timeframe") String timeframe
    ) {
        return candleService.getAllCandles(symbol, timeframe);
    }

    private Instant parseInstant(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Instant.parse(val);
        } catch (Exception e) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(val));
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid timestamp format: " + val);
            }
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException e) {
        return new ErrorResponse(e.getMessage());
    }
    @PostMapping("/candles/seed")
    public String seedCandles() {

        for (int i = 0; i < 100; i++) {

            Candle candle = new Candle();

            candle.setSymbol("BTCUSDT");
            candle.setTimeframe("1m");
            candle.setTimestamp(Instant.now().minusSeconds((long)(99-i)*60));

            BigDecimal open = BigDecimal.valueOf(100000 + i);

            candle.setOpen(open);
            candle.setHigh(open.add(BigDecimal.valueOf(50)));
            candle.setLow(open.subtract(BigDecimal.valueOf(50)));
            candle.setClose(open.add(BigDecimal.valueOf(20)));
            candle.setVolume(BigDecimal.valueOf(1000 + i));

            candleRepository.save(candle);
        }

        return "100 candles inserted";
    }
    @GetMapping("/candles/symbol/{symbol}")
    public List<Candle> getBySymbol(@PathVariable String symbol) {
        return candleRepository.findBySymbolOrderByTimestampAsc(symbol);
    }
    @GetMapping("/candles/timeframe/{timeframe}")
    public List<Candle> getByTimeframe(@PathVariable String timeframe) {
        return candleRepository.findByTimeframe(timeframe);
    }
}
