# Shared helpers for YaPcore Windows release scripts (PowerShell 5+)

function Get-YapRoot {
    param([string]$ScriptDir)
    $parent = Split-Path -Parent $ScriptDir
    if ((Test-Path (Join-Path $parent "yapcore.jar")) -or
        (Test-Path (Join-Path $parent "config\server.properties")) -or
        (Test-Path (Join-Path $parent "build.gradle.kts"))) {
        return (Resolve-Path $parent).Path
    }
    return (Get-Location).Path
}

function Get-YapJava {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return (Join-Path $env:JAVA_HOME "bin\java.exe")
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Require-YapJava {
    $java = Get-YapJava
    if (-not $java) {
        Write-Error "Java not found. Install JDK 25+ and add java to PATH or set JAVA_HOME."
        exit 1
    }
    & $java -version 2>&1 | Select-Object -First 1 | ForEach-Object { Write-Host "Using $_" }
    return $java
}

function Read-YapConfig {
    param([string]$Root)
    $script:YapConfig = @{
        RamMb            = 2048
        RamMinMb         = 512
        MaxPlayers       = 100
        Port             = 25566
        JvmGc            = "zgc"
        JvmNuma          = $true
        JvmHeapPin       = $true
        JvmNumaNode      = 0
        JvmThreadPriority = $true
        GameAuthority    = "paper"
        PaperEmbed       = $true
        PaperPhase3      = $true
        PaperPhase3Nms   = $true
        PaperDir         = "paper-kernel"
        PaperVersion     = "26.2"
    }
    $cfg = Join-Path $Root "config\server.properties"
    if (-not (Test-Path $cfg)) { return }
    Get-Content $cfg | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $key = $line.Substring(0, $i).Trim()
        $val = $line.Substring($i + 1).Trim()
        switch ($key) {
            "ram-mb" { $script:YapConfig.RamMb = [int]($val -replace '[^\d]', '') }
            "ram-min-mb" { $script:YapConfig.RamMinMb = [int]($val -replace '[^\d]', '') }
            "max-players" { $script:YapConfig.MaxPlayers = [int]($val -replace '[^\d]', '') }
            "port" { $script:YapConfig.Port = [int]($val -replace '[^\d]', '') }
            "jvm-gc" { $script:YapConfig.JvmGc = $val.ToLowerInvariant() }
            "jvm-numa" { $script:YapConfig.JvmNuma = ($val -match '^(true|1|yes)$') }
            "jvm-heap-pin" { $script:YapConfig.JvmHeapPin = ($val -match '^(true|1|yes)$') }
            "jvm-numa-node" { $script:YapConfig.JvmNumaNode = [int]($val -replace '[^\d]', '') }
            "jvm-thread-priority" { $script:YapConfig.JvmThreadPriority = ($val -match '^(true|1|yes)$') }
            "game-authority" { $script:YapConfig.GameAuthority = $val.ToLowerInvariant() }
            "paper-embed" { $script:YapConfig.PaperEmbed = ($val -match '^(true|1|yes)$') }
            "paper-phase3-tick-bridge" { $script:YapConfig.PaperPhase3 = ($val -match '^(true|1|yes)$') }
            "paper-phase3-nms-tick" { $script:YapConfig.PaperPhase3Nms = ($val -match '^(true|1|yes)$') }
            "paper-dir" { $script:YapConfig.PaperDir = $val.Trim() }
            "paper-version" { $script:YapConfig.PaperVersion = $val.Trim() }
        }
    }
    if ($script:YapConfig.JvmHeapPin) {
        $script:YapConfig.RamMinMb = $script:YapConfig.RamMb
    }
    if ($script:YapConfig.RamMinMb -gt $script:YapConfig.RamMb) {
        $script:YapConfig.RamMinMb = $script:YapConfig.RamMb
    }
}

function Get-YapJvmArgs {
    param([string]$Root)
    $c = $script:YapConfig
    $opts = @(
        "-Xms$($c.RamMinMb)m",
        "-Xmx$($c.RamMb)m",
        "-Dyapcore.home=$Root",
        "-Dyapengine.gc.profile=zgc-numa"
    )
    switch -Regex ($c.JvmGc) {
        "^(zgc|generational-zgc|genzgc)$" {
            $opts += "-XX:+UseZGC"
            $opts += "-XX:+UnlockExperimentalVMOptions"
            $opts += "-XX:+UnlockDiagnosticVMOptions"
        }
        "^g1$" { $opts += "-XX:+UseG1GC" }
        default {
            $opts += "-XX:+UseZGC"
            $opts += "-XX:+UnlockExperimentalVMOptions"
        }
    }
    if ($c.JvmNuma) { $opts += "-XX:+UseNUMA" }
    if ($c.JvmThreadPriority) { $opts += "-XX:ThreadPriorityPolicy=1" }
    if ($env:YAPCORE_JAVA_OPTS) {
        $opts += ($env:YAPCORE_JAVA_OPTS -split '\s+' | Where-Object { $_ })
    }
    return $opts
}

