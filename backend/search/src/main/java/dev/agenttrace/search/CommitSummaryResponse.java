package dev.agenttrace.search;

import java.time.LocalDateTime;

public record CommitSummaryResponse(String sha, String shortMessage, String author, LocalDateTime authoredAt, boolean hasTrace) {
}
