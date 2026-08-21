#Requires -Version 5.1
<#
.SYNOPSIS
  Start packaged MariaDB for YaPPlayerData (Docker Compose).
#>
$ErrorActionPreference = "Stop"

function Find-YapRoot {
  $dir = $PSScriptRoot
  for ($i = 0; $i -lt 6; $i++) {
    $candidate = Join-Path $dir "deploy\mariadb\docker-compose.yml"
    if (Test-Path $candidate) { return $dir }
    $parent = Split-Path -Parent $dir
    if (-not $parent -or $parent -eq $dir) { break }
    $dir = $parent
  }
  return (Get-Location).Path
}

$Root = Find-YapRoot
$ComposeDir = Join-Path $Root "deploy\mariadb"
if (-not (Test-Path (Join-Path $ComposeDir "docker-compose.yml"))) {
  Write-Host "Cannot find deploy/mariadb/docker-compose.yml (looked from $PSScriptRoot)"
  exit 1
}

function Test-Docker {
  try {
    docker info 2>$null | Out-Null
    return $LASTEXITCODE -eq 0
  } catch {
    return $false
  }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Write-Host "Docker not found. Install Docker Desktop: https://docs.docker.com/desktop/setup/install/windows-install/"
  exit 1
}
if (-not (Test-Docker)) {
  Write-Host "Docker is installed but not running. Start Docker Desktop and retry."
  exit 1
}

$envFile = Join-Path $ComposeDir ".env"
$envExample = Join-Path $ComposeDir ".env.example"
if (-not (Test-Path $envFile)) {
  Copy-Item $envExample $envFile
  Write-Host "Created deploy/mariadb/.env — change YAP_DB_PASSWORD for production."
}

Push-Location $ComposeDir
try {
  docker compose version 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) {
    docker compose up -d
  } else {
    docker-compose up -d
  }
} finally {
  Pop-Location
}

Write-Host ""
Write-Host "Waiting for MariaDB healthy..."
for ($i = 0; $i -lt 40; $i++) {
  $health = docker inspect -f "{{.State.Health.Status}}" yapcore-mariadb 2>$null
  if ($health -eq "healthy") {
    Write-Host "MariaDB is ready."
    Write-Host "Next: .\scripts\windows\Configure-PlayerData.ps1   (or configure-playerdata.cmd in release)"
    Write-Host "      multi-backend: -HostAddress <db-ip> -ServerId <name>"
    exit 0
  }
  Start-Sleep -Seconds 1
}
Write-Host "Health check timed out. Check: docker logs yapcore-mariadb"
exit 1
