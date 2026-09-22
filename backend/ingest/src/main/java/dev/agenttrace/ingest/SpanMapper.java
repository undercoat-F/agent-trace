package dev.agenttrace.ingest;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
			Map<String, Object> resourceAttrs = OtlpAttributes.flatten(resourceSpans.path("resource").path("attributes"));
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
		Long startNanos = OtlpAttributes.nanos(span.path("startTimeUnixNano"));
		Long endNanos = OtlpAttributes.nanos(span.path("endTimeUnixNano"));
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
				OtlpAttributes.utc(startNanos),
				endNanos == null ? null : OtlpAttributes.utc(endNanos),
				durationMs,
				statusCode(status.path("code")),
				text(status.path("message")),
				mapper.writeValueAsString(OtlpAttributes.flatten(span.path("attributes"))));
	}

	private static String text(JsonNode n) {
		return OtlpAttributes.text(n);
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
