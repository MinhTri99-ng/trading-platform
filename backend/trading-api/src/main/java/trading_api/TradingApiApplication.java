package trading_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import trading_api.market.TradingStrategyService;

@SpringBootApplication
public class TradingApiApplication {

	@Bean
	public TradingStrategyService tradingStrategyService() {
		return new TradingStrategyService();
	}

	public static void main(String[] args) {
		SpringApplication.run(TradingApiApplication.class, args);
	}

}
