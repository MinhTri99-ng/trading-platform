package trading_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import trading_api.indicator.IndicatorResult;
import trading_api.indicator.IndicatorService;

@RestController
@RequestMapping("/api/indicators")
public class IndicatorController {

    private final IndicatorService indicatorService;

    public IndicatorController(
            IndicatorService indicatorService
    ) {
        this.indicatorService = indicatorService;
    }

    @GetMapping
    public ResponseEntity<IndicatorResult> getIndicators(
            @RequestParam String symbol,
            @RequestParam String timeframe
    ) {

        IndicatorResult result =
                indicatorService.calculate(
                        symbol,
                        timeframe
                );

        return ResponseEntity.ok(result);
    }
}