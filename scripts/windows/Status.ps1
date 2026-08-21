# YaPcore status (Windows)
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "Lib.ps1")

$Root = Get-YapRoot $ScriptDir
Read-YapConfig $Root
$c = $script:YapConfig

if (Test-YapRunning $Root) {
    Write-Host "YaPcore: RUNNING (pid $(Get-YapPid $Root))"
} else {
    Write-Host "YaPcore: STOPPED"
}
Write-Host "Config: max-players=$($c.MaxPlayers) ram=$($c.RamMinMb)-$($c.RamMb)MB port=$($c.Port)"
Write-Host "Home: $Root"
Write-Host "Web dashboard (if enabled): http://127.0.0.1:8080/"
