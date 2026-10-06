package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanConfig;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Q, the cryogenic shell, as everyone sees it.
 * <ul>
 * <li>On his body (body(), set before every draw): the shell layer IcemanBody draws over every part by its height:
 * closing from the feet to the crown while it forms (an organic ease, a little ahead at the legs), then whole, with a
 * slow cold shimmer; its cracks grow with the health it has lost; every blow flashes it; under the burst's stress it
 * glows and cracks all over until KRAAAK takes it away.</li>
 * <li>In the world: while it forms, frost mist creeping up from the ground, crystal nuclei sparkling just ahead of the
 * growth line, a disc of rime on the ground round him; while it holds, a few big crystals jutting out of the cocoon
 * (organic, never a box) and a faint cold glow inside. A blow: by its damage, light (chips, a sparkle, a little frost),
 * heavy (a crack line flashing on the shell, a chunk breaking off the side hit and flying, a small shake for him), very
 * heavy (a big branching crack, shards, a burst of mist, a stronger shake).</li>
 * <li>Broken: great glacier slabs and the shatter, then while he drops into the landing loose chunks keep sliding off
 * his shoulders and back.</li>
 * <li>The burst: the inner light pulsing faster and faster, mist leaking from the cracks, the crystals trembling, cracks
 * snapping (local sounds); then the flash, a fast shock ring along the ground, a sphere of shards flying out 360 degrees
 * to about its radius, mist, the frost spreading out, a shake for everyone near by distance.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanShellFx {
    private IcemanShellFx() {}

    /** A blow on the shell, kept in his body's frame (so the crack stays where it hit). */
    private record Blow(float at, float power, float fwd, float side, float height, int seed) {}
    /** One Iceman's shell as this client follows it. */
    private static final class Shell {
        final List<Blow> blows = new ArrayList<>();
        /** The last blow (for the flash and the flinch). */
        float hitAt = -100, hitPower, hitFwd, hitSide;
        /** The rime on the ground: where, since when, when it began to fade (-1 still there), when the burst spread it. */
        Vec3 rimeAt; float rimeStart = -100, rimeEnd = -1, rimeBurst = -1;
        /** Which action and clock start the one-shot cues belong to, and which have fired (bits). */
        int action = -1; long start; int fired;
    }
    private static final Map<Integer, Shell> SHELLS = new HashMap<>();
    private static Level lastLevel;

    private static Shell shell(int id) {
        if (SHELLS.size() > 64) SHELLS.clear();
        return SHELLS.computeIfAbsent(id, k -> new Shell());
    }
    private static float now() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime();
    }

    // ------------------------------------------------------------------ the packets
    public static void receive(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        switch (p.kind()) {
            case FX_SHELL_HIT -> hit(p);
            case FX_SHELL_BREAK -> broken(p);
            case FX_SHELL_BURST -> burst(p);
            default -> {}
        }
    }

    /** His body's yaw (degrees) and his feet, now. */
    private static float yaw(Entity e, float partial) {
        return e instanceof LivingEntity l ? Mth.rotLerp(partial, l.yBodyRotO, l.yBodyRot) : e.getViewYRot(partial);
    }
    private static Vec3 forward(float yawDeg) { float y = yawDeg * Mth.DEG_TO_RAD; return new Vec3(-Mth.sin(y), 0, Mth.cos(y)); }
    private static Vec3 right(float yawDeg) { float y = yawDeg * Mth.DEG_TO_RAD; return new Vec3(-Mth.cos(y), 0, -Mth.sin(y)); }

    /**
     * The shell struck (power = damage, dir = toward whoever struck, flat; zero when unknown): the reaction by weight.
     * Light (< 4): chips, a sparkle, a breath of frost. Heavy (< 10): a crack line flashing, a chunk off the side hit.
     * Very heavy: a big branching crack, shards, a mist burst. He shakes with the heavier ones.
     */
    private static void hit(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level.getEntity(p.entity());
        Vec3 feet = e != null ? e.position() : p.pos();
        float yaw = e != null ? yaw(e, 1) : 0;
        Vec3 dir = p.dir();
        if (dir.lengthSqr() < 1e-4) {
            float a = IceParticles.rand() * Mth.TWO_PI;
            dir = new Vec3(Mth.cos(a), 0, Mth.sin(a));
        } else dir = new Vec3(dir.x, 0, dir.z).normalize();
        float dmg = Math.max(0, p.power());
        float now = now();
        Shell s = shell(p.entity());
        float fwd = (float) dir.dot(forward(yaw)), side = (float) dir.dot(right(yaw));
        float height = .45f + .55f * IceParticles.rand();
        s.hitAt = now; s.hitPower = dmg; s.hitFwd = fwd; s.hitSide = side;
        if (dmg >= 4) {
            if (s.blows.size() > 10) s.blows.remove(0);
            s.blows.add(new Blow(now, dmg, fwd, side, height, (int) (IceParticles.rand() * 100000)));
        }
        Vec3 at = feet.add(dir.scale(.48)).add(0, height, 0);
        Vec3 out = dir.add(0, .25, 0);
        boolean me = IcemanClient.isMe(p.entity());
        if (dmg < 4) {
            for (int i = 0, n = IceParticles.count(5, at); i < n; i++)
                IceParticles.shard(at.add(IceParticles.jitter(.05)), out.scale(.08 + .06 * IceParticles.rand()).add(IceParticles.jitter(.04)), .035f + .03f * IceParticles.rand(), 18 + (int) (IceParticles.rand() * 14), IceMesh.FRESH);
            IceParticles.flash(at, .35f, .45f, 3);
            IceParticles.frostDust(at, out.scale(.06), 1.2f);
            if (me) IcemanClient.shake(.03f);
        } else if (dmg < 10) {
            for (int i = 0, n = IceParticles.count(8, at); i < n; i++)
                IceParticles.shard(at.add(IceParticles.jitter(.06)), out.scale(.1 + .1 * IceParticles.rand()).add(IceParticles.jitter(.05)), .04f + .04f * IceParticles.rand(), 22 + (int) (IceParticles.rand() * 16), i % 2 == 0 ? IceMesh.FRESH : IceMesh.GLACIER);
            // The chunk: one real piece of the shell knocked off the side that was hit.
            IceParticles.shard(at, out.scale(.16).add(0, .08, 0), .17f + .05f * IceParticles.rand(), 55 + (int) (IceParticles.rand() * 20), IceMesh.GLACIER);
            IceParticles.flash(at, .6f, .65f, 4);
            IceParticles.mist(at, out.scale(.03), .3f, .02f, .25f, 26);
            IceParticles.frostDust(at, out.scale(.1), 2.5f);
            if (me) IcemanClient.shake(.13f);
        } else {
            for (int i = 0, n = IceParticles.count(16, at); i < n; i++)
                IceParticles.shard(at.add(IceParticles.jitter(.1)), out.scale(.12 + .16 * IceParticles.rand()).add(IceParticles.jitter(.07)), .045f + .06f * IceParticles.rand(), 26 + (int) (IceParticles.rand() * 20), i % 3 == 0 ? IceMesh.FRESH : IceMesh.GLACIER);
            for (int i = 0; i < 2; i++)
                IceParticles.shard(at.add(IceParticles.jitter(.08)), out.scale(.18 + .06 * i).add(IceParticles.jitter(.04)).add(0, .1, 0), .2f + .07f * IceParticles.rand(), 60 + (int) (IceParticles.rand() * 20), IceMesh.GLACIER);
            IceParticles.flash(at, .95f, .8f, 5);
            for (int i = 0, n = IceParticles.count(5, at); i < n; i++)
                IceParticles.mist(at.add(IceParticles.jitter(.15)), out.scale(.05).add(IceParticles.jitter(.02)), .35f, .03f, .3f, 30 + (int) (IceParticles.rand() * 12));
            IceParticles.frostDust(at, out.scale(.12), 4);
            if (me) IcemanClient.shake(.3f);
        }
    }

    /** Broken: the slabs, the shatter, the mist dropping round him. */
    private static void broken(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level.getEntity(p.entity());
        Vec3 feet = e != null ? e.position() : p.pos();
        Vec3 push = p.dir().lengthSqr() < 1e-4 ? Vec3.ZERO : new Vec3(p.dir().x, 0, p.dir().z).normalize().scale(-.05);
        Vec3 c = feet.add(0, .7, 0);
        IceParticles.shatter(c, push, 1.2f, IceMesh.GLACIER);
        // The shell's great slabs, falling away round him (outward and down, not flung far).
        int slabs = IceParticles.count(7, c);
        for (int i = 0; i < slabs; i++) {
            float a = Mth.TWO_PI * (i + IceParticles.rand() * .5f) / Math.max(1, slabs);
            Vec3 out = new Vec3(Mth.cos(a), 0, Mth.sin(a));
            Vec3 at = feet.add(out.scale(.42)).add(0, .15 + 1.0 * IceParticles.rand(), 0);
            IceParticles.shard(at, out.scale(.06 + .06 * IceParticles.rand()).add(push).add(0, .06 + .06 * IceParticles.rand(), 0), .24f + .1f * IceParticles.rand(), 55 + (int) (IceParticles.rand() * 25), IceMesh.GLACIER);
        }
        for (int i = 0, n = IceParticles.count(8, c); i < n; i++) {
            float a = Mth.TWO_PI * i / 8f;
            IceParticles.mist(feet.add(Mth.cos(a) * .5, .15, Mth.sin(a) * .5), new Vec3(Mth.cos(a) * .03, .004, Mth.sin(a) * .03), .45f, .025f, .3f, 36);
        }
        IceParticles.ring(feet.add(0, .05, 0), 2.0f, .35f, IceParticles.SNOW_RGB, .5f, 14, false);
        Shell s = shell(p.entity());
        if (s.rimeEnd < 0) s.rimeEnd = now();
        if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.38f);
        else shakeNear(c, .2f, 8);
    }

    /** KRAAAK: the flash, the ring, the sphere of shards, the mist, the frost spreading, everyone near shaken. */
    private static void burst(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level.getEntity(p.entity());
        Vec3 feet = e != null ? e.position() : p.pos();
        float radius = Math.max(2, p.power());
        Vec3 c = feet.add(0, .8, 0);
        IceParticles.flash(c, 2.6f, 1f, 6);
        IceParticles.flash(c, 1.2f, 1f, 3);
        IceParticles.ring(feet.add(0, .06, 0), radius * 1.15f, .55f, IceParticles.COLD_LIGHT, .95f, 9, true);
        IceParticles.ring(feet.add(0, .04, 0), radius * .95f, .9f, IceParticles.SNOW_RGB, .55f, 18, false);
        IceParticles.ring(c, radius * .75f, .4f, IceParticles.COLD_LIGHT, .6f, 7, true);
        // The shell flies apart: shards spread evenly over a sphere (a golden spiral), fast enough to carry about the
        // radius against the air, the upper half a little favoured (the ground stops the rest).
        int n = IceParticles.count(70, c);
        float speed = radius / 30f;
        for (int i = 0; i < n; i++) {
            float yk = 1 - 1.6f * (i + .5f) / n;
            float rr = Mth.sqrt(Math.max(0, 1 - yk * yk));
            float a = i * 2.39996f;
            Vec3 d = new Vec3(Mth.cos(a) * rr, yk * .7f + .12f, Mth.sin(a) * rr);
            float v = speed * (.7f + .55f * IceParticles.rand());
            boolean big = i % 6 == 0;
            IceParticles.shard(c.add(d.scale(.45)), d.scale(v), big ? .2f + .12f * IceParticles.rand() : .05f + .07f * IceParticles.rand(),
                    (big ? 55 : 30) + (int) (IceParticles.rand() * 25), big ? IceMesh.GLACIER : i % 3 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        }
        for (int i = 0, m = IceParticles.count(14, c); i < m; i++) {
            float a = Mth.TWO_PI * i / 14f;
            Vec3 d = new Vec3(Mth.cos(a), .15 + .2 * IceParticles.rand(), Mth.sin(a));
            IceParticles.mist(c.add(d.scale(.6)), d.scale(.12 + .05 * IceParticles.rand()), .6f, .05f, .4f, 34 + (int) (IceParticles.rand() * 14));
        }
        for (int i = 0, m = IceParticles.count(30, c); i < m; i++) {
            Vec3 d = IceParticles.jitter(1).normalize();
            IceParticles.snow(c.add(d.scale(.4)), d.scale(.25 + .2 * IceParticles.rand()).add(0, .12, 0), .03f + .03f * IceParticles.rand(), 20 + (int) (IceParticles.rand() * 16));
        }
        Shell s = shell(p.entity());
        float now = now();
        if (s.rimeAt == null) { s.rimeAt = feet; s.rimeStart = now - 20; }
        s.rimeBurst = now;
        s.rimeEnd = now;
        if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.6f);
        else shakeNear(c, .65f, radius * 2.5f);
    }
    /** A shake for the local player by distance (full at the point, nothing at reach). */
    static void shakeNear(Vec3 at, float amount, float reach) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = mc.player.position().distanceTo(at);
        if (d >= reach) return;
        float k = (float) (1 - d / reach);
        IcemanClient.shake(amount * k * k);
    }

    // ------------------------------------------------------------------ on his body
    /** Sets the shell on his body for this draw (IcemanBody.SHELL_*), from his state; called by the layer before drawing him. */
    public static void body(int entity, IcemanClient.State s, int action, float t, float now) {
        IcemanBody.SHELL_COVER = 0; IcemanBody.SHELL_CRACK = 0; IcemanBody.SHELL_FLASH = 0; IcemanBody.SHELL_GLOW = 0;
        IcemanBody.SHELL_THICK = 1.8f;
        if (s == null) return;
        Shell sh = SHELLS.get(entity);
        float flash = 0;
        if (sh != null && sh.hitAt > -100) {
            float dt = now - sh.hitAt;
            if (dt >= 0 && dt < 12) flash = (float) Math.exp(-dt / 2.6f) * Mth.clamp(.35f + sh.hitPower / 12f, 0, 1.2f);
        }
        float lost = Mth.clamp(1 - s.shell, 0, 1);
        switch (action) {
            case SHELL_FORM -> {
                float x = Mth.clamp(t / SHELL_FORM_TICKS, 0, 1);
                // Organic: quick to take the feet and legs, slowing as it closes over the shoulders and the head.
                float grow = 1 - (1 - x) * (1 - x) * (1 - .35f * x);
                IcemanBody.SHELL_COVER = 1.15f * grow;
                IcemanBody.SHELL_THICK = 1.1f + 1.0f * grow;
                IcemanBody.SHELL_GLOW = .25f * (1 - x) + .1f;
                IcemanBody.SHELL_FLASH = flash;
            }
            case SHELL -> {
                IcemanBody.SHELL_COVER = 1.15f;
                IcemanBody.SHELL_THICK = 2.1f + .08f * Mth.sin(now * .05f);
                IcemanBody.SHELL_CRACK = Mth.clamp(lost * 1.1f + .35f * flash, 0, 1);
                IcemanBody.SHELL_FLASH = flash;
                IcemanBody.SHELL_GLOW = .08f + .05f * (.5f + .5f * Mth.sin(now * .045f)) + .2f * lost;
            }
            case SHELL_BURST -> {
                if (t < BURST_STRESS) {
                    float k = Mth.clamp(t / BURST_STRESS, 0, 1);
                    float pulse = .5f + .5f * Mth.sin(stressPhase(t));
                    IcemanBody.SHELL_COVER = 1.15f;
                    IcemanBody.SHELL_THICK = 2.1f + .25f * k;
                    IcemanBody.SHELL_CRACK = Mth.clamp(Math.max(lost, k * k * 1.1f), 0, 1);
                    IcemanBody.SHELL_GLOW = Mth.clamp(.15f + k * (.4f + .6f * pulse), 0, 1.2f);
                    IcemanBody.SHELL_FLASH = Math.max(flash, k > .9f ? (k - .9f) * 8 : 0);
                }
            }
            default -> {}
        }
    }
    /** The inner light's pulse during the stress: faster and faster (radians). */
    static float stressPhase(float t) { return .45f * t + .05f * t * t; }

    /**
     * The flinch inside the shell after a blow (for IcemanShellMotion): {toward the blow (1 front, -1 behind), to his
     * right (1) or left (-1), strength 0..1 now}; null when nothing.
     */
    public static float[] flinch(int entity) {
        Shell s = SHELLS.get(entity);
        if (s == null || s.hitAt <= -100) return null;
        float dt = now() - s.hitAt;
        if (dt < 0 || dt > 14) return null;
        float env = (dt / 1.8f) * (float) Math.exp(1 - dt / 1.8f);
        float k = env * Mth.clamp(s.hitPower / 12f, .15f, 1.3f);
        return k < .01f ? null : new float[]{s.hitFwd, s.hitSide, k};
    }

    // ------------------------------------------------------------------ every tick: what follows his clock
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { SHELLS.clear(); lastLevel = mc.level; }
        if (mc.level == null || mc.isPaused()) return;
        float now = mc.level.getGameTime();
        for (var en : IcemanClient.states().entrySet()) {
            IcemanClient.State s = en.getValue();
            int a = s.action;
            boolean inShell = a == SHELL_FORM || a == SHELL || a == SHELL_BURST || a == SHELL_BREAK;
            Shell sh = inShell ? shell(en.getKey()) : SHELLS.get(en.getKey());
            if (sh == null) continue;
            Entity ent = mc.level.getEntity(en.getKey());
            if (sh.action != a || sh.start != s.start) {
                // A shell beginning: the rime is laid where he stands; a shell ended some other way: it fades.
                if (a == SHELL_FORM && ent != null) { sh.rimeAt = ent.position(); sh.rimeStart = now; sh.rimeEnd = -1; sh.rimeBurst = -1; }
                if (!inShell && sh.rimeAt != null && sh.rimeEnd < 0) sh.rimeEnd = now;
                sh.action = a; sh.start = s.start; sh.fired = 0;
            }
            if (ent == null || !inShell || ent.isInvisible()) continue;
            float t = IcemanClient.clock(s, 0);
            Vec3 feet = ent.position();
            float yaw = yaw(ent, 1);
            switch (a) {
                case SHELL_FORM -> {
                    // Humidity drawn in: cold mist creeping up the body from the ground as it closes.
                    if (t < SHELL_FORM_TICKS) {
                        for (int i = 0, n = IceParticles.count(2, feet); i < n; i++) {
                            float ang = IceParticles.rand() * Mth.TWO_PI, r = .55f + .3f * IceParticles.rand();
                            Vec3 at = feet.add(Mth.cos(ang) * r, .05 + .2 * IceParticles.rand(), Mth.sin(ang) * r);
                            IceParticles.mist(at, new Vec3(-Mth.cos(ang) * .02, .018 + .01 * IceParticles.rand(), -Mth.sin(ang) * .02), .3f, .012f, .28f, 22);
                        }
                        if (IceParticles.rand() < .5f) IceParticles.frostDust(feet.add(IceParticles.jitter(.3)).add(0, 1.3 * t / SHELL_FORM_TICKS, 0), new Vec3(0, .02, 0), 1);
                    }
                }
                case SHELL -> {
                    // Now and then a breath of cold falling off the cocoon.
                    if (IceParticles.rand() < .12f) {
                        float ang = IceParticles.rand() * Mth.TWO_PI;
                        IceParticles.mist(feet.add(Mth.cos(ang) * .45, .3 + .8 * IceParticles.rand(), Mth.sin(ang) * .45), new Vec3(Mth.cos(ang) * .01, -.006, Mth.sin(ang) * .01), .25f, .012f, .16f, 30);
                    }
                }
                case SHELL_BURST -> {
                    if (t < BURST_STRESS) {
                        float k = t / BURST_STRESS;
                        // Mist leaking out of the cracks, more as the stress builds.
                        for (int i = 0, n = IceParticles.count(Math.round(1 + 3 * k), feet); i < n; i++) {
                            float ang = IceParticles.rand() * Mth.TWO_PI;
                            Vec3 out = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
                            IceParticles.mist(feet.add(out.scale(.5)).add(0, .2 + 1.0 * IceParticles.rand(), 0), out.scale(.02 + .03 * k).add(0, .01, 0), .2f + .15f * k, .02f, .2f + .15f * k, 18);
                        }
                        if (IceParticles.rand() < .3f + .5f * k) IceParticles.frostDust(feet.add(IceParticles.jitter(.35)).add(0, .7, 0), new Vec3(0, -.02, 0), 1 + 2 * k);
                        // The cracks snapping inside it (local: the server plays the rumble).
                        cue(sh, 0, t >= 4, ent, ModSounds.ICEMAN_CRACK.get(), .7f, .8f);
                        cue(sh, 1, t >= 8, ent, ModSounds.ICEMAN_CRACK.get(), .9f, 1.0f);
                        cue(sh, 2, t >= 11, ent, ModSounds.ICEMAN_CRACK.get(), 1.1f, 1.2f);
                        cue(sh, 3, t >= 12.5f, ent, ModSounds.ICEMAN_CRACK.get(), 1.2f, 1.45f);
                        if (IcemanClient.isMe(en.getKey()) && t > 5) IcemanClient.shake(.012f + .03f * k * k);
                    }
                }
                case SHELL_BREAK -> {
                    // He hits the ground in the landing: snow kicked up round him, a jolt in his view.
                    if (!fired(sh, 4) && t >= BREAK_LAND) {
                        sh.fired |= 1 << 4;
                        for (int i = 0, n = IceParticles.count(10, feet); i < n; i++) {
                            float ang = Mth.TWO_PI * i / 10f + IceParticles.rand() * .4f;
                            IceParticles.snow(feet.add(Mth.cos(ang) * .4, .05, Mth.sin(ang) * .4), new Vec3(Mth.cos(ang) * .1, .07 + .05 * IceParticles.rand(), Mth.sin(ang) * .1), .03f, 16);
                        }
                        IceParticles.mist(feet.add(0, .15, 0), new Vec3(0, .005, 0), .6f, .03f, .25f, 30);
                        if (IcemanClient.isMe(en.getKey())) IcemanClient.shake(.12f);
                    }
                    // Loose chunks sliding off his shoulders and back as he lands and holds there.
                    if (t >= BREAK_LAND - 2 && t < BREAK_LAND + 22) {
                        float k = 1 - Mth.clamp((t - BREAK_LAND) / 22f, 0, 1);
                        if (IceParticles.rand() < .55f * k * Math.min(1f, IcemanConfig.EFFECTS.get().floatValue())) {
                            Vec3 fw = forward(yaw), rt = right(yaw);
                            float side = IceParticles.rand() * 2 - 1;
                            Vec3 at = feet.add(fw.scale(.15 + .25 * IceParticles.rand())).add(rt.scale(.3 * side)).add(0, .75 + .3 * IceParticles.rand(), 0);
                            Vec3 v = rt.scale(.03 * side).add(fw.scale(-.01)).add(0, .01, 0).add(IceParticles.jitter(.01));
                            IceParticles.shard(at, v, .05f + .07f * IceParticles.rand(), 30 + (int) (IceParticles.rand() * 20), IceParticles.rand() < .5f ? IceMesh.MILKY : IceMesh.GLACIER);
                            if (IceParticles.rand() < .5f) IceParticles.frostDust(at, v, 1);
                        }
                    }
                }
                default -> {}
            }
        }
        // Forget shells long gone (their rime faded, no blow in flight).
        float nowT = now;
        SHELLS.entrySet().removeIf(en -> {
            Shell sh = en.getValue();
            IcemanClient.State s = IcemanClient.get(en.getKey());
            boolean active = s != null && (s.action == SHELL_FORM || s.action == SHELL || s.action == SHELL_BURST || s.action == SHELL_BREAK);
            return !active && (sh.rimeEnd < 0 || nowT - sh.rimeEnd > 80) && nowT - sh.hitAt > 40;
        });
    }
    private static boolean fired(Shell s, int bit) { return (s.fired & 1 << bit) != 0; }
    /** A one-shot local sound at him once its moment has come. */
    private static void cue(Shell s, int bit, boolean due, Entity at, SoundEvent sound, float volume, float pitch) {
        if (!due || fired(s, bit)) return;
        s.fired |= 1 << bit;
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.getX(), at.getY() + .8, at.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    // ------------------------------------------------------------------ drawing
    /**
     * The crystals jutting out of the cocoon, in his frame (blocks: forward, to his right, up; the way they point; length,
     * radius; the share of the shell's growth they wait for).
     */
    private static final float[][] CRYSTALS = {
            {.05f, -.30f, 1.02f, .05f, -.5f, 1f, .46f, .09f, .72f},
            {-.16f, .32f, .98f, -.4f, .55f, .9f, .56f, .10f, .74f},
            {-.40f, .04f, .72f, -1f, .1f, .55f, .50f, .11f, .52f},
            {.28f, .30f, .28f, .45f, .9f, .25f, .34f, .08f, .22f},
            {-.12f, -.38f, .45f, -.25f, -1f, .45f, .40f, .09f, .42f},
            {-.22f, -.02f, 1.24f, -.55f, 0f, 1f, .38f, .08f, .9f},
            {.33f, -.22f, .14f, .7f, -.55f, .35f, .28f, .07f, .1f},
            {-.30f, -.26f, .18f, -.6f, -.6f, .3f, .3f, .07f, .12f}};

    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (SHELLS.isEmpty()) return;
        IceStage st = IceStage.open(e);
        if (st == null) return;
        var mc = Minecraft.getInstance();
        try {
            float partial = st.partial, now = st.time;
            IceMesh.Ctx c = st.ice();
            boolean any = false;
            // ---- the ice: the rime discs, the cocoon's crystals, the blows' cracks
            for (var en : SHELLS.entrySet()) {
                Shell sh = en.getValue();
                if (sh.rimeAt != null) {
                    float grow = FilmFx.ease((now - sh.rimeStart) / 9f);
                    float fade = sh.rimeEnd < 0 ? 1 : 1 - Mth.clamp((now - sh.rimeEnd) / 70f, 0, 1);
                    float r = 1.05f * grow;
                    if (sh.rimeBurst >= 0) r = Mth.lerp(FilmFx.ease((now - sh.rimeBurst) / 7f), r, 2.6f);
                    if (fade > 0 && r > .02f) {
                        c.light = IceStage.light(sh.rimeAt.add(0, .5, 0));
                        IcemanGroundFx.rime(c, sh.rimeAt, r, .55f * fade, en.getKey() * 7 + 3);
                        any = true;
                    }
                }
                IcemanClient.State s = IcemanClient.get(en.getKey());
                if (s == null) continue;
                Entity ent = mc.level.getEntity(en.getKey());
                if (ent == null || ent.isInvisible()) continue;
                int a = s.action;
                float t = IcemanClient.clock(s, partial);
                float cover = a == SHELL_FORM ? Mth.clamp(t / SHELL_FORM_TICKS, 0, 1) * 1.25f : a == SHELL || a == SHELL_BURST && t < BURST_STRESS ? 1.25f : 0;
                if (cover <= 0) continue;
                Vec3 feet = ent.getPosition(partial);
                boolean own = IcemanClient.isMe(en.getKey()) && mc.options.getCameraType().isFirstPerson();
                float yaw = yaw(ent, partial);
                Vec3 fw = forward(yaw), rt = right(yaw);
                float stress = a == SHELL_BURST ? Mth.clamp(t / BURST_STRESS, 0, 1) : 0;
                c.light = IceStage.light(feet.add(0, .8, 0));
                c.flash = .25f * stress * (.5f + .5f * Mth.sin(stressPhase(t)));
                for (int i = 0; i < CRYSTALS.length; i++) {
                    float[] k = CRYSTALS[i];
                    float g = FilmFx.ease((cover - k[8]) / .3f);
                    if (g <= 0) continue;
                    Vec3 base = feet.add(fw.scale(k[0])).add(rt.scale(k[1])).add(0, k[2], 0);
                    if (own && base.distanceToSqr(st.cam) < .5) continue;
                    if (stress > 0) base = base.add(IceParticles.jitter(.012 * stress));
                    Vec3 dir = fw.scale(k[3]).add(rt.scale(k[4])).add(0, k[5], 0);
                    IceMesh.crystal(c, base, dir, k[6] * g, k[7] * (.5f + .5f * g), 5, en.getKey() * 13 + i, IceMesh.GLACIER, .9f, .6f + stress);
                    IceMesh.crystal(c, base, dir, k[6] * g * .55f, k[7] * .45f * g, 4, en.getKey() * 13 + i + 50, IceMesh.CORE, .8f, 0);
                }
                c.flash = 0;
                // The nuclei: sparkles just ahead of the closing line.
                if (a == SHELL_FORM && t < SHELL_FORM_TICKS) {
                    float line = Mth.clamp(t / SHELL_FORM_TICKS, 0, 1) * 1.35f;
                    int tick = (int) t;
                    for (int i = 0; i < 7; i++) {
                        double h = IceMesh.hash(tick * 17.3 + i * 5.1 + en.getKey());
                        float ang = (float) (h * Mth.TWO_PI), age = t - tick;
                        Vec3 at = feet.add(Mth.cos(ang) * .44, line + .06 + .1 * IceMesh.hash(tick + i * 3.7), Mth.sin(ang) * .44);
                        IceMesh.sparkle(c, at, .09f + .05f * (float) h, Mth.sin(Mth.PI * Mth.clamp(age, 0, 1)) * .9f);
                    }
                }
                // The blows' cracks: zigzags flashing out over the shell from where each landed, then fading into it.
                for (Blow b : sh.blows) {
                    float dt = now - b.at;
                    float life = b.power >= 10 ? 26 : 14;
                    if (dt < 0 || dt > life) continue;
                    float spread = FilmFx.ease(dt / 3f), bright = (1 - dt / life) * (b.power >= 10 ? 1.1f : .8f);
                    Vec3 n = fw.scale(b.fwd).add(rt.scale(b.side));
                    if (n.lengthSqr() < 1e-4) n = fw; else n = n.normalize();
                    Vec3 at = feet.add(n.scale(.5)).add(0, b.height, 0);
                    Vec3 u = new Vec3(-n.z, 0, n.x), v = new Vec3(0, 1, 0);
                    int arms = b.power >= 10 ? 5 : 3;
                    float len = (b.power >= 10 ? .42f : .24f) * spread;
                    for (int j = 0; j < arms; j++) {
                        float ang = (float) (Mth.TWO_PI * (j + IceMesh.hash(b.seed + j) * .6) / arms);
                        Vec3 p0 = at;
                        for (int q = 1; q <= 3; q++) {
                            float wob = (float) (IceMesh.hash(b.seed * 3 + j * 7 + q) - .5) * .9f;
                            Vec3 d = u.scale(Mth.cos(ang + wob)).add(v.scale(Mth.sin(ang + wob)));
                            Vec3 p1 = p0.add(d.scale(len / 3)).add(n.scale(-.02 * q));
                            IceMesh.line(c, p0, p1, .012f * (4 - q) + .006f, .6f * bright, .85f * bright, bright);
                            p0 = p1;
                        }
                    }
                    IceMesh.glow(c, at, .25f * bright, .2f * bright, .3f * bright, .4f * bright);
                }
                any = true;
            }
            st.endIce();
            if (!any) return;
            // ---- the light: the cold glow inside, the stress pulsing
            FilmContext f = st.fx();
            for (var en : SHELLS.entrySet()) {
                IcemanClient.State s = IcemanClient.get(en.getKey());
                if (s == null) continue;
                Entity ent = mc.level.getEntity(en.getKey());
                if (ent == null || ent.isInvisible()) continue;
                int a = s.action;
                float t = IcemanClient.clock(s, partial);
                Vec3 c0 = ent.getPosition(partial).add(0, .7, 0);
                if (IcemanClient.isMe(en.getKey()) && mc.options.getCameraType().isFirstPerson()) continue;
                if (a == SHELL || a == SHELL_FORM) {
                    float k = a == SHELL ? 1 : Mth.clamp(t / SHELL_FORM_TICKS, 0, 1);
                    FilmFx.glow(f, c0, 1.0, IceParticles.COLD_LIGHT, (.09f + .03f * Mth.sin(now * .05f)) * k);
                } else if (a == SHELL_BURST && t < BURST_STRESS + 1) {
                    float k = Mth.clamp(t / BURST_STRESS, 0, 1), pulse = .5f + .5f * Mth.sin(stressPhase(t));
                    float out = t > BURST_STRESS ? 1 - (t - BURST_STRESS) : 1;
                    FilmFx.glow(f, c0, 1.0 + .7 * k, IceParticles.COLD_LIGHT, (.12f + .45f * k * pulse) * out);
                    FilmFx.glow(f, c0, .5 + .3 * k, 0xffffff, .3f * k * k * pulse * out);
                }
            }
        } finally {
            st.close();
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { SHELLS.clear(); lastLevel = null; }

}
