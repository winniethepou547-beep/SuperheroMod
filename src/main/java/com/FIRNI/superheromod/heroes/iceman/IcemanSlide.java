package com.FIRNI.superheromod.heroes.iceman;

import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanController.State;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanConfig.f;
import static com.FIRNI.superheromod.heroes.iceman.IcemanController.*;

/**
 * The two slides on the server; his own client steers his body (IcemanClient), the server decides when they start and
 * end and who they touch.
 * SHIFT (held): the ice slide, Days of Future Past surfing: long and controlled, a track of ice growing under him (every
 * client grows it from where they see him). A body the slide or the fresh track touches gets frost, and a player sees
 * the world through an icy lens for a while.
 * CTRL: the sub-zero slide: short, very low and fast along the ground; the first body it reaches takes the hit (frost, a
 * small blow, a short frost over their screen).
 */
final class IcemanSlide {
    private IcemanSlide() {}

    /** Where each sliding Iceman has been lately (his feet, newest last): the fresh track bodies can touch. */
    private static final Map<UUID, ArrayDeque<Vec3>> TRAILS = new HashMap<>();
    /** The whole track he leaves (his feet each tick, with the time), for as long as it stands: anyone on it gets frost. */
    private record Mark(Vec3 at, long time) {}
    /** A track: his feet along it, who it touched lately, and the solid boxes under it (IceSolidEntity, oldest first). */
    private static final class Track {
        final ServerPlayer owner; final ArrayDeque<Mark> marks = new ArrayDeque<>(); final Map<Integer, Long> touched = new HashMap<>();
        final ArrayDeque<IceSolidEntity> solids = new ArrayDeque<>();
        /** Where the last box was laid (null: none yet). */
        Vec3 lastSolid;
        Track(ServerPlayer owner) { this.owner = owner; }
    }
    private static final java.util.List<Track> TRACKS = new java.util.ArrayList<>();
    private static final Map<UUID, Track> RIDING = new HashMap<>();
    /**
     * The track's solid boxes: one each SOLID_STEP blocks of his path, SOLID_WIDTH square, SOLID_HEIGHT thick, the top
     * SOLID_TOP under his feet (where the drawn track's top is); only where the track runs in the air (on the ground it
     * lies flat on the ground and a box would only be a step). At most SOLID_MAX per track (the oldest, soonest to melt,
     * goes first). Each stands as long as its stretch of track is drawn.
     */
    static final double SOLID_STEP = .7, SOLID_WIDTH = 1.0, SOLID_HEIGHT = .35, SOLID_TOP = .03, SOLID_GRACE = .35;
    static final int SOLID_MAX = 160, SOLID_LIFE = SLIDE_TRACK_LIFE + SLIDE_TRACK_MELT / 2;

    static void start(ServerPlayer p, State s) {
        if (s.action == SLIDE) return;
        if (!(free(s) || s.action == BRUSH)) return;
        if (s.action == SLIDE_END) set(s, IDLE);
        if (s.cooldowns[CD_SLIDE] > 0) { tell(p, "Buz Kaydırağı: " + seconds(s.cooldowns[CD_SLIDE])); return; }
        if (s.action == BRUSH) IcemanBrush.release(p, s);
        s.slideAge = 0;
        s.slideTouched.clear();
        set(s, SLIDE);
        TRAILS.put(p.getUUID(), new ArrayDeque<>());
        Track track = new Track(p);
        TRACKS.add(track);
        RIDING.put(p.getUUID(), track);
        s.airFall = false;
        sound(p, ModSounds.ICEMAN_SLIDE_START.get(), 1f, 1f);
        sound(p, ModSounds.ICEMAN_FORM_BIG.get(), .6f, 1.3f);
    }
    /**
     * SHIFT let go (or the time is up): on the ground he brakes into his stance (SLIDE_END: a foot dragged, ice spray,
     * the body turning); in the air he leaves the ice with what momentum he has and falls (no fall damage until he is down).
     */
    static void stop(ServerPlayer p, State s) {
        if (s.action != SLIDE) return;
        boolean ground = p.onGround() || !p.level().noCollision(p, p.getBoundingBox().move(0, -.3, 0));
        set(s, ground ? SLIDE_END : IDLE);
        s.airFall = !ground;
        s.cooldowns[CD_SLIDE] = Math.max(s.cooldowns[CD_SLIDE], IcemanConfig.CD_SLIDE.get());
        s.noFallUntil = p.level().getGameTime() + 40;
        TRAILS.remove(p.getUUID());
        RIDING.remove(p.getUUID());
        sound(p, ModSounds.ICEMAN_CRACK.get(), .5f, 1.3f);
        if (ground) sound(p, ModSounds.ICEMAN_DASH.get(), .5f, 1.4f);
    }
    /** The test autopilot: his own client rides the slide by itself (mode = IcemanAction.AUTO_*). */
    static void autopilot(ServerPlayer p, State s, int mode) {
        s.cooldowns[CD_SLIDE] = 0;
        if (s.action == SLIDE) stop(p, s);
        if (s.action == SLIDE_END) set(s, IDLE);
        fxTo(p, FX_AUTO_SLIDE, p.position(), Vec3.ZERO, 0, p.getId(), mode);
    }
    /** CTRL: the sub-zero slide toward rel (radians from his facing; 0 = straight ahead). */
    static void dash(ServerPlayer p, State s, float rel) {
        if (!(free(s) || s.action == BRUSH || s.action == STRIKE || s.action == SLIDE)) return;
        if (s.cooldowns[CD_DASH] > 0) return;
        if (s.action == BRUSH) IcemanBrush.release(p, s);
        if (s.action == SLIDE) stop(p, s);
        s.cooldowns[CD_DASH] = Math.max(IcemanConfig.CD_DASH.get(), DASH_TICKS + 2);
        s.dashYaw = p.getYRot() * Mth.DEG_TO_RAD + rel;
        s.dashHit = false;
        s.queued = false;
        set(s, DASH);
        fx(p, FX_DASH, p.position(), Vec3.ZERO, s.dashYaw, p.getId(), 0);
        sound(p, ModSounds.ICEMAN_DASH.get(), 1f, 1f);
    }

