package dev.agenttrace.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import dev.agenttrace.ingest.SpanMapper.SpanRow;

class SpanMapperTest {

	private final JsonMapper mapper = JsonMapper.builder().build();
	private final SpanMapper spanMapper = new SpanMapper(mapper);

	// A failed execute_tool span, shaped like the collector's OTLP/JSON output.
	private static final String FAILED_TOOL = """
			{"resourceSpans":[{
			  "resource":{"attributes":[{"key":"service.name","value":{"stringValue":"copilot-chat"}}]},
			  "scopeSpans":[{"scope":{"name":"copilot-chat"},"spans":[
			    {"traceId":"0af7651916cd43dd8448eb211c80319c","spanId":"b7ad6b7169203331",
			     "parentSpanId":"00f067aa0ba902b7","name":"execute_tool read_file","kind":1,
			     "startTimeUnixNano":"1789960887379000000","endTimeUnixNano":"1789960887394000000",
			     "attributes":[
			       {"key":"gen_ai.tool.name","value":{"stringValue":"read_file"}},
			       {"key":"error.type","value":{"stringValue":"Error"}},
			       {"key":"gen_ai.usage.input_tokens","value":{"intValue":"42"}},
			       {"key":"ok","value":{"boolValue":false}},
			       {"key":"tags","value":{"arrayValue":{"values":[{"stringValue":"a"},{"intValue":"2"}]}}}],
			     "status":{"code":2,"message":"ENOENT"}},
			    {"spanId":"1111111111111111","name":"no trace id, must be skipped",
			     "startTimeUnixNano":"1789960887379000000"}
			  ]}]}]}
			""";

	@Test
	void mapsFailedToolSpan() {
		List<SpanRow> rows = spanMapper.map(mapper.readTree(FAILED_TOOL));

		assertThat(rows).hasSize(1);
		SpanRow s = rows.get(0);
		assertThat(s.traceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
		assertThat(s.spanId()).isEqualTo("b7ad6b7169203331");
		assertThat(s.parentSpanId()).isEqualTo("00f067aa0ba902b7");
		assertThat(s.name()).isEqualTo("execute_tool read_file");
		assertThat(s.serviceName()).isEqualTo("copilot-chat");
		assertThat(s.durationMs()).isEqualTo(15);
		assertThat(s.statusCode()).isEqualTo(2);
		assertThat(s.statusMessage()).isEqualTo("ENOENT");
		assertThat(s.startTime()).isEqualTo(LocalDateTime.of(2026, 9, 21, 3, 21, 27, 379_000_000));
	}

	@Test
	void flattensTypedAttributes() {
		SpanRow s = spanMapper.map(mapper.readTree(FAILED_TOOL)).get(0);
		var attrs = mapper.readTree(s.attributesJson());

		assertThat(attrs.path("gen_ai.tool.name").asString()).isEqualTo("read_file");
		assertThat(attrs.path("error.type").asString()).isEqualTo("Error");
		assertThat(attrs.path("gen_ai.usage.input_tokens").isNumber()).isTrue();
		assertThat(attrs.path("gen_ai.usage.input_tokens").asLong()).isEqualTo(42);
		assertThat(attrs.path("ok").asBoolean(true)).isFalse();
		assertThat(attrs.path("tags").size()).isEqualTo(2);
	}

	@Test
	void acceptsEnumNamesForStatusAndKind() {
		String json = """
				{"resourceSpans":[{"scopeSpans":[{"spans":[
				  {"traceId":"0af7651916cd43dd8448eb211c80319c","spanId":"b7ad6b7169203331","name":"x",
				   "kind":"SPAN_KIND_CLIENT","startTimeUnixNano":"1000000000",
				   "status":{"code":"STATUS_CODE_ERROR"}}]}]}]}
				""";
		SpanRow s = spanMapper.map(mapper.readTree(json)).get(0);

		assertThat(s.kind()).isEqualTo(3);
		assertThat(s.statusCode()).isEqualTo(2);
		assertThat(s.endTime()).isNull();
		assertThat(s.durationMs()).isNull();
	}

	@Test
	void emptyRequestGivesNoRows() {
		assertThat(spanMapper.map(mapper.readTree("{}"))).isEmpty();
	}

}
