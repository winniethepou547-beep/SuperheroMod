package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.hulk.HulkMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.thor.ThorMotion;
import com.FIRNI.superheromod.client.render.zed.ZedMotion;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;

/**
 * Whoever has Magneto's spike in them takes hold of it with both hands where it sticks out and yanks at it: on every
 * click the hands jerk out along it and the body leans back against it, settling back between clicks; when it comes
 * out the last pull carries the arms on out and they let go. The hands are placed by solving the arm (shoulder and
 * elbow) for the real grip points on the spike, so they meet it whichever way it went in. One set of numbers drives
 * every body: the plain player model and each hero's own pose.
 */
public final class SpikePull {
    /** How much of the grip to blend in, the lean (positive forward), and per side (0 right, 1 left) arm x, arm y (positive to his right) and elbow. */
    public static final class Grip {
        public float w, lean;
        public final float[] armX = new float[2], armY = new float[2], elbow = new float[2];
    }
    private SpikePull() {}

    /**
     * The grip for this body, or null when no spike is in it. Shoulders at shoulderY above the feet and shoulderX out,
     * upper arm and forearm lengths in blocks (fore 0 = a straight arm with no elbow, the plain model).
     */
    public static Grip of(Entity e, float partial, float shoulderY, float shoulderX, float upper, float fore) {
        float[] s = MagnetoFx.impaled(e.getId(), partial);
        if (s == null) return null;
        float yawRel = s[0], pitch = s[1], height = s[2], out = s[3], sinceTug = s[4], since = s[5], sinceOut = s[6];
        Grip g = new Grip();
        g.w = FilmFx.ease(since / 4) * (sinceOut < 0 ? 1 : 1 - FilmFx.ease(sinceOut / MagnetoFx.PULL_OUT));
        if (g.w <= .01f) return null;
        // The yank: out fast on a click, back slower; once it is out the last pull carries on.
        float tug = sinceTug < 2 ? Math.max(0, sinceTug) / 2 : Math.max(0, 1 - (sinceTug - 2) / 7);
        tug = FilmFx.ease(tug);
        if (sinceOut >= 0) tug = 1 + .9f * FilmFx.ease(sinceOut / 3);
        // The spike's direction in the body's frame: forward, up, his right.
        double yr = Math.toRadians(yawRel), pr = Math.toRadians(pitch);
        double df = Math.cos(yr) * Math.cos(pr), dr = Math.sin(yr) * Math.cos(pr), du = -Math.sin(pr);
        // Two grips on the part that sticks out (back along it from where it went in).
        double near = .5 + .55 * out + .2 * tug, far = near + .26;
        double[] a = {-dr * near, height - du * near, -df * near}, b = {-dr * far, height - du * far, -df * far};
        double[] right = a[0] >= b[0] ? a : b, left = right == a ? b : a;
        boolean behind = (a[2] + b[2]) < -.1;
        float wobble = .025f * Mth.sin((since + partial) * 2.1f);
        for (int side = 0; side < 2; side++) {
            double[] p = side == 0 ? right : left;
            // From the shoulder to the grip, in the model's frame (+y down, -z front, -x his right).
            double tx = -(p[0] - (side == 0 ? shoulderX : -shoulderX)), ty = -(p[1] - shoulderY), tz = -p[2];
            double h = Math.sqrt(tx * tx + tz * tz), len = Math.sqrt(h * h + ty * ty);
            double theta = Math.atan2(h, ty), bend = 0, lift = 0;
            if (fore > 0) {
                double l = Mth.clamp(len, Math.abs(upper - fore) + .02, upper + fore - .01);
                double inner = Math.acos(Mth.clamp((upper * upper + fore * fore - l * l) / (2 * upper * fore), -1, 1));
                bend = Math.PI - inner;
                lift = Math.acos(Mth.clamp((upper * upper + l * l - fore * fore) / (2 * upper * l), -1, 1));
            }
            if (!behind) {
                g.armX[side] = (float) -(theta - lift) + wobble;
                g.armY[side] = h < 1e-4 ? 0 : (float) Math.atan2(-tx, -tz);
                g.elbow[side] = (float) bend;
            } else {
                // Reaching back for it: the arms go back and down, elbows a little bent.
                g.armX[side] = (float) Math.min(1.3, theta) + wobble;
                g.armY[side] = h < 1e-4 ? 0 : (float) Math.atan2(tx, tz);
                g.elbow[side] = .35f;
            }
        }
        // Hunched over it with the pain; thrown back against the pull on every yank.
        g.lean = behind ? .12f + .22f * Math.min(1, tug) : .14f - .34f * Math.min(1, tug);
        return g;
    }

