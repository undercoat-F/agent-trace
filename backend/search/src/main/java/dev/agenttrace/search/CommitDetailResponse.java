package dev.agenttrace.search;

import java.time.LocalDateTime;
import java.util.List;

/** git's own commit info, plus our AI trace when this commit has one (concept doc §3). */
public record CommitDetailResponse(
		String sha,
		String message,
		String author,
		LocalDateTime authoredAt,
		List<String> parents,
		boolean hasTrace,
		List<String> filesChanged,
		PromptDetail trace) {
}
