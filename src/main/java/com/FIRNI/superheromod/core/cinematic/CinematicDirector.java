package com.FIRNI.superheromod.core.cinematic;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.cyclops.CyclopsBeamRenderer;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BeamSyncPacket;
import com.FIRNI.superheromod.network.packet.CinematicSyncPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * SINEMATIK YONETMENI — sunucu tarafi.
 *
 * Sorumluluklari:
 *   - sahne uzayini kurmak (StageFrame)
 *   - zaman cizgisini yurutmek, beat'leri tam tick'inde islemek
 *   - aktorleri kilitlemek ve koreografiye gore surmek
 *   - iki katilimciyi ayni tick ile senkronlamak
 *   - her durumda temiz sonlandirmak (failsafe)
 *
 * Kamera bu sinifta YOK: kamera istemcide, cekim tanimlarindan turetiliyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class CinematicDirector {

    /** Katilimci basina calisan tek sinematik. */
    private static final class Playback {
        CinematicDefinition def;
        UUID attackerId;
        UUID targetId;
        StageFrame stage;

        int tick = 0;
        int nextBeat = 0;

        float beamWidth = 0f;
        boolean beamOn = false;
        float darkness = 0f;

        /** Suren firlatma hareketi. */
        Vec3 launchFrom, launchTo;
        float launchArc;
        int launchStart, launchDuration;

        /** Yumusak tasima hedefi. */
        Vec3 moveTo;
        float moveSpeed;

        // Geri yuklenecek durum
        float savedYRot, savedXRot;
        boolean rotationApplied = false;
    }

    private static final Map<UUID, Playback> active = new HashMap<>();

    private CinematicDirector() {}

    // ------------------------------------------------------------------
    // Genel API
    // ------------------------------------------------------------------

    /** Katilimci (saldiran veya hedef) bir sinematikte mi? */
    public static boolean isBusy(UUID playerId) {
        for (Playback p : active.values()) {
            if (playerId.equals(p.attackerId) || playerId.equals(p.targetId)) return true;
        }
        return false;
    }

    public static boolean start(String cinematicId, ServerPlayer attacker, LivingEntity target) {
        CinematicDefinition def = CinematicRegistry.get(cinematicId);
        if (def == null) return false;
        if (isBusy(attacker.getUUID()) || isBusy(target.getUUID())) return false;

        Playback p = new Playback();
        p.def = def;
        p.attackerId = attacker.getUUID();
        p.targetId = target.getUUID();
        p.stage = StageFrame.between(attacker.position(), target.position());
        p.savedYRot = attacker.getYRot();
        p.savedXRot = attacker.getXRot();

        // Hedefi sahnenin istedigi yere oturt (koreografi deterministik olsun)
        if (def.targetAnchor != null) {
            Vec3 world = p.stage.toWorldSpan(def.targetAnchor);
            target.teleportTo(world.x, target.getY(), world.z);
        }

        active.put(attacker.getUUID(), p);
        return true;
    }

    /** Sinematigi zorla bitir ve her seyi geri yukle. */
    public static void abort(UUID attackerId) {
        Playback p = active.remove(attackerId);
        if (p == null) return;
        restore(p);
    }

    // ------------------------------------------------------------------
    // Tick dongusu
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (active.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            active.clear();
            return;
        }

        Iterator<Map.Entry<UUID, Playback>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Playback p = it.next().getValue();

            ServerPlayer attacker = server.getPlayerList().getPlayer(p.attackerId);
            LivingEntity target = attacker == null ? null
                    : resolve((ServerLevel) attacker.level(), p.targetId);

            // FAILSAFE: aktorlerden biri kayboldu/oldu -> temiz cikis
            if (attacker == null || target == null || !target.isAlive()
                    || !attacker.isAlive()) {
                if (p != null) restore(p);
                it.remove();
                continue;
            }

            p.tick++;

            holdActors(attacker, target, p);
            runBeats(attacker, target, p);
            driveMotion(target, p);
            emitBeam(attacker, target, p);
            sync(attacker, target, p);

            if (p.tick >= p.def.totalTicks) {
                restore(p);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // Ic isleyis
    // ------------------------------------------------------------------

    private static void runBeats(ServerPlayer attacker, LivingEntity target, Playback p) {
        List<Beat> beats = p.def.beats;
        while (p.nextBeat < beats.size() && beats.get(p.nextBeat).tick <= p.tick) {
            execute(beats.get(p.nextBeat), attacker, target, p);
            p.nextBeat++;
        }
    }

    private static void execute(Beat beat, ServerPlayer attacker,
                                LivingEntity target, Playback p) {
        ServerLevel level = (ServerLevel) attacker.level();

        switch (beat.action) {
            case BEAM -> {
                p.beamOn = true;
                p.beamWidth = beat.param1;
            }
            case BEAM_OFF -> {
                p.beamOn = false;
                ModNetworking.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> attacker),
                        BeamSyncPacket.stop(attacker.getUUID()));
            }
            case SOUND -> playSound(level, attacker, beat.text, beat.param1, beat.param2);
            case ACTOR_MOVE_TARGET -> {
                p.moveTo = p.stage.toWorldSpan(beat.localA);
                p.moveSpeed = beat.param1;
            }
            case LAUNCH_TARGET -> {
                p.launchFrom = p.stage.toWorldSpan(beat.localA);
                p.launchTo = p.stage.toWorldSpan(beat.localB);
                p.launchArc = beat.param1;
                p.launchStart = p.tick;
                p.launchDuration = Math.max(1, (int) beat.param2);
                p.moveTo = null;
            }
            case FREEZE -> {
                p.moveTo = null;
                p.launchFrom = null;
            }
            case DAMAGE -> {
                float dmg = target.getMaxHealth() * beat.param1;
                target.invulnerableTime = 0;
                target.hurt(attacker.damageSources().playerAttack(attacker), dmg);
            }
            case DARKNESS -> p.darkness = beat.param1;
            case TIME_SCALE -> { /* istemci tarafinda gorsel; sync ile gidiyor */ }
            case LOOK_PITCH -> {
                attacker.connection.teleport(attacker.getX(), attacker.getY(), attacker.getZ(),
                        attacker.getYRot(), beat.param1);
            }
            case FX -> spawnFx(level, p, beat);
        }
    }

    private static void spawnFx(ServerLevel level, Playback p, Beat beat) {
        Vec3 at = beat.localA == null ? p.stage.origin() : p.stage.toWorldSpan(beat.localA);
        float size = Math.max(0.1f, beat.param1);

        switch (beat.text == null ? "" : beat.text) {
            case "dust" -> level.sendParticles(ParticleTypes.CLOUD,
                    at.x, at.y, at.z, (int) (12 * size), 0.35 * size, 0.06, 0.35 * size, 0.02);
            case "smoke" -> level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    at.x, at.y, at.z, (int) (30 * size), 1.4 * size, 0.6, 1.4 * size, 0.05);
            case "explode" -> {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 3, 0.4, 0.4, 0.4, 0);
            }
            case "impact" -> CyclopsBeamRenderer.renderImpact(level, at);
            case "charge" -> level.sendParticles(ParticleTypes.LAVA,
                    at.x, at.y, at.z, (int) (3 * size), 0.15, 0.15, 0.15, 0);
            default -> { }
        }
    }

    /** Isini her tick tazele — aktorler hareket ettikce uclari guncellenir. */
    private static void emitBeam(ServerPlayer attacker, LivingEntity target, Playback p) {
        if (!p.beamOn) return;

        Vec3 eye = attacker.getEyePosition(1.0f);
        Vec3 look = attacker.getLookAngle();
        Vec3 from = eye.add(look.scale(0.4));
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);

        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> attacker),
                BeamSyncPacket.channel(attacker.getUUID(), List.of(from, to), p.beamWidth));

        if (p.tick % 3 == 0) {
            ServerLevel level = (ServerLevel) attacker.level();
            CyclopsBeamRenderer.renderOriginFlash(level, from);
            CyclopsBeamRenderer.renderBeamBubbles(level, from, to, p.beamWidth);
        }
    }

    /** Firlatma ve yumusak tasima — fizik yerine koreografi kontrolunde. */
    private static void driveMotion(LivingEntity target, Playback p) {
        if (p.launchFrom != null) {
            float prog = Math.min(1f, (p.tick - p.launchStart) / (float) p.launchDuration);
            Vec3 base = p.launchFrom.add(p.launchTo.subtract(p.launchFrom).scale(prog));
            double arc = Math.sin(prog * Math.PI) * p.launchArc;

            target.setPos(base.x, base.y + arc, base.z);
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
            target.fallDistance = 0;

            if (prog >= 1f) p.launchFrom = null;
            return;
        }

        if (p.moveTo != null) {
            Vec3 cur = target.position();
            Vec3 delta = p.moveTo.subtract(cur);
            if (delta.lengthSqr() < 0.01) {
                p.moveTo = null;
                return;
            }
            Vec3 step = delta.normalize().scale(Math.min(p.moveSpeed, delta.length()));
            target.setPos(cur.x + step.x, cur.y, cur.z + step.z);
            target.hurtMarked = true;
        }
    }

    /** Aktorler sinematik boyunca normal hareket edemez. */
    private static void holdActors(ServerPlayer attacker, LivingEntity target, Playback p) {
        attacker.setDeltaMovement(0, Math.min(0, attacker.getDeltaMovement().y), 0);
        attacker.fallDistance = 0;

        if (!p.rotationApplied) {
            p.rotationApplied = true;
            float yaw = p.stage.yawOf(new Vec3(0, 0, 1));
            attacker.connection.teleport(attacker.getX(), attacker.getY(), attacker.getZ(), yaw, 0f);
        }

        // Hedef koreografi disinda hareket etmesin
        if (p.launchFrom == null && p.moveTo == null) {
            target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 10, false, false));
    }

    /** Iki katilimciya AYNI tick gonderilir — ikisi ayni ani gorur. */
    private static void sync(ServerPlayer attacker, LivingEntity target, Playback p) {
        CinematicSyncPacket packet = new CinematicSyncPacket(
                true,
                CinematicRegistry.indexOf(p.def.id),
                p.tick,
                attacker.getId(),
                target.getId(),
                p.stage.origin(),
                p.stage.forward(),
                p.darkness,
                (float) p.stage.span());

        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> attacker), packet);
        if (target instanceof ServerPlayer tp) {
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> tp), packet);
        }
    }

    /** Kamera, hareket, isin ve donus geri verilir. */
    private static void restore(Playback p) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerPlayer attacker = server.getPlayerList().getPlayer(p.attackerId);
        CinematicSyncPacket off = CinematicSyncPacket.inactive();

        if (attacker != null) {
            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> attacker),
                    BeamSyncPacket.stop(attacker.getUUID()));
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> attacker), off);

            attacker.connection.teleport(attacker.getX(), attacker.getY(), attacker.getZ(),
                    p.savedYRot, p.savedXRot);
        }

        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(p.targetId);
        if (targetPlayer != null) {
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> targetPlayer), off);
        }
    }

    private static void playSound(ServerLevel level, ServerPlayer at,
                                  String id, float volume, float pitch) {
        if (id == null) return;
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(id));
        if (sound == null) return;
        level.playSound(null, at.blockPosition(), sound, SoundSource.PLAYERS,
                volume <= 0 ? 1f : volume, pitch <= 0 ? 1f : pitch);
    }

    private static LivingEntity resolve(ServerLevel level, UUID id) {
        var e = level.getEntity(id);
        return e instanceof LivingEntity le ? le : null;
    }
}
