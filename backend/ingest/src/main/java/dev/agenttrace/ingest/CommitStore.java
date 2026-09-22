package dev.agenttrace.ingest;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotent by sha (a commit is immutable once made, so a resend simply
 * overwrites with the same data — unlike spans/events, ON DUPLICATE KEY
 * UPDATE here actually replaces the row instead of being a no-op, since a
 * later resend of the same sha could carry a resolved prompt_id/session_id
 * where an earlier one had none).
 */
@Repository
public class CommitStore {

	public record CommitPayload(String sha, String branch, String message, List<String> files,
			String promptId, String sessionId, String committedAt) {

		/** committedAt is ISO-8601 with an offset (git %cI); falls back to now() if missing/unparsable. */
		LocalDateTime committedAtUtc() {
			if (committedAt == null || committedAt.isBlank()) {
				return LocalDateTime.now(ZoneOffset.UTC);
			}
			try {
				return OffsetDateTime.parse(committedAt).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
			}
			catch (java.time.format.DateTimeParseException e) {
				return LocalDateTime.now(ZoneOffset.UTC);
			}
		}
	}

	private final JdbcClient jdbc;
	private final RawPayloadStore rawStore;
	private final JsonMapper mapper;

	public CommitStore(JdbcClient jdbc, RawPayloadStore rawStore, JsonMapper mapper) {
		this.jdbc = jdbc;
		this.rawStore = rawStore;
		this.mapper = mapper;
	}

	@Transactional
	public void store(String body, CommitPayload commit) {
		RawPayloadStore.Result raw = rawStore.store("commits", body);
		String filesJson = mapper.writeValueAsString(commit.files() == null ? List.of() : commit.files());
		jdbc.sql("""
				INSERT INTO commits (sha, branch, message, prompt_id, session_id, files_changed,
				                     committed_at, raw_payload_id)
				VALUES (:sha, :branch, :message, :promptId, :sessionId, CAST(:files AS JSON), :committedAt, :rawId)
				ON DUPLICATE KEY UPDATE
				  branch = VALUES(branch), message = VALUES(message),
				  prompt_id = VALUES(prompt_id), session_id = VALUES(session_id),
				  files_changed = VALUES(files_changed), raw_payload_id = VALUES(raw_payload_id)
				""")
				.param("sha", commit.sha()).param("branch", commit.branch()).param("message", commit.message())
				.param("promptId", commit.promptId()).param("sessionId", commit.sessionId())
				.param("files", filesJson).param("committedAt", commit.committedAtUtc())
				.param("rawId", raw.id())
				.update();
	}

}
