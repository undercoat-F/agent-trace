package dev.agenttrace.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.agenttrace.ingest.LogMapper.EventRow;

/**
 * Fixture is a real, redacted capture of Claude Code's own OTel log output
 * (CLAUDE_CODE_ENABLE_TELEMETRY=1 + OTEL_LOG_USER_PROMPTS/ASSISTANT_RESPONSES/
 * TOOL_DETAILS=1), one "claude -p" run: 10 LogRecords across 2 export
 * batches. Real data, not hand-written, because the docs and the actual
 * output disagree on some field names (event.name has no "claude_code."
 * prefix even though body does; tool_decision's field is "source", not
 * "decision_source"; etc.) — this test pins the parser to what Claude Code
 * actually sends, not to what the docs say it sends.
 */
class LogMapperTest {

	private final JsonMapper mapper = JsonMapper.builder().build();
	private final LogMapper logMapper = new LogMapper(mapper);

	private List<EventRow> mapFixtureLine(int lineIndex) throws IOException {
		Path fixture = Path.of("src/test/resources/claude-code-real-sample.jsonl");
		String line = Files.readAllLines(fixture).get(lineIndex);
		return logMapper.map(mapper.readTree(line));
	}

	@Test
	void mapsAllRecordsAcrossBothBatches() throws IOException {
		assertThat(mapFixtureLine(0)).hasSize(7);
		assertThat(mapFixtureLine(1)).hasSize(3);
	}

	@Test
	void bundlesEventsOfOneTurnByPromptIdNotTraceOrSpanId() throws IOException {
		List<EventRow> events = mapFixtureLine(0);

		// managed_settings_resolved and plugin_loaded precede any prompt: no prompt.id yet.
		assertThat(events.get(0).eventName()).isEqualTo("managed_settings_resolved");
		assertThat(events.get(0).promptId()).isNull();
		assertThat(events.get(1).eventName()).isEqualTo("plugin_loaded");
		assertThat(events.get(1).promptId()).isNull();

		// From user_prompt onward, all events of this turn share one prompt.id.
		String promptId = events.get(2).promptId();
		assertThat(promptId).isEqualTo("e1e0991c-ea4e-4f4b-908c-42bfc758100b");
		for (EventRow e : events.subList(2, events.size())) {
			assertThat(e.promptId()).isEqualTo(promptId);
		}
	}

	@Test
	void eventIdIsSessionIdAndSequenceNotARandomId() throws IOException {
		EventRow first = mapFixtureLine(0).get(0);
		assertThat(first.sessionId()).isEqualTo("39cbab09-b679-4960-a6d6-9c3e8954ef2e");
		assertThat(first.eventSequence()).isZero();
		assertThat(first.eventId()).isEqualTo("39cbab09-b679-4960-a6d6-9c3e8954ef2e:0");
	}

	@Test
	void toolDecisionAndToolResultCarryToolNameAndOutcome() throws IOException {
		List<EventRow> events = mapFixtureLine(0);
		EventRow decision = findByName(events, "tool_decision");
		EventRow result = findByName(events, "tool_result");

		JsonNode decisionAttrs = attrs(decision);
		assertThat(decisionAttrs.path("tool_name").asString()).isEqualTo("Bash");
		assertThat(decisionAttrs.path("decision").asString()).isEqualTo("accept");
		assertThat(decisionAttrs.path("source").asString()).isEqualTo("config"); // NOT "decision_source"

		JsonNode resultAttrs = attrs(result);
		assertThat(resultAttrs.path("tool_name").asString()).isEqualTo("Bash");
		assertThat(resultAttrs.path("success").asString()).isEqualTo("true"); // string, not JSON boolean
		assertThat(resultAttrs.path("duration_ms").asString()).isEqualTo("1252");
		assertThat(decision.eventSequence()).isEqualTo(result.eventSequence() - 2); // api_request sits between them
	}

	@Test
	void userPromptAndAssistantResponseCarryTheirText() throws IOException {
		JsonNode promptAttrs = attrs(findByName(mapFixtureLine(0), "user_prompt"));
		assertThat(promptAttrs.path("prompt").asString())
				.isEqualTo("List the files in the current directory using ls, then reply with just: done");

		// assistant_response lands in the second export batch (line 1), not the first.
		JsonNode responseAttrs = attrs(findByName(mapFixtureLine(1), "assistant_response"));
		assertThat(responseAttrs.path("response").asString()).isEqualTo("done");
	}

	@Test
	void occurredAtComesFromTheRecordLevelTimestampInUtc() throws IOException {
		EventRow first = mapFixtureLine(0).get(0);
		// timeUnixNano = 1790064714409000000 -> 2026-09-22T08:11:54.409 UTC
		assertThat(first.occurredAt()).isEqualTo(LocalDateTime.of(2026, 9, 22, 8, 11, 54, 409_000_000));
	}

	@Test
	void recordsWithoutSessionIdOrSequenceAreSkippedNotCrashed() {
		String json = """
				{"resourceLogs":[{"scopeLogs":[{"logRecords":[
				  {"timeUnixNano":"1000000000","body":{"stringValue":"claude_code.user_prompt"},"attributes":[]}
				]}]}]}
				""";
		assertThat(logMapper.map(mapper.readTree(json))).isEmpty();
	}

	@Test
	void fallsBackToBodyWhenEventNameAttributeIsMissing() {
		String json = """
				{"resourceLogs":[{"scopeLogs":[{"logRecords":[
				  {"timeUnixNano":"1000000000",
				   "body":{"stringValue":"claude_code.some_new_event_type"},
				   "attributes":[
				     {"key":"session.id","value":{"stringValue":"s1"}},
				     {"key":"event.sequence","value":{"intValue":"0"}}
				   ]}
				]}]}]}
				""";
		List<EventRow> events = logMapper.map(mapper.readTree(json));
		assertThat(events).hasSize(1);
		assertThat(events.get(0).eventName()).isEqualTo("some_new_event_type");
	}

	private static EventRow findByName(List<EventRow> events, String name) {
		return events.stream().filter(e -> e.eventName().equals(name)).findFirst()
				.orElseThrow(() -> new AssertionError("no event named " + name));
	}

	private JsonNode attrs(EventRow row) {
		return mapper.readTree(row.attributesJson());
	}

}
