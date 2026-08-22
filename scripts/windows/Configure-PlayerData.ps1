#Requires -Version 5.1
<#
.SYNOPSIS
  Write / patch plugins/YaPPlayerData/config.yml for MariaDB.
#>
param(
  [string]$HostAddress = "127.0.0.1",
  [string]$ServerId = "lobby",
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
$PluginDir = Join-Path $Root "plugins\YaPPlayerData"
$Config = Join-Path $PluginDir "config.yml"
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

New-Item -ItemType Directory -Force -Path $PluginDir | Out-Null
$defaultCfg = Join-Path $RepoRoot "playerdata-plugin\src\main\resources\config.yml"
if (-not (Test-Path $Config)) {
  if (Test-Path $defaultCfg) {
    Copy-Item $defaultCfg $Config
  } else {
    @"
server-id: $ServerId
inventory-profile: $Profile
jdbc:
  url: $jdbc
  user: $user
  password: $pass
"@ | Set-Content -Encoding UTF8 $Config
  }
}

$text = Get-Content -Raw $Config
$text = [regex]::Replace($text, '(?m)^(server-id:\s*).*$', "`${1}$ServerId", 1)
$text = [regex]::Replace($text, '(?m)^(inventory-profile:\s*).*$', "`${1}$Profile", 1)
$text = [regex]::Replace($text, '(?m)^(  url:\s*).*$', "`${1}$jdbc", 1)
$text = [regex]::Replace($text, '(?m)^(  user:\s*).*$', "`${1}$user", 1)
$text = [regex]::Replace($text, '(?m)^(  password:\s*).*$', "`${1}$pass", 1)
Set-Content -Encoding UTF8 -Path $Config -Value $text -NoNewline

# Also patch shared YaPDB
$yapDbDir = Join-Path $Root "plugins\YaPDB"
$yapDbCfg = Join-Path $yapDbDir "config.yml"
New-Item -ItemType Directory -Force -Path $yapDbDir | Out-Null
$defaultYapDb = Join-Path $RepoRoot "yap-db-plugin\src\main\resources\config.yml"
if (-not (Test-Path $yapDbCfg) -and (Test-Path $defaultYapDb)) {
  Copy-Item $defaultYapDb $yapDbCfg
}
if (Test-Path $yapDbCfg) {
  $yd = Get-Content -Raw $yapDbCfg
  $yd = [regex]::Replace($yd, '(?m)^(  url:\s*).*$', "`${1}$jdbc", 1)
  $yd = [regex]::Replace($yd, '(?m)^(  user:\s*).*$', "`${1}$user", 1)
  $yd = [regex]::Replace($yd, '(?m)^(  password:\s*).*$', "`${1}$pass", 1)
  Set-Content -Encoding UTF8 -Path $yapDbCfg -Value $yd -NoNewline
  Write-Host "Updated $yapDbCfg (shared YaPDB)"
}

Write-Host "Updated $Config"
Write-Host "  server-id: $ServerId"
Write-Host "  inventory-profile: $Profile"
Write-Host "  url: $jdbc"
Write-Host "Restart YaPcore so YaPDB + YaPPlayerData connect."
