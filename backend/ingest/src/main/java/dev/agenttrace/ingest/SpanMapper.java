package dev.agenttrace.ingest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns an OTLP/JSON trace request ({@code resourceSpans[].scopeSpans[].spans[]})
 * into flat rows. Pure function of its input: no I/O, so it is easy to test and
 * to re-run over stored raw payloads later.
 */
@Component
public class SpanMapper {

	public record SpanRow(String traceId, String spanId, String parentSpanId, String name, int kind,
			String serviceName, LocalDateTime startTime, LocalDateTime endTime, Long durationMs,
			int statusCode, String statusMessage, String attributesJson) {
	}

	private final JsonMapper mapper;

	public SpanMapper(JsonMapper mapper) {
		this.mapper = mapper;
	}

	public List<SpanRow> map(JsonNode root) {
		List<SpanRow> rows = new ArrayList<>();
		for (JsonNode resourceSpans : root.path("resourceSpans")) {
			Map<String, Object> resourceAttrs = attributes(resourceSpans.path("resource").path("attributes"));
			String serviceName = resourceAttrs.get("service.name") instanceof String s ? s : null;
			for (JsonNode scopeSpans : resourceSpans.path("scopeSpans")) {
				for (JsonNode span : scopeSpans.path("spans")) {
					SpanRow row = mapSpan(span, serviceName);
					if (row != null) {
						rows.add(row);
					}
				}
			}
		}
		return rows;
	}

	private SpanRow mapSpan(JsonNode span, String serviceName) {
		String traceId = text(span.path("traceId"));
		String spanId = text(span.path("spanId"));
		if (traceId == null || spanId == null) {
			return null; // cannot be keyed; the raw payload still holds it
		}
		Long startNanos = nanos(span.path("startTimeUnixNano"));
		Long endNanos = nanos(span.path("endTimeUnixNano"));
		if (startNanos == null) {
			return null;
		}
		JsonNode status = span.path("status");
		Long durationMs = endNanos == null ? null : Math.max(0, (endNanos - startNanos) / 1_000_000L);
		return new SpanRow(
				traceId,
				spanId,
				text(span.path("parentSpanId")),
				text(span.path("name")) == null ? "" : text(span.path("name")),
				kind(span.path("kind")),
				serviceName,
				utc(startNanos),
				endNanos == null ? null : utc(endNanos),
				durationMs,
				statusCode(status.path("code")),
				text(status.path("message")),
				mapper.writeValueAsString(attributes(span.path("attributes"))));
	}

	/** OTLP KeyValue list -> flat {key: typed value}. */
	private Map<String, Object> attributes(JsonNode keyValues) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (JsonNode kv : keyValues) {
			String key = text(kv.path("key"));
			if (key != null) {
				out.put(key, anyValue(kv.path("value")));
			}
		}
		return out;
	}

	private Object anyValue(JsonNode v) {
		if (v.has("stringValue")) {
			return v.path("stringValue").asString();
		}
		if (v.has("intValue")) {
			JsonNode n = v.path("intValue");
			return n.isNumber() ? n.asLong() : Long.parseLong(n.asString());
		}
		if (v.has("doubleValue")) {
			return v.path("doubleValue").asDouble();
		}
		if (v.has("boolValue")) {
			return v.path("boolValue").asBoolean();
		}
		if (v.has("bytesValue")) {
			return v.path("bytesValue").asString(); // base64, kept as text
		}
		if (v.has("arrayValue")) {
			List<Object> list = new ArrayList<>();
			for (JsonNode item : v.path("arrayValue").path("values")) {
				list.add(anyValue(item));
			}
			return list;
		}
		if (v.has("kvlistValue")) {
			return attributes(v.path("kvlistValue").path("values"));
		}
		return null;
	}

	private static String text(JsonNode n) {
		if (n.isMissingNode() || n.isNull()) {
			return null;
		}
		String s = n.asString();
		return s.isEmpty() ? null : s;
	}

	private static Long nanos(JsonNode n) {
		String s = text(n);
		if (s == null) {
			return null;
		}
		long v = Long.parseLong(s);
		return v == 0 ? null : v;
	}

	private static LocalDateTime utc(long epochNanos) {
		return LocalDateTime.ofInstant(
				Instant.ofEpochSecond(epochNanos / 1_000_000_000L, epochNanos % 1_000_000_000L), ZoneOffset.UTC);
	}

	private static int kind(JsonNode n) {
		if (n.isNumber()) {
			return n.asInt();
		}
		return switch (n.asString()) {
			case "SPAN_KIND_INTERNAL" -> 1;
			case "SPAN_KIND_SERVER" -> 2;
			case "SPAN_KIND_CLIENT" -> 3;
			case "SPAN_KIND_PRODUCER" -> 4;
			case "SPAN_KIND_CONSUMER" -> 5;
			default -> 0;
		};
	}

	private static int statusCode(JsonNode n) {
		if (n.isNumber()) {
			return n.asInt();
		}
		return switch (n.asString()) {
			case "STATUS_CODE_OK" -> 1;
			case "STATUS_CODE_ERROR" -> 2;
			default -> 0;
		};
	}

}
