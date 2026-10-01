package com.FIRNI.superheromod.client.render.ghost;
import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.HellCycleInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class GhostClientEvents {
    private static boolean chainHeld;
    @SubscribeEvent public static void chainHold(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null){chainHeld=false;return;}
        boolean down=mc.screen==null && mc.isWindowActive() && mc.options.keyAttack.isDown()
                && "ghost_rider".equals(com.FIRNI.superheromod.client.ClientHeroRegistry.get(mc.player.getUUID()));
        if((down && mc.player.tickCount%8==0) || (!down && chainHeld))
            ModNetworking.CHANNEL.sendToServer(new com.FIRNI.superheromod.network.packet.AbilityInputPacket(
                    com.FIRNI.superheromod.core.ability.AbilitySlot.LMB,down));
        chainHeld=down;
    }
    private static double bikeFall;
    private static boolean wasOnBike;
    private static int bikeAir;
    /** Rider's camera takes a light hit when the bike touches down, scaled by the drop speed. */
    @SubscribeEvent public static void landingShake(TickEvent.ClientTickEvent e) {
        var mc=Minecraft.getInstance();
        if(e.phase!=TickEvent.Phase.END)return;
        boolean onBike=mc.player!=null && mc.player.getVehicle() instanceof HellCycleEntity;
        // Dropping onto the seat after the summon leap.
        if(onBike && !wasOnBike)com.FIRNI.superheromod.client.render.ClientScreenShake.add(.3f);
        wasOnBike=onBike;
        if(mc.player==null || !(mc.player.getVehicle() instanceof HellCycleEntity bike)){bikeFall=0;bikeAir=0;return;}
        double dy=bike.getY()-bike.yo;
        if(dy<-.05){bikeAir++;bikeFall=Math.max(bikeFall,-dy);return;}
        if(bikeAir>3 && bikeFall>.35)com.FIRNI.superheromod.client.render.ClientScreenShake.add((float)Math.min(.5,(bikeFall-.25)*.3));
        bikeAir=0;bikeFall=0;
    }
    @SubscribeEvent public static void input(TickEvent.ClientTickEvent e) {
        var mc=Minecraft.getInstance();
        if(e.phase!=TickEvent.Phase.END || mc.player==null || !(mc.player.getVehicle() instanceof HellCycleEntity) || mc.player.tickCount%2!=0)return;
        float forward=mc.screen==null? (mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0):0;
        float turn=mc.screen==null? (mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0):0;
        ModNetworking.CHANNEL.sendToServer(new HellCycleInputPacket(forward,turn,mc.screen==null && mc.isWindowActive() && mc.options.keyJump.isDown()));
    }
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post e) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || !(mc.player.getVehicle() instanceof HellCycleEntity bike) || !e.getOverlay().id().getPath().equals("hotbar"))return;
        // Fuel lives in the speed readout; only the wheelie angle appears here, while it is held.
        if(mc.options.hideGui || !mc.options.keyJump.isDown())return;
        var g=e.getGuiGraphics();int center=e.getWindow().getGuiScaledWidth()/2,base=e.getWindow().getGuiScaledHeight()/2+26;
        int angle=Math.min(45,Math.max(0,Math.round(-bike.getXRot())));
        com.FIRNI.superheromod.client.hud.HudStyle.caption(g,mc.font,"Wheelie",center-40,base,com.FIRNI.superheromod.client.hud.HudStyle.MUTED,-1);
        com.FIRNI.superheromod.client.hud.HudStyle.caption(g,mc.font,angle+"°",center+40,base,
                angle>=45?com.FIRNI.superheromod.client.hud.HudStyle.ACCENT_HOT:com.FIRNI.superheromod.client.hud.HudStyle.TEXT,1);
        com.FIRNI.superheromod.client.hud.HudStyle.bar(g,center-40,base+11,80,angle/45f,com.FIRNI.superheromod.client.hud.HudStyle.ACCENT);
    }
    @Mod.EventBusSubscriber(modid=SuperheroMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(com.FIRNI.superheromod.core.entity.ModEntities.HELL_CYCLE.get(),HellCycleRenderer::new);
        }
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
            for(String skin:e.getSkins()) {
                var renderer=e.getSkin(skin);
                if(renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer p)p.addLayer(new GhostRiderLayer(p));
            }
        }
    }
}
