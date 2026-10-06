package com.FIRNI.superheromod.heroes.iceman;

import com.FIRNI.superheromod.core.ability.Ability;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.core.ability.AbilityType;
import com.FIRNI.superheromod.core.character.SuperCharacter;
import net.minecraft.server.level.ServerPlayer;

/**
 * Iceman: every key goes straight to IcemanController, which decides (LMB weapons, RMB brush, Q shell, R shattered
 * ground, X not designed yet). E (the weapon wheel), SHIFT (the ice slide) and CTRL (the sub-zero slide) are read by his
 * own client (IcemanClient) and come in as IcemanInputPacket.
 */
public final class IcemanCharacter extends SuperCharacter {
    public static final String ID = IcemanAction.ID;

    public IcemanCharacter() {
        super(ID, "Iceman — Buzun Efendisi");
        for (AbilitySlot slot : new AbilitySlot[]{AbilitySlot.LMB, AbilitySlot.RMB, AbilitySlot.ULTIMATE, AbilitySlot.SKILL_E, AbilitySlot.SKILL_X})
            registerAbility(new Press(slot));
    }

    private static final class Press extends Ability {
        Press(AbilitySlot slot) { super("iceman_" + slot.name().toLowerCase(), AbilityType.INSTANT, slot); }
        @Override protected void initConfig(AbilityConfig c) { c.set("cooldownTicks", 0); }
        @Override protected void onActivate(ServerPlayer p) { IcemanController.press(p, getSlot(), true); }
        @Override public void deactivate(ServerPlayer p) { IcemanController.press(p, getSlot(), false); }
        @Override public void forceStop(ServerPlayer p) {}
    }
}
