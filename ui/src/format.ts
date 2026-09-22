export function formatTime(iso: string): string {
  // Stored/queried as UTC but the driver returns a zone-less string; treat it as UTC explicitly.
  const d = new Date(iso.endsWith("Z") ? iso : `${iso}Z`);
  return d.toLocaleString(undefined, {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function formatDuration(ms: number | null): string {
  if (ms === null) return "—";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

export function statusLabel(code: number): "OK" | "失敗" | "不明" {
  if (code === 2) return "失敗";
  if (code === 1) return "OK";
  return "不明";
}

// event_name values observed in real Claude Code OTel output (see
// backend/ingest/src/test/resources/claude-code-real-sample.jsonl). Anything
// not listed here falls back to the raw name, so a new/unknown event type
// still renders instead of disappearing.
const EVENT_LABELS: Record<string, string> = {
  user_prompt: "プロンプト送信",
  assistant_response: "応答",
  tool_decision: "ツール判断",
  tool_result: "ツール結果",
  api_request: "API呼び出し",
  api_error: "APIエラー",
  mcp_server_connection: "MCP接続",
  managed_settings_resolved: "設定読み込み",
  plugin_loaded: "プラグイン読み込み",
};

export function eventLabel(eventName: string): string {
  return EVENT_LABELS[eventName] ?? eventName;
}

// Mirrors backend/ingest/.../jev/QuestionCatalog.java. Keep in sync if that changes.
const QUESTION_LABELS: Record<string, string> = {
  bugfix_intent: "バグ修正の意図",
  stuck: "行き詰まり",
  deviates_from_design: "設計方針からの逸脱",
  change_type: "変更の主目的",
  impact_score: "影響範囲の大きさ",
};

export function questionLabel(questionId: string): string {
  return QUESTION_LABELS[questionId] ?? questionId;
}

export function formatPercent(value: number): string {
  return `${Math.round(value * 100)}%`;
}

// change_type's Choice option keys -> short display labels (criteria in
// QuestionCatalog.java has the full descriptions Jev sees; these are just
// for the UI).
const CHOICE_OPTION_LABELS: Record<string, string> = {
  feature: "機能追加",
  bugfix: "バグ修正",
  refactor: "リファクタ",
  dependency_update: "依存更新",
  experiment: "実験",
};

export function choiceOptionLabel(key: string): string {
  return CHOICE_OPTION_LABELS[key] ?? key;
}
