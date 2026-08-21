# YaPcore GUI (Windows) — foreground Swing control panel
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $ScriptDir "Start.ps1") -Gui -Fg
