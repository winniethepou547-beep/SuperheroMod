param([switch]$Force)
$ErrorActionPreference = 'Stop'
if ((Test-Path "$PSScriptRoot/sand_soldier.bbmodel") -and -not $Force) {
    throw 'Authoring source already exists. This bootstrap discards edits. Use export-rig.ps1 for normal work; -Force only to regenerate.'
}
$bb = Get-Content "$PSScriptRoot/sand_soldier_rig_draft.bbmodel" -Raw | ConvertFrom-Json -AsHashtable
$bb.name='sand_soldier'; $bb.model_identifier='sand_soldier'
$nodes=@{}
function IndexNode($n) { $nodes[$n.uuid]=$n; foreach($c in $n.children) { if($c -isnot [string]) { IndexNode $c } } }
foreach($n in $bb.outliner) { IndexNode $n }
function AddBone($name,$parent,$origin) {
    $p=$bb.groups|Where-Object name -eq $parent; $id=[guid]::NewGuid().ToString()
    $script:bb.groups+=@{name=$name;uuid=$id;origin=$origin;rotation=@(0,0,0);export=$true;visibility=$true}
    $node=@{uuid=$id;isOpen=$true;children=@()}; $nodes[$p.uuid].children+=$node; $nodes[$id]=$node
}
function AddCube($name,$bone,$from,$to) {
    $g=$bb.groups|Where-Object name -eq $bone; $id=[guid]::NewGuid().ToString(); $faces=@{}
    foreach($f in @('north','south','east','west','up','down')) { $faces[$f]=@{uv=@(0,0,16,16);texture=0} }
    $script:bb.elements+=@{name=$name;type='cube';uuid=$id;from=$from;to=$to;origin=$g.origin;rotation=@(0,0,0);box_uv=$false;visibility=$true;export=$true;faces=$faces}
    $nodes[$g.uuid].children+=$id
}
foreach($side in @('right','left')) {
    $sign=1; if($side -eq 'right'){$sign=-1}; $x=5.5*$sign
    AddBone ($side+'_spikes') ($side+'_upper_arm') @($x,24,0)
    AddCube ($side+'_spike_inner') ($side+'_spikes') @(($x-1),24,-1.5) @(($x+1),33,1.5)
    AddCube ($side+'_spike_outer') ($side+'_spikes') @(($x-3),24,-1) @(($x-1),30,2)
}
AddBone 'slam_mass' 'right_hand' @(-5.5,13,0)
AddCube 'mass_core' 'slam_mass' @(-11.5,7,-6) @(.5,19,6)
AddCube 'mass_ridge' 'slam_mass' @(-13.5,10,-4) @(2.5,16,4)
AddBone 'ranged_mass' 'chest' @(0,28,-1)
AddCube 'ranged_mass_core' 'ranged_mass' @(-3,25,-4) @(3,31,2)
function Clip($name,$duration,$loop,$tracks) {
    $animators=@{}
    foreach($track in $tracks.Keys) {
        $pair=$track.Split(':'); $bone=$pair[0]; $channel=$pair[1]
        $g=$bb.groups|Where-Object name -eq $bone; $keyframes=@()
        foreach($key in $tracks[$track]) {
            $point=@{}
            for($i=0;$i -lt 3;$i++) { $point[@('x','y','z')[$i]]=([double]$key[$i+1]).ToString([Globalization.CultureInfo]::InvariantCulture) }
            $keyframes+=@{channel=$channel;uuid=[guid]::NewGuid().ToString();time=$key[0];color=-1;interpolation='linear';data_points=@($point)}
        }
        if(-not $animators.ContainsKey($g.uuid)) { $animators[$g.uuid]=@{name=$bone;type='bone';keyframes=@()} }
        $animators[$g.uuid].keyframes+=$keyframes
    }
    $script:bb.animations+=@{uuid=[guid]::NewGuid().ToString();name=$name;length=$duration;loop=$loop;override=$false;snapping=60;animators=$animators}
}
$bb.animations=@()
Clip 'idle' 2 'loop' @{
 'chest:rotation'=@(@(0,0,0,0),@(.5,1,0,0),@(1,2,0,0),@(1.5,1,0,0),@(2,0,0,0))
 'right_forearm:rotation'=@(@(0,8,0,0),@(1,11,0,0),@(2,8,0,0))
 'left_forearm:rotation'=@(@(0,10,0,0),@(1,8,0,0),@(2,10,0,0))
}
Clip 'walk' 1 'loop' @{
 'right_thigh:rotation'=@(@(0,25,0,0),@(.25,0,0,0),@(.5,-25,0,0),@(.75,0,0,0),@(1,25,0,0))
 'left_thigh:rotation'=@(@(0,-25,0,0),@(.25,0,0,0),@(.5,25,0,0),@(.75,0,0,0),@(1,-25,0,0))
 'right_shin:rotation'=@(@(0,0,0,0),@(.25,-28,0,0),@(.5,0,0,0),@(1,0,0,0))
 'left_shin:rotation'=@(@(0,0,0,0),@(.5,0,0,0),@(.75,-28,0,0),@(1,0,0,0))
 'right_upper_arm:rotation'=@(@(0,-20,0,0),@(.5,20,0,0),@(1,-20,0,0))
 'left_upper_arm:rotation'=@(@(0,20,0,0),@(.5,-20,0,0),@(1,20,0,0))
}
foreach($side in @('right','left')) {
 $s=1; if($side -eq 'left'){$s=-1}
 $t=@{'chest:rotation'=@(@(0,0,0,0),@(.18,-5,(-22*$s),0),@(.3,8,(24*$s),0),@(.4,9,(28*$s),0),@(.55,2,(7*$s),0),@(.65,0,0,0))}
 $t[$side+'_upper_arm:rotation']=@(@(0,0,0,0),@(.18,70,(-45*$s),(-12*$s)),@(.3,90,(35*$s),0),@(.4,80,(45*$s),0),@(.55,20,(8*$s),0),@(.65,0,0,0))
 $t[$side+'_forearm:rotation']=@(@(0,8,0,0),@(.18,70,0,0),@(.3,8,0,0),@(.4,5,0,0),@(.65,8,0,0))
 Clip ('strike_'+$side) .65 'once' $t
}
Clip 'spawn' 1.2 'once' @{
 'root:scale'=@(@(0,.8,.025,.8),@(.2,.85,.15,.85),@(.45,.92,.45,.92),@(.8,1,.9,1),@(1,1,1.04,1),@(1.2,1,1,1))
 'chest:rotation'=@(@(0,32,0,0),@(.6,25,0,0),@(1,4,0,0),@(1.2,0,0,0))
 'right_forearm:rotation'=@(@(0,75,0,0),@(.6,65,0,0),@(1.2,8,0,0))
 'left_forearm:rotation'=@(@(0,65,0,0),@(.6,75,0,0),@(1.2,8,0,0))
}
Clip 'crumble' .6 'once' @{
 'root:scale'=@(@(0,1,1,1),@(.2,1.05,.8,1.05),@(.4,1.12,.35,1.12),@(.6,1.2,.01,1.2))
 'chest:rotation'=@(@(0,0,0,0),@(.25,18,8,0),@(.6,65,15,0))
 'right_upper_arm:rotation'=@(@(0,0,0,0),@(.25,-15,0,-25),@(.6,0,0,-60))
 'left_upper_arm:rotation'=@(@(0,0,0,0),@(.25,-5,0,18),@(.6,0,0,55))
}
Clip 'aim' 1.1 'once' @{
 'chest:rotation'=@(@(0,0,0,0),@(.7,-7,0,0),@(1.1,4,0,0))
 'right_upper_arm:rotation'=@(@(0,0,0,0),@(.7,45,-12,-15),@(1.1,72,0,0))
 'left_upper_arm:rotation'=@(@(0,0,0,0),@(.7,45,12,15),@(1.1,72,0,0))
 'ranged_mass:scale'=@(@(0,.01,.01,.01),@(.75,1,1,1),@(1.1,1.15,1.15,1.15))
}
Clip 'slam' 2.2 'once' @{
 'right_upper_arm:rotation'=@(@(0,0,0,0),@(.35,95,0,-18),@(.7,165,0,-20),@(1.2,165,0,-20),@(1.5,72,0,-10),@(1.7,50,0,-5),@(2.2,0,0,0))
 'left_upper_arm:rotation'=@(@(0,0,0,0),@(.35,95,0,18),@(.7,165,0,20),@(1.2,165,0,20),@(1.5,72,0,10),@(1.7,50,0,5),@(2.2,0,0,0))
 'chest:rotation'=@(@(0,0,0,0),@(.7,-12,0,0),@(1.2,-12,0,0),@(1.5,52,0,0),@(1.7,45,0,0),@(2.2,0,0,0))
 'slam_mass:scale'=@(@(0,.01,.01,.01),@(.7,.01,.01,.01),@(1.2,1,1,1),@(1.5,1,1,1),@(1.65,.1,.1,.1),@(2.2,.01,.01,.01))
}
[IO.File]::WriteAllText("$PSScriptRoot/sand_soldier.bbmodel",($bb|ConvertTo-Json -Depth 100),[Text.UTF8Encoding]::new($false))
& "$PSScriptRoot/export-rig.ps1"