    // ------------------------------------------------------------------ the bodies
    /** The plain player model (no elbows): true when it posed the arms. */
    public static boolean vanilla(PlayerModel<?> m, Entity e, float partial) {
        Grip g = of(e, partial, 1.375f, .3125f, .68f, 0);
        if (g == null) return false;
        m.rightArm.xRot = Mth.lerp(g.w, m.rightArm.xRot, g.armX[0]); m.rightArm.yRot = Mth.lerp(g.w, m.rightArm.yRot, g.armY[0]); m.rightArm.zRot = Mth.lerp(g.w, m.rightArm.zRot, 0);
        m.leftArm.xRot = Mth.lerp(g.w, m.leftArm.xRot, g.armX[1]); m.leftArm.yRot = Mth.lerp(g.w, m.leftArm.yRot, g.armY[1]); m.leftArm.zRot = Mth.lerp(g.w, m.leftArm.zRot, 0);
        m.head.xRot += .35f * g.w;
        m.rightSleeve.copyFrom(m.rightArm); m.leftSleeve.copyFrom(m.leftArm); m.hat.copyFrom(m.head);
        return true;
    }
    public static void thor(ThorMotion.Pose p, Entity e, float partial) {
        Grip g = of(e, partial, 1.42f, .34f, .31f, .36f);
        if (g == null) return;
        p.rArmX = Mth.lerp(g.w, p.rArmX, g.armX[0]); p.rArmY = Mth.lerp(g.w, p.rArmY, g.armY[0]); p.rArmZ = Mth.lerp(g.w, p.rArmZ, 0); p.rElbow = Mth.lerp(g.w, p.rElbow, g.elbow[0]);
        p.lArmX = Mth.lerp(g.w, p.lArmX, g.armX[1]); p.lArmY = Mth.lerp(g.w, p.lArmY, g.armY[1]); p.lArmZ = Mth.lerp(g.w, p.lArmZ, 0); p.lElbow = Mth.lerp(g.w, p.lElbow, g.elbow[1]);
        p.torsoPitch += g.lean * g.w; p.headPitch += .3f * g.w;
    }
    public static void hulk(HulkMotion.Pose p, Entity e, float partial) {
        float k = Mth.clamp(p.size, 0, 1);
        Grip g = of(e, partial, Mth.lerp(k, 1.4f, 2.2f), Mth.lerp(k, .33f, .87f), Mth.lerp(k, .27f, .58f), Mth.lerp(k, .42f, .95f));
        if (g == null) return;
        p.rArmX = Mth.lerp(g.w, p.rArmX, g.armX[0]); p.rArmY = Mth.lerp(g.w, p.rArmY, g.armY[0]); p.rArmZ = Mth.lerp(g.w, p.rArmZ, 0); p.rElbow = Mth.lerp(g.w, p.rElbow, g.elbow[0]);
        p.lArmX = Mth.lerp(g.w, p.lArmX, g.armX[1]); p.lArmY = Mth.lerp(g.w, p.lArmY, g.armY[1]); p.lArmZ = Mth.lerp(g.w, p.lArmZ, 0); p.lElbow = Mth.lerp(g.w, p.lElbow, g.elbow[1]);
        p.torsoPitch += g.lean * g.w; p.headPitch += .3f * g.w; p.fists = Mth.lerp(g.w, p.fists, 1);
    }
    public static void zed(ZedMotion.Pose p, Entity e, float partial) {
        Grip g = of(e, partial, 1.42f, .34f, .31f, .36f);
        if (g == null) return;
        p.rArmX = Mth.lerp(g.w, p.rArmX, g.armX[0]); p.rArmY = Mth.lerp(g.w, p.rArmY, g.armY[0]); p.rArmZ = Mth.lerp(g.w, p.rArmZ, 0); p.rElbow = Mth.lerp(g.w, p.rElbow, g.elbow[0]);
        p.lArmX = Mth.lerp(g.w, p.lArmX, g.armX[1]); p.lArmY = Mth.lerp(g.w, p.lArmY, g.armY[1]); p.lArmZ = Mth.lerp(g.w, p.lArmZ, 0); p.lElbow = Mth.lerp(g.w, p.lElbow, g.elbow[1]);
        p.torsoPitch += g.lean * g.w; p.headPitch += .3f * g.w; p.run *= 1 - g.w;
    }
    /** Black Panther and Magneto (one joint layout; arm Y there opens outward on each side, so the left one is mirrored). */
    public static void panther(PantherMotion.Pose p, Entity e, float partial) {
        Grip g = of(e, partial, 1.42f, .34f, .3f, .37f);
        if (g == null) return;
        for (int side = 0; side < 2; side++) {
            float y = side == 0 ? g.armY[0] : -g.armY[1];
            p.arm(side, ARM_X, Mth.lerp(g.w, p.arm(side, ARM_X), g.armX[side]));
            p.arm(side, ARM_Y, Mth.lerp(g.w, p.arm(side, ARM_Y), y));
            p.arm(side, ARM_Z, Mth.lerp(g.w, p.arm(side, ARM_Z), 0));
            p.arm(side, ELBOW, Mth.lerp(g.w, p.arm(side, ELBOW), g.elbow[side]));
            p.arm(side, SH_FWD, Mth.lerp(g.w, p.arm(side, SH_FWD), .5f));
            p.arm(side, CURL, Mth.lerp(g.w, p.arm(side, CURL), 1));
        }
        p.add(CHEST_PITCH, g.lean * g.w).add(HEAD_PITCH, .3f * g.w);
    }
}
