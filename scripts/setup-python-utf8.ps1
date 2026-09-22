<#
.SYNOPSIS
  Force Python's stdio to UTF-8, regardless of the Windows console codepage.

.DESCRIPTION
  On Windows, Python's sys.stdout/stdin default to the console's active
  codepage (often cp932 on a Japanese-locale PC), not UTF-8, unless told
  otherwise. This bit us once in this project: `python -m json.tool` on a
  perfectly valid UTF-8 API response showed mojibake (e.g. "…" as garbage),
  which looked like a real encoding bug in the app but wasn't — only the
  diagnostic script's own output was mis-decoded.

  PYTHONUTF8=1 (PEP 540, Python 3.7+) is the targeted fix: it forces UTF-8
  mode for Python specifically, without touching the system-wide console
  codepage (`chcp`), which would affect every other legacy console app too.
  PYTHONIOENCODING=utf-8 is set alongside it as a belt-and-suspenders for
  tools/older Python paths that check that variable instead.

  User-level env vars (no admin elevation needed), so this is safe to run
  as part of routine setup on any machine.

.EXAMPLE
  .\setup-python-utf8.ps1
#>

$ErrorActionPreference = "Stop"

$RequiredEntries = [ordered]@{
    PYTHONUTF8       = "1"
    PYTHONIOENCODING = "utf-8"
}

$changed = 0
foreach ($key in $RequiredEntries.Keys) {
    $current = [Environment]::GetEnvironmentVariable($key, "User")
    $desired = $RequiredEntries[$key]
    if ($current -eq $desired) {
        continue
    }
    if ($current -and $current -ne $desired) {
        Write-Warning "User env var $key is '$current' (expected '$desired'); overwriting."
    }
    [Environment]::SetEnvironmentVariable($key, $desired, "User")
    Write-Host "Set user env var: $key=$desired"
    $changed++
}

if ($changed -eq 0) {
    Write-Host "Already configured, skipping."
} else {
    Write-Host ""
    Write-Host "New terminals will pick this up. Already-open terminals (including this one) will not."
}
