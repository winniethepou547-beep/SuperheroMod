const fs=require('node:fs'),path=require('node:path');
const f=path.resolve(__dirname,'../../src/main/java/com/FIRNI/superheromod/client/render/SandShapeRenderer.java');
let s=fs.readFileSync(f,'utf8');const a=s.indexOf('        // Yuvarlanma:',s.indexOf('private static void drawRock'));
const b=s.indexOf('    // ------------------------------------------------------------------',a);
if(a<0||b<0)throw Error('Already migrated');
s=s.slice(0,a)+`        com.FIRNI.superheromod.client.render.colossus.RockFacets.render(buf,m,sprite,light,
                s.x(),s.y(),s.z(),radius,(float)Math.toRadians(s.yaw()));
    }

`+s.slice(b);fs.writeFileSync(f,s);
