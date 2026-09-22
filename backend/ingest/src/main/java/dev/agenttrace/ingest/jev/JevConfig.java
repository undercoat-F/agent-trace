package dev.agenttrace.ingest.jev;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient.Builder isn't auto-configured as a bean in this module's current
 * dependency set (spring-boot-starter-webmvc alone didn't bring it in,
 * confirmed by a real startup failure) — provided explicitly instead of
 * pulling in more starters just for this.
 */
@Configuration
public class JevConfig {

	@Bean
	public RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}

}
