package com.FIRNI.superheromod.heroes.iceman;

import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanController.State;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanConfig.f;
import static com.FIRNI.superheromod.heroes.iceman.IcemanController.*;

/**
 * The cryogenic brush on the server (right click held, both hands raised). On a body (in his aim, within BRUSH_RANGE):
 * a stream of cold that raises their frost meter fast (their screen frosts over with it) and bites a little. Into the
 * air: ice is sculpted where his aim is, SCULPT_DISTANCE ahead, following the mouse's path; each new point is sent to
 * the clients, which grow the ice there (a line, thickening, volume, crystals). A sculpture stands SCULPT_SECONDS, then
 * cracks and breaks apart. While it stands it is real: bodies are pushed out of it and projectiles shatter on it.
 */
final class IcemanBrush {
    private IcemanBrush() {}

    /** Ice sculpted along a path: its points, how thick, when it was let go and when it breaks. */
    static final class Sculpture {
        final int id; final ServerPlayer owner; final List<Vec3> points = new ArrayList<>(); final float radius;
        AABB box; boolean done; int age, life = -1, breaking = -1;
        Sculpture(int id, ServerPlayer owner, float radius) { this.id = id; this.owner = owner; this.radius = radius; }
        void add(Vec3 at) { points.add(at); AABB b = new AABB(at, at).inflate(radius + .2); box = box == null ? b : box.minmax(b); }
        boolean solid() { return breaking < 0 && !points.isEmpty(); }
    }
    private static final List<Sculpture> SCULPTURES = new ArrayList<>();

    static void press(ServerPlayer p, State s) {
        if (!free(s)) return;
        if (s.cooldowns[CD_BRUSH] > 0) return;
        s.brushTarget = -1; s.brushAge = 0; s.sculpture = null;
        set(s, BRUSH);
        sound(p, ModSounds.ICEMAN_FROST.get(), .6f, .8f);
    }
    static void release(ServerPlayer p, State s) {
        if (s.action != BRUSH) return;
        finish(p, s);
        s.brushTarget = -1;
        set(s, IDLE);
    }
    /** The sculpture being made is let go: it stands its time from now. */
    private static void finish(ServerPlayer p, State s) {
        Sculpture sc = s.sculpture;
        s.sculpture = null;
        if (sc == null) return;
        sc.done = true;
        sc.life = (int) Math.round(IcemanConfig.SCULPT_SECONDS.get() * 20);
        if (sc.points.size() < 2) { SCULPTURES.remove(sc); fxAt(p, sc.points.isEmpty() ? p.position() : sc.points.get(0), FX_SCULPT_BREAK, sc.points.isEmpty() ? p.position() : sc.points.get(0), Vec3.ZERO, 0, p.getId(), sc.id); return; }
        fxAt(p, sc.points.get(0), FX_SCULPT_END, sc.points.get(sc.points.size() - 1), Vec3.ZERO, sc.life, p.getId(), sc.id);
    }

