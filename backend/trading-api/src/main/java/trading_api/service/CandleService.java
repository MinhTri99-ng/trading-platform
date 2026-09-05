package trading_api.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import trading_api.dto.CandleDTO;
import trading_api.entity.Candle;
import trading_api.repository.CandleRepository;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class CandleService {
    private final CandleRepository candleRepository;

    public CandleService(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    public List<CandleDTO> getAllCandles(String symbol, String timeframe) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol cannot be blank");
        if (!List.of("1m", "5m", "15m", "30m", "1h", "2h", "3h", "4h", "12h", "1d", "1w").contains(timeframe)) {
            throw new IllegalArgumentException("Invalid timeframe");
        }
        return candleRepository.findBySymbolAndTimeframeOrderByTimestampAsc(symbol, timeframe).stream()
                .map(c -> new CandleDTO(c.getSymbol(), c.getTimeframe(), c.getTimestamp(), c.getOpen(), c.getHigh(), c.getLow(), c.getClose(), c.getVolume()))
                .toList();
    }

    public List<CandleDTO> getCandles(String symbol, String timeframe, Instant from, Instant to, int limit) {
        // Validate symbol
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be blank");
        }

        // Validate timeframe
        if (!List.of("1m", "5m", "15m", "1h").contains(timeframe)) {
            throw new IllegalArgumentException("Invalid timeframe: must be one of 1m, 5m, 15m, 1h");
        }

        // Validate limit
        if (limit <= 0 || limit > 1000) {
            throw new IllegalArgumentException("Limit must be positive and not exceed 1000");
        }

        List<Candle> candles;
        if (from == null && to == null) {
            List<Candle> latestDescending = candleRepository.findLatestCandles(symbol, timeframe);
            candles = new ArrayList<>(latestDescending.stream().limit(limit).toList());
            Collections.reverse(candles);
        } else {
            Pageable pageable = PageRequest.of(0, limit);
            candles = candleRepository.findCandles(symbol, timeframe, from, to, pageable);
        }

        return candles.stream()
                .map(c -> new CandleDTO(
                        c.getSymbol(),
                        c.getTimeframe(),
                        c.getTimestamp(),
                        c.getOpen(),
                        c.getHigh(),
                        c.getLow(),
                        c.getClose(),
                        c.getVolume()
                ))
                .collect(Collectors.toList());
    }
}
