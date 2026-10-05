package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.heroes.batman.BatmanConfig;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * The reflex block's deflects as everyone sees them (FX_BLOCK from BatmanReflex): which move his body plays (BatmanLayer
 * asks deflect() and capeGrab()), and the contact itself. Gauntlet: a bright point at the spikes, sparks thrown off
 * along the way the attack is sent (not scattered), a few hot metal fragments, a small shock ring, a short deflection
 * streak; the view kicks hard and short. Cape: a dark ripple through the cloth at the point, a soft flash, a puff of air
 * and dust pushed off it, the deflection streak; a softer, longer nudge of the view.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanReflexFx {
    /** A deflect move playing: what, since when, and (the cape) until when it is held across him. */
    private static final class Block {
        final int kind; final float time; float holdEnd;
        Block(int kind, float time, float holdEnd) { this.kind = kind; this.time = time; this.holdEnd = holdEnd; }
        /** Ticks the cape is held in front (after it has been drawn across). */
        float hold() { return Math.max(BatmanMotion.CAPE_HOLD_MIN, holdEnd - time - BatmanMotion.CAPE_ACROSS); }
    }
    private static final Map<Integer, Block> LAST = new HashMap<>();
    private static final class Spark {
        Vec3 pos, vel; final float start, life; final int rgb; final boolean metal;
        Spark(Vec3 pos, Vec3 vel, float start, float life, int rgb, boolean metal) { this.pos = pos; this.vel = vel; this.start = start; this.life = life; this.rgb = rgb; this.metal = metal; }
    }
    private record Hit(Vec3 at, Vec3 dir, int kind, float time) {}
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final List<Hit> HITS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private BatmanReflexFx() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }
    private static float amount() { return BatmanConfig.EFFECTS.get().floatValue(); }

    static void receive(BatmanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = now();
        int kind = Math.abs((int) p.power());
        // A positive kind starts the move; a negative one only draws the contact (a hail of hits plays one move).
        // The cape: one move for the whole hail. Already drawn across (or being drawn): every hit only keeps it there
        // a little longer; it goes back once nothing more comes.
        Block was = LAST.get(p.entity());
        boolean capeUp = kind == BLOCK_CAPE && was != null && was.kind == BLOCK_CAPE && t - was.time < BatmanMotion.CAPE_ACROSS + was.hold();
        if (capeUp) was.holdEnd = Math.max(was.holdEnd, t + BatmanMotion.CAPE_KEEP);
        else if (p.power() > 0) LAST.put(p.entity(), new Block(kind, t, t + BatmanMotion.CAPE_KEEP));
        // A blow on the cloth: it jolts where it was struck (it is cloth, not a wall).
        if (kind == BLOCK_CAPE && (capeUp || p.power() > 0)) {
            Vec3 dir0 = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
            com.FIRNI.superheromod.client.render.cloth.CapeCloth.poke(p.entity(), p.pos().x, p.pos().y, p.pos().z, -dir0.x, -dir0.y, -dir0.z, .18);
        }
        Vec3 at = p.pos(), in = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
        HITS.add(new Hit(at, in, kind, t));
        boolean cape = kind == BLOCK_CAPE, evade = kind == BLOCK_EVADE_R || kind == BLOCK_EVADE_L;
        // The way the attack is sent: back out and to the side (sparks follow it).
        Vec3 aside = new Vec3(-in.z, 0, in.x).scale(kind == BLOCK_LEFT ? -1 : 1);
        Vec3 away = in.scale(.6).add(aside.scale(.8)).add(0, cape ? .35 : .2, 0).normalize();
        int n = evade ? 0 : (int) ((cape ? 10 : 16) * Math.max(.3f, amount()));
        for (int i = 0; i < n; i++) {
            Vec3 v = away.scale(.18 + RANDOM.nextDouble() * .3).add((RANDOM.nextDouble() - .5) * .16, RANDOM.nextDouble() * .12, (RANDOM.nextDouble() - .5) * .16);
            SPARKS.add(new Spark(at, v, t, 6 + RANDOM.nextInt(8), cape ? (i % 2 == 0 ? 0xffe6b0 : 0xd8e6ff) : (i % 3 == 0 ? 0xffffff : 0xffd27a), false));
        }
        if (!cape && !evade) for (int i = 0; i < (int) (3 * amount()); i++)
            SPARKS.add(new Spark(at, away.scale(.12).add((RANDOM.nextDouble() - .5) * .1, .08 + RANDOM.nextDouble() * .08, (RANDOM.nextDouble() - .5) * .1), t, 14 + RANDOM.nextInt(8), 0x9aa0a8, true));
        var me = mc.player;
        if (me != null && me.getId() == p.entity()) BatmanClient.shake(cape ? .1f : .16f);
        if (cape && me != null) {
            // Air pushed off the cloth: a few dust puffs where it is low enough to stir the ground.
            for (int i = 0; i < 4; i++)
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.POOF, at.x + aside.x * (i - 1.5) * .3, at.y - .4, at.z + aside.z * (i - 1.5) * .3, in.x * .05, .01, in.z * .05);
        }
    }

    /** His own view through a deflect: a slip sways it out to that side and back; the cape's sweep turns it a little. */
    @SubscribeEvent public static void camera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float[] d = deflect(mc.player.getId(), now());
        if (d == null) return;
        int kind = (int) d[0];
        float t = d[1], len = BatmanMotion.deflectLength(kind, d[2]);
        float bump = Mth.sin(Mth.PI * Mth.clamp(t / (len - 1), 0, 1));
        float shake = BatmanConfig.SHAKE.get().floatValue();
        if (kind == BLOCK_EVADE_R || kind == BLOCK_EVADE_L) {
            float s = kind == BLOCK_EVADE_R ? 1 : -1;
            e.setRoll(e.getRoll() + s * 7 * bump * shake);
            e.setYaw(e.getYaw() - s * 5 * bump * shake);
            e.setPitch(e.getPitch() + 3 * bump * shake);
        } else if (kind == BLOCK_CAPE) e.setYaw(e.getYaw() + 4 * bump * shake);
        else e.setYaw(e.getYaw() + (kind == BLOCK_LEFT ? -3 : 3) * bump * shake);
    }
    /** The deflect move this Batman's body is playing now: {kind, ticks since, (the cape) ticks held in front}, or null. */
    static float[] deflect(int batman, float now) {
        Block b = LAST.get(batman);
        if (b == null) return null;
        float t = now - b.time;
        float hold = b.kind == BLOCK_CAPE ? b.hold() : 0;
        if (t < 0 || t > BatmanMotion.deflectLength(b.kind, hold)) return null;
        return new float[]{b.kind, t, hold};
    }
    /** How firmly his right hand holds the cape's edge now (0..1). */
    static float capeGrab(int batman, float now) {
        float[] d = deflect(batman, now);
        return d == null || (int) d[0] != BLOCK_CAPE ? 0 : BatmanMotion.capeGrab(d[1], d[2]);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { SPARKS.clear(); HITS.clear(); LAST.clear(); return; }
        float t = mc.level.getGameTime();
        for (Iterator<Spark> it = SPARKS.iterator(); it.hasNext(); ) {
            Spark s = it.next();
            if (t - s.start > s.life) { it.remove(); continue; }
            s.pos = s.pos.add(s.vel);
            s.vel = s.vel.add(0, s.metal ? -.05 : -.025, 0).scale(.9);
        }
        HITS.removeIf(h -> t - h.time() > 10);
        if (LAST.size() > 64) LAST.clear();
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (SPARKS.isEmpty() && HITS.isEmpty())) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float time = now(), partial = e.getPartialTick();
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        var fx = FilmFx.batched();
        FilmContext c = new FilmContext(p, fx, cam, new Vec3(rr.x, rr.y, rr.z), new Vec3(uu.x, uu.y, uu.z), time, 0, partial);
        try {
            for (Hit h : HITS) {
                float age = time - h.time(), k = Mth.clamp(age / 8f, 0, 1);
                Vec3 aside = new Vec3(-h.dir().z, 0, h.dir().x).scale(h.kind() == BLOCK_LEFT ? -1 : 1);
                Vec3 away = h.dir().scale(.6).add(aside.scale(.8)).normalize();
                if (h.kind() == BLOCK_EVADE_R || h.kind() == BLOCK_EVADE_L) {
                    // The blow's air going by where his head was: a pale smear, nothing hit.
                    if (age < 5) FilmFx.streak(c, h.at().subtract(h.dir().scale(.9)), h.at().add(h.dir().scale(-.1)), .05, 0xdfe6ee, 0, .35f * (1 - k), true);
                    continue;
                } else if (h.kind() == BLOCK_CAPE) {
                    // The cloth ripples: dark rings spreading from the point, a soft cold flash, the attack sent off.
                    FilmFx.ring(c, h.at(), .15 + .9 * k, .08, 0x1a1c20, .55f * (1 - k), false);
                    FilmFx.ring(c, h.at(), .1 + .6 * k, .05, 0xcfe0ff, .35f * (1 - k), true);
                    if (age < 3) FilmFx.glow(c, h.at(), .7, 0xcfe0ff, .5f * (1 - age / 3));
                } else {
                    // The spikes: a hard white point, a gold flash, a small shock ring facing the attack.
                    if (age < 2.5f) FilmFx.glow(c, h.at(), .35, 0xffffff, .95f * (1 - age / 2.5f));
                    if (age < 4) FilmFx.glow(c, h.at(), .8, 0xffc45a, .55f * (1 - age / 4));
                    FilmFx.ring(c, h.at(), .1 + .55 * k, .04, 0xffe2a0, .6f * (1 - k), true);
                }
                if (age < 5) FilmFx.streak(c, h.at(), h.at().add(away.scale(.6 + 2.6 * k)), .03, h.kind() == BLOCK_CAPE ? 0xdfe8ff : 0xffe6b0, .7f * (1 - k), 0, true);
            }
            for (Spark s : SPARKS) {
                float age = (time - s.start) / s.life;
                if (age < 0 || age > 1) continue;
                Vec3 at = s.pos.add(s.vel.scale(partial));
                if (s.metal) FilmFx.streak(c, at, at.add(s.vel.scale(.4)), .03, s.rgb, .9f, .9f, false);
                else FilmFx.streak(c, at.subtract(s.vel.scale(1.6)), at, .018, s.rgb, 0, .95f * (1 - age), true);
            }
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { SPARKS.clear(); HITS.clear(); LAST.clear(); }
}
