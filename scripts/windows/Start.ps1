# YaPcore start (Windows) — Usage: .\Start.ps1 [-Gui] [-Fg]
param(
    [switch]$Gui,
    [switch]$Fg,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "Lib.ps1")

if ($Help) {
    Write-Host "Usage: .\Start.ps1 [-Gui] [-Fg]"
    Write-Host "  Headless by default. -Gui opens Swing control panel. -Fg runs in foreground."
    exit 0
}

$Root = Get-YapRoot $ScriptDir
$env:YAPCORE_HOME = $Root
Set-Location $Root

$Java = Require-YapJava
Read-YapConfig $Root
Ensure-YapDirs $Root
Require-YapPaperclip $Root

if (Test-YapRunning $Root) {
    Write-Error "YaPcore is already running (pid $(Get-YapPid $Root)). Use .\Stop.ps1 first."
    exit 1
}

$Jar = Find-YapJar $Root
if (-not $Jar) {
    Write-Error "No yapcore.jar found in $Root"
    exit 1
}

$Jvm = Get-YapJvmArgs $Root
$AppArgs = @()
if ($Gui) { $AppArgs += "--gui" } else { $AppArgs += "--nogui" }

$WorkDir = $Root
$Phase3 = $false
$c = $script:YapConfig
if ($c.GameAuthority -eq "paper" -and $c.PaperEmbed -and $c.PaperPhase3) {
    $WorkDir = Join-Path $Root $c.PaperDir
    if (-not (Test-Path $WorkDir)) { New-Item -ItemType Directory -Path $WorkDir | Out-Null }
    $Phase3 = $true
}

$LogDir = Join-Path $Root "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }
$LogFile = Join-Path $LogDir "server.log"

Write-Host "Starting YaPcore"
Write-Host "  home=$Root"
Write-Host "  cwd=$WorkDir"
if ($Phase3) { Write-Host "  phase3=true (cwd=paper-dir for Paperclip)" }
Write-Host "  java=$Java"
Write-Host "  jar=$Jar"
Write-Host "  heap=$($c.RamMinMb)m-$($c.RamMb)m"
Write-Host "  mode=$(if ($Gui) { 'gui' } else { 'nogui' })"

$allArgs = @($Jvm) + @("-jar", $Jar) + $AppArgs

if ($Fg -or $Gui) {
    Set-Location $WorkDir
    & $Java @allArgs
    exit $LASTEXITCODE
}

$argLine = ($allArgs | ForEach-Object {
    if ($_ -match '\s') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
}) -join " "

$proc = Start-Process -FilePath $Java `
    -ArgumentList $argLine `
    -WorkingDirectory $WorkDir `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError $LogFile `
    -WindowStyle Hidden `
    -PassThru

$proc.Id | Out-File -FilePath (Get-YapPidFile $Root) -Encoding ascii -NoNewline
Write-Host "Started in background pid=$($proc.Id)  log=$LogFile"
Write-Host "Stop with: .\Stop.ps1  (or stop.cmd)"
