package trading_api.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import trading_api.dto.CandleDTO;
import trading_api.repository.CandleRepository;
import trading_api.service.CandleService;
import trading_api.auth.JwtService;
import trading_api.auth.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CandleService candleService;

    @MockitoBean
    private CandleRepository candleRepository; // Needed because MarketController constructor injects it

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private UserRepository userRepository;

    @Test
    void testQueryBtcUsdt1mCandles() throws Exception {
        List<CandleDTO> mockResult = List.of(
                new CandleDTO("BTCUSDT", "1m", Instant.parse("2026-08-24T10:00:00Z"),
                        BigDecimal.valueOf(70000.0), BigDecimal.valueOf(70100.0),
                        BigDecimal.valueOf(69900.0), BigDecimal.valueOf(70050.0),
                        BigDecimal.valueOf(10.5))
        );

        Mockito.when(candleService.getCandles(eq("BTCUSDT"), eq("1m"), any(), any(), anyInt()))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/market/candles")
                .param("symbol", "BTCUSDT")
                .param("timeframe", "1m")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol", is("BTCUSDT")))
                .andExpect(jsonPath("$[0].timeframe", is("1m")))
                .andExpect(jsonPath("$[0].open", is(70000.0)))
                .andExpect(jsonPath("$[0].high", is(70100.0)))
                .andExpect(jsonPath("$[0].low", is(69900.0)))
                .andExpect(jsonPath("$[0].close", is(70050.0)))
                .andExpect(jsonPath("$[0].volume", is(10.5)));
    }

    @Test
    void testQueryBtcUsdt5mCandles() throws Exception {
        List<CandleDTO> mockResult = List.of(
                new CandleDTO("BTCUSDT", "5m", Instant.parse("2026-08-24T10:00:00Z"),
                        BigDecimal.valueOf(70000.0), BigDecimal.valueOf(70100.0),
                        BigDecimal.valueOf(69900.0), BigDecimal.valueOf(70050.0),
                        BigDecimal.valueOf(10.5))
        );

        Mockito.when(candleService.getCandles(eq("BTCUSDT"), eq("5m"), any(), any(), anyInt()))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/market/candles")
                .param("symbol", "BTCUSDT")
                .param("timeframe", "5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].timeframe", is("5m")));
    }

    @Test
    void testTimestampRange() throws Exception {
        Instant from = Instant.parse("2026-08-24T10:00:00Z");
        Instant to = Instant.parse("2026-08-24T11:00:00Z");

        Mockito.when(candleService.getCandles(eq("BTCUSDT"), eq("1m"), eq(from), eq(to), anyInt()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/market/candles")
                .param("symbol", "BTCUSDT")
                .param("timeframe", "1m")
                .param("from", "2026-08-24T10:00:00Z")
                .param("to", "2026-08-24T11:00:00Z"))
                .andExpect(status().isOk());
    }

    @Test
    void testLimit() throws Exception {
        Mockito.when(candleService.getCandles(eq("BTCUSDT"), eq("1m"), any(), any(), eq(5)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/market/candles")
                .param("symbol", "BTCUSDT")
                .param("timeframe", "1m")
                .param("limit", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void testEmptyResult() throws Exception {
        Mockito.when(candleService.getCandles(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/market/candles")
                .param("symbol", "BTCUSDT")
                .param("timeframe", "1m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testInvalidTimeframe() throws Exception {
        Mockito.when(candleService.getCandles(eq("BTCUSDT"), eq("2m"), any(), any(), anyInt()))
                .thenThrow(new IllegalArgumentException("Invalid timeframe: must be one of 1m, 5m, 15m, 1h"));

        mockMvc.perform(get("/api/market/candles")
                .param("symbol", "BTCUSDT")
                .param("timeframe", "2m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid timeframe: must be one of 1m, 5m, 15m, 1h")));
    }

    @Test
    void testInvalidLimit() throws Exception {
        Mockito.when(candleService.getCandles(eq("BTCUSDT"), eq("1m"), any(), any(), eq(-10)))
                .thenThrow(new IllegalArgumentException("Limit must be positive and not exceed 1000"));

        mockMvc.perform(get("/api/market/candles")
                .param("symbol", "BTCUSDT")
                .param("timeframe", "1m")
                .param("limit", "-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Limit must be positive and not exceed 1000")));
    }

    @Test
    void testSortingByTimestamp() throws Exception {
        List<CandleDTO> mockResult = List.of(
                new CandleDTO("BTCUSDT", "1m", Instant.parse("2026-08-24T10:00:00Z"),
                        BigDecimal.valueOf(70000.0), BigDecimal.valueOf(70100.0),
                        BigDecimal.valueOf(69900.0), BigDecimal.valueOf(70050.0),
                        BigDecimal.valueOf(10.5)),
                new CandleDTO("BTCUSDT", "1m", Instant.parse("2026-08-24T10:01:00Z"),
                        BigDecimal.valueOf(70050.0), BigDecimal.valueOf(70150.0),
                        BigDecimal.valueOf(70000.0), BigDecimal.valueOf(70100.0),
                        BigDecimal.valueOf(8.2))
        );

        Mockito.when(candleService.getCandles(eq("BTCUSDT"), eq("1m"), any(), any(), anyInt()))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/market/candles")
                .param("symbol", "BTCUSDT")
                .param("timeframe", "1m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].timestamp", is("2026-08-24T10:00:00Z")))
                .andExpect(jsonPath("$[1].timestamp", is("2026-08-24T10:01:00Z")));
    }

    @Test
    void testVerifyApiResponse() throws Exception {
        List<CandleDTO> mockResult = List.of(
                new CandleDTO("BTCUSDT", "1m", Instant.parse("2026-08-24T10:00:00Z"),
                        BigDecimal.valueOf(70000.0), BigDecimal.valueOf(70100.0),
                        BigDecimal.valueOf(69900.0), BigDecimal.valueOf(70050.0),
                        BigDecimal.valueOf(10.5))
        );

        Mockito.when(candleService.getCandles(eq("BTCUSDT"), eq("1m"), any(), any(), anyInt()))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/market/candles")
                .param("symbol", "BTCUSDT")
                .param("timeframe", "1m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").exists())
                .andExpect(jsonPath("$[0].timeframe").exists())
                .andExpect(jsonPath("$[0].timestamp").exists())
                .andExpect(jsonPath("$[0].open").exists())
                .andExpect(jsonPath("$[0].high").exists())
                .andExpect(jsonPath("$[0].low").exists())
                .andExpect(jsonPath("$[0].close").exists())
                .andExpect(jsonPath("$[0].volume").exists())
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }
}
