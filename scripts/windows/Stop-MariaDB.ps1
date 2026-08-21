#Requires -Version 5.1
<#
.SYNOPSIS
  Stop packaged MariaDB (keeps data volume).
#>
$ErrorActionPreference = "Stop"

function Find-YapRoot {
  $dir = $PSScriptRoot
  for ($i = 0; $i -lt 6; $i++) {
    if (Test-Path (Join-Path $dir "deploy\mariadb\docker-compose.yml")) { return $dir }
    $parent = Split-Path -Parent $dir
    if (-not $parent -or $parent -eq $dir) { break }
    $dir = $parent
  }
  return (Get-Location).Path
}

$Root = Find-YapRoot
$ComposeDir = Join-Path $Root "deploy\mariadb"

Push-Location $ComposeDir
try {
  docker compose version 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) {
    docker compose down
  } else {
    docker-compose down
  }
} finally {
  Pop-Location
}
Write-Host "MariaDB stopped (data volume retained)."
