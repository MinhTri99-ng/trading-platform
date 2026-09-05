package trading_api.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class MarketDataWebSocketController {

    private final BinanceKlineProxyService binanceKlineProxyService;

    public MarketDataWebSocketController(BinanceKlineProxyService binanceKlineProxyService) {
        this.binanceKlineProxyService = binanceKlineProxyService;
    }

    @MessageMapping("/market/subscribe")
    @SendTo("/topic/market-status")
    public String subscribe(MarketStreamSubscriptionRequest request) {
        if (request == null) {
            return "invalid";
        }

        binanceKlineProxyService.subscribe(request.symbol(), request.interval());
        return "subscribed:" + request.symbol().toUpperCase() + ":" + request.interval();
    }
}
