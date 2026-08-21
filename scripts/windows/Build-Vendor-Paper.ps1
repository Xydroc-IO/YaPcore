# Build vendored Paperclip → lib/paper-<mc>-yap.jar (Windows)
# Requires: Git, JDK 25+, vendor/paper (Vendor-Paper.ps1), Git Bash for apply-yap-paper-hooks.sh
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "Lib.ps1")

$Root = Get-YapRoot $ScriptDir
$Pin = Join-Path $Root "vendor\paper.pin"
$Dest = Join-Path $Root "vendor\paper"
$PatchHelper = Join-Path $Root "scripts\apply-yap-paper-hooks.sh"

if (-not (Test-Path (Join-Path $Dest ".git"))) {
    Write-Error "vendor\paper missing — run .\scripts\Vendor-Paper.ps1 first"
    exit 1
}

function Read-Pin([string]$key) {
    $line = Get-Content $Pin | Where-Object { $_ -match "^$key=" } | Select-Object -First 1
    if (-not $line) { return "" }
    return $line.Substring($line.IndexOf("=") + 1).Trim()
}

$mc = Read-Pin "mc"
$artifactRel = Read-Pin "artifact"
$Artifact = Join-Path $Root ($artifactRel -replace '/', '\')

$gradlew = Join-Path $Dest "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Write-Error "Missing $gradlew"
    exit 1
}

Require-YapJava | Out-Null
$env:GRADLE_OPTS = if ($env:GRADLE_OPTS) { $env:GRADLE_OPTS } else { "-Xmx4g" }

Push-Location $Dest
try {
    Write-Host "Applying Paper patches…"
    & .\gradlew.bat paper-server:applyPatches --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    if (Test-Path $PatchHelper) {
        $bash = $null
        foreach ($c in @(
            "bash",
            "${env:ProgramFiles}\Git\bin\bash.exe",
            "${env:ProgramFiles(x86)}\Git\bin\bash.exe",
            "C:\Program Files\Git\bin\bash.exe"
        )) {
            if ($c -eq "bash") {
                $cmd = Get-Command bash -ErrorAction SilentlyContinue
                if ($cmd) { $bash = $cmd.Source; break }
            } elseif (Test-Path $c) {
                $bash = $c; break
            }
        }
        if (-not $bash) {
            Write-Error "Git Bash not found — required to run scripts/apply-yap-paper-hooks.sh on Windows. Install Git for Windows."
            exit 1
        }
        Write-Host "Applying YaP Paper hooks via $bash …"
        & $bash $PatchHelper
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }

    Write-Host "Building Paperclip…"
    & .\gradlew.bat paper-server:createPaperclipJar --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

$libs = Join-Path $Dest "paper-server\build\libs"
$jar = Get-ChildItem $libs -Filter "paper-paperclip-*.jar" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
    $jar = Get-ChildItem $libs -Filter "paper-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc|bundler|paper-server-' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}
if (-not $jar) {
    Write-Error "Could not locate Paperclip jar under $libs"
    exit 1
}

New-Item -ItemType Directory -Force -Path (Split-Path $Artifact) | Out-Null
Copy-Item $jar.FullName $Artifact -Force
$pk = Join-Path $Root "paper-kernel"
New-Item -ItemType Directory -Force -Path $pk | Out-Null
Copy-Item $Artifact (Join-Path $pk "paper-$mc.jar") -Force
Write-Host "Installed vendored Paperclip → $Artifact ($((Get-Item $Artifact).Length) bytes)"
Write-Host "Also staged → paper-kernel\paper-$mc.jar"
