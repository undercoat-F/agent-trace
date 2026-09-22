package dev.agenttrace.ingest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns an OTLP/JSON log request ({@code resourceLogs[].scopeLogs[].logRecords[]})
 * into flat rows. Claude Code's own telemetry is log-shaped, not span-shaped
 * (see docs/agent-trace-concept.md §12-1): traceId/spanId are always absent on
 * these records in real captures (backend/ingest/src/test/resources/
 * claude-code-real-sample.jsonl), and the turn-bundling key is the
 * "prompt.id" attribute instead. Every record also carries "session.id" and
 * "event.sequence" (a per-session, not per-prompt, monotonic counter) —
 * (session_id, event_sequence) is used as this event's identity since it is
 * content-stable across redelivery, unlike a freshly generated random id.
 */
@Component
public class LogMapper {

	public record EventRow(String eventId, String sessionId, int eventSequence, String promptId, String eventName,
			LocalDateTime occurredAt, String attributesJson) {
	}

	private final JsonMapper mapper;

	public LogMapper(JsonMapper mapper) {
		this.mapper = mapper;
	}

	public List<EventRow> map(JsonNode root) {
		List<EventRow> rows = new ArrayList<>();
		for (JsonNode resourceLogs : root.path("resourceLogs")) {
			for (JsonNode scopeLogs : resourceLogs.path("scopeLogs")) {
				for (JsonNode record : scopeLogs.path("logRecords")) {
					EventRow row = mapRecord(record);
					if (row != null) {
						rows.add(row);
					}
				}
			}
		}
		return rows;
	}

	private EventRow mapRecord(JsonNode record) {
		var attrs = OtlpAttributes.flatten(record.path("attributes"));
		String sessionId = asString(attrs.get("session.id"));
		Object sequence = attrs.get("event.sequence");
		if (sessionId == null || !(sequence instanceof Number seq)) {
			return null; // cannot be keyed; the raw payload still holds it
		}
		Long timeNanos = OtlpAttributes.nanos(record.path("timeUnixNano"));
		if (timeNanos == null) {
			return null;
		}
		String eventName = asString(attrs.get("event.name"));
		if (eventName == null) {
			// Fall back to body, e.g. "claude_code.user_prompt" -> "user_prompt".
			String body = OtlpAttributes.text(record.path("body").path("stringValue"));
			eventName = body == null ? "unknown" : body.replaceFirst("^claude_code\\.", "");
		}
		return new EventRow(
				sessionId + ":" + seq.intValue(),
				sessionId,
				seq.intValue(),
				asString(attrs.get("prompt.id")),
				eventName,
				OtlpAttributes.utc(timeNanos),
				mapper.writeValueAsString(attrs));
	}

	private static String asString(Object v) {
		return v instanceof String s ? s : null;
	}

}
