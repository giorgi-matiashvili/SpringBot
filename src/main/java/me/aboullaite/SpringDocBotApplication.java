package me.aboullaite;

import com.github.messenger4j.Messenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringDocBotApplication {

	private static final Logger logger = LoggerFactory.getLogger(SpringDocBotApplication.class);

	/**
	 * Initializes the {@code MessengerSendClient}.
	 *
	 * @param pageAccessToken the generated {@code Page Access Token}
	 */
	@Bean
	public Messenger messengerSendClient(@Value("${messenger4j.pageAccessToken}") String pageAccessToken, @Value("${messenger4j.appSecret}") String appSecret,
										 @Value("${messenger4j.verifyToken}") String verifyToken) {
		logger.debug("Initializing MessengerSendClient - pageAccessToken: {}", pageAccessToken);
		return Messenger.create(pageAccessToken, appSecret, verifyToken);
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringDocBotApplication.class, args);
	}
}
