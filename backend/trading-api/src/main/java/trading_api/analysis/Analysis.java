package trading_api.analysis;

import jakarta.persistence.*;
import trading_api.auth.User;
import java.time.Instant;

@Entity
@Table(name = "analyses", indexes = @Index(name = "idx_analyses_user", columnList = "user_id"))
public class Analysis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 32) private String symbol;
    @Column(nullable = false, length = 16) private String timeframe;
    @Lob @Column(name = "result_json") private String resultJson;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public Instant getCreatedAt() { return createdAt; }
}