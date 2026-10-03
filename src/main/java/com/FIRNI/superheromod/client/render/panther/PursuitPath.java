package com.FIRNI.superheromod.client.render.panther;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * THE FINAL PURSUIT as numbers, shared by everything that draws or films it: the time warp (scene time against
 * film time: the held breath before the release and the slow motion after it), the car (driving, weaving, its
 * suspension answering the road and every blow; after the release the nose slammed into the asphalt, the vault
 * over it and the free flight, integrated as a rigid body: gravity, its own inertia, the end-over-end turn about
 * its middle axis drifting into a roll as such turns do), the car behind, the traffic, where Panther and the
 * gunman are, and what has happened to the roof and the glass.
 *
 * Stage space: the road runs along +z; +x is to the left looking down it; y up, the asphalt at y 0. His car keeps
 * to the fast lane by the median. Car space: the same axes on the car, origin on the ground between its wheels.
 * Scene times before ULT_BOOM equal film times (PantherAction beats); after it, use scene() and since().
 */
public final class PursuitPath {
    private PursuitPath() {}

    // ------------------------------------------------------------------ the road
    public static final float LANE_W = 3.5f, MEDIAN = .25f, ROAD_HALF = MEDIAN + 3 * LANE_W, WALK = 3.8f;
    /** Lane centres on his side (0 = the fast lane by the median) and on the far side. */
    public static float lane(int i) { return -(MEDIAN + LANE_W * (i + .5f)); }
    public static float oncoming(int i) { return MEDIAN + LANE_W * (i + .5f); }

    // ------------------------------------------------------------------ the car (car space)
    /**
     * Car space is drawn S times larger in the world: the cars are built to a real car's measurements, but the
     * players' bodies (a long body and a big head on short legs) only sit in one sized like this.
     */
    public static final float S = 1.35f;
    public static final float HALF_W = .93f, LENGTH = 4.7f, ROOF_Y = 1.42f, WHEEL_R = .34f, AXLE_F = 1.42f, AXLE_R = -1.38f, TRACK = .80f;
    /** The roof: the glass panel over the front seats, the metal over the back seat he tears away, the B-pillar. */
    public static final float SUNROOF_F = .36f, SUNROOF_B = -.08f, TEAR_F = -.12f, TEAR_B = -.82f, B_PILLAR = -.20f, ROOF_F = .42f;
    /** Where the driver's and the gunman's hips sit (car space). */
    public static final Vec3 DRIVER = new Vec3(.40, .44, -.06), GUNMAN = new Vec3(.42, .40, -.92);
    /** The model's hips above its feet origin (the player model's 12 px at the renderer's 15/16 scale). */
    public static final float BODY_SCALE = .9375f, HIP_HEIGHT = BODY_SCALE * .751f;
    static final float CAR_MASS = 1500;
    /** The centre of mass in car space. */
    static final Vec3 COM = new Vec3(0, .60, -.05);
    /** The front bumper's lower edge: what digs into the asphalt when the release slams the nose down. */
    static final Vec3 NOSE = new Vec3(0, .20, 2.36);
    /** The rear tyres' contact patch: the slam pitches the car about it until the nose meets the road. */
    static final Vec3 REAR_CONTACT = new Vec3(0, 0, AXLE_R);
    /** How far the slam pitches the nose down before it touches the road. */
    static final float TOUCH_PITCH = (float) Math.asin(NOSE.y / (NOSE.z - AXLE_R));

