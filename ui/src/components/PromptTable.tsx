import type { PromptRow } from "../types";
import { formatTime } from "../format";

interface Props {
  prompts: PromptRow[];
  selected: PromptRow | null;
  onSelect: (prompt: PromptRow) => void;
}

export function PromptTable({ prompts, selected, onSelect }: Props) {
  if (prompts.length === 0) {
    return <p className="empty-state">条件に一致するターンがありません。</p>;
  }

  return (
    <table className="span-table prompt-table">
      <thead>
        <tr>
          <th>時刻</th>
          <th>プロンプト</th>
          <th>ツール</th>
          <th>失敗</th>
        </tr>
      </thead>
      <tbody>
        {prompts.map((p) => (
          <tr
            key={p.promptId}
            className={p.promptId === selected?.promptId ? "is-selected" : ""}
            onClick={() => onSelect(p)}
          >
            <td className="col-time">{formatTime(p.startedAt)}</td>
            <td className="col-prompt-preview">{p.promptPreview ?? <span className="muted">(記録なし)</span>}</td>
            <td className="col-count">{p.toolCalls}</td>
            <td>{p.failures > 0 ? <span className="badge badge--error">{p.failures}</span> : <span className="badge badge--ok">0</span>}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
