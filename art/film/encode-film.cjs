// Offline delivery tool. No Minecraft world, entity renderer or game tick involved.
// node art/film/encode-film.cjs <production-directory> [--execute]
const fs = require('fs');
const path = require('path');
const {spawnSync} = require('child_process');
const profile = require('./render-profile.json');
function build(directory) {
  const root = path.resolve(directory);
  const frames = path.join(root, 'frames');
  const names = fs.readdirSync(frames).filter(n => /^\d{6}\.png$/.test(n)).sort();
  if (names.length < 2) throw Error('At least two rendered PNG frames are required.');
  const start = Number(names[0].slice(0,6));
  names.forEach((name, i) => {
    if (Number(name.slice(0,6)) !== start+i) throw Error('Missing frame before '+name);
    const fd=fs.openSync(path.join(frames,name),'r');
    const header=Buffer.alloc(24);
    try { if(fs.readSync(fd,header,0,24,0)!==24)throw Error('Truncated PNG: '+name); }
    finally { fs.closeSync(fd); }
    if(header.subarray(0,8).toString('hex')!=='89504e470d0a1a0a'
      ||header.readUInt32BE(16)!==profile.width||header.readUInt32BE(20)!==profile.height)
      throw Error('Unexpected PNG format or dimensions: '+name);
  });
  const audio=path.join(root,profile.audioMaster);
  if(!fs.existsSync(audio))throw Error('Missing mixed audio master: '+audio);
  const output=path.join(root,profile.delivery);
  if(fs.existsSync(output))throw Error('Delivery already exists; choose a new production directory.');
  const args=['-n','-framerate',String(profile.fps),'-start_number',String(start),
    '-i',path.join(root,profile.imageSequence),'-i',audio,
    '-map','0:v:0','-map','1:a:0','-c:v',profile.codec,'-preset','slow',
    '-crf',String(profile.crf),'-pix_fmt',profile.pixelFormat,
    '-c:a',profile.audioCodec,'-b:a',profile.audioBitrate,
    '-af','apad','-t',String(names.length/profile.fps),'-movflags','+faststart',output];
  return {args, frames:names.length, seconds:names.length/profile.fps, output};
}
if(require.main===module) {
  try {
    if(!process.argv[2])throw Error('Usage: encode-film.cjs <production-directory> [--execute]');
    const job=build(process.argv[2]);
    if(process.argv[3]==='--execute') {
      const result=spawnSync('ffmpeg',job.args,{stdio:'inherit',shell:false,windowsHide:true});
      if(result.error)throw result.error;
      if(result.status!==0)throw Error('FFmpeg encoding failed.');
    } else console.log(JSON.stringify(job,null,2));
  } catch(error) {console.error(error.message);process.exitCode=1;}
}
module.exports={build};
