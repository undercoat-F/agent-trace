import { useEffect, useState } from "react";
import { searchPrompts } from "../api";
import { PromptFilterBar } from "../components/PromptFilterBar";
import { PromptTable } from "../components/PromptTable";
import { PromptDetailPanel } from "../components/PromptDetailPanel";
import type { PromptRow } from "../types";
import { useDebounced } from "../useDebounced";

export function PromptsView() {
  const [q, setQ] = useState("");
  const [failuresOnly, setFailuresOnly] = useState(false);
  const debouncedQ = useDebounced(q, 300);

  const [prompts, setPrompts] = useState<PromptRow[]>([]);
  const [selected, setSelected] = useState<PromptRow | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    setError(null);
    searchPrompts({ q: debouncedQ, failuresOnly })
      .then((rows) => {
        setPrompts(rows);
        setSelected((prev) => (prev && rows.some((r) => r.promptId === prev.promptId) ? prev : (rows[0] ?? null)));
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [debouncedQ, failuresOnly]);

  return (
    <>
      <PromptFilterBar q={q} failuresOnly={failuresOnly} onQChange={setQ} onFailuresOnlyChange={setFailuresOnly} />

      {error && <p className="error-state">{error}</p>}

      <div className="app__body">
        <div className="app__list">
          {loading ? <p className="loading-state">読み込み中…</p> : <PromptTable prompts={prompts} selected={selected} onSelect={setSelected} />}
        </div>
        <div className="app__detail">{selected ? <PromptDetailPanel prompt={selected} /> : <p className="empty-state">ターンを選択してください。</p>}</div>
      </div>
    </>
  );
}
