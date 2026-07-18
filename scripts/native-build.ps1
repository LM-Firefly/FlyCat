[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ScriptArgs = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Ensure-WindowsSystemPath {
    if ($env:OS -ne 'Windows_NT') {
        return
    }

    $required = @(
        (Join-Path $env:WINDIR 'System32'),
        $env:WINDIR
    ) | Where-Object { $_ }

    $segments = ($env:Path -split ';') | Where-Object { $_ }
    foreach ($entry in $required) {
        $exists = $segments | Where-Object { $_.TrimEnd('\\') -ieq $entry.TrimEnd('\\') }
        if (-not $exists) {
            $segments = @($entry) + $segments
        }
    }

    $env:Path = ($segments | Select-Object -Unique) -join ';'
}

function Resolve-KotlincPath {
    $candidates = @()

    if ($env:FLYCAT_KOTLINC_BAT) {
        $candidates += $env:FLYCAT_KOTLINC_BAT
    }

    if ($env:FLYCAT_KOTLIN_HOME) {
        $candidates += (Join-Path $env:FLYCAT_KOTLIN_HOME 'bin\kotlinc.bat')
        $candidates += (Join-Path $env:FLYCAT_KOTLIN_HOME 'kotlinc.bat')
    }

    $candidates += 'D:\MiDrive\Android\android-studio\plugins\Kotlin\kotlinc\bin\kotlinc.bat'
    $candidates += 'C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc\bin\kotlinc.bat'

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw @"
Unable to find kotlinc.bat.

Checked:
$($candidates | Where-Object { $_ } | ForEach-Object { " - $_" } | Out-String)
Set FLYCAT_KOTLINC_BAT to your kotlinc.bat path if needed.
"@
}

function Resolve-CargoPath {
    $candidates = @()

    if ($env:FLYCAT_CARGO) {
        $candidates += $env:FLYCAT_CARGO
    }

    foreach ($homeVar in @('FLYCAT_RUST_HOME', 'CARGO_HOME', 'RUSTUP_HOME', 'RUST_HOME')) {
        $homeDir = [Environment]::GetEnvironmentVariable($homeVar)
        if ($homeDir) {
            $candidates += (Join-Path $homeDir 'bin\cargo.exe')
            $candidates += (Join-Path $homeDir 'cargo.exe')
        }
    }

    if ($env:USERPROFILE) {
        $candidates += (Join-Path $env:USERPROFILE '.cargo\bin\cargo.exe')
    }

    $candidates += 'D:\MiDrive\VS Code\data\user-data\Rust\bin\cargo.exe'

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    return $null
}

function Ensure-XzDependency {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $jarDir = Join-Path $RepoRoot 'build\tmp'
    $jarPath = Join-Path $jarDir 'xz-1.12.jar'
    $jarUrl = 'https://repo.maven.apache.org/maven2/org/tukaani/xz/1.12/xz-1.12.jar'

    if (-not (Test-Path -LiteralPath $jarDir -PathType Container)) {
        New-Item -ItemType Directory -Path $jarDir -Force | Out-Null
    }

    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        Write-Host "[native-build] Downloading xz-1.12.jar ..."
        Invoke-WebRequest -Uri $jarUrl -OutFile $jarPath
    } else {
        Write-Host "[native-build] Using cached xz-1.12.jar"
    }

    return $jarPath
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$localeScript = Join-Path $PSScriptRoot 'generate-locale.main.kts'
$nativeScript = Join-Path $PSScriptRoot 'native-build.main.kts'

$runLocaleOnly = ($ScriptArgs -contains '--locale') -or ($ScriptArgs -contains '--generate-locale')
$runNativeOnly = ($ScriptArgs -contains '--native-only') -or ($ScriptArgs -contains '--no-locale')
$runGeoOnly = $ScriptArgs -contains '--geo'
$passthroughArgs = @(
    $ScriptArgs | Where-Object { $_ -notin @('--locale', '--generate-locale', '--native-only', '--no-locale', '--geo') }
)

function Invoke-KotlinScript {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Kotlinc,
        [Parameter(Mandatory = $true)]
        [string]$ScriptPath,
        [Parameter(Mandatory = $true)]
        [string]$LogPrefix,
        [string]$Classpath,
        [string[]]$ScriptInvokeArgs = @()
    )

    if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) {
        throw "Kotlin script not found: $ScriptPath"
    }

    Write-Host "[$LogPrefix] Repo root: $repoRoot"
    Write-Host "[$LogPrefix] kotlinc: $Kotlinc"

    $invokeArgs = @('-script')
    if ($Classpath) {
        $invokeArgs += @('-cp', $Classpath)
    }
    $invokeArgs += $ScriptPath
    if ($ScriptInvokeArgs.Count -gt 0) {
        $invokeArgs += $ScriptInvokeArgs
    }

    Push-Location $repoRoot
    try {
        & $Kotlinc @invokeArgs | Out-Host
        $scriptExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    if ($null -eq $scriptExitCode) {
        $scriptExitCode = 0
    }
    return [int]$scriptExitCode
}

Ensure-WindowsSystemPath
$kotlinc = Resolve-KotlincPath

if ($runGeoOnly) {
    Write-Host "[native-build] Running in geo-download mode"
    $xzJar = Ensure-XzDependency -RepoRoot $repoRoot
    $nativeInvokeArgs = @('--', '--geo')
    $exitCode = Invoke-KotlinScript -Kotlinc $kotlinc -ScriptPath $nativeScript -LogPrefix 'native-build' -Classpath $xzJar -ScriptInvokeArgs $nativeInvokeArgs
    exit $exitCode
}

if ($runLocaleOnly) {
    $localeInvokeArgs = @('--', $repoRoot)
    if ($passthroughArgs.Count -gt 0) {
        $localeInvokeArgs += $passthroughArgs
    }
    $exitCode = Invoke-KotlinScript -Kotlinc $kotlinc -ScriptPath $localeScript -LogPrefix 'generate-locale' -ScriptInvokeArgs $localeInvokeArgs
    exit $exitCode
}

$cargo = Resolve-CargoPath
$xzJar = Ensure-XzDependency -RepoRoot $repoRoot
if ($cargo) {
    $env:FLYCAT_CARGO = $cargo
}
Write-Host "[native-build] cargo: $cargo"

if (-not $runNativeOnly) {
    $localeInvokeArgs = @('--', $repoRoot)
    $localeExitCode = Invoke-KotlinScript -Kotlinc $kotlinc -ScriptPath $localeScript -LogPrefix 'generate-locale' -ScriptInvokeArgs $localeInvokeArgs
    if ($localeExitCode -ne 0) {
        exit $localeExitCode
    }
}

$nativeInvokeArgs = @('--', '--all')  # Default: build everything (geo + go + rust + cpp)
if ($passthroughArgs.Count -gt 0) {
    $nativeInvokeArgs += $passthroughArgs
}

$exitCode = Invoke-KotlinScript -Kotlinc $kotlinc -ScriptPath $nativeScript -LogPrefix 'native-build' -Classpath $xzJar -ScriptInvokeArgs $nativeInvokeArgs
exit $exitCode
