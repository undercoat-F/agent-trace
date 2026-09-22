package dev.agenttrace.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import dev.agenttrace.ingest.CommitStore.CommitPayload;

/**
 * Plain JSON, not OTLP: called directly by scripts/hook-detect-commit.ps1
 * (a PostToolUse hook), not via the collector. Commit metadata isn't
 * telemetry, and Claude Code's own OTel output never includes it anyway
 * (confirmed against real captures) — see docs/agent-trace-concept.md §2/§4.
 */
@RestController
public class CommitController {

	private static final Logger log = LoggerFactory.getLogger(CommitController.class);

	private final JsonMapper mapper;
	private final CommitStore store;

	public CommitController(JsonMapper mapper, CommitStore store) {
		this.mapper = mapper;
		this.store = store;
	}

	@PostMapping(path = "/v1/commits", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> commits(@RequestBody String body) {
		CommitPayload commit;
		try {
			commit = mapper.readValue(body, CommitPayload.class);
		}
		catch (JacksonException e) {
			return ResponseEntity.badRequest().body("{\"error\":\"invalid JSON\"}");
		}
		if (commit.sha() == null || commit.sha().isBlank()) {
			return ResponseEntity.badRequest().body("{\"error\":\"sha is required\"}");
		}
		store.store(body, commit);
		log.info("commit sha={} promptId={} branch={}", commit.sha(), commit.promptId(), commit.branch());
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{}");
	}

}
