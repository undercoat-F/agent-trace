import type { CommitSummary } from "../types";
import { formatTime } from "../format";

interface Props {
  commits: CommitSummary[];
  selected: CommitSummary | null;
  onSelect: (commit: CommitSummary) => void;
}

export function CommitTable({ commits, selected, onSelect }: Props) {
  if (commits.length === 0) {
    return <p className="empty-state">コミットがありません。</p>;
  }

  return (
    <table className="span-table">
      <thead>
        <tr>
          <th>時刻</th>
          <th>メッセージ</th>
          <th>作者</th>
          <th>会話</th>
        </tr>
      </thead>
      <tbody>
        {commits.map((c) => (
          <tr key={c.sha} className={c.sha === selected?.sha ? "is-selected" : ""} onClick={() => onSelect(c)}>
            <td className="col-time">{formatTime(c.authoredAt)}</td>
            <td className="col-prompt-preview">
              <code className="sha-short">{c.sha.slice(0, 7)}</code> {c.shortMessage}
            </td>
            <td>{c.author}</td>
            <td>{c.hasTrace ? <span className="badge badge--ok">あり</span> : <span className="muted">—</span>}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
