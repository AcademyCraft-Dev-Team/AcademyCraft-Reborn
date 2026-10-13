[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Prepare', 'Verify')][string]$Mode,
    [Parameter(Mandatory)][string]$GameDirectory,
    [string]$SourceRunDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$allowedRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'run/sable-compat'))
$gameRoot = [IO.Path]::GetFullPath($GameDirectory)
$worldRoot = Join-Path $gameRoot 'academy-sable-smoke'
$baselinePath = Join-Path $gameRoot 'sable-retention-baseline.json'

function Assert-Under([string]$Path, [string]$Root) {
    $full = [IO.Path]::GetFullPath($Path)
    $prefix = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw "Path escaped required root: $full" }
    return $full
}

function Assert-NoReparseAncestors([string]$Path) {
    $cursor = [IO.Path]::GetFullPath($Path)
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            $item = Get-Item -LiteralPath $cursor -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Linked path refused: $cursor" }
        }
        $parent = [IO.Directory]::GetParent($cursor)
        $cursor = if ($null -eq $parent) { $null } else { $parent.FullName }
    }
}

function Get-SafeFiles([string]$Root) {
    Assert-NoReparseAncestors $Root
    if (-not (Test-Path -LiteralPath $Root -PathType Container)) { throw "Missing directory: $Root" }
    $pending = [Collections.Generic.Stack[string]]::new()
    $files = [Collections.Generic.List[string]]::new()
    $pending.Push([IO.Path]::GetFullPath($Root))
    while ($pending.Count -gt 0) {
        foreach ($item in Get-ChildItem -LiteralPath $pending.Pop() -Force) {
            $null = Assert-Under $item.FullName $Root
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Linked tree entry refused: $($item.FullName)" }
            if ($item.PSIsContainer) { $pending.Push($item.FullName) } else { $files.Add($item.FullName) }
        }
    }
    return @($files | Sort-Object)
}

