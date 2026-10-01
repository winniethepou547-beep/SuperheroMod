const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../..');
const renderer=path.join(root,'src/main/java/com/FIRNI/superheromod/client/render/colossus/ColossusRenderer.java');
let source=fs.readFileSync(renderer,'utf8');
const begin=source.indexOf('    private static void animate(');
const end=source.indexOf('    /**\n     * Kristaller.',begin)>=0?source.indexOf('    /**\n     * Kristaller.',begin):source.indexOf('    /**\r\n     * Kristaller.',begin);
if(begin<0||end<0)throw Error('Pose methods already extracted or missing');
let methods=source.slice(begin,end).replaceAll('ColossusModel','ColossusPose').replaceAll('ModelPart','Part').replaceAll('ClientColossusActions.Action','Action').replaceAll('ClientColossusActions.isMaceSwing(action)','action.type == 0').replaceAll('ClientColossusActions.isRockThrow(action)','action.type == 1');
const fields=['lowerMass','torso','head','rightArm','leftArm','rightForearm','leftForearm','mace','sword'];
const common=`package com.FIRNI.superheromod.heroes.sandman;
import net.minecraft.util.Mth;
/** Shared skeletal pose: rendering and crystal collision evaluate the same attack curves. */
public final class ColossusPose {
 public static final class Part {
  public float x,y,z,xRot,yRot,zRot,xScale=1,yScale=1,zScale=1;
  public boolean visible=true;
 }
 public record Action(byte type,int ticks,int duration) {
  public float progress(float partial){return Math.min(1f,(ticks+partial)/Math.max(1,duration));}
 }
 ${fields.map(n=>'public final Part '+n+'=new Part();').join('\n ')}
 public static ColossusPose evaluate(float age,float yaw,float massYaw,float growth,Action action,float partial,boolean right){
  ColossusPose p=new ColossusPose();p.sword.visible=false;
  animate(p,age,yaw,massYaw,growth);if(action!=null)applyAction(p,action,partial,right);return p;
 }
${methods}
}
`;
fs.writeFileSync(path.join(root,'src/main/java/com/FIRNI/superheromod/heroes/sandman/ColossusPose.java'),common);
source=source.slice(0,begin)+source.slice(end);
const a=source.indexOf('        animate(colossus, age, yaw, smoothed, growth);');
const b=source.indexOf('        pose.pushPose();',a);
if(a<0||b<0)throw Error('Evaluation site missing');
source=source.slice(0,a)+`        var action=ClientColossusActions.of(player.getUUID());
        boolean right=ClientColossusData.crystalState(player,ColossusCrystal.RIGHT_SHOULDER.ordinal())<3;
        var shared=com.FIRNI.superheromod.heroes.sandman.ColossusPose.evaluate(age,yaw,smoothed,growth,
                action==null?null:new com.FIRNI.superheromod.heroes.sandman.ColossusPose.Action(action.type,action.ticks,action.duration),partial,right);
${fields.map(n=>'        applyPose(colossus.'+n+',shared.'+n+');').join('\n')}

`+source.slice(b);
source=source.replace('    private static void drawCrystals(',`    private static void applyPose(ModelPart m,com.FIRNI.superheromod.heroes.sandman.ColossusPose.Part p) {
        m.x+=p.x;m.y+=p.y;m.z+=p.z;m.xRot=p.xRot;m.yRot=p.yRot;m.zRot=p.zRot;
        m.xScale=p.xScale;m.yScale=p.yScale;m.zScale=p.zScale;m.visible=p.visible;
    }

    private static void drawCrystals(`);
fs.writeFileSync(renderer,source);
