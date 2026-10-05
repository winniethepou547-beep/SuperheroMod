package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.core.cinematic.ActorPose.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanUltBeats.*;

/**
 * The target in KARA ŞÖVALYE, as pure functions of the film clock (stage space, see KnightPath): where their middle is,
 * which way they face, how the body is turned over about its middle in the air, the pose, and where they look.
 * <p>
 * Alone under the lamp, nervous; their head snaps to each sound and finds him a blink too late (right, up at the shape
 * over the roofs, left, round behind, front again); they back away from the dark ahead. The grapnel bites their back
 * and rips them up and back (arched, arms flung forward); on their back at the top, flailing; folded by the two-foot
 * kick; spread out by the blast and thrown high; turning over slowly up there; held still in the scan; the fall with
 * flailing limbs; folded by the Batarang and carried onto the wall, where they hang pinned by the jacket, head down.
 */
public final class KnightTarget {
    private KnightTarget() {}

    /** Their middle stands this far over the feet; the body turns about it in the air. */
    public static final double MID = .95;
    /** Where they hang in the air after the launch, and where the Batarang meets them. */
    static final Vec3 HIGH_AT = new Vec3(0, 58, -14), HIT_AT = new Vec3(1.2, 8, 7);
    /** Their middle pinned on the warehouse wall (their back to it). */
    public static final Vec3 PINNED = new Vec3(1.0, 5.6, KnightPath.WALL_Z - .32);

    static float clamp(float x) { return Mth.clamp(x, 0, 1); }
    static float ease(float x) { x = clamp(x); return x * x * (3 - 2 * x); }
    static float k(float t, float a, float b) { return ease((t - a) / (b - a)); }
    static float noise(float x) { return Mth.sin(x * 1.7f) * .5f + Mth.sin(x * 3.1f + 1.3f) * .3f + Mth.sin(x * 7.3f + 4.1f) * .2f; }

    // ------------------------------------------------------------------ where
    public static Vec3 centre(float t) {
        if (t < YANK) {
            // Shifting from foot to foot under the lamp; then backing away from the dark ahead, a step at a time.
            double x = .05 * noise(t * .05f), z = .04 * noise(t * .04f + 2);
            float back = k(t, BACK, BITE + 1);
            z -= 2.6 * back;
            double y = MID - .03 * Math.abs(Mth.sin((t - BACK) * .26f)) * (back > 0 && back < 1 ? 1 : 0);
            // The bite jerks them back.
            z -= .3 * k(t, BITE, YANK);
            return new Vec3(x, y, z);
        }
        if (t < APEX) {
            // Ripped up and back: fast at first, slowing to the top.
            float u = clamp((t - YANK) / (APEX - YANK));
            double y = MID + (15.5 - MID) * (1 - Math.pow(1 - u, 2.4));
            double z = -2.9 - 5.5 * (1 - Math.pow(1 - u, 1.7));
            return new Vec3(0, y, z);
        }
        if (t < KICK) {
            float u = (t - APEX) / (KICK - APEX);
            return new Vec3(0, 15.5 - .45 * u * u, -8.4);
        }
        if (t < 250) {
            // Kicked, then the blast: thrown high over the city, slowing to the top.
            float u = clamp((t - KICK) / (250 - KICK));
            double e = 1 - Math.pow(1 - u, 2.6), s = ease(u);
            return new Vec3(HIGH_AT.x * s, 15.05 + (HIGH_AT.y - 15.05) * e, -8.4 + (HIGH_AT.z + 8.4) * s);
        }
        if (t < DROP) return hover(t);
        if (t < HIT) {
            // The fall from the Batwing's pass: over Batman's head, toward the yard behind him.
            Vec3 d = hover(DROP);
            float u = clamp((t - DROP) / (HIT - DROP));
            return new Vec3(Mth.lerp(u, d.x, HIT_AT.x), d.y - (d.y - HIT_AT.y) * Math.pow(u, 1.4), Mth.lerp(u, d.z, HIT_AT.z));
        }
        if (t < WALL) {
            // Carried by the Batarang onto the wall.
            float u = clamp((t - HIT) / (WALL - HIT));
            return HIT_AT.lerp(PINNED, Math.pow(u, 1.15));
        }
        // Pinned: the jolt, settling, a slight sway.
        float d = t - WALL;
        double jolt = d < 6 ? -.08 * Math.sin(d / 6 * Math.PI) : 0;
        return PINNED.add(.03 * Math.sin(t * .05), jolt - .05 * k(t, WALL, WALL + 8), 0);
    }
    /** Up there, hanging: a slow drift, sinking a little. */
    private static Vec3 hover(float t) {
        float d = t - 250;
        return new Vec3(HIGH_AT.x + .6 * Mth.sin(d * .021f), HIGH_AT.y - 2.5 * k(t, 250, DROP) + .25 * Mth.sin(d * .05f),
                HIGH_AT.z + .6 * (1 - Mth.cos(d * .017f)));
    }
    public static Vec3 feet(float t) { return centre(t).subtract(0, MID, 0); }

