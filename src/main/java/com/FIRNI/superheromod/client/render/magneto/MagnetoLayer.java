package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.client.gui.Showcase;
import com.FIRNI.superheromod.client.render.cloth.CapeCloth;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.*;

/**
 * Magneto himself, drawn in place of the player model (which is hidden for him): standing or floating (eased between,
 * never snapping), the gesture of whatever he is casting crossfaded in and out, the hand that holds or drives metal
 * following his aim, the punch of the iron fist answered by his own fist, his walk underneath, his look spread from
 * the chest to the head, and the cape: real cloth (CapeCloth) simulated in the world, answering every move he makes.
 */
public final class MagnetoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final class Blend {
        int action = -1; float changed, fade = 2.5f; Pose from, last;
        float fly, at = -1;
        float[] glow = new float[2];
    }
    private static final Map<Integer, Blend> BLENDS = new HashMap<>();
    private static final CapeCloth.Frame CAPE_FRAME = new CapeCloth.Frame();
    /** The violet cape: falls to his calves, heavy wool, no scallops. */
    private static final CapeCloth.Style CAPE = new CapeCloth.Style().length(1.2f).bottom(1.05f).thickness(.03f).weight(1f).folds(.04f)
            .colours(MagnetoBody.VIOLET, MagnetoBody.CAPE_IN, MagnetoBody.VIOLET_DARK);

    public MagnetoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack p, MultiBufferSource b, int light, AbstractClientPlayer e, float walk, float amount, float partial, float time, float yaw, float pitch) {
        if (!MagnetoClient.isHero(e) || e.isInvisible()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        MagnetoClient.State s = MagnetoClient.get(e);
        boolean shown = Showcase.is(e);
        int action = shown ? Showcase.action() : s == null ? IDLE : s.action;
        float t = shown ? Showcase.time() : s == null ? 0 : MagnetoClient.clock(s, partial);
        float now = level.getGameTime() + partial;
        Blend blend = BLENDS.computeIfAbsent(e.getId(), id -> new Blend());
        if (BLENDS.size() > 64) BLENDS.clear();

        // Floating or standing, eased.
        boolean flying = shown ? Showcase.aura() > .5f : s != null && (s.flying() || s.gliding());
        float dt = blend.at < 0 ? 0 : Mth.clamp(now - blend.at, 0, 3);
        blend.at = now;
        blend.fly = Mth.clamp(blend.fly + (flying ? dt : -dt) / LIFT_TICKS, 0, 1);
        float fly = PantherMotion.ease(blend.fly);
        Vec3 v = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        float speed = (float) Mth.clamp(v.length() / .5, 0, 1) * fly;
        Pose base = MagnetoMotion.stance(time);
        if (fly > 0) { Pose air = MagnetoMotion.fly(time, speed); base.toward(air, fly); }
        if (fly < 1 && !shown) walking(base, walk, Math.min(1, amount * 1.3f) * (1 - fly));
        Pose pose = MagnetoMotion.sample(action, t, base);
        if (blend.action != action) {
            blend.from = blend.last == null ? null : blend.last.copy();
            blend.changed = now;
            blend.fade = action == IDLE ? 3.5f : action == BURST || action == SHARD ? 1.2f : 2.2f;
            blend.action = action;
        }
        float k = (now - blend.changed) / blend.fade;
        if (blend.from != null && k >= 0 && k < 1) { Pose mixed = blend.from.copy(); mixed.toward(pose, PantherMotion.ease(k)); pose = mixed; }
        blend.last = pose.copy();
        SpikePull.panther(pose, e, partial);

        // The hand that holds or drives the metal follows his aim; the fist's punch goes through his own arm.
        var model = getParentModel();
        float lookYaw = model.head.yRot, lookPitch = model.head.xRot;
        if (action == CONTROL || action == FIST || action == GRAB) {
            pose.armAdd(0, ARM_X, Mth.clamp(lookPitch, -1.2f, 1.2f)).armAdd(0, ARM_Y, Mth.clamp(lookYaw, -.8f, .8f) * .6f);
            if (action == FIST && s != null) MagnetoMotion.punch(pose, s.punchAge < 0 ? -1 : s.punchAge + partial);
        }
        pose.add(CHEST_YAW, .2f * lookYaw).add(CHEST_PITCH, .15f * lookPitch);
        float headYaw = lookYaw * .8f, headPitch = lookPitch * .85f;

        // The working hand's glow, eased in and out.
        for (int side = 0; side < 2; side++) {
            float want = MagnetoMotion.glow(action, t, side);
            blend.glow[side] += (want - blend.glow[side]) * Math.min(1, dt * (want > blend.glow[side] ? .35f : .15f));
            MagnetoBody.GLOW[side] = blend.glow[side];
        }
        MagnetoBody.glowTime = now;
        MagnetoBody.sim = CAPE_FRAME;
        try {
            MagnetoBody.draw(p, b, light, pose, headYaw, headPitch, time, new MagnetoBody.Cloth(0, 0, 0, fly));
        } finally {
            MagnetoBody.GLOW[0] = MagnetoBody.GLOW[1] = 0;
            MagnetoBody.sim = null;
        }
        CapeCloth.draw(p, b, light, e, partial, e.getId(), CAPE_FRAME, CAPE);
    }

    /** His walk: long, unhurried strides, the arms barely swinging. */
    private static void walking(Pose p, float walk, float amount) {
        if (amount < .01f) return;
        float phase = walk * .6662f, cos = Mth.cos(phase), sin = Mth.sin(phase);
        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? 1 : -1;
            p.legAdd(side, LEG_X, sg * cos * .5f * amount).legAdd(side, KNEE, Math.max(0, sg * sin) * .5f * amount);
            p.armAdd(side, ARM_X, -sg * cos * .18f * amount);
        }
        p.add(LIFT, .3f * Math.abs(sin) * amount).add(CHEST_YAW, -.05f * cos * amount);
    }

    public static void clear() { BLENDS.clear(); CapeCloth.clear(); }
}
