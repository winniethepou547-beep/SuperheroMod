package com.FIRNI.superheromod.client.render.cinematic;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Short, peripheral motion streaks; fixed geometry budget and no particle simulation. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class CinematicForegroundRenderer {
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var frame=CinematicClient.visualFrame();
        if(frame==null)return;
        var gui=event.getGuiGraphics();var window=Minecraft.getInstance().getWindow();
        int w=window.getGuiScaledWidth(),h=window.getGuiScaledHeight();
        float flash=0;
        for(var cue:frame.definition().impacts) {
            float weight=cue.envelope(frame.tick());flash=Math.max(flash,cue.flash(frame.tick()));
            if(weight<.01)continue;
            float age=frame.tick()-cue.tick();
            for(int i=0;i<12;i++) {
                float cycle=(age/(4+i%3)+i*.173f)%1;
                boolean left=i%2==0;
                float x=left?w*(.03f+cycle*.22f):w*(.97f-cycle*.22f);
                float y=h*(.09f+(i*.137f)% .82f);
                int length=(int)(w*(.035f+.025f*(i%3)));
                int alpha=(int)(Math.min(.65f,weight*cue.strength()*.22f)*255*(1-cycle));
                gui.pose().pushPose();
                try {
                    gui.pose().translate(x,y,0);
                    gui.pose().mulPose(Axis.ZP.rotationDegrees((left?-12:12)*cue.direction()));
                    gui.fill(-length,-2,length,2,((alpha/4)<<24)|0xD6B575);
                    gui.fill(-length,-1,length,1,(alpha<<24)|0xFFF2D5);
                } finally {gui.pose().popPose();}
            }
        }
        if(flash>.001)gui.fill(0,0,w,h,((int)(flash*255)<<24)|0xFFF0D0);
    }
}
