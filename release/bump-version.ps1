<#
.SYNOPSIS
  Bump the Chunklock version in the three places that have real consumers.

.DESCRIPTION
  Replaces the version string in pom.xml, plugin.yml and README.md, preserving
  each file's exact byte encoding, then shows the diff for review.

  WHY THIS SCRIPT EXISTS: the hand-typed commands this replaces used
  `Set-Content -Encoding utf8`, which behaves differently on the two PowerShells:

    PowerShell 7+ : utf8 = no BOM, and utf8BOM exists
    Windows PS 5.1: utf8 = WITH BOM, and utf8BOM does not exist at all

  This machine runs 5.1 (pwsh 7 is not installed). Running the old commands
  there stripped pom.xml's BOM and re-encoded every emoji in README.md,
  producing a 40-line diff on a file that should have changed one line.

  This script uses explicit .NET encoders instead, so it is correct on both.

.PARAMETER NewVersion
  The version to write, e.g. "3.0.0". Bare number, no leading "v".

.PARAMETER RepoRoot
  Repo root. Defaults to the parent of the folder holding this script.

.PARAMETER WhatIf
  Show what would change without writing anything.

.EXAMPLE
  .\release\bump-version.ps1 3.0.0
  .\release\bump-version.ps1 3.0.0 -WhatIf
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$NewVersion,

    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'

# Not a param default: $PSScriptRoot is not populated in the param block on
# Windows PowerShell 5.1, so it resolves to an empty string there.
if (-not $RepoRoot) {
    $here = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
    $RepoRoot = Split-Path -Parent $here
}

# Explicit encoders. The $true/$false argument is emitBOM.
$Utf8Bom   = New-Object System.Text.UTF8Encoding($true)
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Get-HasBom([string]$Path) {
    $head = [System.IO.File]::ReadAllBytes($Path)
    return ($head.Length -ge 3 -and $head[0] -eq 0xEF -and $head[1] -eq 0xBB -and $head[2] -eq 0xBF)
}

# Reads text, applies a regex, writes back with the file's ORIGINAL BOM state.
# ReadAllText auto-detects and strips a BOM into the string, so BOM state is
# carried separately and restored on write - never inferred from the content.
function Update-VersionInFile {
    param(
        [string]$Path,
        [string]$Pattern,
        [string]$Replacement,
        [string]$Label
    )

    if (-not (Test-Path $Path)) { throw "Not found: $Path" }
    $full = (Resolve-Path $Path).Path

    $hadBom  = Get-HasBom $full
    $text    = [System.IO.File]::ReadAllText($full)
    $updated = [regex]::Replace($text, $Pattern, $Replacement)

    if ($updated -eq $text) {
        Write-Warning "$Label - pattern did not match, nothing changed. Check the file by hand."
        return $false
    }

    if ($PSCmdlet.ShouldProcess($full, "write version $NewVersion")) {
        $enc = if ($hadBom) { $Utf8Bom } else { $Utf8NoBom }
        [System.IO.File]::WriteAllText($full, $updated, $enc)
        $bomNote = if ($hadBom) { 'BOM preserved' } else { 'no BOM, preserved' }
        Write-Host "  ok  $Label ($bomNote)" -ForegroundColor Green
    } else {
        Write-Host "  would update $Label" -ForegroundColor Yellow
    }
    return $true
}

Write-Host "Bumping Chunklock to $NewVersion" -ForegroundColor Cyan
Write-Host "Repo: $RepoRoot"
Write-Host ""

# 1. pom.xml <version> - the (?m) + two-space anchor keeps this off the ~10
#    dependency <version> tags, which are indented deeper.
$null = Update-VersionInFile `
    -Path (Join-Path $RepoRoot 'pom.xml') `
    -Pattern '(?m)(?<=^  <version>)[\d.]+(?=</version>)' `
    -Replacement $NewVersion `
    -Label 'pom.xml <version>'

# 2. plugin.yml - UTF-8 WITH BOM and CRLF. Both preserved: the BOM by the
#    encoder above, the CRLF by never splitting the string into lines.
$null = Update-VersionInFile `
    -Path (Join-Path $RepoRoot 'src\main\resources\plugin.yml') `
    -Pattern 'version: "[\d.]+"' `
    -Replacement ('version: "' + $NewVersion + '"') `
    -Label 'plugin.yml version'

# 3. README.md header. Note the trailing two spaces on that line are markdown
#    hard-breaks - the lookbehind stops before them, so they survive.
$null = Update-VersionInFile `
    -Path (Join-Path $RepoRoot 'README.md') `
    -Pattern '(?m)(?<=^\*\*Version\*\*: )[\d.]+' `
    -Replacement $NewVersion `
    -Label 'README.md header'

# pom.xml <name> should carry no number - finalName already derives it from
# ${project.version}. Cleared permanently in 2.3.0; this catches a reintroduction.
$pom = Join-Path $RepoRoot 'pom.xml'
if ((Get-Content $pom -Raw) -match '<name>Chunklock-[\d.]+</name>') {
    Write-Warning "pom.xml <name> has a hardcoded version again. It names nothing - clear it to <name>Chunklock</name>."
}

Write-Host ""
Write-Host "Diff - expect exactly 3 files and 3 changed lines:" -ForegroundColor Cyan

Push-Location $RepoRoot
try {
    & git diff --stat -- pom.xml src/main/resources/plugin.yml README.md
    Write-Host ""

    $changed = (& git diff --numstat -- pom.xml src/main/resources/plugin.yml README.md |
                ForEach-Object { ($_ -split "`t")[0] -as [int] } |
                Measure-Object -Sum).Sum

    # 3 = one version line per file. It was 4 until 2.3.0, when the hardcoded
    # number came out of pom.xml <name>; if that ever returns, the warning above
    # fires and this legitimately becomes 4 again.
    if ($changed -gt 3) {
        Write-Host ""
        Write-Warning "$changed added lines, expected 3. That usually means an encoding was not preserved."
        Write-Warning "Do not commit. Run: git checkout -- pom.xml src/main/resources/plugin.yml README.md"
    } elseif ($changed -eq 3) {
        Write-Host "Line count correct. Review the diff, then commit all three in ONE commit before tagging." -ForegroundColor Green
    }
} finally {
    Pop-Location
}
