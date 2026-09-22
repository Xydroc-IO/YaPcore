# Thin Windows wrappers that prefer bash (Git Bash/WSL) for scripts that are bash-first.
# Used by dashboard/Swing Setup when PowerShell is the host.

param(
    [Parameter(Mandatory = $true)][ValidateSet("Seed-Defaults", "Fetch-Tebex", "Fetch-Grim", "Grim-Ac", "Apply-Production", "Forwarding")]
    [string]$Action,
    [string[]]$ExtraArgs = @()
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "Lib.ps1")
$Root = Get-YapRoot $ScriptDir

function Find-Bash {
    $candidates = @(
        "bash",
        "C:\Program Files\Git\bin\bash.exe",
        "C:\Program Files\Git\usr\bin\bash.exe",
        "$env:USERPROFILE\scoop\apps\git\current\bin\bash.exe"
    )
    foreach ($c in $candidates) {
        try {
            $cmd = Get-Command $c -ErrorAction SilentlyContinue
            if ($cmd) { return $cmd.Source }
            if (Test-Path $c) { return $c }
        } catch {}
    }
    return $null
}

$map = @{
    "Seed-Defaults"    = "scripts/setup/seed-defaults.sh"
    "Fetch-Tebex"       = "scripts/plugins/fetch-tebex.sh"
    "Fetch-Grim"        = "scripts/plugins/fetch-grim.sh"
    "Grim-Ac"           = "scripts/plugins/grim-ac.sh"
    "Apply-Production"  = "scripts/setup/apply-production-profile.sh"
    "Forwarding"        = "scripts/setup/setup-velocity-forwarding.sh"
}

$rel = $map[$Action]
$script = Join-Path $Root $rel
if (-not (Test-Path $script)) {
    Write-Error "Missing $rel"
    exit 1
}

$bash = Find-Bash
if (-not $bash) {
    Write-Error "bash not found. Install Git Bash or WSL, then re-run Setup, or run: $rel"
    exit 1
}

$argLine = @($script) + $ExtraArgs
& $bash @argLine
exit $LASTEXITCODE
