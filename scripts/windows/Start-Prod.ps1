# YaPcore production profile (Windows) — large pinned heap + ZGC (no numactl on Windows)
param(
    [switch]$Gui,
    [switch]$Fg,
    [int]$HeapGb = 12,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "Lib.ps1")

if ($Help) {
    Write-Host "Usage: .\Start-Prod.ps1 [-Gui] [-Fg] [-HeapGb N]"
    exit 0
}

if ($env:YAPCORE_PROD_HEAP_GB) {
    $HeapGb = [int]$env:YAPCORE_PROD_HEAP_GB
}

$Root = Get-YapRoot $ScriptDir
$env:YAPCORE_HOME = $Root
Read-YapConfig $Root
$mb = $HeapGb * 1024
$script:YapConfig.RamMb = $mb
$script:YapConfig.RamMinMb = $mb
$script:YapConfig.JvmGc = "zgc"
$script:YapConfig.JvmNuma = $true
$script:YapConfig.JvmHeapPin = $true
$script:YapConfig.JvmThreadPriority = $true

# Re-enter Start with updated config in this session by inlining call
$Java = Require-YapJava
Ensure-YapDirs $Root

if (Test-YapRunning $Root) {
    Write-Error "YaPcore is already running. Use .\Stop.ps1 first."
    exit 1
}

$Jar = Find-YapJar $Root
if (-not $Jar) {
    Write-Error "No yapcore.jar found"
    exit 1
}

$Jvm = Get-YapJvmArgs $Root
$AppArgs = @()
if ($Gui) { $AppArgs += "--gui" } else { $AppArgs += "--nogui" }

$WorkDir = $Root
$c = $script:YapConfig

Write-Host "Production start heap=${HeapGb}G ZGC game-authority=$($c.GameAuthority) kernel=$(Get-YapActiveKernelDir)"
$allArgs = @($Jvm) + @("-jar", $Jar) + $AppArgs

if ($Fg -or $Gui) {
    Set-Location $WorkDir
    & $Java @allArgs
    exit $LASTEXITCODE
}

$LogFile = Join-Path $Root "logs\server.log"
$argLine = ($allArgs | ForEach-Object {
    if ($_ -match '\s') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
}) -join " "
$proc = Start-Process -FilePath $Java -ArgumentList $argLine -WorkingDirectory $WorkDir `
    -RedirectStandardOutput $LogFile -RedirectStandardError $LogFile -WindowStyle Hidden -PassThru
$proc.Id | Out-File -FilePath (Get-YapPidFile $Root) -Encoding ascii -NoNewline
Write-Host "Started pid=$($proc.Id)"
