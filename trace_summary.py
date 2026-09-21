#!/usr/bin/env python3
"""hook-log.jsonl を 1ターン(prompt_id)ごとに要約する。

使い方:
    python trace_summary.py hook-log.jsonl
    python trace_summary.py hook-log.jsonl --last 3 --out summary.md
    python trace_summary.py hook-log.jsonl --format json --out turns.json

標準ライブラリだけで動く。関数は副作用が無いので、そのまま import して使える。
    from trace_summary import load_events, build_turns
"""
from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path

WRITE_TOOLS = {"Write", "Edit", "MultiEdit", "NotebookEdit"}
STATUS_LABEL = {"ok": "OK", "failed": "失敗", "no_result": "結果なし"}


# ---------------------------------------------------------------- データ構造
@dataclass
class ToolCall:
    tool_use_id: str
    tool_name: str
    target: str = ""              # ファイルパス or コマンド
    status: str = "no_result"     # ok / failed / no_result
    error: str = ""
    duration_ms: int | None = None


@dataclass
class Turn:
    prompt_id: str
    session_id: str = ""
    cwd: str = ""
    prompt: str = ""
    final_message: str = ""
    tools: list[ToolCall] = field(default_factory=list)
    commits: list[dict] = field(default_factory=list)


# ---------------------------------------------------------------- 読み込み
def load_events(path: Path) -> tuple[list[dict], int]:
    """JSON Lines を読む。読めなかった行数も返す。"""
    events: list[dict] = []
    bad = 0
    with path.open(encoding="utf-8-sig", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                ev = json.loads(line)
            except json.JSONDecodeError:
                bad += 1
                continue
            if isinstance(ev, dict):
                events.append(ev)
            else:
                bad += 1
    return events, bad


#  組み立て----------------------------------------------------------------
def describe_target(tool_input) -> str:
    if not isinstance(tool_input, dict):
        return ""
    if "file_path" in tool_input:
        return str(tool_input["file_path"])
    if "command" in tool_input:
        return " ".join(str(tool_input["command"]).split())
    return ""


def find_commit(tool_response) -> dict | None:
    """PostToolUse の tool_response.gitOperation.commit を取り出す。"""
    if not isinstance(tool_response, dict):
        return None
    op = tool_response.get("gitOperation")
    if isinstance(op, dict) and isinstance(op.get("commit"), dict):
        return op["commit"]
    return None


def build_turns(events: list[dict]) -> list[Turn]:
    """prompt_id で束ね、tool_use_id で Pre/Post を突き合わせる。"""
    turns: dict[str, Turn] = {}
    calls: dict[str, ToolCall] = {}

    for ev in events:
        pid = ev.get("prompt_id") or "(none)"
        turn = turns.setdefault(pid, Turn(prompt_id=pid))
        turn.session_id = turn.session_id or ev.get("session_id", "")
        turn.cwd = turn.cwd or ev.get("cwd", "")
        name = ev.get("hook_event_name")

        if name == "UserPromptSubmit":
            turn.prompt = ev.get("prompt", "")
        elif name == "Stop":
            turn.final_message = ev.get("last_assistant_message", "")
        elif name in ("PreToolUse", "PostToolUse", "PostToolUseFailure"):
            tid = ev.get("tool_use_id") or f"{pid}-{name}-{len(turn.tools)}"
            call = calls.get(tid)
            if call is None:
                call = ToolCall(
                    tool_use_id=tid,
                    tool_name=ev.get("tool_name", ""),
                    target=describe_target(ev.get("tool_input")),
                )
                calls[tid] = call
                turn.tools.append(call)
            if name == "PostToolUse":
                call.status = "ok"
                call.duration_ms = ev.get("duration_ms")
                commit = find_commit(ev.get("tool_response"))
                if commit:
                    turn.commits.append(commit)
            elif name == "PostToolUseFailure":
                call.status = "failed"
                call.error = str(ev.get("error", ""))
                call.duration_ms = ev.get("duration_ms")

    return list(turns.values())


def totals(turns: list[Turn]) -> dict:
    calls = [c for t in turns for c in t.tools]
    return {
        "turns": len(turns),
        "tool_calls": len(calls),
        "failed": sum(c.status == "failed" for c in calls),
        "no_result": sum(c.status == "no_result" for c in calls),
        "commits": sum(len(t.commits) for t in turns),
    }


# ---------------------------------------------------------------- 出力
def clip(text, limit: int) -> str:
    s = " ".join(str(text).split())
    return s if len(s) <= limit else s[: limit - 1] + "…"


def cell(text: str) -> str:
    return text.replace("|", "\\|")


def render_markdown(turns: list[Turn], bad_lines: int = 0) -> str:
    tot = totals(turns)
    out = [
        "# Trace summary",
        "",
        f"- ターン: {tot['turns']} / ツール呼び出し: {tot['tool_calls']}"
        f" / 失敗: {tot['failed']} / 結果なし: {tot['no_result']}"
        f" / コミット: {tot['commits']}",
    ]
    if bad_lines:
        out.append(f"- 読めなかった行: {bad_lines}")
    out.append("")

    for i, t in enumerate(turns, 1):
        out += [
            f"## Turn {i}  `{t.prompt_id[:8]}`",
            "",
            f"- session: `{t.session_id[:8]}` / cwd: `{t.cwd}`",
            "",
            f"**Prompt:** {clip(t.prompt, 300)}",
            "",
        ]
        if t.tools:
            out += ["| # | tool | target | 結果 | ms |", "|---|---|---|---|---|"]
            for n, c in enumerate(t.tools, 1):
                status = STATUS_LABEL.get(c.status, c.status)
                if c.error:
                    status += f": {clip(c.error, 60)}"
                ms = "" if c.duration_ms is None else c.duration_ms
                out.append(
                    f"| {n} | {c.tool_name} | {cell(clip(c.target, 80))}"
                    f" | {cell(status)} | {ms} |"
                )
            out.append("")
            written = sorted({c.target for c in t.tools
                              if c.tool_name in WRITE_TOOLS and c.target})
            if written:
                out.append("**書き込んだファイル:** " + ", ".join(f"`{p}`" for p in written))
                out.append("")
        if t.commits:
            out.append("**Commits:** " + ", ".join(
                f"`{c.get('sha', '?')}` ({c.get('branch', '?')})" for c in t.commits))
            out.append("")
        if t.final_message:
            out += [f"**Final message:** {clip(t.final_message, 400)}", ""]

    out += [
        "---",
        "「結果なし」= PreToolUse だけがあり、Post 系が無い呼び出し。"
        "実行前に止められた可能性がある（OTel の tool_decision で裏を取る）。",
    ]
    return "\n".join(out)


def render_json(turns: list[Turn], bad_lines: int = 0) -> str:
    return json.dumps(
        {"totals": totals(turns), "bad_lines": bad_lines,
         "turns": [asdict(t) for t in turns]},
        ensure_ascii=False, indent=2)


# CLI---------------------------------------------------------------- 
def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="hook-log.jsonl を 1ターンごとに要約する")
    ap.add_argument("logfile", nargs="?", default="hook-log.jsonl")
    ap.add_argument("--format", choices=["md", "json"], default="md")
    ap.add_argument("--last", type=int, help="最新の N ターンだけ表示")
    ap.add_argument("--prompt-id", help="この prompt_id（前方一致）のターンだけ表示")
    ap.add_argument("--out", help="出力先ファイル（UTF-8）。省略すると画面に出す")
    args = ap.parse_args(argv)

    path = Path(args.logfile)
    if not path.exists():
        print(f"ファイルが見つかりません: {path}", file=sys.stderr)
        return 1

    events, bad = load_events(path)
    turns = build_turns(events)
    if args.prompt_id:
        turns = [t for t in turns if t.prompt_id.startswith(args.prompt_id)]
    if args.last:
        turns = turns[-args.last:]

    render = render_markdown if args.format == "md" else render_json
    text = render(turns, bad)

    if args.out:
        Path(args.out).write_text(text + "\n", encoding="utf-8")
        print(f"wrote {args.out}")
    else:
        if hasattr(sys.stdout, "reconfigure"):
            sys.stdout.reconfigure(errors="replace")
        print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
