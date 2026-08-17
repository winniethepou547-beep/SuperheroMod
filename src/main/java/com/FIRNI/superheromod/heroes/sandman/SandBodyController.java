package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * SAND BODY — Sandman kisa sureligine gevsek kum formuna gecer.
 *
 * TAM DOKUNULMAZLIK YOK. Dokumandaki counterplay kurali korunuyor:
 *   yakin dovus  -> agir sekilde azalir (kum yumruga direnc gostermez)
 *   mermi        -> orta seviyede azalir (icinden gecer ama tamamen degil)
 *   patlama/AOE  -> AZALMAZ, tam vurur (kum kutlesi basinci dagitamaz)
 *   dusme/yanma  -> etkisiz (o zaten bir kum yigini)
 *
 * Bilinen sinirlama: mod icindeki isin saldirlari su an vanilla "playerAttack"
 * kaynagini kullaniyor, yani yakin dovusten ayirt edilemiyor ve azaltmaya
 * takiliyor. Isinlara kendi damage type'i verilirse burasi dogru calisir.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandBodyController {

    private static final float MELEE_REDUCTION = 0.78f;
    private static final float PROJECTILE_REDUCTION = 0.50f;

    private static final Map<UUID, Integer> active = new HashMap<>();

    private SandBodyController() {}

    public static void start(ServerPlayer player, int durationTicks) {
        active.put(player.getUUID(), durationTicks);

        ServerLevel level = (ServerLevel) player.level();

        // Vucut catlayip kuma ayrilir
        level.sendParticles(sand(),
                player.getX(), player.getY() + 1.0, player.getZ(),
                55, 0.45, 0.9, 0.45, 0.12);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.3f, 0.65f);
    }

    public static boolean isActive(UUID playerId) {
        return active.containsKey(playerId);
    }

    public static int remaining(UUID playerId) {
        return active.getOrDefault(playerId, 0);
    }

    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (active.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, Integer>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

            if (player == null) {
                it.remove();
                continue;
            }

            int left = entry.getValue() - 1;
            entry.setValue(left);

            // Kum formundayken vucuttan surekli kum dokulur
            if (left % 2 == 0 && player.level() instanceof ServerLevel level) {
                level.sendParticles(sand(),
                        player.getX(), player.getY() + 0.9, player.getZ(),
                        4, 0.35, 0.7, 0.35, 0.03);
            }

            if (left <= 0) {
                it.remove();
                reform(player);
            }
        }
    }

    private static void reform(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        level.sendParticles(sand(),
                player.getX(), player.getY() + 1.0, player.getZ(),
                45, 0.4, 0.9, 0.4, 0.1);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.2f, 0.8f);
    }

    // ------------------------------------------------------------------
    // Hasar ve savrulma azaltma
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isActive(player.getUUID())) return;

        var source = event.getSource();

        // Kum yigini dusmeden, yanmadan, bogulmadan etkilenmez
        if (source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_DROWNING)) {
            event.setCanceled(true);
            return;
        }

        // Patlama ve benzeri alan hasari TAM vurur — counterplay burada
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return;

        float reduction = source.is(DamageTypeTags.IS_PROJECTILE)
                ? PROJECTILE_REDUCTION
                : MELEE_REDUCTION;

        event.setAmount(event.getAmount() * (1.0f - reduction));

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(sand(),
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    12, 0.35, 0.6, 0.35, 0.08);
        }
    }

    /** Gevsek kum savrulmaz — darbe icinden gecer. */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isActive(player.getUUID())) return;

        event.setStrength(event.getStrength() * 0.2f);
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }
}