    // ------------------------------------------------------------------ the time warp
    /** Scene time of the release (film time ULT_BOOM, give or take the held breath before it). */
    public static final float BOOM = 466;
    private static final float STEP = .02f;
    private static final float[] REAL;
    /** How fast the scene runs at scene time tau (1 = real time). */
    static float speedAt(float tau) {
        if (tau < 462) return 1;
        if (tau < 464.5f) return Mth.lerp(ease((tau - 462) / 2.5f), 1, .35f);
        if (tau < 465.6f) return .35f;
        if (tau < BOOM) return Mth.lerp(ease((tau - 465.6f) / .4f), .35f, 1);
        float d = tau - BOOM, c = CRASH;
        if (d < 2) return 1;
        if (d < 5.75f) return Mth.lerp(ease((d - 2) / 3.75f), 1, .25f);
        if (d < 23.25f) return .25f;
        if (d < 29.5f) return Mth.lerp(ease((d - 23.25f) / 6.25f), .25f, 1);
        if (d < c - 13) return 1;
        if (d < c - 8.5f) return Mth.lerp(ease((d - c + 13) / 4.5f), 1, .5f);
        if (d < c - 2) return .5f;
        if (d < c) return Mth.lerp(ease((d - c + 2) / 2), .5f, 1);
        return 1;
    }
    /** Scene time at film time t. */
    public static float scene(float t) {
        if (t <= 0) return t;
        int lo = 0, hi = REAL.length - 1;
        if (t >= REAL[hi]) return hi * STEP + (t - REAL[hi]);
        while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (REAL[mid] <= t) lo = mid; else hi = mid; }
        float a = REAL[lo], b = REAL[hi];
        return (lo + (t - a) / Math.max(1e-6f, b - a)) * STEP;
    }
    /** Film time at scene time tau. */
    public static float real(float tau) {
        if (tau <= 0) return tau;
        int i = (int) (tau / STEP);
        if (i >= REAL.length - 1) return REAL[REAL.length - 1] + (tau - (REAL.length - 1) * STEP);
        return Mth.lerp(tau / STEP - i, REAL[i], REAL[i + 1]);
    }
    /** Scene ticks since the release at film time t (negative before it). */
    public static float since(float t) { return scene(t) - BOOM; }
    /** Film time at which the scene is d ticks past the release. */
    public static float realSince(float d) { return real(BOOM + d); }

    public static float ease(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    public static float clamp(float x) { return Mth.clamp(x, 0, 1); }

    // ------------------------------------------------------------------ the car on the road
    public static final float V0 = 1.30f, V1 = 1.45f;
    /** How far down the road the car is at scene time tau (it gathers speed for the first four seconds). */
    static double driveZ(float tau) {
        if (tau < 80) { float u = tau / 80; return V0 * tau + (V1 - V0) * 80 * (u * u * u - u * u * u * u / 2); }
        return V1 * tau - 6;
    }
    static float driveSpeed(float tau) { return V0 + (V1 - V0) * ease(tau / 80); }
    static float driveAccel(float tau) { if (tau <= 0 || tau >= 80) return 0; float u = tau / 80; return (V1 - V0) * (6 * u - 6 * u * u) / 80; }
    /** Across the road: the middle lane, then over into the fast lane past a truck; a jerk away when the window bursts. */
    static double driveX(float tau) {
        double x = lane(1) + (lane(0) - lane(1)) * ease((tau - 70) / 30);
        x += .32 * swerve(tau, 157, 16) + .12 * swerve(tau, 372, 14);
        x += .045 * Math.sin(tau * .05) + .03 * Math.sin(tau * .13 + 1);
        return x;
    }
    /** A jerk to one side and back, smooth, over len ticks. */
    private static double swerve(float tau, float at, float len) {
        float u = (tau - at) / len;
        if (u <= 0 || u >= 1) return 0;
        return Math.sin(Math.PI * u) * Math.sin(Math.PI * u) * (u < .5f ? 1 : 1 - 2 * (u - .5f) * 1.3);
    }
    private static double driveXd(float tau) { return (driveX(tau + .5f) - driveX(tau - .5f)); }
    private static double driveXdd(float tau) { return driveX(tau + 1) - 2 * driveX(tau) + driveX(tau - 1); }

    /** One moment of the car: where it is, how it is turned, the wheels' turn and steer, how hard the suspension works. */
    public record Car(Vec3 pos, Quaternionf rot, float wheelSpin, float steer, boolean flying) {
        public Matrix4f matrix() { return new Matrix4f().translation((float) pos.x, (float) pos.y, (float) pos.z).rotate(rot).scale(S); }
        public Vec3 world(Vec3 local) { return world(local.x, local.y, local.z); }
        public Vec3 world(double x, double y, double z) {
            Vector3f v = rot.transform(new Vector3f((float) x * S, (float) y * S, (float) z * S));
            return pos.add(v.x, v.y, v.z);
        }
        /** A stage point in this car's space. */
        public Vec3 local(Vec3 world) {
            Vector3f v = new Vector3f((float) (world.x - pos.x), (float) (world.y - pos.y), (float) (world.z - pos.z));
            new Quaternionf(rot).conjugate().transform(v);
            return new Vec3(v.x / S, v.y / S, v.z / S);
        }
        public Vec3 dir(double x, double y, double z) {
            Vector3f v = rot.transform(new Vector3f((float) x, (float) y, (float) z));
            return new Vec3(v.x, v.y, v.z);
        }
    }

    /** The car at scene time tau. */
    public static Car car(float tau) {
        if (tau < BOOM) return driving(tau);
        return blasted(tau - BOOM);
    }

    /** Driving: the road under it, the lane, the body rolling and pitching with the forces, the road's seams, every blow. */
    private static Car driving(float tau) {
        double z = driveZ(tau), x = driveX(tau);
        float v = driveSpeed(tau);
        float yaw = (float) Math.atan2(driveXd(tau), v);
        float roll = (float) (1.7 * driveXdd(tau)), pitch = -12 * driveAccel(tau);
        float bounce = 0;
        // The seams in the road: each axle thumps over them, the body rocks after.
        float seam = 11.3f;
        for (int axle = 0; axle < 2; axle++) {
            double at = z + (axle == 0 ? AXLE_F : AXLE_R);
            double past = at - Math.floor(at / seam) * seam;
            for (int k = 0; k < 2; k++) {
                float d = (float) ((past + k * seam) / v);
                float r = (float) (Math.exp(-d / 1.3) * Math.sin(d * 2.4));
                bounce += .010f * r;
                pitch += (axle == 0 ? -.006f : .006f) * r;
            }
        }
        // The road's grain and the engine: a fine buzz.
        bounce += .0025f * (float) Math.sin(tau * 3.7) + .0018f * (float) Math.sin(tau * 5.3 + 1);
        roll += .0012f * (float) Math.sin(tau * 2.9 + 2);
        // Blows from above: his landing, the claws, the tearing, the throw; the stored energy shaking it before the release.
        bounce -= .07f * spring(tau - ULT_TOUCH, 3.2f, .9f);
        pitch -= .022f * spring(tau - ULT_TOUCH, 3.2f, .9f);
        bounce -= .03f * spring(tau - ULT_HOP, 2.5f, 1.1f) - .02f * spring(tau - (ULT_HOP + 10), 2.5f, 1.1f);
        bounce -= .016f * spring(tau - ULT_CLAW_R, 2, 1.2f) + .016f * spring(tau - ULT_CLAW_L, 2, 1.2f);
        if (tau > ULT_TEAR && tau < ULT_ROOF_FREE + 10) {
            float k = window(tau, ULT_TEAR, ULT_ROOF_FREE + 6, 3);
            bounce += .012f * k * (float) Math.sin(tau * 1.9);
            roll += .018f * k * (float) Math.sin(tau * 1.3 + 1);
        }
        bounce += .03f * spring(tau - ULT_ROOF_FREE, 2.5f, 1);
        bounce -= .055f * spring(tau - ULT_THROW, 2.6f, .95f);
        float shudder = ease((tau - (ULT_CHARGE + 12)) / 40) * (tau < BOOM ? 1 : 0);
        bounce += .005f * shudder * (float) Math.sin(tau * 2.1);
        roll += .004f * shudder * (float) Math.sin(tau * 2.7 + .5);
        pitch += .003f * shudder * (float) Math.sin(tau * 3.3 + 1.1);
        Quaternionf q = new Quaternionf().rotationY(yaw).rotateX(pitch).rotateZ(roll);
        float steer = Mth.clamp((float) (driveXdd(tau) * 9 + Math.atan2(driveXd(tau), v) * .6), -.5f, .5f);
        return new Car(new Vec3(x, bounce, z), q, (float) (z / WHEEL_R), steer, false);
    }
    /** A damped bounce d ticks after a blow (0 before it). */
    static float spring(float d, float decay, float freq) {
        if (d < 0) return 0;
        return (float) (Math.exp(-d / decay) * Math.cos(d * freq) * Math.min(1, d / .8f));
    }
    public static float window(float t, float in, float out, float soft) { return Math.min(ease((t - in) / soft), ease((out - t) / soft)); }

    // ------------------------------------------------------------------ the release: the slam, the vault, the flight
    /** Ticks after the release when the nose has dug in and the car leaves the ground; when it comes down again. */
    public static final float DIG = 6, CRASH;
    private static final float SIM_STEP = .25f;
    private static final List<Vec3> FLIGHT_POS = new ArrayList<>();
    private static final List<Quaternionf> FLIGHT_ROT = new ArrayList<>();
    private static final Car AT_BOOM;
    private static final Vec3 PIVOT;
    private static final float DIG_END_PITCH = .70f;
    /** Where the wreck comes to rest after the crash, and how it lies. */
    private static final Vec3 REST_POS;
    private static final Quaternionf REST_ROT;
    public static final Vec3 CRASH_POS;

    /**
     * After the release (d scene ticks): first the slam (the body driven down onto its stops, the nose dipping onto
     * the road), then the nose digs in and the car pivots over it, the tail rising, faster and faster; then it
     * leaves the ground turning end over end; after the crash it lies where it came to rest.
     */
    private static Car blasted(float d) {
        float spin = (float) (driveZ(BOOM) / WHEEL_R + (V1 / WHEEL_R) * 30 * (1 - Math.exp(-d / 30)));
        if (d < 1.2f) return slam(d, spin);
        if (d < DIG) {
            float u = (d - 1.2f) / (DIG - 1.2f);
            float pitch = TOUCH_PITCH + (DIG_END_PITCH - TOUCH_PITCH) * u * u;
            Vec3 n = PIVOT.add(0, 0, (DIG - 1.2f) * (1.1 * u - .4 * u * u));
            Quaternionf q = new Quaternionf().rotationX(pitch);
            Vector3f off = q.transform(new Vector3f((float) -NOSE.x * S, (float) -NOSE.y * S, (float) -NOSE.z * S));
            return new Car(n.add(off.x, off.y, off.z), q, spin, 0, false);
        }
        if (d < CRASH) {
            float f = (d - DIG) / SIM_STEP;
            int i = Math.min(FLIGHT_POS.size() - 2, (int) f);
            float k = f - i;
            Vec3 com = FLIGHT_POS.get(i).lerp(FLIGHT_POS.get(i + 1), k);
            Quaternionf q = new Quaternionf(FLIGHT_ROT.get(i)).slerp(FLIGHT_ROT.get(i + 1), k);
            Vector3f off = q.transform(new Vector3f((float) -COM.x * S, (float) -COM.y * S, (float) -COM.z * S));
            return new Car(com.add(off.x, off.y, off.z), q, spin, 0, true);
        }
        // The crash: it comes to rest over a few ticks (hidden by the fire), then lies there.
        float k = ease((d - CRASH) / 9);
        Quaternionf q = new Quaternionf(FLIGHT_ROT.get(FLIGHT_ROT.size() - 1)).slerp(REST_ROT, k);
        Vec3 com = CRASH_POS.lerp(REST_POS, k);
        Vector3f off = q.transform(new Vector3f((float) -COM.x * S, (float) -COM.y * S, (float) -COM.z * S));
        return new Car(com.add(off.x, off.y, off.z), q, spin, 0, false);
    }

    /** The slam (d < 1.2): both axles driven onto their stops, the body pitching about the rear tyres until the nose meets the road. */
    private static Car slam(float d, float spin) {
        float k = ease(d / 1.2f);
        Quaternionf q = new Quaternionf().rotationX(TOUCH_PITCH * k);
        Vec3 rc = REAR_CONTACT.scale(S);
        Vector3f r = q.transform(new Vector3f((float) rc.x, (float) rc.y, (float) rc.z));
        Vec3 base = new Vec3(AT_BOOM.pos().x, AT_BOOM.pos().y * (1 - k) - .03 * k, AT_BOOM.pos().z + V1 * d * (1 - .1 * d));
        return new Car(base.add(rc.x - r.x, rc.y - r.y, rc.z - r.z), q, spin, 0, false);
    }

    static {
        AT_BOOM = driving(BOOM);
        Car touched = slam(1.2f, 0);
        PIVOT = touched.world(NOSE);
        // The moment it leaves the ground: turned over the nose by the dig, its middle swinging up round it.
        Quaternionf q = new Quaternionf().rotationX(DIG_END_PITCH);
        Vec3 nose = PIVOT.add(0, 0, (DIG - 1.2f) * .7);
        Vector3f r = q.transform(new Vector3f((float) (COM.x - NOSE.x) * S, (float) (COM.y - NOSE.y) * S, (float) (COM.z - NOSE.z) * S));
        Vec3 com = nose.add(r.x, r.y, r.z);
        float pitchRate = 2 * (DIG_END_PITCH - TOUCH_PITCH) / (DIG - 1.2f);
        Vector3f w = new Vector3f(pitchRate, .04f, -.03f);
        Vector3f swing = new Vector3f(w).cross(r);
        // The nose still sliding on (it slows hard as it digs), the swing of the body round it, and the blast thrown back
        // up off the asphalt under it lifting it clear.
        Vec3 vel = new Vec3(swing.x - .012, swing.y + .17, swing.z + .17);
        double[] ix = {CAR_MASS * (LENGTH * LENGTH + 1.4 * 1.4) / 12, CAR_MASS * (LENGTH * LENGTH + 1.86 * 1.86) / 12, CAR_MASS * (1.86 * 1.86 + 1.4 * 1.4) / 12};
        // Body-frame angular velocity, integrated with Euler's equations (no torque in the air): the turn about the
        // middle axis is unstable, so the small yaw and roll it starts with grow into a tumble.
        Quaternionf rot = new Quaternionf(q);
        Vector3f wb = new Quaternionf(rot).conjugate().transform(new Vector3f(w));
        double px = com.x, py = com.y, pz = com.z, vx = vel.x, vy = vel.y, vz = vel.z;
        float g = .0245f, dt = .05f;
        FLIGHT_POS.add(com); FLIGHT_ROT.add(new Quaternionf(rot));
        float crash = DIG + 80;
        Vector3f[] corners = new Vector3f[8];
        int c = 0;
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) for (int sz = -1; sz <= 1; sz += 2)
            corners[c++] = new Vector3f(HALF_W * sx * S, (float) (sy < 0 ? .22 - COM.y : ROOF_Y - COM.y) * S, (float) (sz * LENGTH / 2 - COM.z) * S);
        int perSample = Math.round(SIM_STEP / dt);
        for (int step = 1; step < 4000; step++) {
            double wx = wb.x, wy = wb.y, wz = wb.z;
            wb.set((float) (wx + (ix[1] - ix[2]) / ix[0] * wy * wz * dt), (float) (wy + (ix[2] - ix[0]) / ix[1] * wz * wx * dt),
                    (float) (wz + (ix[0] - ix[1]) / ix[2] * wx * wy * dt));
            Quaternionf dq = new Quaternionf(rot).mul(new Quaternionf(wb.x, wb.y, wb.z, 0));
            rot.set(rot.x + .5f * dq.x * dt, rot.y + .5f * dq.y * dt, rot.z + .5f * dq.z * dt, rot.w + .5f * dq.w * dt).normalize();
            vy -= g * dt;
            px += vx * dt; py += vy * dt; pz += vz * dt;
            if (step % perSample == 0) { FLIGHT_POS.add(new Vec3(px, py, pz)); FLIGHT_ROT.add(new Quaternionf(rot)); }
            float low = 99;
            for (Vector3f corner : corners) low = Math.min(low, (float) py + rot.transform(new Vector3f(corner)).y);
            if (low < 0 && step * dt > 10) { crash = DIG + step * dt; break; }
        }
        if (FLIGHT_POS.size() < 2) { FLIGHT_POS.add(com); FLIGHT_ROT.add(new Quaternionf(rot)); }
        CRASH = crash;
        CRASH_POS = FLIGHT_POS.get(FLIGHT_POS.size() - 1);
        // It settles on whichever face is nearest the ground (never on its nose or tail), skidded on a little way.
        Quaternionf last = FLIGHT_ROT.get(FLIGHT_ROT.size() - 1);
        Vector3f[] axes = {new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, -1, 0)};
        Vector3f best = axes[0];
        float bestDown = -2;
        for (Vector3f a : axes) {
            float down = -last.transform(new Vector3f(a)).y;
            if (down > bestDown) { bestDown = down; best = a; }
        }
        Vector3f now = last.transform(new Vector3f(best));
        Quaternionf settle = new Quaternionf().rotationTo(now, new Vector3f(0, -1, 0));
        REST_ROT = settle.mul(new Quaternionf(last));
        float lie = best.x != 0 ? HALF_W : (float) (best.y > 0 ? ROOF_Y - COM.y : COM.y);
        REST_POS = new Vec3(CRASH_POS.x + vx * 3, lie * .92 * S, CRASH_POS.z + vz * 3.5);
        // The warp, now the crash is known.
        int n = (int) ((BOOM + CRASH + 400) / STEP) + 2;
        REAL = new float[n];
        double t = 0;
        for (int i = 1; i < n; i++) {
            float mid = (i - .5f) * STEP;
            t += STEP / speedAt(mid);
            REAL[i] = (float) t;
        }
    }

    // ------------------------------------------------------------------ the car behind
    /** How far behind his car the car behind keeps (car space z, so times S in the world), scene time. Tailgating him while he is on the roof. */
    static float behind(float tau) {
        if (tau < 180) return -60;
        if (tau < 228) return Mth.lerp(ease((tau - 180) / 48), -40, -6.9f);
        if (tau < 300) return -6.9f + .08f * (float) Math.sin(tau * .21);
        if (tau < 330) return Mth.lerp(ease((tau - 300) / 30), -6.9f, -30);
        return -30 - (tau - 330) * .6f;
    }
    /** The car behind (a dark SUV, its roof higher than his car's). */
    public static Car suv(float tau) {
        Car hero = driving(Math.min(tau, BOOM - .01f));
        double z = hero.pos().z + behind(tau) * S;
        double x = driveX(Math.max(0, tau - 6)) + .05 * Math.sin(tau * .07);
        float bob = .008f * (float) Math.sin(tau * 3.1 + 2) - .05f * spring(tau - (ULT_HOP + 10), 2.6f, 1) + .03f * spring(tau - ULT_LEAP, 2.4f, 1.1f);
        float yaw = (float) Math.atan2(driveXd(Math.max(0, tau - 6)), V1);
        return new Car(new Vec3(x, bob, z), new Quaternionf().rotationY(yaw), (float) (z / .38), 0, false);
    }
    public static final float SUV_ROOF = 1.86f, SUV_LENGTH = 4.9f;
    /** True while the car behind is near enough to draw. */
    public static boolean suvShown(float tau) { return tau > 175 && tau < 420; }

    // ------------------------------------------------------------------ the traffic
    /** One car of the traffic: its lane (negative = the far side), where it starts, its speed, kind and colour. */
    public record Traffic(int lane, boolean oncoming, double z0, float speed, int kind, int colour, int seed) {
        public double z(float tau) {
            double dir = oncoming ? -1 : 1;
            if (tau <= BOOM) return z0 + dir * speed * tau;
            // After the release the traffic brakes to a stop, hazards on.
            float d = tau - BOOM, stop = oncoming ? 50 : 40;
            return z0 + dir * speed * (BOOM + (d < stop ? d - d * d / (2 * stop) : stop / 2));
        }
        public float x() { return oncoming ? PursuitPath.oncoming(lane) : PursuitPath.lane(lane); }
        public boolean braking(float tau) { return tau > BOOM; }
    }
    public static final int SEDAN = 0, TAXI = 1, SUV = 2, VAN = 3, BUS = 4, TRUCK = 5;
    public static final List<Traffic> TRAFFIC = new ArrayList<>();
    /** Down the road from the release, nothing on his side: his car, Panther and the wreck have it to themselves. */
    static final double CLEAR_FROM, CLEAR_TO;
    static {
        double zb = driveZ(BOOM);
        CLEAR_FROM = zb - 26; CLEAR_TO = zb + 170;
        int seed = 0;
        int[] paints = {0x1c1e22, 0xd8d9dc, 0x5a5e66, 0x23324a, 0x6b1414, 0x9aa0a8, 0x2c2a26, 0x7d7f84};
        // His side: the middle and slow lanes, slower than him (he passes them); the truck he overtakes at the start.
        TRAFFIC.add(new Traffic(1, false, 48, .82f, TRUCK, 0x9a9488, seed++));
        for (int lane = 1; lane <= 2; lane++) {
            for (double z = -60; z < 1250; z += 21 + 19 * hash(seed * 3.1)) {
                float speed = .78f + .32f * (float) hash(seed * 7.3);
                double atBoom = z + speed * BOOM;
                seed++;
                if (Math.abs(z - 48) < 18 && lane == 1) continue;
                if (atBoom > CLEAR_FROM - 20 && atBoom < CLEAR_TO) continue;
                int kind = pick(seed);
                TRAFFIC.add(new Traffic(lane, false, z, speed, kind, kind == TAXI ? 0xe0e2e4 : paints[(int) (hash(seed * 1.7) * paints.length)], seed));
            }
        }
        // The far side: oncoming, fast, beyond the barrier.
        for (int lane = 0; lane <= 2; lane++) {
            for (double z = 0; z < 2300; z += 17 + 22 * hash(seed * 2.3)) {
                seed++;
                int kind = pick(seed);
                TRAFFIC.add(new Traffic(lane, true, z, 1.0f + .3f * (float) hash(seed * 5.1), kind, kind == TAXI ? 0xe0e2e4 : paints[(int) (hash(seed * 4.9) * paints.length)], seed));
            }
        }
    }
    private static int pick(int seed) {
        double h = hash(seed * 9.7);
        return h < .45 ? SEDAN : h < .62 ? TAXI : h < .8 ? SUV : h < .9 ? VAN : h < .96 ? BUS : TRUCK;
    }
    public static double hash(double n) { double x = Math.sin(n * 12.9898) * 43758.5453; return x - Math.floor(x); }

    // ------------------------------------------------------------------ Panther: where his feet are
    /** A performer this moment: the model's feet origin (stage space), which way the body faces (radians), and the frame it rides in. */
    public record Place(Vec3 feet, float yaw, Quaternionf frame) {
        /** Model space (pixels, y down, the player renderer's conventions) to stage space. */
        public Matrix4f matrix() {
            return new Matrix4f().translation((float) feet.x, (float) feet.y, (float) feet.z).rotate(frame).rotateY(Mth.PI - yaw)
                    .scale(-BODY_SCALE, -BODY_SCALE, BODY_SCALE).translate(0, -1.501f, 0);
        }
    }
    /** His feet origin in car space while seated (legs posed; the origin is below the floor). */
    static final Vec3 SEAT_FEET = DRIVER.subtract(0, HIP_HEIGHT / S, 0);
    static final Vec3 ON_ROOF_EDGE = new Vec3(.50, ROOF_Y, -.18), ROOF_LAND = new Vec3(0, ROOF_Y, -.45), ROOF_STAND = new Vec3(0, ROOF_Y, .10),
            ROOF_EDGE_STAND = new Vec3(0, ROOF_Y, 0);
    /** Where he lands on the car behind (car space of his own car while it tails him), and where he sets off from on his roof. */
    static final Vec3 SUV_LAND = new Vec3(0, SUV_ROOF, -6.0), HOP_FROM = new Vec3(.22, ROOF_Y, -.30);
    /** Out of the window and up onto the roof: the way his feet origin goes (car space), ULT_EXIT to ULT_REVEAL. */
    private static final Vec3[] EXIT = {SEAT_FEET, new Vec3(.62, .05, -.04), new Vec3(1.05, .55, -.02), new Vec3(1.12, 1.05, -.06), new Vec3(.82, 1.38, -.12), ON_ROOF_EDGE};

    /** Where Panther is at scene time tau (before the release: on or in his car; after it: flung, landed). */
    public static Place panther(float tau) {
        if (tau >= BOOM) return pantherAfter(tau - BOOM);
        Car car = driving(tau);
        Vec3 local;
        float yaw = 0;
        if (tau < ULT_EXIT) local = SEAT_FEET;
        else if (tau < ULT_REVEAL) local = spline(EXIT, ease((tau - ULT_EXIT) / (ULT_REVEAL - ULT_EXIT)) * .55f + .45f * (tau - ULT_EXIT) / (ULT_REVEAL - ULT_EXIT));
        else if (tau < ULT_HOP) local = ON_ROOF_EDGE.lerp(HOP_FROM, ease((tau - (ULT_HOP - 8)) / 8));
        else if (tau < ULT_HOP + 10) local = ballistic(HOP_FROM, SUV_LAND.add(0, 0, behind(ULT_HOP + 10) + 6.9), 10, tau - ULT_HOP);
        else if (tau < ULT_LEAP) local = SUV_LAND.add(0, 0, behind(tau) + 6.9).add(0, suv(tau).pos().y - car.pos().y, 0);
        else if (tau < ULT_TOUCH) local = ballistic(SUV_LAND.add(0, 0, behind(ULT_LEAP) + 6.9), ROOF_LAND, ULT_TOUCH - ULT_LEAP, tau - ULT_LEAP);
        else if (tau < ULT_TURN) local = ROOF_LAND.add(0, 0, .13 * ease((tau - ULT_TOUCH) / 6)).add(0, -dent(tau, ROOF_LAND.z), 0);
        else if (tau < ULT_TURN + 12) {
            float k = ease((tau - ULT_TURN) / 12);
            local = ROOF_LAND.add(0, 0, .13).lerp(ROOF_STAND, k);
            yaw = Mth.PI * k;
        } else if (tau < ULT_REACH - 6) { local = ROOF_STAND; yaw = Mth.PI; }
        else { local = ROOF_STAND.lerp(ROOF_EDGE_STAND, ease((tau - (ULT_REACH - 6)) / 6)); yaw = Mth.PI; }
        Vec3 feet = car.world(local);
        return new Place(feet, yaw, car.rot());
    }
    /** Where the release went off: his chest on the roof at that moment (stage space). */
    public static Vec3 blastCentre() { return car(BOOM).world(ROOF_EDGE_STAND).add(0, 1.15, 0); }
    /** How deep his landing dented the roof at car-space z (blocks). */
    static float dent(float tau, double z) {
        if (tau < ULT_TOUCH) return 0;
        float k = 1 - (float) Math.exp(-(tau - ULT_TOUCH) / 1.2f);
        return .045f * k * (float) Math.exp(-Math.pow((z - ROOF_LAND.z) / .3, 2));
    }
    /** From a to b in ticks, under gravity (both in car space, where gravity is the world's over S: the car keeps going under him at the same speed). */
    static Vec3 ballistic(Vec3 a, Vec3 b, float ticks, float t) {
        float g = .0245f / S;
        double vy = (b.y - a.y + .5 * g * ticks * ticks) / ticks;
        double k = t / ticks;
        return new Vec3(Mth.lerp(k, a.x, b.x), a.y + vy * t - .5 * g * t * t, Mth.lerp(k, a.z, b.z));
    }
    static Vec3 spline(Vec3[] p, float k) {
        float s = Mth.clamp(k, 0, 1) * (p.length - 1);
        int i = Math.min(p.length - 2, (int) s);
        float t = s - i;
        Vec3 p0 = p[Math.max(0, i - 1)], p1 = p[i], p2 = p[i + 1], p3 = p[Math.min(p.length - 1, i + 2)];
        float t2 = t * t, t3 = t2 * t;
        return p1.scale(2).add(p2.subtract(p0).scale(t)).add(p0.scale(2).subtract(p1.scale(5)).add(p2.scale(4)).subtract(p3).scale(t2))
                .add(p1.scale(3).subtract(p0).subtract(p2.scale(3)).add(p3).scale(t3)).scale(.5);
    }

    /** How long the landing skid lasts (scene ticks). */
    public static final float SKID = 8;
    /** Thrown off the roof by his own release: up and out over the slow lane; down on the road, skidding. */
    public static final Vec3 FLING_VEL;
    public static final float LAND;
    public static final Vec3 LAND_POS, REST_FEET;
    static {
        Vec3 start = AT_BOOM.world(ROOF_EDGE_STAND);
        FLING_VEL = new Vec3(-.22, .38, 1.25);
        float g = .0245f;
        double y0 = start.y;
        LAND = (float) ((FLING_VEL.y + Math.sqrt(FLING_VEL.y * FLING_VEL.y + 2 * g * y0)) / g);
        LAND_POS = new Vec3(start.x + FLING_VEL.x * LAND, 0, start.z + FLING_VEL.z * LAND);
        // The skid: momentum carried on along the road, claws and feet scraping it away.
        REST_FEET = LAND_POS.add(FLING_VEL.x * .3 * SKID / 2, 0, FLING_VEL.z * SKID / 2);
    }
    private static Place pantherAfter(float d) {
        Vec3 start = AT_BOOM.world(ROOF_EDGE_STAND);
        if (d < LAND) {
            float g = .0245f;
            Vec3 at = start.add(FLING_VEL.x * d, FLING_VEL.y * d - .5 * g * d * d, FLING_VEL.z * d);
            return new Place(at, Mth.PI, new Quaternionf());
        }
        float s = Math.min(d - LAND, SKID), u = s / SKID;
        double along = SKID * (u - u * u / 2);
        Vec3 at = LAND_POS.add(FLING_VEL.x * .3 * along, 0, FLING_VEL.z * along);
        return new Place(at, Mth.PI, new Quaternionf());
    }

    // ------------------------------------------------------------------ the gunman
    static final Vec3 GUNMAN_FEET = GUNMAN.subtract(0, HIP_HEIGHT / S, 0), LEANING = new Vec3(.75, GUNMAN_FEET.y + .06, -.86);
    /** The moment he is let go of in the air above the car, and how he flies (stage space, scene time). */
    public static final float THROWN = ULT_THROW + 4;
    /** Where the gunman is (until he is out of sight high behind the car). */
    public static Place gunman(float tau) {
        if (tau < THROWN) {
            Car car = driving(Math.min(tau, BOOM - .01f));
            Vec3 local = GUNMAN_FEET;
            if (tau > ULT_LEAN - 2 && tau < ULT_EXIT + 2) local = GUNMAN_FEET.lerp(LEANING, window(tau, ULT_LEAN - 2, ULT_EXIT - 2, 6));
            // Hauled up out of his seat by the collar.
            if (tau > ULT_THROW - 3) local = GUNMAN_FEET.add(0, .85 * ease((tau - (ULT_THROW - 3)) / 7), .45 * ease((tau - (ULT_THROW - 3)) / 7));
            return new Place(car.world(local), 0, car.rot());
        }
        Place at = gunman(THROWN - .01f);
        float s = tau - THROWN, g = .0245f;
        // Flung straight up off the car at its speed; the wind takes his speed away and he falls behind it.
        double drag = .05, carried = V1 * (1 - Math.exp(-drag * s)) / drag;
        Vec3 feet = at.feet().add(-.04 * s, .95 * s - .5 * g * s * s, carried);
        return new Place(feet, 0, new Quaternionf());
    }

    // ------------------------------------------------------------------ shots and the roof
    /** Each shot of the gunman (scene time), and what it hits: 0 Panther (absorbed), 1 the car's metal, 2 glass, 3 the cabin. */
    public record Shot(float time, int hits, int region, int side, Vec3 target) {}
    public static final List<Shot> SHOTS = new ArrayList<>();
    /** Holes his shots punch up through the roof (car space, scene time). */
    public record Hole(float time, float x, float z) {}
    public static final List<Hole> HOLES = new ArrayList<>();
    static {
        // Through the driver's window from outside behind it: the glass bursts, then hit and miss in turn.
        int[][] plan = {{0, PantherBody.UPPER_ARM, 1}, {1, -1, 0}, {2, -1, 0}, {0, PantherBody.CHEST, 1}, {1, -1, 0}, {0, PantherBody.SHOULDER, 1}, {3, -1, 0}, {0, PantherBody.RIBS, 1}};
        Vec3[] aims = {new Vec3(.66, 1.0, -.02), new Vec3(.93, 1.12, B_PILLAR), new Vec3(-.45, 1.12, 1.10), new Vec3(.42, .92, .14),
                new Vec3(.42, .86, .58), new Vec3(.66, 1.12, -.08), new Vec3(-.38, 1.2, -.25), new Vec3(.62, .80, .02)};
        for (int i = 0; i < ULT_SHOTS; i++)
            SHOTS.add(new Shot(ULT_FIRE + i * ULT_SHOT_GAP, plan[i][0], plan[i][1], plan[i][2], aims[i]));
        // Up through the roof at the thumps over his head.
        float[][] up = {{ULT_ROOF_FIRE, .30f, -.30f}, {ULT_ROOF_FIRE + 4, .05f, -.55f}, {ULT_ROOF_FIRE + 9, .42f, -.22f}, {ULT_ROOF_FIRE + 14, -.20f, -.40f},
                {ULT_INSIDE + 2, -.10f, -.50f}, {ULT_INSIDE + 7, .25f, -.62f}, {ULT_INSIDE + 12, .08f, -.38f}, {ULT_INSIDE + 17, -.32f, -.66f}};
        int[][] upHits = {{3, -1, 0}, {3, -1, 0}, {3, -1, 0}, {3, -1, 0}, {0, PantherBody.SHIN, 1}, {3, -1, 0}, {0, PantherBody.THIGH, -1}, {3, -1, 0}};
        for (int i = 0; i < up.length; i++) {
            HOLES.add(new Hole(up[i][0], up[i][1], up[i][2]));
            SHOTS.add(new Shot(up[i][0], upHits[i][0], upHits[i][1], upHits[i][2], new Vec3(up[i][1], ROOF_Y + .02, up[i][2])));
        }
    }
    /** The shots that strike his suit (for the energy they leave in it). */
    public static int hitsBefore(float tau) {
        int n = 0;
        for (Shot s : SHOTS) if (s.hits() == 0 && s.time() <= tau) n++;
        return n;
    }

    /** How far the rear roof is peeled up about its back edge (radians), and whether it has torn free. */
    public static float peel(float tau) {
        if (tau < ULT_TEAR) return 0;
        if (tau < ULT_ROOF_FREE) { float u = (tau - ULT_TEAR) / (ULT_ROOF_FREE - ULT_TEAR); return 1.75f * u * u * (1.6f - .6f * u); }
        return 1.75f;
    }
    public static boolean roofGone(float tau) { return tau >= ULT_ROOF_FREE; }
    /** The torn-off roof, once free: thrown up and back, slowed hard by the wind, tumbling down onto the road far behind. */
    public static Matrix4f looseRoof(float tau) {
        Car at = driving(ULT_ROOF_FREE);
        float s = tau - ULT_ROOF_FREE, g = .0245f;
        double drag = .11, carried = (V1 - .25) * (1 - Math.exp(-drag * s)) / drag;
        float h0 = ROOF_Y * S + 1.1f, vy = .32f, down = (vy + (float) Math.sqrt(vy * vy + 2 * g * (h0 - .05f))) / g;
        Vec3 hinge = at.world(0, ROOF_Y, TEAR_B);
        if (s >= down) {
            // Down on the road, flat, sliding to a stop.
            Vec3 pos = new Vec3(hinge.x + .02 * down, .05, hinge.z + carried);
            return new Matrix4f().translation((float) pos.x, (float) pos.y, (float) pos.z).rotateY(.05f * down).rotateX(-Mth.PI).scale(S);
        }
        double y = h0 + vy * s - .5 * g * s * s;
        Vec3 pos = new Vec3(hinge.x + .02 * s, y, hinge.z + carried);
        float tumble = 1.75f + (Mth.PI - 1.75f + Mth.TWO_PI) * s / down;
        return new Matrix4f().translation((float) pos.x, (float) pos.y, (float) pos.z).rotateY(.05f * s).rotateX(-tumble).rotateZ(.4f * (float) Math.sin(Math.PI * s / down)).scale(S);
    }
}
