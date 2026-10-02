package com.FIRNI.superheromod.heroes.hulk;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.character.SuperCharacter;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bruce Banner / Hulk. G changes between them; every move works only as Hulk: punches and the
 * charged punch (left click), guard (right click), Thunderclap (R), ground pound (F), rock (C),
 * charged leap (hold space) and Gamma Rage (X). Timers live per player in HulkController.
 */
public final class HulkCharacter extends SuperCharacter {
    public static final String ID = HulkAction.ID;

    public HulkCharacter() {
        super(ID, "Hulk — Bruce Banner");
        for (AbilitySlot slot : new AbilitySlot[]{AbilitySlot.SKILL_G, AbilitySlot.LMB, AbilitySlot.RMB, AbilitySlot.SKILL_E,
                AbilitySlot.SKILL_F, AbilitySlot.SKILL_C, AbilitySlot.SKILL_X})
            registerAbility(new Press(slot));
    }

    private static final class Press extends Ability {
        Press(AbilitySlot slot) { super("hulk_" + slot.name().toLowerCase(), AbilityType.INSTANT, slot); }
        @Override protected void initConfig(AbilityConfig c) { c.set("cooldownTicks", 0); }
        @Override protected void onActivate(ServerPlayer p) { HulkController.press(p, getSlot()); }
        @Override public void deactivate(ServerPlayer p) { HulkController.release(p, getSlot()); }
        @Override public void forceStop(ServerPlayer p) { HulkController.release(p, getSlot()); }
    }
}
