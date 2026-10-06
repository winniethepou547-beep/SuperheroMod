package com.FIRNI.superheromod.heroes.iceman;

import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanController.State;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanConfig.f;
import static com.FIRNI.superheromod.heroes.iceman.IcemanController.*;

/**
 * R: shattered ground, on the server. Both hands down to the ground (GROUND_DOWN); frost spreads from them and the cracks
 * run out under the ground toward the target (the one in his aim, followed as they move; or the point he aims at), as if
 * something were growing beneath it; where they arrive the ice spikes burst up and throw them into the air. Stronger the
 * higher their frost: on someone deep frozen it is the finishing blow (the encasing shatters with it).
 * The crack's front is sent every tick (FX_GROUND) so every client draws the same path.
 */
final class IcemanGround {
    private IcemanGround() {}

    static final class Crack {
        final int id; final ServerPlayer owner; final LivingEntity target; Vec3 front, point; double travelled; int age; final double range;
        Crack(int id, ServerPlayer owner, LivingEntity target, Vec3 front, Vec3 point, double range) {
            this.id = id; this.owner = owner; this.target = target; this.front = front; this.point = point; this.range = range;
        }
    }
    private static final List<Crack> CRACKS = new ArrayList<>();

    static void press(ServerPlayer p, State s) {
        if (!(free(s) || s.action == BRUSH)) return;
        if (s.cooldowns[CD_GROUND] > 0) { tell(p, "Parçalanmış Zemin: " + seconds(s.cooldowns[CD_GROUND])); return; }
        if (s.action == BRUSH) IcemanBrush.release(p, s);
        s.cooldowns[CD_GROUND] = IcemanConfig.CD_GROUND.get();
        set(s, GROUND);
        sound(p, ModSounds.ICEMAN_FROST.get(), .9f, .7f);
    }
    static void tick(ServerPlayer p, State s) {
        if (s.age == GROUND_DOWN) launch(p, null);
        if (s.age >= GROUND_TICKS) set(s, IDLE);
    }
    /** The cracks set off from his feet toward the target (or forced onto one, for the test). */
    static void launch(ServerPlayer p, LivingEntity forced) {
        double range = IcemanConfig.GROUND_RANGE.get();
        LivingEntity t = forced != null ? forced : aimed(p, range, 1.4);
        if (t == null) {
            // A little more forgiving: the one nearest his aim within a narrow cone.
            for (LivingEntity c : inFront(p, range, .93)) { t = c; break; }
        }
        Vec3 point = t != null ? t.position() : aimPoint(p, range);
        Vec3 g = ground(p.serverLevel(), point.add(0, .5, 0), 8);
        if (g != null) point = g;
        Vec3 from = p.position().add(facing(p).scale(.8));
        Crack c = new Crack(nextId++, p, t, from, point, range * 1.35);
        CRACKS.add(c);
        float bonus = t == null ? 0 : IcemanFrost.deep(t) ? 2 : IcemanFrost.get(t) / FROST_MAX;
        fxAt(p, from, FX_GROUND, from, point, bonus, t == null ? -1 : t.getId(), c.id);
        sound(p, ModSounds.ICEMAN_GROUND_CRACK.get(), 1.3f, 1f);
        sound(p, SoundEvents.GLASS_BREAK, .5f, .5f);
    }
    static void tickCracks() {
        for (Iterator<Crack> it = CRACKS.iterator(); it.hasNext(); ) {
            Crack c = it.next();
            ServerPlayer p = c.owner;
            if (p.isRemoved()) { it.remove(); continue; }
            c.age++;
            if (c.target != null && c.target.isAlive() && !c.target.isRemoved()) c.point = c.target.position();
            Vec3 to = c.point.subtract(c.front);
            Vec3 flatTo = new Vec3(to.x, 0, to.z);
            double d = flatTo.length();
            if (d <= GROUND_SPEED + .3 || c.travelled > c.range) {
                erupt(c);
                it.remove();
                continue;
            }
            // Along the ground toward them, hugging its surface.
            Vec3 step = flatTo.scale(GROUND_SPEED / d);
            Vec3 next = c.front.add(step);
            Vec3 g = ground(p.serverLevel(), next.add(0, 1.2, 0), 4);
            c.front = g != null ? g : new Vec3(next.x, Mth.lerp(.3, next.y, c.point.y), next.z);
            c.travelled += GROUND_SPEED;
            fxAt(p, c.front, FX_GROUND, c.front, c.point, -1, c.target == null ? -1 : c.target.getId(), c.id);
            if (c.age % 6 == 0) at(p, c.front, ModSounds.ICEMAN_CRACK.get(), .6f, .8f + p.getRandom().nextFloat() * .3f);
        }
    }
    /** The spikes burst up where the cracks arrived: thrown up, hit, frosted; deep frozen, the finishing blow. */
    private static void erupt(Crack c) {
        ServerPlayer p = c.owner;
        Vec3 at = c.point;
        Vec3 g = ground(p.serverLevel(), at.add(0, .8, 0), 6);
        if (g != null) at = g;
        double radius = IcemanConfig.GROUND_RADIUS.get();
        float biggest = 1;
        for (LivingEntity t : around(p, at.add(0, .5, 0), radius)) {
            boolean deep = IcemanFrost.deep(t);
            float frost = IcemanFrost.get(t) / FROST_MAX;
            float k = deep ? 1.6f : 1 + .6f * frost;
            biggest = Math.max(biggest, k);
            hurt(p, t, f(IcemanConfig.GROUND_DAMAGE) * k, f(IcemanConfig.GROUND_FROST));
            Vec3 away = flat(t.position().subtract(at));
            double launch = IcemanConfig.GROUND_LAUNCH.get() * (.85 + .35 * (k - 1) / .6);
            t.setDeltaMovement(away.x * .2, launch, away.z * .2);
            t.hurtMarked = true;
        }
        fxAt(p, at, FX_GROUND_ERUPT, at, Vec3.ZERO, (float) radius * biggest, c.target == null ? -1 : c.target.getId(), c.id);
        at(p, at, ModSounds.ICEMAN_SPIKES.get(), 1.6f, .95f);
        at(p, at, ModSounds.ICEMAN_SHATTER.get(), 1.1f, .8f);
        at(p, at, SoundEvents.GENERIC_EXPLODE, .35f * biggest, 1.5f);
    }
    /** The test: a target at once (the cracks run to it as if R had been pressed at it). */
    static void test(ServerPlayer p, LivingEntity target) { launch(p, target); }
}
