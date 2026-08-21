# Clone / update vendor/paper to vendor/paper.pin (Windows)
# Requires: Git on PATH
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "Lib.ps1")

$Root = Get-YapRoot $ScriptDir
$Pin = Join-Path $Root "vendor\paper.pin"
$Dest = Join-Path $Root "vendor\paper"

if (-not (Test-Path $Pin)) {
    Write-Error "Missing $Pin"
    exit 1
}

function Read-Pin([string]$key) {
    $line = Get-Content $Pin | Where-Object { $_ -match "^$key=" } | Select-Object -First 1
    if (-not $line) { return "" }
    return $line.Substring($line.IndexOf("=") + 1).Trim()
}

$commit = Read-Pin "commit"
$repo = Read-Pin "repo"
$build = Read-Pin "build"
$mc = Read-Pin "mc"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Error "Git not found on PATH. Install Git for Windows."
    exit 1
}

New-Item -ItemType Directory -Force -Path (Join-Path $Root "vendor") | Out-Null
if (-not (Test-Path (Join-Path $Dest ".git"))) {
    Write-Host "Cloning Paper → $Dest (commit $commit, $mc #$build)"
    git clone --filter=blob:none $repo $Dest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Push-Location $Dest
try {
    git fetch --depth 1 origin $commit 2>$null
    if ($LASTEXITCODE -ne 0) { git fetch origin $commit }
    git checkout --force $commit
    git reset --hard $commit
    $short = (git rev-parse --short HEAD).Trim()
    Write-Host "vendor/paper @ $short ($mc build $build)"
    Write-Host "Next: .\scripts\Build-Vendor-Paper.ps1"
} finally {
    Pop-Location
}
