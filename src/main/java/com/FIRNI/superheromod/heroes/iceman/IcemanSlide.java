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

    static void start(ServerPlayer p, State s) {
        if (s.action == SLIDE) return;
        if (!(free(s) || s.action == BRUSH)) return;
        if (s.cooldowns[CD_SLIDE] > 0) { tell(p, "Buz Kaydırağı: " + seconds(s.cooldowns[CD_SLIDE])); return; }
        if (s.action == BRUSH) IcemanBrush.release(p, s);
        s.slideAge = 0;
        s.slideTouched.clear();
        set(s, SLIDE);
        TRAILS.put(p.getUUID(), new ArrayDeque<>());
        sound(p, ModSounds.ICEMAN_SLIDE_START.get(), 1f, 1f);
        sound(p, ModSounds.ICEMAN_FORM_BIG.get(), .6f, 1.3f);
    }
    static void stop(ServerPlayer p, State s) {
        if (s.action != SLIDE) return;
        set(s, IDLE);
        s.cooldowns[CD_SLIDE] = Math.max(s.cooldowns[CD_SLIDE], IcemanConfig.CD_SLIDE.get());
        s.noFallUntil = p.level().getGameTime() + 40;
        TRAILS.remove(p.getUUID());
        sound(p, ModSounds.ICEMAN_CRACK.get(), .5f, 1.3f);
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
        s.slideAge++;
        p.fallDistance = 0;
        s.noFallUntil = p.level().getGameTime() + 30;
        if (s.slideAge >= IcemanConfig.SLIDE_SECONDS.get() * 20 || !p.isAlive()) { stop(p, s); return; }
        ArrayDeque<Vec3> trail = TRAILS.computeIfAbsent(p.getUUID(), id -> new ArrayDeque<>());
        trail.addLast(p.position());
        while (trail.size() > 24) trail.removeFirst();
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
}
