param([Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_-]{1,64}$')][string]$Name)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'run'))
$testRoot = [IO.Path]::GetFullPath((Join-Path $runRoot ('sublevel-batch-multiplayer-' + $Name)))
if (!$testRoot.StartsWith($runRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Test root escaped run directory' }
if (Test-Path -LiteralPath $testRoot) { throw 'Choose a new session name; existing test data will not be replaced' }
$listeners = Get-NetTCPConnection -LocalPort 25575 -State Listen -ErrorAction SilentlyContinue
if ($listeners) { throw 'Loopback TCP port 25575 is already in use' }
foreach ($role in @('server','actor','observer','coord')) { New-Item -ItemType Directory -Path (Join-Path $testRoot $role) | Out-Null }
$serverProperties = @'
server-ip=127.0.0.1
server-port=25575
online-mode=false
enforce-secure-profile=false
level-name=sublevel-batch-multiplayer-smoke
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
allow-flight=true
max-players=2
spawn-protection=0
view-distance=6
simulation-distance=4
sync-chunk-writes=true
gamemode=creative
difficulty=peaceful
enable-rcon=false
enable-query=false
accepts-transfers=false
'@
[IO.File]::WriteAllText((Join-Path $testRoot 'server/server.properties'),$serverProperties,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $testRoot 'server/eula.txt'),"eula=true`n",[Text.UTF8Encoding]::new($false))
foreach ($role in @('actor','observer')) {
    [IO.File]::WriteAllText((Join-Path $testRoot ($role+'/options.txt')),"lang:zh_cn`nrenderDistance:6`nsimulationDistance:5`nmaxFps:60`nenableVsync:false`npauseOnLostFocus:false`n",[Text.UTF8Encoding]::new($false))
}
$bundleRoot = Join-Path $repoRoot 'src/main/resources/academy/native_physics/windows-x86_64'
$nativeRoot = Join-Path $testRoot 'native-runtime'
New-Item -ItemType Directory -Path $nativeRoot | Out-Null
[IO.File]::WriteAllBytes((Join-Path $nativeRoot 'academy_physics.dll'), [Convert]::FromBase64String([IO.File]::ReadAllText((Join-Path $bundleRoot 'academy_physics.dll.b64'))))
Copy-Item -LiteralPath (Join-Path $bundleRoot 'academy_physics.properties') -Destination (Join-Path $nativeRoot 'academy_physics.properties')
$bundleMetadata = [IO.File]::ReadAllText((Join-Path $nativeRoot 'academy_physics.properties'))
$declaredHash = [regex]::Match($bundleMetadata,'(?m)^sha256=([0-9a-fA-F]{64})\s*$').Groups[1].Value
if ((Get-FileHash -LiteralPath (Join-Path $nativeRoot 'academy_physics.dll') -Algorithm SHA256).Hash -ne $declaredHash) { throw 'Bundled native library hash mismatch' }
Write-Output $testRoot
