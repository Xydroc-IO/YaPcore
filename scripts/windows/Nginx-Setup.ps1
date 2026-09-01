# YaPcore nginx setup (Windows) — same templates as Linux scripts/nginx-setup.sh
# Usage:
#   .\Nginx-Setup.ps1              # generate + install if nginx found
#   .\Nginx-Setup.ps1 -DryRun      # print only
#   .\Nginx-Setup.ps1 -Uninstall
#   .\Nginx-Setup.ps1 -NginxHome C:\nginx
#
# Needs nginx WITH the stream module (official nginx.org Windows zip often lacks stream).
# See docs/start/WINDOWS.md for install options.

param(
    [switch]$DryRun,
    [switch]$Uninstall,
    [string]$NginxHome = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "Lib.ps1")

if ($Help) {
    Write-Host "Usage: .\Nginx-Setup.ps1 [-DryRun] [-Uninstall] [-NginxHome path]"
    exit 0
}

$Root = Get-YapRoot $ScriptDir
Read-YapConfig $Root

# Extra keys from server.properties
$extra = @{
    NginxPublic = 25565
    NginxPackHttp = 80
    NginxDomain = "_"
    YapJava = $script:YapConfig.Port
    YapBedrock = $script:YapConfig.Port
    YapPack = 8081
    Shared = $true
}
$cfg = Join-Path $Root "config\server.properties"
if (Test-Path $cfg) {
    Get-Content $cfg | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $key = $line.Substring(0, $i).Trim()
        $val = $line.Substring($i + 1).Trim()
        switch ($key) {
            "nginx-public-port" { $extra.NginxPublic = [int]($val -replace '[^\d]', '') }
            "nginx-pack-port" { $extra.NginxPackHttp = [int]($val -replace '[^\d]', '') }
            "nginx-domain" { if ($val) { $extra.NginxDomain = $val } }
            "server-domain" { if ($extra.NginxDomain -eq "_" -and $val) { $extra.NginxDomain = $val } }
            "public-host" { if ($extra.NginxDomain -eq "_" -and $val) { $extra.NginxDomain = $val } }
            "port" { $extra.YapJava = [int]($val -replace '[^\d]', '') }
            "bedrock-port" { $extra.YapBedrock = [int]($val -replace '[^\d]', '') }
            "resource-pack-http-port" { $extra.YapPack = [int]($val -replace '[^\d]', '') }
            "shared-listen-port" { $extra.Shared = ($val -match '^(true|1|yes)$') }
        }
    }
}
if ($extra.Shared) { $extra.YapBedrock = $extra.YapJava }
if (-not $extra.NginxDomain) { $extra.NginxDomain = "_" }

function Render-Template([string]$path) {
    $t = Get-Content -Raw $path
    $t = $t.Replace("__NGINX_PUBLIC_PORT__", "$($extra.NginxPublic)")
    $t = $t.Replace("__NGINX_PACK_PORT__", "$($extra.NginxPackHttp)")
    $t = $t.Replace("__NGINX_DOMAIN__", $extra.NginxDomain)
    $t = $t.Replace("__YAP_JAVA_PORT__", "$($extra.YapJava)")
    $t = $t.Replace("__YAP_BEDROCK_PORT__", "$($extra.YapBedrock)")
    $t = $t.Replace("__YAP_PACK_PORT__", "$($extra.YapPack)")
    return $t
}

$streamTpl = Join-Path $Root "deploy\nginx\yapcore-stream.conf.template"
$httpTpl = Join-Path $Root "deploy\nginx\yapcore-http.conf.template"
if (-not (Test-Path $streamTpl) -or -not (Test-Path $httpTpl)) {
    Write-Error "Missing deploy\nginx templates under $Root — release packages should include deploy\"
    exit 1
}

$outDir = Join-Path $Root "deploy\nginx\generated"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$streamOut = Join-Path $outDir "yapcore-stream.conf"
$httpOut = Join-Path $outDir "yapcore-http.conf"
(Render-Template $streamTpl) | Set-Content -Path $streamOut -Encoding utf8
(Render-Template $httpTpl) | Set-Content -Path $httpOut -Encoding utf8

Write-Host "Rendered:"
Write-Host "  $streamOut"
Write-Host "  $httpOut"
Write-Host "Domain: $($extra.NginxDomain)"
Write-Host "Same-PC join: 127.0.0.1:$($extra.YapJava)"
Write-Host "Public stream: $($extra.NginxDomain):$($extra.NginxPublic) → 127.0.0.1:$($extra.YapJava)"
Write-Host "Packs HTTP: http://$($extra.NginxDomain):$($extra.NginxPackHttp)/pack/"

