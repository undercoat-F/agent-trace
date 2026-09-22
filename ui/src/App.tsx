import { useEffect, useState } from "react";
import { fetchTools, searchSpans } from "./api";
import { FilterBar } from "./components/FilterBar";
import { SpanTable } from "./components/SpanTable";
import { SpanDetailPanel } from "./components/SpanDetailPanel";
import type { SpanRow, StatusFilter } from "./types";
import "./App.css";

// Simple debounce so free-text search doesn't fire a request per keystroke.
function useDebounced<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(t);
  }, [value, delayMs]);
  return debounced;
}

export default function App() {
  const [status, setStatus] = useState<StatusFilter>("");
  const [tool, setTool] = useState("");
  const [q, setQ] = useState("");
  const debouncedQ = useDebounced(q, 300);

  const [tools, setTools] = useState<string[]>([]);
  const [spans, setSpans] = useState<SpanRow[]>([]);
  const [selected, setSelected] = useState<SpanRow | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchTools().then(setTools).catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    setError(null);
    searchSpans({ status, tool, q: debouncedQ })
      .then((rows) => {
        setSpans(rows);
        setSelected((prev) => (prev && rows.some((r) => r.spanId === prev.spanId) ? prev : (rows[0] ?? null)));
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [status, tool, debouncedQ]);

  return (
    <div className="app">
      <header className="app__header">
        <h1>Agent Trace — Span Explorer</h1>
        <p className="app__subtitle">Claude Code / GitHub Copilot のツール呼び出しを検索する</p>
      </header>

      <FilterBar
        status={status}
        tool={tool}
        q={q}
        tools={tools}
        onStatusChange={setStatus}
        onToolChange={setTool}
        onQChange={setQ}
      />

      {error && <p className="error-state">{error}</p>}

      <div className="app__body">
        <div className="app__list">
          {loading ? <p className="loading-state">読み込み中…</p> : <SpanTable spans={spans} selected={selected} onSelect={setSelected} />}
        </div>
        <div className="app__detail">{selected ? <SpanDetailPanel span={selected} /> : <p className="empty-state">スパンを選択してください。</p>}</div>
      </div>
    </div>
  );
}
