# One-time proportion/curve migration; preserves texture and existing authoring data.
$ErrorActionPreference = 'Stop'
$path = "$PSScriptRoot/sand_soldier.bbmodel"
$bb = Get-Content $path -Raw | ConvertFrom-Json -AsHashtable
if ($bb.ContainsKey('sandman_polish_version')) { throw 'Already migrated; edit the source model directly.' }
foreach ($cube in $bb.elements) {
    if ($cube.name -in @('mass_core','mass_ridge')) {
        for ($axis=0; $axis -lt 3; $axis++) {
            $center = ($cube.from[$axis] + $cube.to[$axis]) / 2
            $half = ($cube.to[$axis] - $cube.from[$axis]) * .34
            $cube.from[$axis] = $center - $half
            $cube.to[$axis] = $center + $half
        }
    }
}
function Key($time,$channel,$values) {
    return @{uuid=[guid]::NewGuid().ToString();time=$time;channel=$channel;interpolation='linear';data_points=@(@{x=[string]$values[0];y=[string]$values[1];z=[string]$values[2]})}
}
foreach ($clip in $bb.animations) {
    if ($clip.name -eq 'aim') {
        $clip.length = 1.5
        foreach ($animator in $clip.animators.Values) {
            foreach ($channel in @('rotation','scale')) {
                $keys = @($animator.keyframes | Where-Object channel -eq $channel | Sort-Object time)
                if (-not $keys.Count) { continue }
                $p = $keys[0].data_points[0]
                $animator.keyframes += Key 1.5 $channel @($p.x,$p.y,$p.z)
                if ($animator.name -eq 'ranged_mass') {
                    $animator.keyframes += Key 1.15 'scale' @(.01,.01,.01)
                }
            }
        }
    }
    # Shape-preserving Hermite tangents, baked at 60Hz for matching editor/runtime playback.
    foreach ($animator in $clip.animators.Values) {
        $baked = @()
        foreach ($channel in @('rotation','position','scale')) {
            $keys = @($animator.keyframes | Where-Object channel -eq $channel | Sort-Object time)
            if ($keys.Count -lt 2) { $baked += $keys; continue }
            $tangents = @()
            for ($i=0; $i -lt $keys.Count; $i++) {
                $tangent = @(0.0,0.0,0.0)
                if ($i -gt 0 -and $i -lt $keys.Count-1) {
                    foreach ($axis in 0..2) {
                        $name = @('x','y','z')[$axis]
                        $d0 = ([double]$keys[$i].data_points[0][$name]-[double]$keys[$i-1].data_points[0][$name])/($keys[$i].time-$keys[$i-1].time)
                        $d1 = ([double]$keys[$i+1].data_points[0][$name]-[double]$keys[$i].data_points[0][$name])/($keys[$i+1].time-$keys[$i].time)
                        if ($d0*$d1 -gt 0) { $tangent[$axis] = 2*$d0*$d1/($d0+$d1) }
                    }
                }
                $tangents += ,$tangent
            }
            for ($i=0; $i -lt $keys.Count-1; $i++) {
                $a=$keys[$i]; $b=$keys[$i+1]; $dt=$b.time-$a.time
                $steps=[Math]::Max(1,[Math]::Ceiling($dt*60))
                for ($j=0; $j -lt $steps; $j++) {
                    $u=$j/$steps; $values=@()
                    foreach ($axis in 0..2) {
                        $name=@('x','y','z')[$axis]
                        $values += (2*$u*$u*$u-3*$u*$u+1)*[double]$a.data_points[0][$name] + ($u*$u*$u-2*$u*$u+$u)*$dt*$tangents[$i][$axis] + (-2*$u*$u*$u+3*$u*$u)*[double]$b.data_points[0][$name] + ($u*$u*$u-$u*$u)*$dt*$tangents[$i+1][$axis]
                    }
                    $baked += Key ($a.time+$u*$dt) $channel $values
                }
            }
            $baked += $keys[-1]
        }
        $animator.keyframes = $baked
    }
}
$bb.sandman_polish_version=1
[IO.File]::WriteAllText($path,($bb|ConvertTo-Json -Depth 100),[Text.UTF8Encoding]::new($false))
& "$PSScriptRoot/export-rig.ps1"
