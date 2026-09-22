import { useEffect, useState } from "react";
import { fetchBranches, fetchCommits } from "../api";
import { CommitTable } from "../components/CommitTable";
import { CommitDetailPanel } from "../components/CommitDetailPanel";
import type { BranchSummary, CommitSummary } from "../types";

export function CommitsView() {
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [branch, setBranch] = useState("");
  const [commits, setCommits] = useState<CommitSummary[]>([]);
  const [selected, setSelected] = useState<CommitSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchBranches()
      .then((rows) => {
        setBranches(rows);
        setBranch((prev) => prev || rows[0]?.name || "");
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    if (!branch) return;
    setLoading(true);
    setError(null);
    fetchCommits(branch)
      .then((rows) => {
        setCommits(rows);
        setSelected((prev) => (prev && rows.some((r) => r.sha === prev.sha) ? prev : (rows[0] ?? null)));
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [branch]);

  return (
    <>
      <div className="filter-bar">
        <select value={branch} onChange={(e) => setBranch(e.target.value)} aria-label="ブランチを選択">
          {branches.length === 0 && <option value="">(ブランチが見つかりません)</option>}
          {branches.map((b) => (
            <option key={b.name} value={b.name}>
              {b.name}
            </option>
          ))}
        </select>
      </div>

      {error && <p className="error-state">{error}</p>}

      <div className="app__body">
        <div className="app__list">
          {loading ? <p className="loading-state">読み込み中…</p> : <CommitTable commits={commits} selected={selected} onSelect={setSelected} />}
        </div>
        <div className="app__detail">{selected ? <CommitDetailPanel commit={selected} /> : <p className="empty-state">コミットを選択してください。</p>}</div>
      </div>
    </>
  );
}
