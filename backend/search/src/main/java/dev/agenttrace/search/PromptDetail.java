package dev.agenttrace.search;

import java.time.LocalDateTime;
import java.util.List;

import tools.jackson.databind.JsonNode;

/** Full turn detail: the actual prompt/response text, its event timeline, and Jev's judgments (concept doc §3/§7). */
public record PromptDetail(
		String promptId,
		String sessionId,
		String userPrompt,
		String assistantResponse,
		LocalDateTime startedAt,
		LocalDateTime endedAt,
		int toolCalls,
		int failures,
		List<EventSummary> events,
		List<JudgmentSummary> judgments) {

	public record EventSummary(
			String eventId,
			int eventSequence,
			String eventName,
			LocalDateTime occurredAt,
			String toolName,
			String toolSuccess) {
	}

	public record JudgmentSummary(
			String questionId,
			int questionVersion,
			double value,
			double confidence,
			String modelVersion,
			// The full typed answer (choice label / score legend / probabilities) —
			// `value`/`confidence` alone can't say e.g. *which* Choice option won.
			JsonNode rawAnswer) {
	}
}
