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
    private static final float BODY_REDUCTION = 0.65f;
    private static final UUID HEALTH_BONUS = UUID.fromString("2e719314-8a0e-4fbc-a63c-cd7b5db30af2");
    /** Kristale gelen hasar carpani. */
    private static final float CRYSTAL_MULTIPLIER = 2.0f;

    private static final int DEFAULT_DURATION = 420;   // 21 saniye
    /** Gogus kristali kirilinca ulti bu kadar kisalir. */
    private static final int CHEST_BREAK_PENALTY = 120;

    private static final class Colossus {
        final UUID player;
        int ticksLeft;
        final Map<ColossusCrystal, Float> crystalHealth = new EnumMap<>(ColossusCrystal.class);

        /** Olusma sayaci — dolana kadar dev henuz kurulmamis sayilir. */
        int formTicks = 0;
        int lastStage = -1;

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

        float formProgress() {
            return Math.min(1f, formTicks / (float) ColossusForm.FORM_TICKS);
        }

        boolean isForming() {
            return formTicks < ColossusForm.FORM_TICKS;
        }
    }

    private static final Map<UUID, Colossus> active = new HashMap<>();

    private SandColossusController() {}

    // ------------------------------------------------------------------

    public static boolean isColossus(UUID playerId) {
        return active.containsKey(playerId);
    }
    public static boolean isForming(UUID id) { var c=active.get(id); return c!=null&&c.isForming(); }
    public static float formProgress(UUID id) { var c=active.get(id); return c==null?1:c.formProgress(); }

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
        setBossHealth(player, true);

        ServerLevel level = (ServerLevel) player.level();

        // Vucut parcalanir — olusma buradan sonra asama asama ilerler
        level.sendParticles(sand(),
                player.getX(), player.getY() + 1.0, player.getZ(),
                90, 0.9, 1.2, 0.9, 0.22);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.8f, 0.4f);

        broadcast(player);
    }

    public static void stop(ServerPlayer player) {
        if (active.remove(player.getUUID()) == null) return;
        setBossHealth(player, false);

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
                if (player != null) setBossHealth(player, false);
                it.remove();
                continue;
            }

            if (colossus.staggerTicks > 0) colossus.staggerTicks--;

            // Olusma bitene kadar sure isletilmiyor — dev henuz ayakta degil
            if (colossus.isForming()) {
                tickForming(player, colossus);
                continue;
            }

            tickForm(player, colossus);

            if (--colossus.ticksLeft <= 0) {
                it.remove();
                stopVisualsOnly(player);
            }
        }
    }

    /**
     * OLUSMA — kum askerlerindeki gibi asama asama kurulur.
     *
     * Oyuncu bu sirada yerinde kilitli: dev kurulurken yurumek "olusma"
     * hissini tamamen bozuyordu. Yer cekimi devam ediyor, yani havada
     * asili kalmiyor.
     */
    private static void tickForming(ServerPlayer player, Colossus colossus) {
        colossus.formTicks++;
        float progress = colossus.formProgress();

        // Yerinde kilitle ama yer cekimini kesme
        player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
        player.hurtMarked = true;
        player.fallDistance = 0f;

        if (!(player.level() instanceof ServerLevel level)) return;

        int stage = ColossusForm.stage(progress);
        if (stage != colossus.lastStage) {
            colossus.lastStage = stage;
            onFormStage(level, player, stage);
        }

        emitFormParticles(level, player, progress, stage);

        // Palm contact happens before the push back upright.
        if (colossus.formTicks == 60) {
            level.playSound(null, player.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.32f);
            level.playSound(null, player.blockPosition(),
                    SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 2.0f, 0.25f);

            Vec3 hand=ColossusCrystal.handPosition(player,true);
            groundSlam(level, player, new Vec3(hand.x,player.getY(),hand.z), 7.0, 5.0f);
            broadcast(player);
        } else if (!colossus.isForming() || colossus.formTicks % 3 == 0) {
            broadcast(player);
        }
    }

    /** Her asamanin kendi sesi var — kurulusun ilerledigi duyulmali. */
    private static void onFormStage(ServerLevel level, ServerPlayer player, int stage) {
        switch (stage) {
            case 1 -> level.playSound(null, player.blockPosition(),
                    SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.6f, 0.45f);
            case 2 -> level.playSound(null, player.blockPosition(),
                    SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.7f, 0.38f);
            case 3 -> level.playSound(null, player.blockPosition(),
                    SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.6f, 0.42f);
            case 4 -> level.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.55f);
            default -> { }
        }
    }

    /** Asamaya gore kum farkli yerden toplanir — yerden yukari dogru. */
    private static void emitFormParticles(ServerLevel level, ServerPlayer player,
                                          float progress, int stage) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // Zeminde donen kum halkasi — tum olusma boyunca
        double ring = 2.0 + progress * 3.0;
        for (int i = 0; i < 3; i++) {
            double a = (player.tickCount * 0.35) + i * (Math.PI * 2 / 3);
            level.sendParticles(sand(),
                    x + Math.cos(a) * ring, y + 0.1, z + Math.sin(a) * ring,
                    2, 0.2, 0.05, 0.2, 0.06);
        }

        // Kum yerden yukari akiyor — yukseklik asamayla artiyor
        double height = ColossusCrystal.COLOSSUS_HEIGHT * ColossusForm.growth(progress);
        if (height > 0.2) {
            level.sendParticles(sand(),
                    x, y + height * 0.5, z,
                    6, 1.0, height * 0.45, 1.0, 0.05);
        }

        // Son asamada kristaller belirir
        if (stage == 4 && player.tickCount % 2 == 0) {
            for (ColossusCrystal crystal : ColossusCrystal.values()) {
                Vec3 pos = crystal.worldPosition(player);
                level.sendParticles(ParticleTypes.CRIT,
                        pos.x, pos.y, pos.z, 2, 0.15, 0.15, 0.15, 0.02);
            }
        }
    }

    /**
     * Dev agirdir: COK YAVAS hareket eder ve dusme hasari almaz.
     *
     * Bacagi olmadigi icin yurumez, alttaki kum kutlesi kayarak ilerler.
     * Hareket tamamen engellenmiyor — durdugu yere cakili bir ulti oynanis
     * olarak olu kaliyordu; cok yavas ilerleyebilmesi hem tehdit hem hedef
     * olmasini sagliyor.
     */
    private static void tickForm(ServerPlayer player, Colossus colossus) {
        // Sersemken neredeyse hic ilerleyemez
        int slowness = colossus.staggerTicks > 0 ? 5 : 3;
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 10, slowness, false, false));

        player.fallDistance = 0f;

        if (!(player.level() instanceof ServerLevel level)) return;

        Vec3 velocity = player.getDeltaMovement();
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        boolean moving = speed > 0.01;

        emitLowerMass(level, player, moving, speed);

        // Govdeden surekli dokulen kum
        if (player.tickCount % 4 == 0) {
            level.sendParticles(sand(),
                    player.getX(), player.getY() + 6.0, player.getZ(),
                    3, 1.2, 1.4, 1.2, 0.02);
        }
    }

    /**
     * BEL ALTI KUM KUTLESI — bacak yerine gecen sey.
     *
     * Dururken: kutle yerinde dairesel olarak akar, "nefes alan" bir yigin.
     * Hareket ederken: kum ARKAYA dogru savrulur, yani kutlenin kaydigi
     * gorunur. Bacak animasyonu olmadigi icin hareket hissini tamamen bu
     * veriyor.
     */
    private static void emitLowerMass(ServerLevel level, ServerPlayer player,
                                      boolean moving, double speed) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // Kutlenin tabani — devin genisligine gore
        double spread = ColossusCrystal.COLOSSUS_WIDTH * 0.55;

        if (!moving) {
            // Yerinde akan kum: donen bir halka
            if (player.tickCount % 2 != 0) return;

            for (int i = 0; i < 3; i++) {
                double a = (player.tickCount * 0.12) + i * (Math.PI * 2 / 3);
                level.sendParticles(sand(),
                        x + Math.cos(a) * spread, y + 0.12, z + Math.sin(a) * spread,
                        2, 0.25, 0.06, 0.25, 0.02);
            }

            // Kutlenin govdesi
            level.sendParticles(sand(),
                    x, y + 1.2, z, 4, spread * 0.8, 1.0, spread * 0.8, 0.015);
            return;
        }

        // Hareket halinde: kum arkaya savrulur
        Vec3 vel = player.getDeltaMovement();
        double back = -1.0 / Math.max(0.01, speed);
        double bx = vel.x * back;
        double bz = vel.z * back;

        int count = 3 + (int) Math.min(6, speed * 90);
        level.sendParticles(sand(),
                x + bx * spread * 0.6, y + 0.15, z + bz * spread * 0.6,
                count, spread * 0.7, 0.12, spread * 0.7, 0.05);

        // Kutlenin kendisi de akis yonunde uzar
        level.sendParticles(sand(),
                x, y + 1.0, z,
                4, spread * 0.9, 0.9, spread * 0.9, 0.03);
    }

    private static void stopVisualsOnly(ServerPlayer player) {
        setBossHealth(player, false);
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(sand(),
                player.getX(), player.getY() + 1.5, player.getZ(),
                90, 1.0, 1.4, 1.0, 0.20);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.6f, 0.5f);

        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                ColossusSyncPacket.inactive(player.getUUID()));
    }

    /** Preserve health percentage: entering/leaving the form cannot be used as a heal. */
    private static void setBossHealth(ServerPlayer player, boolean enabled) {
        var attribute=player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if(attribute==null)return;
        float fraction=player.getHealth()/player.getMaxHealth();
        attribute.removeModifier(HEALTH_BONUS);
        if(enabled)attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                HEALTH_BONUS,"Sand colossus vitality",1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        player.setHealth(Math.min(player.getMaxHealth(),fraction*player.getMaxHealth()));
    }

    @SubscribeEvent public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)stop(player);
    }

    // ------------------------------------------------------------------
    // Hasar — kristal mekanigi
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Colossus colossus = active.get(victim.getUUID());
        if (colossus == null) return;

        // Olusurken vurulamaz — dev henuz kurulmadi, kristalleri de yok
        if (colossus.isForming()) {
            event.setCanceled(true);
            return;
        }

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

            Vec3 center = crystal.worldPosition(victim);

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

        Vec3 pos = crystal.worldPosition(victim);

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
        groundSlam(level, source, center, radius, damage, .62);
    }

    static void groundSlam(ServerLevel level, Player source, Vec3 center,
                           double radius, float damage, double upwardSpeed) {
        AABB area = new AABB(center, center).inflate(radius);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != source && e.isAlive())) {

            double dist = target.position().distanceTo(center);
            if (dist > radius) continue;
            double falloff = Math.max(0.3, 1.0 - dist / radius);

            target.hurt(source.damageSources().playerAttack(source),
                    (float) (damage * falloff));

            Vec3 push = target.position().subtract(center);
            Vec3 flat = new Vec3(push.x, 0, push.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();

            target.setDeltaMovement(flat.x * 1.1, upwardSpeed, flat.z * 1.1);
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
                new ColossusSyncPacket(player.getUUID(), true,
                        colossus.formProgress(), states));
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }
}
