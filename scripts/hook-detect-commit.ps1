<#
.SYNOPSIS
  PostToolUse hook: when a tool call ran `git commit`, tell ingest about the
  resulting commit so it can be linked back to the prompt that produced it.

.DESCRIPTION
  Claude Code's own OTel output never includes a git commit SHA (confirmed
  against real captures — see docs/agent-trace-concept.md §2/§4). This hook
  is the "補完手段" that section already calls for: detect the commit
  ourselves and correlate it by prompt_id/session_id, which — verified
  against a real controlled run — are the exact same UUIDs the hook payload
  and Claude Code's OTel events both carry for one turn.

  Best-effort only: if ingest is unreachable, this silently does nothing and
  never fails the tool call. That's acceptable because the existing
  PostToolUse hook (hook-log.jsonl) already durably captured the same
  git-commit tool_response regardless — a future replay tool could recover
  the linkage from there. This hook only saves having to build that replay
  tool before commit lookup is usable at all.

.NOTES
  Registered as an *additional* entry under PostToolUse in .claude/settings.json,
  alongside the existing hook-log.jsonl logger — both run, independently.
#>

$ErrorActionPreference = "SilentlyContinue"  # never let telemetry plumbing break the actual tool call

$IngestUrl = if ($env:AGENT_TRACE_INGEST_URL) { $env:AGENT_TRACE_INGEST_URL } else { "http://localhost:8081" }

$reader = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$input = $reader.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($input)) { exit 0 }

try {
    $payload = $input | ConvertFrom-Json
} catch {
    exit 0
}

$command = $payload.tool_input.command
if (-not $command -or $command -notmatch "git\s+commit") {
    exit 0
}

$cwd = $payload.cwd
if (-not $cwd -or -not (Test-Path $cwd)) { exit 0 }

Push-Location $cwd
try {
    $sha = (git rev-parse HEAD 2>$null)
    if (-not $sha) { exit 0 }  # command matched "git commit" but nothing was actually committed (e.g. --dry-run, nothing staged)

    $branch = (git rev-parse --abbrev-ref HEAD 2>$null)
    $message = (git log -1 --pretty=%B $sha 2>$null) -join "`n"
    # --root: without it, diff-tree produces no output for a repo's first (parentless) commit.
    $files = @(git diff-tree --no-commit-id --name-only -r --root $sha 2>$null)
    $committedAt = (git show -s --format=%cI $sha 2>$null)  # ISO 8601 with offset; the actual commit time, not "now"

    $body = @{
        sha         = $sha.Trim()
        branch      = $branch
        message     = $message
        files       = $files
        promptId    = $payload.prompt_id
        sessionId   = $payload.session_id
        committedAt = $committedAt
    } | ConvertTo-Json -Depth 5

    Invoke-RestMethod -Uri "$IngestUrl/v1/commits" -Method Post -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 5 | Out-Null
} finally {
    Pop-Location
}
