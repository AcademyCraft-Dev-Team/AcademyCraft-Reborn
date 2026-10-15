# run-client-stress.ps1
#
# Drives one structure-stress client baseline run against an isolated copy of a save:
#   * launches runClientSable with validation/structure_stress/client_stress.init.gradle,
#   * dismisses the NeoForge "future/deprecated method" startup warning automatically,
#   * waits for the in-game probe to write stress-client-result.txt and to stop the client,
#   * writes build/stress-client-<suffix>-summary.txt and keeps every raw log.
#
# Usage (replace rN with a fresh suffix; the directory must not exist yet):
#   powershell -ExecutionPolicy Bypass -File validation/structure_stress/run-client-stress.ps1 `
#       -RunSuffix r1 -SampleSeconds 60 -Structures 1
#
# Prepare the isolated directory first (save copy is never the live user save):
#   robocopy "run\sable-compat\repro-crash-suite-r2\client\saves\新的世界 (32)" `
#       "run\sable-compat\stress-client-r1\client\saves\新的世界 (32)" /E
param(
    [Parameter(Mandatory = $true)][string]$RunSuffix,
    [int]$SampleSeconds = 60,
    [int]$Structures = 1,
    [string]$World = '',
    [int]$TimeoutMinutes = 45,
    [switch]$NoDialogAutomation,
    [string]$RepoRoot = ''
)
$ErrorActionPreference = 'Continue'
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
. (Join-Path $PSScriptRoot 'close-startup-dialogs.ps1')

$runDir = Join-Path $RepoRoot "run/sable-compat/stress-client-$RunSuffix"
$clientDir = Join-Path $runDir 'client'
$savesDir = Join-Path $clientDir 'saves'
if (-not (Test-Path -LiteralPath $savesDir)) { Write-Error "Missing isolated client directory: $clientDir"; exit 2 }
if (-not $World) {
    $World = (Get-ChildItem -LiteralPath $savesDir -Directory | Select-Object -First 1).Name
}
if (-not $World -or -not (Test-Path -LiteralPath (Join-Path $savesDir "$World\level.dat"))) {
    Write-Error 'Copy the save into the isolated directory first (saves/<world>/level.dat missing)'
    exit 2
}
$resultPath = Join-Path $clientDir 'stress-client-result.txt'
if (Test-Path -LiteralPath $resultPath) { Write-Error "Stale result exists; use a fresh directory: $resultPath"; exit 2 }

# Archive any log left over from an earlier run so its "world ready" lines can
# never be mistaken for this run's progress.
$logsDir = Join-Path $clientDir 'logs'
if (Test-Path -LiteralPath $logsDir) {
    foreach ($name in @('latest.log', 'debug.log')) {
        $logFile = Join-Path $logsDir $name
        if (Test-Path -LiteralPath $logFile) {
            $archive = Join-Path $logsDir ("{0}.previous-{1}.log" -f $name.Replace('.log', ''), (Get-Date -Format 'yyyyMMdd-HHmmss'))
            Move-Item -LiteralPath $logFile -Destination $archive -Force
        }
    }
}

$buildLog = Join-Path $RepoRoot "build/stress-client-$RunSuffix.log"
$driverLog = Join-Path $RepoRoot "build/stress-client-$RunSuffix-driver.log"
$summaryPath = Join-Path $RepoRoot "build/stress-client-$RunSuffix-summary.txt"
if (Test-Path -LiteralPath $driverLog) { Remove-Item -LiteralPath $driverLog -Force }

$cmdFile = Join-Path $env:TEMP ("ac-stress-client-{0}-{1}.cmd" -f $RunSuffix, (Get-Random))
$worldArg = ''
if ($World -and $World -match '^[\x20-\x7E]+$') { $worldArg = "-PacademyStressWorld=$World" }
$cmdBody = @"
@echo off
cd /d "$RepoRoot"
call gradlew.bat runClientSable -PacademySableRuntime=suite -PacademySableRunDirectory=run/sable-compat/stress-client-$RunSuffix -PacademyStressJavaLauncher=jbr $worldArg -PacademyStressSampleSeconds=$SampleSeconds -PacademyStressStructures=$Structures -I validation/structure_stress/client_stress.init.gradle --console=plain --no-configuration-cache > "$buildLog" 2>&1
"@
Set-Content -LiteralPath $cmdFile -Value $cmdBody -Encoding Default
if (-not (Test-Path -LiteralPath $cmdFile)) { Write-Error "Cannot write launcher cmd: $cmdFile"; exit 2 }
$started = Get-Date
Write-Host "academy-stress: launching client run stress-client-$RunSuffix (world='$World')"
$proc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/d', '/c', $cmdFile -PassThru -WindowStyle Hidden

$dismissed = Invoke-StartupDialogDismissal -ClientDir $clientDir -DriverLog $driverLog `
    -JoinWaitSeconds (600) -NoDialogAutomation:$NoDialogAutomation
$verdict = if ($dismissed) { 'JOINED' } else { 'NO_WORLD' }

if ($dismissed) {
    $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 5
        if (Test-Path -LiteralPath $resultPath) { break }
        if ($proc.HasExited) { break }
    }
    if (Test-Path -LiteralPath $resultPath) { $verdict = 'RESULT_WRITTEN' } elseif ($proc.HasExited) { $verdict = 'PROCESS_EXIT_NO_RESULT' }
    else { $verdict = 'TIMEOUT' }
}

$killed = $false
if (-not $proc.HasExited) {
    & taskkill /PID $proc.Id /T /F 2>&1 | Out-Null
    $killed = $true
    Start-Sleep -Seconds 5
}
$pattern = 'stress-client-' + [regex]::Escape($RunSuffix) + '([/\\]|$)'
Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match $pattern } |
    ForEach-Object { & taskkill /PID $_.ProcessId /T /F 2>&1 | Out-Null; $killed = $true }
if ($killed) { Start-Sleep -Seconds 3 }

$dialogVerdict = 'unknown'
if (Test-Path -LiteralPath $driverLog) {
    $line = Get-Content -LiteralPath $driverLog | Where-Object { $_ -match 'DIALOG_RESULT dialog dismissed=' } | Select-Object -Last 1
    if ($line) { $dialogVerdict = ($line -split '\] ', 2)[1] }
}
$lines = @(
    "run=stress-client-$RunSuffix",
    "world=$World",
    "sample_seconds=$SampleSeconds",
    "structures=$Structures",
    "verdict=$verdict",
    "killed=$killed",
    "elapsed_seconds=$([int]((Get-Date) - $started).TotalSeconds)",
    "dialog=$dialogVerdict",
    "result_file=$resultPath",
    "gradle_log=$buildLog",
    "driver_log=$driverLog"
)
if (Test-Path -LiteralPath $resultPath) {
    $lines += '--- stress-client-result.txt ---'
    $lines += Get-Content -LiteralPath $resultPath
} else {
    $lines += '--- no result file ---'
}
$lines | Set-Content -LiteralPath $summaryPath -Encoding UTF8
Write-Host "academy-stress: verdict=$verdict summary=$summaryPath"
if ($verdict -eq 'RESULT_WRITTEN' -and (Test-Path -LiteralPath $resultPath) -and
        ((Get-Content -LiteralPath $resultPath -First 1) -eq 'PASSED')) { exit 0 }
exit 1
