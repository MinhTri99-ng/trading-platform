package trading_api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import trading_api.service.BinanceHistoricalCandleService;

import java.time.Instant;

@RestController
@RequestMapping("/api/market/history")
public class HistoricalCandleController {
    private final BinanceHistoricalCandleService historicalCandleService;

    public HistoricalCandleController(BinanceHistoricalCandleService historicalCandleService) {
        this.historicalCandleService = historicalCandleService;
    }

    @PostMapping("/import")
    public BinanceHistoricalCandleService.ImportResult importCandles(
            @RequestParam String symbol,
            @RequestParam String timeframe,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return historicalCandleService.importCandles(symbol, timeframe, from, to);
    }
}