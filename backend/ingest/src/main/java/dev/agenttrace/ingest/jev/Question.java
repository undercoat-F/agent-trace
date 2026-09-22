package dev.agenttrace.ingest.jev;

import java.util.List;
import java.util.Map;

/**
 * The three typed questions the Jev API accepts (docs.typesafe.ai/primitives),
 * matching concept doc §8's "Noul / Choice / Score". Verified against the
 * real documented wire format on 2026-09-22 — see JevClient for the exact
 * request/response JSON shape.
 */
public sealed interface Question {

	String type();

	/** "Is this true?" — answer is a single probability 0-1. criteria is optional context for yes/no. */
	record Noul(String instructions, Map<String, String> criteria) implements Question {
		public Noul(String instructions) {
			this(instructions, null);
		}

		@Override
		public String type() {
			return "noul";
		}
	}

	/** Pick one of a closed set of options (up to 255). criteria maps option key -> description. */
	record Choice(String instructions, Map<String, String> criteria) implements Question {
		@Override
		public String type() {
			return "choice";
		}
	}

	/** Where the state sits on an ordered 2-10 level rubric, described in words. */
	record Score(String instructions, List<String> criteria) implements Question {
		@Override
		public String type() {
			return "score";
		}
	}

}
