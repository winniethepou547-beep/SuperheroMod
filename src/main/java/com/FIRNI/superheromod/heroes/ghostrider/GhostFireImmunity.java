package com.FIRNI.superheromod.heroes.ghostrider;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SuperheroMod.MODID)
public final class GhostFireImmunity {
    @SubscribeEvent public static void attack(LivingAttackEvent e) {
        if(e.getSource().is(DamageTypeTags.IS_FIRE) && GhostRiderCharacter.ID.equals(AbilityManager.getCharacterId(e.getEntity().getUUID())))e.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase==TickEvent.Phase.END && !e.player.level().isClientSide && GhostRiderCharacter.ID.equals(AbilityManager.getCharacterId(e.player.getUUID())))e.player.clearFire();
    }
}
