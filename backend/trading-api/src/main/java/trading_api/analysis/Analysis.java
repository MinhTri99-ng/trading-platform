package trading_api.analysis;

import jakarta.persistence.*;
import trading_api.auth.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    @Column(length = 32) private String market;
    @Column(length = 128) private String pattern;
    private Double confidence;
    @Lob @Column(name = "analysis_text") private String analysisText;
    @Column(name = "image_url", length = 2048) private String imageUrl;
    @Lob @Column(name = "result_json") private String resultJson;
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TradeSetup> tradeSetups = new ArrayList<>();
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getAnalysisText() { return analysisText; }
    public void setAnalysisText(String analysisText) { this.analysisText = analysisText; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public List<TradeSetup> getTradeSetups() { return tradeSetups; }
    public void addTradeSetup(TradeSetup tradeSetup) {
        tradeSetups.add(tradeSetup);
        tradeSetup.setAnalysis(this);
    }
    public Instant getCreatedAt() { return createdAt; }
}