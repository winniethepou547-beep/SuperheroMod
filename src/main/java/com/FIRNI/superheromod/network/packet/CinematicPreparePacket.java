package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.core.cinematic.CinematicRegistry;
import com.FIRNI.superheromod.client.render.puppet.PuppetRenderer;
import com.FIRNI.superheromod.network.ModNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record CinematicPreparePacket(UUID session, String definition) {
    public static void encode(CinematicPreparePacket p, FriendlyByteBuf b) { b.writeUUID(p.session); b.writeUtf(p.definition, 128); }
    public static CinematicPreparePacket decode(FriendlyByteBuf b) { return new CinematicPreparePacket(b.readUUID(), b.readUtf(128)); }
    public static void handle(CinematicPreparePacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            boolean success = false;
            try {
                var def = CinematicRegistry.get(p.definition);
                if (def != null) { PuppetRenderer.preload(def); success = true; }
            } catch (RuntimeException error) {
                com.mojang.logging.LogUtils.getLogger().error("Cinematic preparation failed: {}", p.definition, error);
            }
            ModNetworking.CHANNEL.sendToServer(new CinematicReadyPacket(p.session, success));
        }));
        context.setPacketHandled(true);
    }
}
