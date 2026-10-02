package com.FIRNI.superheromod.heroes.zed;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.character.SuperCharacter;
import net.minecraft.server.level.ServerPlayer;

/**
 * Zed, Master of Shadows. Left click: quick slashes (Contempt for the Weak rides on them); Q: Razor
 * Shuriken; F: Living Shadow (send, then swap); E: Shadow Slash; R: Death Mark (recast to return to its
 * shadow); X: Shadow Execution, a film. Everything lives per player in ZedController.
 */
public final class ZedCharacter extends SuperCharacter {
    public static final String ID = ZedAction.ID;

    public ZedCharacter() {
        super(ID, "Zed — Gölgelerin Efendisi");
        for (AbilitySlot slot : new AbilitySlot[]{AbilitySlot.LMB, AbilitySlot.ULTIMATE, AbilitySlot.SKILL_F, AbilitySlot.SKILL_V, AbilitySlot.SKILL_E, AbilitySlot.SKILL_X})
            registerAbility(new Press(slot));
    }

    private static final class Press extends Ability {
        Press(AbilitySlot slot) { super("zed_" + slot.name().toLowerCase(), AbilityType.INSTANT, slot); }
        @Override protected void initConfig(AbilityConfig c) { c.set("cooldownTicks", 0); }
        @Override protected void onActivate(ServerPlayer p) { ZedController.press(p, getSlot()); }
        @Override public void deactivate(ServerPlayer p) {}
        @Override public void forceStop(ServerPlayer p) {}
    }
}
