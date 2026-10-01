package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;

/** Shared, texture-free flow/erosion material. Parameters travel with each vertex, not mutable uniforms. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class GhostFireMaterial extends RenderType {
    private static ShaderInstance shader;
    public static final RenderType TYPE=create("ghost_flow_fire",DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,65536,false,true,CompositeState.builder()
            .setShaderState(new ShaderStateShard(GhostFireMaterial::current))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
            .setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST)
            .createCompositeState(false));
    private GhostFireMaterial() {super("unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(SuperheroMod.MODID,"ghost_fire"),DefaultVertexFormat.POSITION_COLOR_TEX),s->shader=s);
    }
    public static boolean ready(){return shader!=null;}
    private static ShaderInstance current() {
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(shader!=null && mc.level!=null) {
            var time=shader.getUniform("EffectTime");
            if(time!=null)time.set(((mc.level.getGameTime()%24000)+mc.getFrameTime())/20f);
        }
        return shader;
    }
    public static void volume(VertexConsumer v,PoseStack p,net.minecraft.world.phys.Vec3 center,
                              net.minecraft.world.phys.Vec3 right,net.minecraft.world.phys.Vec3 up,
                              double width,double height,float seed,float alpha) {
        volume(v,p,center,right,up,width,height,seed,alpha,1);
    }
    /** Soft bloom halo around a flame: a camera-facing glow that reads as light in the air. */
    public static void glow(VertexConsumer v,PoseStack p,net.minecraft.world.phys.Vec3 center,
                            net.minecraft.world.phys.Vec3 right,net.minecraft.world.phys.Vec3 up,double size,float alpha) {
        for(int corner=0;corner<4;corner++) {
            double x=corner<2?-1:1,y=corner==0 || corner==3?-1:1;
            var pos=center.add(right.scale(x*size)).add(up.scale(y*size));
            vertex(v,p,pos.x,pos.y,pos.z,(float)(x*.5+.5),(float)(y*.5+.5),.65f,0,1,alpha);
        }
    }
    /** heat 1 = white-hot core, falling toward 0 cools through deep red into soot. */
    public static void volume(VertexConsumer v,PoseStack p,net.minecraft.world.phys.Vec3 center,
                              net.minecraft.world.phys.Vec3 right,net.minecraft.world.phys.Vec3 up,
                              double width,double height,float seed,float alpha,float heat) {
        for(int corner=0;corner<4;corner++) {
            double x=corner<2?-1:1,y=corner==0 || corner==3?-1:1;
            var pos=center.add(right.scale(x*width)).add(up.scale(y*height));
            vertex(v,p,pos.x,pos.y,pos.z,(float)(x*.5+.5),(float)(y*.5+.5),.12f,seed,heat,alpha);
        }
    }
    // mode: 0=flame, .35=smoke, .65=soft glow, 1=cooled ground. heat also controls cooling.
    public static void vertex(VertexConsumer v,PoseStack p,double x,double y,double z,
                              float u,float t,float mode,float seed,float heat,float opacity) {
        v.vertex(p.last().pose(),(float)x,(float)y,(float)z).color(mode,seed,heat,opacity).uv(u,t).endVertex();
    }
}
