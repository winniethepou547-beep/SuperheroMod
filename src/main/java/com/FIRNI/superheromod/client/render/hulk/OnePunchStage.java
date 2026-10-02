package com.FIRNI.superheromod.client.render.hulk;

import com.FIRNI.superheromod.client.render.film.FilmBackdrop;
import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.client.render.hulk.RagePath.*;
import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * The stage of ONE PUNCH, drawn by the film: an open plain under a blue sky, a great mountain far down
 * the line of the punch; Hulk and his target; every blow's shock ring and dust, the dust swelling into a
 * cloud at the height of the barrage and clearing; the cracks and trembling grit of the wind-up; the
 * air current the last punch drives out across the plain, swelling, slowing, thinning; and the mountain
 * behind it, split open by a vast ravine, rocks still falling from its walls.
 */
public final class OnePunchStage {
    private static final int DUST_LIGHT = 0xe9e4da, DUST_MID = 0xcbc3b4, DUST_DARK = 0xa59d8e, AIR = 0xf3f1ec, AIR_SHADE = 0xb4b0a8;
    private static FilmCast cast;

    private OnePunchStage() {}

    static float clamp(float t) { return HulkMotion.clamp(t); }
    static float window(float t, float in, float out, float soft) { return FilmFx.window(t, in, out, soft); }

    /** The sky and the ground: midday, the dust in the air thickening with the barrage and the air current. */
    public static FilmBackdrop.Params backdrop(float t) {
        float dust = .28f * window(t, ULT_BARRAGE + 20, ULT_CLEAR + 10, 30) + .4f * window(t, ULT_PUNCH + 10, ULT_REVEAL + 20, 30) + .08f * clamp((t - ULT_REVEAL) / 40f);
        return FilmBackdrop.Params.plain(new Vec3(-.35, .85, -.4), dust, .6f);
    }

    public static void render(FilmContext c, Player hulk, Entity target, float t, float partial) {
        Matrix4f m = c.pose().last().pose();
        mountain(c, m, t);
        rocks(c, m, t);
        performers(c, hulk, target, t, partial);
        windup(c, m, t);
        blows(c, t);
        barrageDust(c, t);
        current(c, m, t);
        motes(c, t);
    }

