package com.FIRNI.superheromod.client.render;
import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.sandman.SandTravelController;
import com.FIRNI.superheromod.network.packet.SandTravelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Frame-time deformation of the actual player model, anchored at its feet. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class SandTravelAnimation {
    private static final Map<UUID,Long> starts=new HashMap<>();
    static Long started(UUID player) { return starts.get(player); }
    static float height(float age) {
        float fraction=age<SandTravelController.TRANSFER_TICK
                ?1-age/SandTravelController.TRANSFER_TICK
                :(age-SandTravelController.TRANSFER_TICK)/(SandTravelController.END_TICK-SandTravelController.TRANSFER_TICK);
        fraction=Mth.clamp(fraction,0,1);
        return fraction*fraction*(3-2*fraction);
    }
    public static void update(SandTravelPacket p) { if(p.active())starts.put(p.player(),p.start());else starts.remove(p.player()); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void render(RenderPlayerEvent.Pre event) {
        Long start=starts.get(event.getEntity().getUUID()); if(start==null)return;
        float age=event.getEntity().level().getGameTime()-start+event.getPartialTick();
        if(age<0 || age>=SandTravelController.END_TICK)return;
        float height=height(age);
        if(height<.025f) { event.setCanceled(true); return; }
        // EntityRenderDispatcher owns the enclosing pose push/pop, including cancellation.
        event.getPoseStack().scale(1+(1-height)*.3f,Math.max(.015f,height),1+(1-height)*.3f);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var level=Minecraft.getInstance().level;
        if(level==null) { starts.clear(); return; }
        starts.values().removeIf(t->level.getGameTime()-t>SandTravelController.END_TICK+20 || t>level.getGameTime()+20);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { starts.clear(); }
}
