package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Riding readout and the sense of speed: a quiet speed/fuel corner, a smoothed field-of-view
 * push and film-style speed lines that streak in from the screen edges above cruising speed.
 */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class HellCycleSpeedHud {
    private static final int LINES=56;
    /** FOV and line strength follow the speed per frame, so acceleration never steps. */
    private static float speedSmooth;
    private static long lastNanos;
    private static float smoothed(HellCycleEntity bike) {
        long now=System.nanoTime();
        float dt=lastNanos==0?0:Math.min(.1f,(now-lastNanos)/1e9f);lastNanos=now;
        float target=(float)Math.min(1,bike.speed()/HellCycleEntity.MAX_SPEED);
        speedSmooth+=(target-speedSmooth)*(1-(float)Math.exp(-dt*5));
        return speedSmooth;
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e) {
        var mc=Minecraft.getInstance();
        if(mc.player!=null && mc.player.getVehicle() instanceof HellCycleEntity b) {
            float s=smoothed(b);
            e.setFOV(e.getFOV()+s*s*14);
        } else { speedSmooth=0; lastNanos=0; }
    }
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post e) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || !(mc.player.getVehicle() instanceof HellCycleEntity b) || !e.getOverlay().id().getPath().equals("hotbar"))return;
        var g=e.getGuiGraphics();int w=e.getWindow().getGuiScaledWidth(),h=e.getWindow().getGuiScaledHeight();
        float speed=speedSmooth;
        float time=mc.level.getGameTime()+mc.getFrameTime();
        speedLines(g,w,h,speed,time);
        if(mc.options.hideGui)return;
        int right=w-16,base=h-30;
        HudStyle.shade(g,right-86,base-26,92,44,.9f);
        HudStyle.value(g,mc.font,Integer.toString(Math.round(b.speed()*72)),right-26,base-22,2.2f,speed>.85f?HudStyle.ACCENT_HOT:HudStyle.TEXT,1);
        HudStyle.caption(g,mc.font,"km/h",right,base-12,HudStyle.MUTED,1);
        HudStyle.bar(g,right-70,base+2,70,speed,HudStyle.ACCENT);
        float fuel=b.fuel()/600f;
        HudStyle.caption(g,mc.font,"Fuel",right-70,base+8,HudStyle.MUTED,-1);
        HudStyle.bar(g,right-40,base+11,40,fuel,fuel<.2f?HudStyle.DANGER:0xFFD8CFC4);
    }
    /**
     * Anime/film speed lines: thin tapered wedges aimed at the screen centre, living a few
     * frames each, denser and longer the faster the bike goes. Nothing above cruising speed.
     */
    private static void speedLines(net.minecraft.client.gui.GuiGraphics g,int w,int h,float speed,float time) {
        float strength=Math.max(0,(speed-.5f)/.5f);
        if(strength<=0)return;
        float cx=w/2f,cy=h/2f,reach=(float)Math.hypot(cx,cy);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer=Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
        var m=g.pose().last().pose();
        int count=(int)(LINES*strength);
        for(int i=0;i<count;i++) {
            // Each line re-rolls its angle every short cycle so the field flickers like film grain.
            float cycle=(float)Math.floor(time*.9f+i*.37f);
            float seed=hash(i*31+cycle*17);
            double angle=seed*Math.PI*2;
            float life=(time*.9f+i*.37f)-cycle;
            float inner=reach*(.55f+.25f*hash(i*7+cycle))-life*reach*.15f, outer=reach*1.05f;
            float alpha=strength*(1-life)*(.35f+.45f*hash(i*13+cycle));
            float half=(1.2f+2.2f*hash(i*5+cycle))*(.6f+strength*.6f);
            float cos=(float)Math.cos(angle),sin=(float)Math.sin(angle);
            // Keep the centre of view clear: lines live near the edges.
            float tx=cx+cos*inner,ty=cy+sin*inner*.75f;
            float ox=cx+cos*outer,oy=cy+sin*outer*.75f;
            float px=-sin*half,py=cos*half;
            buffer.vertex(m,tx,ty,0).color(1f,.96f,.9f,0f).endVertex();
            buffer.vertex(m,ox+px,oy+py,0).color(1f,.93f,.85f,alpha).endVertex();
            buffer.vertex(m,ox-px,oy-py,0).color(1f,.93f,.85f,alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
    private static float hash(float n) {
        double x=Math.sin(n*12.9898)*43758.5453;
        return (float)(x-Math.floor(x));
    }
}
