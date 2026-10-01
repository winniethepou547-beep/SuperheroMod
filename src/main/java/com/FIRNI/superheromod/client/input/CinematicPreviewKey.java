package com.FIRNI.superheromod.client.input;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.CinematicPreviewRequestPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class CinematicPreviewKey {
    private static final KeyMapping PREVIEW=new KeyMapping("key.superheromod.cinematic_preview",GLFW.GLFW_KEY_H,"key.categories.superheromod");
    @Mod.EventBusSubscriber(modid=SuperheroMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(PREVIEW);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        boolean clicked=false;
        while(PREVIEW.consumeClick())clicked=true;
        var mc=Minecraft.getInstance();
        if(clicked&&mc.player!=null&&mc.screen==null)
            ModNetworking.CHANNEL.sendToServer(new CinematicPreviewRequestPacket());
    }
}