    /** Facing (degrees, Minecraft yaw: 0 = +z, positive turns right). */
    public static float yaw(float t) {
        if (t < HIT) return -180 * k(t, G3 - 3, G3 + 5) + 180 * k(t, G4 - 8, G4 + 5);
        return 180 * k(t, HIT, WALL - 1);
    }
    /** Turned over about their middle (degrees about x; positive tips the head toward +z) and rolled (about z). */
    public static float tumble(float t) {
        if (t < BITE) return 0;
        if (t < KICK) return -90 * k(t, BITE, APEX) + 6 * Mth.sin((t - APEX) * .3f) * k(t, APEX, APEX + 3);
        if (t < HIT) return free(t);
        // The Batarang stands them up against the wall: the nearest upright.
        float at = free(HIT);
        float up = Math.round(at / 360f) * 360f;
        return Mth.lerp(k(t, HIT, WALL - 1), at, up);
    }
    /** Turning over in the air: kicked into a spin that slows high up, nearly held in the scan, faster in the fall. */
    private static float free(float t) {
        float d = t - KICK;
        float a = -90 - 300 * (1 - (float) Math.exp(-d / 30)) - .55f * d;
        // Held in the scan: the turn eases off (subtract the drift it would have made).
        a += .4f * (Math.max(0, Math.min(t, SCANNED) - SCAN)) * k(t, SCAN, SCAN + 10);
        // The fall: tumbling faster.
        float fall = Math.max(0, t - DROP);
        a -= 2.6f * fall * fall / (fall + 8);
        return a;
    }
    public static float roll(float t) {
        if (t < KICK) return 0;
        if (t < HIT) return 25 * Mth.sin((t - KICK) * .021f) + (t > DROP ? 40 * Mth.sin((t - DROP) * .09f) * k(t, DROP, DROP + 8) : 0);
        float at = roll(HIT - .01f);
        return at * (1 - k(t, HIT, WALL - 1));
    }

    // ------------------------------------------------------------------ the body
    private static final FilmCast.Track BODY = track();
    private static final ActorPose OUT = new ActorPose();

    public static ActorPose pose(float t) {
        ActorPose p = OUT;
        p.set(BODY.sample(t));
        float tremble = k(t, G2, G2 + 10) * (1 - k(t, YANK, YANK + 2));
        if (t < YANK) {
            // Nerves: a shiver through the hands, quick breaths.
            add(p, RIGHT_LOWER_ARM, 3 * tremble * noise(t * 1.3f), 0, 0);
            add(p, LEFT_LOWER_ARM, 3 * tremble * noise(t * 1.1f + 4), 0, 0);
            add(p, CHEST, 1.5f * Mth.sin(t * .45f) * (.4f + tremble), 0, 0);
            // Backing away: one foot after the other.
            float back = k(t, BACK, BITE + 1);
            if (back > 0 && back < 1) {
                float s = Mth.sin((t - BACK) * .26f) * 22;
                add(p, RIGHT_UPPER_LEG, s, 0, 0); add(p, LEFT_UPPER_LEG, -s, 0, 0);
                add(p, RIGHT_LOWER_LEG, Math.max(0, -s) * .8f, 0, 0); add(p, LEFT_LOWER_LEG, Math.max(0, s) * .8f, 0, 0);
            }
            look(p, t);
        }
        // Flailing in the air: strong when thrown and falling, faint when hanging, nearly still in the scan.
        float flail = 0;
        if (t > YANK && t < HIT) {
            flail = .9f * k(t, YANK, YANK + 4) * (1 - .7f * k(t, KICK - 3, KICK));
            flail += .5f * k(t, BLAST + 2, BLAST + 10) * (1 - .6f * k(t, 240, 270));
            flail *= 1 - .85f * k(t, SCAN, SCAN + 12) * (1 - k(t, SCANNED, PASS_BY));
            flail += 1.1f * k(t, DROP, DROP + 6);
        }
        if (flail > 0) {
            add(p, RIGHT_UPPER_ARM, 30 * flail * noise(t * .31f), 0, 22 * flail * noise(t * .27f + 1));
            add(p, LEFT_UPPER_ARM, 30 * flail * noise(t * .29f + 3), 0, 22 * flail * noise(t * .33f + 5));
            add(p, RIGHT_LOWER_ARM, 25 * flail * Math.abs(noise(t * .4f + 2)), 0, 0);
            add(p, LEFT_LOWER_ARM, 25 * flail * Math.abs(noise(t * .37f + 7)), 0, 0);
            add(p, RIGHT_UPPER_LEG, 26 * flail * noise(t * .25f + 4), 0, 0);
            add(p, LEFT_UPPER_LEG, 26 * flail * noise(t * .23f + 9), 0, 0);
            add(p, RIGHT_LOWER_LEG, 22 * flail * Math.abs(noise(t * .35f + 6)), 0, 0);
            add(p, LEFT_LOWER_LEG, 22 * flail * Math.abs(noise(t * .32f + 8)), 0, 0);
            add(p, HEAD, 10 * flail * noise(t * .2f + 3), 14 * flail * noise(t * .17f), 0);
        }
        if (t > WALL) {
            // Pinned: hanging limp, a feeble twitch of a leg now and then.
            float d = t - WALL;
            add(p, RIGHT_LOWER_LEG, 6 * Math.max(0, noise(d * .12f)), 0, 0);
            add(p, HEAD, 0, 6 * noise(d * .05f + 2), 0);
        }
        return p;
    }
    private static void add(ActorPose p, int joint, float x, float y, float z) {
        p.rot[joint][0] += (float) Math.toRadians(x);
        p.rot[joint][1] += (float) Math.toRadians(y);
        p.rot[joint][2] += (float) Math.toRadians(z);
    }

