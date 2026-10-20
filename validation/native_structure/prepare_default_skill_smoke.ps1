param(
    [Parameter(Mandatory=$true)][string]$Name,
    [switch]$Fresh
)
$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($Name -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Name must contain only letters, numbers, hyphens and underscores' }
$auditRoot = Join-Path $repoRoot "run/native-sublevel-direct-smoke-$Name"
if (Test-Path -LiteralPath $auditRoot) { throw 'Audit directory already exists' }
$sourceRoot = Join-Path $repoRoot 'run'
$sourceWorld = Join-Path $sourceRoot 'saves/test'
if ($Fresh) {
    $sourceRoot = Join-Path $repoRoot 'run/native-sublevel-direct-smoke'
    $sourceWorld = Join-Path $sourceRoot 'saves/Native SubLevel Direct Smoke'
}
$auditWorld = Join-Path $auditRoot 'saves/test'
$hashBefore = @(Get-ChildItem -LiteralPath $sourceWorld -Recurse -File | Where-Object { $_.Name -ne 'session.lock' } | ForEach-Object {
    # Region files can remain open while the source client is paused. Read without taking a write lock.
    $hashStream = [System.IO.File]::Open($_.FullName, [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        [pscustomobject]@{ Path = [System.IO.Path]::GetRelativePath($sourceWorld, $_.FullName);
            Hash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($hashStream)) }
    } finally { $hashStream.Dispose() }
})
New-Item -ItemType Directory -Path $auditWorld -Force | Out-Null
if ($Fresh) {
    New-Item -ItemType Directory -Path (Join-Path $auditWorld 'dimensions') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $auditWorld 'data') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $sourceWorld 'level.dat') -Destination $auditWorld
    Copy-Item -LiteralPath (Join-Path $sourceWorld 'dimensions/minecraft') -Destination (Join-Path $auditWorld 'dimensions') -Recurse
    Copy-Item -LiteralPath (Join-Path $sourceWorld 'data/minecraft') -Destination (Join-Path $auditWorld 'data') -Recurse
} else {
    Get-ChildItem -LiteralPath $sourceWorld | Where-Object { $_.Name -ne 'session.lock' } | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $auditWorld -Recurse
    }
}
Copy-Item -LiteralPath (Join-Path $sourceRoot 'options.txt') -Destination $auditRoot
Copy-Item -LiteralPath (Join-Path $sourceRoot 'config') -Destination $auditRoot -Recurse
$hashBefore | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $auditRoot 'source-world-sha256.json')
Write-Output $auditRoot
