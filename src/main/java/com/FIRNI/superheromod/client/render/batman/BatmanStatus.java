package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.DazeStars;
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
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * What Batman does to the others, as everyone sees it:
 * - DOWNED (the grapnel yank): they fall over backwards onto their back, arms up, feet toward him, are dragged along the
 *   ground with dirt flying, lie a moment and get up again. Done for every kind of body by turning the whole render.
 * - STAGGERED: stars circling the head (DazeStars, the one daze effect shared with the flash grenade), the body
 *   wobbling; a staggered player's own view shakes. The stars stay DAZE_LINGER ticks more after the stagger ends.
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

    static void stagger(int entity, float ticks) {
        float t = now();
        if (ticks <= 0) { if (DAZED.remove(entity) != null) DazeStars.release(entity, DAZE_LINGER); }
        else { DAZED.put(entity, t + ticks); DazeStars.daze(entity, ticks + DAZE_LINGER, .55f); }
    }
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

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { DAZED.clear(); DOWNED.clear(); PUSHED.clear(); }
}
