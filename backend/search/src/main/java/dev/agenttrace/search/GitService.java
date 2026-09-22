package dev.agenttrace.search;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Reads the project's own .git directly (concept doc §3): the reverse-lookup
 * entry point should be an ordinary, familiar branch/commit log, not just the
 * subset of commits we happen to have an AI trace for. Opens lazily and
 * degrades to empty results if the repo isn't mounted, rather than failing
 * the whole service's startup over a missing volume.
 */
@Service
public class GitService {

	private static final Logger log = LoggerFactory.getLogger(GitService.class);

	public record BranchSummary(String name, String headSha) {
	}

	public record CommitSummary(String sha, String shortMessage, String author, LocalDateTime authoredAt) {
	}

	public record CommitDetail(String sha, String message, String author, LocalDateTime authoredAt,
			List<String> parents) {
	}

	private final String gitDirPath;
	private Repository repository;

	public GitService(@Value("${git.repo-path:.git}") String gitDirPath) {
		this.gitDirPath = gitDirPath;
	}

	@PostConstruct
	void open() {
		try {
			File gitDir = new File(gitDirPath);
			if (!gitDir.exists()) {
				log.warn("git.repo-path '{}' does not exist; branch/commit browsing will return empty results", gitDirPath);
				return;
			}
			repository = new FileRepositoryBuilder().setGitDir(gitDir).readEnvironment().build();
			log.info("Opened git repository at {}", gitDirPath);
		}
		catch (IOException e) {
			log.warn("Could not open git repository at '{}': {}", gitDirPath, e.getMessage());
		}
	}

	public boolean isAvailable() {
		return repository != null;
	}

	public List<BranchSummary> branches() {
		if (repository == null) {
			return List.of();
		}
		try (Git git = new Git(repository)) {
			List<BranchSummary> out = new ArrayList<>();
			for (Ref ref : git.branchList().call()) {
				out.add(new BranchSummary(Repository.shortenRefName(ref.getName()), ref.getObjectId().getName()));
			}
			return out;
		}
		catch (Exception e) {
			log.warn("Failed to list branches: {}", e.getMessage());
			return List.of();
		}
	}

	public List<CommitSummary> commits(String branch, int limit, int offset) {
		if (repository == null) {
			return List.of();
		}
		try (Git git = new Git(repository)) {
			ObjectId start = repository.resolve(branch);
			if (start == null) {
				return List.of();
			}
			List<CommitSummary> out = new ArrayList<>();
			for (RevCommit c : git.log().add(start).setSkip(offset).setMaxCount(limit).call()) {
				out.add(new CommitSummary(c.getName(), c.getShortMessage(),
						c.getAuthorIdent().getName(), toUtc(c.getAuthorIdent().getWhenAsInstant())));
			}
			return out;
		}
		catch (Exception e) {
			log.warn("Failed to list commits for branch '{}': {}", branch, e.getMessage());
			return List.of();
		}
	}

	public CommitDetail commit(String sha) {
		if (repository == null) {
			return null;
		}
		try (Git git = new Git(repository)) {
			ObjectId id = repository.resolve(sha);
			if (id == null) {
				return null;
			}
			try (var revWalk = new org.eclipse.jgit.revwalk.RevWalk(repository)) {
				RevCommit c = revWalk.parseCommit(id);
				List<String> parents = new ArrayList<>();
				for (RevCommit p : c.getParents()) {
					parents.add(p.getName());
				}
				return new CommitDetail(c.getName(), c.getFullMessage().strip(),
						c.getAuthorIdent().getName(), toUtc(c.getAuthorIdent().getWhenAsInstant()), parents);
			}
		}
		catch (Exception e) {
			log.warn("Failed to read commit '{}': {}", sha, e.getMessage());
			return null;
		}
	}

	private static LocalDateTime toUtc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
