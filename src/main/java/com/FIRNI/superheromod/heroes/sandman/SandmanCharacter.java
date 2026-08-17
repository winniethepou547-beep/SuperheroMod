package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.character.SuperCharacter;

/**
 * SANDMAN — alan kontrolu / summon / donusum / tank.
 *
 * Cyclops menzilli isin ve hassas nisan uzerine kuruluyken Sandman yakin
 * dovus, arazi manipulasyonu ve kumdan asker uretimi uzerine kurulu.
 *
 * Yetenekler adim adim ekleniyor; su an sadece Sand Fist var.
 */
public class SandmanCharacter extends SuperCharacter {

    public static final String ID = "sandman";

    public SandmanCharacter() {
        super(ID, "Sandman");
        registerAbility(new SandFistAbility());    // LMB
        registerAbility(new SandSpikeAbility());   // RMB
    }
}
