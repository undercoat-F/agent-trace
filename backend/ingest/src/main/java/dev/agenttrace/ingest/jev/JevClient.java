package dev.agenttrace.ingest.jev;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import dev.agenttrace.ingest.jev.Answer.ChoiceAnswer;
import dev.agenttrace.ingest.jev.Answer.NoulAnswer;
import dev.agenttrace.ingest.jev.Answer.ScoreAnswer;
import dev.agenttrace.ingest.jev.Question.Choice;
import dev.agenttrace.ingest.jev.Question.Noul;
import dev.agenttrace.ingest.jev.Question.Score;

/**
 * Self-written client for the Jev API (docs.typesafe.ai/api), since TypeSafe
 * only publishes official SDKs for Python/JavaScript, not Java — and the
 * community Java SDKs found couldn't be trusted without more vetting (several
 * near-duplicate repos, one still 0.1.0-SNAPSHOT). The wire protocol is a
 * single POST with a small, well-documented JSON shape, so a first-party
 * client is a few dozen lines and keeps the state we send (prompts, tool
 * output — potentially sensitive) inside code we've actually read.
 *
 * judge(state, questions) matches concept doc §10's judge(state, question)
 * -> {value, confidence, model_version}, batched: Jev evaluates every
 * question in one request against the same state (concept doc §8).
 *
 * NEVER log the API key or the Authorization header anywhere in this class.
 */
@Component
public class JevClient {

	private static final Logger log = LoggerFactory.getLogger(JevClient.class);

	public record JudgeResult(String modelVersion, Map<String, Answer> answers, long inputTokens, long outputTokens) {
	}

	private final RestClient restClient;
	private final JsonMapper mapper;
	private final String model;
	private final boolean available;

	public JevClient(
			RestClient.Builder restClientBuilder,
			JsonMapper mapper,
			@Value("${jev.base-url}") String baseUrl,
			@Value("${jev.api-key:}") String apiKey,
			@Value("${jev.model}") String model) {
		this.mapper = mapper;
		this.model = model;
		this.available = apiKey != null && !apiKey.isBlank();
		this.restClient = restClientBuilder
				.baseUrl(baseUrl)
				.defaultHeader("Authorization", "Bearer " + apiKey)
				.build();
		if (!available) {
			log.warn("jev.api-key is not set; judge() will throw JevException until it is configured");
		}
	}

	public boolean isAvailable() {
		return available;
	}

	/**
	 * @param state     the text/object to judge (a prompt's turn, a span, ...) — sent as-is, so
	 *                  never pass anything the configured Jev endpoint shouldn't see
	 * @param questions question id -> typed question, all evaluated against the same state in one call
	 */
	public JudgeResult judge(Object state, Map<String, Question> questions) {
		if (!available) {
			throw new JevException("jev.api-key is not configured");
		}
		ObjectNode request = mapper.createObjectNode();
		request.set("state", mapper.valueToTree(state));
		request.put("model", model);
		ObjectNode questionsNode = request.putObject("questions");
		questions.forEach((id, q) -> questionsNode.set(id, questionToJson(q)));

		JsonNode response;
		try {
			String body = restClient.post()
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.body(mapper.writeValueAsString(request))
					.retrieve()
					.onStatus(HttpStatusCode::isError, (req, res) -> {
						// Response body only — never the request, which carries the Authorization header.
						String errorBody = new String(res.getBody().readAllBytes());
						throw new JevException("Jev API returned " + res.getStatusCode() + ": " + errorBody);
					})
					.body(String.class);
			response = mapper.readTree(body);
		}
		catch (JevException e) {
			throw e;
		}
		catch (Exception e) {
			throw new JevException("Jev API call failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
		}

		Map<String, Answer> answers = new LinkedHashMap<>();
		response.path("answers").properties().forEach(entry -> answers.put(entry.getKey(), answerFromJson(entry.getValue())));
		JsonNode usage = response.path("usage");
		return new JudgeResult(
				response.path("model").asString(model),
				answers,
				usage.path("input_tokens").asLong(0),
				usage.path("output_tokens").asLong(0));
	}

	ObjectNode questionToJson(Question q) {
		ObjectNode node = mapper.createObjectNode();
		node.put("type", q.type());
		switch (q) {
			case Noul n -> {
				node.put("instructions", n.instructions());
				if (n.criteria() != null) {
					node.set("criteria", mapper.valueToTree(n.criteria()));
				}
			}
			case Choice c -> {
				node.put("instructions", c.instructions());
				node.set("criteria", mapper.valueToTree(c.criteria()));
			}
			case Score s -> {
				node.put("instructions", s.instructions());
				node.set("criteria", mapper.valueToTree(s.criteria()));
			}
		}
		return node;
	}

	Answer answerFromJson(JsonNode a) {
		String type = a.path("type").asString("");
		return switch (type) {
			case "noul" -> new NoulAnswer(a.path("noul").asDouble());
			case "choice" -> new ChoiceAnswer(
					a.path("choice").asString(),
					toDoubleMap(a.path("probabilities")),
					a.path("confidence").asDouble());
			case "score" -> new ScoreAnswer(
					a.path("score").asDouble(),
					toStringMap(a.path("legend")),
					toDoubleMap(a.path("probabilities")),
					a.path("confidence").asDouble());
			default -> throw new JevException("Unknown Jev answer type: " + type);
		};
	}

	private static Map<String, Double> toDoubleMap(JsonNode obj) {
		Map<String, Double> out = new LinkedHashMap<>();
		obj.properties().forEach(e -> out.put(e.getKey(), e.getValue().asDouble()));
		return out;
	}

	private static Map<String, String> toStringMap(JsonNode obj) {
		Map<String, String> out = new LinkedHashMap<>();
		obj.properties().forEach(e -> out.put(e.getKey(), e.getValue().asString()));
		return out;
	}

}
