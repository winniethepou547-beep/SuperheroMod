package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.client.render.panther.PursuitPath.*;
import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * The cars of THE FINAL PURSUIT. His car is a long, low gunmetal saloon, built in full because the camera spends
 * half the film inside it: a body lofted along its length (its shoulders, the arches over the wheels, the
 * bonnet falling to the nose), the glass house with its pillars, a glass panel in the roof over the front seats
 * and metal over the back, the dashboard and its instruments, the wheel, the seats, the doors' inner trim, the
 * headlining; real wheels turning and steering; head- and tail-lights. It takes its damage as the film goes: the
 * rear side window smashed out, holes punched up through the roof, the dent of his landing, the
 * claws' punctures, the roof peeled back and torn away; after the release the nose crushed, glass crazed, panels
 * shed, and the burnt wreck. The car behind and the traffic share the body in simpler forms.
 */
final class PursuitCar {
    private PursuitCar() {}

    static final int PAINT = 0x8a909a, TRIM = 0x131418, CHROME = 0xb7bcc4, TYRE = 0x111111, RIM = 0x9aa0a8, CABIN = 0x1f2025,
            SEAT = 0x26272c, HEADLINER = 0x8f8a82, DASH = 0x18191d, GLASS = 0x1b2330;

    // ------------------------------------------------------------------ the body's shape
    /** The lower body along its length: z, half width, top line, sill. */
    private static final float[][] LOFT = {
            {-2.36f, .80f, .88f, .32f}, {-2.22f, .89f, .93f, .28f}, {-1.62f, .93f, .97f, .26f}, {-1.20f, .93f, .885f, .26f},
            {0f, .93f, .865f, .26f}, {1.18f, .93f, .865f, .26f}, {1.90f, .92f, .80f, .26f}, {2.25f, .86f, .72f, .24f}, {2.38f, .74f, .64f, .22f}};
    private static float loft(float z, int k) {
        if (z <= LOFT[0][0]) return LOFT[0][k];
        for (int i = 1; i < LOFT.length; i++) {
            if (z <= LOFT[i][0]) {
                float u = (z - LOFT[i - 1][0]) / (LOFT[i][0] - LOFT[i - 1][0]);
                u = u * u * (3 - 2 * u);
                return Mth.lerp(u, LOFT[i - 1][k], LOFT[i][k]);
            }
        }
        return LOFT[LOFT.length - 1][k];
    }
    /** The bottom edge of the side at z: the sill, or the arch over a wheel. */
    private static float sideBottom(float z, float scale) {
        float sill = loft(z, 3);
        for (float axle : new float[]{AXLE_F, AXLE_R}) {
            float d = Math.abs(z - axle * scale);
            float r = .42f;
            if (d < r) sill = Math.max(sill, WHEEL_R + (float) Math.sqrt(r * r - d * d) * .95f);
        }
        return sill;
    }

    // ------------------------------------------------------------------ his car
    private static final Vector3f VA = new Vector3f(), VB = new Vector3f(), VC = new Vector3f(), VD = new Vector3f();
    /** The steering wheel: where its hub is (car space) and its radius, set to where his hands grip it. */
    static Vec3 wheelHub = new Vec3(.40, .87, .66);
    static float wheelRadius = .19f;
    private static boolean wheelPlaced;

    /** How broken his car is at scene time tau. */
    record Damage(boolean driverGlass, boolean rearLeftGlass, boolean rearSidesGlass, float windscreenCracks, float crazed,
                  float crush, float wreck, boolean bumper, boolean mirrorL, float bootOpen, boolean boot, boolean wheelRL, float scorch) {}
    static Damage damage(float tau) {
        float d = tau - BOOM;
        return new Damage(true, tau < ULT_SMASH, tau < ULT_ROOF_FREE, 0,
                clamp(d / 3), clamp((d - 1.2f) / 3.5f), clamp((d - CRASH) / 2), d < 2.2f, d < 1, d > 14 ? .6f + .4f * (float) Math.sin(d * .5f) : 0,
                d < 40, d < CRASH + .5f, clamp((d - CRASH) / 8));
    }

    static void hero(FilmContext c, float tau, float time) {
        placeWheel();
        Car car = car(tau);
        Damage dmg = damage(tau);
        Matrix4f model = car.matrix();
        Matrix4f view = c.pose().last().pose();
        Vec3 centre = car.world(0, .8, 0);
        PursuitShade.lightsNear(centre.x, centre.y, centre.z, 20);
        VertexConsumer v = PursuitShade.solid(c);
        float crush = dmg.crush(), wreck = dmg.wreck();
        int paint = dmg.scorch() > 0 ? PursuitCity.mix(PAINT, 0x2b2623, .8f * dmg.scorch()) : PAINT;
        Matrix4f m = new Matrix4f(model);
        if (wreck > 0) m.scale(1 + .06f * wreck, 1 - .22f * wreck, 1 - .05f * wreck);
        lowerBody(v, view, m, paint, crush, 1, dmg.bumper());
        greenhouse(v, view, m, paint, tau, wreck);
        roofFrame(v, view, m, paint, tau, wreck);
        if (!roofGone(tau)) rearRoof(v, view, m, paint, tau, peel(tau));
        else tornEdges(v, view, m, paint);
        if (wreck < 1) interior(v, view, m, tau);
        wheels(v, view, m, car.wheelSpin(), car.steer(), dmg.wheelRL() ? 4 : 3, 1);
        details(v, view, m, paint, dmg, tau);
        // The roof, torn free, tumbling away behind.
        if (roofGone(tau) && tau < ULT_ROOF_FREE + 120) {
            v = PursuitShade.solid(c);
            Matrix4f loose = looseRoof(tau);
            Vector3f at = loose.transformPosition(new Vector3f());
            PursuitShade.lightsNear(at.x, at.y, at.z, 16);
            looseSheet(v, view, loose, paint);
        }
    }

