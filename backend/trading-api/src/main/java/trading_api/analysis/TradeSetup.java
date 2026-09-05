package trading_api.analysis;

import jakarta.persistence.*;
import trading_api.auth.User;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_setups", indexes = {
        @Index(name = "idx_trade_setups_analysis", columnList = "analysis_id"),
        @Index(name = "idx_trade_setups_user", columnList = "user_id")
})
public class TradeSetup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_id", nullable = false) private Analysis analysis;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 8) private String type;
    @Column(name = "entry_price", precision = 30, scale = 10) private BigDecimal entryPrice;
    @Column(name = "stop_loss", precision = 30, scale = 10) private BigDecimal stopLoss;
    @Column(name = "take_profit", precision = 30, scale = 10) private BigDecimal takeProfit;
    @Column(nullable = false, length = 16) private String status;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public Analysis getAnalysis() { return analysis; }
    public void setAnalysis(Analysis analysis) { this.analysis = analysis; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }
    public BigDecimal getStopLoss() { return stopLoss; }
    public void setStopLoss(BigDecimal stopLoss) { this.stopLoss = stopLoss; }
    public BigDecimal getTakeProfit() { return takeProfit; }
    public void setTakeProfit(BigDecimal takeProfit) { this.takeProfit = takeProfit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}