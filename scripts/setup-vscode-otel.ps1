<#
.SYNOPSIS
  Auto-append Copilot Chat OTel settings into the global user settings.json.
  These settings have "scope": "application" in the Copilot Chat extension,
  so they can only live in the global user settings, never in a workspace's
  .vscode/settings.json. This script automates that one-time global write.

.EXAMPLE
  .\setup-vscode-otel.ps1                # enabled + captureContent (OTLP to collector)
  .\setup-vscode-otel.ps1 -FileExport    # also exporterType=file, outfile=<root>\otel-data\copilot-otel.jsonl
  .\setup-vscode-otel.ps1 -Otlp          # undo -FileExport (back to the default OTLP export)

.NOTES
  - settings.json is JSONC (allows comments). To avoid corrupting comments or
    formatting, this script does NOT fully parse/re-serialize the file. It only
    inserts the missing keys right after the opening "{" as a minimal text diff.
  - If the keys already exist, nothing is changed (idempotent).
  - A timestamped .bak copy is created before any write.
#>

param(
    # Also write exporterType=file + outfile, so Copilot spans go to a local
    # JSONL file instead of the OTLP collector. Opt-in: with this on, Copilot
    # telemetry no longer reaches the collector.
    [switch]$FileExport,
    # Undo -FileExport: remove exporterType and outfile so Copilot falls back to
    # its default OTLP export to the collector.
    [switch]$Otlp,
    # Destination of the JSONL file. Default is derived from the project root,
    # so the repo can be moved to another machine without editing anything.
    [string]$OutFile
)

$ErrorActionPreference = "Stop"

if ($FileExport -and $Otlp) {
    Write-Error "-FileExport and -Otlp are mutually exclusive."
}

# Required OTel settings (key -> literal value text to insert)
$RequiredEntries = [ordered]@{
    "github.copilot.chat.otel.enabled"        = "true"
    "github.copilot.chat.otel.captureContent" = "true"
}

if ($FileExport) {
    if (-not $OutFile) {
        $OutFile = Join-Path (Split-Path -Parent $PSScriptRoot) "otel-data\copilot-otel.jsonl"
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutFile) | Out-Null
    # JSON string: forward slashes avoid backslash escaping
    $outFileJson = ($OutFile -replace '\\', '/')
    $RequiredEntries["github.copilot.chat.otel.exporterType"] = '"file"'
    $RequiredEntries["github.copilot.chat.otel.outfile"]      = '"' + $outFileJson + '"'
}

# Candidate paths for VS Code / Insiders / VSCodium user settings
$candidatePaths = @(
    "$env:APPDATA\Code\User\settings.json",
    "$env:APPDATA\Code - Insiders\User\settings.json",
    "$env:APPDATA\VSCodium\User\settings.json"
)

$targets = $candidatePaths | Where-Object { Test-Path (Split-Path $_ -Parent) }

if (-not $targets) {
    Write-Warning "Could not find a VS Code user settings folder. Add these manually to settings.json:"
    $RequiredEntries.GetEnumerator() | ForEach-Object { Write-Host "  `"$($_.Key)`": $($_.Value)," }
    exit 1
}

function Merge-OtelSettings {
    param([string]$SettingsPath)

    if (-not (Test-Path $SettingsPath)) {
        Write-Host "Creating: $SettingsPath"
        Set-Content -LiteralPath $SettingsPath -Value "{`n}`n" -Encoding utf8 -NoNewline:$false
    }

    $original = Get-Content -LiteralPath $SettingsPath -Raw -Encoding utf8

    $missing = [ordered]@{}
    foreach ($key in $RequiredEntries.Keys) {
        $pattern = [regex]::Escape('"' + $key + '"') + '\s*:'
        if ($original -notmatch $pattern) {
            $missing[$key] = $RequiredEntries[$key]
        } else {
            # Key exists: never overwrite, but flag a differing value instead of skipping silently
            $m = [regex]::Match($original, $pattern + '\s*([^,\r\n}]+)')
            $current = if ($m.Success) { $m.Groups[1].Value.Trim() } else { $null }
            if ($current -and $current -ne $RequiredEntries[$key]) {
                Write-Warning "$SettingsPath : '$key' is $current (expected $($RequiredEntries[$key])); left unchanged."
            }
        }
    }

    if ($missing.Count -eq 0) {
        Write-Host "Already configured, skipping: $SettingsPath"
        return
    }

    $backupPath = "$SettingsPath.bak-$(Get-Date -Format 'yyyyMMddHHmmss')"
    Copy-Item -LiteralPath $SettingsPath -Destination $backupPath
    Write-Host "Backup created: $backupPath"

    $firstBraceIndex = $original.IndexOf('{')
    if ($firstBraceIndex -lt 0) {
        Write-Error "$SettingsPath does not look like a JSON object. Please edit it manually."
        return
    }

    # Decide whether a trailing comma is needed, based on whether content
    # follows the opening brace (ignoring whitespace) before the closing brace.
    $afterBrace = $original.Substring($firstBraceIndex + 1)
    $hasExistingContent = ($afterBrace -match '\S') -and ($afterBrace.TrimStart() -notmatch '^\}')

    $lines = foreach ($kv in $missing.GetEnumerator()) {
        "  `"$($kv.Key)`": $($kv.Value),"
    }
    # No trailing newline: the original text after "{" already starts with one,
    # so this keeps insert/remove exact inverses (no stray blank lines).
    $snippet = ($lines -join "`n")
    if (-not $hasExistingContent) {
        # Empty object: drop the trailing comma on the last inserted key
        $snippet = $snippet.TrimEnd(',')
    }

    $updated = $original.Substring(0, $firstBraceIndex + 1) + "`n" + $snippet + $original.Substring($firstBraceIndex + 1)
    Set-Content -LiteralPath $SettingsPath -Value $updated -Encoding utf8 -NoNewline
    Write-Host "Updated: $SettingsPath ($($missing.Count) key(s) added)"
}

function Remove-FileExportSettings {
    param([string]$SettingsPath)

    $original = Get-Content -LiteralPath $SettingsPath -Raw -Encoding utf8
    # Whole single-line entries for exporterType / outfile (as inserted by -FileExport)
    $pattern = '(?m)^[ \t]*"github\.copilot\.chat\.otel\.(?:exporterType|outfile)"[ \t]*:[^\r\n]*(?:\r?\n|$)'

    if ($original -notmatch $pattern) {
        Write-Host "No file-export keys found, skipping: $SettingsPath"
        return
    }

    $backupPath = "$SettingsPath.bak-$(Get-Date -Format 'yyyyMMddHHmmss')"
    Copy-Item -LiteralPath $SettingsPath -Destination $backupPath
    Write-Host "Backup created: $backupPath"

    $updated = [regex]::Replace($original, $pattern, '')
    Set-Content -LiteralPath $SettingsPath -Value $updated -Encoding utf8 -NoNewline
    Write-Host "Updated: $SettingsPath (file-export keys removed; OTLP is the default)"
}

foreach ($t in $targets) {
    if ($Otlp) {
        if (Test-Path $t) { Remove-FileExportSettings -SettingsPath $t }
    } else {
        Merge-OtelSettings -SettingsPath $t
    }
}

Write-Host ""
Write-Host "Run 'Developer: Reload Window' in VS Code for the change to take effect."
