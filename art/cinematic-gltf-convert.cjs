// Coordinate/profile bridge, not an arbitrary rig retargeter.
// Usage: node art/cinematic-gltf-convert.cjs input.gltf output.gltf [--to-editor]
const fs=require('fs'),path=require('path');
function requireThat(ok,message){if(!ok)throw new Error(message);}
const I=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];
const C=[-1,0,0,0,0,-1,0,0,0,0,1,0,0,0,0,1];
const A=[-1,0,0,0,0,-1,0,0,0,0,1,0,0,1.5,0,1];
function multiply(a,b){const r=Array(16).fill(0);for(let col=0;col<4;col++)for(let row=0;row<4;row++)for(let k=0;k<4;k++)r[col*4+row]+=a[k*4+row]*b[col*4+k];return r;}
function close(a,b){return a.length===b.length&&a.every((v,i)=>Math.abs(v-b[i])<1e-6);}

function convert(input,sourceDir,toEditor=false){
 const doc=structuredClone(input);
 requireThat(doc.asset?.version==='2.0','glTF 2.0 JSON required (GLB is not supported).');
 requireThat(!doc.extensionsRequired?.length,'Required glTF extensions unsupported.');
 requireThat(doc.meshes?.length===1&&doc.skins?.length===1,'Export one skinned mesh and one armature.');
 requireThat(doc.nodes?.length>0&&doc.nodes.length<=128,'Node budget exceeded.');
 requireThat(doc.accessors?.length<=2048&&doc.bufferViews?.length<=2048,'Accessor budget exceeded.');
 if(toEditor)requireThat(doc.extras?.puppetProfile==='model-space-v1','Editor export needs a model-space-v1 source.');
 else requireThat(doc.extras?.puppetProfile!=='model-space-v1','Input is already model-space-v1; refusing double conversion.');
 const buffers=[];let total=0;
 for(const item of doc.buffers??[]){
  requireThat(Number.isInteger(item.byteLength)&&item.byteLength>=0&&item.byteLength<=12_000_000,'Invalid buffer length.');
  let bytes;
  if(item.uri?.startsWith('data:application/octet-stream;base64,'))bytes=Buffer.from(item.uri.split(',')[1],'base64');
  else {
   const uri=decodeURIComponent(item.uri??'');
   requireThat(uri&&!path.isAbsolute(uri)&&!uri.includes(':')&&!uri.includes('\\'),'Only local buffer files within the source folder are supported.');
   const root=fs.realpathSync(sourceDir),file=fs.realpathSync(path.resolve(root,uri));
   const relative=path.relative(root,file);
   requireThat(relative&&!relative.startsWith('..')&&!path.isAbsolute(relative),'Buffer path escapes source folder.');
   requireThat(fs.statSync(file).size<=12_000_000,'Buffer file exceeds budget.');bytes=fs.readFileSync(file);
  }
  requireThat(bytes.length===item.byteLength,'Buffer byteLength mismatch.');total+=bytes.length;
  requireThat(total<=12_000_000,'Combined buffer budget exceeded.');buffers.push(bytes);
 }
 const chunks=[],offsets=[];let size=0;
 for(const b of buffers){const pad=(4-size%4)%4;chunks.push(Buffer.alloc(pad));size+=pad;offsets.push(size);chunks.push(b);size+=b.length;}
 const data=Buffer.concat(chunks);
 for(const view of doc.bufferViews){
  const start=view.byteOffset??0;
  requireThat(buffers[view.buffer]&&Number.isInteger(start)&&start>=0&&Number.isInteger(view.byteLength)&&view.byteLength>=0&&start+view.byteLength<=buffers[view.buffer].length,'Buffer view outside source.');
  view.byteOffset=start+offsets[view.buffer];view.buffer=0;
 }
 function access(index,width,type=5126){
  const a=doc.accessors[index],v=a&&doc.bufferViews[a.bufferView];
  const types={1:'SCALAR',2:'VEC2',3:'VEC3',4:'VEC4',16:'MAT4'};
  requireThat(a&&v&&!a.sparse&&!a.normalized&&a.componentType===type&&a.type===types[width],'Unsupported accessor '+index);
  const bytes=type===5126||type===5125?4:type===5123?2:1;
  const stride=v.byteStride??width*bytes,offset=a.byteOffset??0,count=a.count;
  requireThat(Number.isInteger(count)&&count>0&&count<=98304&&Number.isInteger(offset)&&offset>=0&&stride>=width*bytes&&stride%bytes===0&&offset%bytes===0&&(v.byteOffset+offset)%bytes===0,'Invalid accessor layout.');
  requireThat(offset+(count-1)*stride+width*bytes<=v.byteLength,'Accessor outside buffer view.');
  return {a,count,at:i=>v.byteOffset+offset+i*stride};
 }
 const touched=new Set();
 function floats(index,width,operation,key){
  if(touched.has(index)){requireThat(touched.has(index+':'+key),'Accessor reused with incompatible transforms.');return;}
  touched.add(index);touched.add(index+':'+key);const a=access(index,width);let min=Array(width).fill(Infinity),max=Array(width).fill(-Infinity);
  for(let i=0;i<a.count;i++){
   const at=a.at(i),old=Array.from({length:width},(_,k)=>data.readFloatLE(at+k*4));
   requireThat(old.every(Number.isFinite),'Nonfinite accessor.');const next=operation(old);
   requireThat(next.every(Number.isFinite),'Nonfinite converted accessor.');next.forEach((v,k)=>{data.writeFloatLE(v,at+k*4);min[k]=Math.min(min[k],v);max[k]=Math.max(max[k],v);});
  }
  if(a.a.min)a.a.min=min;if(a.a.max)a.a.max=max;
 }
 const parents=Array(doc.nodes.length).fill(-1);
 doc.nodes.forEach((n,i)=>(n.children??[]).forEach(c=>{requireThat(Number.isInteger(c)&&c>=0&&c<parents.length&&parents[c]===-1&&c!==i,'Invalid skeleton hierarchy.');parents[c]=i;}));
 for(let i=0;i<parents.length;i++){let p=i,steps=0;while(p>=0){requireThat(++steps<=parents.length,'Skeleton cycle.');p=parents[p];}}
 // Blender groups the mesh under an identity Armature object. Detach only when
 // the entire ancestor chain is static identity; never discard real transforms.
 doc.nodes.forEach((node,i)=>{
  if(node.mesh===undefined||parents[i]===-1)return;
  for(let p=parents[i];p>=0;p=parents[p]){
   const n=doc.nodes[p];
   requireThat(close(n.matrix??I,I)&&close(n.translation??[0,0,0],[0,0,0])&&close(n.rotation??[0,0,0,1],[0,0,0,1])&&close(n.scale??[1,1,1],[1,1,1]),'Apply nonidentity mesh-parent transforms before export.');
   requireThat(!(doc.animations??[]).some(a=>a.channels.some(c=>c.target.node===p)),'Animated mesh parent cannot be flattened.');
  }
  const parent=doc.nodes[parents[i]];parent.children=parent.children.filter(c=>c!==i);parents[i]=-1;
  const scene=doc.scenes[doc.scene??0];if(!scene.nodes.includes(i))scene.nodes.push(i);
 });
 let meshCount=0;
 doc.nodes.forEach((n,i)=>{
  if(n.matrix){requireThat(close(n.matrix,I),'Apply object transforms before export; nonidentity node matrices unsupported.');delete n.matrix;}
  if(n.mesh!==undefined){
   meshCount++;requireThat(n.mesh===0&&n.skin===0&&parents[i]===-1&&close(n.translation??[0,0,0],[0,0,0])&&close(n.rotation??[0,0,0,1],[0,0,0,1])&&close(n.scale??[1,1,1],[1,1,1]),'Mesh must be an unparented identity-transform skinned node.');
   delete n.translation;delete n.rotation;delete n.scale;return;
  }
  const t=n.translation??[0,0,0],q=n.rotation??[0,0,0,1];
  requireThat(t.length===3&&q.length===4&&[...t,...q].every(Number.isFinite),'Invalid node transform.');
  n.translation=[-t[0],(parents[i]===-1?1.5:0)-t[1],t[2]];n.rotation=[-q[0],-q[1],q[2],q[3]];
 });
 requireThat(meshCount===1,'One mesh instance required.');
 const primitives=doc.meshes[0].primitives;requireThat(primitives.length===1,'Join mesh objects before export; one primitive required.');
 const primitive=primitives[0],attrs=primitive.attributes;
 requireThat((primitive.mode??4)===4&&!primitive.targets&&!attrs.JOINTS_1&&!attrs.WEIGHTS_1,'Only triangles with up to four bone weights are supported.');
 requireThat(attrs.POSITION!==undefined&&attrs.TEXCOORD_0!==undefined&&attrs.JOINTS_0!==undefined&&attrs.WEIGHTS_0!==undefined,'Position, UV and skin weights required.');
 floats(attrs.POSITION,3,p=>[-p[0],1.5-p[1],p[2]],'position');
 if(attrs.NORMAL!==undefined)floats(attrs.NORMAL,3,p=>[-p[0],-p[1],p[2]],'normal');
 if(attrs.TANGENT!==undefined)floats(attrs.TANGENT,4,p=>[-p[0],-p[1],p[2],p[3]],'tangent');
 floats(doc.skins[0].inverseBindMatrices,16,m=>multiply(multiply(C,m),A),'bind');
 requireThat(doc.animations===undefined||doc.animations.length<=32,'Clip budget exceeded.');
 for(const animation of doc.animations??[])for(const channel of animation.channels){
  const s=animation.samplers[channel.sampler],node=channel.target.node;
  requireThat(doc.nodes[node]&&doc.nodes[node].mesh===undefined,'Invalid animation target.');
  requireThat(['LINEAR','STEP'].includes(s.interpolation??'LINEAR'),'Bake curves to LINEAR before export; CUBICSPLINE unsupported.');
  switch(channel.target.path){
   case 'rotation':floats(s.output,4,q=>[-q[0],-q[1],q[2],q[3]],'rotation');break;
   case 'translation':floats(s.output,3,p=>[-p[0],(parents[node]===-1?1.5:0)-p[1],p[2]],parents[node]===-1?'root-translation':'translation');break;
   case 'scale':access(s.output,3);break;
   default:throw new Error('Unsupported animation channel: '+channel.target.path);
  }
 }
 // Material appearance is supplied by the game role, not imported shader graphs.
 delete primitive.material;delete doc.materials;delete doc.textures;delete doc.images;delete doc.samplers;
 doc.extras={...doc.extras};if(toEditor)delete doc.extras.puppetProfile;else doc.extras.puppetProfile='model-space-v1';
 doc.buffers=[{byteLength:data.length,uri:'data:application/octet-stream;base64,'+data.toString('base64')}];
 return doc;
}
if(require.main===module){
 try{
  const [input,output,flag]=process.argv.slice(2);requireThat(input&&output&&(!flag||flag==='--to-editor'),'Usage: input.gltf output.gltf [--to-editor]');
  requireThat(path.resolve(input)!==path.resolve(output),'Use a separate output file.');
  requireThat(fs.statSync(input).size<20_000_000,'JSON file exceeds budget.');
  const doc=convert(JSON.parse(fs.readFileSync(input,'utf8').replace(/^\uFEFF/,'')),path.dirname(path.resolve(input)),flag==='--to-editor');
  fs.mkdirSync(path.dirname(path.resolve(output)),{recursive:true});fs.writeFileSync(output,JSON.stringify(doc,null,2)+'\n');console.log('Written '+path.resolve(output));
 }catch(e){console.error(e.message);process.exitCode=1;}
}
module.exports={convert};
