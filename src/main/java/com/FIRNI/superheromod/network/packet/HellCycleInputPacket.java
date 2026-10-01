package com.FIRNI.superheromod.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record HellCycleInputPacket(float forward, float turn, boolean wheelie) {
    public static void encode(HellCycleInputPacket p, FriendlyByteBuf b) { b.writeFloat(p.forward); b.writeFloat(p.turn); b.writeBoolean(p.wheelie); }
    public static HellCycleInputPacket decode(FriendlyByteBuf b) { return new HellCycleInputPacket(b.readFloat(),b.readFloat(),b.readBoolean()); }
    public static void handle(HellCycleInputPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> {
            var player=c.get().getSender();
            if(player!=null && Float.isFinite(p.forward) && Float.isFinite(p.turn) && player.getVehicle() instanceof com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity bike) bike.control(player,p.forward,p.turn,p.wheelie);
        }); c.get().setPacketHandled(true);
    }
}
