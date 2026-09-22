package dev.agenttrace.search;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic SQL only (concept doc §5): all filters below are plain WHERE
 * clauses over columns already lifted out of attributes at ingest time. No
 * ranking model, no fuzzy matching — that keeps results reproducible and fast.
 */
@Repository
public class SpanRepository {

	private static final int MAX_LIMIT = 200;

	private final JdbcClient jdbc;
	private final JsonMapper mapper;

	public SpanRepository(JdbcClient jdbc, JsonMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	/**
	 * @param status  "ok" (0/1) | "error" (2) | null (any)
	 * @param tool    exact gen_ai.tool.name match, or null
	 * @param q       substring match against span name, or null
	 */
	public List<SpanRow> search(String status, String tool, String q, int limit, int offset) {
		int boundedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
		int boundedOffset = Math.max(offset, 0);
		return jdbc.sql("""
				SELECT trace_id, span_id, parent_span_id, name, service_name, tool_name,
				       start_time, duration_ms, status_code, status_message, error_type, raw_payload_id
				FROM spans
				WHERE (:status IS NULL OR (:status = 'error' AND status_code = 2)
				                       OR (:status = 'ok' AND status_code <> 2))
				  AND (:tool IS NULL OR tool_name = :tool)
				  AND (:q IS NULL OR name LIKE CONCAT('%', :q, '%'))
				ORDER BY start_time DESC
				LIMIT :limit OFFSET :offset
				""")
				.param("status", status).param("tool", tool).param("q", q)
				.param("limit", boundedLimit).param("offset", boundedOffset)
				.query(this::mapRow)
				.list();
	}

	public Optional<SpanDetail> findOne(String traceId, String spanId) {
		return jdbc.sql("""
				SELECT trace_id, span_id, parent_span_id, name, service_name, tool_name,
				       start_time, duration_ms, status_code, status_message, error_type,
				       raw_payload_id, attributes
				FROM spans
				WHERE trace_id = :traceId AND span_id = :spanId
				""")
				.param("traceId", traceId).param("spanId", spanId)
				.query((rs, rowNum) -> new SpanDetail(mapRow(rs, rowNum), mapper.readTree(rs.getString("attributes"))))
				.optional();
	}

	/** Distinct tool names actually seen, for the UI's filter dropdown. */
	public List<String> tools() {
		return jdbc.sql("SELECT DISTINCT tool_name FROM spans WHERE tool_name IS NOT NULL ORDER BY tool_name")
				.query(String.class).list();
	}

	private SpanRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new SpanRow(
				rs.getString("trace_id"), rs.getString("span_id"), rs.getString("parent_span_id"),
				rs.getString("name"), rs.getString("service_name"), rs.getString("tool_name"),
				rs.getTimestamp("start_time").toLocalDateTime(),
				(Long) rs.getObject("duration_ms"), rs.getInt("status_code"), rs.getString("status_message"),
				rs.getString("error_type"), rs.getLong("raw_payload_id"));
	}

}
