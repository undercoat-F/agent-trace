package dev.agenttrace.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Stores a request body into {@code raw_payloads}, keyed by its SHA-256, so
 * re-delivery (collector restart, retry, several ingest instances) is a
 * no-op. Shared by PayloadStore (traces) and EventStore (logs): this part —
 * find-then-insert with a locking re-read on the concurrent-insert race — is
 * the tricky bit, worth not duplicating.
 */
@Repository
public class RawPayloadStore {

	/** @param duplicate true when this exact payload had been stored before */
	public record Result(long id, boolean duplicate) {
	}

	private final JdbcClient jdbc;

	public RawPayloadStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Caller is expected to run this inside its own @Transactional. */
	public Result store(String signal, String body) {
		String sha = sha256(body);
		// Look first instead of relying on "affected rows": Connector/J reports matched
		// rows for ON DUPLICATE KEY, which would make every repeat look like an insert.
		Optional<Long> existing = findId(sha);
		if (existing.isPresent()) {
			return new Result(existing.get(), true);
		}
		try {
			GeneratedKeyHolder keys = new GeneratedKeyHolder();
			jdbc.sql("""
					INSERT INTO raw_payloads (signal_type, body_sha256, body)
					VALUES (:signal, :sha, CAST(:body AS JSON))
					""")
					.param("signal", signal).param("sha", sha).param("body", body)
					.update(keys);
			return new Result(keys.getKey().longValue(), false);
		}
		catch (DuplicateKeyException race) {
			// another ingest instance stored the same payload between our look and insert.
			// Plain SELECT would miss the other instance's row: REPEATABLE READ keeps
			// showing our first snapshot. A locking read sees the latest committed row.
			Long id = jdbc.sql("SELECT id FROM raw_payloads WHERE body_sha256 = :sha FOR SHARE")
					.param("sha", sha).query(Long.class).optional().orElseThrow(() -> race);
			return new Result(id, true);
		}
	}

	private Optional<Long> findId(String sha) {
		return jdbc.sql("SELECT id FROM raw_payloads WHERE body_sha256 = :sha")
				.param("sha", sha).query(Long.class).optional();
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
