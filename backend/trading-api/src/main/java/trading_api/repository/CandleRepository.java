package trading_api.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import trading_api.entity.Candle;

import java.time.Instant;
import java.util.List;

public interface CandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findBySymbolOrderByTimestampAsc(String symbol);

    List<Candle> findByTimeframeOrderByTimestampAsc(String timeframe);
    List<Candle> findByTimeframe(String timeframe);
    List<Candle> findBySymbolAndTimeframeOrderByTimestampAsc(
            String symbol,
            String timeframe
    );

    boolean existsBySymbolAndTimeframeAndTimestamp(
            String symbol,
            String timeframe,
            Instant timestamp
    );

    List<Candle> findBySymbolAndTimeframeAndTimestampBetween(
            String symbol,
            String timeframe,
            Instant from,
            Instant to
    );

    @Query("SELECT c FROM Candle c WHERE c.symbol = :symbol AND c.timeframe = :timeframe " +
           "AND (:from IS NULL OR c.timestamp >= :from) " +
           "AND (:to IS NULL OR c.timestamp <= :to) " +
           "ORDER BY c.timestamp ASC")
    List<Candle> findCandles(
            @Param("symbol") String symbol,
            @Param("timeframe") String timeframe,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
    @Query("""
        SELECT c
        FROM Candle c
        WHERE c.symbol = :symbol
        AND c.timeframe = :timeframe
        ORDER BY c.timestamp DESC
    """)
    List<Candle> findLatestCandles(
            @Param("symbol") String symbol,
            @Param("timeframe") String timeframe
    );
}
