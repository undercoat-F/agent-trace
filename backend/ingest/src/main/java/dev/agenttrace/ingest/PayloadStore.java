package dev.agenttrace.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.agenttrace.ingest.SpanMapper.SpanRow;

/**
 * Idempotent writes. The same request body may arrive any number of times
 * (collector restart, retry, several ingest instances): the raw payload is
 * keyed by its SHA-256 and spans by (trace_id, span_id), so repeats are no-ops.
 */
@Repository
public class PayloadStore {

	/** @param duplicate true when this exact payload had been stored before */
	public record Result(long rawPayloadId, boolean duplicate, int spans) {
	}

	private final JdbcClient jdbc;

	public PayloadStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public Result store(String signal, String body, List<SpanRow> spans) {
		String sha = sha256(body);
		// Look first instead of relying on "affected rows": Connector/J reports matched
		// rows for ON DUPLICATE KEY, which would make every repeat look like an insert.
		Optional<Long> existing = findRawId(sha);
		if (existing.isPresent()) {
			return new Result(existing.get(), true, 0); // spans were written with the first copy
		}
		long rawId;
		try {
			GeneratedKeyHolder keys = new GeneratedKeyHolder();
			jdbc.sql("""
					INSERT INTO raw_payloads (signal_type, body_sha256, body)
					VALUES (:signal, :sha, CAST(:body AS JSON))
					""")
					.param("signal", signal).param("sha", sha).param("body", body)
					.update(keys);
			rawId = keys.getKey().longValue();
		}
		catch (DuplicateKeyException race) {
			// another ingest instance stored the same payload between our look and insert
			// Plain SELECT would miss the other instance's row: REPEATABLE READ keeps
			// showing our first snapshot. A locking read sees the latest committed row.
			Long id = jdbc.sql("SELECT id FROM raw_payloads WHERE body_sha256 = :sha FOR SHARE")
					.param("sha", sha).query(Long.class).optional().orElseThrow(() -> race);
			return new Result(id, true, 0);
		}
		for (SpanRow s : spans) {
			jdbc.sql("""
					INSERT INTO spans (trace_id, span_id, parent_span_id, name, kind, service_name,
					                   start_time, end_time, duration_ms, status_code, status_message,
					                   attributes, raw_payload_id)
					VALUES (:traceId, :spanId, :parentSpanId, :name, :kind, :serviceName,
					        :startTime, :endTime, :durationMs, :statusCode, :statusMessage,
					        CAST(:attributes AS JSON), :rawId)
					ON DUPLICATE KEY UPDATE span_id = span_id
					""")
					.param("traceId", s.traceId()).param("spanId", s.spanId())
					.param("parentSpanId", s.parentSpanId()).param("name", s.name())
					.param("kind", s.kind()).param("serviceName", s.serviceName())
					.param("startTime", s.startTime()).param("endTime", s.endTime())
					.param("durationMs", s.durationMs()).param("statusCode", s.statusCode())
					.param("statusMessage", s.statusMessage()).param("attributes", s.attributesJson())
					.param("rawId", rawId)
					.update();
		}
		return new Result(rawId, false, spans.size());
	}

	private Optional<Long> findRawId(String sha) {
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
