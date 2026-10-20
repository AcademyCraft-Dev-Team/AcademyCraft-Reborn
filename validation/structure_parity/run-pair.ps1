param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$RunId,
    [ValidateSet('local','sable')][string]$Backend,
    [ValidateSet('ground','floating')][string]$Fixture,
    [Parameter(Mandatory=$true)][string]$AcceptedEulaFile
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (-not (Get-Content -LiteralPath $AcceptedEulaFile | Where-Object { $_ -match '^eula=true\s*$' })) {
    throw 'Provide an existing accepted EULA file from your development server.'
}
$directory = Join-Path $repo "run/sable-compat/parity-$RunId/server"
if (Test-Path -LiteralPath $directory) { throw 'Use a fresh RunId; results/worlds are never overwritten.' }
New-Item -ItemType Directory -Path $directory -Force | Out-Null
Copy-Item -LiteralPath $AcceptedEulaFile -Destination (Join-Path $directory 'eula.txt')
@('level-name=parity-world','level-seed=20261020','level-type=minecraft\:flat',
  'generate-structures=false','gamemode=creative','online-mode=false',
  'server-ip=127.0.0.1','server-port=25649','max-tick-time=120000',
  'pause-when-empty-seconds=0','view-distance=2','simulation-distance=2',
  'sync-chunk-writes=true') | Set-Content -LiteralPath (Join-Path $directory 'server.properties') -Encoding Ascii
$arguments = @('runParityServer','-DisDev=true','-I','validation/structure_parity/server_parity.init.gradle',
    "-PacademyParityBackend=$Backend","-PacademyParityRun=$RunId","-PacademyParityFixture=$Fixture",
    '--console=plain','--no-configuration-cache')
if ($Backend -eq 'sable') {
    $arguments += @('-PacademySableRuntime=sable', "-PacademySableRunDirectory=run/sable-compat/parity-$RunId")
}
# Caller provisions JBR/native inputs; this repository-local environment script is optional.
if (Test-Path -LiteralPath (Join-Path $repo 'build/native-physics-p16-env.ps1')) {
    . (Join-Path $repo 'build/native-physics-p16-env.ps1')
    $arguments += $physicsNativeArgs
}
Push-Location $repo
try {
    # Native JVM/Gradle warnings go to stderr. PowerShell 5.1 turns a native stderr line into a
    # terminating error under 'Stop' before the exit code or result.json is checked, so keep the
    # call non-terminating and judge success from Gradle's exit code and result.json.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & ./gradlew.bat @arguments *> (Join-Path $repo "build/parity-$RunId.log")
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed for $RunId" }
    $result = Get-Content -LiteralPath (Join-Path $directory 'result.json') -Raw | ConvertFrom-Json
    if ($result.measurement_status -ne 'COMPLETE') { throw "Incomplete measurement: $($result.error)" }
    Write-Output "$RunId measurement=$($result.measurement_status) lift=$($result.lift_status) capture_ms=$($result.capture_call_ms)"
} finally { Pop-Location }
