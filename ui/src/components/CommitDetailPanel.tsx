import { useEffect, useState } from "react";
import { fetchCommitDetail } from "../api";
import type { CommitDetail, CommitSummary } from "../types";
import { eventLabel, formatTime } from "../format";

export function CommitDetailPanel({ commit }: { commit: CommitSummary }) {
  const [detail, setDetail] = useState<CommitDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setDetail(null);
    setError(null);
    fetchCommitDetail(commit.sha)
      .then(setDetail)
      .catch((e: Error) => setError(e.message));
  }, [commit.sha]);

  if (error) {
    return <p className="error-state">読み込みに失敗しました: {error}</p>;
  }
  if (!detail) {
    return <p className="loading-state">読み込み中…</p>;
  }

  return (
    <div className="detail-panel">
      <h2 className="detail-panel__title">
        <code className="sha-short">{detail.sha.slice(0, 7)}</code>
      </h2>
      <p className="prompt-text">{detail.message}</p>

      <dl className="detail-panel__meta">
        <dt>作者</dt>
        <dd>{detail.author}</dd>
        <dt>時刻</dt>
        <dd>{formatTime(detail.authoredAt)}</dd>
        <dt>親コミット</dt>
        <dd className="detail-panel__ids">{detail.parents.length > 0 ? detail.parents.map((p) => p.slice(0, 7)).join(", ") : "(なし。最初のコミット)"}</dd>
      </dl>

      {!detail.hasTrace && (
        <p className="empty-state">
          このコミットに紐づく会話の記録はありません。
          <br />
          <span className="muted">(このフックを組み込む前のコミット、または人手によるコミットの可能性があります)</span>
        </p>
      )}

      {detail.hasTrace && detail.filesChanged.length > 0 && (
        <>
          <h3 className="detail-panel__subtitle">変更ファイル</h3>
          <ul className="file-list">
            {detail.filesChanged.map((f) => (
              <li key={f}>
                <code>{f}</code>
              </li>
            ))}
          </ul>
        </>
      )}

      {detail.trace && (
        <>
          <h3 className="detail-panel__subtitle">このコミットを生んだプロンプト</h3>
          <p className="prompt-text">{detail.trace.userPrompt ?? <span className="muted">(記録なし)</span>}</p>

          <h3 className="detail-panel__subtitle">応答</h3>
          <p className="prompt-text">{detail.trace.assistantResponse ?? <span className="muted">(記録なし)</span>}</p>

          <h3 className="detail-panel__subtitle">経過(このターンで起きたこと)</h3>
          <ol className="event-timeline">
            {detail.trace.events.map((e) => (
              <li key={e.eventId} className={e.toolSuccess === "false" ? "event-timeline__item--failed" : ""}>
                <span className="event-timeline__time">{formatTime(e.occurredAt)}</span>
                <span className="event-timeline__name">{eventLabel(e.eventName)}</span>
                {e.toolName && <span className="event-timeline__tool">{e.toolName}</span>}
                {e.toolSuccess === "false" && <span className="badge badge--error">失敗</span>}
              </li>
            ))}
          </ol>
        </>
      )}
    </div>
  );
}
