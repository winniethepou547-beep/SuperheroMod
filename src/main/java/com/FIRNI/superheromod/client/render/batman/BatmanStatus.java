package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * What Batman does to the others, as everyone sees it:
 * - DOWNED (the grapnel yank): they fall over backwards onto their back, arms up, feet toward him, are dragged along the
 *   ground with dirt flying, lie a moment and get up again. Done for every kind of body by turning the whole render.
 * - STAGGERED: a red daze mark (a fan of spikes) over the head, the body wobbling; a staggered player's own view shakes.
 * - A critical hit lands: a quick flash (the sparks are BatmanFx).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanStatus {
    private record Down(float start, float ticks, Vec3 dir) {}
    private static final Map<Integer, Float> DAZED = new HashMap<>();
    private static final Map<Integer, Down> DOWNED = new HashMap<>();
    private static final Set<Integer> PUSHED = new HashSet<>();

    private BatmanStatus() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }

    static void stagger(int entity, float ticks) { if (ticks <= 0) DAZED.remove(entity); else DAZED.put(entity, now() + ticks); }
    static void downed(int entity, Vec3 dir, float ticks, int batman) { DOWNED.put(entity, new Down(now(), ticks, dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize())); }
    static void crit(int entity) {}
    public static boolean staggered(Entity e) { Float u = DAZED.get(e.getId()); return u != null && u > now(); }

    /** 0..1: how far over on their back they are now (falling in fast, getting up at the end). */
    private static float lying(Down d, float t) {
        float age = t - d.start();
        if (age < 0 || age > d.ticks()) return 0;
        float fall = ease(age / 4f), rise = 1 - ease((age - (d.ticks() - 7)) / 7f);
        return Math.min(fall, rise);
    }
    private static float ease(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    /** The plain player model's arms while down: thrown up over the head, flailing a little. */
    public static boolean downedArms(PlayerModel<?> m, Entity e, float ageInTicks) {
        Down d = DOWNED.get(e.getId());
        if (d == null) return false;
        float k = lying(d, now());
        if (k <= .01f) return false;
        float flail = Mth.sin(ageInTicks * .9f) * .25f;
        m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -2.8f + flail); m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, .35f);
        m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -2.8f - flail); m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -.35f);
        m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, -.2f); m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, -.1f);
        m.rightSleeve.copyFrom(m.rightArm); m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg); m.leftPants.copyFrom(m.leftLeg);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void pre(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity en = e.getEntity();
        float t = now();
        Down d = DOWNED.get(en.getId());
        float lie = d == null ? 0 : lying(d, t);
        Float dazed = DAZED.get(en.getId());
        boolean wobble = dazed != null && dazed > t;
        if (lie <= .001f && !wobble) return;
        PoseStack p = e.getPoseStack();
        p.pushPose();
        PUSHED.add(en.getId());
        if (lie > .001f) {
            // On their back, the head away from him, feet toward him (cancelling the body's own turn so the chest faces up).
            Vec3 head = d.dir().scale(-1);
            float phi = (float) Math.atan2(head.x, head.z);
            float body = Mth.rotLerp(e.getPartialTick(), en.yBodyRotO, en.yBodyRot);
            p.translate(0, .22 * lie, 0);
            p.mulPose(Axis.YP.rotation(phi));
            p.mulPose(Axis.XP.rotation(lie * Mth.HALF_PI));
            p.mulPose(Axis.YP.rotationDegrees(-(180 - body)));
        } else {
            float w = (en.tickCount + e.getPartialTick()) + en.getId();
            float k = Math.min(1, (dazed - t) / 10f);
            p.mulPose(Axis.ZP.rotation(.13f * k * Mth.sin(w * .45f)));
            p.mulPose(Axis.XP.rotation(.08f * k * Mth.sin(w * .33f + 1)));
        }
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void post(RenderLivingEvent.Post<?, ?> e) {
        if (PUSHED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { DAZED.clear(); DOWNED.clear(); return; }
        float t = mc.level.getGameTime();
        DAZED.values().removeIf(u -> u < t - 2);
        DOWNED.values().removeIf(d -> t - d.start() > d.ticks() + 2);
        // Dragged on their back: dirt thrown up from under them.
        for (var en : DOWNED.entrySet()) {
            Entity body = mc.level.getEntity(en.getKey());
            Down d = en.getValue();
            float age = t - d.start();
            if (body == null || age > DRAG_TICKS + 2) continue;
            BlockPos under = BlockPos.containing(body.getX(), body.getY() - .2, body.getZ());
            BlockState ground = mc.level.getBlockState(under);
            if (ground.isAir()) continue;
            var r = mc.level.random;
            for (int i = 0; i < 4; i++)
                mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), body.getX() + (r.nextDouble() - .5) * .8, body.getY() + .1, body.getZ() + (r.nextDouble() - .5) * .8,
                        -d.dir().x * .15 + (r.nextDouble() - .5) * .1, .12 + r.nextDouble() * .12, -d.dir().z * .15 + (r.nextDouble() - .5) * .1);
            if (age < 1) for (int i = 0; i < 10; i++)
                mc.level.addParticle(ParticleTypes.POOF, body.getX(), body.getY() + .2, body.getZ(), (r.nextDouble() - .5) * .2, .05, (r.nextDouble() - .5) * .2);
        }
        // Staggered, this client's own player: the view shakes as long as it lasts.
        if (mc.player != null && staggered(mc.player) && ((int) t & 1) == 0) ClientScreenShake.add(.07f);
    }

    /** The daze mark: a fan of red spikes rising over the head, pulsing and turning slowly, with a soft glow. */
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || DAZED.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = now(), partial = e.getPartialTick();
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        var fx = FilmFx.batched();
        Vec3 right = new Vec3(rr.x, rr.y, rr.z), up = new Vec3(uu.x, uu.y, uu.z);
        FilmContext c = new FilmContext(p, fx, cam, right, up, t, 0, partial);
        try {
            for (var en : DAZED.entrySet()) {
                if (en.getValue() < t) continue;
                Entity body = mc.level.getEntity(en.getKey());
                if (body == null || body == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
                Down d = DOWNED.get(en.getKey());
                float lie = d == null ? 0 : lying(d, t);
                Vec3 top = body.getPosition(partial).add(0, lie > .5f ? .8 : body.getBbHeight() + .35, 0);
                float left = Math.min(1, (en.getValue() - t) / 8f), pulse = .85f + .15f * Mth.sin(t * .6f);
                FilmFx.glow(c, top.add(up.scale(.12)), .55, 0xff2a2a, .35f * left);
                int spikes = 9;
                for (int i = 0; i < spikes; i++) {
                    // Spread over the top half, the middle ones longest.
                    float a = (float) Math.PI * (i + .5f) / spikes + Mth.sin(t * .05f) * .08f;
                    float lenK = .55f + .45f * Mth.sin((float) Math.PI * (i + .5f) / spikes);
                    Vec3 dir = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                    Vec3 base = top.add(dir.scale(.1)), tip = top.add(dir.scale((.28 + .22 * lenK) * pulse));
                    FilmFx.streak(c, base, tip, .05, 0xff3030, .95f * left, .15f * left, true);
                    FilmFx.streak(c, base, tip, .02, 0xffd0d0, .8f * left, 0, true);
                }
            }
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { DAZED.clear(); DOWNED.clear(); PUSHED.clear(); }
}
