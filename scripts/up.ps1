<#
.SYNOPSIS
  One-shot startup for agent-trace:
    1. Auto-append the Copilot OTel settings to the VS Code user settings.json
       (skipped if already present).
    2. Auto-append Claude Code's own OTel env vars to ~/.claude/settings.json
       (skipped if already present).
    3. Force Python's stdio to UTF-8 (PYTHONUTF8/PYTHONIOENCODING), so ad-hoc
       `python` diagnostics of API/DB output don't show mojibake on a
       non-UTF-8-locale Windows PC — see setup-python-utf8.ps1 for why.
    4. docker compose up -d to start the collector and other background services.
#>

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

& "$PSScriptRoot\setup-vscode-otel.ps1"
& "$PSScriptRoot\setup-claude-otel.ps1"
& "$PSScriptRoot\setup-python-utf8.ps1"

Push-Location $root
try {
    python .\infra\otel-collector\build_collector_config.py
    docker compose up -d
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Startup complete. Run 'Developer: Reload Window' in VS Code before using Copilot Chat."
Write-Host "Agent data directory: $root\otel-data"
