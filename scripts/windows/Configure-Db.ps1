#Requires -Version 5.1
<#
.SYNOPSIS
  Write / patch plugins/YaPDB/config.yml for the shared MariaDB pool.
  Optionally also patches YaPPlayerData when -ServerId is set.
#>
param(
  [string]$HostAddress = "127.0.0.1",
  [string]$ServerId = "",
  [string]$Profile = "global",
  [string]$Root = ""
)

$ErrorActionPreference = "Stop"

function Find-YapRoot {
  $dir = $PSScriptRoot
  for ($i = 0; $i -lt 6; $i++) {
    if (Test-Path (Join-Path $dir "deploy\mariadb\.env.example")) { return $dir }
    $parent = Split-Path -Parent $dir
    if (-not $parent -or $parent -eq $dir) { break }
    $dir = $parent
  }
  return (Get-Location).Path
}

$RepoRoot = Find-YapRoot
if ([string]::IsNullOrWhiteSpace($Root)) { $Root = $RepoRoot }
$ComposeDir = Join-Path $RepoRoot "deploy\mariadb"
$YapDbDir = Join-Path $Root "plugins\YaPDB"
$YapDbCfg = Join-Path $YapDbDir "config.yml"
$EnvFile = Join-Path $ComposeDir ".env"
$EnvExample = Join-Path $ComposeDir ".env.example"

if (-not (Test-Path $EnvFile)) {
  Copy-Item $EnvExample $EnvFile
}

$vars = @{}
Get-Content $EnvFile | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
  $p = $_.Split('=', 2)
  if ($p.Length -eq 2) { $vars[$p[0].Trim()] = $p[1].Trim() }
}
$port = if ($vars.ContainsKey("YAP_DB_PORT")) { $vars["YAP_DB_PORT"] } else { "3306" }
$db = if ($vars.ContainsKey("YAP_DB_NAME")) { $vars["YAP_DB_NAME"] } else { "yap_playerdata" }
$user = if ($vars.ContainsKey("YAP_DB_USER")) { $vars["YAP_DB_USER"] } else { "yap" }
$pass = if ($vars.ContainsKey("YAP_DB_PASSWORD")) { $vars["YAP_DB_PASSWORD"] } else { "change-me" }
$jdbc = "jdbc:mysql://${HostAddress}:${port}/${db}?useSSL=false&allowPublicKeyRetrieval=true"

New-Item -ItemType Directory -Force -Path $YapDbDir | Out-Null
$defaultCfg = Join-Path $RepoRoot "yap-db-plugin\src\main\resources\config.yml"
if (-not (Test-Path $YapDbCfg)) {
  if (Test-Path $defaultCfg) {
    Copy-Item $defaultCfg $YapDbCfg
  } else {
    @"
jdbc:
  url: $jdbc
  user: $user
  password: $pass
pool:
  name: YaPDB
  maximum-pool-size: 16
  minimum-idle: 2
  connection-timeout-ms: 10000
"@ | Set-Content -Encoding UTF8 $YapDbCfg
  }
}

$text = Get-Content -Raw $YapDbCfg
$text = [regex]::Replace($text, '(?m)^(  url:\s*).*$', "`${1}$jdbc", 1)
$text = [regex]::Replace($text, '(?m)^(  user:\s*).*$', "`${1}$user", 1)
$text = [regex]::Replace($text, '(?m)^(  password:\s*).*$', "`${1}$pass", 1)
Set-Content -Encoding UTF8 -Path $YapDbCfg -Value $text -NoNewline

Write-Host "Updated $YapDbCfg"
Write-Host "  url: $jdbc"

if ($ServerId -ne "") {
  $playerScript = Join-Path $PSScriptRoot "Configure-PlayerData.ps1"
  if (Test-Path $playerScript) {
    & $playerScript -HostAddress $HostAddress -ServerId $ServerId -Profile $Profile -Root $Root
  }
}

Write-Host "Restart YaPcore so YaPDB connects."