function Find-YapJar {
    param([string]$Root)
    $candidates = @(
        (Join-Path $Root "yapcore.jar"),
        (Join-Path $Root "build\dist\yapcore.jar"),
        (Join-Path $Root "build\libs\yapcore-0.1.0.jar")
    )
    foreach ($p in $candidates) {
        if (Test-Path $p) { return (Resolve-Path $p).Path }
    }
    $hit = Get-ChildItem -Path (Join-Path $Root "build\libs") -Filter "yapcore*.jar" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($hit) { return $hit.FullName }
    return $null
}

function Ensure-YapDirs {
    param([string]$Root)
    $c = $script:YapConfig
    @(
        "config", "plugins", "logs", "lib", $c.PaperDir
    ) | ForEach-Object {
        $p = Join-Path $Root $_
        if (-not (Test-Path $p)) { New-Item -ItemType Directory -Path $p | Out-Null }
    }
    Ensure-YapConfigHub $Root
    Ensure-YapPluginsJunction $Root
}

function New-YapJunction {
    param([string]$Link, [string]$Target)
    if (Test-Path $Link) { return }
    $parent = Split-Path -Parent $Link
    if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent | Out-Null }
    if (-not (Test-Path $Target)) { New-Item -ItemType Directory -Path $Target | Out-Null }
    # Directory junction (no admin required for local dirs)
    cmd.exe /c "mklink /J `"$Link`" `"$Target`"" | Out-Null
}

function Ensure-YapConfigHub {
    param([string]$Root)
    $c = $script:YapConfig
    $hub = Join-Path $Root "config"
    $paperCfg = Join-Path $Root "$($c.PaperDir)\config"
    if (-not (Test-Path $paperCfg)) { New-Item -ItemType Directory -Path $paperCfg | Out-Null }
    New-YapJunction (Join-Path $hub "paper") $paperCfg
}

function Ensure-YapPluginsJunction {
    param([string]$Root)
    $c = $script:YapConfig
    $paperPlugins = Join-Path $Root "$($c.PaperDir)\plugins"
    $unified = Join-Path $Root "plugins"
    if (Test-Path $paperPlugins) {
        $item = Get-Item $paperPlugins -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { return }
        # Real directory — leave alone (bench/isolated); release trees expect junction
        return
    }
    New-YapJunction $paperPlugins $unified
}

function Require-YapPaperclip {
    param([string]$Root)
    $c = $script:YapConfig
    if ($c.GameAuthority -ne "paper") { return }
    if (-not $c.PaperEmbed -or -not $c.PaperPhase3 -or -not $c.PaperPhase3Nms) { return }
    $yap = Join-Path $Root "lib\paper-$($c.PaperVersion)-yap.jar"
    if (-not (Test-Path $yap)) {
        Write-Host ""
        Write-Host "Missing YaP Paperclip: $yap" -ForegroundColor Yellow
        Write-Host "On Windows (same as Linux), build it in this tree:"
        Write-Host "  .\scripts\Vendor-Paper.ps1"
        Write-Host "  .\scripts\Build-Vendor-Paper.ps1"
        Write-Host "Requires: Git, JDK 25+, and Git Bash (for apply-yap-paper-hooks.sh)."
        Write-Host ""
        Write-Error "Phase 3 NMS tick requires lib\paper-$($c.PaperVersion)-yap.jar"
        exit 1
    }
}

function Get-YapPidFile {
    param([string]$Root)
    return (Join-Path $Root "yapcore.pid")
}

function Test-YapRunning {
    param([string]$Root)
    $pidFile = Get-YapPidFile $Root
    if (-not (Test-Path $pidFile)) { return $false }
    $raw = (Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if (-not $raw) { return $false }
    $procId = 0
    if (-not [int]::TryParse($raw.Trim(), [ref]$procId)) { return $false }
    try {
        $p = Get-Process -Id $procId -ErrorAction Stop
        return $null -ne $p
    } catch {
        return $false
    }
}

function Get-YapPid {
    param([string]$Root)
    $pidFile = Get-YapPidFile $Root
    if (-not (Test-Path $pidFile)) { return $null }
    $raw = (Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if (-not $raw) { return $null }
    return $raw.Trim()
}
