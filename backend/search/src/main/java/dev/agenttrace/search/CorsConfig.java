package dev.agenttrace.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * search and the UI are separate origins in dev (Vite on 5173, this API on
 * 8082). Scoped to the configured origin only — never a wildcard, since the
 * DB this reads from holds full prompt/response/tool-argument content.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private final String uiOrigin;

	public CorsConfig(@Value("${ui.origin:http://localhost:5173}") String uiOrigin) {
		this.uiOrigin = uiOrigin;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**").allowedOrigins(uiOrigin).allowedMethods("GET");
	}

}
