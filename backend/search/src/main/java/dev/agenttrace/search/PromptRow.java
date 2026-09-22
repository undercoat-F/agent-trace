package dev.agenttrace.search;

import java.time.LocalDateTime;

/** List-view row: deliberately excludes the full prompt/response text (too long for a table cell). */
public record PromptRow(
		String promptId,
		String sessionId,
		String promptPreview,
		LocalDateTime startedAt,
		LocalDateTime endedAt,
		int toolCalls,
		int failures) {
}
