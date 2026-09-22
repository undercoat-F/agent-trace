import { useEffect, useState } from "react";
import { fetchTools, searchSpans } from "../api";
import { FilterBar } from "../components/FilterBar";
import { SpanTable } from "../components/SpanTable";
import { SpanDetailPanel } from "../components/SpanDetailPanel";
import type { SpanRow, StatusFilter } from "../types";
import { useDebounced } from "../useDebounced";

export function SpansView() {
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
    <>
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
    </>
  );
}
