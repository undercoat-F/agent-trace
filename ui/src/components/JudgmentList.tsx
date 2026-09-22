import type { JudgmentSummary } from "../types";
import { choiceOptionLabel, formatPercent, questionLabel } from "../format";

// rawAnswer's shape depends on the question type — see backend/ingest/.../jev/Answer.java.
interface NoulRaw {
  noul: number;
}
interface ChoiceRaw {
  choice: string;
  probabilities: Record<string, number>;
  confidence: number;
}
interface ScoreRaw {
  score: number;
  legend: Record<string, string>;
  probabilities: Record<string, number>;
  confidence: number;
}

export function JudgmentList({ judgments }: { judgments: JudgmentSummary[] }) {
  if (judgments.length === 0) {
    return <p className="empty-state">まだ判定されていません。</p>;
  }

  return (
    <div className="judgment-list">
      {judgments.map((j) => (
        <div key={j.questionId} className="judgment-card">
          <div className="judgment-card__question">{questionLabel(j.questionId)}</div>
          <JudgmentValue judgment={j} />
        </div>
      ))}
    </div>
  );
}

function JudgmentValue({ judgment }: { judgment: JudgmentSummary }) {
  const raw = judgment.rawAnswer as unknown;

  if (isNoul(raw)) {
    return (
      <div className="judgment-card__body">
        <span className="judgment-card__value">{formatPercent(raw.noul)}</span>
        <span className="muted">の確率でそう言える</span>
      </div>
    );
  }

  if (isChoice(raw)) {
    return (
      <div className="judgment-card__body">
        <span className="judgment-card__value">{choiceOptionLabel(raw.choice)}</span>
        <span className="muted"> (確信度 {formatPercent(raw.confidence)})</span>
        <ProbabilityBars probabilities={raw.probabilities} labelFor={choiceOptionLabel} />
      </div>
    );
  }

  if (isScore(raw)) {
    const level = Math.round(raw.score).toString();
    return (
      <div className="judgment-card__body">
        <span className="judgment-card__value">{raw.score.toFixed(1)}</span>
        <span className="muted"> (確信度 {formatPercent(raw.confidence)})</span>
        <p className="judgment-card__legend">{raw.legend[level] ?? ""}</p>
      </div>
    );
  }

  return <span className="muted">(不明な形式)</span>;
}

function ProbabilityBars({ probabilities, labelFor }: { probabilities: Record<string, number>; labelFor: (k: string) => string }) {
  const entries = Object.entries(probabilities).sort((a, b) => b[1] - a[1]);
  return (
    <ul className="probability-bars">
      {entries.map(([key, p]) => (
        <li key={key}>
          <span className="probability-bars__label">{labelFor(key)}</span>
          <span className="probability-bars__track">
            <span className="probability-bars__fill" style={{ width: `${p * 100}%` }} />
          </span>
          <span className="probability-bars__pct">{formatPercent(p)}</span>
        </li>
      ))}
    </ul>
  );
}

function isNoul(v: unknown): v is NoulRaw {
  return typeof v === "object" && v !== null && typeof (v as NoulRaw).noul === "number";
}
function isChoice(v: unknown): v is ChoiceRaw {
  return typeof v === "object" && v !== null && typeof (v as ChoiceRaw).choice === "string";
}
function isScore(v: unknown): v is ScoreRaw {
  return typeof v === "object" && v !== null && typeof (v as ScoreRaw).score === "number" && "legend" in (v as object);
}