    /** Where his hands are on the wheel when seated: the wheel is put there. */
    private static void placeWheel() {
        if (wheelPlaced) return;
        wheelPlaced = true;
        PantherMotion.Pose seat = PursuitMoves.seat();
        Place place = new Place(SEAT_FEET.scale(S), 0, new org.joml.Quaternionf());
        Matrix4f body = place.matrix();
        Vec3 r = PursuitRig.palm(body, seat, 0).scale(1 / S), l = PursuitRig.palm(body, seat, 1).scale(1 / S);
        wheelHub = r.lerp(l, .5).add(0, -.02, .03);
        wheelRadius = (float) Math.max(.14, Math.min(.22, r.distanceTo(l) * .5));
    }

    /** The lower body: two bands down each side (the shoulder and the tuck under), the bonnet and boot lids, the ends. */
    private static void lowerBody(VertexConsumer v, Matrix4f view, Matrix4f m, int paint, float crush, float scale, boolean bumper) {
        lowerBody(v, view, m, paint, crush, scale, bumper, 44);
    }
    private static void lowerBody(VertexConsumer v, Matrix4f view, Matrix4f m, int paint, float crush, float scale, boolean bumper, int n) {
        float front = 2.38f - .35f * crush;
        for (int i = 0; i < n; i++) {
            float za = Mth.lerp(i / (float) n, -2.36f, front), zb = Mth.lerp((i + 1) / (float) n, -2.36f, front);
            float sa = za > 1.18f ? 1.18f + (za - 1.18f) * (2.38f - 1.18f) / (front - 1.18f) : za;
            float sb = zb > 1.18f ? 1.18f + (zb - 1.18f) * (2.38f - 1.18f) / (front - 1.18f) : zb;
            float wa = loft(sa, 1), wb = loft(sb, 1), ta = loft(sa, 2), tb = loft(sb, 2), ba = sideBottom(za, scale), bb = sideBottom(zb, scale);
            // A crumple in the crushed nose.
            if (crush > 0 && za > 1.3f) { float k = crush * (za - 1.3f); ta -= .12f * k * (1 + (float) Math.sin(za * 23)); wa -= .05f * k; }
            if (crush > 0 && zb > 1.3f) { float k = crush * (zb - 1.3f); tb -= .12f * k * (1 + (float) Math.sin(zb * 23)); wb -= .05f * k; }
            float ma = Math.max(ba + .05f, .58f), mb = Math.max(bb + .05f, .58f);
            for (int side = -1; side <= 1; side += 2) {
                // The shoulder: from the crease out at full width up to the top line, rolling in.
                PursuitShade.quad(v, view, m, side * wa, ma, za, side * wb, mb, zb, side * (wb - .06f), tb, zb, side * (wa - .06f), ta, za, paint, .7f);
                // Below the crease the side tucks in toward the sill.
                if (ma > ba) PursuitShade.quad(v, view, m, side * (wa - .04f), ba, za, side * (wb - .04f), bb, zb, side * wb, mb, zb, side * wa, ma, za, paint, .55f);
                // The black sill and the arch's inner shadow.
                PursuitShade.quad(v, view, m, side * (wa - .04f), ba, za, side * (wb - .04f), bb, zb, side * (wb - .12f), Math.min(bb, .3f) - .05f, zb, side * (wa - .12f), Math.min(ba, .3f) - .05f, za, TRIM, .2f);
            }
            // The bonnet and the boot lid, across the top in strips (for the light sliding over them).
            if (za >= 1.18f || zb <= -1.62f) {
                for (int k = 0; k < 4; k++) {
                    float xa0 = -wa + .06f + (2 * wa - .12f) * k / 4f, xa1 = -wa + .06f + (2 * wa - .12f) * (k + 1) / 4f;
                    float xb0 = -wb + .06f + (2 * wb - .12f) * k / 4f, xb1 = -wb + .06f + (2 * wb - .12f) * (k + 1) / 4f;
                    float crown = .025f;
                    float ya0 = ta + crown * (1 - sq(xa0 / wa)), ya1 = ta + crown * (1 - sq(xa1 / wa)), yb0 = tb + crown * (1 - sq(xb0 / wb)), yb1 = tb + crown * (1 - sq(xb1 / wb));
                    PursuitShade.quad(v, view, m, xa0, ya0, za, xa1, ya1, za, xb1, yb1, zb, xb0, yb0, zb, paint, .8f);
                }
            } else {
                // The shelf along the top of the doors under the windows, and the dark floor of the cabin inside.
                for (int side = -1; side <= 1; side += 2)
                    PursuitShade.quad(v, view, m, side * (wa - .06f), ta, za, side * (wb - .06f), tb, zb, side * (wb - .14f), tb, zb, side * (wa - .14f), ta, za, paint, .6f);
            }
            // The underside.
            PursuitShade.quad(v, view, m, -wa + .12f, .2f, za, wa - .12f, .2f, za, wb - .12f, .2f, zb, -wb + .12f, .2f, zb, TRIM, 0);
        }
        // The tail: a panel down to the bumper, the bumper.
        float tw = loft(-2.36f, 1);
        PursuitShade.quad(v, view, m, -tw, .32f, -2.36f, tw, .32f, -2.36f, tw - .05f, .88f, -2.36f, -tw + .05f, .88f, -2.36f, paint, .6f);
        PursuitShade.box(v, view, m, -tw - .02f, .24f, -2.42f, tw + .02f, .44f, -2.30f, TRIM, .3f);
        // The nose: grille, bumper (torn away by the dig).
        float fw = loft(2.38f, 1);
        PursuitShade.quad(v, view, m, -fw, .22f, front, fw, .22f, front, fw - .05f, .64f - .1f * crush, front, -fw + .05f, .64f - .1f * crush, front, TRIM, .4f);
        if (bumper) PursuitShade.box(v, view, m, -fw - .04f, .18f, front - .05f, fw + .04f, .36f, front + .06f, PAINT, .6f);
    }
    private static float sq(float x) { return x * x; }

