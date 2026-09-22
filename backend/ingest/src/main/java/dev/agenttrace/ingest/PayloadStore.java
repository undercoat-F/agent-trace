package dev.agenttrace.ingest;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.agenttrace.ingest.SpanMapper.SpanRow;

/**
 * Idempotent writes for traces: the raw payload is deduped by RawPayloadStore,
 * spans by (trace_id, span_id).
 */
@Repository
public class PayloadStore {

	/** @param duplicate true when this exact payload had been stored before */
	public record Result(long rawPayloadId, boolean duplicate, int spans) {
	}

	private final JdbcClient jdbc;
	private final RawPayloadStore rawStore;

	public PayloadStore(JdbcClient jdbc, RawPayloadStore rawStore) {
		this.jdbc = jdbc;
		this.rawStore = rawStore;
	}

	@Transactional
	public Result store(String signal, String body, List<SpanRow> spans) {
		RawPayloadStore.Result raw = rawStore.store(signal, body);
		if (raw.duplicate()) {
			return new Result(raw.id(), true, 0); // spans were written with the first copy
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
					.param("rawId", raw.id())
					.update();
		}
		return new Result(raw.id(), false, spans.size());
	}

}
