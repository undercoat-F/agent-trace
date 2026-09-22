import type { StatusFilter } from "../types";

interface Props {
  status: StatusFilter;
  tool: string;
  q: string;
  tools: string[];
  onStatusChange: (v: StatusFilter) => void;
  onToolChange: (v: string) => void;
  onQChange: (v: string) => void;
}

export function FilterBar({ status, tool, q, tools, onStatusChange, onToolChange, onQChange }: Props) {
  return (
    <div className="filter-bar">
      <input
        type="search"
        placeholder="スパン名で検索…"
        value={q}
        onChange={(e) => onQChange(e.target.value)}
        aria-label="スパン名で検索"
      />
      <select value={status} onChange={(e) => onStatusChange(e.target.value as StatusFilter)} aria-label="状態で絞り込み">
        <option value="">すべての状態</option>
        <option value="ok">OK のみ</option>
        <option value="error">失敗のみ</option>
      </select>
      <select value={tool} onChange={(e) => onToolChange(e.target.value)} aria-label="ツールで絞り込み">
        <option value="">すべてのツール</option>
        {tools.map((t) => (
          <option key={t} value={t}>
            {t}
          </option>
        ))}
      </select>
    </div>
  );
}