    private static FilmCast.Track track() {
        ActorPose ready = of().j(RIGHT_UPPER_ARM, -18, 0, 10).j(RIGHT_LOWER_ARM, -30, 0, 0).j(LEFT_UPPER_ARM, -16, 0, -10).j(LEFT_LOWER_ARM, -28, 0, 0)
                .j(RIGHT_UPPER_LEG, -6, 0, 5).j(RIGHT_LOWER_LEG, 8, 0, 0).j(LEFT_UPPER_LEG, 5, 0, -5).j(LEFT_LOWER_LEG, 6, 0, 0).crouch(.04f);
        ActorPose wary = of().j(CHEST, 6, 0, 0).j(RIGHT_UPPER_ARM, -58, 0, 16).j(RIGHT_LOWER_ARM, -78, 0, 0).j(LEFT_UPPER_ARM, -54, 0, -14)
                .j(LEFT_LOWER_ARM, -82, 0, 0).j(RIGHT_UPPER_LEG, -14, 0, 7).j(RIGHT_LOWER_LEG, 20, 0, 0).j(LEFT_UPPER_LEG, 12, 0, -7).j(LEFT_LOWER_LEG, 12, 0, 0).crouch(.12f);
        ActorPose shield = wary.copy().j(RIGHT_UPPER_ARM, -96, 0, 8).j(RIGHT_LOWER_ARM, -64, 0, 0).j(LEFT_UPPER_ARM, -90, 0, -8).j(LEFT_LOWER_ARM, -70, 0, 0)
                .j(CHEST, 10, 0, 0).crouch(.16f);
        ActorPose yanked = of().j(CHEST, -24, 0, 0).j(HEAD, 22, 0, 0).j(RIGHT_UPPER_ARM, -100, 0, 22).j(RIGHT_LOWER_ARM, -18, 0, 0).j(LEFT_UPPER_ARM, -94, 0, -24)
                .j(LEFT_LOWER_ARM, -24, 0, 0).j(RIGHT_UPPER_LEG, -48, 0, 8).j(RIGHT_LOWER_LEG, 30, 0, 0).j(LEFT_UPPER_LEG, -36, 0, -8).j(LEFT_LOWER_LEG, 42, 0, 0);
        ActorPose flail = of().j(CHEST, -10, 0, 0).j(HEAD, 14, 0, 0).j(RIGHT_UPPER_ARM, -40, 0, 78).j(RIGHT_LOWER_ARM, -30, 0, 0).j(LEFT_UPPER_ARM, -30, 0, -84)
                .j(LEFT_LOWER_ARM, -36, 0, 0).j(RIGHT_UPPER_LEG, -30, 0, 14).j(RIGHT_LOWER_LEG, 50, 0, 0).j(LEFT_UPPER_LEG, -12, 0, -16).j(LEFT_LOWER_LEG, 34, 0, 0);
        ActorPose folded = of().j(CHEST, 36, 0, 0).j(HEAD, 26, 0, 0).j(RIGHT_UPPER_ARM, -64, 0, 30).j(RIGHT_LOWER_ARM, -20, 0, 0).j(LEFT_UPPER_ARM, -60, 0, -30)
                .j(LEFT_LOWER_ARM, -22, 0, 0).j(RIGHT_UPPER_LEG, -72, 0, 10).j(RIGHT_LOWER_LEG, 56, 0, 0).j(LEFT_UPPER_LEG, -66, 0, -10).j(LEFT_LOWER_LEG, 60, 0, 0);
        ActorPose spread = of().j(CHEST, -16, 0, 0).j(HEAD, -20, 0, 0).j(RIGHT_UPPER_ARM, -10, 0, 118).j(RIGHT_LOWER_ARM, -8, 0, 0).j(LEFT_UPPER_ARM, -10, 0, -118)
                .j(LEFT_LOWER_ARM, -10, 0, 0).j(RIGHT_UPPER_LEG, 4, 0, 30).j(RIGHT_LOWER_LEG, 10, 0, 0).j(LEFT_UPPER_LEG, 4, 0, -30).j(LEFT_LOWER_LEG, 12, 0, 0);
        ActorPose limp = of().j(CHEST, 4, 0, 0).j(HEAD, 12, 0, 0).j(RIGHT_UPPER_ARM, -24, 0, 66).j(RIGHT_LOWER_ARM, -34, 0, 0).j(LEFT_UPPER_ARM, -18, 0, -70)
                .j(LEFT_LOWER_ARM, -30, 0, 0).j(RIGHT_UPPER_LEG, -14, 0, 16).j(RIGHT_LOWER_LEG, 28, 0, 0).j(LEFT_UPPER_LEG, -4, 0, -18).j(LEFT_LOWER_LEG, 20, 0, 0);
        ActorPose held = spread.copy().j(HEAD, -6, 0, 0).j(RIGHT_UPPER_ARM, -14, 0, 96).j(LEFT_UPPER_ARM, -14, 0, -96).j(RIGHT_UPPER_LEG, 0, 0, 20).j(LEFT_UPPER_LEG, 0, 0, -20);
        ActorPose falling = flail.copy().j(RIGHT_UPPER_ARM, -150, 0, 40).j(LEFT_UPPER_ARM, -140, 0, -44).j(HEAD, -18, 0, 0);
        ActorPose struck = folded.copy().j(CHEST, 30, 0, 0).j(RIGHT_UPPER_ARM, -40, 0, 50).j(LEFT_UPPER_ARM, -36, 0, -54).j(RIGHT_UPPER_LEG, -30, 0, 8).j(LEFT_UPPER_LEG, -26, 0, -8);
        ActorPose slammed = of().j(CHEST, -8, 0, 0).j(HEAD, -24, 0, 0).j(RIGHT_UPPER_ARM, -6, 0, 70).j(RIGHT_LOWER_ARM, -20, 0, 0).j(LEFT_UPPER_ARM, -6, 0, -64)
                .j(LEFT_LOWER_ARM, -24, 0, 0).j(RIGHT_UPPER_LEG, -8, 0, 12).j(RIGHT_LOWER_LEG, 16, 0, 0).j(LEFT_UPPER_LEG, -4, 0, -12).j(LEFT_LOWER_LEG, 20, 0, 0);
        ActorPose pinned = of().j(CHEST, 10, 0, 0).j(HEAD, 34, 0, 0).j(RIGHT_UPPER_ARM, -4, 0, 22).j(RIGHT_LOWER_ARM, -14, 0, 0).j(LEFT_UPPER_ARM, -30, 0, -40)
                .j(LEFT_LOWER_ARM, -12, 0, 0).j(RIGHT_UPPER_LEG, -6, 0, 6).j(RIGHT_LOWER_LEG, 14, 0, 0).j(LEFT_UPPER_LEG, 2, 0, -5).j(LEFT_LOWER_LEG, 8, 0, 0);
        return new FilmCast.Track().key(0, 0, ready).key(G2 + 8, 14, wary).key(G4 + SEEN + 2, 6, shield).key(BITE + 1, 1.5f, yanked)
                .key(APEX, 10, flail).key(KICK, 3, folded).key(BLAST + 2, 3, spread).key(270, 40, limp).key(SCAN + 10, 12, held)
                .key(SCANNED + 4, 8, limp).key(DROP + 4, 4, falling).key(HIT, 1.5f, struck).key(WALL, 1, slammed).key(WALL + 18, 14, pinned);
    }

