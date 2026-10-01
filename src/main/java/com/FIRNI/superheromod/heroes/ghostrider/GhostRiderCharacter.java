package com.FIRNI.superheromod.heroes.ghostrider;

import com.FIRNI.superheromod.core.character.SuperCharacter;
import com.FIRNI.superheromod.core.ability.*;
import net.minecraft.server.level.ServerPlayer;

/** Johnny Blaze. Combat timers belong to the player, never to these shared definitions. */
public final class GhostRiderCharacter extends SuperCharacter {
    public static final String ID = "ghost_rider";
    public GhostRiderCharacter() {
        super(ID, "Ghost Rider — Johnny Blaze");
        registerAbility(new ChainAction(AbilitySlot.LMB));
        registerAbility(new ChainAction(AbilitySlot.RMB));
        registerAbility(new SummonCycle());
        registerAbility(new Breath());
        registerAbility(new ChainAction(AbilitySlot.SKILL_F));
        registerAbility(new Penance());
    }
    private static final class Penance extends Ability {
        Penance(){super("ghost_rider_penance_stare",AbilityType.INSTANT,AbilitySlot.SKILL_X);}
        protected void initConfig(AbilityConfig c){c.set("cooldownTicks",0);}
        protected void onActivate(ServerPlayer p){PenanceStare.tryStart(p);}
    }
    private static final class Breath extends Ability {
        Breath(){super("ghost_rider_hellfire_breath",AbilityType.INSTANT,AbilitySlot.SKILL_E);}
        protected void initConfig(AbilityConfig c){c.set("cooldownTicks",0);}
        protected void onActivate(ServerPlayer p){HellfireBreathController.start(p);}
        @Override public void deactivate(ServerPlayer p){HellfireBreathController.stop(p);}
        @Override public void forceStop(ServerPlayer p){HellfireBreathController.stop(p);}
    }
    private static final class SummonCycle extends Ability {
        SummonCycle() { super("ghost_rider_cycle", AbilityType.INSTANT, AbilitySlot.SHIFT); }
        protected void initConfig(AbilityConfig c) { c.set("cooldownTicks", 0); }
        protected void onActivate(ServerPlayer p) { if(!HellfireBreathController.active(p.getUUID()))HellCycleEntity.summon(p); }
    }
    private static final class ChainAction extends Ability {
        ChainAction(AbilitySlot slot) { super("ghost_rider_" + slot.name().toLowerCase(), AbilityType.INSTANT, slot); }
        protected void initConfig(AbilityConfig c) { c.set("cooldownTicks", 0); }
        protected void onActivate(ServerPlayer p) { GhostChainController.press(p, getSlot()); }
        @Override public void deactivate(ServerPlayer p){GhostChainController.release(p,getSlot());}
        @Override public void forceStop(ServerPlayer p){GhostChainController.cancel(p);}
    }
}
