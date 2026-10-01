package com.FIRNI.superheromod.client.render.cinematic;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.cinematic.CinematicDefinition;
import com.FIRNI.superheromod.core.cinematic.CinematicRegistry;
import com.FIRNI.superheromod.core.cinematic.CinematicCameraSampler;
import com.FIRNI.superheromod.client.render.puppet.CinematicPuppet;
import com.FIRNI.superheromod.client.render.puppet.PuppetRenderer;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import com.FIRNI.superheromod.core.cinematic.StageFrame;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;


/**
 * SINEMATIK ISTEMCISI — kamerayi cekim tanimlarindan surer.
 *
 * Sunucu sadece "hangi sinematik + kacinci tick + sahne nerede" gonderir;
 * kamera konumu, FOV, sarsinti ve gecisler burada hesaplanir. Boylece kamera
 * kare hizinda akici olur, 20Hz tick'e takilmaz.
 *
 * Sadece katilimcilarin kamerasi ele alinir; digerleri normal oynar.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class CinematicClient {

    private static boolean active = false;
    private static CinematicDefinition def;
    private static int attackerId = -1;
    private static int targetId = -1;
    private static Vec3 stageOrigin = Vec3.ZERO;
    private static Vec3 stageForward = new Vec3(0, 0, 1);
    private static float darkness = 0f;
    /** Saldiran-hedef mesafesi. Agdan gelir; iki noktadan yeniden
     *  hesaplamak yanlis olcek verip kadraji bozuyordu. */
    private static float span = 1.5f;

    private static long lastPacketMs = 0;
    private static long clockNanos;
    private static float clockTick, clockRate=1;
    private static float playbackRate = 1;
    private static int transportRevision;
    private static java.util.UUID sessionId;
    private static float fov = 70f;
    private static float roll = 0f;

    // Current timeline sample, consumed by Forge fog callbacks.
    private static float fogNear = -1f;
    private static float fogFar = -1f;
    private static float fogR = -1f, fogG = -1f, fogB = -1f;
    private static boolean fogActive = false;



    private static Field cameraPosField;
    private static boolean fieldResolved = false;

    private CinematicClient() {}

    // ------------------------------------------------------------------
    // Ag girisi
    // ------------------------------------------------------------------

    public static void update(int cinematicIndex, float tick, int attacker, int target,
                              Vec3 origin, Vec3 forward, float dark, float spanIn,
                              float rate, int revision, java.util.UUID session) {
        CinematicDefinition d = CinematicRegistry.byIndex(cinematicIndex);
        if (d == null) return;

        boolean fresh = !active || d != def || !session.equals(sessionId);
        boolean discontinuity = fresh || revision != transportRevision;
        long clockNow=System.nanoTime();
        float predicted=clockTick+(clockNow-clockNanos)/50_000_000f*clockRate;
        float error=tick-predicted;
        clockTick=discontinuity || rate == 0 || Math.abs(error)>4 ? tick : predicted;
        clockRate=rate == 0 ? 0 : rate * (discontinuity ? 1 : Mth.clamp(1+error*.08f,.9f,1.1f));
        clockNanos=clockNow;
        playbackRate = rate;
        transportRevision = revision;
        sessionId = session;

        active = true;
        def = d;
        attackerId = attacker;
        targetId = target;
        stageOrigin = origin;
        stageForward = forward.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : forward.normalize();
        darkness = dark;
        span = spanIn;

        lastPacketMs = System.currentTimeMillis();

        if (discontinuity) reset();
        if (fresh) setupPuppets();

        // Renk katmani sadece sinematiği IZLEYENDE acilir
        if (shouldDrive()) CinematicPostFx.enable();
    }

    public static void stop() {
        active = false;
        def = null;
        CinematicPostFx.disable();
        PuppetRenderer.clear();
        reset();
    }

    /**
     * Gercek oyunculari sahne aktorleriyle degistirir.
     *
     * Aktorler oyuncunun kendi skinini kullanir; gercek bedenler cizimden
     * cikarilir. Boylece sahnedeki figurler Minecraft'in poz kisitlarina
     * bagli olmaktan cikar.
     */
    private static void setupPuppets() {
        PuppetRenderer.clear();
        if (!shouldDrive()) return;

        addPuppet(attackerId);
        addPuppet(targetId);
        for (var track : def.actorTracks) {
            if (track.binding == com.FIRNI.superheromod.core.cinematic.CinematicActorTrack.Binding.SAND) {
                var puppet = new CinematicPuppet(java.util.UUID.nameUUIDFromBytes(
                        (def.id+":"+track.role).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        new net.minecraft.resources.ResourceLocation("minecraft","textures/block/sand.png"),false);
                puppet.track=track;
                PuppetRenderer.add(puppet);
            } else {
                Entity entity=resolve(track.binding == com.FIRNI.superheromod.core.cinematic.CinematicActorTrack.Binding.ATTACKER
                        ? attackerId : targetId);
                if(entity!=null) for(var puppet:PuppetRenderer.all())
                    if(puppet.sourcePlayer.equals(entity.getUUID())) puppet.track=track;
            }
        }
    }

    private static void addPuppet(int entityId) {
        Entity entity = resolve(entityId);
        // Zombie UVs match the humanoid rig. Keep its skin while allowing elbows/knees to act.
        // Piglin heads and non-humanoid mobs need separate adapters and keep their own mesh below.
        if(entity instanceof net.minecraft.world.entity.monster.Zombie zombie
                && !(entity instanceof net.minecraft.world.entity.monster.ZombifiedPiglin)) {
            var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(zombie);
            var puppet=new CinematicPuppet(entity.getUUID(),renderer.getTextureLocation(zombie),false);
            puppet.legacyZombieUv=true;
            puppet.baseScale=zombie.isBaby()?.5f:1f;
            PuppetRenderer.add(puppet);
            return;
        }
        if (!(entity instanceof AbstractClientPlayer player)) {
            if(entity instanceof net.minecraft.world.entity.LivingEntity) {
                var puppet=new CinematicPuppet(entity.getUUID(),new net.minecraft.resources.ResourceLocation("minecraft","textures/entity/steve.png"),false);
                puppet.sourceEntity=entity;
                PuppetRenderer.add(puppet);
            }
            return;
        }

        PuppetRenderer.add(new CinematicPuppet(
                player.getUUID(),
                player.getSkinTextureLocation(),
                "slim".equals(player.getModelName())));
    }

    /**
     * Samples authored actor tracks at presentation time; gameplay bodies remain untouched.
     * Legacy scenes without named tracks retain their pose-key adapter.
     */
    private static void updatePuppets(float partial, float timeline) {
        if (PuppetRenderer.all().isEmpty()) return;

        synchronized (PuppetRenderer.all()) {
            for (CinematicPuppet puppet : PuppetRenderer.all()) {
                if(puppet.track!=null) {
                    puppet.track.sample(timeline,puppet.sample);
                    var stage=stageFrom(stageOrigin);
                    puppet.position=stage.toWorldSpan(puppet.sample.position);
                    double radians=Math.toRadians(puppet.sample.yaw);
                    puppet.yaw=stage.yawOf(new Vec3(-Math.sin(radians),0,Math.cos(radians)));
                    puppet.scale=puppet.sample.scale*puppet.baseScale;
                    puppet.pose.set(puppet.sample.pose);
                    PuppetRenderer.prepareRigPose(puppet);
                    continue;
                }
                Entity source = findByUuid(puppet.sourcePlayer);
                if (source == null) continue;

                puppet.position = source.getPosition(partial);
                puppet.yaw = source.getViewYRot(partial);

                CinematicDefinition.Actor actor =
                        source.getId() == attackerId
                                ? CinematicDefinition.Actor.ATTACKER
                                : CinematicDefinition.Actor.TARGET;
                if(actor==CinematicDefinition.Actor.TARGET) puppet.position=
                        com.FIRNI.superheromod.core.cinematic.CinematicMotion.target(def,
                                stageFrom(stageOrigin),puppet.position,timeline);
                puppet.yaw=stageFrom(stageOrigin).yawOf(new Vec3(0,0,
                        actor==CinematicDefinition.Actor.ATTACKER?1:-1));

                if (!applyPoseTrack(puppet, actor, timeline)) {
                    idleBreath(puppet, timeline);
                }
            }
            com.FIRNI.superheromod.client.render.puppet.PuppetContacts.apply(
                    PuppetRenderer.all(),def.handContacts,timeline);
        }
    }

    /**
     * Poz izini uygular: zaman cizgisinde aktorun onceki ve sonraki
     * anahtarlarini bulup arasini doldurur.
     *
     * Gecis sirasinda anahtarin ZINCIRI kullanilir; boylece butun eklemler
     * ayni anda degil, kuvvet vucuttan gecerek hareket eder.
     *
     * @return poz izi bu aktoru surduyse true
     */
    private static boolean applyPoseTrack(CinematicPuppet puppet,
                                          CinematicDefinition.Actor actor,
                                          float timeline) {
        CinematicDefinition.PoseKey before = null;
        CinematicDefinition.PoseKey after = null;

        for (CinematicDefinition.PoseKey key : def.poseKeys) {
            if (key.actor() != actor) continue;
            if (key.tick() <= timeline) {
                before = key;
            } else {
                after = key;
                break;
            }
        }

        if (before == null && after == null) return false;

        if (before == null) {
            // Ilk anahtardan once: notr duruştan ilk poza dogru gel
            float span = Math.max(1f, after.tick());
            ActorPose.lerp(NEUTRAL, after.pose(), timeline / span,
                    after.chain(), puppet.pose);
            return true;
        }

        if (after == null) {
            puppet.pose.set(before.pose());
            breathOver(puppet, timeline);
            return true;
        }

        float span = Math.max(1f, after.tick() - before.tick());
        float t = Mth.clamp((timeline - before.tick()) / span, 0f, 1f);

        ActorPose.lerp(before.pose(), after.pose(), t, after.chain(), puppet.pose);
        return true;
    }

    /** Sabit pozda beklerken bile govde tamamen donmus gorunmesin. */
    private static void breathOver(CinematicPuppet puppet, float timeline) {
        float t = timeline * 0.08f;
        puppet.pose.rot[ActorPose.CHEST][0] += Mth.sin(t) * 0.018f;
        puppet.pose.rot[ActorPose.HEAD][0] += Mth.sin(t * 0.7f) * 0.014f;
    }

    /**
     * Nefes — aktorun donmus gorunmemesi icin.
     *
     * Senaryonun 1. sahnesi "gogus cok hafif yukselip iner, omuzlar simetrik
     * degil" diyor; bu onun en sade hali. Poz olaylari geldiginde bunun
     * uzerine gercek koreografi binecek.
     */
    private static void idleBreath(CinematicPuppet puppet, float timeline) {
        float t = timeline * 0.08f;
        ActorPose pose = puppet.pose;

        pose.rot[ActorPose.CHEST][0] = Mth.sin(t) * 0.025f;
        pose.rot[ActorPose.HEAD][0] = Mth.sin(t * 0.7f) * 0.02f;

        // Omuzlar bilerek simetrik degil
        pose.rot[ActorPose.RIGHT_UPPER_ARM][2] = 0.06f + Mth.sin(t) * 0.015f;
        pose.rot[ActorPose.LEFT_UPPER_ARM][2] = -0.09f + Mth.sin(t * 0.9f) * 0.015f;

        // Dirsekler tam duz durmaz
        pose.rot[ActorPose.RIGHT_LOWER_ARM][0] = -0.14f;
        pose.rot[ActorPose.LEFT_LOWER_ARM][0] = -0.10f;
    }

    /** Ilk poz anahtarindan once kullanilan notr durus. */
    private static final ActorPose NEUTRAL = new ActorPose();
    private static final com.FIRNI.superheromod.core.cinematic.CinematicActorTrack.Sample CAMERA_ACTOR_SAMPLE =
            new com.FIRNI.superheromod.core.cinematic.CinematicActorTrack.Sample();

    private static Entity findByUuid(java.util.UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getUUID().equals(id)) return e;
        }
        return null;
    }

    public static boolean isRunning() {
        // FAILSAFE: paket akisi kesilirse sinematikte takili kalmayalim
        if (active && System.currentTimeMillis() - lastPacketMs > 1500) {
            stop();
        }
        return active && def != null;
    }

    private static void reset() {
        fov = 70f;
        roll = 0f;
        fogNear = -1f;
        fogFar = -1f;
        fogR = fogG = fogB = -1f;
        fogActive = false;
    }

    // ------------------------------------------------------------------
    // Kamera
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!shouldDrive()) return;

        Minecraft mc = Minecraft.getInstance();
        float partial = (float) event.getPartialTick();
        Entity attacker = resolve(attackerId);
        Entity target = resolve(targetId);
        if (attacker == null || target == null) return;

        Vec3 aPos = attacker.getPosition(partial);
        Vec3 tPos = target.getPosition(partial);

        // Sahne uzayi: sunucudan gelen origin + forward ile kurulur.
        // Boylece koreografi dunya konumundan bagimsiz.
        StageFrame stage = stageFrom(aPos);

        float timeline = timelineTick();
        updatePuppets(partial, timeline);
        final Vec3 initialAttacker = aPos, initialTarget = tPos;
        var frame = CinematicCameraSampler.sample(def, stage, timeline, at -> {
            Vec3 a = initialAttacker;
            Vec3 t = com.FIRNI.superheromod.core.cinematic.CinematicMotion.target(def, stage, initialTarget, at);
            for (var track : def.actorTracks) {
                if (track.binding == com.FIRNI.superheromod.core.cinematic.CinematicActorTrack.Binding.SAND) continue;
                track.sample(at, CAMERA_ACTOR_SAMPLE);
                if (track.binding == com.FIRNI.superheromod.core.cinematic.CinematicActorTrack.Binding.ATTACKER)
                    a = stage.toWorldSpan(CAMERA_ACTOR_SAMPLE.position);
                else t = stage.toWorldSpan(CAMERA_ACTOR_SAMPLE.position);
            }
            return new CinematicCameraSampler.Actors(a, t);
        });
        fov = frame.fov(); roll = frame.roll();
        var fog = frame.fog();
        fogActive = fog.active(); fogNear = fog.near(); fogFar = fog.far();
        fogR = fog.r(); fogG = fog.g(); fogB = fog.b();
        setCameraPosition(event.getCamera(), frame.position());
        Vec3 dir = frame.look().subtract(frame.position());
        if (dir.lengthSqr() < 1.0E-6) return;
        dir = dir.normalize();

        float impactYaw=0,impactPitch=0,impactRoll=0;
        for(var impact:def.impacts) {
            float impulse=impact.oscillation(timeline);
            impactYaw+=impulse*.28f*impact.direction();
            impactPitch+=impulse*.65f;
            impactRoll+=impulse*.4f*impact.direction();
        }
        event.setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z))+Mth.clamp(impactYaw,-2,2));
        event.setPitch((float) Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0)))+Mth.clamp(impactPitch,-3,3));
        // Yatirma: oyun kamerasi asla yatmaz, bu yuzden beyin yatik kadraji
        // aninda "bu oynanis degil, bu cekim" diye okur
        event.setRoll(roll+Mth.clamp(impactRoll,-2,2));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!shouldDrive() || !fogActive) return;

        event.setNearPlaneDistance(fogNear);
        event.setFarPlaneDistance(fogFar);
        event.setCanceled(true);
    }

    /** Sis rengi sahnenin tonunu belirler. */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!shouldDrive() || !fogActive || fogR < 0f) return;

        event.setRed(fogR);
        event.setGreen(fogG);
        event.setBlue(fogB);
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if (!shouldDrive()) return;
        float kick=0;
        for(var impact:def.impacts)kick+=impact.envelope(timelineTick())*impact.strength()*1.5f;
        event.setNewFovModifier((fov+Math.min(5,kick)) / 70f);
    }

    /** Sinema bantlari + karanlik katmani. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        if (!shouldDrive()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gui = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // Ekran karartma ve sinema bantlari kaldirildi — sahne kamerayla
        // anlatiliyor, ekrani siyahla kapatarak degil.
        if (!def.letterbox) return;

        // Sadece giris/cikista cok kisa bir kararma ile gecis
        float t = timelineTick();
        float fade = 0f;
        if (t < 3f) fade = 1f - t / 3f;
        else if (t > def.totalTicks - 4f) fade = (t - (def.totalTicks - 4f)) / 4f;

        if (fade > 0.01f) {
            int alpha = (int) (Mth.clamp(fade, 0f, 1f) * 255) << 24;
            gui.fill(0, 0, w, h, alpha);
        }
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    public static boolean shouldDrive() {
        if (!isRunning()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        // Sadece katilimcilarin kamerasi ele alinir
        int id = mc.player.getId();
        return id == attackerId || id == targetId;
    }

    public record VisualFrame(CinematicDefinition definition, StageFrame stage, float tick) {}
    public static VisualFrame visualFrame() {
        return shouldDrive() ? new VisualFrame(def,stageFrom(stageOrigin),timelineTick()) : null;
    }

    /** Sunucu tick'i 20Hz; ekran daha hizli. Kesirli zaman cizgisi uretilir. */
    private static float timelineTick() {
        return Math.min(def.totalTicks,clockTick+(System.nanoTime()-clockNanos)/50_000_000f*clockRate);
    }

    /** Sunucunun kurdugu ekseni birebir yeniden kurar (span dahil). */
    private static StageFrame stageFrom(Vec3 attackerPos) {
        return StageFrame.of(stageOrigin, stageForward, span);
    }

    private static Entity resolve(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || id < 0) return null;
        return mc.level.getEntity(id);
    }

    private static void setCameraPosition(Camera camera, Vec3 pos) {
        if (!fieldResolved) {
            fieldResolved = true;
            try {
                cameraPosField = Camera.class.getDeclaredField("position");
                cameraPosField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                for (Field f : Camera.class.getDeclaredFields()) {
                    if (f.getType() == Vec3.class) {
                        cameraPosField = f;
                        cameraPosField.setAccessible(true);
                        break;
                    }
                }
            }
        }
        if (cameraPosField != null) {
            try {
                cameraPosField.set(camera, pos);
            } catch (Exception ignored) {}
        }
    }
}
