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
 * Everything of THE FINAL PURSUIT that is not a body or a car: the muzzle flashes, tracers and spent cases; what
 * each bullet does where it lands (the suit drinking it in violet light and a Wakandan lattice, sparks off metal,
 * glass bursting); the holes through the roof and the city's light falling in through them; the claws biting the
 * metal, the tearing, the glass of the back windows; the charge gathering; the release (a white core, the sphere,
 * its lattice, the ring along the road, the glass blown out, the nose digging sparks and asphalt out of the road);
 * his landing (cracks, the skid's sparks and marks); the crash (the flash, the violet, the fireball, the black
 * smoke, the debris of every kind); the dust that buries the road and the wind that parts it round him; the last
 * motes of energy. All of it worked out from scene time, so the slow motion slows every piece of it.
 */
final class PursuitFx {
    private PursuitFx() {}

    static final int VIOLET = 0x9a5cff, VIOLET_DEEP = 0x4a1cb0, BLUE = 0x6f8cff, WHITE = 0xffffff, SPARK = 0xffc36a, FIRE = 0xff8a2a, SMOKE = 0x16130f, DUST = 0x857a6c;
    private static final float G = .0245f;

    // ------------------------------------------------------------------ the lights the effects throw (for shading)
    /** Lights the effects add this moment: the muzzle flashes, the release, the fire. */
    static void lights(float tau, float time) {
        PursuitShade.EXTRA.clear();
        PursuitShade.flashR = PursuitShade.flashG = PursuitShade.flashB = 0;
        for (Shot s : SHOTS) {
            float d = tau - s.time();
            if (d < 0 || d > 2) continue;
            Vec3 m = PursuitThug.muzzle;
            float k = 1 - d / 2;
            PursuitShade.EXTRA.add(new PursuitShade.Light((float) m.x, (float) m.y, (float) m.z, 1.6f * k, 1.2f * k, .6f * k, 6));
        }
        float d = tau - BOOM;
        if (d >= 0 && d < 16) {
            float k = d < 1 ? 1 : (float) Math.exp(-(d - 1) / 4);
            Vec3 at = blastCentre();
            PursuitShade.EXTRA.add(new PursuitShade.Light((float) at.x, (float) at.y, (float) at.z, 2.2f * k, 1.4f * k, 3.4f * k, 40));
            PursuitShade.flashR = .25f * k; PursuitShade.flashG = .15f * k; PursuitShade.flashB = .45f * k;
        }
        float f = tau - BOOM - CRASH;
        if (f >= 0) {
            Vec3 at = CRASH_POS;
            float burst = f < 2 ? 1 : (float) Math.exp(-(f - 2) / 10);
            float flick = .75f + .25f * (float) Math.sin(time * 1.7) * (float) Math.sin(time * 2.9 + 1);
            float fire = .9f * flick * (1 - .5f * clamp((f - 120) / 200));
            PursuitShade.EXTRA.add(new PursuitShade.Light((float) at.x, 1.2f, (float) at.z, 3f * burst + 1.6f * fire, 1.6f * burst + .7f * fire, .9f * burst + .2f * fire, 18 + 20 * burst));
            if (f < 3) { float k = 1 - f / 3; PursuitShade.flashR += .35f * k; PursuitShade.flashG += .28f * k; PursuitShade.flashB += .2f * k; }
        }
    }

