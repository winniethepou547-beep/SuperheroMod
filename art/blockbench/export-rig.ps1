param(
    [string]$Source = "$PSScriptRoot/sand_soldier.bbmodel",
    [string]$Output = "$PSScriptRoot/../../src/main/resources/assets/superheromod/rigs/sand_soldier.json"
)
$ErrorActionPreference = 'Stop'
$bb = Get-Content -LiteralPath $Source -Raw | ConvertFrom-Json
if ($bb.meta.format_version -ne '5.0') { throw 'Only Blockbench 5.0 projects are supported.' }
$groupMap = @{}; $elementMap = @{}; $parents = @{}
foreach ($g in $bb.groups) { $groupMap[$g.uuid] = $g }
foreach ($e in $bb.elements) { $elementMap[$e.uuid] = $e }
$bones = [Collections.Generic.List[object]]::new()
function Visit($node, $parent) {
    $g = $groupMap[$node.uuid]
    if (-not $g) { throw 'Missing group' }
    $origin = $g.origin
    $parentOrigin = @(0,24,0); $parentName = ''
    if ($parent) { $parentOrigin = $parent.origin; $parentName = $parent.name }
    $offset = @(($origin[0]-$parentOrigin[0]), ($parentOrigin[1]-$origin[1]), ($origin[2]-$parentOrigin[2]))
    $cubes = [Collections.Generic.List[object]]::new()
    foreach ($child in $node.children) {
        if ($child -isnot [string]) { continue }
        $cube = $elementMap[$child]
        if (-not $cube -or $cube.type -ne 'cube') { throw 'Only cubes are supported' }
        if (@($cube.rotation | Where-Object { $_ -ne 0 }).Count) { throw 'Use an animated bone for rotated cubes' }
        $cubes.Add(@{
            from = @(($cube.from[0]-$origin[0]), ($origin[1]-$cube.to[1]), ($cube.from[2]-$origin[2]))
            size = @(($cube.to[0]-$cube.from[0]), ($cube.to[1]-$cube.from[1]), ($cube.to[2]-$cube.from[2]))
        })
    }
    $bone = @{name=$g.name; parent=$parentName; offset=$offset; cubes=@($cubes.ToArray())}
    if (@($g.rotation | Where-Object { $_ -ne 0 }).Count) {
        $bone.rotation = @((-$g.rotation[0]*[Math]::PI/180), ($g.rotation[1]*[Math]::PI/180), (-$g.rotation[2]*[Math]::PI/180))
    }
    $bones.Add($bone)
    foreach ($child in $node.children) { if ($child -isnot [string]) { Visit $child $g } }
}
foreach ($node in $bb.outliner) { Visit $node $null }
if (@($bones | Group-Object name | Where-Object Count -gt 1).Count) { throw 'Duplicate bone names' }
$clips = @{}
foreach ($clip in $bb.animations) {
    $tracks = [Collections.Generic.List[object]]::new()
    foreach ($property in $clip.animators.psobject.Properties) {
        $animator = $property.Value
        if ($animator.type -ne 'bone') { throw 'Only bone animation is supported' }
        foreach ($channel in @('rotation','position','scale')) {
            $keys = [Collections.Generic.List[object]]::new()
            foreach ($k in @($animator.keyframes | Where-Object channel -eq $channel | Sort-Object time)) {
                if ($k.interpolation -ne 'linear' -or $k.data_points.Count -ne 1) { throw 'Use linear numeric keys; bake other curves first' }
                $p = $k.data_points[0]
                $v = @([double]::Parse($p.x,[Globalization.CultureInfo]::InvariantCulture),[double]::Parse($p.y,[Globalization.CultureInfo]::InvariantCulture),[double]::Parse($p.z,[Globalization.CultureInfo]::InvariantCulture))
                if ($channel -eq 'rotation') { $v=@((-$v[0]*[Math]::PI/180), ($v[1]*[Math]::PI/180), (-$v[2]*[Math]::PI/180)) }
                if ($channel -eq 'position') { $v[1] = -$v[1] }
                $keys.Add(@{time=[double]$k.time;value=$v})
            }
            if ($keys.Count) { $tracks.Add(@{bone=$groupMap[$property.Name].name;channel=$channel;keys=@($keys.ToArray())}) }
        }
    }
    $clips[$clip.name]=@{length=$clip.length;loop=($clip.loop -eq 'loop');tracks=@($tracks.ToArray())}
}
$result=@{version=1;bones=@($bones.ToArray());clips=$clips}
$outputPath=[IO.Path]::GetFullPath($Output)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($outputPath)) | Out-Null
[IO.File]::WriteAllText($outputPath, ($result | ConvertTo-Json -Depth 100), [Text.UTF8Encoding]::new($false))
Write-Output "Exported $($bones.Count) bones, $($clips.Count) clips: $outputPath"
