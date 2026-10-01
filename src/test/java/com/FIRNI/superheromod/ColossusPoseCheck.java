package com.FIRNI.superheromod;
import com.FIRNI.superheromod.heroes.sandman.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
public final class ColossusPoseCheck {
 public static void main(String[] args) {
  for(byte type=0;type<4;type++) {
   int duration=type==0?32:type==1?12:type==2?121:44;
   for(int frame=0;frame<=duration*3;frame++) {
    float t=frame/3f;int tick=(int)t;
    var pose=ColossusPose.evaluate(100+t,40,40,1,new ColossusPose.Action(type,tick,duration),t-tick,true);
    for(var crystal:ColossusCrystal.values()) {
     Vector3f p=crystal.socket(Vec3.ZERO,40,1,pose).transformPosition(new Vector3f());
     if(!Float.isFinite(p.x+p.y+p.z))throw new AssertionError("Nonfinite socket");
    }
   }
  }
  var idle=ColossusPose.evaluate(100,0,0,1,null,0,true);
  double firstX=0,lastX=0;
  for(int tick=18;tick<=30;tick++) {
   var pose=ColossusPose.evaluate(100,0,0,1,new ColossusPose.Action((byte)3,tick,44),0,true);
   Vec3 tip=ColossusCrystal.swordTip(Vec3.ZERO,0,pose);
   if(tick==18)firstX=tip.x;
   if(tick==30)lastX=tip.x;
   if(Math.abs(tip.y)>.3 || tip.z<4)throw new AssertionError("Blade misses ground ahead: "+tip);
  }
  if(lastX-firstX<7)throw new AssertionError("Blade must sweep left to right: "+firstX+" -> "+lastX);
  var braced=ColossusPose.evaluate(100,0,0,1,null,0,true);
  ColossusPose.form(braced,.60f);
  for(boolean right:new boolean[]{true,false}) {
   Vec3 palm=ColossusCrystal.handPosition(Vec3.ZERO,0,braced,right);
   System.out.println("Formation palm: "+palm);
   if(palm.y<-.8||palm.y>.8||palm.z<3)throw new AssertionError("Formation must brace both hands ahead at ground level");
  }
  var slam=ColossusPose.evaluate(100,0,0,1,new ColossusPose.Action((byte)0,21,32),0,true);
  Vec3 contact=ColossusCrystal.handPosition(Vec3.ZERO,0,slam,true);
  System.out.println("Slam contact: "+contact);
  if(contact.z<4)throw new AssertionError("Slam still under caster");
  var swing=ColossusPose.evaluate(100,0,0,1,new ColossusPose.Action((byte)0,10,32),0,true);
  var a=ColossusCrystal.RIGHT_SHOULDER.socket(Vec3.ZERO,0,1,idle).transformPosition(new Vector3f());
  var b=ColossusCrystal.RIGHT_SHOULDER.socket(Vec3.ZERO,0,1,swing).transformPosition(new Vector3f());
  if(a.distance(b)<.3f)throw new AssertionError("Crystal did not follow arm");
  double y=0,v=1.45;while(v>0){y+=v;v=(v-.08)*.98;}
  if(y<9||y>12)throw new AssertionError("Launch height: "+y);
  System.out.println("PASS: all attack sockets finite, shoulder follows arm; nominal launch apex="+y);
 }
}
