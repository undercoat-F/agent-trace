package dev.agenttrace.ingest.jev;

import java.util.List;

/**
 * The "state" sent to Jev for one turn — a compact summary, not the raw
 * event stream (which could be large and mostly irrelevant to judging).
 * Serialized as-is via Jackson, so field names become the JSON Jev sees.
 */
public record PromptState(
		String userPrompt,
		String assistantResponse,
		int toolCalls,
		int failures,
		List<ToolCallSummary> tools) {

	public record ToolCallSummary(String toolName, boolean success) {
	}

}
