# run-server-native-stress.ps1
#
# Drives one native structure-stress dedicated-server baseline run:
#   * prepares an isolated flat superflat server under run/stress-server-native-<suffix>,
#   * launches runServerCompat with validation/structure_stress/server_native_stress.init.gradle,
#   * waits for the probe result/exit markers (or kills on timeout),
#   * writes build/stress-server-native-<suffix>-summary.txt and keeps every raw log.
#
# Usage (replace rN with a fresh suffix; the directory must not exist yet):
#   powershell -ExecutionPolicy Bypass -File validation/structure_stress/run-server-native-stress.ps1 `
#       -RunSuffix r1 -Operations 30 -Regions 2 -Seconds 60
param(
    [Parameter(Mandatory = $true)][string]$RunSuffix,
    [int]$Operations = 30,
    [int]$Regions = 2,
    [int]$Seconds = 60,
    [int]$TimeoutMinutes = 45,
    [int]$Port = 25620,
    [string]$RepoRoot = ''
)
$ErrorActionPreference = 'Continue'
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
Push-Location $RepoRoot
try {
    $runDir = Join-Path $RepoRoot "run/stress-server-native-$RunSuffix"
    $worldDir = Join-Path $runDir 'native-structure-stress'
    $resultPath = Join-Path $runDir 'stress-server-native-result.txt'
    $exitPath = Join-Path $runDir 'stress-server-native-exit.txt'
    if ((Test-Path -LiteralPath $runDir) -and ((Test-Path -LiteralPath $resultPath) -or (Test-Path -LiteralPath $worldDir))) {
        Write-Error "Stale run directory; use a fresh suffix: $runDir"
        exit 2
    }
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding Ascii
    $settings = @(
        '#Minecraft server properties',
        'accepts-transfers=false',
        'allow-flight=true',
        'enable-query=false',
        'enable-rcon=false',
        'enforce-secure-profile=false',
        'gamemode=creative',
        'generate-structures=false',
        'level-name=native-structure-stress',
        'level-type=minecraft\:flat',
        'max-players=1',
        'max-tick-time=-1',
        'online-mode=false',
        'pause-when-empty-seconds=0',
        'server-ip=127.0.0.1',
        "server-port=$Port",
        'spawn-protection=0',
        'sync-chunk-writes=true',
        'view-distance=2',
        'simulation-distance=2',
        'white-list=false'
    )
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Value $settings -Encoding Ascii

    . (Join-Path $RepoRoot 'build/native-physics-p16-env.ps1')
    $gradleArgs = @(
        'runServerCompat', '-DisDev=true',
        '-PacademyStressJavaLauncher=jbr',
        '-I', 'validation/structure_stress/server_native_stress.init.gradle',
        "-PacademyStressServerNativeDirectory=run/stress-server-native-$RunSuffix",
        "-PacademyStressOperations=$Operations",
        "-PacademyStressRegions=$Regions",
        "-PacademyStressSeconds=$Seconds"
    ) + $physicsNativeArgs

    $buildLog = Join-Path $RepoRoot "build/stress-server-native-$RunSuffix.log"
    $summaryPath = Join-Path $RepoRoot "build/stress-server-native-$RunSuffix-summary.txt"
    $cmdFile = Join-Path $env:TEMP "ac-stress-native-$RunSuffix.cmd"
    $quoted = ($gradleArgs | ForEach-Object { if ($_ -match '[\s]') { '"{0}"' -f $_ } else { $_ } }) -join ' '
    $cmdBody = "@echo off`r`ncd /d ""$RepoRoot""`r`ncall gradlew.bat $quoted --console=plain --no-configuration-cache > ""$buildLog"" 2>&1`r`n"
    Set-Content -LiteralPath $cmdFile -Value $cmdBody -Encoding Ascii
    $started = Get-Date
    Write-Host "academy-stress: launching native server run $RunSuffix (operations=$Operations regions=$Regions seconds=$Seconds)"
    $proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/d', '/c', $cmdFile -PassThru -WindowStyle Hidden

    $verdict = 'TIMEOUT'
    $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 5
        if (Test-Path -LiteralPath $exitPath) { $verdict = 'EXIT_MARKER'; break }
        if ($proc.HasExited) { $verdict = 'PROCESS_EXIT'; break }
    }
    $killed = $false
    if (-not $proc.HasExited) {
        & taskkill /PID $proc.Id /T /F 2>&1 | Out-Null
        $killed = $true
        Start-Sleep -Seconds 5
    }
    $pattern = 'stress-server-native-' + [regex]::Escape($RunSuffix) + '([/\\]|$)'
    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match $pattern } |
        ForEach-Object { & taskkill /PID $_.ProcessId /T /F 2>&1 | Out-Null; $killed = $true }
    if ($killed) { Start-Sleep -Seconds 3 }
    if ((Test-Path -LiteralPath $resultPath) -and $verdict -eq 'PROCESS_EXIT') { $verdict = 'RESULT_WRITTEN' }

    $lines = @(
        "run=stress-server-native-$RunSuffix",
        "operations=$Operations",
        "regions=$Regions",
        "sample_seconds=$Seconds",
        "verdict=$verdict",
        "killed=$killed",
        "elapsed_seconds=$([int]((Get-Date) - $started).TotalSeconds)",
        "result_file=$resultPath",
        "exit_file=$exitPath",
        "gradle_log=$buildLog"
    )
    if (Test-Path -LiteralPath $resultPath) {
        $lines += '--- stress-server-native-result.txt ---'
        $lines += Get-Content -LiteralPath $resultPath
    } else {
        $lines += '--- no result file ---'
    }
    $lines | Set-Content -LiteralPath $summaryPath -Encoding UTF8
    Write-Host "academy-stress: verdict=$verdict summary=$summaryPath"
    if ((Test-Path -LiteralPath $resultPath) -and ((Get-Content -LiteralPath $resultPath -First 1) -eq 'PASSED')) { exit 0 }
    exit 1
} finally {
    Pop-Location
}
