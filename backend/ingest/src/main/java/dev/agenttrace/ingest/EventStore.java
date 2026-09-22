package dev.agenttrace.ingest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.agenttrace.ingest.LogMapper.EventRow;

/**
 * Idempotent writes for logs: the raw payload is deduped by RawPayloadStore,
 * events by (session_id, event_sequence). After inserting events, every
 * prompt_id touched by this batch has its `prompts` row recomputed from
 * `events` directly (not incrementally patched), so it stays correct
 * regardless of arrival order or redelivery — same spirit as spans' generated
 * columns: prompts is a rebuildable projection, never hand-maintained state.
 */
@Repository
public class EventStore {

	/**
	 * @param duplicate                    true when this exact payload had been stored before
	 * @param promptsReadyToJudge          prompt_ids whose assistant_response arrived in this batch — the
	 *                                     best available proxy for "this turn is basically done". Caller
	 *                                     triggers judging with these AFTER this method's transaction has
	 *                                     committed, since judging reads the prompts row this same call wrote.
	 */
	public record Result(long rawPayloadId, boolean duplicate, int events, Set<String> promptsReadyToJudge) {
	}

	private final JdbcClient jdbc;
	private final RawPayloadStore rawStore;

	public EventStore(JdbcClient jdbc, RawPayloadStore rawStore) {
		this.jdbc = jdbc;
		this.rawStore = rawStore;
	}

	@Transactional
	public Result store(String body, List<EventRow> events) {
		RawPayloadStore.Result raw = rawStore.store("logs", body);
		if (raw.duplicate()) {
			return new Result(raw.id(), true, 0, Set.of()); // events were written with the first copy
		}
		Set<String> promptIds = new LinkedHashSet<>();
		Set<String> readyToJudge = new LinkedHashSet<>();
		for (EventRow e : events) {
			jdbc.sql("""
					INSERT INTO events (event_id, session_id, event_sequence, prompt_id, event_name,
					                    occurred_at, attributes, raw_payload_id)
					VALUES (:eventId, :sessionId, :eventSequence, :promptId, :eventName,
					        :occurredAt, CAST(:attributes AS JSON), :rawId)
					ON DUPLICATE KEY UPDATE event_id = event_id
					""")
					.param("eventId", e.eventId()).param("sessionId", e.sessionId())
					.param("eventSequence", e.eventSequence()).param("promptId", e.promptId())
					.param("eventName", e.eventName()).param("occurredAt", e.occurredAt())
					.param("attributes", e.attributesJson()).param("rawId", raw.id())
					.update();
			if (e.promptId() != null) {
				promptIds.add(e.promptId());
				if ("assistant_response".equals(e.eventName())) {
					readyToJudge.add(e.promptId());
				}
			}
		}
		for (String promptId : promptIds) {
			reaggregatePrompt(promptId);
		}
		return new Result(raw.id(), false, events.size(), readyToJudge);
	}

	private void reaggregatePrompt(String promptId) {
		jdbc.sql("""
				INSERT INTO prompts (prompt_id, session_id, user_prompt, assistant_response,
				                     started_at, ended_at, tool_calls, failures)
				SELECT
				  :promptId,
				  MIN(session_id),
				  MAX(CASE WHEN event_name = 'user_prompt' THEN JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.prompt')) END),
				  MAX(CASE WHEN event_name = 'assistant_response' THEN JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.response')) END),
				  MIN(occurred_at),
				  MAX(occurred_at),
				  SUM(event_name = 'tool_result'),
				  SUM(event_name = 'tool_result' AND tool_success = 'false')
				FROM events
				WHERE prompt_id = :promptId
				ON DUPLICATE KEY UPDATE
				  session_id = VALUES(session_id),
				  user_prompt = VALUES(user_prompt),
				  assistant_response = VALUES(assistant_response),
				  started_at = VALUES(started_at),
				  ended_at = VALUES(ended_at),
				  tool_calls = VALUES(tool_calls),
				  failures = VALUES(failures)
				""")
				.param("promptId", promptId)
				.update();
	}

}
