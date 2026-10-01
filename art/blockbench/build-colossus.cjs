// Original Minecraft-scale sand titan. Source and runtime geometry share these definitions.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
if(fs.existsSync(path.join(__dirname,'colossus.bbmodel'))&&!process.argv.includes('--force')) {
 throw Error('Existing editable model preserved. Use --force only to deliberately regenerate it.');
}
const bones=[];
function bone(name,parent,offset=[0,0,0]) { const b={name,parent,offset,cubes:[]}; bones.push(b); return b; }
function box(b,x,y,z,w,h,d){ b.cubes.push({from:[x,y,z],size:[w,h,d]}); }
const root=bone('root','');
const lower=bone('lower_sand_mass','root');
box(lower,-18,94,-14,36,34,28);
box(lower,-25,118,-19,50,25,38);
box(lower,-32,139,-24,64,21,48);
// Flowing buttresses around a continuous core: long slopes, not random protruding boulders.
for(let i=0;i<12;i++){
 const a=i*Math.PI*2/12, r=28+(i%3)*3;
 const x=Math.cos(a)*r,z=Math.sin(a)*r*.78;
 const top=126+(i%4)*4;
 box(lower,x-7,top,z-7,14,160-top,14);
 box(lower,x*1.18-6,151,z*1.2-6,12,9,12);
}
const torso=bone('torso','root');
box(torso,-27,38,-14,54,31,28);
box(torso,-23,67,-13,46,22,26);
box(torso,-18,87,-12,36,19,24);
// Broad paired pectorals, sternum and tapering abdominal terraces.
box(torso,-25,43,-18,23,19,6); box(torso,2,43,-18,23,19,6);
box(torso,-4,44,-19,8,28,7);
for(let row=0;row<3;row++) for(let side of [-1,1])box(torso,side<0?-17:2,68+row*10,-15,15,8,4);
box(torso,-23,45,12,46,30,6);
box(torso,-13,28,-10,26,14,20);
const head=bone('head','torso',[0,28,0]);
// Faceless, recessed monolith; no nose, eyebrows or mouth.
box(head,-11,-24,-9,22,24,20);
box(head,-8,-28,-7,16,10,16);
function crag(name,parent,offset,size,rotation){
 const b=bone(name,parent,offset); b.rotation=rotation;
 box(b,-size[0]/2,-size[1]/2,-size[2]/2,...size);
}
crag('crown_shear','head',[-8,-18,1],[10,22,17],[.22,-.18,-.30]);
crag('crown_shear_2','head',[9,-9,2],[9,20,17],[-.15,.21,.24]);
for(let i=0;i<14;i++){
 const a=i*Math.PI*2/14;
 crag('dune_crag_'+i,'lower_sand_mass',[Math.cos(a)*29,136+(i%3)*5,Math.sin(a)*22],
 [13,27,14],[Math.sin(a)*.32,i*.71,Math.cos(a)*.30]);
}
for(let side of [-1,1])for(let i=0;i<3;i++){
 crag('rib_crag_'+side+'_'+i,'torso',[side*(24-i*3),55+i*16,1],[12,20,26],
 [.13*(i-1),side*.16,side*(.25+i*.07)]);
}
for(const sign of [-1,1]) {
 const side=sign<0?'right':'left';
 const arm=bone(side+'_arm','torso',[sign*28,46,0]);
 const x=sign<0?-19:-2;
 box(arm,x,-7,-11,21,33,22);
 box(arm,sign<0?-22:-3,-12,-13,25,14,26);
 box(arm,sign<0?-20:12,1,-8,8,20,17);
 const fore=bone(side+'_forearm',side+'_arm',[sign*8,26,0]);
 box(fore,-11,0,-10,22,23,20);
 box(fore,-13,17,-12,26,16,24);
 for(let k=0;k<4;k++)box(fore,-12+k*6,23,-14,5,10,5);
 box(fore,sign<0?10:-16,18,-7,6,12,10);
 bone(side+'_crags',side+'_arm');
 for(let i=0;i<3;i++)crag(side+'_shoulder_crag_'+i,side+'_crags',[sign*(11+i*4),-6+i*8,2],
 [13,22,25],[i*.13,sign*.2,sign*(.35+i*.12)]);
 crag(side+'_fore_crag',side+'_forearm',[sign*10,9,1],[11,25,19],[.15,.2,sign*.3]);
}
// Compact fused striking mass. No floating stick or head above the shoulder.
const mace=bone('mace','right_forearm',[0,26,0]);
box(mace,-14,-5,-14,28,19,28);
box(mace,-17,0,-10,34,10,20);
box(mace,-10,1,-17,20,11,34);
for(let i=0;i<4;i++)box(mace,-12+i*7,10,-12,5,7,24);
const sword=bone('sword','right_arm');
box(sword,-13,-8,-11,26,28,22);
box(sword,-11,18,-8,22,38,16);
box(sword,-9,54,-5,18,34,10);
box(sword,-6,86,-3,12,24,6);
box(sword,-3,108,-2,6,12,4);
crag('blade_ridge','sword',[0,44,0],[7,65,19],[0,.35,0]);
const output=path.join(__dirname,'../../src/main/resources/assets/superheromod/rigs/colossus.json');
fs.writeFileSync(output,JSON.stringify({version:1,bones,clips:{}},null,2));
const groups=[],elements=[],outliner=[],nodes={},origins={};
for(const b of bones){
 const parent=b.parent?origins[b.parent]:[0,24,0];
 const o=[parent[0]+b.offset[0],parent[1]-b.offset[1],parent[2]+b.offset[2]];
 origins[b.name]=o; const id=crypto.randomUUID();
 const r=b.rotation||[0,0,0];
 groups.push({name:b.name,uuid:id,origin:o,rotation:[-r[0]*180/Math.PI,r[1]*180/Math.PI,-r[2]*180/Math.PI],export:true,visibility:true});
 const n={uuid:id,isOpen:true,children:[]}; nodes[b.name]=n;
 if(b.parent)nodes[b.parent].children.push(n);else outliner.push(n);
 for(const [i,c] of b.cubes.entries()){
  const uuid=crypto.randomUUID(),[x,y,z]=c.from,[w,h,d]=c.size;
  const faces=Object.fromEntries(['north','south','east','west','up','down'].map(f=>[f,{uv:[0,0,16,16],texture:0}]));
  elements.push({uuid,name:b.name+'_'+i,type:'cube',from:[o[0]+x,o[1]-y-h,o[2]+z],to:[o[0]+x+w,o[1]-y,o[2]+z+d],origin:o,rotation:[0,0,0],box_uv:false,faces,visibility:true,export:true});n.children.push(uuid);
 }
}
const texture=JSON.parse(fs.readFileSync(path.join(__dirname,'sand_soldier.bbmodel'))).textures;
fs.writeFileSync(path.join(__dirname,'colossus.bbmodel'),JSON.stringify({meta:{format_version:'5.0',model_format:'free',box_uv:false},name:'colossus',model_identifier:'colossus',resolution:{width:16,height:16},groups,elements,outliner,textures:texture,animations:[]},null,2));
console.log(`Colossus: ${bones.length} bones, ${elements.length} cubes, 10-block crown-to-ground height.`);
