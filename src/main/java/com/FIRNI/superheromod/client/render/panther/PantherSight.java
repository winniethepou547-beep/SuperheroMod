package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.panther.PantherAction.CAMO_SEEN;

/**
 * What Black Panther sees in his camouflage: an eye over every other player's head (only on his own screen, through
 * walls). Violet and struck through: they cannot see him. Red and open: he is inside CAMO_SEEN blocks of them, they
 * see his shimmer. The eye pops when it changes and the red one beats like a pulse; it fades in and out with the
 * camouflage.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class PantherSight {
    private static final ResourceLocation HIDDEN = new ResourceLocation(SuperheroMod.MODID, "textures/gui/panther/eye_hidden.png");
    private static final ResourceLocation SEEN = new ResourceLocation(SuperheroMod.MODID, "textures/gui/panther/eye_seen.png");
    private static final int FULL = 15728880;
    private static final double RANGE = 64;
    /** Per player: seen now, and when that last changed (level time). */
    private static final Map<Integer, float[]> STATE = new HashMap<>();
    /** How far the eyes have faded in (0..1), and when that was last stepped. */
    private static float shown, shownAt;

    private PantherSight() {}

    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float now = mc.level.getGameTime() + e.getPartialTick();
        PantherClient.State s = PantherClient.isHero(mc.player) ? PantherClient.get(mc.player) : null;
        boolean on = s != null && s.camoLeft > 0 && !FilmDirector.playing() && !mc.options.hideGui;
        float dt = Mth.clamp(now - shownAt, 0, 2);
        shownAt = now;
        shown = Mth.clamp(shown + (on ? dt / 5f : -dt / 4f), 0, 1);
        if (shown <= 0) { STATE.clear(); return; }

        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var rotation = e.getCamera().rotation();
        Vec3 me = mc.player.getPosition(e.getPartialTick());
        for (Player other : mc.level.players()) {
            if (other == mc.player || other.isSpectator() || !other.isAlive()) continue;
            Vec3 at = other.getPosition(e.getPartialTick());
            double d = at.distanceTo(me);
            if (d > RANGE) continue;
            boolean seen = d <= CAMO_SEEN;
            float[] st = STATE.computeIfAbsent(other.getId(), id -> new float[]{seen ? 1 : 0, -100});
            if ((st[0] > .5f) != seen) { st[0] = seen ? 1 : 0; st[1] = now; }
            float since = now - st[1];
            // The pop as it changes, the red one's slow beat, size kept readable with distance.
            float pop = since < 6 ? 1 + .45f * (float) Math.exp(-since / 1.6f) * Mth.sin(Math.min(1, since / 6f) * Mth.PI) : 1;
            float beat = seen ? 1 + .08f * (float) Math.pow(Math.max(0, Mth.sin(now * .35f)), 6) : 1;
            double toCam = at.distanceTo(cam);
            float size = (float) (.42 + toCam * .018) * pop * beat * (.6f + .4f * shown);
            float alpha = shown * (since < 3 ? .6f + .4f * since / 3 : 1);
            p.pushPose();
            p.translate(at.x - cam.x, at.y + other.getBbHeight() + .75 + size * .4 - cam.y, at.z - cam.z);
            p.mulPose(rotation);
            Matrix4f m = p.last().pose();
            VertexConsumer v = buffers.getBuffer(RenderType.textSeeThrough(seen ? SEEN : HIDDEN));
            float h = size * .5f;
            // (Local +x is the screen's left once turned to the camera; the corners go round counter-clockwise as seen.)
            v.vertex(m, -h, -h, 0).color(1f, 1f, 1f, alpha).uv(1, 1).uv2(FULL).endVertex();
            v.vertex(m, -h, h, 0).color(1f, 1f, 1f, alpha).uv(1, 0).uv2(FULL).endVertex();
            v.vertex(m, h, h, 0).color(1f, 1f, 1f, alpha).uv(0, 0).uv2(FULL).endVertex();
            v.vertex(m, h, -h, 0).color(1f, 1f, 1f, alpha).uv(0, 1).uv2(FULL).endVertex();
            p.popPose();
        }
        buffers.endBatch();
        if (STATE.size() > 128) STATE.clear();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { STATE.clear(); shown = 0; }
}
