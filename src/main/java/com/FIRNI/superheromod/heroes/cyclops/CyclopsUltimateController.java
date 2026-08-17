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
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
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

    /**
     * Ulti hedefe maks caninin YARISI kadar vurur.
     *
     * Ayni deger hem dogrudan lazer isabetinde hem patlamada kullaniliyor;
     * lazere yakalanan biri ayrica patlama hasari ALMAZ (asagida elenir),
     * boylece ultinin toplam hasari her durumda yari can olarak kalir.
     */
    private static final float ULT_HEALTH_PERCENT = 0.50f;
    private static final double BLAST_RADIUS = 9.0;

    /** Isaretli bloklarin cevresinde kac blok yarikap sokulur. */
    private static final int BLAST_SPREAD = 2;
    /** Kac katman asagi kazilir (krater derinligi). */
    private static final int BLAST_DEPTH = 2;

    /** Patlama sonrasi havada asili kalan toz/duman suresi (7 saniye). */
    private static final int SMOLDER_TICKS = 140;
    /** Duman kaynagi olarak kullanilan azami krater noktasi. */
    private static final int SMOLDER_SOURCES = 20;

    /** Sadece patlama aninda kullaniliyor; sarj/yildirim efektleri geometri. */
    private static final DustParticleOptions CHARGE_CRACK =
            new DustParticleOptions(new Vector3f(1.0f, 0.12f, 0.05f), 1.4f);

    /**
     * Catlak agindaki tek bir dal. Bloklardan bagimsizdir; tum kaplama alani
     * icin bir kez kurulur.
     *
     * @param threshold bu dalin gorunmeye basladigi sarj seviyesi (0..1)
     */
    private record Crack(Vec3 from, Vec3 dir, float length, float threshold) {}

    private static final class UltState {
        Phase phase = Phase.AIMING;
        int ticks = 0;
        float startYRot;
        final Set<BlockPos> chargedBlocks = new LinkedHashSet<>();
        /** Isin hatti uzerindeki ana bloklar — catlak agi bunlari izler. */
        final List<BlockPos> spine = new ArrayList<>();
        /** CHARGE basinda bir kez kurulur, sonra sadece sarj seviyesi degisir. */
        final List<Crack> cracks = new ArrayList<>();
        final Set<UUID> beamHitTargets = new HashSet<>();
    }

    private static final Map<UUID, UltState> states = new HashMap<>();

    /**
     * Patlama sonrasi kraterden tuten toz bulutu.
     *
     * Oyuncu durumundan AYRI tutuluyor: ulti bitmis sayilir (bekleme suresi
     * isler, tekrar kullanilabilir), duman kendi basina sonene kadar devam eder.
     */
    private static final class Smolder {
        final ServerLevel level;
        final List<Vec3> sources;
        final Vec3 center;
        /** Krater suya degdiyse duman siyah degil BEYAZ buhar olur. */
        final boolean steam;
        int ticks = 0;

        Smolder(ServerLevel level, List<Vec3> sources, Vec3 center, boolean steam) {
            this.level = level;
            this.sources = sources;
            this.center = center;
            this.steam = steam;
        }
    }

    private static final List<Smolder> smolders = new ArrayList<>();

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

        tickSmolders();

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
     * Catlaklar buraya DAHIL DEGIL — onlar alanin tamaminda tek bir agdir.
     */
    private static List<GroundFxPacket.Entry> scorchEntries(UltState st, int ticks) {
        List<GroundFxPacket.Entry> out = new ArrayList<>(st.chargedBlocks.size());
        for (BlockPos pos : st.chargedBlocks) {
            out.add(new GroundFxPacket.Entry(
                    GroundFxPacket.KIND_SCORCH,
                    new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5),
                    new Vec3(0, 1, 0),
                    1.0f, 0f, ticks));
        }
        return out;
    }

    /** Sarj seviyesine gore gorunur hale gelmis catlak dallari. */
    private static List<GroundFxPacket.Entry> crackEntries(UltState st, float charge, int ticks) {
        List<GroundFxPacket.Entry> out = new ArrayList<>();
        for (Crack c : st.cracks) {
            if (charge < c.threshold()) continue;
            out.add(new GroundFxPacket.Entry(
                    GroundFxPacket.KIND_CRACK,
                    c.from(), c.dir(), c.length(), charge, ticks));
        }
        return out;
    }

    /**
     * Tum kaplama alanini kaplayan TEK bir catlak agi kurar:
     * isin hatti boyunca ana bir govde, uzerinden ayrilan yan dallar ve
     * onlardan cikan kisa alt dallar. Blok basina ayri desen yok.
     */
    private static void buildCracks(ServerLevel level, UltState st) {
        st.cracks.clear();
        if (st.spine.size() < 2) return;

        List<Vec3> points = new ArrayList<>(st.spine.size());
        for (BlockPos p : st.spine) {
            points.add(new Vec3(p.getX() + 0.5, p.getY() + 1.02, p.getZ() + 0.5));
        }

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 from = points.get(i);
            Vec3 to = points.get(i + 1);
            Vec3 delta = to.subtract(from);
            double len = delta.length();
            if (len < 0.1) continue;

            Vec3 dir = delta.normalize();
            // Ana govde en bastan gorunur
            st.cracks.add(new Crack(from, dir, (float) len, 0f));

            // Yan dallar — govdenin iki yanina, sarj ilerledikce acilir
            Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
            int branches = 1 + level.random.nextInt(2);

            for (int b = 0; b < branches; b++) {
                double lean = (level.random.nextDouble() - 0.5) * 0.9;
                double sign = level.random.nextBoolean() ? 1 : -1;

                Vec3 branchDir = side.scale(sign).add(dir.scale(lean)).normalize();
                float branchLen = 0.8f + level.random.nextFloat() * 1.4f;
                float threshold = 0.20f + level.random.nextFloat() * 0.45f;

                Vec3 branchFrom = from.add(dir.scale(len * level.random.nextDouble()));
                st.cracks.add(new Crack(branchFrom, branchDir, branchLen, threshold));

                // Alt dal — agin ucu daha da yayilsin
                if (level.random.nextFloat() < 0.55f) {
                    Vec3 tip = branchFrom.add(branchDir.scale(branchLen * 0.7));
                    Vec3 subDir = branchDir.add(side.scale(sign * -0.8)).normalize();
                    st.cracks.add(new Crack(tip, subDir,
                            0.5f + level.random.nextFloat() * 0.7f,
                            threshold + 0.15f));
                }
            }
        }
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
            // Ultinin isini en kalini — partikuller TAM goz hizasindan
            CyclopsBeamRenderer.renderEyeMuzzle(level, eye, dir, 4.0f);
            CyclopsBeamRenderer.renderImpact(level, hit);
        }
        if (st.ticks % 3 == 0) {
            CyclopsBeamRenderer.renderBeamBubbles(level, origin, hit, 1.4f);
        }

        // Hedeflere can yuzdesiyle hasar (bir ulti icinde hedef basina bir kez)
        for (RaycastResult.EntityHit eh : result.getEntityHits()) {
            if (eh.getEntity() instanceof LivingEntity target
                    && st.beamHitTargets.add(target.getUUID())) {
                float dmg = target.getMaxHealth() * ULT_HEALTH_PERCENT;
                target.hurt(player.damageSources().playerAttack(player), dmg);
                target.setDeltaMovement(dir.x * 1.2, 0.6, dir.z * 1.2);
                target.hurtMarked = true;
            }
        }

        markChargedBlocks(level, st, origin, hit);

        // Lazerin degdigi bloklarin ustu aninda siyah kaplanir; catlak agi
        // henuz kurulmadi, sarj fazinda ortaya cikacak.
        if (st.ticks % 2 == 0) {
            sendGroundFx(player, scorchEntries(st, 4));
        }

        if (st.ticks >= SWEEP_TICKS) {
            st.phase = Phase.CHARGE;
            st.ticks = 0;
            sendPhase(player, UltimateStatePacket.PHASE_NONE);

            // Catlak agi tum alan icin bir kez kurulur
            buildCracks(level, st);

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

        // Kaplama sabit; uzerindeki tek catlak agi sarjla birlikte buyur.
        if (st.ticks % every == 0) {
            List<GroundFxPacket.Entry> fx = new ArrayList<>();
            fx.addAll(scorchEntries(st, every + 2));
            fx.addAll(crackEntries(st, progress, every + 2));
            sendGroundFx(player, fx);

            // Catlaklardan sizan cok minik kor — yanlara tasmaz, yukari cikar
            if (progress > 0.45f) {
                for (BlockPos pos : st.spine) {
                    if (level.random.nextFloat() > 0.35f) continue;
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

    /**
     * Patlamadan sonra kraterin uzerinde asili kalan toz/duman bulutu.
     *
     * Partikuller hareket hizi neredeyse sifir verilerek gonderiliyor; boylece
     * hemen dagilmak yerine havada duruyor ve yavasca cokuyor. Yogunluk zamanla
     * azalir. Paket sayisi dusuk kalsin diye her seferinde sadece birkac
     * kaynaktan cok sayida partikul cikariliyor.
     */
    private static void tickSmolders() {
        if (smolders.isEmpty()) return;

        Iterator<Smolder> it = smolders.iterator();
        while (it.hasNext()) {
            Smolder sm = it.next();
            sm.ticks++;

            if (sm.ticks >= SMOLDER_TICKS || sm.sources.isEmpty()) {
                it.remove();
                continue;
            }

            // 1 -> 0 arasi sonumleme; basta yogun, sonra ince pus
            float fade = 1f - sm.ticks / (float) SMOLDER_TICKS;
            ServerLevel level = sm.level;

            if (sm.ticks % 3 == 0) {
                int picks = Math.max(1, Math.round(4 * fade));
                for (int i = 0; i < picks; i++) {
                    Vec3 src = sm.sources.get(level.random.nextInt(sm.sources.size()));

                    // Kraterden yukselen duman / buhar
                    level.sendParticles(
                            sm.steam ? ParticleTypes.CLOUD : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            src.x, src.y + 0.2, src.z,
                            2, 0.5, 0.15, 0.5, 0.005);

                    // Havada asili kalan toz — hiz ~0, yavasca cokuyor
                    level.sendParticles(
                            sm.steam ? ParticleTypes.CLOUD : ParticleTypes.ASH,
                            src.x, src.y + 1.2 + level.random.nextDouble() * 2.2, src.z,
                            3, 1.1, 0.9, 1.1, 0.0);
                }
            }

            // Bulutun govdesi: merkezde genis ve agir, yavas suruklenen kutle
            if (sm.ticks % 5 == 0) {
                int count = Math.max(2, Math.round(14 * fade));
                level.sendParticles(
                        sm.steam ? ParticleTypes.CLOUD : ParticleTypes.LARGE_SMOKE,
                        sm.center.x, sm.center.y + 1.4, sm.center.z,
                        count, 3.2, 1.3, 3.2, 0.0);
            }

            // Ilk saniyelerde yukari dogru kalkan sutun
            if (sm.ticks < 50 && sm.ticks % 8 == 0) {
                level.sendParticles(
                        sm.steam ? ParticleTypes.CLOUD : ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                        sm.center.x, sm.center.y + 0.8, sm.center.z,
                        2, 1.4, 0.2, 1.4, 0.01);
            }

            // Kor sonme sesi — gittikce seyrelir
            if (sm.ticks % 25 == 0 && fade > 0.3f) {
                level.playSound(null, BlockPos.containing(sm.center),
                        SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.5f * fade, 0.6f);
            }
        }
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

        // Ust uste iki patlama sesi — biri tok, biri gecikmeli yanki
        level.playSound(null, BlockPos.containing(center),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.6f, 0.42f);
        level.playSound(null, BlockPos.containing(center),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.8f, 0.7f);

        // Patlama + duman bulutu
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y + 0.5, center.z, 4, 2.4, 0.8, 2.4, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                center.x, center.y + 0.6, center.z, 110, 3.4, 1.4, 3.4, 0.08);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                center.x, center.y + 0.4, center.z, 45, 3.0, 0.6, 3.0, 0.04);
        level.sendParticles(ParticleTypes.ASH,
                center.x, center.y + 1.0, center.z, 70, 3.8, 1.6, 3.8, 0.03);

        // Yerde disa dogru kosan sok dalgasi halkasi
        for (int i = 0; i < 36; i++) {
            double a = i / 36.0 * Math.PI * 2;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            level.sendParticles(ParticleTypes.EXPLOSION,
                    center.x + dx * 3.0, center.y + 0.4, center.z + dz * 3.0,
                    1, dx * 0.3, 0.05, dz * 0.3, 0.12);
        }

        // Patlama isaretli bloklarla sinirli kalmaz: cevrede 2 blok yaricap,
        // ayrica bir alt katman da sokulur — krater acilsin.
        Set<BlockPos> blast = new LinkedHashSet<>(st.chargedBlocks);
        for (BlockPos pos : st.chargedBlocks) {
            for (int dx = -BLAST_SPREAD; dx <= BLAST_SPREAD; dx++) {
                for (int dz = -BLAST_SPREAD; dz <= BLAST_SPREAD; dz++) {
                    for (int dy = -BLAST_DEPTH; dy <= 0; dy++) {
                        if (dx == 0 && dz == 0 && dy == 0) continue;
                        // Kare degil daire: kosede tasma olmasin
                        if (dx * dx + dz * dz > BLAST_SPREAD * BLAST_SPREAD) continue;

                        BlockPos n = pos.offset(dx, dy, dz);
                        if (!level.getBlockState(n).isAir()) blast.add(n);
                    }
                }
            }
        }

        // Her sutunda en dibe inen y — yanmis zemin buranin ALTINA serilecek
        Map<Long, Integer> craterFloor = new HashMap<>();

        int launched = 0;
        for (BlockPos pos : blast) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0) continue;

            long column = columnKey(pos.getX(), pos.getZ());
            Integer prev = craterFloor.get(column);
            if (prev == null || pos.getY() < prev) craterFloor.put(column, pos.getY());

            if (st.chargedBlocks.contains(pos)) {
                level.sendParticles(ParticleTypes.EXPLOSION,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        1, 0.2, 0.2, 0.2, 0);
                level.sendParticles(CHARGE_CRACK,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        6, 0.4, 0.3, 0.4, 0.05);
            }

            // Entity siniri dolduysa blok yine de yok olur, sadece ucmaz
            if (launched >= MAX_LAUNCHED_BLOCKS) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                continue;
            }

            // Merkezden disa dogru savrulup havalanir
            Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vec3 outward = blockCenter.subtract(center);
            Vec3 flat = new Vec3(outward.x, 0, outward.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();

            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, state);
            fb.setDeltaMovement(
                    flat.x * (0.35 + level.random.nextDouble() * 0.50),
                    0.90 + level.random.nextDouble() * 0.70,
                    flat.z * (0.35 + level.random.nextDouble() * 0.50));
            fb.setHurtsEntities(1.5f, 4);
            fb.time = 1;
            launched++;
        }

        // Patlama hasari: maks canin yuzdesi
        AABB blastBox = new AABB(center, center).inflate(BLAST_RADIUS);
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, blastBox,
                // Lazer zaten vurduysa patlama tekrar vurmaz — ultinin toplam
                // hasari yari can olarak kalsin
                e -> e != player && e.isAlive() && !st.beamHitTargets.contains(e.getUUID()));

        for (LivingEntity target : caught) {
            double dist = target.position().distanceTo(center);
            double falloff = Math.max(0.35, 1.0 - dist / BLAST_RADIUS);
            float dmg = (float) (target.getMaxHealth() * ULT_HEALTH_PERCENT * falloff);

            target.hurt(player.damageSources().playerAttack(player), dmg);

            Vec3 push = target.position().subtract(center).normalize();
            target.setDeltaMovement(push.x * 1.35, 1.0, push.z * 1.35);
            target.hurtMarked = true;
        }

        boolean touchedWater = scorchCraterFloor(level, st, craterFloor, center);
        registerSmolder(level, st, center, touchedWater);
    }

    // ------------------------------------------------------------------
    // Yanmis zemin
    // ------------------------------------------------------------------

    /** Magma cekirdeginin bittigi yer (0 = orta cizgi, 1 = krater kenari). */
    private static final double ZONE_MAGMA = 0.30;
    /** Netherrack kusaginin bittigi yer; disi obsidyen. */
    private static final double ZONE_NETHER = 0.68;

    /**
     * Kraterin en dibine, yok olan bloklarin hemen altina yanmis zemin serer.
     *
     * Desen MERKEZE UZAKLIGA gore kuruluyor: ortada magma damari, cevresinde
     * netherrack, kenarda obsidyen — icten disa sogumus gibi.
     *
     * Duz cizgi olmamasi icin iki sey var: orta cizgi krater boyunca sinusle
     * kivriliyor (damar yilankavi ilerliyor) ve her blogun bolge sinirina
     * gurultu ekleniyor (kusak kenarlari testere gibi degil, dagilarak
     * geciyor). Ayrica her kusakta komsu blok turunden serpistirme var,
     * boylece gecisler keskin durmuyor.
     *
     * @return krater suya degdi mi (duman beyaz buhara donecek mi)
     */
    private static boolean scorchCraterFloor(ServerLevel level, UltState st,
                                             Map<Long, Integer> craterFloor, Vec3 center) {
        if (craterFloor.isEmpty()) return false;

        // Kraterin ana ekseni — magma damari bu yonde uzanir
        Vec3 axis = new Vec3(1, 0, 0);
        if (st.spine.size() >= 2) {
            BlockPos a = st.spine.get(0);
            BlockPos b = st.spine.get(st.spine.size() - 1);
            Vec3 d = new Vec3(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
            if (d.lengthSqr() > 1.0E-4) axis = d.normalize();
        }
        Vec3 side = new Vec3(-axis.z, 0, axis.x);

        // Kraterin gercek yari genisligi — bolgeler buna gore olceklenir,
        // boylece dar da genis de olsa oran ayni kalir
        double halfWidth = 1.0;
        for (Long key : craterFloor.keySet()) {
            double dx = columnX(key) + 0.5 - center.x;
            double dz = columnZ(key) + 0.5 - center.z;
            halfWidth = Math.max(halfWidth, Math.abs(dx * side.x + dz * side.z));
        }

        boolean touchedWater = false;

        for (Map.Entry<Long, Integer> entry : craterFloor.entrySet()) {
            int x = columnX(entry.getKey());
            int z = columnZ(entry.getKey());
            BlockPos floor = new BlockPos(x, entry.getValue() - 1, z);

            BlockState existing = level.getBlockState(floor);
            // Kirilmaz zemin (bedrock) korunur
            if (existing.getDestroySpeed(level, floor) < 0) continue;

            if (nearWater(level, floor)) {
                touchedWater = true;
                // Kizgin zemin suya degdi — beyaz buhar
                level.sendParticles(ParticleTypes.CLOUD,
                        x + 0.5, floor.getY() + 1.1, z + 0.5,
                        5, 0.35, 0.25, 0.35, 0.02);

                // Suyun icine yanmis blok koymuyoruz, sadece buhar cikiyor
                if (existing.getFluidState().is(FluidTags.WATER)) continue;
            }

            if (existing.isAir()) continue;

            double dx = x + 0.5 - center.x;
            double dz = z + 0.5 - center.z;
            double along = dx * axis.x + dz * axis.z;
            double lateral = dx * side.x + dz * side.z;

            // Orta cizgi krater boyunca kivriliyor — damar duz gitmesin
            double drift = Math.sin(along * 0.33) * halfWidth * 0.28
                         + Math.sin(along * 0.12 + 1.3) * halfWidth * 0.17;

            // 0 = damarin tam ortasi, 1 = kraterin kenari
            double t = Math.abs(lateral - drift) / halfWidth;
            // Kusak sinirlarini dagit ki kenarlar duz cizgi olmasin
            t += (level.random.nextDouble() - 0.5) * 0.30;

            level.setBlock(floor, zoneBlock(t, level), 2);
        }

        if (touchedWater) {
            level.playSound(null, BlockPos.containing(center),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.6f, 0.8f);
        }
        return touchedWater;
    }

    /**
     * Merkeze uzakliga gore blok secer. Her kusakta komsu turden serpistirme
     * var; boylece gecis cizgisi degil, karisim olarak gorunuyor.
     *
     * @param t 0 = damarin ortasi, 1 = kraterin kenari
     */
    private static BlockState zoneBlock(double t, ServerLevel level) {
        float roll = level.random.nextFloat();

        if (t < ZONE_MAGMA) {
            // Cekirdek: agirlikli magma, arasinda netherrack damarlari
            return roll < 0.72f
                    ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : Blocks.NETHERRACK.defaultBlockState();
        }

        if (t < ZONE_NETHER) {
            // Orta kusak: netherrack, seyrek magma lekesi ve sogumus parcalar
            if (roll < 0.14f) return Blocks.MAGMA_BLOCK.defaultBlockState();
            if (roll > 0.88f) return Blocks.OBSIDIAN.defaultBlockState();
            return Blocks.NETHERRACK.defaultBlockState();
        }

        // Kenar: sogumus obsidyen, arasinda netherrack
        return roll < 0.72f
                ? Blocks.OBSIDIAN.defaultBlockState()
                : Blocks.NETHERRACK.defaultBlockState();
    }

    private static boolean nearWater(ServerLevel level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) return true;
        for (Direction dir : Direction.values()) {
            if (level.getFluidState(pos.relative(dir)).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int columnX(long key) {
        return (int) (key >> 32);
    }

    private static int columnZ(long key) {
        return (int) key;
    }

    /**
     * Patlama sonrasi toz bulutunu baslatir. Krater noktalari seyreltilerek
     * kaynak listesine alinir — hepsinden partikul cikarmak paket yagmuru
     * yaratiyordu.
     */
    private static void registerSmolder(ServerLevel level, UltState st, Vec3 center,
                                        boolean steam) {
        List<BlockPos> all = new ArrayList<>(st.chargedBlocks);
        if (all.isEmpty()) return;

        int step = Math.max(1, all.size() / SMOLDER_SOURCES);
        List<Vec3> sources = new ArrayList<>();
        for (int i = 0; i < all.size() && sources.size() < SMOLDER_SOURCES; i += step) {
            BlockPos p = all.get(i);
            sources.add(new Vec3(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5));
        }

        smolders.add(new Smolder(level, sources, center, steam));
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    /** Lazerin gectigi hat boyunca zemindeki bloklari isinmis olarak isaretler. */
    /** Performans siniri: bu sayidan fazla blok isaretlenmez. */
    private static final int MAX_CHARGED_BLOCKS = 70;
    /** Patlamada azami kac blok fiziksel olarak havaya ucar. */
    private static final int MAX_LAUNCHED_BLOCKS = 160;

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

            // Ana hat: catlak agi bu noktalari izler
            if (st.chargedBlocks.add(ground)) st.spine.add(ground);

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
