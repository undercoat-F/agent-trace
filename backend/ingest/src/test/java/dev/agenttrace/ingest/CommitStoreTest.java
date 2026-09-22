package dev.agenttrace.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.agenttrace.ingest.CommitStore.CommitPayload;

class CommitStoreTest {

	@Test
	void convertsGitIsoTimestampToUtc() {
		// git %cI for a JST (+09:00) commit -> the equivalent UTC instant.
		var payload = new CommitPayload("sha", "main", "msg", List.of(), "p", "s", "2026-09-22T18:30:00+09:00");
		assertThat(payload.committedAtUtc()).isEqualTo(LocalDateTime.of(2026, 9, 22, 9, 30, 0));
	}

	@Test
	void utcTimestampPassesThroughUnchanged() {
		var payload = new CommitPayload("sha", "main", "msg", List.of(), "p", "s", "2026-09-22T09:30:00Z");
		assertThat(payload.committedAtUtc()).isEqualTo(LocalDateTime.of(2026, 9, 22, 9, 30, 0));
	}

	@Test
	void missingTimestampFallsBackToNowRatherThanThrowing() {
		var payload = new CommitPayload("sha", "main", "msg", List.of(), "p", "s", null);
		assertThat(payload.committedAtUtc()).isCloseTo(LocalDateTime.now(java.time.ZoneOffset.UTC),
				org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
	}

	@Test
	void unparsableTimestampFallsBackToNowRatherThanThrowing() {
		var payload = new CommitPayload("sha", "main", "msg", List.of(), "p", "s", "not-a-date");
		assertThat(payload.committedAtUtc()).isCloseTo(LocalDateTime.now(java.time.ZoneOffset.UTC),
				org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
	}

}