    /** The glass house's solid parts: the pillars and the window frames. (The glass itself is drawn translucent later.) */
    private static void greenhouse(VertexConsumer v, Matrix4f view, Matrix4f m, int paint, float tau, float wreck) {
        for (int side = -1; side <= 1; side += 2) {
            float s = side;
            // A-pillar: between the windscreen's edge and the side window's front edge.
            PursuitShade.quad(v, view, m, s * .76f, .88f, 1.18f, s * .90f, .87f, 1.10f, s * .68f, 1.38f, .40f, s * .65f, 1.37f, .42f, paint, .7f);
            // B-pillar, gloss black.
            PursuitShade.quad(v, view, m, s * .905f, .87f, B_PILLAR - .04f, s * .905f, .87f, B_PILLAR + .04f, s * .685f, 1.38f, B_PILLAR + .04f, s * .685f, 1.38f, B_PILLAR - .04f, TRIM, .9f);
            // C-pillar: the panel between the rear door's window and the rear screen.
            PursuitShade.quad(v, view, m, s * .93f, .97f, -1.62f, s * .905f, .87f, -1.05f, s * .685f, 1.38f, -.80f, s * .66f, 1.37f, -.82f, paint, .7f);
            // The chrome line round the windows.
            PursuitShade.quad(v, view, m, s * .91f, .865f, -1.05f, s * .91f, .865f, 1.10f, s * .905f, .885f, 1.10f, s * .905f, .885f, -1.05f, CHROME, .9f);
            // Inside: the door's trim from the sill to the window, so the cabin is dark trim, not paint.
            for (float[] door : new float[][]{{B_PILLAR, 1.10f}, {-1.05f, B_PILLAR}}) {
                PursuitShade.quad(v, view, m, s * .86f, .30f, door[0], s * .86f, .30f, door[1], s * .86f, .85f, door[1], s * .86f, .85f, door[0], CABIN, .1f);
                PursuitShade.box(v, view, m, s * .86f - .06f * s - .02f, .62f, door[0] + .1f, s * .86f - .02f, .66f, door[1] - .1f, 0x2c2d33, .3f);
            }
        }
    }

    /** The roof over the front seats: its header, the glass panel's frame, the bar at the B-pillars; the headlining under. */
    private static void roofFrame(VertexConsumer v, Matrix4f view, Matrix4f m, int paint, float tau, float wreck) {
        float y = ROOF_Y - .2f * wreck;
        PursuitShade.quad(v, view, m, -.66f, y - .04f, ROOF_F, .66f, y - .04f, ROOF_F, .68f, y, SUNROOF_F, -.68f, y, SUNROOF_F, paint, .8f);
        for (int side = -1; side <= 1; side += 2) {
            PursuitShade.quad(v, view, m, side * .50f, y + .005f, SUNROOF_F, side * .68f, y - .01f, SUNROOF_F, side * .68f, y - .01f, SUNROOF_B, side * .50f, y + .005f, SUNROOF_B, paint, .8f);
            // The rails down the roof's edges, back to the rear screen.
            PursuitShade.quad(v, view, m, side * .66f, y - .015f, TEAR_F, side * .69f, y - .03f, TEAR_F, side * .69f, y - .03f, TEAR_B, side * .66f, y - .015f, TEAR_B, paint, .8f);
        }
        PursuitShade.quad(v, view, m, -.68f, y, SUNROOF_B, .68f, y, SUNROOF_B, .68f, y, TEAR_F, -.68f, y, TEAR_F, paint, .8f);
        // Under it: the headlining, pale.
        float h = y - .045f;
        PursuitShade.quad(v, view, m, -.64f, h - .02f, ROOF_F - .02f, .64f, h - .02f, ROOF_F - .02f, .64f, h, SUNROOF_F, -.64f, h, SUNROOF_F, HEADLINER, 0);
        for (int side = -1; side <= 1; side += 2)
            PursuitShade.quad(v, view, m, side * .50f, h, SUNROOF_F, side * .64f, h, SUNROOF_F, side * .64f, h, SUNROOF_B, side * .50f, h, SUNROOF_B, HEADLINER, 0);
        PursuitShade.quad(v, view, m, -.64f, h, SUNROOF_B, .64f, h, SUNROOF_B, .64f, h, TEAR_F, -.64f, h, TEAR_F, HEADLINER, 0);
    }

