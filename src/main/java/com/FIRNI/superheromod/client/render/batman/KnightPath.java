package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.film.Film;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanUltBeats.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanUltBeats.PLANT;

/**
 * KARA ŞÖVALYE's master timeline (Batman's X), pure functions of the film clock in ticks: the yard, Batman (where, which
 * way, turned how, in what pose, how lit), the grapnel lines, the sticky bomb, the Batwing, the Batarang, the flashes
 * and the camera with its kicks. KnightTarget keeps the target, KnightStage draws it all, DarkKnightFilm adds sound.
 * <p>
 * Stage space: y up, the target starts at the origin facing +z under the lamp. The yard is walled in: the warehouse
 * ahead (+z, where they end up pinned), a tall block behind (-z, where the grapnel comes from), the fire escape on the
 * right (-x) and low roofs on the left (+x). The city lies far below when the film goes up into the sky.
 */
public final class KnightPath {
    private KnightPath() {}

    // ------------------------------------------------------------------ the yard
    public static final double WALL_Z = 14, BACK_Z = -14, EAST_X = -12, WEST_X = 12;
    public static final double WALL_H = 12, BACK_H = 18, EAST_H = 15, WEST_H = 9;
    /** The street lamp: the pole's foot, and the lamp head hanging over the target on its arm. */
    public static final Vec3 POLE = new Vec3(1.5, 0, .9), LAMP = new Vec3(.2, 4.85, .15);
    /** The cage lamp on the warehouse wall over where the target ends up. */
    public static final Vec3 WALL_LAMP = new Vec3(1.0, 8.4, WALL_Z - .4);
    /** The glimpses: crouched on the fire escape (right), on the low roof's edge (left), perched high behind, at the light's edge ahead. */
    static final Vec3 ESCAPE = new Vec3(EAST_X + 1.25, 8.3, 3.2), ROOF_W = new Vec3(WEST_X + .45, WEST_H + .5, -1.2),
            PERCH = new Vec3(2.2, BACK_H + .5, BACK_Z - .3), EDGE = new Vec3(.4, 0, 7.4);
    /** Where he fires the grapnel from (the roof edge behind them), where he lands at the end, where the last hook bites. */
    static final Vec3 SNIPE = new Vec3(-.4, BACK_H + .5, BACK_Z - .35);
    public static final Vec3 LAND = new Vec3(0, 0, -2.2);
    static final Vec3 ANCHOR = new Vec3(-9.5, BACK_H + .5, BACK_Z - .05);
    /** His body is drawn at this scale (like the player model). */
    public static final float BODY = .9375f;
    /** His middle over his feet: he turns over about it. */
    public static final double MIDDLE = .92;

