package dev.agenttrace.ingest.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import dev.agenttrace.ingest.jev.Answer.ChoiceAnswer;
import dev.agenttrace.ingest.jev.Answer.NoulAnswer;
import dev.agenttrace.ingest.jev.Answer.ScoreAnswer;
import dev.agenttrace.ingest.jev.Question.Choice;
import dev.agenttrace.ingest.jev.Question.Noul;
import dev.agenttrace.ingest.jev.Question.Score;

/**
 * Request/response shapes here are pinned to docs.typesafe.ai/api and
 * docs.typesafe.ai/primitives (fetched 2026-09-22), not invented — no real
 * network call, no real API key needed for any of this.
 */
class JevClientTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	private JevClient client(String apiKey) {
		return new JevClient(RestClient.builder(), mapper, "https://api.typesafe.ai/v1/systemone", apiKey, "jev-latest");
	}

	@Test
	void unavailableWithoutAnApiKey() {
		JevClient c = client("");
		assertThat(c.isAvailable()).isFalse();
		assertThatThrownBy(() -> c.judge("x", Map.of())).isInstanceOf(JevException.class);
	}

	@Test
	void noulQuestionWithoutCriteria() {
		var json = client("k").questionToJson(new Noul("Is this urgent?"));
		assertThat(json.path("type").asString()).isEqualTo("noul");
		assertThat(json.path("instructions").asString()).isEqualTo("Is this urgent?");
		assertThat(json.has("criteria")).isFalse();
	}

	@Test
	void noulQuestionWithCriteria() {
		var json = client("k").questionToJson(new Noul("Refund?", Map.of("yes", "wants money back", "no", "does not")));
		assertThat(json.path("criteria").path("yes").asString()).isEqualTo("wants money back");
	}

	@Test
	void choiceQuestion() {
		var json = client("k").questionToJson(new Choice("Which team?", Map.of("billing", "money issues", "eng", "bugs")));
		assertThat(json.path("type").asString()).isEqualTo("choice");
		assertThat(json.path("criteria").path("billing").asString()).isEqualTo("money issues");
	}

	@Test
	void scoreQuestion() {
		var json = client("k").questionToJson(new Score("Severity?", List.of("trivial", "minor", "major", "critical")));
		assertThat(json.path("type").asString()).isEqualTo("score");
		assertThat(json.path("criteria").get(2).asString()).isEqualTo("major");
	}

	@Test
	void parsesNoulAnswer() {
		var a = client("k").answerFromJson(mapper.readTree("""
				{"type":"noul","noul":0.87}
				"""));
		assertThat(a).isInstanceOf(NoulAnswer.class);
		assertThat(a.value()).isEqualTo(0.87);
		assertThat(((NoulAnswer) a).confidence()).isCloseTo(0.74, org.assertj.core.data.Offset.offset(0.001));
	}

	@Test
	void parsesChoiceAnswer() {
		var a = client("k").answerFromJson(mapper.readTree("""
				{"type":"choice","choice":"billing","probabilities":{"billing":0.85,"eng":0.15},"confidence":0.7}
				"""));
		assertThat(a).isInstanceOf(ChoiceAnswer.class);
		assertThat(((ChoiceAnswer) a).choice()).isEqualTo("billing");
		assertThat(a.value()).isEqualTo(0.85); // probability of the chosen option
		assertThat(a.confidence()).isEqualTo(0.7);
	}

	@Test
	void parsesScoreAnswer() {
		var a = client("k").answerFromJson(mapper.readTree("""
				{"type":"score","score":1.5,"legend":{"0":"low","1":"mid","2":"high"},
				 "probabilities":{"0":0.25,"1":0.5,"2":0.25},"confidence":0.65}
				"""));
		assertThat(a).isInstanceOf(ScoreAnswer.class);
		assertThat(a.value()).isEqualTo(1.5);
		assertThat(((ScoreAnswer) a).legend()).containsEntry("1", "mid");
	}

	@Test
	void unknownAnswerTypeThrows() {
		var client = client("k");
		var node = mapper.readTree("""
				{"type":"mystery"}
				""");
		assertThatThrownBy(() -> client.answerFromJson(node)).isInstanceOf(JevException.class);
	}

	@Test
	void judgeSendsAuthHeaderAndParsesTheFullResponse() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer test-key-do-not-log"))
				.andRespond(withSuccess("""
						{"model":"jev-latest","answers":{"is_urgent":{"type":"noul","noul":0.95}},
						 "usage":{"input_tokens":42,"output_tokens":3}}
						""", MediaType.APPLICATION_JSON));

		JevClient client = new JevClient(builder, mapper, "https://api.typesafe.ai/v1/systemone", "test-key-do-not-log", "jev-latest");
		var result = client.judge("Help! My payouts have failed.", Map.of("is_urgent", new Noul("Is this urgent?")));

		assertThat(result.modelVersion()).isEqualTo("jev-latest");
		assertThat(result.inputTokens()).isEqualTo(42);
		assertThat(((NoulAnswer) result.answers().get("is_urgent")).noul()).isEqualTo(0.95);
		server.verify();
	}

	@Test
	void nonTwoxxStatusBecomesAJevExceptionWithTheResponseBodyNotTheRequest() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\":\"rate limited\"}"));

		JevClient client = new JevClient(builder, mapper, "https://api.typesafe.ai/v1/systemone", "k", "jev-latest");
		assertThatThrownBy(() -> client.judge("x", Map.of("q", new Noul("?"))))
				.isInstanceOf(JevException.class)
				.hasMessageContaining("429")
				.hasMessageContaining("rate limited");
	}

}
