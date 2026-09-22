package dev.agenttrace.search;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** The AI-trace side of a commit (captured by scripts/hook-detect-commit.ps1), joined against git's own log. */
@Repository
public class CommitTraceRepository {

	public record CommitTrace(String sha, String promptId, List<String> filesChanged) {
	}

	private final JdbcClient jdbc;
	private final JsonMapper mapper;

	public CommitTraceRepository(JdbcClient jdbc, JsonMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	/** For annotating a commit list with a "has an AI trace" flag without one query per row. */
	public Set<String> shasWithTrace(List<String> shas) {
		if (shas.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("SELECT sha FROM commits WHERE sha IN (:shas)")
				.param("shas", shas).query(String.class).list());
	}

	public Optional<CommitTrace> findOne(String sha) {
		return jdbc.sql("SELECT sha, prompt_id, files_changed FROM commits WHERE sha = :sha")
				.param("sha", sha)
				.query((rs, rowNum) -> new CommitTrace(
						rs.getString("sha"), rs.getString("prompt_id"),
						parseFiles(rs.getString("files_changed"))))
				.optional();
	}

	private List<String> parseFiles(String json) {
		if (json == null) {
			return List.of();
		}
		String[] arr = mapper.readValue(json, String[].class);
		return List.of(arr);
	}

}