    static float clamp(float x) { return Mth.clamp(x, 0, 1); }
    static float ease(float x) { x = clamp(x); return x * x * (3 - 2 * x); }
    static float k(float t, float a, float b) { return ease((t - a) / (b - a)); }
    public static float window(float t, float in, float out, float soft) { return Math.min(ease((t - in) / soft), ease((out - t) / soft)); }
    static float noise(float x) { return Mth.sin(x * 1.7f) * .5f + Mth.sin(x * 3.1f + 1.3f) * .3f + Mth.sin(x * 7.3f + 4.1f) * .2f; }
    /** Minecraft yaw (radians) that faces from a to b. */
    static float face(Vec3 from, Vec3 to) { return (float) Math.atan2(-(to.x - from.x), to.z - from.z); }
    static Vec3 forward(float yaw) { return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)); }

    // ------------------------------------------------------------------ Batman
    /** Batman at one moment (filled by batman(); one instance, render thread only). */
    public static final class Act {
        public boolean shown;
        /** His feet (the model root), facing (Minecraft yaw, radians), turned over about his middle (pitch: + tips forward; roll). */
        public Vec3 at = Vec3.ZERO;
        public float yaw, pitch, roll;
        public Pose pose;
        /** The cape opened into wings (0..1), the light on him (0 black .. 1 the lamp's full light), the head's turn. */
        public float spread, light, lookYaw, lookPitch;
        /** What his right and left hands hold (BatmanBody.HOLD_*). */
        public int holdRight, holdLeft;
        public float holdArg;
        /** The little light on his forearm (0..1). */
        public float signal;
        /** 0..1: melting into the dark (the stage pours shadow over him). */
        public float dark;
        public Vec3 middle() { return at.add(0, MIDDLE, 0); }
    }
    private static final Act ACT = new Act();

    /** Where the shape over the roofs is (the second beat). */
    public static Vec3 glider(float t) {
        float u = clamp((t - PASS) / 18f);
        return new Vec3(Mth.lerp(u, -28, 28), Mth.lerp(u, 20.5f, 23.5f) + 1.2f * Mth.sin(u * Mth.PI), Mth.lerp(u, 14, 2));
    }
    /** His middle gliding round the target high up (radius widening to give the Batwing room). */
    static Vec3 circling(float t) {
        Vec3 c = KnightTarget.centre(t);
        float r = 3.5f + 3.5f * k(t, 210, 262) + 7f * k(t, LIGHTS, ARRIVE);
        float a = (t - 210) * .034f + 1.3f;
        return c.add(r * Mth.cos(a), -1.3 + .4 * Mth.sin(t * .06f), r * Mth.sin(a));
    }
    static float circlingYaw(float t) {
        Vec3 a = circling(t), b = circling(t + .5f);
        return face(a, b);
    }

    public static Act batman(float t, float time) {
        Act a = ACT;
        a.shown = false; a.pitch = a.roll = 0; a.spread = 0; a.light = .1f; a.lookYaw = a.lookPitch = 0;
        a.holdRight = a.holdLeft = BatmanBody.HOLD_NONE; a.holdArg = 0; a.signal = 0; a.dark = 0;
        Pose base = BatmanMotion.stance(time);
        a.pose = base;
        // ---- the fear: a blink of him, and gone
        if (glimpse(a, t, G1, ESCAPE, BatmanMotion.kneel(base), .08f)) return a;
        if (glimpse(a, t, G2, ROOF_W, cloaked(base), .1f)) return a;
        if (glimpse(a, t, G3, PERCH, perch(base), .06f)) return a;
        if (glimpse(a, t, G4, EDGE, cloaked(base), .3f)) return a;
        if (t >= PASS && t < PASS + 18) {
            Vec3 g = glider(t);
            a.shown = true; a.at = g.subtract(0, MIDDLE, 0); a.yaw = face(glider(t - .5f), g);
            a.pose = BatmanMotion.glide(time, 0); a.spread = 1; a.light = .05f;
            return a;
        }
        // ---- the grapnel from the roof edge behind them
        if (t >= FIRE - 10 && t < LEAP) {
            a.shown = true; a.at = SNIPE; a.light = .06f;
            Vec3 hook = KnightTarget.point(Math.max(t, BITE), 0, .25, -.2);
            a.yaw = face(SNIPE, hook);
            Pose p = BatmanMotion.aimBody(base);
            Vec3 shoulder = SNIPE.add(0, 1.4, 0);
            BatmanMotion.aim(p, 0, toModel(hook.subtract(shoulder), a.yaw), k(t, FIRE - 8, FIRE - 3));
            // The haul: the weight thrown back as the line comes taut.
            float haul = k(t, YANK - 1, YANK + 2) * (1 - k(t, LEAP - 2, LEAP));
            p.add(CROUCH, 3 * haul).add(SHIFT_Z, 1.2f * haul).add(SPINE_PITCH, -.1f * haul);
            a.pose = p;
            a.holdRight = BatmanBody.HOLD_GUN; a.holdArg = t >= FIRE ? 1 : 0;
            return a;
        }
        // ---- the leap onto them, the bomb, under them, the kick
        if (t >= LEAP && t < BLAST) {
            a.shown = true; a.light = .12f;
            if (t < PLANT) {
                float u = clamp((t - LEAP) / (PLANT - LEAP));
                Vec3 end = over(PLANT);
                Vec3 ctrl = SNIPE.add(0, 3.5, 3);
                a.at = bezier(SNIPE, ctrl, end, ease(u * .85f + .15f * u * u));
                a.yaw = 0;
                a.pitch = Mth.HALF_PI * k(t, LEAP + 2, PLANT);
                a.pose = BatmanMotion.strike(base, 0);
                a.spread = .6f * Mth.sin(Mth.PI * u);
                return a;
            }
            if (t < UNDER) {
                // Over them, face down: the right hand slaps the bomb onto their chest; then he swings down past them.
                float u = clamp((t - APEX) / (UNDER - APEX));
                Vec3 top = over(t), bottom = under(t);
                Vec3 at = top.lerp(bottom, ease(u));
                a.at = at.add(1.2 * Mth.sin(Mth.PI * ease(u)), 0, 0);
                a.yaw = 0;
                a.pitch = Mth.HALF_PI + Mth.PI * .5f * ease(u);
                Pose p = base.copy().set(PantherMotion.PLANT, 0).set(SPINE_PITCH, -.1f).set(HEAD_PITCH, .2f);
                float slap = k(t, PLANT - 1, PLANT + 1) * (1 - k(t, PLANT + 3, APEX + 2));
                p.arm(0, SH_FWD, 1.4f).arm(0, ARM_X, -1.5f * slap - .3f).arm(0, ARM_Z, .1f).arm(0, ELBOW, .35f).arm(0, CURL, .3f);
                p.arm(1, ARM_Z, .9f).arm(1, ARM_X, -.4f).arm(1, ELBOW, .5f);
                for (int side = 0; side < 2; side++) p.leg(side, KNEE, .5f + .9f * ease(u)).leg(side, LEG_X, -.8f * ease(u));
                a.pose = p;
                return a;
            }
            // Upside down under them: knees to the chest, then both feet driven up into them.
            a.at = under(t);
            a.yaw = 0;
            a.pitch = Mth.PI;
            float drive = k(t, KICK - 2, KICK);
            Pose p = base.copy().set(PantherMotion.PLANT, 0).set(SPINE_PITCH, .25f * (1 - drive)).set(HEAD_PITCH, -.5f);
            for (int side = 0; side < 2; side++) {
                p.leg(side, LEG_X, Mth.lerp(drive, -1.7f, .05f)).leg(side, KNEE, Mth.lerp(drive, 2.2f, 0)).leg(side, ANKLE, -.3f).leg(side, LEG_Z, .06f);
                p.arm(side, ARM_Z, 1.1f).arm(side, ARM_X, .3f).arm(side, ELBOW, .4f).arm(side, CURL, .9f);
            }
            a.pose = p;
            return a;
        }
        // ---- thrown up after them by the blast, turning upright, opening into the glide round them
        if (t >= BLAST && t < DROP) {
            // Moonlit up here (and lit by the guns' flicker) so his shape reads against the sky.
            a.shown = true; a.light = Math.min(1, .22f + .2f * k(t, 222, 250) + .35f * gunLight(t));
            Vec3 target = KnightTarget.centre(t);
            float lag = clamp((t - BLAST) / 14f);
            Vec3 follow = target.add(1.6 * ease(lag), -1.2 - 2.6 * ease(lag), 1.0 * ease(lag));
            float blend = k(t, 214, 236);
            Vec3 mid = follow.lerp(circling(t), blend);
            a.at = mid.subtract(0, MIDDLE, 0);
            a.yaw = Mth.wrapDegrees((float) Math.toDegrees(circlingYaw(t))) * Mth.DEG_TO_RAD * blend;
            a.pitch = Mth.PI + Mth.PI * k(t, BLAST, 222);
            if (blend > .5f) { a.pitch = 0; a.roll = -.35f * k(t, 226, 240); }
            Pose glide = BatmanMotion.glide(time, 0);
            Pose flung = base.copy().set(PantherMotion.PLANT, 0).set(SPINE_PITCH, .2f);
            for (int side = 0; side < 2; side++) flung.arm(side, ARM_Z, 1.3f).arm(side, ARM_X, -.2f).leg(side, KNEE, .7f).leg(side, LEG_X, -.4f);
            float open = k(t, 216, 232);
            flung.toward(glide, open);
            a.pose = flung;
            a.spread = open;
            if (t >= CALL - 4 && t < LIGHTS + 8) {
                // The forearm: the left arm folded across in front, the right hand pressing the panel on it.
                float w = Math.min(k(t, CALL - 4, CALL + 2), 1 - k(t, LIGHTS, LIGHTS + 8));
                Pose press = a.pose.copy();
                press.arm(1, SH_FWD, .8f).arm(1, ARM_X, -1.35f).arm(1, ARM_Y, -.7f).arm(1, ARM_Z, .1f).arm(1, ELBOW, 1.75f).arm(1, CURL, .9f).arm(1, WRIST_X, 0);
                press.arm(0, SH_FWD, 1.1f).arm(0, ARM_X, -1.15f).arm(0, ARM_Y, -.95f).arm(0, ARM_Z, .05f).arm(0, ELBOW, 1.55f).arm(0, CURL, .4f);
                press.set(HEAD_PITCH, -.5f);
                a.pose.toward(press, w);
                a.spread = Mth.lerp(w, a.spread, .25f);
                a.signal = window(t, SIGNAL, LIGHTS + 6, 2) * (.6f + .4f * Mth.sin(t * 1.4f));
            }
            return a;
        }
        // ---- the dive into the yard, the landing under the lamp
        if (t >= DROP && t < RAISE) {
            a.shown = true;
            if (t < ROOF) {
                float u = clamp((t - DROP) / (ROOF - DROP));
                Vec3 from = circling(DROP), to = LAND.add(0, MIDDLE, 0);
                Vec3 mid = from.lerp(to, Math.pow(u, 1.6)).add(0, 3 * Mth.sin(Mth.PI * u) * (1 - u), 0);
                a.at = mid.subtract(0, MIDDLE, 0);
                a.yaw = Mth.PI;
                float flare = k(t, ROOF - 4, ROOF);
                a.pose = BatmanMotion.glide(time, 1 - flare);
                a.pose.toward(BatmanMotion.kneel(base), flare);
                a.spread = (1 - flare) * .8f;
                a.light = .08f + .3f * flare;
                return a;
            }
            a.at = LAND; a.yaw = Mth.PI; a.light = .35f;
            Pose land = BatmanMotion.kneel(base);
            land.toward(base, k(t, ROOF + 2, RAISE - 1));
            a.pose = land;
            return a;
        }
        // ---- the throw without looking, the calm, the turn, the grapnel, gone
        if (t >= RAISE && t < HAUL + 14) {
            a.shown = true; a.at = LAND; a.light = .35f;
            float turn = k(t, TURN, TURN + 7);
            a.yaw = Mth.lerp(turn, Mth.PI, face(LAND, ANCHOR));
            Pose p = base.copy();
            // The hand up, the Batarang in it; at THROW the flick back over the shoulder.
            float up = k(t, RAISE, RAISE + 6) * (1 - k(t, THROW + 3, HIT + 2));
            float flick = k(t, THROW - 1.5f, THROW + .5f);
            Pose raised = base.copy();
            raised.arm(0, SH_UP, .8f).arm(0, ARM_X, Mth.lerp(flick, -2.75f, -3.45f)).arm(0, ARM_Y, .15f).arm(0, ARM_Z, .28f)
                    .arm(0, ELBOW, Mth.lerp(flick, .55f, .1f)).arm(0, WRIST_X, Mth.lerp(flick, .35f, -.9f)).arm(0, CURL, Mth.lerp(flick, .8f, .2f));
            raised.set(CHEST_YAW, -.06f * flick).set(HEAD_PITCH, .04f);
            p.toward(raised, up);
            if (t >= RAISE + 4 && t < THROW) a.holdRight = BatmanBody.HOLD_BATARANG;
            // The grapnel: drawn, laid along the line up to the bite, fired; hauled away.
            if (t >= GUN) {
                Pose gun = BatmanMotion.aimBody(base);
                Vec3 shoulder = LAND.add(0, 1.4, 0);
                BatmanMotion.aim(gun, 0, toModel(ANCHOR.subtract(shoulder), a.yaw), 1);
                p.toward(gun, k(t, GUN, GUN + 5));
                a.holdRight = BatmanBody.HOLD_GUN; a.holdArg = t >= FIRE2 ? 1 : 0;
                a.lookPitch = -.55f * k(t, GUN, GUN + 5);
            }
            if (t >= HAUL) {
                float d = t - HAUL;
                double s = .45 * d * d;
                Vec3 line = ANCHOR.subtract(LAND.add(0, 1.4, 0)).normalize();
                a.at = LAND.add(line.scale(s));
                Pose pull = BatmanMotion.pull(base, time);
                BatmanMotion.aim(pull, 0, toModel(ANCHOR.subtract(a.at.add(0, 1.4, 0)), a.yaw), 1);
                p.toward(pull, k(t, HAUL, HAUL + 3));
                a.spread = .3f * k(t, HAUL, HAUL + 4);
                if (a.at.distanceTo(LAND) > 30) a.shown = false;
            }
            a.pose = p;
            return a;
        }
        return a;
    }
    /** How each glimpse ends: he melts into the dark, a burst of smoke, or gone in the blink of an eye. */
    public static final int VANISH_DARK = 0, VANISH_SMOKE = 1, VANISH_BLINK = 2;
    static final int[] GLIMPSES = {G1, G2, G3, G4};
    static final int[] VANISH = {VANISH_DARK, VANISH_SMOKE, VANISH_BLINK, VANISH_SMOKE};
    static final Vec3[] SPOTS = {ESCAPE, ROOF_W, PERCH, EDGE};
    /** Ticks the melting into the dark takes (the others are over in a tick or two). */
    public static final int DARK_TICKS = 9;
    /** The vanish going on at t: {kind, ticks since it began (at GONE), glimpse}, or null. */
    public static float[] vanish(float t) {
        for (int i = 0; i < GLIMPSES.length; i++) {
            float d = t - (GLIMPSES[i] + GONE);
            if (d >= -2 && d < 30) return new float[]{VANISH[i], d, i};
        }
        return null;
    }
    public static Vec3 spot(int glimpse) { return SPOTS[glimpse]; }
    /** One glimpse: there from start, the target's eyes find him at SEEN, at GONE he goes the way this glimpse goes. */
    private static boolean glimpse(Act a, float t, float start, Vec3 at, Pose pose, float light) {
        int kind = VANISH_SMOKE;
        for (int i = 0; i < GLIMPSES.length; i++) if (GLIMPSES[i] == start) kind = VANISH[i];
        float d = t - (start + GONE);
        float end = kind == VANISH_DARK ? DARK_TICKS * .75f : 1;
        if (t < start || d >= end) return false;
        Vec3 target = Vec3.ZERO.add(0, 1, 0);
        Vec3 away = new Vec3(at.x - target.x, 0, at.z - target.z).normalize();
        a.shown = true;
        a.yaw = face(at, target);
        a.pose = pose;
        a.light = light;
        a.at = at;
        a.lookPitch = (float) Math.atan2(at.y + 1.5 - target.y, Math.sqrt((at.x) * (at.x) + (at.z) * (at.z))) * .6f;
        if (kind == VANISH_DARK && d > 0) {
            // Melting into the dark: no light on him any more, sinking back a little into the shadow it pours out of.
            float k = clamp(d / (DARK_TICKS * .75f));
            a.dark = k;
            a.light = light * (1 - k);
            a.at = at.add(away.scale(.35 * k)).add(0, -.12 * k, 0);
        }
        return true;
    }
    /** Standing wrapped in the cape: the arms in close, the head a little down. */
    private static Pose cloaked(Pose base) {
        Pose p = base.copy().set(HEAD_PITCH, .12f);
        for (int side = 0; side < 2; side++) p.arm(side, ARM_Z, .08f).arm(side, ARM_X, -.1f).arm(side, ELBOW, .5f).arm(side, SH_FWD, .5f);
        return p;
    }
    /** Perched on a ledge like a gargoyle: deep crouch, forearms over the knees. */
    private static Pose perch(Pose base) {
        Pose p = base.copy().set(CROUCH, 9.2f).set(SPINE_PITCH, .55f).set(CHEST_PITCH, .15f).set(HEAD_PITCH, -.55f).set(NECK, .8f);
        for (int side = 0; side < 2; side++) {
            p.leg(side, LEG_Z, .22f).leg(side, LEG_Y, .25f);
            p.arm(side, SH_FWD, 1.2f).arm(side, ARM_X, -.8f).arm(side, ARM_Z, .15f).arm(side, ELBOW, .9f).arm(side, CURL, .8f);
        }
        return p;
    }
    /** His feet when over the target (face down, his body a little over theirs) and under them (upside down). */
    private static Vec3 over(float t) { return KnightTarget.centre(t).add(0, 1.05 - MIDDLE, 0); }
    private static Vec3 under(float t) {
        float drive = k(t, KICK - 2, KICK);
        return KnightTarget.centre(t).add(0, -1.75 + .55 * drive - MIDDLE, 0);
    }
    static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, double u) {
        double v = 1 - u;
        return a.scale(v * v).add(b.scale(2 * v * u)).add(c.scale(u * u));
    }
    /** A stage direction in his model's root space (+y down, -z in front), for one facing. */
    static float[] toModel(Vec3 d, float yaw) {
        Vector3f v = new Vector3f((float) d.x, (float) d.y, (float) d.z);
        if (v.lengthSquared() < 1e-8f) return new float[]{0, 0, -1};
        v.normalize();
        new Quaternionf().rotateY(-(Mth.PI - yaw)).transform(v);
        return new float[]{-v.x, -v.y, v.z};
    }

    // ------------------------------------------------------------------ lines, bomb, Batarang
    /** The first grapnel: from his gun to the hook (null when not out); the hook flies FIRE..BITE. */
    public static Vec3 hook(float t) {
        if (t < FIRE || t >= LEAP + 2) return null;
        Vec3 bite = KnightTarget.point(Math.max(t, BITE), 0, .25, -.2);
        if (t >= BITE) return bite;
        return SNIPE.add(0, 1.45, .3).lerp(bite, clamp((t - FIRE) / (BITE - FIRE)));
    }
    /** The last grapnel: the hook flying FIRE2..BITE2 to the roof edge (null when not out). */
    public static Vec3 hook2(float t) {
        if (t < FIRE2 || t > CABLE) return null;
        Vec3 from = LAND.add(0, 1.5, 0);
        return from.lerp(ANCHOR, Math.pow(clamp((t - FIRE2) / (BITE2 - FIRE2)), .8));
    }
    /** The bomb on their chest, PLANT..BLAST. */
    public static Vec3 bomb(float t) {
        if (t < PLANT || t >= BLAST) return null;
        return KnightTarget.point(t, -.05, .35, .2);
    }
    public static Vec3 blastAt() { return KnightTarget.point(BLAST, -.05, .35, .2); }
    /** The Batarang (null when not out): thrown back over his shoulder, it curves out and meets them; then it carries them onto the wall and stays in it. */
    public static Vec3 batarang(float t) {
        if (t < THROW) return null;
        if (t < HIT) return batarangAlong((t - THROW) / (HIT - THROW));
        if (t < WALL) return KnightTarget.point(t, .3, .55, .1);
        return stuck();
    }
    /** Its curve from his hand (0) to them (1); a little past either end carries on along it (the chase camera rides it). */
    public static Vec3 batarangAlong(float u) {
        Vec3 from = LAND.add(.42, 2.45, -.05), to = KnightTarget.point(HIT, .3, .55, .1);
        Vec3 ctrl = from.lerp(to, .5).add(2.4, 1.6, -.8);
        return bezier(from, ctrl, to, u);
    }
    /** Where the Batarang sits in the wall: through their jacket at the shoulder. */
    public static Vec3 stuck() { return new Vec3(KnightTarget.PINNED.x - .3, KnightTarget.PINNED.y + .55, WALL_Z - .03); }

    // ------------------------------------------------------------------ the Batwing
    /** Where it comes from (relative to the target): far off and above, along FAR's direction. */
    static final Vec3 FAR = new Vec3(-82, 18, 72);
    /** Its orbit while it fires: radius, height over the target (so it fires down at them), one lap in this many ticks. */
    static final float RADIUS = 11, ABOVE = 5, SPIN = Mth.TWO_PI / 34;
    /** The horizontal way it comes from (unit). */
    static Vec3 farDir() { return new Vec3(FAR.x, 0, FAR.z).normalize(); }
    /**
     * The Batwing at t (null before its lights show or once it is long gone). Two lights far off, then it comes in fast,
     * braking, straight over the target and over the camera that watches it come, into its orbit on the near side; it
     * circles above them firing; then one last run past them and away.
     */
    public static Vec3 batwing(float t) {
        if (t < LIGHTS || t > PASS_BY + 30) return null;
        Vec3 c = KnightTarget.centre(Math.min(t, DROP));
        if (t < ARRIVE) {
            float u = clamp((t - LIGHTS) / (ARRIVE - LIGHTS));
            return c.add(FAR).lerp(orbit(ARRIVE), 1 - Math.pow(1 - u, 1.6));
        }
        if (t < SCANNED) return orbit(t);
        Vec3 from = orbit(SCANNED), pass = KnightTarget.centre(PASS_BY).add(1.8, 1.6, .3);
        Vec3 v = pass.subtract(from).scale(1f / (PASS_BY - SCANNED));
        float d = t - SCANNED;
        return from.add(v.scale(d + (d > PASS_BY - SCANNED ? .04 * Math.pow(d - (PASS_BY - SCANNED), 2) : 0)));
    }
    private static Vec3 orbit(float t) {
        Vec3 c = KnightTarget.centre(t);
        // It enters the orbit on the side away from where it came (past the target, over the camera).
        float a = (float) Math.atan2(-farDir().z, -farDir().x) + (t - ARRIVE) * SPIN;
        return c.add(RADIUS * Mth.cos(a), ABOVE + .5 * Mth.sin(t * .11f), RADIUS * Mth.sin(a));
    }
    /** Its heading (unit), and how hard it banks (radians, + rolls its right wing down). */
    public static Vec3 batwingHeading(float t) {
        Vec3 a = batwing(t - .5f), b = batwing(t + .5f);
        if (a == null || b == null || b.distanceToSqr(a) < 1e-8) return new Vec3(0, 0, 1);
        Vec3 along = b.subtract(a).normalize();
        // Firing, it pivots in the air to keep its nose (and its guns) on them as it circles.
        float lock = window(t, SCAN - 4, SCANNED + 2, 4);
        if (lock <= 0) return along;
        Vec3 at = batwing(t), on = KnightTarget.centre(t).subtract(at).normalize();
        return along.lerp(on, lock).normalize();
    }
    public static float batwingBank(float t) {
        if (t < ARRIVE - 2 || t > SCANNED + 2) return 0;
        // Hovering on its guns it banks only a little.
        return .85f * window(t, ARRIVE - 2, SCANNED + 2, 5) * (1 - .65f * window(t, SCAN - 4, SCANNED + 2, 4));
    }
    /** Its two nose guns (its own space: +z the nose, +x its left). */
    public static final float GUN_X = .5f, GUN_Y = -.3f, GUN_Z = 4.1f;
    /** How fast a round flies (blocks per tick). */
    public static final float ROUND_SPEED = 7;
    /** The rounds: one every tick from SCAN to SCANNED (the guns take turns), and a last burst on the pass. */
    public static boolean fires(int tick) {
        return (tick >= SCAN && tick < SCANNED) || (tick >= PASS_BY - 7 && tick < PASS_BY - 1);
    }
    /** 0..1: how hard the guns are going now (the target's limbs jerk with it). */
    public static float gunfire(float t) {
        return Math.max(window(t, SCAN, SCANNED, 3), window(t, PASS_BY - 7, PASS_BY - 1, 1.5f));
    }
    /** The flicker of muzzle flashes and hits on the target (0..1). */
    public static float gunLight(float t) {
        return gunfire(t) * (.55f + .45f * Math.abs(Mth.sin(t * 2.7f)));
    }

    // ------------------------------------------------------------------ the flashes
    /** A cold flash over everything: the bomb, the Batwing tearing past. */
    public static float flash(float t) {
        float f = 0;
        float b = t - BLAST;
        if (b >= 0 && b < 8) f = Math.max(f, (float) Math.exp(-b / 2.2));
        float p = t - PASS_BY;
        if (p >= 0 && p < 6) f = Math.max(f, .35f * (float) Math.exp(-p / 1.8));
        // The Batarang striking them, and the wall taking them: a short white bite each.
        float h = t - HIT;
        if (h >= 0 && h < 4) f = Math.max(f, .3f * (float) Math.exp(-h / 1.2));
        float w = t - WALL;
        if (w >= 0 && w < 5) f = Math.max(f, .5f * (float) Math.exp(-w / 1.4));
        return f;
    }
    /** Distant lightning in the cloud (the backdrop's flash). */
    public static float lightning(float t) {
        float f = 0;
        for (float at : new float[]{22, 96, 148, 268, 358, 498}) {
            float d = t - at;
            if (d >= 0 && d < 10) f = Math.max(f, (d < 1.5f ? 1 : .6f * (float) Math.exp(-(d - 1.5f) / 2)) * (d > 3 && d < 4 ? 1.4f : 1));
        }
        return Math.min(1, f);
    }

    // ------------------------------------------------------------------ the camera
    public static Film.View view(float t) {
        Vec3 tc = KnightTarget.centre(t);
        if (t < 34) {
            // Straight down on the lamp's pool in the rain, craning down into a high angle.
            float u = ease(t / 34);
            return new Film.View(new Vec3(.3, 30, .6).lerp(new Vec3(4.5, 12.5, -8.5), u), new Vec3(0, 0, 0).lerp(new Vec3(0, .9, .5), u), 46 - 6 * u, 0);
        }
        if (t < G1) return new Film.View(new Vec3(.35, 1.45, 1.7).add(0, 0, -.1 * (t - 34) / 6), new Vec3(0, 1.62, 0), 36, 0);
        if (t < PASS) {
            float u = (t - G1) / (PASS - G1);
            return new Film.View(new Vec3(.6, 1.85, -1.15).lerp(new Vec3(.45, 1.85, -.8), u), ESCAPE.add(0, .9, 0), 40 - 5 * u, 0);
        }
        if (t < G2) {
            Vec3 aim = new Vec3(-2, 13, 9).lerp(glider(Mth.clamp(t, PASS, PASS + 18)), .35);
            return new Film.View(new Vec3(1.6, .35, -2.2), aim, 64, 0);
        }
        if (t < G3) {
            float u = (t - G2) / (G3 - G2);
            return new Film.View(new Vec3(-1.05, 1.85, -.9).add(.15 * u, 0, 0), ROOF_W.add(0, 1.2, 0), 40 - 4 * u, 0);
        }
        if (t < G4 - 8) return new Film.View(new Vec3(.6, .25, 2.3), PERCH.add(0, .8, 0).lerp(new Vec3(0, 1.4, 0), .12), 56, -3);
        if (t < BACK) return new Film.View(new Vec3(-.5, 1.9, -1.55), EDGE.add(0, 1.2, 0), 40, 0);
        if (t < FIRE) {
            float u = (t - BACK) / (FIRE - BACK);
            return new Film.View(new Vec3(.2, .55, 3.6).lerp(new Vec3(.2, .5, 2.4), u), new Vec3(0, 1.6, -2).lerp(new Vec3(0, 2.4, -3), u), 50, 0);
        }
        if (t < YANK) return new Film.View(new Vec3(1.7, 1.3, -1.4), new Vec3(0, 1.5, -3.6), 46, 0);
        if (t < PLANT) {
            Vec3 lag = KnightTarget.centre(t - 2);
            float rise = k(t, YANK, PLANT);
            return new Film.View(lag.add(4.8, -1.6 + 2.2 * rise, 2.4), tc.add(0, .3, -.4), 56, 0);
        }
        if (t < UNDER) return new Film.View(tc.add(3.2, .7, .5), tc.add(0, .3, 0), 50, 4);
        if (t < BLAST + 3) {
            Vec3 at = KnightTarget.centre(UNDER);
            return new Film.View(at.add(2.4, -5.8, 1.6), tc.add(0, -.7, 0), 58, 0);
        }
        if (t < 228) {
            Vec3 at = KnightTarget.centre(UNDER).add(2.4, -5.8, 1.6);
            Vec3 bat = batman(t, t).middle();
            float u = k(t, BLAST + 3, 228);
            return new Film.View(at, tc.lerp(bat, .4), 58 - 22 * u, 0);
        }
        if (t < HIGH) {
            // From high above: both rising toward us, the city's grid far below.
            float u = (t - 228) / (HIGH - 228);
            Vec3 top = KnightTarget.centre(Math.min(t, KnightTarget.APEX_T));
            Vec3 bat = batman(t, t).middle();
            return new Film.View(new Vec3(tc.x + 1.2 + 2 * u, top.y + 11, tc.z - 7.5), tc.lerp(bat, .45).add(0, -1, 0), 50, 0);
        }
        // Close on him gliding: first from behind (his back, the wings, the body turning over beyond him), then abreast.
        if (t < 274) return glideShot(t, false);
        if (t < CALL) return glideShot(t, true);
        if (t < LIGHTS) {
            // Close on his forearm.
            Act b = batman(t, t);
            Vec3 m = b.middle(), f = forward(b.yaw), side = f.cross(new Vec3(0, 1, 0));
            return new Film.View(m.add(f.scale(1.25)).add(side.scale(.75)).add(0, .55, 0), m.add(f.scale(.2)).add(0, .2, 0), 36, 0);
        }
        if (t < ARRIVE) {
            // Over the target toward the two lights far off: it grows, comes straight at us and roars over the camera.
            Vec3 dir = farDir(), side = dir.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 aim = tc.add(dir.scale(14)).add(0, 2 + 2 * k(t, ARRIVE - 6, ARRIVE), 0);
            return new Film.View(tc.subtract(dir.scale(8)).add(side.scale(2.2)).add(0, 1.6, 0), aim, 46 + 8 * k(t, ARRIVE - 8, ARRIVE), 0);
        }
        if (t < 352) return new Film.View(tc.add(13, -4, -9), tc.add(0, 1, 0), 58, 0);
        if (t < 376) {
            float a = .7f + (t - 352) * .011f;
            return new Film.View(tc.add(5.5 * Mth.cos(a), .7, 5.5 * Mth.sin(a)), tc.add(0, .3, 0), 48, 0);
        }
        // Him, gliding wide of the guns, the rain of rounds on the body beyond him.
        if (t < SCANNED) return glideShot(t, true);
        if (t < DROP) return new Film.View(tc.add(3.2, -.8, -2.6), tc, 54, 0);
        if (t < ROOF - 2) {
            // Above the falling body, looking down past it: the smoke streams up at us, the yard comes up below.
            return new Film.View(tc.add(1.0, 7, -3.2), tc.add(0, -6, 1.1), 58, 0);
        }
        if (t < RAISE) return new Film.View(new Vec3(2.7, .45, -6.6), new Vec3(0, 1, -2.2), 46, 0);
        if (t < THROW + 1) {
            // Behind him, low: his back, the hand rising with the Batarang; the falling body sweeps over him.
            float w = .55f * k(t, RAISE + 2, THROW);
            Vec3 head = LAND.add(0, 2.1, 0);
            return new Film.View(new Vec3(1.3, .55, 3.8), head.lerp(tc, w), 58, 0);
        }
        if (t < HIT - 1) {
            // Riding just behind the Batarang as it spins up its curve at them.
            float u = (t - THROW) / (HIT - THROW);
            Vec3 behind = batarangAlong(u - .2f), ahead = batarangAlong(u + .15f);
            Vec3 along = ahead.subtract(behind).normalize(), side = along.cross(new Vec3(0, 1, 0)).normalize();
            return new Film.View(behind.add(side.scale(.45)).add(0, .3, 0), ahead.lerp(tc, .3), 62, 0);
        }
        if (t < WALL + 12) {
            // Side on along the wall: the body carried in on the Batarang, slammed into the brick, the sparks.
            Vec3 pin = KnightTarget.PINNED;
            float u = k(t, HIT, WALL);
            return new Film.View(new Vec3(pin.x + 7.2, 3.4, WALL_Z - 4.2), tc.lerp(pin, .5 + .5 * u).add(0, .2, 0), 50 - 4 * u, 0);
        }
        // In front of him; the yard, the light, the wall behind. It holds on after he is gone.
        float u = k(t, 500, STAGE_END);
        Vec3 aim = new Vec3(.3, 3.7, 6);
        return new Film.View(new Vec3(.3, 1.45, -9.2).lerp(new Vec3(.3, 1.6, -8.2), u), aim, 50 - 2 * u, 0);
    }

    /** Close on him gliding high up: behind him and outside the circle, or abreast of him looking in at the target. */
    private static Film.View glideShot(float t, boolean abreast) {
        Act b = batman(t, t);
        Vec3 m = b.middle(), tc = KnightTarget.centre(t), f = forward(b.yaw);
        Vec3 out = new Vec3(m.x - tc.x, 0, m.z - tc.z);
        out = out.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : out.normalize();
        if (!abreast) return new Film.View(m.subtract(f.scale(4.2)).add(out.scale(1.6)).add(0, 1.3, 0), m.lerp(tc, .28).add(0, .2, 0), 52, 0);
        return new Film.View(m.add(out.scale(3.4)).add(f.scale(1.6)).add(0, .4, 0), m.lerp(tc, .12), 46, 0);
    }

    /** Kicks: yaw, pitch, roll, field of view (degrees). */
    public static float[] kick(float t) {
        float yaw = 0, pitch = 0, roll = 0, fov = 0;
        float[][] hits = {{BITE, .8f}, {YANK, 1.6f}, {KICK, 1.4f}, {BLAST, 3.0f}, {ARRIVE - 3, 2.4f}, {PASS_BY, 2.2f}, {ROOF, .9f}, {HIT, 1.2f}, {WALL, 2.4f}};
        for (float[] h : hits) {
            float d = t - h[0];
            if (d < 0 || d > 20) continue;
            float a = h[1] * (float) Math.exp(-d / 4.5f);
            yaw += a * noise(d * 1.9f + h[0]) * 1.2f;
            pitch += a * (noise(d * 2.3f + h[0] * 2) - (d < 2 ? .7f : 0));
            roll += a * noise(d * 1.7f + h[0] * 3) * .7f;
            fov += d < 3 ? h[1] * 1.4f * (1 - d / 3) : 0;
        }
        // The guns: a steady rattle in the camera while they fire.
        float guns = gunfire(t);
        if (guns > 0) { yaw += .25f * guns * noise(t * 2.3f); pitch += .25f * guns * noise(t * 2.9f + 5); }
        // A handheld breath on the yard shots, stronger as the fear builds.
        float hand = .12f + .2f * k(t, G1, BACK);
        if (t < FIRE) { yaw += hand * noise(t * .07f); pitch += hand * noise(t * .05f + 4); }
        return yaw == 0 && pitch == 0 && roll == 0 && fov == 0 ? null : new float[]{yaw, pitch, roll, fov};
    }
}
