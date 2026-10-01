// Forward kinematics against the exported rig; Minecraft model pixels, then world blocks.
const fs = require('node:fs');
const path = require('node:path');
const rig = JSON.parse(fs.readFileSync(path.join(__dirname, '../../src/main/resources/assets/superheromod/rigs/sand_soldier.json')));
function pose(clip, time) {
  const bones = Object.fromEntries(rig.bones.map(b => [b.name, {...b, p:[...b.offset], r:[0,0,0], s:[1,1,1]}]));
  for(const t of rig.clips[clip].tracks) {
    let i=0; while(i+1<t.keys.length && t.keys[i+1].time<=time)i++;
    const a=t.keys[i], b=t.keys[Math.min(i+1,t.keys.length-1)];
    const u=a===b?0:Math.max(0,Math.min(1,(time-a.time)/(b.time-a.time)));
    const v=a.value.map((x,j)=>x+(b.value[j]-x)*u), bone=bones[t.bone];
    if(t.channel==='rotation')bone.r=v;
    if(t.channel==='scale')bone.s=v;
    if(t.channel==='position')bone.p=bone.p.map((x,j)=>x+v[j]);
  }
  bones.root.s[0]*=1.12; bones.root.s[2]*=1.12;
  function point(name,v) {
    const b=bones[name]; let [x,y,z]=v.map((v,i)=>v*b.s[i]);
    const [rx,ry,rz]=b.r;
    [y,z]=[y*Math.cos(rx)-z*Math.sin(rx),y*Math.sin(rx)+z*Math.cos(rx)];
    [x,z]=[x*Math.cos(ry)+z*Math.sin(ry),-x*Math.sin(ry)+z*Math.cos(ry)];
    [x,y]=[x*Math.cos(rz)-y*Math.sin(rz),x*Math.sin(rz)+y*Math.cos(rz)];
    const p=[x+b.p[0],y+b.p[1],z+b.p[2]];
    return b.parent?point(b.parent,p):p;
  }
  return {bones,point};
}
function measure(time) {
 const {bones,point}=pose('slam',time);
 const hand=point('right_hand',[0,0,0]);
 const vertices=[];
 for(const c of bones.slam_mass.cubes) for(let mask=0;mask<8;mask++) {
   const p=point('slam_mass',c.from.map((v,i)=>v+((mask>>i)&1)*c.size[i]));
   vertices.push([p[0]/16*2.1,(24-p[1])/16*2.1,-p[2]/16*2.1]);
 }
 return {time,handHeight:(24-hand[1])/16*2.1,massBottom:Math.min(...vertices.map(v=>v[1])),massForward:vertices.reduce((a,v)=>a+v[2],0)/vertices.length};
}
const assert=require('node:assert/strict');
const impact=measure(30/20);
assert.ok(Math.abs(impact.massBottom)<.12, 'Slam misses ground at server impact tick');
assert.ok(Math.abs(impact.massForward-2.4)<.15, 'Visual slam differs from damage centre');
assert.ok(measure(1.2).massBottom>3, 'Gathered mass must visibly be overhead');
assert.ok(Math.abs(measure(2.2).handHeight-measure(0).handHeight)<1e-6, 'Recovery does not return to rest');
for(let frame=0;frame<=132;frame++) {
 const sample=measure(frame/60);
 assert.ok(sample.massBottom>-.12, `Mass penetrates floor at ${sample.time}s`);
}
console.log('PASS: flat-ground impact height, damage-centre alignment, overhead gather, recovery and full-clip floor clearance.');
console.log(JSON.stringify(impact));
