package com.FIRNI.superheromod.heroes.magneto;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.character.SuperCharacter;
import net.minecraft.server.level.ServerPlayer;

/**
 * Magneto, Master of Magnetism. SHIFT: flight; left click: a shard of metal (the fist's punch, the throw); Q: Iron
 * Barrage; E: Metal Scrap Telekinesis; R: Giant Iron Fist; F: Magnetic Iron Shield; X: Magnetic Execution.
 * Everything lives per player in MagnetoController.
 */
public final class MagnetoCharacter extends SuperCharacter {
    public static final String ID = MagnetoAction.ID;

    public MagnetoCharacter() {
        super(ID, "Magneto — Manyetizmanın Efendisi");
        for (AbilitySlot slot : new AbilitySlot[]{AbilitySlot.LMB, AbilitySlot.RMB, AbilitySlot.SHIFT, AbilitySlot.ULTIMATE, AbilitySlot.SKILL_V,
                AbilitySlot.SKILL_E, AbilitySlot.SKILL_F, AbilitySlot.SKILL_X})
            registerAbility(new Press(slot));
    }

    private static final class Press extends Ability {
        Press(AbilitySlot slot) { super("magneto_" + slot.name().toLowerCase(), AbilityType.INSTANT, slot); }
        @Override protected void initConfig(AbilityConfig c) { c.set("cooldownTicks", 0); }
        @Override protected void onActivate(ServerPlayer p) { MagnetoController.press(p, getSlot(), true); }
        @Override public void deactivate(ServerPlayer p) { MagnetoController.press(p, getSlot(), false); }
        @Override public void forceStop(ServerPlayer p) {}
    }
}
