package com.FIRNI.superheromod.client.render.zed;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import static com.FIRNI.superheromod.heroes.zed.ZedAction.*;

/**
 * Zed himself, drawn in place of the player model (which is hidden for him). While he melts into
 * shadow (the Death Mark) he is drawn as his own shadow, fading out, his eyes the last to go. Each
 * draw reports where his eyes are, for their bloom and their streaks.
 */
public final class ZedLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public ZedLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack p, MultiBufferSource b, int light, AbstractClientPlayer e, float walk, float amount, float partial, float time, float yaw, float pitch) {
        if (!ZedClient.isHero(e) || e.isInvisible()) return;
        ZedClient.State s = ZedClient.get(e);
        int action = s == null ? IDLE : s.action;
        float t = s == null ? 0 : ZedClient.clock(s, partial);
        ZedMotion.Pose pose = ZedMotion.sample(action, t, time);
        var model = getParentModel();
        if (pose.vanish >= .98f) return;
        ZedBody.capture = true;
        ZedBody.eyeRight = ZedBody.eyeLeft = null;
        try {
            if (pose.vanish > .02f) ZedBody.draw(p, b, light, pose, walk, amount, model.head.yRot, model.head.xRot, time, ZedBody.SHADOW, .8f * (1 - pose.vanish));
            else ZedBody.draw(p, b, light, pose, walk, amount, model.head.yRot, model.head.xRot, time, ZedBody.NORMAL, 1);
        } finally {
            ZedBody.capture = false;
        }
        // Only a draw in the world counts (not the one in the inventory screen).
        var level = Minecraft.getInstance().level;
        if (level != null && ZedBody.eyeRight != null && ZedBody.eyeRight.distanceTo(e.getPosition(partial)) < 3.5)
            ZedEyes.record(e.getId(), ZedBody.eyeRight, ZedBody.eyeLeft, level.getGameTime() + partial, 1);
    }
}
