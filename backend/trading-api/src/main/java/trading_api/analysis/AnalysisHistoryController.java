package trading_api.analysis;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import trading_api.auth.CurrentUser;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisHistoryController {
    private final AnalysisHistoryService historyService;
    public AnalysisHistoryController(AnalysisHistoryService historyService) { this.historyService = historyService; }
    @GetMapping("/my-history")
    public List<AnalysisDtos.AnalysisResponse> history() { return historyService.history(CurrentUser.required()); }
    @GetMapping("/{id}")
    public AnalysisDtos.AnalysisResponse detail(@PathVariable Long id) { return historyService.detail(id, CurrentUser.required()); }
}