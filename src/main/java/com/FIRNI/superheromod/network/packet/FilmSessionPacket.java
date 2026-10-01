package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Progress of a server-held film finisher (Maximum Power, Sand Army): which film, who performs
 * it on whom, the film clock and the stage (anchor and facing) every client films it around.
 */
public record FilmSessionPacket(String film, int attacker, int target, int age, boolean active, float yaw, Vec3 anchor) {
    public static void encode(FilmSessionPacket p, FriendlyByteBuf b) {
        b.writeUtf(p.film, 64); b.writeInt(p.attacker); b.writeInt(p.target); b.writeShort(p.age); b.writeBoolean(p.active);
        b.writeFloat(p.yaw); b.writeDouble(p.anchor.x); b.writeDouble(p.anchor.y); b.writeDouble(p.anchor.z);
    }
    public static FilmSessionPacket decode(FriendlyByteBuf b) {
        return new FilmSessionPacket(b.readUtf(64), b.readInt(), b.readInt(), b.readShort(), b.readBoolean(), b.readFloat(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
    }
    public static void handle(FilmSessionPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.FIRNI.superheromod.client.render.film.FilmSessionClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
