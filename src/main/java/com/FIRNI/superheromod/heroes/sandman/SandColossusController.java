package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ColossusSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * SAND COLOSSUS — Sandman'in ultisi.
 *
 * MIMARI KARAR: oyuncu ayri bir dev entity CAGIRMIYOR, kendisi DONUSUYOR.
 * Dokuman "Sandman'in vucudu parcalanir ve devasa bir kum savasciya donusur"
 * diyor — yani kontrol oyuncuda kalmali. Ayri entity olsaydi ya oyuncunun
 * onu surmesi ya da bagimsiz davranmasi gerekirdi; ikisi de tasarima aykiri.
 *
 * Bu karar ayrica KRISTAL isabetlerini kolaylastiriyor: oyuncunun tek bir
 * carpisma kutusu var, ama saldiranin NISAN ISINI kristallere karsi test
 * edilerek hangi bolgeye vurdugu anlasiliyor. Coklu parca entity kurmaya
 * gerek kalmiyor.
 *
 * Denge: govde cok az hasar alir, kristaller 2 KAT alir. Yani kristal
 * vurusu govde vurusundan yaklasik 7 kat etkili — oyuncu nisan almaya
 * zorlaniyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandColossusController {

    /** Govdeye gelen hasarin ne kadari emilir. */
    private static final float BODY_REDUCTION = 0.86f;
    /** Kristale gelen hasar carpani. */
    private static final float CRYSTAL_MULTIPLIER = 2.0f;

    private static final int DEFAULT_DURATION = 420;   // 21 saniye
    /** Gogus kristali kirilinca ulti bu kadar kisalir. */
    private static final int CHEST_BREAK_PENALTY = 120;

    private static final class Colossus {
        final UUID player;
        int ticksLeft;
        final Map<ColossusCrystal, Float> crystalHealth = new EnumMap<>(ColossusCrystal.class);

        /** Kafa kristali kirilinca sersemleme. */
        int staggerTicks;
        /** Omuz kristalleri kirildiysa o kol zayiflar. */
        boolean rightArmBroken;
        boolean leftArmBroken;

        Colossus(UUID player, int duration) {
            this.player = player;
            this.ticksLeft = duration;
            for (ColossusCrystal c : ColossusCrystal.values()) {
                crystalHealth.put(c, c.maxHealth);
            }
        }

        boolean isBroken(ColossusCrystal c) {
            return crystalHealth.getOrDefault(c, 0f) <= 0f;
        }
    }

    private static final Map<UUID, Colossus> active = new HashMap<>();

    private SandColossusController() {}

    // ------------------------------------------------------------------

    public static boolean isColossus(UUID playerId) {
        return active.containsKey(playerId);
    }

    public static boolean isStaggered(UUID playerId) {
        Colossus c = active.get(playerId);
        return c != null && c.staggerTicks > 0;
    }

    /** Mace vurusu icin: o kolun kristali kirildi mi. */
    public static boolean isArmWeakened(UUID playerId, boolean rightArm) {
        Colossus c = active.get(playerId);
        if (c == null) return false;
        return rightArm ? c.rightArmBroken : c.leftArmBroken;
    }

    public static void start(ServerPlayer player) {
        if (active.containsKey(player.getUUID())) return;

        active.put(player.getUUID(), new Colossus(player.getUUID(), DEFAULT_DURATION));

        ServerLevel level = (ServerLevel) player.level();

        // Vucut parcalanir ve cevredeki kum yukselir
        level.sendParticles(sand(),
                player.getX(), player.getY() + 1.0, player.getZ(),
                120, 1.2, 1.6, 1.2, 0.25);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 2.0f, 0.28f);
        level.playSound(null, player.blockPosition(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, 0.45f);

        // Devasa kutle yere iner — cevredekiler savrulur
        groundSlam(level, player, player.position(), 6.0, 4.0f);

        broadcast(player);
    }

    public static void stop(ServerPlayer player) {
        if (active.remove(player.getUUID()) == null) return;

        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(sand(),
                player.getX(), player.getY() + 1.5, player.getZ(),
                90, 1.0, 1.4, 1.0, 0.20);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.6f, 0.5f);

        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                ColossusSyncPacket.inactive(player.getUUID()));
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (active.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, Colossus>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Colossus colossus = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(colossus.player);

            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            if (colossus.staggerTicks > 0) colossus.staggerTicks--;

            tickForm(player, colossus);

            if (--colossus.ticksLeft <= 0) {
                it.remove();
                stopVisualsOnly(player);
            }
        }
    }

    /** Dev agirdir: yavas hareket eder ve dusme hasari almaz. */
    private static void tickForm(ServerPlayer player, Colossus colossus) {
        // Sersemken cok daha yavas
        int slowness = colossus.staggerTicks > 0 ? 3 : 1;
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 10, slowness, false, false));

        player.fallDistance = 0f;

        if (!(player.level() instanceof ServerLevel level)) return;

        // Alt kum kutlesi surekli akar — bacak olmadigi icin bu, hareket
        // hissini veren tek sey
        if (player.tickCount % 2 == 0) {
            level.sendParticles(sand(),
                    player.getX(), player.getY() + 0.15, player.getZ(),
                    5, 0.9, 0.10, 0.9, 0.04);
        }

        // Govdeden surekli dokulen kum
        if (player.tickCount % 4 == 0) {
            level.sendParticles(sand(),
                    player.getX(), player.getY() + 2.6, player.getZ(),
                    3, 0.8, 1.0, 0.8, 0.02);
        }
    }

    private static void stopVisualsOnly(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(sand(),
                player.getX(), player.getY() + 1.5, player.getZ(),
                90, 1.0, 1.4, 1.0, 0.20);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.6f, 0.5f);

        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                ColossusSyncPacket.inactive(player.getUUID()));
    }

    // ------------------------------------------------------------------
    // Hasar — kristal mekanigi
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Colossus colossus = active.get(victim.getUUID());
        if (colossus == null) return;

        ColossusCrystal hit = findHitCrystal(colossus, victim, event.getSource().getEntity());

        if (hit != null) {
            // KRISTAL ISABETI — 2 kat hasar, govde zirhi devreye GIRMEZ
            float damage = event.getAmount() * CRYSTAL_MULTIPLIER;
            event.setAmount(damage);
            damageCrystal(victim, colossus, hit, damage);
            return;
        }

        // Govde — cogu hasar emilir
        event.setAmount(event.getAmount() * (1.0f - bodyReduction(colossus)));

        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(sand(),
                    victim.getX(), victim.getY() + 2.0, victim.getZ(),
                    8, 0.7, 0.8, 0.7, 0.05);
        }
    }

    /** Gogus kristali kirildiysa zirh duser. */
    private static float bodyReduction(Colossus colossus) {
        return colossus.isBroken(ColossusCrystal.CHEST)
                ? BODY_REDUCTION * 0.55f
                : BODY_REDUCTION;
    }

    /**
     * Saldiranin NISAN ISINI kristallere karsi test eder.
     *
     * Oyuncunun tek carpisma kutusu oldugu icin "nereye vurdu" bilgisi yok;
     * ama saldiran nisan aldigi icin bakis isini kullanmak dogru sonucu
     * veriyor. Coklu parca entity kurmaya gerek kalmiyor.
     */
    private static ColossusCrystal findHitCrystal(Colossus colossus, Player victim,
                                                  Entity attacker) {
        if (!(attacker instanceof LivingEntity shooter)) return null;

        Vec3 eye = RaycastSystem.getEyeOrigin(shooter);
        Vec3 look = RaycastSystem.getLookDirection(shooter);
        Vec3 base = victim.position();
        float yaw = victim.getYRot();

        ColossusCrystal best = null;
        double bestDist = Double.MAX_VALUE;

        for (ColossusCrystal crystal : ColossusCrystal.values()) {
            if (colossus.isBroken(crystal)) continue;

            Vec3 center = crystal.worldPosition(base, yaw);

            // Isinin kristale en yakin gectigi nokta
            Vec3 toCenter = center.subtract(eye);
            double along = toCenter.dot(look);
            if (along < 0) continue;   // arkada kaldi

            Vec3 closest = eye.add(look.scale(along));
            double miss = closest.distanceTo(center);

            if (miss <= crystal.radius && along < bestDist) {
                bestDist = along;
                best = crystal;
            }
        }

        return best;
    }

    private static void damageCrystal(Player victim, Colossus colossus,
                                      ColossusCrystal crystal, float damage) {
        float before = colossus.crystalHealth.getOrDefault(crystal, 0f);
        float after = Math.max(0f, before - damage);
        colossus.crystalHealth.put(crystal, after);

        if (!(victim.level() instanceof ServerLevel level)) return;

        Vec3 pos = crystal.worldPosition(victim.position(), victim.getYRot());

        // Kristal isabeti govde isabetinden BELIRGIN sekilde farkli duymali
        level.playSound(null, BlockPos.containing(pos),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 1.4f);
        level.sendParticles(ParticleTypes.CRIT,
                pos.x, pos.y, pos.z, 14, 0.3, 0.3, 0.3, 0.2);

        if (after > 0f || before <= 0f) return;

        onCrystalBroken(victim, colossus, crystal, level, pos);
    }

    /** Kristal kirildi — devin o bolgesi kalici olarak zayiflar. */
    private static void onCrystalBroken(Player victim, Colossus colossus,
                                        ColossusCrystal crystal,
                                        ServerLevel level, Vec3 pos) {
        level.playSound(null, BlockPos.containing(pos),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.8f, 0.6f);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 2, 0.2, 0.2, 0.2, 0);
        level.sendParticles(sand(), pos.x, pos.y, pos.z, 30, 0.5, 0.5, 0.5, 0.18);

        switch (crystal) {
            case CHEST -> {
                // Ulti kisalir ve zirh duser — en degerli kristal bu
                colossus.ticksLeft = Math.max(20, colossus.ticksLeft - CHEST_BREAK_PENALTY);
                level.playSound(null, victim.blockPosition(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4f, 0.7f);
            }
            case RIGHT_SHOULDER -> colossus.rightArmBroken = true;
            case LEFT_SHOULDER -> colossus.leftArmBroken = true;
            case HEAD -> {
                colossus.staggerTicks = 60;
                level.playSound(null, victim.blockPosition(),
                        SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 2.0f, 0.4f);
            }
            case BACK -> {
                // Sirt kirilinca kum kutlesi dagilir, hareket daha da yavaslar
                victim.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 200, 2, false, false));
            }
        }

        broadcast((ServerPlayer) victim);
    }

    /**
     * Devin carpisma kutusu da devasa olmali.
     *
     * Aksi halde 10 bloktan gorunen bir figure ancak ayak dibinden vurulur;
     * gorunen boy ile vurulabilen alan tutmazdi.
     */
    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isColossus(player.getUUID())) return;

        event.setNewSize(EntityDimensions.scalable(
                ColossusCrystal.COLOSSUS_WIDTH, ColossusCrystal.COLOSSUS_HEIGHT));
        event.setNewEyeHeight(ColossusCrystal.COLOSSUS_HEIGHT * 0.86f);
    }

    /** Dev savrulmaz. */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isColossus(player.getUUID())) return;

        event.setStrength(event.getStrength() * 0.05f);
    }

    // ------------------------------------------------------------------
    // Ortak
    // ------------------------------------------------------------------

    /** Yere agir inis — cevredekilere hasar ve savurma. */
    static void groundSlam(ServerLevel level, Player source, Vec3 center,
                           double radius, float damage) {
        AABB area = new AABB(center, center).inflate(radius);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != source && e.isAlive())) {

            double dist = target.position().distanceTo(center);
            double falloff = Math.max(0.3, 1.0 - dist / radius);

            target.hurt(source.damageSources().playerAttack(source),
                    (float) (damage * falloff));

            Vec3 push = target.position().subtract(center);
            Vec3 flat = new Vec3(push.x, 0, push.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();

            target.setDeltaMovement(flat.x * 1.1, 0.62, flat.z * 1.1);
            target.hurtMarked = true;
        }

        // Yerde disa kosan kum halkasi
        for (int i = 0; i < 32; i++) {
            double a = i / 32.0 * Math.PI * 2;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            level.sendParticles(sand(),
                    center.x + dx * radius * 0.5, center.y + 0.2, center.z + dz * radius * 0.5,
                    2, dx * 0.3, 0.05, dz * 0.3, 0.16);
        }
    }

    private static void broadcast(ServerPlayer player) {
        Colossus colossus = active.get(player.getUUID());
        if (colossus == null) return;

        int[] states = new int[ColossusCrystal.values().length];
        for (ColossusCrystal crystal : ColossusCrystal.values()) {
            states[crystal.ordinal()] = ColossusCrystal.stateOf(
                    colossus.crystalHealth.getOrDefault(crystal, 0f), crystal.maxHealth);
        }

        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new ColossusSyncPacket(player.getUUID(), true, states));
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }
}
