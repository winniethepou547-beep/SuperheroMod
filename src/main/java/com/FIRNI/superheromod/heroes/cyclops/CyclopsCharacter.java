package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.character.SuperCharacter;

public class CyclopsCharacter extends SuperCharacter {

    public static final String ID = "cyclops";

    public CyclopsCharacter() {
        super(ID, "Cyclops");
        registerAbility(new OpticBlastAbility());      // LMB
        registerAbility(new ConcussiveBeamAbility());   // RMB
        registerAbility(new RapidFireAbility());        // R
        registerAbility(new RicochetAbility());         // C
        registerAbility(new PropulsionBurstAbility());   // SHIFT
        registerAbility(new OpticAscentAbility());       // F
        registerAbility(new XRayAbility());              // X
        registerAbility(new RubyRageUltimate());         // Q
    }
}
