$ErrorActionPreference = 'Stop'
$path = "$PSScriptRoot/sand_soldier.bbmodel"
$bb = Get-Content $path -Raw | ConvertFrom-Json -AsHashtable
if ($bb.ContainsKey('slam_contact_version')) { throw 'Contact correction already applied.' }
$clip = $bb.animations | Where-Object name -eq 'slam'
$chest = $bb.groups | Where-Object name -eq 'chest'
foreach ($key in $clip.animators[$chest.uuid].keyframes) {
    if ($key.channel -eq 'rotation') { $key.data_points[0].x = (-[double]$key.data_points[0].x).ToString([Globalization.CultureInfo]::InvariantCulture) }
}
# Lean into the strike, lower the centre of mass, then recover. Key at 1.5s
# matches server impact tick 30. Units are Blockbench model pixels.
$root = $bb.groups | Where-Object name -eq 'root'
$keys = @()
$anchors = @(@(0,0),@(1.2,0),@(1.5,1),@(1.7,.85),@(2.2,0))
for ($tick=0; $tick -le 132; $tick++) {
    $time = $tick/60.0
    $i=0
    while ($i -lt $anchors.Count-2 -and $time -gt $anchors[$i+1][0]) { $i++ }
    $u=[Math]::Clamp(($time-$anchors[$i][0])/($anchors[$i+1][0]-$anchors[$i][0]),0,1)
    $u=$u*$u*(3-2*$u)
    $amount=$anchors[$i][1]+($anchors[$i+1][1]-$anchors[$i][1])*$u
    $keys+=@{uuid=[guid]::NewGuid().ToString();channel='position';time=$time;interpolation='linear';data_points=@(@{x='0';y=(-4*$amount).ToString([Globalization.CultureInfo]::InvariantCulture);z=(-5.3*$amount).ToString([Globalization.CultureInfo]::InvariantCulture)})}
}
$clip.animators[$root.uuid]=@{name='root';type='bone';keyframes=$keys}
$bb.slam_contact_version=1
[IO.File]::WriteAllText($path,($bb|ConvertTo-Json -Depth 100),[Text.UTF8Encoding]::new($false))
& "$PSScriptRoot/export-rig.ps1"
