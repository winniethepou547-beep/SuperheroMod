package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * What a Hulk/Banner is doing: action and its age, flags (Hulk form...), the charge of whatever is
 * being charged (0..1), guard stamina, the thrown rock (where, what block), the cooldowns left on
 * his moves (ticks: clap, pound, rock, rage, charged punch, leap, change) and the film's smash delay.
 */
public record HulkStatePacket(int entity, int action, int age, int flags, float charge, float stamina,
                              Vec3 rock, int rockBlock, int[] cooldowns, int smashDelay) {
    public static final int COOLDOWNS = 7;
    public static void encode(HulkStatePacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entity); b.writeByte(p.action); b.writeVarInt(p.age); b.writeByte(p.flags);
        b.writeFloat(p.charge); b.writeFloat(p.stamina);
        b.writeDouble(p.rock.x); b.writeDouble(p.rock.y); b.writeDouble(p.rock.z); b.writeVarInt(p.rockBlock);
        for (int i = 0; i < COOLDOWNS; i++) b.writeVarInt(i < p.cooldowns.length ? p.cooldowns[i] : 0);
        b.writeVarInt(p.smashDelay);
    }
    public static HulkStatePacket decode(FriendlyByteBuf b) {
        int entity = b.readVarInt(), action = b.readByte(), age = b.readVarInt(), flags = b.readByte();
        float charge = b.readFloat(), stamina = b.readFloat();
        Vec3 rock = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        int block = b.readVarInt();
        int[] cd = new int[COOLDOWNS];
        for (int i = 0; i < COOLDOWNS; i++) cd[i] = b.readVarInt();
        return new HulkStatePacket(entity, action, age, flags, charge, stamina, rock, block, cd, b.readVarInt());
    }
    public static void handle(HulkStatePacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.hulk.HulkClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
