package com.FIRNI.superheromod.network.packet;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import com.FIRNI.superheromod.client.render.SandTravelAnimation;
public record SandTravelPacket(UUID player,long start,boolean active) {
    public static void encode(SandTravelPacket p,FriendlyByteBuf b) { b.writeUUID(p.player); b.writeLong(p.start); b.writeBoolean(p.active); }
    public static SandTravelPacket decode(FriendlyByteBuf b) { return new SandTravelPacket(b.readUUID(),b.readLong(),b.readBoolean()); }
    public static void handle(SandTravelPacket p,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();
        if(context.getDirection().getReceptionSide().isClient()) context.enqueueWork(()->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SandTravelAnimation.update(p)));
        context.setPacketHandled(true);
    }
}
