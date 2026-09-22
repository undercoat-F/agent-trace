import { useEffect, useState } from "react";
import { fetchSpanDetail } from "../api";
import type { SpanRow } from "../types";
import { formatDuration, formatTime } from "../format";
import { StatusBadge } from "./StatusBadge";

export function SpanDetailPanel({ span }: { span: SpanRow }) {
  const [attributes, setAttributes] = useState<Record<string, unknown> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setAttributes(null);
    setError(null);
    fetchSpanDetail(span.traceId, span.spanId)
      .then((d) => setAttributes(d.attributes))
      .catch((e: Error) => setError(e.message));
  }, [span.traceId, span.spanId]);

  return (
    <div className="detail-panel">
      <h2 className="detail-panel__title">{span.name}</h2>
      <dl className="detail-panel__meta">
        <dt>状態</dt>
        <dd>
          <StatusBadge code={span.statusCode} />
          {span.statusMessage && <span className="detail-panel__status-message"> — {span.statusMessage}</span>}
        </dd>
        <dt>サービス</dt>
        <dd>{span.serviceName ?? "—"}</dd>
        {span.toolName && (
          <>
            <dt>ツール</dt>
            <dd>{span.toolName}</dd>
          </>
        )}
        <dt>時刻</dt>
        <dd>{formatTime(span.startTime)}</dd>
        <dt>所要時間</dt>
        <dd>{formatDuration(span.durationMs)}</dd>
        <dt>trace_id / span_id</dt>
        <dd className="detail-panel__ids">
          {span.traceId} / {span.spanId}
        </dd>
      </dl>

      <h3 className="detail-panel__subtitle">属性(attributes)</h3>
      {error && <p className="error-state">読み込みに失敗しました: {error}</p>}
      {!error && !attributes && <p className="loading-state">読み込み中…</p>}
      {attributes && <pre className="attributes-json">{JSON.stringify(attributes, null, 2)}</pre>}
    </div>
  );
}
