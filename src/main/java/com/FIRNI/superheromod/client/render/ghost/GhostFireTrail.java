package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Burning tyre track behind the Hell Cycle: low flames along the ground that die down. */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class GhostFireTrail {
    private record Mark(Vec3 at, long tick, float strength) {}
    private static final ArrayDeque<Mark> MARKS = new ArrayDeque<>();
    private static final Map<Integer, Long> LAST = new HashMap<>();
    private static final int LIFE = 36;

    /** Called by the bike renderer; records at most one mark per bike per tick. */
    public static void mark(int bike, Vec3 groundContact, float strength) {
        var level = Minecraft.getInstance().level;
        if (level == null || strength <= .05f) return;
        long now = level.getGameTime();
        if (LAST.getOrDefault(bike, -1L) == now) return;
        LAST.put(bike, now);
        if (LAST.size() > 64) LAST.clear();
        MARKS.addLast(new Mark(groundContact, now, Math.min(1, strength)));
        while (MARKS.size() > 400) MARKS.removeFirst();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { MARKS.clear(); LAST.clear(); }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !GhostFireMaterial.ready()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        while (!MARKS.isEmpty() && now - MARKS.peekFirst().tick > LIFE) MARKS.removeFirst();
        if (MARKS.isEmpty()) return;
        var p = e.getPoseStack(); Vec3 cam = e.getCamera().getPosition();
        p.pushPose(); p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        try {
            var buffers = mc.renderBuffers().bufferSource();
            var v = buffers.getBuffer(GhostFireMaterial.TYPE);
            var rot = e.getCamera().rotation();
            var r = new org.joml.Vector3f(1, 0, 0).rotate(rot); var u = new org.joml.Vector3f(0, 1, 0).rotate(rot);
            Vec3 right = new Vec3(r.x, r.y, r.z), up = new Vec3(u.x, u.y, u.z);
            int i = 0;
            for (Mark mark : MARKS) {
                if (mark.at.distanceToSqr(cam) > 80 * 80) continue;
                float life = (now - mark.tick + e.getPartialTick()) / LIFE;
                float fade = (1 - life) * (1 - life) * mark.strength;
                double h = .12 + .22 * fade;
                GhostFireMaterial.volume(v, p, mark.at.add(0, h * .8, 0), right, up, .16 + .1 * fade, h, (i++ % 13) / 13f, fade * .85f, 1 - life * .7f);
            }
            buffers.endBatch(GhostFireMaterial.TYPE);
        } finally { mv.popPose(); RenderSystem.applyModelViewMatrix(); p.popPose(); }
    }
}
