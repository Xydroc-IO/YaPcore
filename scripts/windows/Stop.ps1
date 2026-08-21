# YaPcore stop (Windows) — Usage: .\Stop.ps1 [-Force]
param(
    [switch]$Force,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "Lib.ps1")

if ($Help) {
    Write-Host "Usage: .\Stop.ps1 [-Force]"
    exit 0
}

$Root = Get-YapRoot $ScriptDir
$pidFile = Get-YapPidFile $Root
$procId = Get-YapPid $Root

if (-not $procId) {
    Write-Host "YaPcore does not appear to be running."
    if (Test-Path $pidFile) { Remove-Item $pidFile -Force -ErrorAction SilentlyContinue }
    exit 0
}

Write-Host "Stopping YaPcore (pid $procId)..."
try {
    $p = Get-Process -Id ([int]$procId) -ErrorAction Stop
    if ($Force) {
        Stop-Process -Id $p.Id -Force
    } else {
        Stop-Process -Id $p.Id
        $deadline = (Get-Date).AddSeconds(20)
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Milliseconds 500
            if (-not (Get-Process -Id $p.Id -ErrorAction SilentlyContinue)) { break }
        }
        if (Get-Process -Id $p.Id -ErrorAction SilentlyContinue) {
            Write-Host "Graceful stop timed out; forcing"
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        }
    }
} catch {
    Write-Host "Process already gone."
}

Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
Write-Host "YaPcore stopped."
