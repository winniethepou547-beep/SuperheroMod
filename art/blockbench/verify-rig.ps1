param([string]$Source = "$PSScriptRoot/sand_soldier.bbmodel")
$ErrorActionPreference = 'Stop'
$generated = "$PSScriptRoot/../../build/rig-check/sand_soldier.json"
& "$PSScriptRoot/export-rig.ps1" -Source $Source -Output $generated
$actual = Get-Content $generated -Raw | ConvertFrom-Json -AsHashtable
$shipped = Get-Content "$PSScriptRoot/../../src/main/resources/assets/superheromod/rigs/sand_soldier.json" -Raw | ConvertFrom-Json -AsHashtable
function Canonical($value) {
    if ($value -is [System.Collections.IDictionary]) {
        $ordered = [ordered]@{}
        foreach ($key in @($value.Keys | Sort-Object)) { $ordered[$key] = Canonical $value[$key] }
        return $ordered
    }
    if ($value -is [array]) { return ,@($value | ForEach-Object { Canonical $_ }) }
    return $value
}
$a = Canonical $actual | ConvertTo-Json -Depth 100 -Compress
$b = Canonical $shipped | ConvertTo-Json -Depth 100 -Compress
if ($a -cne $b) { throw 'Editor source differs from runtime asset. Run export-rig.ps1 and review changes.' }
$names = @{}
foreach ($bone in $actual.bones) {
    if ($bone.parent -and -not $names.ContainsKey($bone.parent)) { throw "Missing/late parent: $($bone.name)" }
    $names[$bone.name] = $true
}
foreach ($clip in $actual.clips.Values) {
    foreach ($track in $clip.tracks) {
        if (-not $names.ContainsKey($track.bone)) { throw "Unknown bone: $($track.bone)" }
        $last = -1.0
        foreach ($key in $track.keys) {
            if ($key.time -le $last -or $key.time -gt $clip.length) { throw 'Invalid key timing' }
            $last = $key.time
            foreach ($number in $key.value) {
                if (-not [double]::IsFinite($number)) { throw 'Non-finite animation value' }
            }
        }
    }
}
Write-Output 'PASS: editor/runtime parity, hierarchy, finite transforms and key timing.'
