package com.FIRNI.superheromod.heroes.thor;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.character.SuperCharacter;
import net.minecraft.server.level.ServerPlayer;

/**
 * Thor, God of Thunder. Built for 1v1: a three-hit Mjolnir combo (left click), the throw and
 * recall (right click), spin flight (shift), the whirling guard with a parry (E), the Wakanda
 * strike (R) and the God of Thunder film (X). Every timer lives per player in ThorController.
 */
public final class ThorCharacter extends SuperCharacter {
    public static final String ID = ThorAction.ID;

    public ThorCharacter() {
        super(ID, "Thor — God of Thunder");
        registerAbility(new Press(AbilitySlot.LMB));
        registerAbility(new Press(AbilitySlot.RMB));
        registerAbility(new Press(AbilitySlot.SHIFT));
        registerAbility(new Press(AbilitySlot.SKILL_V));   // E key: whirling guard
        registerAbility(new Press(AbilitySlot.SKILL_E));   // R key: Wakanda strike
        registerAbility(new Press(AbilitySlot.SKILL_X));   // X key: God of Thunder
    }

    private static final class Press extends Ability {
        Press(AbilitySlot slot) { super("thor_" + slot.name().toLowerCase(), AbilityType.INSTANT, slot); }
        @Override protected void initConfig(AbilityConfig c) { c.set("cooldownTicks", 0); }
        @Override protected void onActivate(ServerPlayer p) { ThorController.press(p, getSlot()); }
        @Override public void deactivate(ServerPlayer p) { ThorController.release(p, getSlot()); }
        @Override public void forceStop(ServerPlayer p) { ThorController.release(p, getSlot()); }
    }
}
