param(
    [Parameter(Mandatory=$true)][string[]]$RunIds,
    [Parameter(Mandatory=$true)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
function Percentile($Values, [double]$Fraction) {
    $ordered = @($Values | Sort-Object)
    if ($ordered.Count -eq 0) { return $null }
    return [double]$ordered[[Math]::Max(0, [int][Math]::Ceiling($ordered.Count * $Fraction) - 1)]
}
$summaries = foreach ($id in $RunIds) {
    if ($id -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Invalid RunId' }
    $path = Join-Path $repo "run/sable-compat/parity-$id/server/result.json"
    $data = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    if ($data.measurement_status -ne 'COMPLETE' -or $data.alignment_valid -ne $true) {
        throw "Incomplete or unaligned sample: $id"
    }
    $metrics = [ordered]@{}
    foreach ($phase in @('lift','flight')) {
        $values = @($data.samples | Where-Object phase -eq $phase | ForEach-Object server_tick_ms)
        $metrics[$phase] = [ordered]@{n=$values.Count; p50_ms=(Percentile $values .5); p95_ms=(Percentile $values .95); max_ms=(Percentile $values 1)}
    }
    [pscustomobject][ordered]@{
        run=$id; backend=$data.backend; fixture=$data.fixture; cells_sha256=$data.cell_sha256
        raw_sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
        measurement_status=$data.measurement_status; alignment_error=$data.alignment_error
        capture_call_ms=$data.capture_call_ms; capture_ready_ms=$data.capture_ready_ms
        mass=$data.mass; lift_status=$data.lift_status; reached_tick=$data.reached_tick
        hover_streak=$data.longest_hover_streak; prelaunch_rise=$data.prelaunch_rise
        launch_speed=$data.launch_speed; flight_dx=$data.flight_20tick_dx; flight_seconds=$data.flight_20tick_seconds
        ticks=$metrics
    }
}
if (@($summaries.cells_sha256 | Select-Object -Unique).Count -ne 1) { throw 'Cannot compare different cell fixtures' }
$comparisons = foreach ($fixture in @('ground','floating')) {
    $local = @($summaries | Where-Object { $_.backend -eq 'local' -and $_.fixture -eq $fixture })
    $sable = @($summaries | Where-Object { $_.backend -eq 'sable' -and $_.fixture -eq $fixture })
    if (!$local.Count -or $local.Count -ne $sable.Count) { throw "Unpaired repetition count: $fixture" }
    # Means across independent runs; phase percentiles remain per run in the evidence above.
    $lc = ($local.capture_call_ms | Measure-Object -Average).Average
    $sc = ($sable.capture_call_ms | Measure-Object -Average).Average
    $lp = ($local | ForEach-Object { $_.ticks.lift.p95_ms } | Measure-Object -Average).Average
    $sp = ($sable | ForEach-Object { $_.ticks.lift.p95_ms } | Measure-Object -Average).Average
    [ordered]@{fixture=$fixture; runs_per_backend=$local.Count; local_capture_mean_ms=$lc; sable_capture_mean_ms=$sc
        capture_ratio_local_over_sable=$lc/$sc; local_lift_p95_mean_ms=$lp; sable_lift_p95_mean_ms=$sp
        lift_p95_cost_ratio_local_over_sable=$lp/$sp
        performance_equivalence='UNVERIFIED: check lift outcome, mass/material, geometry and client metrics separately'}
}
[ordered]@{schema='academy-structure-parity-pilot-1'; runs=@($summaries); comparisons=@($comparisons)} |
    ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $OutputFile -Encoding UTF8
Write-Output $OutputFile
