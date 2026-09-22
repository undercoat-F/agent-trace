package dev.agenttrace.ingest.jev;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * concept doc §8's initial question set. question_id/version pairs are
 * stable identifiers: rewriting a question's instructions bumps its version
 * (see judgments.question_version) rather than silently changing what past
 * rows mean.
 *
 * NOTE: "impact_score"'s rubric levels are not specified in the concept doc
 * (only "この変更の影響範囲の大きさ" as the instruction) — the 5 levels below
 * are this implementation's own choice, not something verified against a
 * spec. Worth reviewing/adjusting.
 */
public final class QuestionCatalog {

	private QuestionCatalog() {
	}

	public static final int VERSION = 1;

	public static final Map<String, Question> ALL = build();

	private static Map<String, Question> build() {
		Map<String, Question> q = new LinkedHashMap<>();
		q.put("bugfix_intent", new Question.Noul("このターンはバグの修正を意図していたか"));
		q.put("stuck", new Question.Noul("このターンで行き詰まりが起きたか(同じ手を繰り返した)"));
		q.put("deviates_from_design", new Question.Noul("このターンは既存の設計方針からの逸脱を含むか"));
		q.put("change_type", new Question.Choice("この変更の主目的は?", Map.of(
				"feature", "新しい機能の追加",
				"bugfix", "既存の不具合の修正",
				"refactor", "外部から見た動作を変えないコード整理",
				"dependency_update", "依存ライブラリやバージョンの更新",
				"experiment", "試験的な変更(採用するかどうか未確定)")));
		// Own rubric — see class-level note.
		q.put("impact_score", new Question.Score("この変更の影響範囲の大きさ", List.of(
				"単一ファイル内の些細な変更(タイポ修正、コメント追加など)",
				"単一ファイルの主要な変更",
				"複数ファイルにまたがるが、既存の設計の範囲内の変更",
				"アーキテクチャや公開インターフェースに影響する変更",
				"複数モジュールにまたがる、または後方互換性に影響する大規模な変更")));
		return Map.copyOf(q);
	}

}
