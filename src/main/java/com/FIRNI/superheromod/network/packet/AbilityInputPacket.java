package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AbilityInputPacket {

    private final int slotOrdinal;
    private final boolean pressed;

    public AbilityInputPacket(AbilitySlot slot, boolean pressed) {
        this.slotOrdinal = slot.ordinal();
        this.pressed = pressed;
    }

    private AbilityInputPacket(int slotOrdinal, boolean pressed) {
        this.slotOrdinal = slotOrdinal;
        this.pressed = pressed;
    }

    public static void encode(AbilityInputPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.slotOrdinal);
        buf.writeBoolean(msg.pressed);
    }

    public static AbilityInputPacket decode(FriendlyByteBuf buf) {
        return new AbilityInputPacket(buf.readByte(), buf.readBoolean());
    }

    public static void handle(AbilityInputPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            AbilitySlot[] slots = AbilitySlot.values();
            if (msg.slotOrdinal < 0 || msg.slotOrdinal >= slots.length) return;
            AbilitySlot slot = slots[msg.slotOrdinal];
            // A Magneto spike in them: their left click is for pulling it out, not for attacking.
            if (slot == AbilitySlot.LMB && msg.pressed && com.FIRNI.superheromod.heroes.magneto.MagnetoSpike.stuck(player)) return;

            if (msg.pressed) {
                // Batman's sonic emitters can be struck down by any hero's left click.
                if (slot == AbilitySlot.LMB) com.FIRNI.superheromod.heroes.batman.SonicEmitterEntity.struckBy(player);
                AbilityManager.activateAbility(player, slot);
            } else {
                AbilityManager.deactivateAbility(player, slot);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
