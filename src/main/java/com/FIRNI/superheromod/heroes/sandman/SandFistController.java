package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * Sand Fist'in faz makinesi.
 *
 * Faz akisi (tick cinsinden):
 *   WINDUP    0-4    kol geriye cekilir
 *   EXPAND    5-8    kol kum olarak genisler, yumruk buyur
 *   STRIKE    9-10   ileri savrulur
 *   IMPACT    11     HASAR SADECE BURADA uygulanir
 *   RECOVERY  12-18  kol kuculerek normale doner
 *
 * Hasarin animasyonun ortasinda durmasi onemli: vurus daha basarken hasar
 * verirse darbe agirligini kaybediyor. Ayni vurusta bir hedefe birden fazla
 * kez hasar gitmemesi icin vurulanlar swing basina tutuluyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandFistController {

    public enum Phase { WINDUP, EXPAND, STRIKE, IMPACT, RECOVERY }

    private static final int WINDUP_END = 4;
    private static final int EXPAND_END = 8;
    private static final int STRIKE_END = 10;
    private static final int IMPACT_TICK = 11;
    private static final int TOTAL_TICKS = 18;

    /** Kum tanesi rengi — dust partikulu icin. */
    private static final net.minecraft.core.particles.DustParticleOptions SAND_DUST =
            new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.85f, 0.74f, 0.48f), 1.1f);

    private static final BlockParticleOption SAND_BLOCK =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

    private static final class Swing {
        final UUID player;
        final float damage;
        final double range;
        final double radius;
        final double knockback;
        final double knockbackVertical;

        int ticks = 0;
        /** Ayni vurusta ayni hedefe tekrar hasar gitmesin. */
        final Set<UUID> hit = new HashSet<>();

        Swing(UUID player, AbilityConfig cfg) {
            this.player = player;
            this.damage = cfg.getFloat("damage", 6.0f);
            this.range = cfg.getDouble("range", 4.2);
            this.radius = cfg.getDouble("radius", 1.9);
            this.knockback = cfg.getDouble("knockback", 1.15);
            this.knockbackVertical = cfg.getDouble("knockbackVertical", 0.42);
        }

        Phase phase() {
            if (ticks <= WINDUP_END) return Phase.WINDUP;
            if (ticks <= EXPAND_END) return Phase.EXPAND;
            if (ticks <= STRIKE_END) return Phase.STRIKE;
            if (ticks == IMPACT_TICK) return Phase.IMPACT;
            return Phase.RECOVERY;
        }
    }

    private static final Map<UUID, Swing> swings = new HashMap<>();

    private SandFistController() {}

    public static void start(ServerPlayer player, AbilityConfig cfg) {
        swings.put(player.getUUID(), new Swing(player.getUUID(), cfg));

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    public static boolean isSwinging(UUID playerId) {
        return swings.containsKey(playerId);
    }

    /** Istemci poz sistemi icin: oyuncu su an hangi fazda? */
    public static Phase phaseOf(UUID playerId) {
        Swing swing = swings.get(playerId);
        return swing == null ? null : swing.phase();
    }

    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (swings.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, Swing>> it = swings.entrySet().iterator();
        while (it.hasNext()) {
            Swing swing = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(swing.player);

            if (player == null) {
                it.remove();
                continue;
            }

            swing.ticks++;
            tickSwing(player, swing);

            if (swing.ticks >= TOTAL_TICKS) it.remove();
        }
    }

    private static void tickSwing(ServerPlayer player, Swing swing) {
        ServerLevel level = (ServerLevel) player.level();

        switch (swing.phase()) {
            case WINDUP -> {
                // Kol geriye cekilirken cevreden kum toplaniyor
                if (swing.ticks % 2 == 0) gatherSand(level, player);
            }
            case EXPAND -> {
                // Yumruk buyurken kopan kucuk kum parcalari
                Vec3 fist = fistPosition(player, 1.1);
                level.sendParticles(SAND_DUST, fist.x, fist.y, fist.z,
                        6, 0.28, 0.28, 0.28, 0.01);
                level.sendParticles(SAND_BLOCK, fist.x, fist.y, fist.z,
                        3, 0.22, 0.22, 0.22, 0.02);

                if (swing.ticks == EXPAND_END) {
                    level.playSound(null, player.blockPosition(),
                            SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.1f, 0.5f);
                }
            }
            case STRIKE -> {
                // Savrulurken yumrugun arkasinda kum izi
                Vec3 fist = fistPosition(player, 1.8);
                level.sendParticles(SAND_DUST, fist.x, fist.y, fist.z,
                        8, 0.35, 0.35, 0.35, 0.03);
            }
            case IMPACT -> applyImpact(player, swing, level);
            case RECOVERY -> {
                // Kol kuculurken dokulen kum
                if (swing.ticks % 3 == 0) {
                    Vec3 fist = fistPosition(player, 1.0);
                    level.sendParticles(SAND_BLOCK, fist.x, fist.y, fist.z,
                            2, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }
    }

    /** Hasar SADECE burada — impact karesinde. */
    private static void applyImpact(ServerPlayer player, Swing swing, ServerLevel level) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getLookAngle();
        Vec3 center = eye.add(look.scale(swing.range * 0.5));

        AABB box = new AABB(center, center).inflate(swing.radius + swing.range * 0.5);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());

        for (LivingEntity target : targets) {
            if (!swing.hit.add(target.getUUID())) continue;

            // Sadece onumuzdeki koni icindekiler — arkadakiler yumruk yemez
            Vec3 toTarget = target.position().add(0, target.getBbHeight() * 0.5, 0)
                    .subtract(eye);
            if (toTarget.lengthSqr() > swing.range * swing.range) continue;
            if (toTarget.lengthSqr() > 1.0E-4
                    && look.dot(toTarget.normalize()) < 0.35) continue;

            target.hurt(player.damageSources().playerAttack(player), swing.damage);

            Vec3 push = new Vec3(look.x, 0, look.z);
            push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize();
            target.setDeltaMovement(
                    push.x * swing.knockback,
                    swing.knockbackVertical,
                    push.z * swing.knockback);
            target.hurtMarked = true;

            Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
            level.sendParticles(SAND_BLOCK, hitPos.x, hitPos.y, hitPos.z,
                    18, 0.4, 0.4, 0.4, 0.14);
        }

        Vec3 fist = fistPosition(player, swing.range * 0.8);

        // Darbe bulutu
        level.sendParticles(SAND_DUST, fist.x, fist.y, fist.z,
                26, 0.55, 0.5, 0.55, 0.06);
        level.sendParticles(SAND_BLOCK, fist.x, fist.y, fist.z,
                20, 0.5, 0.45, 0.5, 0.12);

        level.playSound(null, BlockPos.containing(fist),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.5f);
        level.playSound(null, BlockPos.containing(fist),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);

        groundShockwave(level, fist);
    }

    /**
     * Yumruk yere yakin patladiysa zeminde kucuk bir kum halkasi acilir.
     * Blok kirmiyor — Sandman'in kimligi yikim degil alan kontrolu.
     */
    private static void groundShockwave(ServerLevel level, Vec3 fist) {
        BlockPos ground = BlockPos.containing(fist);
        int drop = 0;
        while (drop < 3 && level.getBlockState(ground).isAir()) {
            ground = ground.below();
            drop++;
        }
        if (level.getBlockState(ground).isAir()) return;

        double y = ground.getY() + 1.05;
        for (int i = 0; i < 20; i++) {
            double a = i / 20.0 * Math.PI * 2;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            level.sendParticles(SAND_BLOCK,
                    fist.x + dx * 1.5, y, fist.z + dz * 1.5,
                    1, 0.08, 0.02, 0.08, 0.06);
        }
    }

    /**
     * Windup sirasinda kolun cevresine kum cekilir: parcalar disaridan
     * yumrugun olusacagi noktaya dogru akar. Hiz vektoru ice dogru veriliyor,
     * boylece "toplaniyor" izlenimi olusuyor.
     */
    private static void gatherSand(ServerLevel level, ServerPlayer player) {
        Vec3 fist = fistPosition(player, 0.9);

        for (int i = 0; i < 5; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = 0.9 + level.random.nextDouble() * 0.7;
            double dx = Math.cos(a) * r;
            double dz = Math.sin(a) * r;
            double dy = (level.random.nextDouble() - 0.3) * 0.8;

            level.sendParticles(SAND_DUST,
                    fist.x + dx, fist.y + dy, fist.z + dz,
                    1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Yumrugun o anki dunya konumu — goz hizasindan biraz asagida ve onde. */
    private static Vec3 fistPosition(ServerPlayer player, double forward) {
        Vec3 look = player.getLookAngle();
        return player.getEyePosition(1.0f)
                .add(look.scale(forward))
                .add(0, -0.35, 0);
    }
}
