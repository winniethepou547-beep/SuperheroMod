const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {convert}=require('./cinematic-gltf-convert.cjs');
const root=path.resolve(__dirname,'../src/main/resources/assets/superheromod/cinematics/gltf');
for(const name of ['puppet_wide','puppet_slim']){
 const source=JSON.parse(fs.readFileSync(path.join(root,name+'.gltf'),'utf8'));
 const editor=convert(source,root,true),back=convert(editor,root,false);
 assert.equal(editor.extras.puppetProfile,undefined);assert.equal(back.extras.puppetProfile,'model-space-v1');
 const decode=doc=>Buffer.from(doc.buffers[0].uri.split(',')[1],'base64');
 const a=decode(source),b=decode(back);assert.equal(a.length,b.length);
 // Buffer is FLOAT attributes and unsigned-short joint/index data. Untransformed bytes stay equal;
 // transformed floats tolerate rounding in the +1.5/-1.5 coordinate shift.
 const floatRegions=[];
 source.accessors.forEach(accessor=>{if(accessor.componentType===5126){const view=source.bufferViews[accessor.bufferView];floatRegions.push([view.byteOffset??0,view.byteLength]);}});
 for(const [start,size]of floatRegions)for(let i=start;i<start+size;i+=4)assert.ok(Math.abs(a.readFloatLE(i)-b.readFloatLE(i))<1e-6,'Roundtrip float '+i);
 source.nodes.forEach((node,i)=>{
  for(const field of ['translation','rotation','scale']){
   const original=node[field]??(field==='rotation'?[0,0,0,1]:field==='scale'?[1,1,1]:[0,0,0]);
   const restored=back.nodes[i][field]??original;
   original.forEach((v,k)=>assert.ok(Math.abs(v-restored[k])<1e-6,'Node transform roundtrip'));
  }
 });
 assert.throws(()=>convert(source,root,false),/already/);
 const bad=structuredClone(editor);bad.nodes[0].children=[2];assert.throws(()=>convert(bad,root,false),/hierarchy|cycle/);
 const escape=structuredClone(editor);escape.buffers[0].uri='../outside.bin';assert.throws(()=>convert(escape,root,false));
 const malformed=structuredClone(editor);malformed.accessors[0].byteOffset=1000000;assert.throws(()=>convert(malformed,root,false),/outside/);
 const out=path.resolve(__dirname,'../build/cinematic-conversion');fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,name+'.gltf'),JSON.stringify(back));
 console.log('PASS: '+name+' mesh, inverse bind, node and animation coordinate roundtrip');
}