    // ------------------------------------------------------------------ where they look (head and chest, before the yank)
    /** The look as keys: time, yaw relative to the body (degrees, positive right), pitch (degrees, negative up). */
    private static final float[][] LOOK = {
            {0, 0, 4}, {G1 - 3, 0, 4}, {G1 + SEEN, 74, -32}, {PASS - 3, 74, -32},
            {G2 - 4, -62, -26}, {G2 + SEEN, -96, -37}, {G3 - 3, -96, -37}, {G3 + SEEN, 9, -48}, {G4 - 8, 9, -48},
            {G4 + SEEN, -3, 2}, {BACK, 0, 8}, {YANK, 0, 8}};

    private static void look(ActorPose p, float t) {
        float yaw = 0, pitch = 0;
        for (int i = 0; i < LOOK.length - 1; i++) {
            float[] a = LOOK[i], b = LOOK[i + 1];
            if (t < b[0] || i == LOOK.length - 2) {
                float u = k(t, a[0], b[0]);
                yaw = Mth.lerp(u, a[1], b[1]);
                pitch = Mth.lerp(u, a[2], b[2]);
                break;
            }
        }
        // The shape over the roofs: they follow it across the sky.
        float follow = Math.min(k(t, PASS - 3, PASS + 2), 1 - k(t, PASS + 16, G2 + 2));
        if (follow > 0) {
            Vec3 at = KnightPath.glider(Mth.clamp(t, PASS, PASS + 18)).subtract(centre(t).add(0, .7, 0));
            float bodyYaw = yaw(t);
            float aYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-at.x, at.z)) - bodyYaw);
            float aPitch = (float) -Math.toDegrees(Math.atan2(at.y, Math.sqrt(at.x * at.x + at.z * at.z)));
            yaw = Mth.lerp(follow, yaw, aYaw);
            pitch = Mth.lerp(follow, pitch, aPitch);
        }
        // Small nervous glances in between.
        yaw += 6 * noise(t * .045f + 1);
        float head = Mth.clamp(yaw, -72, 72), chest = Mth.clamp(yaw - head, -34, 34);
        add(p, HEAD, Mth.clamp(pitch * .8f, -45, 30), head, 0);
        add(p, CHEST, Math.min(0, pitch * .2f), chest, 0);
    }

    // ------------------------------------------------------------------ light
    /** How the target is lit: the lamp's warm pool, the cold night, the scan's blue, the flashes; RGB packed. */
    public static int tint(float t) {
        Vec3 c = centre(t);
        float r = .15f, g = .16f, b = .23f;
        // The lamp, warm and hard, from above.
        double dx = c.x - KnightPath.LAMP.x, dz = c.z - KnightPath.LAMP.z;
        float lamp = (float) Math.exp(-(dx * dx + dz * dz) / 6.5) * (c.y < KnightPath.LAMP.y ? 1 : 0);
        r += 1.0f * lamp; g += .74f * lamp; b += .44f * lamp;
        // High up: cold, from the hidden moon.
        float high = (float) Mth.clamp((c.y - 12) / 20, 0, 1);
        r += .12f * high; g += .14f * high; b += .22f * high;
        // The Batwing's scan.
        float scan = KnightPath.scanLight(t);
        r += .1f * scan; g += .35f * scan; b += .5f * scan;
        // The wall lamp over the pin.
        Vec3 w = KnightPath.WALL_LAMP;
        float wall = (float) Math.exp(-c.distanceToSqr(w) / 14);
        r += .75f * wall; g += .6f * wall; b += .42f * wall;
        float flash = KnightPath.flash(t);
        r += .6f * flash; g += .7f * flash; b += .85f * flash;
        return (int) (Mth.clamp(r, 0, 1) * 255) << 16 | (int) (Mth.clamp(g, 0, 1) * 255) << 8 | (int) (Mth.clamp(b, 0, 1) * 255);
    }

    /** A point on them (the body turned over about its middle): side/up/front in blocks of the upright body. */
    public static Vec3 point(float t, double right, double up, double front) {
        float yaw = (float) Math.toRadians(yaw(t)), a = (float) Math.toRadians(tumble(t)), r = (float) Math.toRadians(roll(t));
        // Upright body frame: front from the yaw, right = front x up.
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -fz, rz = fx;
        Vec3 v = new Vec3(rx * right + fx * front, up, rz * right + fz * front);
        // Then the stage-space turns, applied as the drawing does: about x, then about z.
        v = rotX(v, a);
        v = rotZ(v, r);
        return centre(t).add(v);
    }
    static Vec3 rotX(Vec3 v, float a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
    }
    static Vec3 rotZ(Vec3 v, float a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z);
    }
}
