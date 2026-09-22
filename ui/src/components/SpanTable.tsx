import type { SpanRow } from "../types";
import { formatDuration, formatTime } from "../format";
import { StatusBadge } from "./StatusBadge";

interface Props {
  spans: SpanRow[];
  selected: SpanRow | null;
  onSelect: (span: SpanRow) => void;
}

export function SpanTable({ spans, selected, onSelect }: Props) {
  if (spans.length === 0) {
    return <p className="empty-state">条件に一致するスパンがありません。</p>;
  }

  return (
    <table className="span-table">
      <thead>
        <tr>
          <th>時刻</th>
          <th>名前</th>
          <th>サービス</th>
          <th>所要時間</th>
          <th>状態</th>
        </tr>
      </thead>
      <tbody>
        {spans.map((s) => (
          <tr
            key={`${s.traceId}/${s.spanId}`}
            className={s.traceId === selected?.traceId && s.spanId === selected?.spanId ? "is-selected" : ""}
            onClick={() => onSelect(s)}
          >
            <td className="col-time">{formatTime(s.startTime)}</td>
            <td className="col-name">{s.name}</td>
            <td className="col-service">{s.serviceName ?? "—"}</td>
            <td className="col-duration">{formatDuration(s.durationMs)}</td>
            <td>
              <StatusBadge code={s.statusCode} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
