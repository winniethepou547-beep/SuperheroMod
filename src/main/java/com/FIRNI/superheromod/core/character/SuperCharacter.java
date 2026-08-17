package com.FIRNI.superheromod.core.character;

import com.FIRNI.superheromod.core.ability.Ability;
import com.FIRNI.superheromod.core.ability.AbilitySlot;

import java.util.ArrayList;
import java.util.List;

public abstract class SuperCharacter {

    private final String characterId;
    private final String displayName;
    private final List<Ability> abilities = new ArrayList<>();

    public SuperCharacter(String characterId, String displayName) {
        this.characterId = characterId;
        this.displayName = displayName;
    }

    protected void registerAbility(Ability ability) {
        abilities.add(ability);
    }

    public String getCharacterId() {
        return characterId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<Ability> getAbilities() {
        return abilities;
    }

    public Ability getAbilityById(String id) {
        for (Ability ability : abilities) {
            if (ability.getId().equals(id)) {
                return ability;
            }
        }
        return null;
    }

    public Ability getAbilityBySlot(AbilitySlot slot) {
        for (Ability ability : abilities) {
            if (ability.getSlot() == slot) {
                return ability;
            }
        }
        return null;
    }
}