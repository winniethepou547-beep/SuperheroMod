package com.FIRNI.superheromod.client.render.cinematic;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class CinematicInputGuard {
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        CinematicClient.stop();
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        if(!CinematicClient.shouldDrive())return;
        var input=event.getInput();
        input.forwardImpulse=input.leftImpulse=0;
        input.up=input.down=input.left=input.right=input.jumping=input.shiftKeyDown=false;
    }
}
