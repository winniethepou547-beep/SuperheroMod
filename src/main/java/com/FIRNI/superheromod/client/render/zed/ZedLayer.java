package com.FIRNI.superheromod.client.render.zed;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import java.util.HashMap;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.zed.ZedAction.*;

/**
 * Zed himself, drawn in place of the player model (which is hidden for him). When his action changes
 * the new pose starts from wherever his body was (a cut flows out of the last one's recovery, nothing
 * snaps). While he melts into shadow (the Death Mark) he is drawn as his own shadow, fading out, his
 * eyes the last to go. Each draw reports where his eyes and blade tips are, for their light and trails.
 */
public final class ZedLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final class Blend { int action = -1; float changed, run, runAt; ZedMotion.Pose from, last; }
    private static final Map<Integer, Blend> BLENDS = new HashMap<>();
    private static final float CROSSFADE = 1.6f;

    public ZedLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack p, MultiBufferSource b, int light, AbstractClientPlayer e, float walk, float amount, float partial, float time, float yaw, float pitch) {
        if (!ZedClient.isHero(e) || e.isInvisible()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        ZedClient.State s = ZedClient.get(e);
        int action = s == null ? IDLE : s.action;
        float t = s == null ? 0 : ZedClient.clock(s, partial);
        float now = level.getGameTime() + partial;
        ZedMotion.Pose pose = ZedMotion.sample(action, t, time);
        // Out of the last pose into the new one.
        Blend blend = BLENDS.computeIfAbsent(e.getId(), id -> new Blend());
        if (BLENDS.size() > 64) BLENDS.clear();
        if (blend.action != action) {
            blend.from = blend.last == null ? null : blend.last.copy();
            blend.action = action; blend.changed = now;
        }
        float k = (now - blend.changed) / CROSSFADE;
        if (blend.from != null && k < 1 && k >= 0) {
            ZedMotion.Pose mixed = blend.from.copy();
            mixed.toward(pose, ZedMotion.ease(k));
            mixed.vanish = pose.vanish; mixed.glow = pose.glow;
            pose = mixed;
        }
        // Into the sprint and out of it smoothly; full only when he is not doing anything else.
        boolean free = action == IDLE || action == SWAP || action == MARK_RETURN;
        float want = e.isSprinting() && amount > .3f ? (free ? 1 : .35f) : 0;
        float dt = Math.max(0, Math.min(2, now - blend.runAt));
        blend.runAt = now;
        blend.run += (want - blend.run) * (1 - (float) Math.exp(-dt * .35f));
        pose.run = blend.run;
        blend.last = pose;
        var model = getParentModel();
        if (pose.vanish >= .98f) return;
        ZedBody.capture = true;
        ZedBody.eyeRight = ZedBody.eyeLeft = ZedBody.tipRight = ZedBody.tipLeft = null;
        try {
            if (pose.vanish > .02f) ZedBody.draw(p, b, light, pose, walk, amount, model.head.yRot, model.head.xRot, time, ZedBody.SHADOW, .8f * (1 - pose.vanish));
            else ZedBody.draw(p, b, light, pose, walk, amount, model.head.yRot, model.head.xRot, time, ZedBody.NORMAL, 1);
        } finally {
            ZedBody.capture = false;
        }
        // Only a draw in the world counts (not the one in the inventory screen).
        if (ZedBody.eyeRight != null && ZedBody.eyeRight.distanceTo(e.getPosition(partial)) < 3.5) {
            ZedEyes.record(e.getId(), ZedBody.eyeRight, ZedBody.eyeLeft, now, 1);
            ZedBlades.record(e.getId(), ZedBody.tipRight, ZedBody.tipLeft, now);
        }
    }
}