    /**
     * The metal over the back seat, in a grid so it can dent where he landed and bend as he peels it: the peel
     * turns it up about its back edge, curling more toward the edge he holds.
     */
    private static void rearRoof(VertexConsumer v, Matrix4f view, Matrix4f m, int paint, float tau, float peel) {
        int nx = 6, nz = 8;
        float len = TEAR_F - TEAR_B;
        for (int i = 0; i < nz; i++) {
            for (int j = 0; j < nx; j++) {
                float u0 = i / (float) nz, u1 = (i + 1) / (float) nz, x0 = -.66f + 1.32f * j / nx, x1 = -.66f + 1.32f * (j + 1) / nx;
                Vector3f a = sheet(tau, peel, u0, x0, 0), b = sheet(tau, peel, u0, x1, 0), cc = sheet(tau, peel, u1, x1, 0), d = sheet(tau, peel, u1, x0, 0);
                m.transformPosition(a); m.transformPosition(b); m.transformPosition(cc); m.transformPosition(d);
                PursuitShade.quad(v, view, a, b, cc, d, paint, .8f);
                Vector3f a2 = sheet(tau, peel, u0, x0, -.045f), b2 = sheet(tau, peel, u0, x1, -.045f), c2 = sheet(tau, peel, u1, x1, -.045f), d2 = sheet(tau, peel, u1, x0, -.045f);
                m.transformPosition(a2); m.transformPosition(b2); m.transformPosition(c2); m.transformPosition(d2);
                PursuitShade.quad(v, view, a2, b2, c2, d2, HEADLINER, 0);
            }
        }
        // While it peels, the torn front edge shows its jagged teeth.
        if (peel > .02f) {
            for (int j = 0; j < 9; j++) {
                float x0 = -.66f + 1.32f * j / 9, x1 = -.66f + 1.32f * (j + 1) / 9, xm = (x0 + x1) / 2;
                Vector3f a = sheet(tau, peel, 1, x0, 0), b = sheet(tau, peel, 1, x1, 0), tip = sheet(tau, peel, 1.06f + .05f * (float) hash(j * 3.3), xm, 0);
                m.transformPosition(a); m.transformPosition(b); m.transformPosition(tip);
                PursuitShade.quad(v, view, a, b, tip, tip, paint, .9f);
            }
        }
    }
    /** A point of the rear roof sheet: u from its back edge (0) to its front edge (1), across at x, lift off its surface. */
    static Vector3f sheet(float tau, float peel, float u, float x, float lift) {
        float len = TEAR_F - TEAR_B;
        float crown = .035f * (1 - sq(x / .68f));
        float y = ROOF_Y + crown + lift - dent(tau, TEAR_B + u * len) * (1 - sq(x / .7f));
        if (peel <= 0) return new Vector3f(x, y, TEAR_B + u * len);
        // Bend it up about the back edge, curling harder toward the front.
        int steps = Math.max(1, (int) (u * 10));
        float px = 0, py = 0, du = u / steps;
        for (int k = 0; k < steps; k++) {
            float uu = (k + .5f) * du;
            float ang = peel * (.55f + .9f * uu);
            px += (float) Math.cos(ang) * du * len;
            py += (float) Math.sin(ang) * du * len;
        }
        float endAng = peel * (.55f + .9f * u);
        float liftX = -(float) Math.sin(endAng) * (lift + crown), liftY = (float) Math.cos(endAng) * (lift + crown);
        return new Vector3f(x, ROOF_Y + py + liftY, TEAR_B + px + liftX);
    }
    /** What is left round the hole: the rails and the bar, their torn edges in teeth. */
    private static void tornEdges(VertexConsumer v, Matrix4f view, Matrix4f m, int paint) {
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 8; k++) {
                float z0 = TEAR_B + (TEAR_F - TEAR_B) * k / 8, z1 = TEAR_B + (TEAR_F - TEAR_B) * (k + 1) / 8;
                float tip = .05f + .05f * (float) hash(k * 2.1 + side);
                PursuitShade.quad(v, view, m, side * .66f, ROOF_Y - .01f, z0, side * .66f, ROOF_Y - .01f, z1, side * (.66f - tip), ROOF_Y - .02f, (z0 + z1) / 2, side * (.66f - tip), ROOF_Y - .02f, (z0 + z1) / 2, paint, .9f);
            }
        }
        for (int k = 0; k < 10; k++) {
            float x0 = -.66f + 1.32f * k / 10, x1 = -.66f + 1.32f * (k + 1) / 10;
            float tip = .04f + .06f * (float) hash(k * 4.7);
            PursuitShade.quad(v, view, m, x0, ROOF_Y, TEAR_F, x1, ROOF_Y, TEAR_F, (x0 + x1) / 2, ROOF_Y - .02f, TEAR_F - tip, (x0 + x1) / 2, ROOF_Y - .02f, TEAR_F - tip, paint, .9f);
        }
    }
    /** The torn roof on its own, curled (its frame: the hinge at the origin, the sheet along +z). */
    private static void looseSheet(VertexConsumer v, Matrix4f view, Matrix4f loose, int paint) {
        int nx = 5, nz = 7;
        float len = TEAR_F - TEAR_B;
        for (int i = 0; i < nz; i++) for (int j = 0; j < nx; j++) {
            float u0 = i / (float) nz, u1 = (i + 1) / (float) nz, x0 = -.66f + 1.32f * j / nx, x1 = -.66f + 1.32f * (j + 1) / nx;
            float c0 = .25f * u0 * u0, c1 = .25f * u1 * u1;
            PursuitShade.quad(v, view, loose, x0, c0, u0 * len, x1, c0, u0 * len, x1, c1, u1 * len, x0, c1, u1 * len, paint, .8f);
            PursuitShade.quad(v, view, loose, x0, c0 - .04f, u0 * len, x1, c0 - .04f, u0 * len, x1, c1 - .04f, u1 * len, x0, c1 - .04f, u1 * len, HEADLINER, 0);
        }
    }

    /** The cabin: the dashboard and its hood, the steering wheel, the seats, the console, the mirror, the floor. */
    private static void interior(VertexConsumer v, Matrix4f view, Matrix4f m, float tau) {
        PursuitShade.box(v, view, m, -.84f, .55f, .74f, .84f, .83f, 1.22f, DASH, .3f);
        PursuitShade.box(v, view, m, -.84f, .80f, .78f, .84f, .86f, 1.18f, 0x232429, .4f);
        PursuitShade.box(v, view, m, .18f, .80f, .58f, .62f, .90f, .76f, DASH, .3f);
        PursuitShade.quad(v, view, m, -.86f, .27f, -1.6f, .86f, .27f, -1.6f, .86f, .27f, 1.0f, -.86f, .27f, 1.0f, 0x141518, 0);
        // The console between the front seats.
        PursuitShade.box(v, view, m, -.13f, .27f, -.6f, .13f, .62f, .9f, 0x1b1c20, .3f);
        // The steering column and wheel, gripped where his hands are.
        Vec3 hub = wheelHub;
        // The driver's hands on it: his steering, and the panic on the wheel once something is on his roof (as PursuitMoves.driver).
        float wheelTurn = PursuitMoves.steering(tau) + (tau < BOOM ? .25f * PursuitPath.ease((tau - ULT_TOUCH) / 6) * (float) Math.sin(tau * .41) : 0);
        Matrix4f w = new Matrix4f(m).translate((float) hub.x, (float) hub.y, (float) hub.z).rotateX(-.42f).rotateZ(wheelTurn);
        PursuitShade.box(v, view, new Matrix4f(m).translate((float) hub.x, (float) hub.y, (float) hub.z).rotateX(-.42f), -.035f, -.035f, 0, .035f, .035f, .30f, 0x1d1e22, .4f);
        int seg = 14;
        float r = wheelRadius;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
            float t = .022f;
            PursuitShade.quad(v, view, w, x0 * 1.1f, y0 * 1.1f, -t, x1 * 1.1f, y1 * 1.1f, -t, x1 * 1.1f, y1 * 1.1f, t, x0 * 1.1f, y0 * 1.1f, t, 0x16171a, .5f);
            PursuitShade.quad(v, view, w, x0 * .88f, y0 * .88f, -t, x1 * .88f, y1 * .88f, -t, x1 * 1.1f, y1 * 1.1f, -t, x0 * 1.1f, y0 * 1.1f, -t, 0x16171a, .5f);
        }
        for (double a : new double[]{-Math.PI / 2, Math.PI, 0}) {
            float x = (float) Math.cos(a) * r * .9f, y = (float) Math.sin(a) * r * .9f;
            PursuitShade.quad(v, view, w, -.02f, -.02f, 0, .02f, .02f, 0, x + .02f, y + .02f, 0, x - .02f, y - .02f, 0, 0x2a2b30, .6f);
        }
        PursuitShade.box(v, view, w, -.055f, -.055f, -.02f, .055f, .055f, .03f, 0x2a2b30, .6f);
        // The seats: cushion, back, headrest.
        for (int side = -1; side <= 1; side += 2) {
            float x = side * .40f;
            PursuitShade.box(v, view, m, x - .25f, .28f, -.30f, x + .25f, .40f, .26f, SEAT, .15f);
            Matrix4f back = new Matrix4f(m).translate(x, .39f, -.27f).rotateX(-.32f);
            PursuitShade.box(v, view, back, -.25f, 0, -.09f, .25f, .62f, .05f, SEAT, .15f);
            PursuitShade.box(v, view, back, -.13f, .66f, -.06f, .13f, .84f, .04f, SEAT, .15f);
        }
        PursuitShade.box(v, view, m, -.78f, .28f, -1.16f, .78f, .39f, -.60f, SEAT, .15f);
        Matrix4f rear = new Matrix4f(m).translate(0, .38f, -1.16f).rotateX(-.3f);
        PursuitShade.box(v, view, rear, -.78f, 0, -.1f, .78f, .58f, .02f, SEAT, .15f);
        // The mirror at the top of the windscreen.
        PursuitShade.box(v, view, m, -.12f, 1.2f, .46f, .12f, 1.27f, .5f, 0x17181c, .7f);
        PursuitShade.box(v, view, m, -.01f, 1.27f, .46f, .01f, 1.35f, .48f, 0x17181c, .7f);
    }

    /** Wheels: tyre and rim, turning; the front pair steered; count 3 leaves the rear left one off (lost in the crash). */
    private static void wheels(VertexConsumer v, Matrix4f view, Matrix4f m, float spin, float steer, int count, float scale) {
        wheels(v, view, m, spin, steer, count, scale, 14);
    }
    private static void wheels(VertexConsumer v, Matrix4f view, Matrix4f m, float spin, float steer, int count, float scale, int seg) {
        for (int i = 0; i < 4; i++) {
            if (count == 3 && i == 3) continue;
            boolean front = i < 2;
            float x = (i % 2 == 0 ? -1 : 1) * TRACK * scale, z = (front ? AXLE_F : AXLE_R) * scale;
            Matrix4f w = new Matrix4f(m).translate(x, WHEEL_R, z);
            if (front) w.rotateY(steer * .5f);
            w.rotateX(spin);
            wheel(v, view, w, i % 2 == 0 ? -1 : 1, seg);
        }
    }
    static void wheel(VertexConsumer v, Matrix4f view, Matrix4f w, int side) { wheel(v, view, w, side, 14); }
    static void wheel(VertexConsumer v, Matrix4f view, Matrix4f w, int side, int seg) {
        float r = WHEEL_R, half = .12f, rim = .23f;
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            float y0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r, y1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            PursuitShade.quad(v, view, w, -half, y0, z0, half, y0, z0, half, y1, z1, -half, y1, z1, TYRE, .15f);
            float o = side * half;
            // The tyre's wall, then the rim with five spokes.
            PursuitShade.quad(v, view, w, o, y0, z0, o, y1, z1, o, y1 * rim / r, z1 * rim / r, o, y0 * rim / r, z0 * rim / r, 0x1a1a1a, .1f);
            boolean spoke = k % 3 == 0 && seg > 10;
            PursuitShade.quad(v, view, w, o * .9f, y0 * rim / r, z0 * rim / r, o * .9f, y1 * rim / r, z1 * rim / r, o * .9f, y1 * (spoke ? .05f : .7f) * rim / r, z1 * (spoke ? .05f : .7f) * rim / r,
                    o * .9f, y0 * (spoke ? .05f : .7f) * rim / r, z0 * (spoke ? .05f : .7f) * rim / r, RIM, .85f);
        }
    }

    /** Small parts: lights' housings, mirrors, handles, the bullet marks; the boot lid flapping in the air. */
    private static void details(VertexConsumer v, Matrix4f view, Matrix4f m, int paint, Damage dmg, float tau) {
        for (int side = -1; side <= 1; side += 2) {
            // Headlamp housings, tail-lamp housings.
            PursuitShade.box(v, view, m, side * .78f - .16f, .58f, 2.20f, side * .78f + .16f, .68f, 2.34f - .3f * dmg.crush(), 0x2a2e36, .9f);
            PursuitShade.box(v, view, m, side * .55f - .3f, .76f, -2.38f, side * .55f + .3f, .86f, -2.32f, 0x3a0a0c, .9f);
            // Door handles.
            for (float z : new float[]{.35f, -.75f}) PursuitShade.box(v, view, m, side * .935f - .01f, .76f, z - .1f, side * .935f + .01f, .79f, z + .1f, CHROME, .9f);
            // The mirrors (the left one blown off by the release).
            if (side > 0 && !dmg.mirrorL()) continue;
            PursuitShade.box(v, view, m, side * .92f, .93f, 1.02f, side * 1.08f, 1.04f, 1.14f, paint, .8f);
            PursuitShade.box(v, view, m, side * .88f, .92f, 1.06f, side * .93f, .95f, 1.10f, TRIM, .3f);
        }
        // The boot lid, swinging on its hinge once it bursts open in the air.
        if (dmg.bootOpen() > 0 && dmg.boot()) {
            Matrix4f lid = new Matrix4f(m).translate(0, .97f, -1.64f).rotateX(-1.2f * dmg.bootOpen());
            PursuitShade.box(v, view, lid, -.86f, -.02f, -.70f, .86f, .02f, 0, paint, .8f);
        }
    }

    // ------------------------------------------------------------------ glass (translucent; after everything solid)
    /** His car's glass: what is left of each pane, tinted, catching the lights; the cracks and stars in it. */
    static void heroGlass(FilmContext c, float tau) {
        Car car = car(tau);
        Damage dmg = damage(tau);
        if (dmg.wreck() >= 1) return;
        Matrix4f m = car.matrix();
        Vec3 centre = car.world(0, 1, 0);
        PursuitShade.lightsNear(centre.x, centre.y, centre.z, 18);
        VertexConsumer v = PursuitShade.soft(c);
        Matrix4f view = c.pose().last().pose();
        float a = .42f;
        // The windscreen.
        pane(v, view, m, -.74f, .885f, 1.17f, .74f, .885f, 1.17f, .64f, 1.365f, .43f, -.64f, 1.365f, .43f, a);
        // The rear screen.
        pane(v, view, m, -.80f, .975f, -1.61f, .80f, .975f, -1.61f, .65f, 1.365f, -.83f, -.65f, 1.365f, -.83f, a);
        for (int side = -1; side <= 1; side += 2) {
            float s = side;
            boolean frontGone = side > 0 && !dmg.driverGlass();
            boolean rearGone = side > 0 ? !dmg.rearLeftGlass() : !dmg.rearSidesGlass();
            if (side > 0 && !dmg.rearSidesGlass()) rearGone = true;
            if (!frontGone) pane(v, view, m, s * .905f, .875f, B_PILLAR + .04f, s * .905f, .875f, 1.10f, s * .685f, 1.375f, .40f, s * .685f, 1.375f, B_PILLAR + .04f, a * .85f);
            if (!rearGone) pane(v, view, m, s * .905f, .875f, -1.05f, s * .905f, .875f, B_PILLAR - .04f, s * .685f, 1.375f, B_PILLAR - .04f, s * .685f, 1.375f, -.80f, a * .85f);
        }
        // The glass panel in the roof.
        pane(v, view, m, -.50f, ROOF_Y + .005f, SUNROOF_F, .50f, ROOF_Y + .005f, SUNROOF_F, .50f, ROOF_Y + .005f, SUNROOF_B, -.50f, ROOF_Y + .005f, SUNROOF_B, a * 1.1f);
        // Shards still in the frames of the burst windows.
        VertexConsumer g = PursuitShade.soft(c);
        if (!dmg.driverGlass()) shards(g, view, m, 1, B_PILLAR + .04f, 1.10f, .40f, 11);
        if (!dmg.rearLeftGlass()) shards(g, view, m, 1, -1.05f, B_PILLAR - .04f, -.80f, 23);
        if (!dmg.rearSidesGlass()) shards(g, view, m, -1, -1.05f, B_PILLAR - .04f, -.80f, 31);
        // Cracks: the bullet star in the windscreen, the crazing after the release, the panel he stepped on.
        VertexConsumer add = PursuitShade.add(c);
        if (dmg.windscreenCracks() > 0) star(add, view, m, new Vector3f(-.42f, 1.12f, .78f), .22f, 7, .9f);
        if (tau >= ULT_TURN + 6) star(add, view, m, new Vector3f(.05f, ROOF_Y + .01f, .12f), tau >= ULT_PRESS ? .3f : .18f, 13, .8f);
        if (dmg.crazed() > 0) {
            star(add, view, m, new Vector3f(.2f, 1.05f, .95f), .5f * dmg.crazed(), 17, .9f);
            star(add, view, m, new Vector3f(-.3f, 1.2f, -1.2f), .45f * dmg.crazed(), 19, .8f);
        }
    }
    /** One pane: dark tinted glass, the city's lights sliding across it. */
    private static void pane(VertexConsumer v, Matrix4f view, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float alpha) {
        m.transformPosition(ax, ay, az, VA); m.transformPosition(bx, by, bz, VB); m.transformPosition(cx, cy, cz, VC); m.transformPosition(dx, dy, dz, VD);
        float ux = VC.x - VA.x, uy = VC.y - VA.y, uz = VC.z - VA.z, wx = VD.x - VB.x, wy = VD.y - VB.y, wz = VD.z - VB.z;
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
        nx /= nl; ny /= nl; nz /= nl;
        for (Vector3f p : new Vector3f[]{VA, VB, VC, VD}) {
            int col = PursuitShade.shade(p.x, p.y, p.z, nx, ny, nz, GLASS, .95f);
            float bright = ((col >> 16 & 255) + (col >> 8 & 255) + (col & 255)) / 765f;
            v.vertex(view, p.x, p.y, p.z).color((col >> 16 & 255) / 255f, (col >> 8 & 255) / 255f, (col & 255) / 255f, Math.min(.9f, alpha + bright * .8f)).endVertex();
        }
    }
    /** Jagged pieces of glass left round the frame of a burst side window. */
    private static void shards(VertexConsumer v, Matrix4f view, Matrix4f m, int side, float z0, float z1, float topZ1, int seed) {
        float s = side;
        for (int k = 0; k < 7; k++) {
            float u = k / 7f, z = Mth.lerp(u, z0, z1);
            float h = .05f + .09f * (float) hash(seed + k * 1.7);
            m.transformPosition(s * .905f, .875f, z, VA);
            m.transformPosition(s * .905f, .875f, z + (z1 - z0) / 7, VB);
            m.transformPosition(s * (.905f - .04f * h * 4), .875f + h, z + (z1 - z0) / 14, VC);
            PursuitShade.flat(v, view, VA, VB, VC, VC, 0x8a9cb0, .45f, .45f, .55f, .55f);
        }
    }
    /** A crack star: lines running out from a point in the glass, branching, bright where they catch the light. */
    private static void star(VertexConsumer v, Matrix4f view, Matrix4f m, Vector3f centre, float size, int seed, float alpha) {
        Vector3f c0 = m.transformPosition(new Vector3f(centre));
        for (int k = 0; k < 9; k++) {
            double a = k * Math.PI * 2 / 9 + hash(seed + k) * .5;
            float len = size * (.5f + .6f * (float) hash(seed * 3 + k));
            Vector3f dir = new Vector3f((float) Math.cos(a), (float) Math.sin(a) * .7f, (float) Math.sin(a) * .5f);
            Vector3f mid = new Vector3f(centre).add(new Vector3f(dir).mul(len * .5f)).add((float) (hash(seed + k * 9) - .5) * .02f, 0, 0);
            Vector3f end = new Vector3f(centre).add(new Vector3f(dir).mul(len));
            m.transformPosition(mid); m.transformPosition(end);
            line(v, view, c0, mid, .006f, alpha);
            line(v, view, mid, end, .004f, alpha * .7f);
        }
    }
    private static void line(VertexConsumer v, Matrix4f view, Vector3f a, Vector3f b, float w, float alpha) {
        float dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        Vector3f side = new Vector3f(dy, -dx, 0).cross(dx, dy, dz);
        if (side.lengthSquared() < 1e-10f) side.set(0, 1, 0);
        side.normalize(w);
        VA.set(a).add(side); VB.set(a).sub(side); VC.set(b).sub(side); VD.set(b).add(side);
        PursuitShade.flat(v, view, VA, VB, VC, VD, 0xdfe8f0, alpha, alpha, alpha * .6f, alpha * .6f);
    }

    // ------------------------------------------------------------------ lights
    /** His car's lamps: the headlamps' glare and the light they throw on the road, the tail-lights, the instruments. */
    static void heroLights(FilmContext c, float tau) {
        Car car = car(tau);
        Damage dmg = damage(tau);
        if (dmg.wreck() > .5f) return;
        float on = 1 - dmg.crush() * .7f;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 head = car.world(side * .78, .63, 2.36 - .3 * dmg.crush());
            FilmFx.glow(c, head, .35, 0xffffff, .9f * on);
            FilmFx.glow(c, head, 1.3, 0xcfe0ff, .25f * on);
            Vec3 tail = car.world(side * .55, .81, -2.39);
            FilmFx.glow(c, tail, .3, 0xff2020, .8f);
            FilmFx.glow(c, tail, 1.1, 0xff1010, .25f);
            if (!car.flying()) PursuitCity.smear(c, (float) tail.x, (float) tail.z, 0xff2020, .14f, .5f, c.camera().x, c.camera().z);
        }
        // The light the headlamps throw on the road ahead.
        if (!car.flying() && dmg.crush() < .5f) {
            Vec3 pool = car.world(0, .02, 9);
            FilmFx.glow(c, new Vec3(pool.x, .03, pool.z), 5.5, 0xbfd2ff, .07f);
        }
        // The instruments: a cold glow under the wheel and on the screen in the middle.
        if (dmg.wreck() <= 0) {
            FilmFx.glow(c, car.world(.40, .86, .95), .22, 0x6fd8ff, .55f);
            FilmFx.glow(c, car.world(0, .80, .98), .18, 0x9ab8ff, .35f);
        }
    }

    // ------------------------------------------------------------------ the car behind and the traffic
    /** The dark SUV that tails him (his landing on its roof bounces it). */
    static void suv(FilmContext c, float tau) {
        if (!suvShown(tau)) return;
        Car car = PursuitPath.suv(tau);
        body(c, car.matrix(), SUV, 0x1a1c21, car.wheelSpin(), true, false, car.pos());
    }
    /** Every car of the traffic near the camera. */
    static void traffic(FilmContext c, float tau, float time) {
        double camZ = c.camera().z;
        for (Traffic t : TRAFFIC) {
            double z = t.z(tau);
            if (z < camZ - 80 || z > camZ + 230) continue;
            // Never draw a car round the camera itself (a shot running alongside in its lane).
            double dx = t.x() - c.camera().x, dz = z - camZ;
            if (dx * dx + dz * dz < 18 && c.camera().y < 3.2) continue;
            Matrix4f m = new Matrix4f().translation(t.x(), .004f * (float) Math.sin(tau * 3 + t.seed()), (float) z);
            if (t.oncoming()) m.rotateY(Mth.PI);
            m.scale(S);
            body(c, m, t.kind(), t.colour(), (float) (z / WHEEL_R), true, t.braking(tau) && ((int) (time / 6) & 1) == 0, new Vec3(t.x(), .8, z));
        }
    }
    /**
     * A plain car of the traffic (or the car behind): the same lofted body scaled to its kind, dark glass, wheels,
     * its lamps lit (and its hazards blinking once it stops).
     */
    private static void body(FilmContext c, Matrix4f m, int kind, int colour, float spin, boolean lights, boolean hazards, Vec3 centre) {
        Matrix4f view = c.pose().last().pose();
        float fade = 1 - PursuitShade.fog(centre.x, centre.y, centre.z);
        if (fade < .02f) return;
        double dist = centre.distanceTo(c.camera());
        // Far off, a car is a body, a cabin and its lamps; close by, the full loft and real wheels.
        boolean near = dist < 45;
        PursuitShade.lightsNear(centre.x, centre.y, centre.z, near ? 16 : 10);
        VertexConsumer v = PursuitShade.solid(c);
        float len = kind == BUS ? 2.5f : kind == TRUCK ? 1.6f : kind == VAN ? 1.08f : kind == SUV ? 1.05f : 1f;
        if (dist > 90) {
            Matrix4f far = new Matrix4f(m).scale(1, 1, len);
            float roofY = kind == BUS || kind == TRUCK ? 2.6f : kind == VAN ? 2.1f : kind == SUV ? 1.8f : 1.38f;
            PursuitShade.box(v, view, far, -.92f, .25f, -2.35f, .92f, .86f, 2.35f, colour, .5f);
            if (kind != BUS && kind != TRUCK && kind != VAN) PursuitShade.box(v, view, far, -.7f, .86f, -1.4f, .7f, roofY, .9f, GLASS, .8f);
            else PursuitShade.box(v, view, far, -.92f, .86f, -2.35f, .92f, roofY, 2.35f, colour, .4f);
            lamps(c, m, len, .9f, lights, hazards, fade);
            return;
        }
        float tall = kind == BUS ? 2.2f : kind == TRUCK ? 2.0f : kind == VAN ? 1.55f : kind == SUV ? 1.3f : 1f;
        Matrix4f s = new Matrix4f(m).scale(kind == BUS || kind == TRUCK ? 1.25f : kind == SUV ? 1.05f : 1, 1, len);
        if (kind == BUS || kind == TRUCK || kind == VAN) {
            // A box on wheels: the bus's lit windows, the lorry's cab and load.
            float h = 1.4f * tall;
            PursuitShade.box(v, view, s, -.93f, .3f, -2.3f, .93f, h, 2.3f, colour, .4f);
            if (kind == TRUCK) PursuitShade.box(v, view, s, -.93f, .3f, 2.3f, .93f, 2.2f, 2.38f, 0x2a2c30, .4f);
            wheels(v, view, new Matrix4f(m).scale(1.1f, 1, 1), spin, 0, 4, len, near ? 10 : 6);
            VertexConsumer a = PursuitShade.add(c);
            if (kind == BUS) for (int side = -1; side <= 1; side += 2)
                PursuitShade.flat(a, view, s, side * .94f, 1.35f, -2.1f, side * .94f, 1.35f, 2.1f, side * .94f, 2.45f, 2.1f, side * .94f, 2.45f, -2.1f, 0xffe6b8, .5f * fade);
            lamps(c, m, len, 1.05f, lights, hazards, fade);
            return;
        }
        lowerBody(v, view, s, colour, 0, 1, true, near ? 18 : 8);
        // A simple glass house: dark glass all round, a roof.
        float roof = kind == SUV ? SUV_ROOF / 1.0f : 1.40f, belt = kind == SUV ? .98f : .87f;
        float fz = kind == SUV ? 1.05f : .42f, bz = kind == SUV ? -1.9f : -.82f, fzb = kind == SUV ? 1.55f : 1.18f, bzb = kind == SUV ? -2.25f : -1.62f;
        PursuitShade.quad(v, view, s, -.68f, roof, fz, .68f, roof, fz, .68f, roof, bz, -.68f, roof, bz, colour, .7f);
        if (kind == SUV) PursuitShade.box(v, view, s, -.93f, .86f, -2.3f, .93f, belt, 1.6f, colour, .6f);
        PursuitShade.quad(v, view, s, -.76f, belt, fzb, .76f, belt, fzb, .68f, roof, fz, -.68f, roof, fz, GLASS, .95f);
        PursuitShade.quad(v, view, s, -.80f, belt + .1f, bzb, .80f, belt + .1f, bzb, .68f, roof, bz, -.68f, roof, bz, GLASS, .95f);
        for (int side = -1; side <= 1; side += 2)
            PursuitShade.quad(v, view, s, side * .905f, belt, bzb + .4f, side * .905f, belt, fzb - .08f, side * .685f, roof, fz, side * .685f, roof, bz, GLASS, .95f);
        if (kind == TAXI) {
            PursuitShade.box(v, view, s, -.25f, roof, -.2f, .25f, roof + .22f, .15f, 0xe08a20, .3f);
            FilmFx.glow(c, vec(m.transformPosition(new Vector3f(0, roof + .12f, 0))), .5, 0xffb040, .5f * fade);
        }
        wheels(v, view, m, spin, 0, 4, len, near ? 10 : 6);
        lamps(c, m, len, belt, lights, hazards, fade);
    }
    /** Head- and tail-lights of a plain car, the hazards blinking, their smears in the wet road. */
    private static void lamps(FilmContext c, Matrix4f m, float len, float y, boolean on, boolean hazards, float fade) {
        if (!on) return;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 head = vec(m.transformPosition(new Vector3f(side * .7f, .62f, 2.36f * len)));
            Vec3 tail = vec(m.transformPosition(new Vector3f(side * .65f, .8f, -2.37f * len)));
            FilmFx.glow(c, head, .3, 0xffffff, .8f * fade);
            FilmFx.glow(c, head, 1.1, 0xd6e4ff, .18f * fade);
            FilmFx.glow(c, tail, .25, 0xff2a2a, .75f * fade);
            FilmFx.glow(c, tail, .9, 0xff1a1a, .2f * fade);
            if (hazards) FilmFx.glow(c, tail.add(0, .05, 0), .7, 0xffa020, .7f * fade);
            PursuitCity.smear(c, (float) head.x, (float) head.z, 0xdfe8ff, .12f * fade, .6f, c.camera().x, c.camera().z);
            PursuitCity.smear(c, (float) tail.x, (float) tail.z, 0xff2020, .1f * fade, .5f, c.camera().x, c.camera().z);
        }
    }
    private static Vec3 vec(Vector3f v) { return new Vec3(v.x, v.y, v.z); }
}
