[CmdletBinding()]
param([string]$AcceptedEulaPath = 'run/server-compat/eula.txt')

$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$validationRoot = Join-Path $workspace 'run/vfx-validation'
$serverRoot = Join-Path $validationRoot 'server'
[IO.Directory]::CreateDirectory($serverRoot) | Out-Null
$eulaTarget = Join-Path $serverRoot 'eula.txt'
if (-not (Test-Path -LiteralPath $eulaTarget)) {
    $accepted = [IO.Path]::GetFullPath((Join-Path $workspace $AcceptedEulaPath))
    if (-not (Test-Path -LiteralPath $accepted) -or
        -not ([IO.File]::ReadAllText($accepted) -match '(?m)^eula=true\s*$')) {
        throw 'Provide an existing accepted Minecraft EULA file using -AcceptedEulaPath.'
    }
    Copy-Item -LiteralPath $accepted -Destination $eulaTarget
}
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $serverRoot 'server.properties'), @'
server-ip=127.0.0.1
server-port=25575
online-mode=false
enforce-secure-profile=false
level-name=vfx-validation
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
view-distance=6
simulation-distance=5
spawn-protection=0
max-players=2
allow-flight=true
sync-chunk-writes=false
'@ + "`n", $utf8)
foreach ($role in @('caster', 'observer')) {
    $clientRoot = Join-Path $validationRoot $role
    [IO.Directory]::CreateDirectory((Join-Path $clientRoot 'config')) | Out-Null
    # These isolated clients bypass first-run onboarding and nonfatal mod metadata warnings.
    [IO.File]::WriteAllText((Join-Path $clientRoot 'config/neoforge-client.toml'), "showLoadWarnings=false`n", $utf8)
    [IO.File]::WriteAllText((Join-Path $clientRoot 'options.txt'), @'
onboardAccessibility:false
skipMultiplayerWarning:true
tutorialStep:none
enableVsync:false
maxFps:120
pauseOnLostFocus:false
renderDistance:6
simulationDistance:5
'@ + "`n", $utf8)
}
Write-Output "Prepared isolated validation directories at $validationRoot"
