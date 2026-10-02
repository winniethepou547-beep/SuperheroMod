package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * What a Zed is doing: his action and its age, which hand the last slash used, the cooldowns of
 * Q/W/E/R, his two shadows (the W shadow: where, facing, what it is mimicking and since when, ticks
 * left; the R shadow: where, facing, ticks left) and the Death Mark (who, ticks left, damage stored).
 */
public record ZedStatePacket(int entity, int action, int age, int flags, int[] cooldowns,
                             boolean wAlive, Vec3 wPos, float wYaw, int wAction, int wAge, int wLeft,
                             boolean rAlive, Vec3 rPos, float rYaw, int rLeft,
                             int markTarget, int markLeft, float markStored) {
    public static final int COOLDOWNS = 4;
    private static void vec(FriendlyByteBuf b, Vec3 v) { b.writeFloat((float) v.x); b.writeFloat((float) v.y); b.writeFloat((float) v.z); }
    private static Vec3 vec(FriendlyByteBuf b) { return new Vec3(b.readFloat(), b.readFloat(), b.readFloat()); }
    public static void encode(ZedStatePacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entity); b.writeByte(p.action); b.writeVarInt(p.age); b.writeByte(p.flags);
        for (int i = 0; i < COOLDOWNS; i++) b.writeVarInt(i < p.cooldowns.length ? p.cooldowns[i] : 0);
        b.writeBoolean(p.wAlive); vec(b, p.wPos); b.writeFloat(p.wYaw); b.writeByte(p.wAction); b.writeVarInt(p.wAge); b.writeVarInt(p.wLeft);
        b.writeBoolean(p.rAlive); vec(b, p.rPos); b.writeFloat(p.rYaw); b.writeVarInt(p.rLeft);
        b.writeVarInt(p.markTarget + 1); b.writeVarInt(p.markLeft); b.writeFloat(p.markStored);
    }
    public static ZedStatePacket decode(FriendlyByteBuf b) {
        int entity = b.readVarInt(), action = b.readByte(), age = b.readVarInt(), flags = b.readByte();
        int[] cd = new int[COOLDOWNS];
        for (int i = 0; i < COOLDOWNS; i++) cd[i] = b.readVarInt();
        boolean wAlive = b.readBoolean(); Vec3 wPos = vec(b); float wYaw = b.readFloat(); int wAction = b.readByte(), wAge = b.readVarInt(), wLeft = b.readVarInt();
        boolean rAlive = b.readBoolean(); Vec3 rPos = vec(b); float rYaw = b.readFloat(); int rLeft = b.readVarInt();
        int mark = b.readVarInt() - 1, markLeft = b.readVarInt(); float stored = b.readFloat();
        return new ZedStatePacket(entity, action, age, flags, cd, wAlive, wPos, wYaw, wAction, wAge, wLeft, rAlive, rPos, rYaw, rLeft, mark, markLeft, stored);
    }
    public static void handle(ZedStatePacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.zed.ZedClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
