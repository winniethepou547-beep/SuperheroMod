package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;

public record HellfireBreathPacket(int player,boolean active,int age,float length) {
    public static void encode(HellfireBreathPacket p,FriendlyByteBuf b) {b.writeInt(p.player);b.writeBoolean(p.active);b.writeInt(p.age);b.writeFloat(p.length);}
    public static HellfireBreathPacket decode(FriendlyByteBuf b) {return new HellfireBreathPacket(b.readInt(),b.readBoolean(),b.readInt(),b.readFloat());}
    public static void handle(HellfireBreathPacket p,Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.FIRNI.superheromod.client.render.ghost.HellfireBreathRenderer.receive(p)));
        c.get().setPacketHandled(true);
    }
}