    static void tick(ServerPlayer p, State s) {
        if (s.action == DASH) { dashTick(p, s); return; }
        if (s.action == SLIDE_END) { if (s.age >= SLIDE_END_TICKS) set(s, IDLE); return; }
        s.slideAge++;
        p.fallDistance = 0;
        s.noFallUntil = p.level().getGameTime() + 30;
        if (s.slideAge >= IcemanConfig.SLIDE_SECONDS.get() * 20 || !p.isAlive()) { stop(p, s); return; }
        ArrayDeque<Vec3> trail = TRAILS.computeIfAbsent(p.getUUID(), id -> new ArrayDeque<>());
        trail.addLast(p.position());
        while (trail.size() > 24) trail.removeFirst();
        Track track = RIDING.get(p.getUUID());
        if (track != null) {
            track.marks.addLast(new Mark(p.position(), p.level().getGameTime()));
            lay(p, track, p.position());
        }
        if (s.slideAge % 2 != 0) return;
        long now = p.level().getGameTime();
        // Whoever the slide (or the fresh ice behind it) touches.
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(3.5), t -> targetable(p, t))) {
            Long last = s.slideTouched.get(t.getId());
            if (last != null && now - last < 30) continue;
            if (!touches(t, trail)) continue;
            s.slideTouched.put(t.getId(), now);
            hurt(p, t, 0, f(IcemanConfig.SLIDE_FROST));
            Vec3 away = flat(t.position().subtract(p.position()));
            t.setDeltaMovement(t.getDeltaMovement().add(away.x * .25, .18, away.z * .25));
            t.hurtMarked = true;
            fxAt(p, t.position(), FX_SLIDE_HIT, t.getBoundingBox().getCenter(), away, 1, t.getId(), p.getId());
            if (t instanceof ServerPlayer victim)
                fxTo(victim, FX_LENS, p.position(), Vec3.ZERO, 1, p.getId(), (int) Math.round(IcemanConfig.SLIDE_LENS_SECONDS.get() * 20));
            at(p, t.position(), ModSounds.ICEMAN_FROST.get(), 1f, 1.1f);
        }
    }
    /** The body is within reach of the track's last stretch (its feet to its middle). */
    private static boolean touches(LivingEntity t, ArrayDeque<Vec3> trail) {
        Vec3 feet = t.position(), mid = t.getBoundingBox().getCenter();
        double reach = .9 + t.getBbWidth() * .5;
        Vec3 prev = null;
        for (Vec3 q : trail) {
            if (prev != null && (segment(feet, prev, q) < reach || segment(mid, prev, q) < reach)) return true;
            prev = q;
        }
        return false;
    }
    private static double segment(Vec3 c, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double u = len2 < 1e-6 ? 0 : Mth.clamp(c.subtract(a).dot(ab) / len2, 0, 1);
        return c.distanceTo(a.add(ab.scale(u)));
    }

    private static void dashTick(ServerPlayer p, State s) {
        p.fallDistance = 0;
        s.noFallUntil = p.level().getGameTime() + 20;
        if (!s.dashHit && s.age >= DASH_HIT_FROM) {
            Vec3 dir = new Vec3(-Mth.sin(s.dashYaw), 0, Mth.cos(s.dashYaw));
            for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(.9, .4, .9).move(dir.scale(.5)), t -> targetable(p, t))) {
                s.dashHit = true;
                hurt(p, t, f(IcemanConfig.DASH_DAMAGE), f(IcemanConfig.DASH_FROST));
                double k = IcemanConfig.DASH_KNOCK.get();
                t.setDeltaMovement(dir.x * k, .35, dir.z * k);
                t.hurtMarked = true;
                Vec3 at = t.getBoundingBox().getCenter().subtract(dir.scale(t.getBbWidth() * .5)).add(0, -.3, 0);
                fxAt(p, at, FX_DASH_HIT, at, dir, 1, t.getId(), p.getId());
                if (t instanceof ServerPlayer victim) fxTo(victim, FX_LENS, p.position(), Vec3.ZERO, .55f, p.getId(), 26);
                at(p, at, ModSounds.ICEMAN_HIT.get(), 1.1f, .95f);
                at(p, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .7f, 1.1f);
                break;
            }
        }
        if (s.age >= DASH_TICKS) set(s, IDLE);
    }

    /**
     * A solid box under the track where his feet are now, if the last one is SOLID_STEP behind and the track runs in the
     * air here. Laid soft: it hardens once he (and anyone else) is clear of it, so he never snags on the ice laid under
     * him; afterwards it is solid for everyone, him included (he can land on his old track and run along it).
     */
    private static void lay(ServerPlayer p, Track t, Vec3 feet) {
        if (t.lastSolid != null && t.lastSolid.distanceToSqr(feet) < SOLID_STEP * SOLID_STEP) return;
        double top = feet.y - SOLID_TOP, ground = ground(p.level(), feet);
        if (feet.y - ground < .3) return;
        double bottom = Math.max(top - SOLID_HEIGHT, ground);
        if (top - bottom < .1) return;
        IceSolidEntity box = solidBox(p, new Vec3(feet.x, bottom, feet.z), (float) (top - bottom), SOLID_LIFE);
        t.lastSolid = feet;
        if (box == null) return;
        t.solids.addLast(box);
        while (t.solids.size() > SOLID_MAX) t.solids.pollFirst().gone();
    }
    /** One of the track's boxes, its bottom middle at `bottom`; also used by the test command. */
    static IceSolidEntity solidBox(ServerPlayer p, Vec3 bottom, float height, int life) {
        return IceSolidEntity.spawn(p, bottom, (float) SOLID_WIDTH, height, life, SOLID_GRACE);
    }
    /** The top of the ground under a point (within 8 blocks), or far below when there is none (as IcemanTrack.ground). */
    private static double ground(net.minecraft.world.level.Level level, Vec3 at) {
        net.minecraft.core.BlockPos base = net.minecraft.core.BlockPos.containing(at.x, at.y + .2, at.z);
        for (int dy = 0; dy <= 8; dy++) {
            net.minecraft.core.BlockPos pos = base.below(dy);
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            if (top <= at.y + .25) return top;
        }
        return at.y - 99;
    }
    /** All of one Iceman's tracks gone at once, their solid ice with them (logging out, no longer Iceman). */
    static void clear(ServerPlayer p) {
        TRAILS.remove(p.getUUID());
        RIDING.remove(p.getUUID());
        for (java.util.Iterator<Track> it = TRACKS.iterator(); it.hasNext(); ) {
            Track t = it.next();
            if (t.owner != p && !t.owner.getUUID().equals(p.getUUID())) continue;
            for (IceSolidEntity e : t.solids) e.gone();
            t.solids.clear();
            it.remove();
        }
    }

    /** Everyone standing on (or in) one of the tracks still there gets a little frost now and then, and the icy lens. */
    static void tickTracks() {
        if (TRACKS.isEmpty()) return;
        for (java.util.Iterator<Track> it = TRACKS.iterator(); it.hasNext(); ) {
            Track t = it.next();
            ServerPlayer p = t.owner;
            // (The boxes need nothing here: each goes away by itself when its stretch of track has melted.)
            while (!t.solids.isEmpty() && t.solids.peekFirst().isRemoved()) t.solids.pollFirst();
            if (p.isRemoved() || t.marks.isEmpty() && !RIDING.containsValue(t)) { it.remove(); continue; }
            long now = p.level().getGameTime();
            while (!t.marks.isEmpty() && now - t.marks.peekFirst().time() > SLIDE_TRACK_LIFE + SLIDE_TRACK_MELT / 2) t.marks.removeFirst();
            if (t.marks.isEmpty() || now % 5 != 0) continue;
            double x0 = 1e9, y0 = 1e9, z0 = 1e9, x1 = -1e9, y1 = -1e9, z1 = -1e9;
            for (Mark m : t.marks) { x0 = Math.min(x0, m.at().x); y0 = Math.min(y0, m.at().y); z0 = Math.min(z0, m.at().z); x1 = Math.max(x1, m.at().x); y1 = Math.max(y1, m.at().y); z1 = Math.max(z1, m.at().z); }
            var box = new net.minecraft.world.phys.AABB(x0, y0, z0, x1, y1, z1).inflate(1.2, 1.5, 1.2);
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> targetable(p, e))) {
                Long last = t.touched.get(e.getId());
                if (last != null && now - last < 25) continue;
                Vec3 feet = e.position();
                boolean on = false;
                for (Mark m : t.marks) {
                    double dx = m.at().x - feet.x, dz = m.at().z - feet.z, dy = feet.y - m.at().y;
                    if (dx * dx + dz * dz < 1.1 && dy > -1.6 && dy < 1.0) { on = true; break; }
                }
                if (!on) continue;
                t.touched.put(e.getId(), now);
                hurt(p, e, 0, f(IcemanConfig.SLIDE_FROST) * .35f);
                if (e instanceof ServerPlayer victim) fxTo(victim, FX_LENS, p.position(), Vec3.ZERO, .6f, p.getId(), 40);
            }
        }
    }
}
