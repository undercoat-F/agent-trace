package dev.agenttrace.search;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromptController {

	private final PromptRepository repository;

	public PromptController(PromptRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/api/prompts")
	public List<PromptRow> search(
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "false") boolean failuresOnly,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return repository.search(blankToNull(q), failuresOnly, limit, offset);
	}

	@GetMapping("/api/prompts/{promptId}")
	public ResponseEntity<PromptDetail> one(@PathVariable String promptId) {
		return repository.findOne(promptId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}

}
