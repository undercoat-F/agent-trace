package dev.agenttrace.ingest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/** Shared OTLP KeyValue-list flattening, used by both SpanMapper and LogMapper. */
final class OtlpAttributes {

	private OtlpAttributes() {
	}

	/** OTLP KeyValue list -> flat {key: typed value}. */
	static Map<String, Object> flatten(JsonNode keyValues) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (JsonNode kv : keyValues) {
			String key = text(kv.path("key"));
			if (key != null) {
				out.put(key, anyValue(kv.path("value")));
			}
		}
		return out;
	}

	private static Object anyValue(JsonNode v) {
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
			return flatten(v.path("kvlistValue").path("values"));
		}
		return null;
	}

	static String text(JsonNode n) {
		if (n.isMissingNode() || n.isNull()) {
			return null;
		}
		String s = n.asString();
		return s.isEmpty() ? null : s;
	}

	/** A "0" or absent unix-nano field means unset in OTLP (not the epoch). */
	static Long nanos(JsonNode n) {
		String s = text(n);
		if (s == null) {
			return null;
		}
		long v = Long.parseLong(s);
		return v == 0 ? null : v;
	}

	static LocalDateTime utc(long epochNanos) {
		return LocalDateTime.ofInstant(
				Instant.ofEpochSecond(epochNanos / 1_000_000_000L, epochNanos % 1_000_000_000L), ZoneOffset.UTC);
	}

}