function Get-FileRecords([string]$Root) {
    return @(foreach ($file in Get-SafeFiles $Root) {
        [pscustomobject]@{
            path = [IO.Path]::GetRelativePath($Root, $file).Replace('\', '/')
            bytes = (Get-Item -LiteralPath $file -Force).Length
            sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
        }
    })
}

function Assert-RecordsEqual($Expected, $Actual, [string]$Label) {
    $expectedItems = @($Expected); $actualItems = @($Actual)
    if ($expectedItems.Count -ne $actualItems.Count) { throw "$Label file count differs" }
    $byPath = @{}
    foreach ($item in $actualItems) {
        if ($byPath.ContainsKey($item.path)) { throw "$Label duplicate path: $($item.path)" }
        $byPath[$item.path] = $item
    }
    foreach ($item in $expectedItems) {
        $other = $byPath[$item.path]
        if ($null -eq $other -or $other.bytes -ne $item.bytes -or $other.sha256 -ne $item.sha256) {
            throw "$Label bytes differ: $($item.path)"
        }
    }
}

function Assert-WorldStopped([string]$Run, [string]$World) {
    Assert-NoReparseAncestors $Run
    foreach ($process in Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'") {
        $command = [string]$process.CommandLine
        if ($command.Replace('/', '\').IndexOf($Run.Replace('/', '\'), [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            throw "A Java process still references this run: PID $($process.ProcessId)"
        }
    }
    $lock = Join-Path $World 'session.lock'
    if (-not (Test-Path -LiteralPath $lock -PathType Leaf)) { throw "Full world has no session.lock: $World" }
    $stream = [IO.File]::Open($lock, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
    try { $null = $stream.ReadByte() } finally { $stream.Dispose() }
}

function Assert-PassedSource([string]$Run) {
    $null = Assert-Under $Run (Join-Path $repoRoot 'run')
    if (-not ([IO.Path]::GetFileName($Run).StartsWith('native-structure-physics-persistence-'))) { throw 'Source must be an existing native persistence test run' }
    foreach ($stage in @('prepare', 'unavailable', 'resume')) {
        $result = Join-Path $Run "native-physics-persistence-$stage-result.txt"
        $exit = Join-Path $Run "native-physics-persistence-$stage-exit.txt"
        if (-not (Test-Path -LiteralPath $result -PathType Leaf) -or -not (Test-Path -LiteralPath $exit -PathType Leaf)) { throw "Missing passed source stage: $stage" }
        if (-not (Get-Content -LiteralPath $result -Raw).StartsWith("PASSED`n")) { throw "Source stage did not pass: $stage" }
        $exitText = Get-Content -LiteralPath $exit -Raw
        if ($exitText -notmatch '(?m)^exit_status=0\r?$' -or $exitText -notmatch '(?m)^server_thread_terminated=true\r?$' -or
                $exitText -notmatch '(?m)^stopping_guards_verified=true\r?$') { throw "Source stage lacks normal stopped evidence: $stage" }
    }
}

$null = Assert-Under $gameRoot $allowedRoot
Assert-NoReparseAncestors $gameRoot
if ([IO.Path]::GetFileName($gameRoot) -ne 'server') { throw 'Retention GameDirectory must be the isolated server directory' }

if ($Mode -eq 'Prepare') {
    if (-not $SourceRunDirectory) { throw 'Prepare requires SourceRunDirectory' }
    $sourceRun = [IO.Path]::GetFullPath($SourceRunDirectory)
    $sourceWorld = Join-Path $sourceRun 'native-structure-physics-persistence-smoke'
    Assert-PassedSource $sourceRun
    Assert-WorldStopped $sourceRun $sourceWorld
    $sourceFiles = Get-FileRecords $sourceWorld
    foreach ($required in @('level.dat', 'data/academy/native_structures/metadata.dat')) {
        if (-not ($sourceFiles | Where-Object path -eq $required)) { throw "Full source world lacks $required" }
    }
    if (-not ($sourceFiles | Where-Object { $_.path -like 'dimensions/academy/structure_space_*/region/*.mca' })) { throw 'Source lacks real native storage region files' }
    if (Test-Path -LiteralPath $worldRoot) { throw "Destination world already exists; nothing will be replaced: $worldRoot" }
    if (Test-Path -LiteralPath $baselinePath) { throw "Baseline already exists: $baselinePath" }
    $null = [IO.Directory]::CreateDirectory($gameRoot)
    Assert-NoReparseAncestors $gameRoot
    # Full world copy, including dimensions, chunks, entities, player data and server configs.
    Copy-Item -LiteralPath $sourceWorld -Destination $worldRoot -Recurse
    $copiedFiles = Get-FileRecords $worldRoot
    Assert-RecordsEqual $sourceFiles $copiedFiles 'Complete copied world'
    Assert-RecordsEqual $sourceFiles (Get-FileRecords $sourceWorld) 'Original world after copying'
    $metadataFiles = Get-FileRecords (Join-Path $worldRoot 'data/academy/native_structures')
    $baseline = [ordered]@{
        format = 'academy-sable-retention-v1'
        createdUtc = [DateTime]::UtcNow.ToString('o')
        sourceRun = $sourceRun
        sourceWorld = $sourceWorld
        destinationWorld = $worldRoot
        sourceStagesPassed = @('prepare', 'unavailable', 'resume')
        sourceStoppedVerified = $true
        completeWorldCopyVerified = $true
        completeWorldFiles = $sourceFiles
        metadata = $metadataFiles
    }
    $json = $baseline | ConvertTo-Json -Depth 10
    $stream = [IO.File]::Open($baselinePath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $bytes = [Text.UTF8Encoding]::new($false).GetBytes($json + "`n"); $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    Write-Output "PREPARED complete_world_files=$($sourceFiles.Count) metadata_files=$($metadataFiles.Count) baseline=$baselinePath"
    return
}

$baseline = Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json
if ($baseline.format -ne 'academy-sable-retention-v1' -or -not $baseline.completeWorldCopyVerified -or
        [IO.Path]::GetFullPath($baseline.destinationWorld) -ne $worldRoot) { throw 'Invalid/mismatched retention baseline' }
Assert-PassedSource $baseline.sourceRun
Assert-WorldStopped $baseline.sourceRun $baseline.sourceWorld
Assert-WorldStopped $gameRoot $worldRoot
$worldFiles = Get-FileRecords $worldRoot
Assert-RecordsEqual $baseline.completeWorldFiles (Get-FileRecords $baseline.sourceWorld) 'Original source world'
Assert-RecordsEqual $baseline.metadata (Get-FileRecords (Join-Path $worldRoot 'data/academy/native_structures')) 'Retained native metadata'
$result = Get-Content -LiteralPath (Join-Path $gameRoot 'sable-retention-result.txt') -Raw
$exitText = Get-Content -LiteralPath (Join-Path $gameRoot 'sable-retention-exit.txt') -Raw
if (-not $result.StartsWith("PASSED`n") -or $exitText -notmatch '(?m)^exit_status=0\r?$' -or
        $exitText -notmatch '(?m)^server_thread_terminated=true\r?$' -or $exitText -notmatch '(?m)^normal_stopping_event=true\r?$') { throw 'Retention process did not pass and stop normally' }
$storageBefore = @($baseline.completeWorldFiles | Where-Object { $_.path -like 'dimensions/academy/structure_space_*/*' })
$storageAfter = @($worldFiles | Where-Object { $_.path -like 'dimensions/academy/structure_space_*/*' })
$storageUnchanged = $true
try { Assert-RecordsEqual $storageBefore $storageAfter 'Reserved storage file observation' } catch { $storageUnchanged = $false }
$verificationPath = Join-Path $gameRoot 'sable-retention-external-verification.json'
if (Test-Path -LiteralPath $verificationPath) { throw 'External verification evidence already exists' }
$verification = [ordered]@{
    status = 'PASSED'
    checkedUtc = [DateTime]::UtcNow.ToString('o')
    baselineSha256 = (Get-FileHash -LiteralPath $baselinePath -Algorithm SHA256).Hash
    sourceCompleteWorldUnchanged = $true
    nativeMetadataByteHashesUnchanged = $true
    reservedStorageFilesByteIdenticalObservation = $storageUnchanged
    reservedStorageObservationIsContentCorruptionVerdict = $false
    metadata = (Get-FileRecords (Join-Path $worldRoot 'data/academy/native_structures'))
}
[IO.File]::WriteAllText($verificationPath, ($verification | ConvertTo-Json -Depth 10) + "`n", [Text.UTF8Encoding]::new($false))
Write-Output "PASSED original_world_unchanged=true native_metadata_unchanged=true reserved_storage_byte_observation=$storageUnchanged"
