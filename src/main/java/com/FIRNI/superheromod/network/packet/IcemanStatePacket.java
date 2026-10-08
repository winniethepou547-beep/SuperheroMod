package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.COOLDOWNS;

/**
 * One Iceman's whole state, every tick: what he is doing and since when, his flags (wheel open, weapon in hand, shell up,
 * sculpting, sliding), the weapon picked and the combo's swing, the hold's charge (0..1), the body the brush is on (-1:
 * sculpting into the air, or none), the shell's health (0..1), the sculpture being made, the slide's time left (0..1),
 * his cooldowns.
 */
public record IcemanStatePacket(int entity, int action, int age, int flags, int weapon, int combo, float charge, int brushTarget,
                                float shell, int sculpture, float slide, int[] cooldowns) {
    public static void encode(IcemanStatePacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entity); b.writeByte(p.action); b.writeVarInt(p.age); b.writeByte(p.flags);
        b.writeByte(p.weapon); b.writeByte(p.combo); b.writeFloat(p.charge); b.writeVarInt(p.brushTarget + 1);
        b.writeFloat(p.shell); b.writeInt(p.sculpture); b.writeFloat(p.slide);
        for (int i = 0; i < COOLDOWNS; i++) b.writeVarInt(p.cooldowns[i]);
    }
    public static IcemanStatePacket decode(FriendlyByteBuf b) {
        int entity = b.readVarInt(), action = b.readByte(), age = b.readVarInt(), flags = b.readByte();
        int weapon = b.readByte(), combo = b.readByte();
        float charge = b.readFloat();
        int brush = b.readVarInt() - 1;
        float shell = b.readFloat();
        int sculpture = b.readInt();
        float slide = b.readFloat();
        int[] cd = new int[COOLDOWNS];
        for (int i = 0; i < COOLDOWNS; i++) cd[i] = b.readVarInt();
        return new IcemanStatePacket(entity, action, age, flags, weapon, combo, charge, brush, shell, sculpture, slide, cd);
    }
    public static void handle(IcemanStatePacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.iceman.IcemanClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
