import { useState } from "react";
import { SpansView } from "./views/SpansView";
import { PromptsView } from "./views/PromptsView";
import { CommitsView } from "./views/CommitsView";
import "./App.css";

type Tab = "spans" | "prompts" | "commits";

export default function App() {
  const [tab, setTab] = useState<Tab>("spans");

  return (
    <div className="app">
      <header className="app__header">
        <h1>Agent Trace</h1>
        <p className="app__subtitle">Claude Code / GitHub Copilot のツール呼び出しを検索する</p>
      </header>

      <nav className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === "spans"} className={tab === "spans" ? "is-active" : ""} onClick={() => setTab("spans")}>
          Copilot(スパン)
        </button>
        <button role="tab" aria-selected={tab === "prompts"} className={tab === "prompts" ? "is-active" : ""} onClick={() => setTab("prompts")}>
          Claude Code(ターン)
        </button>
        <button role="tab" aria-selected={tab === "commits"} className={tab === "commits" ? "is-active" : ""} onClick={() => setTab("commits")}>
          コミット逆引き
        </button>
      </nav>

      {tab === "spans" && <SpansView />}
      {tab === "prompts" && <PromptsView />}
      {tab === "commits" && <CommitsView />}
    </div>
  );
}
