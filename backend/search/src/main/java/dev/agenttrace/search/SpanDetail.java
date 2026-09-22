package dev.agenttrace.search;

import tools.jackson.databind.JsonNode;

/** {@link SpanRow} plus the full attributes payload, for the detail view. */
public record SpanDetail(SpanRow span, JsonNode attributes) {
}
