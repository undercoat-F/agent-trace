package dev.agenttrace.search;

import java.time.LocalDateTime;

/**
 * One row of {@code spans}, shaped for JSON responses. attributesJson stays a raw
 * string (already valid JSON from MySQL) so Jackson embeds it as an object
 * instead of double-encoding it as a string.
 */
public record SpanRow(
		String traceId,
		String spanId,
		String parentSpanId,
		String name,
		String serviceName,
		String toolName,
		LocalDateTime startTime,
		Long durationMs,
		int statusCode,
		String statusMessage,
		String errorType,
		long rawPayloadId) {
}
