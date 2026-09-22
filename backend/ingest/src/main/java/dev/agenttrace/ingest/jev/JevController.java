package dev.agenttrace.ingest.jev;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.agenttrace.ingest.jev.Answer.NoulAnswer;

/**
 * /internal/jev/selftest: a harmless, fixed Noul question, to confirm the
 * client + configured key actually reach the real API — without exposing an
 * endpoint that accepts arbitrary state/questions from a caller yet (that's
 * the judging pipeline, not built here). Response carries only the parsed
 * numeric result, never request/response headers.
 */
@RestController
public class JevController {

	private final JevClient jev;

	public JevController(JevClient jev) {
		this.jev = jev;
	}

	@GetMapping("/internal/jev/selftest")
	public ResponseEntity<?> selftest() {
		if (!jev.isAvailable()) {
			return ResponseEntity.status(503).body(Map.of("available", false, "reason", "jev.api-key not configured"));
		}
		try {
			JevClient.JudgeResult result = jev.judge(
					"The sky is blue on a clear day.",
					Map.of("is_true", new Question.Noul("Is this statement true?")));
			NoulAnswer answer = (NoulAnswer) result.answers().get("is_true");
			return ResponseEntity.ok(Map.of(
					"available", true,
					"model", result.modelVersion(),
					"noul", answer.noul(),
					"inputTokens", result.inputTokens(),
					"outputTokens", result.outputTokens()));
		}
		catch (JevException e) {
			return ResponseEntity.status(502).body(Map.of("available", true, "error", e.getMessage()));
		}
	}

}
