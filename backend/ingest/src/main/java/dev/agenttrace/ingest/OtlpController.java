package dev.agenttrace.ingest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.agenttrace.ingest.SpanMapper.SpanRow;

/**
 * OTLP/HTTP receiver (JSON encoding). The OTel Collector forwards here with
 * {@code otlphttp} + {@code encoding: json}, so this side never handles protobuf.
 * Uncompressed bodies only: the collector exporter is configured with compression none.
 */
@RestController
public class OtlpController {

	private static final Logger log = LoggerFactory.getLogger(OtlpController.class);
	private static final String OTLP_OK = "{}";

	private final JsonMapper mapper;
	private final SpanMapper spanMapper;
	private final PayloadStore store;

	public OtlpController(JsonMapper mapper, SpanMapper spanMapper, PayloadStore store) {
		this.mapper = mapper;
		this.spanMapper = spanMapper;
		this.store = store;
	}

	@PostMapping(path = "/v1/traces", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> traces(@RequestBody String body) {
		JsonNode root = parse(body);
		if (root == null) {
			return ResponseEntity.badRequest().body("{\"error\":\"invalid JSON\"}");
		}
		List<SpanRow> spans = spanMapper.map(root);
		PayloadStore.Result r = store.store("traces", body, spans);
		log.info("traces raw={} duplicate={} spans={}", r.rawPayloadId(), r.duplicate(), r.spans());
		return json(OTLP_OK);
	}

	/** Logs are kept raw for now (principle 1); expansion into events comes later. */
	@PostMapping(path = "/v1/logs", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> logs(@RequestBody String body) {
		if (parse(body) == null) {
			return ResponseEntity.badRequest().body("{\"error\":\"invalid JSON\"}");
		}
		PayloadStore.Result r = store.store("logs", body, List.of());
		log.info("logs raw={} duplicate={}", r.rawPayloadId(), r.duplicate());
		return json(OTLP_OK);
	}

	private JsonNode parse(String body) {
		try {
			return mapper.readTree(body);
		}
		catch (JacksonException e) {
			return null;
		}
	}

	private static ResponseEntity<String> json(String body) {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
	}

}
