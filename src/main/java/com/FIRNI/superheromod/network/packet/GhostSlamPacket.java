package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;

public record GhostSlamPacket(Vec3 position) {
    public static void encode(GhostSlamPacket p,FriendlyByteBuf b) {b.writeDouble(p.position.x);b.writeDouble(p.position.y);b.writeDouble(p.position.z);}
    public static GhostSlamPacket decode(FriendlyByteBuf b) {return new GhostSlamPacket(new Vec3(b.readDouble(),b.readDouble(),b.readDouble()));}
    public static void handle(GhostSlamPacket p,Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->
            com.FIRNI.superheromod.client.render.ghost.GhostSlamEffects.add(p.position)));
        c.get().setPacketHandled(true);
    }
}
