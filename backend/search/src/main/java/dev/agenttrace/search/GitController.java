package dev.agenttrace.search;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.agenttrace.search.CommitTraceRepository.CommitTrace;
import dev.agenttrace.search.GitService.BranchSummary;
import dev.agenttrace.search.GitService.CommitDetail;
import dev.agenttrace.search.GitService.CommitSummary;

/**
 * Branch/commit browsing over the project's own git history (GitService),
 * annotated with whether each commit has an AI trace (CommitTraceRepository)
 * — the reverse-lookup entry point from concept doc §3.
 */
@RestController
public class GitController {

	private final GitService git;
	private final CommitTraceRepository traces;
	private final PromptRepository prompts;

	public GitController(GitService git, CommitTraceRepository traces, PromptRepository prompts) {
		this.git = git;
		this.traces = traces;
		this.prompts = prompts;
	}

	@GetMapping("/api/branches")
	public List<BranchSummary> branches() {
		return git.branches();
	}

	@GetMapping("/api/commits")
	public List<CommitSummaryResponse> commits(
			@RequestParam String branch,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		List<CommitSummary> commits = git.commits(branch, Math.min(Math.max(limit, 1), 200), Math.max(offset, 0));
		var withTrace = traces.shasWithTrace(commits.stream().map(CommitSummary::sha).toList());
		return commits.stream()
				.map(c -> new CommitSummaryResponse(c.sha(), c.shortMessage(), c.author(), c.authoredAt(), withTrace.contains(c.sha())))
				.toList();
	}

	@GetMapping("/api/commits/{sha}")
	public ResponseEntity<CommitDetailResponse> commit(@PathVariable String sha) {
		CommitDetail detail = git.commit(sha);
		if (detail == null) {
			return ResponseEntity.notFound().build();
		}
		Optional<CommitTrace> trace = traces.findOne(detail.sha());
		PromptDetail promptDetail = trace.flatMap(t -> t.promptId() == null ? Optional.<PromptDetail>empty() : prompts.findOne(t.promptId()))
				.orElse(null);
		return ResponseEntity.ok(new CommitDetailResponse(
				detail.sha(), detail.message(), detail.author(), detail.authoredAt(), detail.parents(),
				trace.isPresent(), trace.map(CommitTrace::filesChanged).orElse(List.of()), promptDetail));
	}

}
