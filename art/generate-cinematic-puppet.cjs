// Reproducible glTF model fixture; no external assets or dependencies.
// Model coordinates intentionally match the documented Minecraft puppet profile.
const fs=require('fs'),path=require('path');
const bones=[
 ['head','chest',[0,-6,0]],['chest','hips',[0,-6,0]],['hips',null,[0,12,0]],
 ['right_upper_arm','chest',[-5,-4,0]],['right_lower_arm','right_upper_arm',[0,4,0]],
 ['left_upper_arm','chest',[5,-4,0]],['left_lower_arm','left_upper_arm',[0,4,0]],
 ['right_upper_leg','hips',[-1.9,0,0]],['right_lower_leg','right_upper_leg',[0,6,0]],
 ['left_upper_leg','hips',[1.9,0,0]],['left_lower_leg','left_upper_leg',[0,6,0]],
 ['right_wrist','right_lower_arm',[0,4,0]],['left_wrist','left_lower_arm',[0,4,0]]
];
const boneIndex=name=>bones.findIndex(b=>b[0]===name);
function global(i){const b=bones[i],p=b[1]?global(boneIndex(b[1])):[0,0,0];return b[2].map((v,k)=>v/16+p[k]);}
function generate(slim){
 const positions=[],uv=[],weights=[],joints=[],indices=[];
 function face(p,u,v,du,dv,a,b,joint,reverse,normal){
  const d=p[1].map((x,k)=>x-p[0][k]),e=p[2].map((x,k)=>x-p[0][k]);
  const n=[d[1]*e[2]-d[2]*e[1],d[2]*e[0]-d[0]*e[2],d[0]*e[1]-d[1]*e[0]];
  const flip=n.reduce((s,x,k)=>s+x*normal[k],0)<0;
  const tex=[[u,v],[u+du,v],[u+du,v+dv],[u,v+dv]],base=positions.length/3;
  for(let i=0;i<4;i++){
   const k=flip?3-i:i;let t=Math.max(0,Math.min(1,(p[k][1]-joint+2)/4));t=t*t*(3-2*t);if(reverse)t=1-t;
   positions.push(...p[k].map(x=>x/16));uv.push(...tex[k].map(x=>x/64));
   if((b===4||b===6)&&p[k][1]>=9){
    let w=Math.max(0,Math.min(1,(p[k][1]-9)/4));w=w*w*(3-2*w);
    joints.push(b,b===4?11:12,0,0);weights.push(1-w,w,0,0);
   }else{joints.push(a,b,0,0);weights.push(1-t,t,0,0);}
  }
  indices.push(base,base+1,base+2,base,base+2,base+3);
 }
 function box(x,y,z,w,h,d,u,v,a,b,joint,reverse=false){
  for(let row=0;row<h;row++){
   const top=y+row,bot=top+1;
   face([[x,top,z],[x+w,top,z],[x+w,bot,z],[x,bot,z]],u+d,v+d+row,w,1,a,b,joint,reverse,[0,0,-1]);
   face([[x,top,z+d],[x+w,top,z+d],[x+w,bot,z+d],[x,bot,z+d]],u+d+w+d,v+d+row,w,1,a,b,joint,reverse,[0,0,1]);
   face([[x,top,z],[x,top,z+d],[x,bot,z+d],[x,bot,z]],u,v+d+row,d,1,a,b,joint,reverse,[-1,0,0]);
   face([[x+w,top,z],[x+w,top,z+d],[x+w,bot,z+d],[x+w,bot,z]],u+d+w,v+d+row,d,1,a,b,joint,reverse,[1,0,0]);
  }
  face([[x,y,z],[x+w,y,z],[x+w,y,z+d],[x,y,z+d]],u+d,v,w,d,a,b,joint,reverse,[0,-1,0]);
  face([[x,y+h,z],[x+w,y+h,z],[x+w,y+h,z+d],[x,y+h,z+d]],u+d+w,v,w,d,a,b,joint,reverse,[0,1,0]);
 }
 box(-4,-8,-4,8,8,8,0,0,0,0,0);
 box(-4,0,-2,8,12,4,16,16,2,1,6,true);
 box(slim?-7:-8,0,-2,slim?3:4,12,4,40,16,3,4,6);
 box(4,0,-2,slim?3:4,12,4,32,48,5,6,6);
 box(-3.9,12,-2,4,12,4,0,16,7,8,18);
 box(-.1,12,-2,4,12,4,16,48,9,10,18);
 const chunks=[],views=[],accessors=[];let length=0;
 function accessor(values,type,size,short=false){
  const padding=(4-length%4)%4;if(padding){chunks.push(Buffer.alloc(padding));length+=padding;}
  const bytes=Buffer.alloc(values.length*(short?2:4));values.forEach((v,i)=>short?bytes.writeUInt16LE(v,i*2):bytes.writeFloatLE(v,i*4));
  const view=views.length;views.push({buffer:0,byteOffset:length,byteLength:bytes.length});chunks.push(bytes);length+=bytes.length;
  const a={bufferView:view,componentType:short?5123:5126,count:values.length/size,type};
  if(type==='SCALAR'&&!short){a.min=[Math.min(...values)];a.max=[Math.max(...values)];}
  if(type==='VEC3'){a.min=[0,1,2].map(k=>Math.min(...values.filter((_,i)=>i%3===k)));a.max=[0,1,2].map(k=>Math.max(...values.filter((_,i)=>i%3===k)));}
  accessors.push(a);return accessors.length-1;
 }
 const attributes={POSITION:accessor(positions,'VEC3',3),TEXCOORD_0:accessor(uv,'VEC2',2),JOINTS_0:accessor(joints,'VEC4',4,true),WEIGHTS_0:accessor(weights,'VEC4',4)};
 const index=accessor(indices,'SCALAR',1,true),matrices=[];
 bones.forEach((_,i)=>{const p=global(i);matrices.push(1,0,0,0,0,1,0,0,0,0,1,0,-p[0],-p[1],-p[2],1);});
 const inverse=accessor(matrices,'MAT4',16);
 const times=accessor([0,.25,.5,.75,1],'SCALAR',1);
 const wristValues=sign=>[0,.16,0,-.12,0].flatMap(a=>[0,0,Math.sin(a*sign/2),Math.cos(a*sign/2)]);
 const right=accessor(wristValues(1),'VEC4',4),left=accessor(wristValues(-1),'VEC4',4);
 const animations=[{name:'guard_wrists',samplers:[{input:times,output:right,interpolation:'LINEAR'},{input:times,output:left,interpolation:'LINEAR'}],channels:[{sampler:0,target:{node:11,path:'rotation'}},{sampler:1,target:{node:12,path:'rotation'}}]}];
 const nodes=bones.map(([name,parent,t],i)=>({name,translation:t.map(x=>x/16),...Object.fromEntries([['children',bones.flatMap((b,j)=>b[1]===name?[j]:[])]].filter(([,v])=>v.length))}));
 nodes.push({name:'puppet_mesh',mesh:0,skin:0});
 const doc={asset:{version:'2.0',generator:'SuperheroMod cinematic fixture'},extras:{puppetProfile:'model-space-v1'},scene:0,scenes:[{nodes:[2,bones.length]}],nodes,animations,
  meshes:[{primitives:[{attributes,indices:index,mode:4}]}],skins:[{joints:bones.map((_,i)=>i),inverseBindMatrices:inverse,skeleton:2}],
  accessors,bufferViews:views,buffers:[{byteLength:length,uri:'data:application/octet-stream;base64,'+Buffer.concat(chunks).toString('base64')}]};
 const output=path.resolve(__dirname,'../src/main/resources/assets/superheromod/cinematics/gltf/puppet_'+(slim?'slim':'wide')+'.gltf');
 fs.writeFileSync(output,JSON.stringify(doc,null,2)+'\n');console.log(`${output}: ${positions.length/3} vertices, ${indices.length/3} triangles`);
}
generate(false);generate(true);
