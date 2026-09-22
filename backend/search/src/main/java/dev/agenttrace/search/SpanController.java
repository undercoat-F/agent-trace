package dev.agenttrace.search;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpanController {

	private final SpanRepository repository;

	public SpanController(SpanRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/api/spans")
	public List<SpanRow> search(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String tool,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {
		return repository.search(blankToNull(status), blankToNull(tool), blankToNull(q), limit, offset);
	}

	@GetMapping("/api/spans/{traceId}/{spanId}")
	public ResponseEntity<SpanDetail> one(@PathVariable String traceId, @PathVariable String spanId) {
		return repository.findOne(traceId, spanId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/api/tools")
	public List<String> tools() {
		return repository.tools();
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}

}
