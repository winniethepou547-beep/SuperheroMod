// Original deterministic mineral texture; no third-party asset extraction.
const fs=require('node:fs'),z=require('node:zlib'),path=require('node:path');
function crc(b){let c=-1;for(let n of b){c^=n;for(let k=0;k<8;k++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^-1)>>>0;}
function chunk(type,b){const t=Buffer.from(type),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(b.length);c.writeUInt32BE(crc(Buffer.concat([t,b])));return Buffer.concat([n,t,b,c]);}
const size=32,raw=Buffer.alloc(size*(1+size*4));
for(let y=0;y<size;y++)for(let x=0;x<size;x++){
 const seam=Math.abs(Math.sin(x*.27+y*.08)),grain=((x*13+y*37)%11)/11;
 const brightness=.73+.17*seam+.10*grain;
 const i=y*(1+size*4)+1+x*4;
 raw[i]=255*brightness;raw[i+1]=(160+48*seam)*brightness;raw[i+2]=(53+43*seam)*brightness;raw[i+3]=255;
}
const h=Buffer.alloc(13);h.writeUInt32BE(size,0);h.writeUInt32BE(size,4);h[8]=8;h[9]=6;
const out=path.resolve(__dirname,'../../src/main/resources/assets/superheromod/textures/entity/colossus_crystal.png');
fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',h),chunk('IDAT',z.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]));
