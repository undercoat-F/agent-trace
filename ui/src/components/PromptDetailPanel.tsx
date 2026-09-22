import { useEffect, useState } from "react";
import { fetchPromptDetail } from "../api";
import type { PromptDetail, PromptRow } from "../types";
import { eventLabel, formatTime } from "../format";

export function PromptDetailPanel({ prompt }: { prompt: PromptRow }) {
  const [detail, setDetail] = useState<PromptDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setDetail(null);
    setError(null);
    fetchPromptDetail(prompt.promptId)
      .then(setDetail)
      .catch((e: Error) => setError(e.message));
  }, [prompt.promptId]);

  if (error) {
    return <p className="error-state">読み込みに失敗しました: {error}</p>;
  }
  if (!detail) {
    return <p className="loading-state">読み込み中…</p>;
  }

  return (
    <div className="detail-panel">
      <h2 className="detail-panel__title">プロンプト</h2>
      <p className="prompt-text">{detail.userPrompt ?? <span className="muted">(記録なし。OTEL_LOG_USER_PROMPTS 未設定の可能性)</span>}</p>

      <h3 className="detail-panel__subtitle">応答</h3>
      <p className="prompt-text">{detail.assistantResponse ?? <span className="muted">(記録なし)</span>}</p>

      <dl className="detail-panel__meta">
        <dt>時刻</dt>
        <dd>
          {formatTime(detail.startedAt)} 〜 {formatTime(detail.endedAt)}
        </dd>
        <dt>ツール呼び出し / 失敗</dt>
        <dd>
          {detail.toolCalls} / {detail.failures > 0 ? <span className="badge badge--error">{detail.failures}</span> : "0"}
        </dd>
        <dt>prompt_id / session_id</dt>
        <dd className="detail-panel__ids">
          {detail.promptId} / {detail.sessionId}
        </dd>
      </dl>

      <h3 className="detail-panel__subtitle">経過(このターンで起きたこと)</h3>
      <ol className="event-timeline">
        {detail.events.map((e) => (
          <li key={e.eventId} className={e.toolSuccess === "false" ? "event-timeline__item--failed" : ""}>
            <span className="event-timeline__time">{formatTime(e.occurredAt)}</span>
            <span className="event-timeline__name">{eventLabel(e.eventName)}</span>
            {e.toolName && <span className="event-timeline__tool">{e.toolName}</span>}
            {e.toolSuccess === "false" && <span className="badge badge--error">失敗</span>}
          </li>
        ))}
      </ol>
    </div>
  );
}
