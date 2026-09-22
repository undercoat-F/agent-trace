package dev.agenttrace.ingest.jev;

import java.util.Map;

/** One question's typed answer, per docs.typesafe.ai/api. */
public sealed interface Answer {

	/** Concept doc §7's "value": the single number worth indexing/sorting on. */
	double value();

	/** Concept doc §7's "confidence". Noul has none of its own — the probability itself is the signal. */
	double confidence();

	record NoulAnswer(double noul) implements Answer {
		@Override
		public double value() {
			return noul;
		}

		/** Distance from 0.5, doubled: 1.0 at a confident 0 or 1, 0.0 at a coin-flip 0.5. */
		@Override
		public double confidence() {
			return Math.abs(noul - 0.5) * 2;
		}
	}

	record ChoiceAnswer(String choice, Map<String, Double> probabilities, double confidence) implements Answer {
		@Override
		public double value() {
			return probabilities.getOrDefault(choice, 0.0);
		}
	}

	record ScoreAnswer(double score, Map<String, String> legend, Map<String, Double> probabilities,
			double confidence) implements Answer {
		@Override
		public double value() {
			return score;
		}
	}

}
