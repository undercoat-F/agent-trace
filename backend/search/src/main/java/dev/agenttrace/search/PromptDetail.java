package dev.agenttrace.search;

import java.time.LocalDateTime;
import java.util.List;

/** Full turn detail: the actual prompt/response text, plus its event timeline (concept doc §3). */
public record PromptDetail(
		String promptId,
		String sessionId,
		String userPrompt,
		String assistantResponse,
		LocalDateTime startedAt,
		LocalDateTime endedAt,
		int toolCalls,
		int failures,
		List<EventSummary> events) {

	public record EventSummary(
			String eventId,
			int eventSequence,
			String eventName,
			LocalDateTime occurredAt,
			String toolName,
			String toolSuccess) {
	}
}
