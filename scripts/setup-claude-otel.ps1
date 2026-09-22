<#
.SYNOPSIS
  Idempotently manage Claude Code's own OTel telemetry env vars in the global
  ~/.claude/settings.json "env" block, so every future `claude` launch sends
  traces/logs to the local collector without needing per-shell `export`.

.DESCRIPTION
  Claude Code reads env vars from settings.json's "env" key on every launch
  (this takes precedence over shell-exported vars). See
  https://code.claude.com/docs/en/monitoring-usage and
  https://code.claude.com/docs/en/env-vars.

  PRIVACY: OTEL_LOG_USER_PROMPTS / OTEL_LOG_ASSISTANT_RESPONSES /
  OTEL_LOG_TOOL_DETAILS send prompt text, response text, and tool arguments to
  the configured OTLP endpoint. Only enable these if that endpoint is trusted
  (here: the local collector, bound to 127.0.0.1 only).

  This script only ever touches the specific keys it manages (see
  $ManagedKeys below). Other "env" entries, and all other settings.json keys,
  are left exactly as found. A timestamped .bak copy is made before any write.

  Uses PSCustomObject (not -AsHashtable: not available on Windows PowerShell
  5.1, the shell this repo targets) to parse/merge, so comments would NOT be
  preserved if this file ever had any — it doesn't today (plain JSON).

.EXAMPLE
  .\setup-claude-otel.ps1        # apply (idempotent)
  .\setup-claude-otel.ps1 -Off   # remove exactly the keys this script adds
#>

param(
    [switch]$Off
)

$ErrorActionPreference = "Stop"

# The exact env block from docs/agent-trace-concept.md §9. Values are strings:
# settings.json's "env" values must be strings, not JSON booleans/numbers.
$ManagedKeys = [ordered]@{
    CLAUDE_CODE_ENABLE_TELEMETRY  = "1"
    OTEL_LOGS_EXPORTER            = "otlp"
    OTEL_EXPORTER_OTLP_PROTOCOL   = "http/protobuf"
    OTEL_EXPORTER_OTLP_ENDPOINT   = "http://localhost:4318"
    OTEL_LOG_USER_PROMPTS         = "1"
    OTEL_LOG_ASSISTANT_RESPONSES  = "1"
    OTEL_LOG_TOOL_DETAILS         = "1"
}

$SettingsPath = Join-Path $env:USERPROFILE ".claude\settings.json"

function Backup-Settings {
    param([string]$Path)
    $backupPath = "$Path.bak-$(Get-Date -Format 'yyyyMMddHHmmss')"
    Copy-Item -LiteralPath $Path -Destination $backupPath
    Write-Host "Backup created: $backupPath"
}

function Test-HasProperty {
    param($Object, [string]$Name)
    return $null -ne $Object.PSObject.Properties[$Name]
}

if (-not (Test-Path $SettingsPath)) {
    if ($Off) {
        Write-Host "No settings file at $SettingsPath, nothing to remove."
        exit 0
    }
    Write-Host "Creating: $SettingsPath"
    New-Item -ItemType Directory -Force -Path (Split-Path $SettingsPath -Parent) | Out-Null
    Set-Content -LiteralPath $SettingsPath -Value "{`n}`n" -Encoding utf8
}

# settings.json here is plain JSON (no comments observed), so a real
# parse/merge is safe and keeps every unrelated key exactly as it was.
$raw = Get-Content -LiteralPath $SettingsPath -Raw -Encoding utf8
$json = if ([string]::IsNullOrWhiteSpace($raw)) { [PSCustomObject]@{} } else { $raw | ConvertFrom-Json }

if ($Off) {
    if (-not (Test-HasProperty $json "env")) {
        Write-Host "No 'env' block, nothing to remove."
        exit 0
    }
    $removed = 0
    foreach ($key in $ManagedKeys.Keys) {
        if (Test-HasProperty $json.env $key) {
            $json.env.PSObject.Properties.Remove($key)
            $removed++
        }
    }
    if ($removed -eq 0) {
        Write-Host "None of the managed keys were present, nothing to remove."
        exit 0
    }
    if (@($json.env.PSObject.Properties).Count -eq 0) {
        $json.PSObject.Properties.Remove("env")
    }
    Backup-Settings -Path $SettingsPath
    ($json | ConvertTo-Json -Depth 20) | Set-Content -LiteralPath $SettingsPath -Encoding utf8
    Write-Host "Removed $removed managed key(s) from: $SettingsPath"
    exit 0
}

if (-not (Test-HasProperty $json "env")) {
    $json | Add-Member -NotePropertyName "env" -NotePropertyValue ([PSCustomObject]@{})
}

$toAdd = [ordered]@{}
foreach ($key in $ManagedKeys.Keys) {
    if (Test-HasProperty $json.env $key) {
        $current = $json.env.$key
        if ($current -ne $ManagedKeys[$key]) {
            Write-Warning "$SettingsPath : env.$key is '$current' (expected '$($ManagedKeys[$key])'); left unchanged."
        }
    } else {
        $toAdd[$key] = $ManagedKeys[$key]
    }
}

if ($toAdd.Count -eq 0) {
    Write-Host "Already configured, skipping: $SettingsPath"
    exit 0
}

Backup-Settings -Path $SettingsPath
foreach ($key in $toAdd.Keys) {
    $json.env | Add-Member -NotePropertyName $key -NotePropertyValue $toAdd[$key]
}
($json | ConvertTo-Json -Depth 20) | Set-Content -LiteralPath $SettingsPath -Encoding utf8
Write-Host "Updated: $SettingsPath ($($toAdd.Count) key(s) added under env)"
Write-Host ""
Write-Host "Takes effect on the next 'claude' launch (no restart of an already-running session needed for future ones)."
