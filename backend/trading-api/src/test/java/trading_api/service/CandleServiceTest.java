package trading_api.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import trading_api.entity.Candle;
import trading_api.repository.CandleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CandleServiceTest {
    @Test
    void returnsLatestWindowInAscendingOrder() {
        CandleRepository repository = Mockito.mock(CandleRepository.class);
        Mockito.when(repository.findLatestCandles("BTCUSDT", "1h"))
                .thenReturn(List.of(candle(3), candle(2), candle(1)));

        CandleService service = new CandleService(repository);

        var result = service.getCandles("BTCUSDT", "1h", null, null, 2);

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).timestamp().getEpochSecond());
        assertEquals(3, result.get(1).timestamp().getEpochSecond());
        Mockito.verify(repository).findLatestCandles("BTCUSDT", "1h");
        Mockito.verify(repository, Mockito.never()).findCandles(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private Candle candle(int timestamp) {
        Candle candle = new Candle();
        candle.setSymbol("BTCUSDT");
        candle.setTimeframe("1h");
        candle.setTimestamp(Instant.ofEpochSecond(timestamp));
        candle.setOpen(BigDecimal.valueOf(timestamp));
        candle.setHigh(BigDecimal.valueOf(timestamp + 1));
        candle.setLow(BigDecimal.valueOf(timestamp - 1));
        candle.setClose(BigDecimal.valueOf(timestamp));
        candle.setVolume(BigDecimal.ONE);
        return candle;
    }
}