    static void tick(ServerPlayer p, State s) {
        if (!s.rmb) { release(p, s); return; }
        s.brushAge++;
        if (s.age < BRUSH_RAISE) return;
        LivingEntity t = aimed(p, IcemanConfig.BRUSH_RANGE.get(), .7);
        if (t != null) {
            // On a body: the stream. Any sculpture in the making is let go.
            if (s.sculpture != null) finish(p, s);
            if (s.brushTarget != t.getId()) at(p, t.position(), ModSounds.ICEMAN_FORM.get(), .6f, 1.4f);
            s.brushTarget = t.getId();
            float frost = f(IcemanConfig.BRUSH_FROST) / 20f;
            boolean froze = IcemanFrost.add(p, t, frost, false);
            if (s.age % 10 == 0 && !IcemanFrost.deep(t)) {
                t.invulnerableTime = 0;
                t.hurt(p.damageSources().playerAttack(p), f(IcemanConfig.BRUSH_DAMAGE) / 2f);
            }
            if (s.age % 6 == 0 || froze) fxAt(p, t.position(), FX_BRUSH_FROST, t.getBoundingBox().getCenter(), Vec3.ZERO, IcemanFrost.get(t), t.getId(), 0);
            return;
        }
        s.brushTarget = -1;
        sculpt(p, s);
    }
    /** Into the air: a point on the sculpture wherever his aim is, each SCULPT_STEP of the mouse's path. */
    private static void sculpt(ServerPlayer p, State s) {
        double dist = IcemanConfig.SCULPT_DISTANCE.get();
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(dist));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 at = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation().subtract(p.getLookAngle().scale(.25));
        if (at.distanceTo(eye) < 1.8) return;
        Sculpture sc = s.sculpture;
        if (sc == null) {
            sc = new Sculpture(nextId++, p, f(IcemanConfig.SCULPT_THICKNESS));
            s.sculpture = sc;
            SCULPTURES.add(sc);
            // Too many standing: the oldest breaks.
            List<Sculpture> mine = SCULPTURES.stream().filter(o -> o.owner == p && o.breaking < 0).toList();
            int max = IcemanConfig.SCULPT_MAX.get();
            for (int i = 0; i < mine.size() - max; i++) crack(mine.get(i));
        }
        if (!sc.points.isEmpty() && sc.points.get(sc.points.size() - 1).distanceTo(at) < SCULPT_STEP) return;
        // A fast flick fills in between, never leaving a gap.
        Vec3 last = sc.points.isEmpty() ? at : sc.points.get(sc.points.size() - 1);
        int steps = Math.max(1, (int) Math.ceil(last.distanceTo(at) / (SCULPT_STEP * 1.6)));
        if (sc.points.isEmpty()) steps = 1;
        for (int i = 1; i <= steps && sc.points.size() < SCULPT_POINTS; i++) {
            Vec3 pt = sc.points.isEmpty() ? at : last.lerp(at, i / (double) steps);
            sc.add(pt);
            fxAt(p, pt, FX_SCULPT_POINT, pt, new Vec3(sc.radius, 0, 0), sc.points.size() - 1, p.getId(), sc.id);
        }
        if (sc.points.size() % 4 == 1) at(p, at, ModSounds.ICEMAN_SCULPT.get(), .55f, .9f + p.getRandom().nextFloat() * .25f);
        // Full: it is let go; holding on starts a new one.
        if (sc.points.size() >= SCULPT_POINTS) finish(p, s);
    }
    private static void crack(Sculpture sc) {
        if (sc.breaking >= 0) return;
        sc.breaking = 0;
        Vec3 mid = sc.points.isEmpty() ? sc.owner.position() : sc.points.get(sc.points.size() / 2);
        fxAt(sc.owner, mid, FX_SCULPT_BREAK, mid, Vec3.ZERO, sc.points.size(), sc.owner.getId(), sc.id);
        at(sc.owner, mid, ModSounds.ICEMAN_CRACK.get(), 1f, .9f);
    }

    /** Every sculpture: its time, its cracking and breaking, its pushing bodies out and stopping projectiles. */
    static void tickSculptures() {
        for (Iterator<Sculpture> it = SCULPTURES.iterator(); it.hasNext(); ) {
            Sculpture sc = it.next();
            if (sc.owner.isRemoved()) { it.remove(); continue; }
            sc.age++;
            if (sc.breaking >= 0) {
                if (++sc.breaking == SCULPT_CRACK) {
                    Vec3 mid = sc.points.get(sc.points.size() / 2);
                    at(sc.owner, mid, ModSounds.ICEMAN_SHATTER.get(), 1.2f, 1f);
                }
                if (sc.breaking > SCULPT_CRACK + 2) it.remove();
                continue;
            }
            if (sc.done && --sc.life <= 0) { crack(sc); continue; }
            if (sc.box == null || sc.age % 2 != 0) continue;
            for (Entity e : sc.owner.level().getEntities(sc.owner, sc.box.inflate(.6), e -> e instanceof LivingEntity || e instanceof Projectile)) {
                Vec3 c = e.getBoundingBox().getCenter();
                double reach = sc.radius + e.getBbWidth() * .5;
                Vec3 near = nearest(sc, c, e.getBbHeight() * .5);
                if (near == null) continue;
                Vec3 off = c.subtract(near);
                double d = off.length();
                if (d > reach) continue;
                if (e instanceof Projectile proj) {
                    // Shattered on the ice.
                    fxAt(sc.owner, near, FX_SHATTER, c, proj.getDeltaMovement().scale(-.15), .35f, -1, 2);
                    at(sc.owner, c, ModSounds.ICEMAN_HIT.get(), .6f, 1.5f);
                    proj.discard();
                    continue;
                }
                // Pushed out sideways (never through it).
                Vec3 out = d < 1e-3 ? new Vec3(1, 0, 0) : new Vec3(off.x, Math.max(0, off.y) * .3, off.z).normalize();
                double push = (reach - d) * .5 + .06;
                Vec3 v = e.getDeltaMovement();
                double into = v.dot(out);
                if (into < 0) v = v.subtract(out.scale(into));
                e.setDeltaMovement(v.add(out.scale(push)));
                e.hurtMarked = true;
            }
        }
    }
    /** The point of the sculpture's path nearest to c (the body's middle may be anywhere within half its height of it). */
    private static Vec3 nearest(Sculpture sc, Vec3 c, double halfHeight) {
        Vec3 best = null;
        double bestD = 1e9;
        List<Vec3> pts = sc.points;
        for (int i = 0; i < pts.size(); i++) {
            Vec3 a = pts.get(i), b = i + 1 < pts.size() ? pts.get(i + 1) : a;
            Vec3 ab = b.subtract(a);
            double len2 = ab.lengthSqr();
            double u = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, c.subtract(a).dot(ab) / len2));
            Vec3 q = a.add(ab.scale(u));
            // The body's own height: compare with its nearest point along its vertical line.
            double dy = c.y - q.y;
            Vec3 cc = new Vec3(c.x, c.y - Math.max(-halfHeight, Math.min(halfHeight, dy)), c.z);
            double d = cc.distanceToSqr(q);
            if (d < bestD) { bestD = d; best = q; }
        }
        return best == null ? null : best.add(0, 0, 0);
    }
    /** Breaks all of one Iceman's sculptures (logging out, no longer Iceman). */
    static void clear(ServerPlayer p, State s) {
        s.sculpture = null;
        for (Sculpture sc : SCULPTURES) if (sc.owner == p) crack(sc);
        SCULPTURES.removeIf(sc -> sc.owner == p);
        IcemanWeapons.clear(p);
    }
}
