package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.character.SuperCharacter;
import net.minecraft.server.level.ServerPlayer;

/**
 * Batman (Arkham). Left click: punches; right click: Batarangs; R: gadget wheel / use gadget; E: grapnel gun;
 * CTRL: roll; SPACE in the air: cape glide. The keys his own client reads (R wheel, E, CTRL, SPACE) arrive as
 * BatmanInputPacket; left and right click come through here. Everything lives per player in BatmanController.
 */
public final class BatmanCharacter extends SuperCharacter {
    public static final String ID = BatmanAction.ID;

    public BatmanCharacter() {
        super(ID, "Batman — Kara Şövalye");
        for (AbilitySlot slot : new AbilitySlot[]{AbilitySlot.LMB, AbilitySlot.RMB})
            registerAbility(new Press(slot));
    }

    private static final class Press extends Ability {
        Press(AbilitySlot slot) { super("batman_" + slot.name().toLowerCase(), AbilityType.INSTANT, slot); }
        @Override protected void initConfig(AbilityConfig c) { c.set("cooldownTicks", 0); }
        @Override protected void onActivate(ServerPlayer p) { BatmanController.press(p, getSlot(), true); }
        @Override public void deactivate(ServerPlayer p) { BatmanController.press(p, getSlot(), false); }
        @Override public void forceStop(ServerPlayer p) {}
    }
}
