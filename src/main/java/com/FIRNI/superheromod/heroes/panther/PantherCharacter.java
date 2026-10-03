package com.FIRNI.superheromod.heroes.panther;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.character.SuperCharacter;
import net.minecraft.server.level.ServerPlayer;

/**
 * Black Panther, King of Wakanda. Left click: Vibranium Claws (held: the frenzy); SHIFT: Panther Pounce;
 * Q: the spinning triple kick; E: the kinetic release; R: Panther Reflex; X: kept for a future ultimate.
 * Everything lives per player in PantherController.
 */
public final class PantherCharacter extends SuperCharacter {
    public static final String ID = PantherAction.ID;

    public PantherCharacter() {
        super(ID, "Black Panther — Wakanda'nın Kralı");
        for (AbilitySlot slot : new AbilitySlot[]{AbilitySlot.LMB, AbilitySlot.SHIFT, AbilitySlot.ULTIMATE, AbilitySlot.SKILL_V, AbilitySlot.SKILL_E, AbilitySlot.SKILL_X})
            registerAbility(new Press(slot));
    }

    private static final class Press extends Ability {
        Press(AbilitySlot slot) { super("panther_" + slot.name().toLowerCase(), AbilityType.INSTANT, slot); }
        @Override protected void initConfig(AbilityConfig c) { c.set("cooldownTicks", 0); }
        @Override protected void onActivate(ServerPlayer p) { PantherController.press(p, getSlot(), true); }
        @Override public void deactivate(ServerPlayer p) { PantherController.press(p, getSlot(), false); }
        @Override public void forceStop(ServerPlayer p) {}
    }
}
