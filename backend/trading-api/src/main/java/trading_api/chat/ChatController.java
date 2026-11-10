package trading_api.chat;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trading_api.vision.GeminiVisionProvider;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final String SYSTEM_INSTRUCTION = """
            Bạn là TradeAI Assistant - Chuyên gia phân tích kỹ thuật Crypto và phương pháp SMC (Smart Money Concepts).
            Hãy trả lời chi tiết, chuyên nghiệp, rõ ràng bằng tiếng Việt.
            Sử dụng định dạng Markdown (bôi đen, gạch đầu dòng) để câu trả lời dễ đọc.
            Nếu người dùng hỏi về xu hướng hoặc giá cả hiện tại, hãy dựa trên kiến thức phân tích kỹ thuật SMC
            (như Liquidity, Order Block, FVG, Break of Structure) để đưa ra kịch bản phân tích mẫu chuẩn xác
            chứ không từ chối trả lời.
            """;
    private static final Set<String> ALLOWED_ROLES = Set.of("user", "model");

    private final GeminiVisionProvider geminiProvider;

    public ChatController(GeminiVisionProvider geminiProvider) {
        this.geminiProvider = geminiProvider;
    }

    @PostMapping("/completions")
    public ResponseEntity<?> complete(@Valid @RequestBody ChatDtos.ChatRequest request) {
        if (request.messages().stream().anyMatch(message ->
                !ALLOWED_ROLES.contains(message.role().trim().toLowerCase(Locale.ROOT)))) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "message", "Chat message role must be user or model."
            ));
        }

        String prompt = request.messages().stream()
                .map(message -> message.role().trim().toLowerCase(Locale.ROOT) + ": " + message.content().trim())
                .collect(Collectors.joining("\n\n"));

        try {
            return ResponseEntity.ok(new ChatDtos.ChatResponse(
                    geminiProvider.generateText(SYSTEM_INSTRUCTION, prompt)
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(java.util.Map.of(
                    "message", "Hệ thống AI đang bảo trì hoặc đạt giới hạn lưu lượng, vui lòng thử lại sau ít phút."
            ));
        }
    }
}