    // ------------------------------------------------------------------ solid bits (cases, debris, chunks)
    static void solid(FilmContext c, float tau, float time) {
        Matrix4f view = c.pose().last().pose();
        VertexConsumer v = PursuitShade.solid(c);
        // Spent cases out of the pistol, tumbling: outside the car the wind whips them away behind it; inside they drop.
        if (tau < BOOM) {
            Car car = car(tau);
            Vec3 from = toLocal(car, PursuitThug.muzzle.subtract(PursuitThug.aim.scale(.25)));
            for (Shot s : SHOTS) {
                float d = tau - s.time();
                if (d < 0 || d > 14) continue;
                boolean outside = s.time() < ULT_EXIT;
                double x = from.x + (outside ? .06 : -.04) * d, y = from.y + .09 * d - .5 * G * d * d, z = from.z;
                if (outside) z -= V1 * (d - (1 - Math.exp(-.15 * d)) / .15);
                else y = Math.max(.3, y);
                spin(v, view, car.world(x, y, z), .018f, .045f, d * .9f + s.time(), 0xc8a24a, .9f);
            }
        }
        float d = tau - BOOM;
        // The release: the left mirror blown off, the bumper torn away by the dig, the asphalt it gouges up.
        if (d >= 0) {
            Car at = car(BOOM);
            piece(v, view, at.world(1.0, .98, 1.08), new Vec3(.35, .28, V1 - .2), d, 0, 1, .09f, PursuitCar.PAINT, .7f, 3);
            Car dug = car(BOOM + 2.2f);
            if (d > 2.2f) piece(v, view, dug.world(0, .3, 2.3), new Vec3(-.05, .22, .5), d, 2.2f, 2, .32f, PursuitCar.PAINT, .7f, 7);
            for (int i = 0; i < 16; i++) {
                float born = 1.4f + 4.2f * (float) hash(i * 1.7);
                if (d < born) continue;
                Vec3 nose = car(BOOM + born).world(NOSE);
                Vec3 vel = new Vec3((hash(i * 2.3) - .5) * .25, .12 + .25 * hash(i * 3.1), .3 + .5 * hash(i * 4.9));
                piece(v, view, new Vec3(nose.x, .05, nose.z), vel, d, born, 5 + i, .05f + .09f * (float) hash(i * 5.3), 0x2a2b2e, 0, 11 + i);
            }
        }
        // The crash: panels, a wheel, glass, small parts, asphalt; burning.
        float f = d - CRASH;
        if (f >= 0) {
            int n = 34;
            for (int i = 0; i < n; i++) {
                double h = hash(i * 7.31);
                double a = hash(i * 2.11) * Math.PI * 2, up = .15 + .5 * hash(i * 3.9);
                double speed = .25 + .6 * hash(i * 5.7);
                Vec3 vel = new Vec3(Math.cos(a) * speed, up, Math.sin(a) * speed + .4);
                int col = h < .35 ? PursuitCity.mix(PursuitCar.PAINT, 0x2b2623, (float) hash(i)) : h < .55 ? 0x1c1d20 : h < .7 ? 0x3a3c40 : 0x2a2b2e;
                float size = h < .35 ? .25f + .3f * (float) hash(i * 8.1) : .06f + .12f * (float) hash(i * 9.3);
                piece(v, view, CRASH_POS.add(0, .3, 0), vel, f, 0, 40 + i, size, col, h < .35 ? .7f : .3f, 50 + i);
            }
            // The lost wheel, bouncing away down the road.
            Vec3 w = bounce(CRASH_POS.add(-.8, .2, -1.2), new Vec3(-.22, .32, .55), f, .45f);
            Matrix4f wm = new Matrix4f().translation((float) w.x, (float) w.y + WHEEL_R, (float) w.z).rotateY(.5f).rotateX(f * .45f).rotateZ(.3f * (float) Math.sin(f * .2));
            PursuitShade.lightsNear(w.x, 1, w.z, 16);
            PursuitCar.wheel(PursuitShade.solid(c), view, wm, 1);
        }
    }
    /** One flying piece: from where, its velocity, ticks since the start, when it set off, its spin, size, colour, gloss. */
    private static void piece(VertexConsumer v, Matrix4f view, Vec3 from, Vec3 vel, float now, float born, int seed, float size, int colour, float gloss, int spinSeed) {
        float s = now - born;
        if (s < 0) return;
        Vec3 at = bounce(from, vel, s, .3f);
        // Spinning fast as it leaves, slowing as it bounces and slides to rest (never jumping).
        float spinTurn = (float) (1 - Math.exp(-s / 30)) * 30 * (.3f + .5f * (float) hash(spinSeed));
        spin(v, view, at, size, size * (.3f + .7f * (float) hash(seed * 1.3)), spinTurn + seed, colour, gloss);
    }
    /** A ballistic path that bounces on the road (twice, losing most of its speed) and then slides to rest. */
    static Vec3 bounce(Vec3 p0, Vec3 v0, float s, float keep) {
        double x = p0.x, y = p0.y, z = p0.z, vx = v0.x, vy = v0.y, vz = v0.z;
        float t = s;
        for (int b = 0; b < 3; b++) {
            double hit = (vy + Math.sqrt(vy * vy + 2 * G * Math.max(0, y))) / G;
            if (t < hit) return new Vec3(x + vx * t, y + vy * t - .5 * G * t * t, z + vz * t);
            x += vx * hit; z += vz * hit; y = 0;
            t -= hit;
            vy = -(vy - G * hit) * keep;
            vx *= .6; vz *= .6;
            if (vy < .02) break;
        }
        double slide = Math.min(t, 12);
        double k = 1 - slide / 24;
        return new Vec3(x + vx * slide * k, 0, z + vz * slide * k);
    }
    /** A small tumbling box. */
    private static void spin(VertexConsumer v, Matrix4f view, Vec3 at, float a, float b, float angle, int colour, float gloss) {
        PursuitShade.lightsNear(at.x, at.y, at.z, 12);
        Matrix4f m = new Matrix4f().translation((float) at.x, (float) at.y + a * .5f, (float) at.z).rotateXYZ(angle * 1.3f, angle * .7f, angle * .4f);
        PursuitShade.box(v, view, m, -a, -b * .3f, -b, a, b * .3f, b, colour, gloss);
    }

