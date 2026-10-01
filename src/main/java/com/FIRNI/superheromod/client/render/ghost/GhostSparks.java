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
import java.util.ArrayList;
import java.util.List;

/**
 * Molten sparks in the style of a sling-ring portal: dense showers of bright streaks thrown
 * off tangentially, stretched along their velocity, arcing under gravity, bouncing once off
 * the ground and cooling from white-gold to deep orange before they die.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class GhostSparks {
    private static final class Spark {
        Vec3 pos, vel; double floor; float age, life, size; boolean bounced;
    }
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final int MAX = 700;
    private static final double GRAVITY = .045, DRAG = .975, STREAK = 1.4;
    private static double lastClock = Double.NaN;

    /** Throw count sparks from point, mostly along velocity (blocks/tick), resting on floor. */
    public static void emit(Vec3 point, Vec3 velocity, double floor, int count) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var random = level.getRandom();
        for (int i = 0; i < count && SPARKS.size() < MAX; i++) {
            Spark s = new Spark();
            double speed = .45 + random.nextDouble() * .75;
            Vec3 spread = new Vec3(random.nextGaussian(), random.nextGaussian() * .6 + .35, random.nextGaussian()).scale(.16);
            s.vel = velocity.scale(speed * .5).add(spread).add(0, .05 + random.nextDouble() * .12, 0);
            s.pos = point;
            s.floor = floor;
            s.life = 9 + random.nextFloat() * 12;
            s.size = .012f + random.nextFloat() * .016f;
            SPARKS.add(s);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { SPARKS.clear(); lastClock = Double.NaN; }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { SPARKS.clear(); return; }
        double clock = mc.level.getGameTime() + mc.getFrameTime();
        double dt = Double.isNaN(lastClock) ? 0 : Math.max(0, Math.min(2, clock - lastClock));
        lastClock = clock;
        step(dt);
        if (SPARKS.isEmpty() || !GhostFireMaterial.ready()) return;
        var p = e.getPoseStack();
        Vec3 camera = e.getCamera().getPosition();
        p.pushPose(); p.translate(-camera.x, -camera.y, -camera.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        try {
            var buffers = mc.renderBuffers().bufferSource();
            var v = buffers.getBuffer(GhostFireMaterial.TYPE);
            for (Spark s : SPARKS) {
                float life = s.age / s.life;
                Vec3 head = s.pos, tail = s.pos.subtract(s.vel.scale(STREAK));
                if (head.distanceToSqr(tail) < .0025) tail = head.subtract(s.vel.normalize().scale(.05));
                Vec3 axis = head.subtract(tail);
                Vec3 side = axis.cross(camera.subtract(head));
                if (side.lengthSqr() < 1e-8) continue;
                side = side.normalize().scale(s.size * (1.2 - life * .5));
                float heat = (float) Math.max(0, 1 - life * 1.15);
                float alpha = (float) Math.min(1, (1 - life) * 1.6);
                Vec3[] q = {tail.subtract(side), tail.add(side), head.add(side), head.subtract(side)};
                float[] u = {0, 1, 1, 0}, t = {0, 0, 1, 1};
                for (int c = 0; c < 4; c++) GhostFireMaterial.vertex(v, p, q[c].x, q[c].y, q[c].z, u[c], t[c], .55f, 0, heat, alpha);
            }
            buffers.endBatch(GhostFireMaterial.TYPE);
        } finally { mv.popPose(); RenderSystem.applyModelViewMatrix(); p.popPose(); }
    }
    private static void step(double dt) {
        if (dt <= 0) return;
        double drag = Math.pow(DRAG, dt);
        SPARKS.removeIf(s -> {
            s.age += dt;
            if (s.age >= s.life) return true;
            s.vel = s.vel.scale(drag).add(0, -GRAVITY * dt, 0);
            s.pos = s.pos.add(s.vel.scale(dt));
            if (s.pos.y < s.floor) {
                if (s.bounced) return true;
                // One lively bounce, skidding along the ground.
                s.bounced = true;
                s.pos = new Vec3(s.pos.x, s.floor, s.pos.z);
                s.vel = new Vec3(s.vel.x * .55, -s.vel.y * .32, s.vel.z * .55);
            }
            return false;
        });
    }
}
