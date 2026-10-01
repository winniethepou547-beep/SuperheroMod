// Original deterministic mineral texture; no third-party asset extraction.
const fs=require('node:fs'),z=require('node:zlib'),path=require('node:path');
function crc(b){let c=-1;for(let n of b){c^=n;for(let k=0;k<8;k++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^-1)>>>0;}
function chunk(type,b){const t=Buffer.from(type),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(b.length);c.writeUInt32BE(crc(Buffer.concat([t,b])));return Buffer.concat([n,t,b,c]);}
const size=128,raw=Buffer.alloc(size*(1+size*4));
for(let y=0;y<size;y++)for(let x=0;x<size;x++){
 const nx=(x-63.5)/63.5,ny=(y-63.5)/63.5; const seam=Math.max(0,1-nx*nx-ny*ny),grain=.5+.5*Math.sin(x*.13+Math.sin(y*.09)*3)*Math.cos(y*.12);
 const brightness=.73+.17*seam+.10*grain;
 const i=y*(1+size*4)+1+x*4;
 raw[i]=205;raw[i+1]=177;raw[i+2]=126;raw[i+3]=Math.round(170*Math.pow(seam,2.2)*(.65+.35*grain));
}
const h=Buffer.alloc(13);h.writeUInt32BE(size,0);h.writeUInt32BE(size,4);h[8]=8;h[9]=6;
const out=path.resolve(__dirname,'../../src/main/resources/assets/superheromod/textures/entity/sand_mist.png');
fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',h),chunk('IDAT',z.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]));

