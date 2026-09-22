package dev.agenttrace.ingest.jev;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import dev.agenttrace.ingest.jev.PromptState.ToolCallSummary;

/**
 * concept doc principle 2: Jev runs at ingest time, not at search time.
 * Triggered from EventStore whenever a batch contains an assistant_response
 * event — the best available proxy for "this turn is basically done" (see
 * EventStore for the exact trigger). Never lets a Jev failure break the
 * actual telemetry ingestion it's triggered from: @Async, and every
 * exception is caught and logged here, not propagated.
 */
@Service
public class PromptJudge {

	private static final Logger log = LoggerFactory.getLogger(PromptJudge.class);

	private final JdbcClient jdbc;
	private final JevClient jev;
	private final JsonMapper mapper;

	public PromptJudge(JdbcClient jdbc, JevClient jev, JsonMapper mapper) {
		this.jdbc = jdbc;
		this.jev = jev;
		this.mapper = mapper;
	}

	@Async
	public void judgeAsync(String promptId) {
		if (!jev.isAvailable()) {
			return;
		}
		try {
			judge(promptId);
		}
		catch (Exception e) {
			log.warn("Judging prompt {} failed (ingestion is unaffected): {}", promptId, e.toString());
		}
	}

	@Transactional
	void judge(String promptId) {
		PromptState state = loadState(promptId);
		if (state == null) {
			return; // prompt row not there yet (race with the reaggregate that triggered this); skip, no retry for v1
		}
		JevClient.JudgeResult result = jev.judge(state, QuestionCatalog.ALL);
		result.answers().forEach((questionId, answer) -> store(promptId, questionId, answer, result.modelVersion()));
		log.info("Judged prompt {} ({} question(s)) with {}", promptId, result.answers().size(), result.modelVersion());
	}

	private PromptState loadState(String promptId) {
		var prompt = jdbc.sql("SELECT user_prompt, assistant_response, tool_calls, failures FROM prompts WHERE prompt_id = :id")
				.param("id", promptId)
				.query((rs, rowNum) -> new Object[] {
						rs.getString("user_prompt"), rs.getString("assistant_response"),
						rs.getInt("tool_calls"), rs.getInt("failures") })
				.optional();
		if (prompt.isEmpty()) {
			return null;
		}
		Object[] row = prompt.get();
		List<ToolCallSummary> tools = jdbc.sql("""
				SELECT tool_name, tool_success FROM events
				WHERE prompt_id = :id AND event_name = 'tool_result'
				ORDER BY event_sequence
				""")
				.param("id", promptId)
				.query((rs, rowNum) -> new ToolCallSummary(rs.getString("tool_name"), "true".equals(rs.getString("tool_success"))))
				.list();
		return new PromptState((String) row[0], (String) row[1], (int) row[2], (int) row[3], tools);
	}

	private void store(String promptId, String questionId, Answer answer, String modelVersion) {
		jdbc.sql("""
				INSERT INTO judgments (prompt_id, question_id, question_version, value, confidence,
				                       model_version, raw_answer)
				VALUES (:promptId, :questionId, :version, :value, :confidence, :modelVersion, CAST(:raw AS JSON))
				ON DUPLICATE KEY UPDATE
				  value = VALUES(value), confidence = VALUES(confidence),
				  model_version = VALUES(model_version), raw_answer = VALUES(raw_answer),
				  judged_at = CURRENT_TIMESTAMP(6)
				""")
				.param("promptId", promptId).param("questionId", questionId).param("version", QuestionCatalog.VERSION)
				.param("value", answer.value()).param("confidence", answer.confidence())
				.param("modelVersion", modelVersion).param("raw", mapper.writeValueAsString(answer))
				.update();
	}

}
