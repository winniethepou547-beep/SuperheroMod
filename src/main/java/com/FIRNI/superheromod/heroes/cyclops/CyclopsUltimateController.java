package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.Ability;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.core.ability.AbilityState;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BeamSyncPacket;
import com.FIRNI.superheromod.network.packet.GroundFxPacket;
import com.FIRNI.superheromod.network.packet.UltimateStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

import java.util.*;

/**
 * Ruby Rage ultisinin faz makinesi.
 *
 * AIMING  -> Confirm/Cancel bekler (sure sinirsiz)
 * WINDUP  -> kafa asagi + eller capraz, enerji toplanir
 * SWEEP   -> devasa lazer asagidan yukariya hat ceker, degdigi bloklar isinir
 * CHARGE  -> isinan bloklar kizil catlaklarla parlar
 * EXPLODE -> bloklar havaya firlar, cevredeki hedefler can yuzdesiyle hasar alir
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class CyclopsUltimateController {

    public enum Phase { AIMING, WINDUP, SWEEP, CHARGE, EXPLODE }

    // --- Ayarlar ---
    private static final int WINDUP_TICKS = 10;
    private static final int SWEEP_TICKS = 10;
    private static final int CHARGE_TICKS = 30;

    private static final double BEAM_RANGE = 45.0;
    private static final float BEAM_WIDTH = 3.2f;

    private static final float SWEEP_START_PITCH = 65f;   // asagi
    private static final float SWEEP_END_PITCH = -35f;    // yukari

    /** Dogrudan lazer isabeti: maks canin yuzdesi. */
    private static final float BEAM_HEALTH_PERCENT = 0.45f;
    /** Patlama: maks canin yuzdesi. */
    private static final float BLAST_HEALTH_PERCENT = 0.30f;
    private static final double BLAST_RADIUS = 6.0;

    /** Sadece patlama aninda kullaniliyor; sarj/yildirim efektleri geometri. */
    private static final DustParticleOptions CHARGE_CRACK =
            new DustParticleOptions(new Vector3f(1.0f, 0.12f, 0.05f), 1.4f);

    private static final class UltState {
        Phase phase = Phase.AIMING;
        int ticks = 0;
        float startYRot;
        final Set<BlockPos> chargedBlocks = new LinkedHashSet<>();
        final Set<UUID> beamHitTargets = new HashSet<>();
    }

    private static final Map<UUID, UltState> states = new HashMap<>();

    public static boolean isActive(UUID playerId) {
        return states.containsKey(playerId);
    }

    public static boolean isAiming(UUID playerId) {
        UltState st = states.get(playerId);
        return st != null && st.phase == Phase.AIMING;
    }

    // ------------------------------------------------------------------
    // Giris noktalari
    // ------------------------------------------------------------------

    public static void beginAiming(ServerPlayer player) {
        UltState st = new UltState();
        st.phase = Phase.AIMING;
        states.put(player.getUUID(), st);

        sendPhase(player, UltimateStatePacket.PHASE_AIMING);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.6f);
    }

    public static void confirm(ServerPlayer player) {
        UltState st = states.get(player.getUUID());
        if (st == null || st.phase != Phase.AIMING) return;

        st.phase = Phase.WINDUP;
        st.ticks = 0;
        st.startYRot = player.getYRot();

        sendPhase(player, UltimateStatePacket.PHASE_WINDUP);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    public static void cancel(ServerPlayer player) {
        UltState st = states.get(player.getUUID());
        if (st == null || st.phase != Phase.AIMING) return;

        states.remove(player.getUUID());
        sendPhase(player, UltimateStatePacket.PHASE_NONE);

        // Iptal edildi -> ulti bekleme suresi iade edilir
        Ability ult = AbilityManager.getAbility(player.getUUID(), AbilitySlot.ULTIMATE);
        if (ult != null) {
            ult.setCooldownTicksRemaining(0);
            ult.setState(AbilityState.IDLE);
        }

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    // ------------------------------------------------------------------
    // Tick dongusu
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (states.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, UltState>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UltState> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }

            UltState st = entry.getValue();
            st.ticks++;

            boolean done = switch (st.phase) {
                case AIMING -> { tickAiming(player); yield false; }
                case WINDUP -> tickWindup(player, st);
                case SWEEP -> tickSweep(player, st);
                case CHARGE -> tickCharge(player, st);
                case EXPLODE -> { doExplode(player, st); yield true; }
            };

            if (done) {
                sendPhase(player, UltimateStatePacket.PHASE_NONE);
                it.remove();
            }
        }
    }

    /** Nisan modu: gozlerden kizil yildirim sacilir. */
    private static void tickAiming(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition(1.0f);

        if (player.tickCount % 2 == 0) {
            spawnEyeLightning(player, level, eye, 2, 0.55);
        }

        // Yerde nisan konisi — LAZER DEGIL, kendi yer efekti sistemi.
        // Oklar hedefe dogru akar, koni acikligi mesafeyi gosterir.
        if (player.tickCount % 3 == 0) {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() > 1.0E-4) {
                flat = flat.normalize();
                sendGroundFx(player, List.of(new GroundFxPacket.Entry(
                        GroundFxPacket.KIND_CONE,
                        player.position(), flat, 12.0f, 1.0f, 5)));
            }
        }
    }

    /**
     * Isaretli her blogun UST YUZU icin bir kaplama efekti uretir.
     * Boyut tam 1 blok; yan yana bloklar kesintisiz tek siyah alan olusturur.
     *
     * @param charge 0 = catlaksiz duz siyah, 1 = patlamaya hazir catlaklar
     */
    private static List<GroundFxPacket.Entry> scorchEntries(UltState st, float charge, int ticks) {
        List<GroundFxPacket.Entry> out = new ArrayList<>(st.chargedBlocks.size());
        for (BlockPos pos : st.chargedBlocks) {
            out.add(new GroundFxPacket.Entry(
                    GroundFxPacket.KIND_SCORCH,
                    new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5),
                    new Vec3(0, 1, 0),
                    1.0f, charge, ticks));
        }
        return out;
    }

    private static void sendGroundFx(ServerPlayer player, List<GroundFxPacket.Entry> entries) {
        if (entries.isEmpty()) return;
        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new GroundFxPacket(entries));
    }

    private static boolean tickWindup(ServerPlayer player, UltState st) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition(1.0f);

        // Kafayi asagi egdir (poz + lazerin baslangic acisi)
        float t = Math.min(1f, st.ticks / (float) WINDUP_TICKS);
        float pitch = lerp(player.getXRot(), SWEEP_START_PITCH, 0.45f);
        player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                st.startYRot, pitch);

        spawnEyeLightning(player, level, eye, 3, 0.8 + t * 0.6);

        if (st.ticks == 1) {
            level.playSound(null, player.blockPosition(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4f, 0.4f);
        }

        if (st.ticks >= WINDUP_TICKS) {
            st.phase = Phase.SWEEP;
            st.ticks = 0;
            sendPhase(player, UltimateStatePacket.PHASE_SWEEP);
            level.playSound(null, player.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.6f, 1.3f);
        }
        return false;
    }

    /** Devasa lazer asagidan yukariya hat ceker. */
    private static boolean tickSweep(ServerPlayer player, UltState st) {
        ServerLevel level = (ServerLevel) player.level();

        float t = Math.min(1f, st.ticks / (float) SWEEP_TICKS);
        float pitch = SWEEP_START_PITCH + (SWEEP_END_PITCH - SWEEP_START_PITCH) * t;

        player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                st.startYRot, pitch);

        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 dir = anglesToVector(st.startYRot, pitch);
        Vec3 origin = eye.add(dir.scale(0.4));

        RaycastResult result = RaycastSystem.cast(
                level, player, origin, dir, BEAM_RANGE, BEAM_WIDTH * 0.5f, true,
                e -> e instanceof LivingEntity && e != player);

        Vec3 hit = result.getHitPosition();

        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                BeamSyncPacket.flash(origin, hit, BEAM_WIDTH, 6));

        // Sweep sadece 10 tick surer; partikulleri her tick gondermek
        // FPS'i dusuruyordu. Seyreltilmis olarak gonderiliyor.
        if (st.ticks % 2 == 0) {
            CyclopsBeamRenderer.renderOriginFlash(level, origin);
            CyclopsBeamRenderer.renderImpact(level, hit);
        }
        if (st.ticks % 3 == 0) {
            CyclopsBeamRenderer.renderBeamBubbles(level, origin, hit, 1.4f);
        }

        // Hedeflere can yuzdesiyle hasar (bir ulti icinde hedef basina bir kez)
        for (RaycastResult.EntityHit eh : result.getEntityHits()) {
            if (eh.getEntity() instanceof LivingEntity target
                    && st.beamHitTargets.add(target.getUUID())) {
                float dmg = target.getMaxHealth() * BEAM_HEALTH_PERCENT;
                target.hurt(player.damageSources().playerAttack(player), dmg);
                target.setDeltaMovement(dir.x * 1.2, 0.6, dir.z * 1.2);
                target.hurtMarked = true;
            }
        }

        markChargedBlocks(level, st, origin, hit);

        // Lazerin degdigi bloklarin ustu aninda siyah kaplanir; catlaklar
        // henuz yok (charge = 0), sarj fazinda buyuyecekler.
        if (st.ticks % 2 == 0) {
            sendGroundFx(player, scorchEntries(st, 0f, 4));
        }

        if (st.ticks >= SWEEP_TICKS) {
            st.phase = Phase.CHARGE;
            st.ticks = 0;
            sendPhase(player, UltimateStatePacket.PHASE_NONE);

            // Lazer kesildi: kafayi duz one bakis pozisyonuna geri getir
            player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                    st.startYRot, 0f);
        }
        return false;
    }

    /** Siyah kaplamanin uzerindeki catlaklar buyur, sonra patlar. */
    private static boolean tickCharge(ServerPlayer player, UltState st) {
        ServerLevel level = (ServerLevel) player.level();

        float progress = st.ticks / (float) CHARGE_TICKS;
        int every = progress > 0.6f ? 2 : 4;

        // Kaplama duruyor, sadece catlak seviyesi (charge) yukseliyor.
        if (st.ticks % every == 0) {
            sendGroundFx(player, scorchEntries(st, progress, every + 2));

            // Catlaklardan sizan cok minik kor — yanlara tasmaz, yukari cikar
            if (progress > 0.45f) {
                for (BlockPos pos : st.chargedBlocks) {
                    if (level.random.nextFloat() > 0.25f) continue;
                    level.sendParticles(CHARGE_CRACK,
                            pos.getX() + 0.5, pos.getY() + 1.08, pos.getZ() + 0.5,
                            1, 0.16, 0.02, 0.16, 0.005);
                }
            }
        }

        if (st.ticks % 8 == 0) {
            level.playSound(null, player.blockPosition(),
                    SoundEvents.LAVA_POP, SoundSource.PLAYERS, 1.0f, 0.5f + progress);
        }

        if (st.ticks >= CHARGE_TICKS) {
            st.phase = Phase.EXPLODE;
            st.ticks = 0;
        }
        return false;
    }

    /** Patlama: bloklar havaya firlar, cevredekiler can yuzdesiyle hasar alir. */
    private static void doExplode(ServerPlayer player, UltState st) {
        ServerLevel level = (ServerLevel) player.level();
        if (st.chargedBlocks.isEmpty()) return;

        Vec3 center = Vec3.ZERO;
        for (BlockPos pos : st.chargedBlocks) {
            center = center.add(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        center = center.scale(1.0 / st.chargedBlocks.size());

        level.playSound(null, BlockPos.containing(center),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.55f);

        // Patlama + duman bulutu
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y + 0.5, center.z, 2, 1.5, 0.5, 1.5, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                center.x, center.y + 0.6, center.z, 60, 2.6, 1.0, 2.6, 0.06);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                center.x, center.y + 0.4, center.z, 25, 2.2, 0.4, 2.2, 0.03);
        level.sendParticles(ParticleTypes.ASH,
                center.x, center.y + 1.0, center.z, 40, 3.0, 1.2, 3.0, 0.02);

        // Patlama isaretli bloklarla sinirli kalmaz; cevrelerindeki bloklari
        // da sokup ucurur.
        Set<BlockPos> blast = new LinkedHashSet<>(st.chargedBlocks);
        for (BlockPos pos : st.chargedBlocks) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos n = pos.offset(dx, 0, dz);
                    if (!level.getBlockState(n).isAir()) blast.add(n);
                }
            }
        }

        int launched = 0;
        for (BlockPos pos : blast) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0) continue;

            if (st.chargedBlocks.contains(pos)) {
                level.sendParticles(ParticleTypes.EXPLOSION,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        1, 0.2, 0.2, 0.2, 0);
                level.sendParticles(CHARGE_CRACK,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        6, 0.4, 0.3, 0.4, 0.05);
            }

            if (launched >= MAX_LAUNCHED_BLOCKS) continue;

            // Merkezden disa dogru savrulup havalanir
            Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vec3 outward = blockCenter.subtract(center);
            Vec3 flat = new Vec3(outward.x, 0, outward.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();

            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, state);
            fb.setDeltaMovement(
                    flat.x * (0.25 + level.random.nextDouble() * 0.35),
                    0.70 + level.random.nextDouble() * 0.55,
                    flat.z * (0.25 + level.random.nextDouble() * 0.35));
            fb.setHurtsEntities(1.0f, 3);
            fb.time = 1;
            launched++;
        }

        // Patlama hasari: maks canin yuzdesi
        AABB blastBox = new AABB(center, center).inflate(BLAST_RADIUS);
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, blastBox,
                e -> e != player && e.isAlive());

        for (LivingEntity target : caught) {
            double dist = target.position().distanceTo(center);
            double falloff = Math.max(0.35, 1.0 - dist / BLAST_RADIUS);
            float dmg = (float) (target.getMaxHealth() * BLAST_HEALTH_PERCENT * falloff);

            target.hurt(player.damageSources().playerAttack(player), dmg);

            Vec3 push = target.position().subtract(center).normalize();
            target.setDeltaMovement(push.x * 1.1, 0.85, push.z * 1.1);
            target.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    /** Lazerin gectigi hat boyunca zemindeki bloklari isinmis olarak isaretler. */
    /** Performans siniri: bu sayidan fazla blok isaretlenmez. */
    private static final int MAX_CHARGED_BLOCKS = 70;
    /** Patlamada azami kac blok fiziksel olarak havaya ucar. */
    private static final int MAX_LAUNCHED_BLOCKS = 90;

    private static void markChargedBlocks(ServerLevel level, UltState st, Vec3 from, Vec3 to) {
        if (st.chargedBlocks.size() >= MAX_CHARGED_BLOCKS) return;

        double length = from.distanceTo(to);
        if (length < 0.5) return;

        Vec3 dir = to.subtract(from).normalize();
        // Adim araligi buyutuldu (0.75 -> 1.5) ve cevre taramasi kaldirildi:
        // eskiden adim basina 9 blok ekleniyordu, yuzlerce bloga cikiyordu.
        int steps = (int) Math.min(24, length / 1.5);

        for (int i = 0; i <= steps; i++) {
            if (st.chargedBlocks.size() >= MAX_CHARGED_BLOCKS) return;

            Vec3 p = from.add(dir.scale(i * 1.5));
            BlockPos ground = findGroundBelow(level, BlockPos.containing(p));
            if (ground == null) continue;

            st.chargedBlocks.add(ground);

            // Sadece capraz iki komsu — dolgun gorunsun ama patlamasin
            BlockPos a = ground.offset(1, 0, 0);
            BlockPos b = ground.offset(0, 0, 1);
            if (!level.getBlockState(a).isAir()) st.chargedBlocks.add(a);
            if (!level.getBlockState(b).isAir()) st.chargedBlocks.add(b);
        }
    }

    private static BlockPos findGroundBelow(ServerLevel level, BlockPos start) {
        BlockPos p = start;
        for (int i = 0; i < 6; i++) {
            if (!level.getBlockState(p).isAir()) return p;
            p = p.below();
        }
        return null;
    }

    /**
     * Kafadan sacilan minik koyu kirmizi yildirimlar. Redstone partikulu yerine
     * kirik cizgi seklinde gercek geometri cizilir — cok daha yildirim gibi durur.
     */
    private static void spawnEyeLightning(ServerPlayer player, ServerLevel level,
                                          Vec3 eye, int bolts, double reach) {
        // Yildirimlar gozun ONUNDEN cikmali. Onceki surum tam kure uzerinde
        // rastgele yon seciyordu, bu yuzden kafanin arkasindan da cikiyordu.
        Vec3 look = player.getLookAngle();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = look.cross(up);
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 upPerp = right.cross(look).normalize();

        Vec3 origin = eye.add(look.scale(0.25));

        for (int i = 0; i < bolts; i++) {
            // On yarim kureye sinirli koni: yatayda +-70, dikeyde +-45 derece
            double yawOff = Math.toRadians((level.random.nextDouble() - 0.5) * 140);
            double pitchOff = Math.toRadians((level.random.nextDouble() - 0.5) * 90);

            Vec3 dir = look.scale(Math.cos(yawOff) * Math.cos(pitchOff))
                    .add(right.scale(Math.sin(yawOff)))
                    .add(upPerp.scale(Math.sin(pitchOff)))
                    .normalize();

            double len = (0.35 + level.random.nextDouble() * reach);
            int joints = 3 + level.random.nextInt(2);

            List<Vec3> bolt = new ArrayList<>(joints + 1);
            bolt.add(origin);

            Vec3 p;
            for (int j = 1; j <= joints; j++) {
                double t = (double) j / joints;
                Vec3 straight = origin.add(dir.scale(len * t));
                // her eklemde rastgele kirilma
                double jitter = 0.16 * (1.0 - t * 0.4);
                p = straight.add(
                        (level.random.nextDouble() - 0.5) * jitter * 2,
                        (level.random.nextDouble() - 0.5) * jitter * 2,
                        (level.random.nextDouble() - 0.5) * jitter * 2);
                bolt.add(p);
            }

            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    BeamSyncPacket.flashPath(bolt, 0.16f, 3, false));
        }
    }

    private static void sendPhase(ServerPlayer player, byte phase) {
        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new UltimateStatePacket(phase, player.getUUID()));
    }

    private static Vec3 anglesToVector(float yRotDeg, float xRotDeg) {
        float yaw = (float) Math.toRadians(yRotDeg);
        float pitch = (float) Math.toRadians(xRotDeg);
        double xz = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * xz, -Math.sin(pitch), Math.cos(yaw) * xz).normalize();
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