if ($DryRun) {
    Write-Host "---- stream ----"
    Get-Content $streamOut
    Write-Host "---- http ----"
    Get-Content $httpOut
    exit 0
}

function Find-NginxHome {
    if ($NginxHome -and (Test-Path (Join-Path $NginxHome "nginx.exe"))) { return $NginxHome }
    if ($env:NGINX_HOME -and (Test-Path (Join-Path $env:NGINX_HOME "nginx.exe"))) { return $env:NGINX_HOME }
    foreach ($c in @(
        "C:\nginx",
        "C:\tools\nginx",
        "${env:ProgramFiles}\nginx",
        "${env:ProgramFiles(x86)}\nginx"
    )) {
        if ($c -and (Test-Path (Join-Path $c "nginx.exe"))) { return $c }
    }
    $cmd = Get-Command nginx -ErrorAction SilentlyContinue
    if ($cmd) { return (Split-Path -Parent $cmd.Source) }
    return $null
}

$home = Find-NginxHome
if (-not $home) {
    Write-Host ""
    Write-Host "nginx not found. Configs are ready under deploy\nginx\generated\" -ForegroundColor Yellow
    Write-Host "Install nginx WITH stream module, set NGINX_HOME, then re-run."
    Write-Host "See docs\WINDOWS.md (nginx section)."
    exit 2
}

$nginxExe = Join-Path $home "nginx.exe"
Write-Host "Using nginx: $nginxExe"

# Stream module check
$ver = & $nginxExe -V 2>&1 | Out-String
if ($ver -notmatch "stream") {
    Write-Host ""
    Write-Host "This nginx build may lack the stream module (needed for Minecraft TCP/UDP)." -ForegroundColor Yellow
    Write-Host "Official nginx.org Windows zips often omit stream. See docs\WINDOWS.md."
    Write-Host "Generated configs are still in deploy\nginx\generated\ — install a stream-capable build, then re-run."
    exit 3
}

$confDir = Join-Path $home "conf"
$confD = Join-Path $confDir "conf.d"
New-Item -ItemType Directory -Force -Path $confD | Out-Null

if ($Uninstall) {
    Remove-Item (Join-Path $confDir "yapcore-stream.conf") -Force -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $confD "yapcore-http.conf") -Force -ErrorAction SilentlyContinue
    & $nginxExe -t
    if ($LASTEXITCODE -eq 0) { & $nginxExe -s reload }
    Write-Host "YaPcore nginx snippets removed from $home"
    exit 0
}

Copy-Item $streamOut (Join-Path $confDir "yapcore-stream.conf") -Force
Copy-Item $httpOut (Join-Path $confD "yapcore-http.conf") -Force

$main = Join-Path $confDir "nginx.conf"
if (Test-Path $main) {
    $mainText = Get-Content -Raw $main
    if ($mainText -notmatch "yapcore-stream.conf") {
        $bak = "$main.yapcore.bak.$(Get-Date -Format yyyyMMddHHmmss)"
        Copy-Item $main $bak
        Add-Content $main "`r`n# YaPcore Minecraft TCP/UDP proxy`r`ninclude yapcore-stream.conf;`r`n"
        Write-Host "Patched nginx.conf (backup: $bak)"
    }
    if ($mainText -notmatch "conf\.d" -and $mainText -notmatch "yapcore-http") {
        # ensure http include for conf.d if missing — many Windows nginx.conf already include conf.d/*.conf
        if ($mainText -notmatch 'include\s+conf\.d') {
            Write-Host "NOTE: Ensure http{} includes conf.d/*.conf so yapcore-http.conf loads." -ForegroundColor Yellow
        }
    }
}

Push-Location $home
try {
    & .\nginx.exe -t
    if ($LASTEXITCODE -ne 0) {
        Write-Error "nginx -t failed"
        exit 1
    }
    # reload or start
    $running = Get-Process nginx -ErrorAction SilentlyContinue
    if ($running) {
        & .\nginx.exe -s reload
    } else {
        Start-Process -FilePath .\nginx.exe -WorkingDirectory $home -WindowStyle Hidden
    }
} finally {
    Pop-Location
}

Write-Host "nginx ready — game :$($extra.NginxPublic) → 127.0.0.1:$($extra.YapJava), packs :$($extra.NginxPackHttp)"
