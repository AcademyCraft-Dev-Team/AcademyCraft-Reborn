param(
    [string]$PlanPath = "docs/NON_COMMON_SKILL_PROFICIENCY_PLAN.md",
    [string]$EnglishPath = "src/main/resources/assets/academy/lang/en_us.json",
    [string]$ChinesePath = "src/main/resources/assets/academy/lang/zh_cn.json"
)

$ErrorActionPreference = "Stop"
$thresholds = @(1000, 2000, 3000)
$rows = [ordered]@{}

foreach ($line in [IO.File]::ReadAllLines((Resolve-Path $PlanPath))) {
    if (-not $line.StartsWith("|")) { continue }
    $parts = $line.Split('|')
    if ($parts.Count -lt 7) { continue }
    $match = [regex]::Match($parts[1], '`([a-z0-9_]+)`')
    if (-not $match.Success) { continue }
    $id = $match.Groups[1].Value
    $effects = @()
    for ($index = 3; $index -le 5; $index++) {
        $effects += $parts[$index].Trim().Replace('`', '').Replace('**', '')
    }
    $rows[$id] = $effects
}

if ($rows.Count -ne 87) {
    throw "Expected 87 non-common skill rows, found $($rows.Count)."
}

function Add-Entries([string]$path, [System.Collections.IDictionary]$entries) {
    $resolved = Resolve-Path $path
    $text = [IO.File]::ReadAllText($resolved)
    $missing = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in $entries.GetEnumerator()) {
        $quotedKey = ConvertTo-Json -InputObject ([string]$entry.Key) -Compress
        if ($text.Contains($quotedKey + ':')) { continue }
        $quotedValue = ConvertTo-Json -InputObject ([string]$entry.Value) -Compress
        $missing.Add("  $quotedKey`: $quotedValue")
    }
    if ($missing.Count -eq 0) { return }
    $closing = $text.LastIndexOf('}')
    if ($closing -lt 0) { throw "Invalid JSON object: $path" }
    $prefix = $text.Substring(0, $closing).TrimEnd()
    $suffix = $text.Substring($closing)
    $updated = $prefix + ",`r`n" + ($missing -join ",`r`n") + "`r`n" + $suffix
    [IO.File]::WriteAllText($resolved, $updated, [Text.UTF8Encoding]::new($false))
}

$englishObject = [IO.File]::ReadAllText((Resolve-Path $EnglishPath)) | ConvertFrom-Json
$chineseEntries = [ordered]@{}
$englishEntries = [ordered]@{}
$tierNames = @("first", "second", "final")

foreach ($row in $rows.GetEnumerator()) {
    $id = $row.Key
    $displayProperty = $englishObject.PSObject.Properties["skill.academy.$id"]
    $displayName = if ($null -eq $displayProperty) { $null } else { $displayProperty.Value }
    if ([string]::IsNullOrWhiteSpace($displayName)) { $displayName = $id.Replace('_', ' ') }
    for ($index = 0; $index -lt 3; $index++) {
        $threshold = $thresholds[$index]
        $key = "skill.academy.$id.proficiency.$threshold"
        $chineseEntries[$key] = $row.Value[$index]
        $englishEntries[$key] = "$displayName unlocks its $($tierNames[$index]) proficiency enhancement at $threshold proficiency."
    }
}

Add-Entries $ChinesePath $chineseEntries
Add-Entries $EnglishPath $englishEntries

Write-Output "Synchronized $($rows.Count * 3) proficiency entries in each language file."
