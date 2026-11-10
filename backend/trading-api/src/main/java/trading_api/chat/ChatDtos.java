package trading_api.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class ChatDtos {
    private ChatDtos() {
    }

    public record ChatRequest(
            @NotEmpty @Valid List<ChatMessage> messages
    ) {
    }

    public record ChatMessage(
            @NotBlank String role,
            @NotBlank String content
    ) {
    }

    public record ChatResponse(String message) {
    }
}
