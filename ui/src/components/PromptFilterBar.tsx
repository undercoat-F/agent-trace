interface Props {
  q: string;
  failuresOnly: boolean;
  onQChange: (v: string) => void;
  onFailuresOnlyChange: (v: boolean) => void;
}

export function PromptFilterBar({ q, failuresOnly, onQChange, onFailuresOnlyChange }: Props) {
  return (
    <div className="filter-bar">
      <input
        type="search"
        placeholder="プロンプトの内容で検索…"
        value={q}
        onChange={(e) => onQChange(e.target.value)}
        aria-label="プロンプトの内容で検索"
      />
      <label className="filter-bar__checkbox">
        <input type="checkbox" checked={failuresOnly} onChange={(e) => onFailuresOnlyChange(e.target.checked)} />
        失敗したターンのみ
      </label>
    </div>
  );
}