    // ------------------------------------------------------------------ the mountain
    /** Boxes: x0, y0, z0, x1, y1, z1, colour. */
    private static final List<float[]> WHOLE = new ArrayList<>(), SPLIT = new ArrayList<>();
    static {
        double cell = 6;
        int haze = 0xa9bdd6;
        for (double x = -MOUNTAIN_HALF; x < MOUNTAIN_HALF; x += cell) for (double z = MOUNTAIN_Z; z < MOUNTAIN_Z + MOUNTAIN_DEPTH; z += cell) {
            double cx = x + cell / 2, cz = z + cell / 2;
            double nx = cx / MOUNTAIN_HALF, nz = (cz - MOUNTAIN_Z) / MOUNTAIN_DEPTH;
            double profile = (1 - Math.pow(Math.abs(nx), 1.6)) * (.55 + .45 * Math.sin(Math.PI * nz));
            double ridge = .12 * Math.sin(nx * 7 + 1.3) + .08 * Math.sin(nx * 17 + nz * 4);
            double h = MOUNTAIN_HEIGHT * Math.max(.06, Math.min(1.08, profile + ridge + .14 * (hash(cx * .13 + cz * .71) - .5)));
            int rock = mix(hash(cx * .37 + cz * 1.3) < .5 ? 0x8b7660 : 0x7f7a72, haze, .38f);
            int top = mix(h > 30 && hash(cx * 2.1 + cz) > .45 ? 0x6f8d4c : 0x9c8a72, haze, .38f);
            // The whole mountain: one column per cell, a capped top.
            WHOLE.add(box(x, -2, z, x + cell, h - 2.5, z + cell, rock));
            WHOLE.add(box(x - .2, h - 2.5, z - .2, x + cell + .2, h, z + cell + .2, top));
            // Split open: the cells near the line of the punch are cut out of it, wider toward the top,
            // with ragged steps; the rest stand as they were.
            double centre = 2.5 * Math.sin(cz * .05);
            boolean near = Math.abs(cx - centre) < 11 + .3 * h + 10;
            if (!near) {
                SPLIT.add(box(x, -2, z, x + cell, h - 2.5, z + cell, rock));
                SPLIT.add(box(x - .2, h - 2.5, z - .2, x + cell + .2, h, z + cell + .2, top));
                continue;
            }
            double seg = 7;
            for (double y = -2; y < h; y += seg) {
                double y1 = Math.min(h, y + seg), mid = (y + y1) / 2;
                double half = 11 + .3 * mid + 4 * hash(cz * .7 + y * .31 + cx);
                if (Math.abs(cx - centre) - cell * .5 < half) continue;
                int c = y1 >= h ? top : mix(rock, 0x5d544a, (float) (.25 * hash(y + cx)));
                SPLIT.add(box(x, y, z, x + cell, y1, z + cell, c));
            }
        }
        // Rubble spilled out of the ravine onto the plain in front of the mountain.
        for (int i = 0; i < 40; i++) {
            double side = hash(i * 1.9) < .5 ? -1 : 1, s = 2.5 + 5 * hash(i * 2.7);
            double x = side * (6 + 26 * hash(i * 3.3)), z = MOUNTAIN_Z - 2 - 22 * hash(i * 4.1);
            SPLIT.add(box(x - s / 2, -1, z - s / 2, x + s / 2, s * .7, z + s / 2, mix(0x8b7660, 0xa9bdd6, .3f)));
        }
    }
    private static float[] box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
        return new float[]{(float) x0, (float) y0, (float) z0, (float) x1, (float) y1, (float) z1, Float.intBitsToFloat(rgb)};
    }
    static int mix(int a, int b, float k) {
        int r = (int) ((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k), g = (int) ((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k), bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return r << 16 | g << 8 | bl;
    }
    /** The split appears while the air current hides the mountain. */
    private static final float SPLIT_AT = ULT_REVEAL - 12;
    private static void mountain(FilmContext c, Matrix4f m, float t) {
        for (float[] b : t < SPLIT_AT ? WHOLE : SPLIT) FilmFx.cube(c, m, b[0], b[1], b[2], b[3], b[4], b[5], Float.floatToRawIntBits(b[6]));
    }
    /** Rocks breaking off the ravine's walls and falling, a while after it opens. */
    private static void rocks(FilmContext c, Matrix4f m, float t) {
        if (t < SPLIT_AT) return;
        for (int i = 0; i < 46; i++) {
            float start = SPLIT_AT + 6 + 110 * (float) hash(i * 5.3);
            float age = t - start;
            if (age < 0) continue;
            double side = hash(i * 1.1) < .5 ? -1 : 1, y0 = 18 + 55 * hash(i * 2.9);
            double x = side * (11 + .3 * y0 + 2 * hash(i * 7.7)), z = MOUNTAIN_Z + 2 + (MOUNTAIN_DEPTH - 6) * hash(i * 3.7);
            double y = Math.max(0, y0 - .5 * .05 * age * age);
            float s = (float) (1.2 + 2.4 * hash(i * 8.3));
            int rgb = mix(0x7f7266, 0xa9bdd6, .3f);
            FilmFx.cube(c, m, (float) (x - s / 2), (float) y, (float) (z - s / 2), (float) (x + s / 2), (float) y + s, (float) (z + s / 2), rgb);
            if (y <= 0 && age < 200) {
                float since = (float) (age - Math.sqrt(2 * y0 / .05));
                if (since > 0 && since < 40) FilmFx.puff(c, new Vec3(x, 1.5 + since * .05, z), 3 + since * .15, DUST_MID, .35f * (1 - since / 40));
            }
        }
    }

    // ------------------------------------------------------------------ the two of them
    private static void performers(FilmContext c, Player hulk, Entity target, float t, float partial) {
        var mc = Minecraft.getInstance();
        Vec3 at = RagePath.hulk(t);
        FilmFx.shadow(c, at, 1.2, .38f);
        if (hulk != null) {
            var dispatcher = mc.getEntityRenderDispatcher();
            float body = hulk.yBodyRot, bodyO = hulk.yBodyRotO, head = hulk.yHeadRot, headO = hulk.yHeadRotO, xRot = hulk.getXRot(), xRotO = hulk.xRotO;
            hulk.yBodyRot = hulk.yBodyRotO = hulk.yHeadRot = hulk.yHeadRotO = 0;
            hulk.setXRot(0); hulk.xRotO = 0;
            dispatcher.setRenderShadow(false);
            try {
                dispatcher.render(hulk, at.x, at.y, at.z, 0, partial, c.pose(), c.buffers(), LightTexture.FULL_BRIGHT);
            } finally {
                dispatcher.setRenderShadow(mc.options.entityShadows().get());
                hulk.yBodyRot = body; hulk.yBodyRotO = bodyO; hulk.yHeadRot = head; hulk.yHeadRotO = headO; hulk.setXRot(xRot); hulk.xRotO = xRotO;
            }
        }
        if (target == null) return;
        if (cast == null || cast.entity() != target) cast = FilmCast.of(target);
        if (cast == null) return;
        Vec3 feet = RagePath.target(t);
        FilmFx.shadow(c, new Vec3(feet.x, 0, feet.z), .7, .32f * (float) Math.max(0, 1 - feet.y / 3));
        cast.draw(c, feet, targetYaw(t), targetPose(t), 1, 0xffffff);
    }

    // ------------------------------------------------------------------ the barrage
    /** Where the fist meets them for punch i. */
    private static Vec3 contact(int i) {
        Vec3 tg = RagePath.target(ULT_HITS[i]);
        double side = ULT_RIGHT[i] ? -.25 : .25;
        return tg.add(side + .3 * (hash(i * 4.4) - .5), 1.35 + .5 * hash(i * 6.1), -.45);
    }
    /** Each blow: a flash, a ring of pressed air bursting out the far side, dust thrown the way it went. */
    private static void blows(FilmContext c, float t) {
        for (int i = 0; i < ULT_HITS.length; i++) {
            float age = t - ULT_HITS[i];
            if (age < 0 || age > 9) continue;
            Vec3 at = contact(i);
            float p = ULT_POWER[i], fade = 1 - age / 9;
            double r = (.35 + 1.5 * Math.sqrt(age / 9)) * (.6 + .5 * p);
            HulkFx.tiltedRing(c, at.add(0, 0, .15 * age), new Vec3(0, 0, 1), r, .22 + .1 * p, 0xffffff, .8f * fade);
            HulkFx.tiltedRing(c, at.add(0, 0, .15 * age), new Vec3(0, 0, 1), r, .35 + .1 * p, 0xdcd8d0, .5f * fade, FilmFx.SOFT);
            if (age < 2.5f) FilmFx.glow(c, at, .9 + .6 * p, 0xffffff, .9f * (1 - age / 2.5f));
            // A half ring along the ground under them on the heavy ones.
            if (p >= 1) FilmFx.ring(c, new Vec3(at.x, .06, at.z), r * 1.6, .35, 0xd8d2c4, .45f * fade, false);
        }
        // Fists too fast to follow: streaks from the shoulder out to where each fist is.
        float speed = clamp((t - ULT_BARRAGE) / (ULT_STOP - ULT_BARRAGE));
        if (speed > .25f && t < ULT_STOP + 2) {
            Vec3 h = RagePath.hulk(t);
            for (int i = 0; i < ULT_HITS.length; i++) {
                float age = t - ULT_HITS[i];
                if (age < -1.5f || age > 2) continue;
                double side = ULT_RIGHT[i] ? -.85 : .85;
                Vec3 shoulder = h.add(side, 2.15, .2), fist = contact(i);
                FilmFx.streak(c, shoulder, fist, .35, 0xffffff, 0, .45f * speed * (1 - Math.abs(age) / 2), true);
            }
        }
    }
    /** The dust of the barrage: thrown up by every blow and every stamp of his feet, swelling into a cloud, then settling away. */
    private static void barrageDust(FilmContext c, float t) {
        float clear = 1 - clamp((t - ULT_STOP - 4) / 42f);
        if (clear <= 0 || t < ULT_FIRST) return;
        for (int i = 0; i < ULT_HITS.length; i++) {
            float born = ULT_HITS[i];
            for (int j = 0; j < 3; j++) {
                float life = 45 + 35 * (float) hash(i * 3.1 + j);
                float age = t - born;
                if (age < 0 || age > life) continue;
                Vec3 from = j == 0 ? RagePath.hulk(born).add(0, .3, 0) : contact(i).add(0, -.6 * j, 0);
                double a = hash(i * 7.7 + j * 1.3) * Math.PI * 2, speed = .05 + .08 * hash(i + j * 9.1);
                double travel = (1 - Math.exp(-age / 12)) * 12 * speed;
                Vec3 at = from.add(Math.cos(a) * travel, .3 * travel + .02 * age, Math.sin(a) * travel + (j > 0 ? .3 * travel : 0));
                float alpha = .5f * FilmFx.ease(age / 3) * (float) Math.pow(1 - age / life, 1.2) * clear;
                int rgb = j == 0 ? DUST_MID : (i + j) % 2 == 0 ? DUST_LIGHT : DUST_DARK;
                FilmFx.puff(c, at, .5 + 2.2 * Math.sqrt(age / life), rgb, alpha);
            }
        }
        // At the height of it a cloud wraps both of them; only shapes and flashes show through.
        float peak = clamp((t - (ULT_BARRAGE + 30)) / 40f) * clear;
        if (peak <= 0) return;
        Vec3 mid = RagePath.hulk(t).lerp(RagePath.target(t), .5);
        for (int i = 0; i < 34; i++) {
            double a = i * 2.39996 + t * .012 * (i % 2 == 0 ? 1 : -1), r = 1.6 + 2.8 * hash(i * 5.5);
            Vec3 at = mid.add(Math.cos(a) * r, .6 + 2.4 * hash(i * 3.3) + .25 * Math.sin(t * .05 + i), Math.sin(a) * r);
            FilmFx.puff(c, at, 2.4 + 1.8 * hash(i * 9.1), i % 3 == 0 ? DUST_DARK : DUST_LIGHT, .42f * peak);
        }
    }

    // ------------------------------------------------------------------ the wind-up
    private static void windup(FilmContext c, Matrix4f m, float t) {
        float on = window(t, ULT_WINDUP, ULT_PUNCH + 60, 6);
        if (on <= 0) return;
        Vec3 feet = RagePath.hulk(Math.min(t, ULT_PUNCH - 4));
        float grow = clamp((t - ULT_WINDUP) / (ULT_FREEZE - ULT_WINDUP));
        // Cracks running out from under his feet.
        var v = c.buffers().getBuffer(FilmFx.SOFT);
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9 + .4 * hash(i * 2.2), len = (1.5 + 2.5 * hash(i * 4.4)) * grow;
            Vec3 p0 = feet.add(Math.cos(a) * .7, .04, Math.sin(a) * .7);
            Vec3 p1 = p0.add(Math.cos(a + .3 * (hash(i) - .5)) * len * .5, 0, Math.sin(a + .3 * (hash(i) - .5)) * len * .5);
            Vec3 p2 = p1.add(Math.cos(a - .4 * (hash(i * 3) - .5)) * len * .5, 0, Math.sin(a - .4 * (hash(i * 3) - .5)) * len * .5);
            HulkFx.flat(v, m, p0, p1, .22, 0x2a241e, .8f * on);
            HulkFx.flat(v, m, p1, p2, .14, 0x2a241e, .7f * on);
        }
        // Dust lifting off the ground round him, and grit trembling on the spot.
        if (t < ULT_PUNCH) for (int i = 0; i < 14; i++) {
            float life = 30, age = (t - ULT_WINDUP + i * 2.1f) % life;
            double a = hash(i * 7.1) * Math.PI * 2, r = 1 + 1.6 * hash(i * 1.7) + age * .02;
            FilmFx.puff(c, feet.add(Math.cos(a) * r, .2 + age * .03, Math.sin(a) * r), .5 + age * .03, DUST_MID, .35f * grow * (1 - age / life));
        }
        if (t < ULT_FREEZE) for (int i = 0; i < 18; i++) {
            double a = hash(i * 3.9) * Math.PI * 2, r = .9 + 2 * hash(i * 5.7);
            float hop = (float) Math.abs(Math.sin(t * 2.7 + i * 1.3)) * .08f * grow;
            float x = (float) (feet.x + Math.cos(a) * r), z = (float) (feet.z + Math.sin(a) * r), s = .07f + .05f * (float) hash(i);
            FilmFx.cube(c, m, x - s, hop, z - s, x + s, hop + 2 * s, z + s, 0x6b6157);
        }
        // The fist drawn back glows faintly with gamma.
        if (t > ULT_WINDUP + 4 && t < ULT_PUNCH) FilmFx.glow(c, feet.add(-.95, 1.7, -.6), .8 + .6 * grow, HulkFx.GAMMA, .25f * grow);
    }

    // ------------------------------------------------------------------ the air current
    /** How far out the current's front has run. */
    static double front(float age) { return 2 + 172 * (1 - Math.exp(-age / 30)); }
    /** How wide the current is at a distance down the line. */
    private static double width(double z) { return 1.6 + .17 * z; }

    private static void current(FilmContext c, Matrix4f m, float t) {
        float since = t - (ULT_PUNCH + 1);
        if (since < 0) return;
        float gone = 1 - clamp((t - ULT_REVEAL) / 55f);
        if (gone <= 0) return;
        Vec3 origin = RagePath.hulk(ULT_PUNCH).add(0, 2.1, 1.6);
        // The pressure front right out of the fist.
        if (since < 14) {
            float k = since / 14;
            HulkFx.tiltedRing(c, origin.add(0, 0, 1 + 6 * k), new Vec3(0, 0, 1), 1.5 + 6 * k, 1.2, 0xffffff, .9f * (1 - k));
            HulkFx.tiltedRing(c, origin.add(0, 0, 1 + 6 * k), new Vec3(0, 0, 1), 1.5 + 6 * k, 1.6, 0xd9d5cc, .6f * (1 - k), FilmFx.SOFT);
            FilmFx.ring(c, new Vec3(origin.x, .06, origin.z + 2), 2 + 12 * k, 1, 0xd8d2c4, .6f * (1 - k), false);
        }
        // The mass of air and dust: hundreds of puffs let go one after another, each running out down the
        // line, swirling at the edges, slowing, swelling and thinning; two tones each, like painted cloud.
        int count = 320;
        for (int i = 0; i < count; i++) {
            float born = i * .2f, age = since - born;
            if (age < 0) continue;
            double speedK = .7 + .35 * hash(i * 3.1);
            double z = Math.min(front(age) * speedK, front(since));
            double w = width(z);
            double a = hash(i * 1.7) * Math.PI * 2 + age * .025 * (hash(i * 9.3) - .5) * 2, rr = Math.sqrt(hash(i * 2.3));
            double x = origin.x + Math.cos(a) * w * rr * 1.25;
            double y = Math.max(.5, origin.y + Math.sin(a) * w * rr * .55 + w * .3);
            double size = .9 + .16 * z * (.7 + .6 * hash(i * 4.9)) + age * .02;
            float alpha = .62f * FilmFx.ease(age / 3) * gone;
            if (age > 42) alpha *= (float) Math.exp(-(age - 42) / 38);
            if (alpha < .01f) continue;
            Vec3 at = new Vec3(x, y, origin.z + z);
            FilmFx.puff(c, at.add(0, -size * .12, 0), size * 1.02, AIR_SHADE, alpha * .9f);
            FilmFx.puff(c, at.add(0, size * .08, 0), size * .9, AIR, alpha);
        }
        // Long streaks racing along inside it, and grit and stones carried with it.
        double f = front(since);
        if (since < 60) {
            float k = 1 - since / 60;
            for (int i = 0; i < 46; i++) {
                double z = f * (.25 + .75 * hash(i * 6.6 + Math.floor(since * .5))), w = width(z) * .8;
                double a = hash(i * 2.2) * Math.PI * 2;
                Vec3 head = new Vec3(origin.x + Math.cos(a) * w, Math.max(.4, origin.y + Math.sin(a) * w * .5 + w * .25), origin.z + z);
                FilmFx.streak(c, head.subtract(0, 0, 6 + .08 * z), head, .25 + .01 * z, 0xffffff, 0, .55f * k, false);
            }
            for (int i = 0; i < 26; i++) {
                double z = Math.min(f * .9, (since - i * .4) * 2.4);
                if (z < 1) continue;
                double a = hash(i * 7.3) * Math.PI * 2, w = width(z) * .7;
                float x = (float) (origin.x + Math.cos(a) * w), y = (float) Math.max(.3, origin.y + Math.sin(a) * w * .4), zz = (float) (origin.z + z);
                float s = .15f + .25f * (float) hash(i * 3.3);
                FilmFx.cube(c, m, x - s, y - s, zz - s, x + s, y + s, zz + s, 0x7a6e60);
            }
        }
        // A wall of pressed dust where the front is, until it reaches the mountain and spreads.
        if (f < MOUNTAIN_Z + 20) {
            double w = width(f);
            HulkFx.tiltedRing(c, new Vec3(origin.x, origin.y + w * .2, origin.z + f), new Vec3(0, 0, 1), w * .9, w * .25, 0xdfdbd2, .35f * gone, FilmFx.SOFT);
        }
    }

    /** Dust and grit drifting down long after, over the whole scene. */
    private static void motes(FilmContext c, float t) {
        float on = clamp((t - SPLIT_AT) / 30f);
        if (on <= 0) return;
        for (int i = 0; i < 70; i++) {
            double x = (hash(i * 1.3) - .5) * 70, z = 8 + 140 * hash(i * 2.7);
            double fall = (t * .03 + hash(i * 5.1) * 30) % 30;
            Vec3 at = new Vec3(x, 30 - fall, z);
            FilmFx.puff(c, at, .5 + 1.2 * hash(i * 4.4), DUST_MID, .22f * on);
        }
    }
}
