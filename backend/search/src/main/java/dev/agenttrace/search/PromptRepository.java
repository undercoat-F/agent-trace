package dev.agenttrace.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import dev.agenttrace.search.PromptDetail.EventSummary;
import dev.agenttrace.search.PromptDetail.JudgmentSummary;

/**
 * Deterministic SQL only (concept doc §5), same as SpanRepository. prompts is
 * a rebuildable projection of events (see EventStore in ingest), so nothing
 * here writes — this module never migrates or mutates that state.
 */
@Repository
public class PromptRepository {

	private static final int MAX_LIMIT = 200;
	private static final int PREVIEW_LENGTH = 80;

	private final JdbcClient jdbc;
	private final JsonMapper mapper;

	public PromptRepository(JdbcClient jdbc, JsonMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	/**
	 * @param q            substring match against the full prompt text, or null
	 * @param failuresOnly only prompts with at least one failed tool call
	 */
	public List<PromptRow> search(String q, boolean failuresOnly, int limit, int offset) {
		int boundedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
		int boundedOffset = Math.max(offset, 0);
		// LEFT() in SQL avoids transferring full prompt text for a list view; the "…"
		// marker is appended in Java, never embedded as a literal in SQL text — that
		// previously got corrupted by this connection's character encoding even though
		// utf8mb4 columns and bound parameters were never affected. See ingest/search's
		// application.properties (characterEncoding=UTF-8) for the underlying fix too.
		return jdbc.sql("""
				SELECT prompt_id, session_id,
				       LEFT(user_prompt, :previewLen) AS prompt_preview,
				       CHAR_LENGTH(user_prompt) > :previewLen AS truncated,
				       started_at, ended_at, tool_calls, failures
				FROM prompts
				WHERE (:q IS NULL OR user_prompt LIKE CONCAT('%', :q, '%'))
				  AND (:failuresOnly = FALSE OR failures > 0)
				ORDER BY started_at DESC
				LIMIT :limit OFFSET :offset
				""")
				.param("previewLen", PREVIEW_LENGTH).param("q", q).param("failuresOnly", failuresOnly)
				.param("limit", boundedLimit).param("offset", boundedOffset)
				.query(this::mapRow)
				.list();
	}

	public Optional<PromptDetail> findOne(String promptId) {
		Optional<PromptDetail> prompt = jdbc.sql("""
				SELECT prompt_id, session_id, user_prompt, assistant_response,
				       started_at, ended_at, tool_calls, failures
				FROM prompts
				WHERE prompt_id = :promptId
				""")
				.param("promptId", promptId)
				.query((rs, rowNum) -> new PromptDetail(
						rs.getString("prompt_id"), rs.getString("session_id"),
						rs.getString("user_prompt"), rs.getString("assistant_response"),
						rs.getTimestamp("started_at").toLocalDateTime(), rs.getTimestamp("ended_at").toLocalDateTime(),
						rs.getInt("tool_calls"), rs.getInt("failures"), List.of(), List.of()))
				.optional();
		return prompt.map(p -> new PromptDetail(
				p.promptId(), p.sessionId(), p.userPrompt(), p.assistantResponse(),
				p.startedAt(), p.endedAt(), p.toolCalls(), p.failures(), events(promptId), judgments(promptId)));
	}

	private List<EventSummary> events(String promptId) {
		return jdbc.sql("""
				SELECT event_id, event_sequence, event_name, occurred_at, tool_name, tool_success
				FROM events
				WHERE prompt_id = :promptId
				ORDER BY event_sequence
				""")
				.param("promptId", promptId)
				.query((rs, rowNum) -> new EventSummary(
						rs.getString("event_id"), rs.getInt("event_sequence"), rs.getString("event_name"),
						rs.getTimestamp("occurred_at").toLocalDateTime(), rs.getString("tool_name"),
						rs.getString("tool_success")))
				.list();
	}

	private List<JudgmentSummary> judgments(String promptId) {
		return jdbc.sql("""
				SELECT question_id, question_version, value, confidence, model_version, raw_answer
				FROM judgments
				WHERE prompt_id = :promptId
				ORDER BY question_id
				""")
				.param("promptId", promptId)
				.query((rs, rowNum) -> new JudgmentSummary(
						rs.getString("question_id"), rs.getInt("question_version"),
						rs.getDouble("value"), rs.getDouble("confidence"), rs.getString("model_version"),
						mapper.readTree(rs.getString("raw_answer"))))
				.list();
	}

	private PromptRow mapRow(ResultSet rs, int rowNum) throws SQLException {
		String preview = rs.getString("prompt_preview");
		if (rs.getBoolean("truncated")) {
			preview = preview + "…";
		}
		return new PromptRow(
				rs.getString("prompt_id"), rs.getString("session_id"), preview,
				rs.getTimestamp("started_at").toLocalDateTime(), rs.getTimestamp("ended_at").toLocalDateTime(),
				rs.getInt("tool_calls"), rs.getInt("failures"));
	}

}