    // ------------------------------------------------------------------ light (additive)
    static void light(FilmContext c, float tau, float time) {
        shots(c, tau);
        roofLight(c, tau, time);
        claws(c, tau);
        charge(c, tau, time);
        release(c, tau, time);
        landing(c, tau);
        crash(c, tau, time);
    }

    /** Muzzle flashes, tracers, and what each bullet does where it lands. */
    private static void shots(FilmContext c, float tau) {
        Vec3 muzzle = PursuitThug.muzzle, aim = PursuitThug.aim;
        for (Shot s : SHOTS) {
            float d = tau - s.time();
            if (d < -.4f || d > 12) continue;
            Vec3 target = target(s, tau);
            if (d < 1.6f) {
                // The flash: a hot star at the muzzle, a cone of fire along the barrel.
                float k = d < 0 ? 0 : 1 - d / 1.6f;
                FilmFx.glow(c, muzzle, .45 * k + .1, 0xfff0c0, .9f * k);
                FilmFx.glow(c, muzzle, 1.2 * k, 0xff9a40, .35f * k);
                FilmFx.streak(c, muzzle, muzzle.add(aim.scale(.45 * k + .1)), .12 * k, 0xffe0a0, .9f * k, 0, true);
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2 + s.time();
                    Vec3 side = aim.cross(new Vec3(0, 1, 0)).normalize().scale(Math.cos(a)).add(new Vec3(0, Math.sin(a), 0)).scale(.16 * k);
                    FilmFx.streak(c, muzzle, muzzle.add(side).add(aim.scale(.08)), .03, 0xffd890, .7f * k, 0, true);
                }
            }
            // The tracer: a streak flying from the gun to where it lands in less than a tick.
            if (d > -.4f && d < .5f) {
                float u = clamp((d + .4f) / .5f);
                Vec3 head = muzzle.lerp(target, u), tail = muzzle.lerp(target, Math.max(0, u - .55f));
                FilmFx.streak(c, tail, head, .025, 0xfff4d0, .1f, .95f, true);
                FilmFx.glow(c, head, .08, 0xffffff, .8f);
            }
            if (d < 0) continue;
            switch (s.hits()) {
                case 0 -> absorb(c, target, d, s.time());
                case 1 -> sparks(c, target, d, s.time(), 14, 1);
                case 2 -> sparks(c, target, d, s.time(), 8, .6f);
                default -> {
                    if (s.time() >= ULT_ROOF_FIRE) sparks(c, target.add(0, .02, 0), d, s.time(), 10, .8f);
                    else FilmFx.glow(c, target, .25, 0xcfc8bc, .3f * (1 - d / 12));
                }
            }
        }
        // The driver's window bursting at the first shot, the rear one smashed by the gun butt: glass flying, glittering.
        glassBurst(c, tau, ULT_FIRE, new Vec3(.9, 1.1, .45), new Vec3(-.25, .05, .15), 3);
        glassBurst(c, tau, ULT_SMASH, new Vec3(.92, 1.08, -.65), new Vec3(.3, .05, 0), 5);
        glassBurst(c, tau, ULT_ROOF_FREE, new Vec3(.9, 1.1, -.65), new Vec3(.3, .2, -.2), 7);
        glassBurst(c, tau, ULT_ROOF_FREE, new Vec3(-.9, 1.1, -.65), new Vec3(-.3, .2, -.2), 9);
    }
    /** Where a shot lands at scene time tau (it stays on what it hit: his suit, the car). */
    static Vec3 target(Shot s, float tau) {
        if (s.hits() == 0) {
            Place p = panther(Math.min(tau, BOOM - .01f));
            return PursuitRig.region(p.matrix(), PursuitMoves.panther(Math.min(tau, BOOM - .01f), tau), s.region(), s.side());
        }
        return car(Math.min(tau, BOOM - .01f)).world(s.target());
    }
    /** The suit drinking a bullet: a violet flash, a hexagonal lattice spreading over the suit, light running off along its lines. */
    private static void absorb(FilmContext c, Vec3 at, float d, float seed) {
        if (d > 10) return;
        float k = (float) Math.exp(-d / 2.2f);
        FilmFx.glow(c, at, .18 + .1 * k, WHITE, .9f * k);
        FilmFx.glow(c, at, .55 + .5 * (1 - k), VIOLET, .55f * k);
        Vec3 n = at.subtract(c.camera()).normalize().scale(-1);
        Vec3 u = n.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-4) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 w = n.cross(u).normalize();
        float r = .05f + .32f * (1 - (float) Math.exp(-d / 2.5f));
        // Two rings of the lattice, the inner turned against the outer.
        for (int ring = 0; ring < 2; ring++) {
            float rr = r * (ring == 0 ? 1 : .55f), rot = ring * .52f + seed;
            Vec3 prev = null, first = null;
            for (int i = 0; i <= 6; i++) {
                double a = i * Math.PI / 3 + rot;
                Vec3 p = at.add(u.scale(Math.cos(a) * rr)).add(w.scale(Math.sin(a) * rr)).add(n.scale(.02));
                if (prev != null) FilmFx.streak(c, prev, p, .012, ring == 0 ? 0xd2b8ff : VIOLET, .8f * k, .8f * k, true);
                if (ring == 0 && i < 6 && i % 2 == 0) FilmFx.streak(c, at, p, .008, VIOLET, .5f * k, .1f * k, true);
                prev = p;
            }
        }
    }
    /** Sparks bursting off metal: bright, falling, gone in a few ticks. */
    private static void sparks(FilmContext c, Vec3 at, float d, float seed, int n, float power) {
        if (d > 10) return;
        FilmFx.glow(c, at, .2 * power, 0xfff0d0, (1 - d / 3) * .9f);
        for (int i = 0; i < n; i++) {
            double h = hash(seed * 3.1 + i * 7.7);
            float life = 4 + 6 * (float) hash(seed + i * 1.3);
            if (d > life) continue;
            double a = hash(seed * 1.7 + i * 2.9) * Math.PI * 2, up = hash(seed + i * 5.3);
            double sp = (.06 + .2 * h) * power;
            Vec3 vel = new Vec3(Math.cos(a) * sp, up * sp * 1.2, Math.sin(a) * sp);
            Vec3 p = at.add(vel.scale(d)).add(0, -.5 * G * d * d, 0), q = at.add(vel.scale(Math.max(0, d - .9))).add(0, -.5 * G * Math.max(0, d - .9) * Math.max(0, d - .9), 0);
            float k = 1 - d / life;
            FilmFx.streak(c, q, p, .012, SPARK, 0, k, true);
        }
    }
    /** A window bursting: shards thrown out, the wind taking them back past the car, glittering as they turn. */
    private static void glassBurst(FilmContext c, float tau, float at, Vec3 local, Vec3 push, int seed) {
        float d = tau - at;
        if (d < 0 || d > 30) return;
        Car car = car(at);
        Vec3 origin = car.world(local);
        Vec3 drift = car.dir(push.x, push.y, push.z);
        for (int i = 0; i < 26; i++) {
            double h = hash(seed * 13.1 + i);
            Vec3 vel = drift.scale(.6 + .8 * h).add((hash(i * 3.7 + seed) - .5) * .12, (hash(i * 5.3 + seed) - .3) * .12, (hash(i * 7.1 + seed) - .5) * .12);
            // In the road's frame the car runs on; the glass, slowed by the air, falls behind it.
            double carried = V1 * (1 - Math.exp(-.12 * d)) / .12;
            Vec3 p = new Vec3(origin.x + vel.x * d, Math.max(.02, origin.y + vel.y * d - .5 * G * d * d), origin.z + vel.z * d + carried);
            float glint = (float) Math.max(0, Math.sin(d * (1.5 + h * 2) + i)) ;
            float k = 1 - d / 30;
            FilmFx.glow(c, p, .03 + .03 * h, 0xe8f2ff, (.3f + .7f * glint * glint) * k);
        }
    }

    /** Through the holes in the roof: the city's light falling in, in shafts, changing as the car runs under the lamps and signs. */
    private static void roofLight(FilmContext c, float tau, float time) {
        if (tau < ULT_ROOF_FIRE || roofGone(tau)) return;
        Car car = car(tau);
        for (Hole h : HOLES) {
            if (tau < h.time()) continue;
            Vec3 top = car.world(h.x(), ROOF_Y + .01, h.z()), down = car.world(h.x() * .9, ROOF_Y - 1.0, h.z() + .15);
            PursuitShade.lightsNear(top.x, top.y + 3, top.z, 14);
            float[] amb = PursuitShade.ambient(top.x, top.y + 3, top.z);
            int col = PursuitShade.pack(.25f + amb[0] * .8f, .25f + amb[1] * .8f, .3f + amb[2] * .8f);
            FilmFx.streak(c, top, down, .05, col, .35f, 0, true);
            FilmFx.glow(c, top, .06, col, .8f);
            // The torn metal round the hole, petals bent up and out, catching the light.
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2 + h.time();
                Vec3 tip = car.world(h.x() + Math.cos(a) * .05, ROOF_Y + .035, h.z() + Math.sin(a) * .05);
                FilmFx.streak(c, top, tip, .012, 0xc8ccd4, .6f, .3f, true);
            }
        }
    }
    /** The claws biting through the roof: a shower of sparks, and the punctures they leave glinting at their torn edges. */
    private static void claws(FilmContext c, float tau) {
        for (int side = 0; side < 2; side++) {
            float hit = side == 0 ? ULT_CLAW_R : ULT_CLAW_L;
            if (tau < hit || tau > ULT_ROOF_FREE + 100) continue;
            float d = tau - hit;
            PantherMotion.Pose pose = PursuitMoves.panther(hit, hit);
            Place place = panther(hit);
            Car then = car(hit), now = car(Math.min(tau, BOOM - .01f));
            for (int f = 0; f < 4; f++) {
                Vec3 tip = PursuitRig.claw(place.matrix(), pose, side, f);
                // Keep the puncture on the car (it moves on; the hole goes with it).
                Vec3 local = toLocal(then, tip);
                Vec3 at = roofGone(tau) ? null : now.world(local.x, ROOF_Y + .02, local.z);
                if (at != null) FilmFx.streak(c, at.add(0, 0, -.06), at.add(0, 0, .06), .02, 0xd8dce4, .5f, .5f, true);
                if (d < 9 && at != null) sparks(c, at, d, hit + f * 3, 7, 1.1f);
            }
        }
        // The tearing: sparks shrieking off the edge as it gives, metal fragments thrown back.
        if (tau >= ULT_TEAR && tau < ULT_ROOF_FREE + 12) {
            Car car = car(tau);
            float peel = peel(Math.min(tau, ULT_ROOF_FREE - .01f));
            for (int i = 0; i < 6; i++) {
                float t0 = ULT_TEAR + i * 2;
                if (tau < t0) continue;
                float x = (float) (hash(i * 3.3) - .5) * 1.2f;
                Vector3f edge = PursuitCar.sheet(Math.min(tau, ULT_ROOF_FREE - .01f), peel, 1, x, 0);
                Vec3 at = car.world(edge.x, edge.y, edge.z);
                sparks(c, at, tau - t0, t0 + i, 9, 1.3f);
            }
        }
    }
    private static Vec3 toLocal(Car car, Vec3 world) { return car.local(world); }

    /** The charge: motes of violet lifting off the suit, more and faster; at the held breath the air drawn in round him. */
    private static void charge(FilmContext c, float tau, float time) {
        if (tau < ULT_CHARGE || tau > BOOM + 1) return;
        Place p = panther(tau);
        PantherMotion.Pose pose = PursuitMoves.panther(tau, time);
        Matrix4f body = p.matrix();
        Vec3 chest = PursuitRig.chest(body, pose);
        float k = ease((tau - ULT_CHARGE) / (BOOM - ULT_CHARGE));
        FilmFx.glow(c, chest, 1.2 + 1.8 * k, VIOLET, .12f + .18f * k);
        int n = (int) (8 + 30 * k);
        for (int i = 0; i < n; i++) {
            float life = 14, age = (tau * (1 + k) + i * 3.7f) % life;
            int bone = new int[]{PursuitRig.CHEST, PursuitRig.R_UPPER, PursuitRig.L_UPPER, PursuitRig.R_THIGH, PursuitRig.L_THIGH, PursuitRig.R_FOREARM, PursuitRig.L_FOREARM}[i % 7];
            Vec3 from = PursuitRig.point(body, pose, bone, (float) (hash(i * 1.3) - .5) * 4, (float) hash(i * 2.7) * 4, -2);
            Vec3 at = from.add((hash(i * 3.1) - .5) * .3 * age / life, .5 * age / life, (hash(i * 4.3) - .5) * .3 * age / life);
            FilmFx.glow(c, at, .05 + .04 * k, i % 3 == 0 ? WHITE : VIOLET, .7f * (1 - age / life) * (.4f + .6f * k));
        }
        // The held breath: rings of air drawn in toward him.
        if (tau > ULT_HOLD) {
            float h = (tau - ULT_HOLD) / (BOOM - ULT_HOLD);
            for (int r = 0; r < 3; r++) {
                float rr = 3.5f * (1 - ((h * 1.6f + r / 3f) % 1));
                PantherFx.ring(c, chest, new Vec3(0, 1, 0), rr, .03 + .02 * rr, 0xd8c8ff, .25f * (1 - rr / 3.5f) * h);
            }
        }
    }

    /** The release and what it does to the car: core, sphere, lattice, the ring along the road, glass out, sparks and asphalt from the nose. */
    private static void release(FilmContext c, float tau, float time) {
        float d = tau - BOOM;
        if (d < 0 || d > 40) return;
        Vec3 at = blastCentre();
        // The core: white, enormous for an instant.
        if (d < 4) {
            float k = 1 - d / 4;
            FilmFx.glow(c, at, 1.5 + 4 * (1 - k), WHITE, .95f * k);
            FilmFx.glow(c, at, 4 + 6 * (1 - k), 0xd8c8ff, .55f * k);
        }
        float out = 1 - (float) Math.pow(1 - Math.min(1, d / 8f), 3);
        double r = 10 * out;
        float fade = d < 8 ? 1 : Math.max(0, 1 - (d - 8) / 8f);
        if (fade > 0) {
            PantherFx.sphere(c, at, r, VIOLET, .55f * fade);
            PantherFx.sphere(c, at, r * .8, BLUE, .25f * fade);
            FilmFx.glow(c, at, r * 1.2, VIOLET_DEEP, .1f * fade);
            PantherFx.lattice(c, at, r, time, .8f * fade);
            FilmFx.ring(c, new Vec3(at.x, .05, at.z), r * 1.35, .5, VIOLET, .55f * fade, true);
            FilmFx.ring(c, new Vec3(at.x, .05, at.z), r * 1.35, .15, WHITE, .5f * fade, true);
        }
        // The glass of every window blown out together, glittering in the slow motion.
        if (d < 30) {
            Car car = car(BOOM);
            for (int i = 0; i < 60; i++) {
                double h = hash(i * 4.13);
                Vec3 from = car.world((hash(i * 1.7) - .5) * 1.8, .95 + .4 * hash(i * 2.9), (hash(i * 3.3) - .5) * 3.2);
                Vec3 dir = from.subtract(at).normalize();
                Vec3 p = from.add(dir.scale((.25 + .35 * h) * d)).add(0, -.5 * G * d * d, V1 * .7 * d);
                if (p.y < .02) continue;
                float glint = (float) Math.max(0, Math.sin(d * (.8 + h * 1.6) + i));
                FilmFx.glow(c, p, .035 + .03 * h, 0xeaf2ff, (.25f + .75f * glint * glint) * (1 - d / 30));
            }
        }
        // The nose dug into the road: a fan of sparks off the scraping bumper.
        if (d > 1.2f && d < DIG + 6) {
            for (int i = 0; i < 22; i++) {
                float born = 1.2f + (DIG - 1.2f) * (float) hash(i * 2.7);
                float s = d - born;
                if (s < 0 || s > 6) continue;
                Vec3 nose = car(BOOM + born).world(NOSE);
                Vec3 vel = new Vec3((hash(i * 1.9) - .5) * .35, .05 + .2 * hash(i * 3.3), .5 + .6 * hash(i * 4.1));
                Vec3 p = new Vec3(nose.x, .03, nose.z).add(vel.scale(s)).add(0, -.5 * G * s * s, 0);
                Vec3 q = new Vec3(nose.x, .03, nose.z).add(vel.scale(Math.max(0, s - 1.2)));
                FilmFx.streak(c, q, p, .016, SPARK, 0, 1 - s / 6, true);
            }
        }
    }

    /** His landing: a violet pulse into the road, cracks running out, the skid's sparks and its marks. */
    private static void landing(FilmContext c, float tau) {
        float d = tau - BOOM - LAND;
        if (d < 0) return;
        if (d < 10) {
            float k = 1 - d / 10;
            FilmFx.glow(c, LAND_POS.add(0, .3, 0), 1.2 + d * .2, VIOLET, .4f * k);
            FilmFx.ring(c, LAND_POS.add(0, .04, 0), .5 + d * .45, .25, VIOLET, .5f * k, true);
        }
        if (d < SKID + 2) {
            Place p = panther(tau);
            PantherMotion.Pose pose = PursuitMoves.panther(tau, tau);
            Vec3 hand = PursuitRig.claw(p.matrix(), pose, 0, 1);
            for (int i = 0; i < 10; i++) {
                float s = (d * 3 + i * .37f) % 3;
                Vec3 vel = new Vec3((hash(i * 2.1 + Math.floor(d)) - .5) * .2, .08 + .12 * hash(i * 3.7), -.15 - .2 * hash(i * 4.9));
                Vec3 from = new Vec3(hand.x, .05, hand.z);
                Vec3 at = from.add(vel.scale(s)).add(0, -.5 * G * s * s, 0), back = from.add(vel.scale(Math.max(0, s - .8)));
                FilmFx.streak(c, back, at, .014, SPARK, 0, (1 - s / 3) * (1 - clamp((d - SKID) / 2)), true);
            }
        }
    }

    /**
     * The crash, layer by layer so the colours never all hit at once: the impact flash, the violet left in the car,
     * the fireball (white, yellow, orange, red), then fire burning on in the wreck.
     */
    private static void crash(FilmContext c, float tau, float time) {
        float f = tau - BOOM - CRASH;
        if (f < 0) return;
        Vec3 at = CRASH_POS.add(0, .4, 0);
        if (f < 2.5f) {
            float k = 1 - f / 2.5f;
            FilmFx.glow(c, at, 3 + 4 * (1 - k), WHITE, .9f * k);
        }
        if (f > .5f && f < 10) {
            float k = window(f, .5f, 10, 2.5f);
            FilmFx.glow(c, at, 2.5 + f * .5, VIOLET, .55f * k);
            PantherFx.sphere(c, at, 1 + f * .5, VIOLET, .35f * k);
        }
        // The fireball: puffs of fire swelling and rising, white at the start, then yellow, orange, red.
        if (f > 1.5f && f < 45) {
            for (int i = 0; i < 26; i++) {
                double a = hash(i * 3.17) * Math.PI * 2, rr = hash(i * 1.91);
                float born = 1.5f + 3 * (float) hash(i * 5.3), s = f - born;
                if (s < 0) continue;
                float life = 22 + 18 * (float) hash(i * 6.7);
                if (s > life) continue;
                double spread = 2.6 * (1 - Math.exp(-s / 6));
                Vec3 p = at.add(Math.cos(a) * rr * spread, .3 + s * .09 + rr * 1.2, Math.sin(a) * rr * spread);
                float u = s / life;
                int col = u < .12f ? 0xfff2c8 : u < .3f ? 0xffc040 : u < .55f ? FIRE : 0xc8381a;
                FilmFx.glow(c, p, 1.1 + 2.2 * Math.sqrt(u), col, .55f * (1 - u));
            }
        }
        // The wreck burning: flames licking up, flickering, for as long as the film lasts.
        if (f > 6) {
            Car wreck = car(tau);
            float grow = clamp((f - 6) / 10);
            for (int i = 0; i < 10; i++) {
                Vec3 base = wreck.world((hash(i * 2.3) - .5) * 1.4, .6, .8 + (hash(i * 4.1) - .5) * 2.2);
                float flick = (float) (.6 + .4 * Math.sin(time * (2.3 + hash(i)) + i * 1.7));
                for (int k = 0; k < 3; k++) {
                    float rise = ((time * .35f + i * .31f + k * .33f) % 1);
                    Vec3 p = base.add(0, rise * 1.6 + .1, 0);
                    int col = rise < .3f ? 0xffd070 : rise < .6f ? FIRE : 0xb8301a;
                    FilmFx.glow(c, p, (.45 + .4 * (1 - rise)) * grow, col, .5f * flick * (1 - rise) * grow);
                }
            }
            FilmFx.glow(c, wreck.world(0, .8, .6), 3.5 * grow, 0xff7a20, .16f * grow);
        }
    }

    // ------------------------------------------------------------------ matter in the air (translucent, last)
    static void soft(FilmContext c, float tau, float time, Vec3 revealFrom) {
        float d = tau - BOOM;
        // Grit kicked up by his landing on the roof and on the road.
        if (d >= LAND && d < LAND + 40) {
            float s = d - LAND;
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14 + hash(i);
                Vec3 p = LAND_POS.add(Math.cos(a) * (.5 + s * .12), .2 + s * .02, Math.sin(a) * (.5 + s * .12));
                FilmFx.puff(c, p, .5 + s * .05, 0x6e665c, .35f * (1 - s / 40));
            }
        }
        // The asphalt dust off the dig.
        if (d > 1.5f && d < 40) {
            float s = d - 1.5f;
            Vec3 at = car(BOOM + 2).world(NOSE);
            for (int i = 0; i < 10; i++) {
                double a = hash(i * 3.9) * Math.PI;
                FilmFx.puff(c, new Vec3(at.x + Math.cos(a) * s * .07, .3 + s * .03, at.z + s * .3 + Math.sin(a) * s * .04), .5 + s * .06, 0x5a5550, .3f * (1 - s / 40));
            }
        }
        float f = d - CRASH;
        if (f < 0) return;
        // The black smoke of the burning wreck, rising and leaning with the air.
        Car wreck = car(tau);
        for (int i = 0; i < 30; i++) {
            float life = 90, age = (f * .8f + i * 3.1f) % life;
            if (f < age) continue;
            Vec3 base = wreck.world((hash(i * 1.3) - .5) * 1.2, .9, .5);
            Vec3 p = base.add(age * .03 + Math.sin(age * .05 + i) * .3, age * .085, age * .02);
            FilmFx.puff(c, p, .8 + age * .045, i % 3 == 0 ? 0x2a221c : SMOKE, .5f * clamp(age / 4) * (1 - age / life));
        }
        // The dust of the crash: a wall of it rolling out over the road, then the wind parting it round him.
        float part = ease((PursuitPath.real(tau) - PART_FROM) / 30);
        for (int i = 0; i < 150; i++) {
            float born = 6 * (float) hash(i * 2.17);
            float s = f - born;
            if (s < 0) continue;
            double a = hash(i * 1.37) * Math.PI * 2, speed = .4 + 1.3 * hash(i * 3.91);
            double travel = speed * 10 * (1 - Math.exp(-s / 14));
            Vec3 p = CRASH_POS.add(Math.cos(a) * travel, .4 + 2.5 * hash(i * 4.4) + s * .012, Math.sin(a) * travel * .9 - travel * .35);
            float size = (float) (1.2 + 3.8 * (1 - Math.exp(-s / 25)));
            float alpha = .62f * clamp(s / 3) * (1 - clamp((s - 120) / 60));
            if (revealFrom != null && part > 0) {
                // Pushed aside from the line between the camera and him, thinning there most.
                Vec3 heroAt = REST_FEET;
                double lx = p.x - heroAt.x;
                double alongZ = p.z - revealFrom.z;
                double near = Math.exp(-lx * lx / 30);
                double side = lx >= 0 ? 1 : -1;
                p = p.add(side * part * 7 * near + side * part * 1.5, part * .5, 0);
                alpha *= 1 - .85f * part * (float) near * (float) (alongZ > -4 ? 1 : .5);
            }
            if (alpha < .01f) continue;
            float lit = (float) Math.exp(-p.distanceTo(CRASH_POS) / 12);
            int col = PursuitCity.mix(DUST, 0xc87a40, .5f * lit);
            FilmFx.puff(c, p, size, col, alpha);
        }
        // The energy's last motes leaving the suit (chest, then arms, then legs), up into the air.
        float t = PursuitPath.real(tau);
        if (t > PursuitMoves.DRAIN && t < PursuitMoves.DRAIN + 40) {
            Place pl = panther(tau);
            PantherMotion.Pose pose = PursuitMoves.panther(tau, time);
            Matrix4f body = pl.matrix();
            float u = (t - PursuitMoves.DRAIN) / 40;
            for (int i = 0; i < 18; i++) {
                float start = i / 18f * .7f, s = (u - start) * 40;
                if (s < 0 || s > 16) continue;
                int bone = i < 6 ? PursuitRig.CHEST : i < 12 ? (i % 2 == 0 ? PursuitRig.R_FOREARM : PursuitRig.L_FOREARM) : (i % 2 == 0 ? PursuitRig.R_SHIN : PursuitRig.L_SHIN);
                Vec3 from = PursuitRig.point(body, pose, bone, 0, 2, -2.2f);
                Vec3 p = from.add(Math.sin(s * .4 + i) * .1, s * .06, Math.cos(s * .3 + i) * .1);
                FilmFx.glow(c, p, .05, i % 2 == 0 ? VIOLET : 0xd8c8ff, .7f * (1 - s / 16));
            }
        }
    }
    /** Film time the wind begins to part the dust. */
    static final float PART_FROM = 662;
}
