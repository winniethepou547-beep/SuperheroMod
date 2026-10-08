package com.FIRNI.superheromod.heroes.iceman;

import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanController.State;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanConfig.f;
import static com.FIRNI.superheromod.heroes.iceman.IcemanController.*;

/**
 * The Ice Armory on the server: the weapon picked on the wheel forms in his hand; left click swings it (two swings, right
 * to left then left to right, then its finisher: the mace's two-handed overhead slam, the spear's flurry of thrusts, the
 * sword's spin), held it charges (the mace grows up to MACE_MAX times its size and is slammed down on release; the spear
 * is drawn back and thrown; the sword is driven into the ground before him while he kneels over it, and pulls everyone
 * near toward it until he lets go, when it cracks and shatters). A pressed button goes straight into a short wind-up
 * (CHARGE, under HOLD_TICKS): let go within it and it is a swing, held past it and it is the hold, so a click never waits.
 * <p>
 * A weapon never just vanishes: every break is sent (FX_WEAPON_BREAK, FX_SWORD_BREAK) so the clients crack it and let it
 * fall apart. Using a power that needs his hands (the slides, the shell, shattered ground, the brush) breaks the weapon
 * in hand ({@link #abandon}); the wheel alone never does.
 */
final class IcemanWeapons {
    private IcemanWeapons() {}

    /** A thrown spear: flying, then stuck (in a body or a block) until it cracks apart. */
    static final class Spear {
        final int id; final ServerPlayer owner; Vec3 pos, vel; final float charge, gravity; int age, stuckAge = -1;
        LivingEntity stuckIn; Vec3 offset;
        Spear(int id, ServerPlayer owner, Vec3 pos, Vec3 vel, float charge) {
            this.id = id; this.owner = owner; this.pos = pos; this.vel = vel; this.charge = charge;
            gravity = .055f * (1 - .85f * charge);
        }
    }
    private static final List<Spear> SPEARS = new ArrayList<>();

    // ------------------------------------------------------------------ the wheel
    /**
     * Another weapon picked: the one in hand (or still forming) cracks and falls apart first (a swing, a hold or a
     * release in progress ends there), then the new one forms.
     */
    static void select(ServerPlayer p, State s, int weapon) {
        if (s.weapon == weapon && (s.weaponOut || s.action == FORM)) return;
        boolean had = s.weaponOut || s.action == FORM;
        float size = s.action == CHARGE && s.weapon == W_MACE && s.age >= HOLD_TICKS ? 1 + (MACE_MAX - 1) * s.charge : 1;
        if (s.plant != null) { unplant(p, s); had = false; }
        if (s.action == STRIKE || s.action == CHARGE || s.action == RELEASE || s.action == FORM) { s.charge = 0; s.queued = false; set(s, IDLE); }
        if (had) fx(p, FX_WEAPON_BREAK, hand(p, 0), facing(p).scale(.06), size, p.getId(), s.weapon);
        s.weapon = weapon;
        s.weaponOut = false;
        s.combo = -1;
        if (had) {
            // The old one falls apart in his hand before the new one starts to form.
            s.cooldowns[CD_WEAPON] = Math.max(s.cooldowns[CD_WEAPON], WEAPON_CRACK_TICKS + 2);
            s.formPending = true;
        } else if (free(s) && s.cooldowns[CD_WEAPON] <= 0) form(p, s);
        else s.formPending = true;
        sound(p, SoundEvents.UI_BUTTON_CLICK.get(), .3f, 1.7f);
    }
    static void form(ServerPlayer p, State s) {
        s.formPending = false;
        set(s, FORM);
        fx(p, FX_FORM, hand(p, 0), Vec3.ZERO, s.weapon, p.getId(), 0);
        // The air freezing in his palm: crystal ticks now, the weapon's own sound when it closes (tick, FORM end).
        sound(p, ModSounds.ICEMAN_CRYSTAL_TICKS.get(), .8f, s.weapon == W_MACE ? .8f : s.weapon == W_SPEAR ? 1.2f : 1f);
    }
    /**
     * A power that needs his hands was used (the caller checked it really started; was = the action before it): the
     * weapon in his hand (or forming in it) breaks there. It does not form again by itself: the next left click forms it.
     */
    static void abandon(ServerPlayer p, State s, int was, float wasCharge) {
        if (s.plant != null) { unplant(p, s); s.formPending = false; return; }
        if (!s.weaponOut && was != FORM) return;
        float size = was == CHARGE && s.weapon == W_MACE ? 1 + (MACE_MAX - 1) * Mth.clamp(wasCharge, 0, 1) : 1;
        s.weaponOut = false; s.formPending = false; s.combo = -1; s.queued = false;
        s.cooldowns[CD_WEAPON] = Math.max(s.cooldowns[CD_WEAPON], WEAPON_CRACK_TICKS + 2);
        // (The clients crack it and let it fall apart, with the sounds of each stage.)
        fx(p, FX_WEAPON_BREAK, hand(p, 0), facing(p).scale(.06), size, p.getId(), s.weapon);
    }

    // ------------------------------------------------------------------ left click
    static void press(ServerPlayer p, State s) {
        if (s.action == STRIKE) { s.queued = true; return; }
        if (s.action == FORM) { s.queued = true; return; }
        if (!free(s)) return;
        if (!s.weaponOut) {
            if (s.cooldowns[CD_WEAPON] > 0) { tell(p, WEAPON_NAMES[s.weapon] + " oluşuyor: " + seconds(s.cooldowns[CD_WEAPON])); return; }
            form(p, s);
            return;
        }
        s.charge = 0;
        set(s, CHARGE);
    }
    static void release(ServerPlayer p, State s) {
        if (s.action != CHARGE) return;
        if (s.age < HOLD_TICKS) startSwing(p, s);
        // The sword on its way into the ground goes in first (the hold lets go as soon as it stands: chargeTick).
        else if (s.weapon == W_SWORD && s.plant == null) return;
        else releaseHold(p, s);
    }
    private static void startSwing(ServerPlayer p, State s) {
        long now = p.level().getGameTime();
        s.combo = s.combo >= 0 && s.combo < 2 && now - s.lastSwingEnd <= COMBO_CHAIN ? s.combo + 1 : 0;
        s.queued = false;
        s.charge = 0;
        set(s, STRIKE);
        // A step into the swing (the heavy mace less, the spear more).
        Vec3 f = facing(p);
        double step = s.weapon == W_MACE ? .12 : s.weapon == W_SPEAR ? .3 : .2;
        if (s.combo == 2 && s.weapon == W_SPEAR) step = .35;
        p.setDeltaMovement(p.getDeltaMovement().add(f.x * step, 0, f.z * step));
        p.hurtMarked = true;
        float pitch = s.weapon == W_MACE ? .75f : s.weapon == W_SPEAR ? 1.25f : 1f;
        sound(p, s.weapon == W_MACE ? ModSounds.ICEMAN_MACE_SWING.get() : s.weapon == W_SPEAR ? ModSounds.ICEMAN_SPEAR_THRUST.get() : ModSounds.ICEMAN_SWORD_SWING.get(),
                .8f, pitch + s.combo * .05f);
        // The cold air it cuts through, quietly under it (deeper for the heavy mace).
        sound(p, ModSounds.ICEMAN_COLD_WHOOSH.get(), s.weapon == W_MACE ? .42f : .3f, (s.weapon == W_MACE ? .7f : s.weapon == W_SPEAR ? 1.3f : 1f) + s.combo * .04f);
    }
    private static void releaseHold(ServerPlayer p, State s) {
        set(s, RELEASE);
        if (s.weapon == W_MACE) sound(p, ModSounds.ICEMAN_MACE_SWING.get(), 1.2f, .55f);
        if (s.weapon == W_SPEAR) sound(p, ModSounds.ICEMAN_SPEAR_THROW.get(), 1f, .9f + .3f * s.charge);
        if (s.weapon == W_SWORD) {
            // The planted sword cracks where it stands (the clients shatter it after SWORD_CRACK_TICKS); the pull ends.
            boolean planted = s.plant != null;
            Vec3 at = planted ? s.plant : hand(p, 0);
            fxAt(p, at, FX_SWORD_BREAK, at, facing(p), s.charge, p.getId(), planted ? 1 : 0);
            at(p, at, ModSounds.ICEMAN_CRACK.get(), .8f, 1.25f);
            s.plant = null;
        }
    }
    /** The planted sword broken where it stands by anything but letting go (another power, death, another weapon picked). */
    static void unplant(ServerPlayer p, State s) {
        Vec3 at = s.plant;
        s.plant = s.kneel = null;
        s.weaponOut = false;
        s.cooldowns[CD_WEAPON] = Math.max(s.cooldowns[CD_WEAPON], IcemanConfig.CD_WEAPON.get());
        if (at == null) return;
        fxAt(p, at, FX_SWORD_BREAK, at, facing(p), 1, p.getId(), 1);
        at(p, at, ModSounds.ICEMAN_CRACK.get(), .8f, 1.25f);
    }

    // ------------------------------------------------------------------ every tick (FORM, STRIKE, CHARGE, RELEASE)
    static void tick(ServerPlayer p, State s) {
        switch (s.action) {
            case FORM -> {
                if (s.age < FORM_TICKS) return;
                s.weaponOut = true;
                // The last crystals close over it.
                if (s.weapon == W_MACE) sound(p, ModSounds.ICEMAN_FORM_BIG.get(), .75f, .95f);
                else sound(p, ModSounds.ICEMAN_FORM.get(), .8f, s.weapon == W_SPEAR ? 1.2f : 1.05f);
                set(s, IDLE);
                if (s.lmb) { s.charge = 0; set(s, CHARGE); }
                else if (s.queued) { s.queued = false; s.charge = 0; set(s, CHARGE); release(p, s); }
            }
            case STRIKE -> strikeTick(p, s);
            case CHARGE -> chargeTick(p, s);
            case RELEASE -> releaseTick(p, s);
            default -> {}
        }
    }
    private static void strikeTick(ServerPlayer p, State s) {
        int w = s.weapon, c = Mth.clamp(s.combo, 0, 2);
        int len = SWING_TICKS[w][c], hit = SWING_HIT[w][c];
        if (c < 2 && s.age == hit) swingHit(p, s, c);
        if (c == 2) {
            if (w == W_MACE && s.age == hit) overheadSlam(p, s);
            if (w == W_SPEAR) for (int i = 0; i < SPEAR_FLURRY.length; i++) if (s.age == SPEAR_FLURRY[i]) flurryThrust(p, s, i);
            if (w == W_SWORD && s.age == hit) finisherSpin(p, s);
        }
        if (s.age >= len) {
            s.lastSwingEnd = p.level().getGameTime();
            // The mace's head burst on the ground; the handle left in his fist falls apart now (it forms again by itself).
            if (c == 2 && w == W_MACE) breakWeapon(p, s, 1, 1);
            if (s.queued && s.weaponOut) { s.queued = false; s.charge = 0; set(s, CHARGE); release(p, s); }
            else { s.queued = false; set(s, IDLE); }
        }
    }
    /** The weapon used up in his hand (how: 0 whole, 1 only the mace's handle is left): it cracks and falls apart there, and forms again. */
    private static void breakWeapon(ServerPlayer p, State s, float size, int how) {
        s.weaponOut = false;
        s.cooldowns[CD_WEAPON] = Math.max(s.cooldowns[CD_WEAPON], IcemanConfig.CD_WEAPON.get());
        s.formPending = true;
        fx(p, FX_WEAPON_BREAK, hand(p, 0), facing(p).scale(.1), size, p.getId(), s.weapon | how << 4);
    }
    private static float damage(int w, int c) {
        return switch (w) {
            case W_MACE -> f(c < 2 ? IcemanConfig.MACE_DAMAGE : IcemanConfig.MACE_FINISH_DAMAGE);
            case W_SPEAR -> f(c < 2 ? IcemanConfig.SPEAR_DAMAGE : IcemanConfig.SPEAR_FLURRY_DAMAGE);
            default -> f(c < 2 ? IcemanConfig.SWORD_DAMAGE : IcemanConfig.SWORD_FINISH_DAMAGE);
        };
    }
    static float frost(int w) {
        return f(w == W_MACE ? IcemanConfig.MACE_FROST : w == W_SPEAR ? IcemanConfig.SPEAR_FROST : IcemanConfig.SWORD_FROST);
    }
    /** One of the two swings: the mace and the sword sweep an arc (up to three bodies), the spear stabs one ahead. */
    private static void swingHit(ServerPlayer p, State s, int c) {
        int w = s.weapon;
        double reach = IcemanConfig.WEAPON_REACH.get() + (w == W_SPEAR ? 1.2 : w == W_MACE ? .2 : 0);
        List<LivingEntity> hits = inFront(p, reach, w == W_SPEAR ? .8 : .25);
        int max = w == W_SPEAR ? 1 : 3;
        Vec3 f = facing(p), left = new Vec3(f.z, 0, -f.x).scale(-1);
        // Right to left (c 0) pushes them to his left, left to right (c 1) to his right.
        Vec3 side = c == 0 ? left : left.scale(-1);
        boolean any = false;
        for (int i = 0; i < Math.min(max, hits.size()); i++) {
            LivingEntity t = hits.get(i);
            if (!hurt(p, t, damage(w, c), frost(w))) continue;
            any = true;
            double knock = w == W_MACE ? .75 : w == W_SPEAR ? .35 : .45;
            Vec3 push = w == W_SPEAR ? f.scale(knock) : f.scale(knock * .55).add(side.scale(knock * .6));
            t.setDeltaMovement(t.getDeltaMovement().scale(.3).add(push.x, w == W_MACE ? .22 : .1, push.z));
            t.hurtMarked = true;
            Vec3 at = t.getBoundingBox().getCenter().subtract(f.scale(t.getBbWidth() * .5)).add(0, .2, 0);
            fxAt(p, at, FX_HIT, at, push, w, t.getId(), c);
            // Layered: the deep ice impact, the crack of the ice on them, the blow itself.
            at(p, at, ModSounds.ICEMAN_HIT.get(), w == W_MACE ? 1.1f : .8f, (w == W_MACE ? .8f : w == W_SPEAR ? 1.25f : 1f) + p.getRandom().nextFloat() * .1f);
            at(p, at, ModSounds.ICEMAN_CRACK.get(), w == W_MACE ? .45f : .3f, (w == W_MACE ? 1.05f : 1.45f) + p.getRandom().nextFloat() * .1f);
            at(p, at, w == W_MACE ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP, .5f, 1.2f);
        }
        if (!any) at(p, hand(p, 0), ModSounds.ICEMAN_FROST.get(), .25f, 1.6f);
    }
    /** The mace's third swing: both hands, overhead, down into the ground ahead of him; the mace bursts on impact. */
    private static void overheadSlam(ServerPlayer p, State s) {
        Vec3 f = facing(p);
        Vec3 at = p.position().add(f.scale(2.3));
        Vec3 g = ground(p.serverLevel(), at.add(0, .6, 0), 4);
        if (g != null) at = g;
        for (LivingEntity t : around(p, at, 2.7)) {
            if (!hurt(p, t, f(IcemanConfig.MACE_FINISH_DAMAGE), frost(W_MACE) * 1.4f)) continue;
            Vec3 away = flat(t.position().subtract(at));
            t.setDeltaMovement(away.x * .55, .55, away.z * .55);
            t.hurtMarked = true;
        }
        fxAt(p, at, FX_SLAM, at, f, 1, p.getId(), 0);
        // Heavy: the slam, the ground cracking under it, the head bursting.
        at(p, at, ModSounds.ICEMAN_MACE_SLAM.get(), 1.4f, .95f);
        at(p, at, ModSounds.ICEMAN_GROUND_CRACK.get(), .9f, .75f);
        at(p, at, ModSounds.ICEMAN_SHATTER.get(), .9f, .8f);
    }
    /** One thrust of the spear's flurry: whoever is straight ahead, a step at a time. */
    private static void flurryThrust(ServerPlayer p, State s, int i) {
        List<LivingEntity> hits = inFront(p, IcemanConfig.WEAPON_REACH.get() + 1.5, .7);
        Vec3 f = facing(p);
        if (!hits.isEmpty()) {
            LivingEntity t = hits.get(0);
            if (hurt(p, t, f(IcemanConfig.SPEAR_FLURRY_DAMAGE), frost(W_SPEAR) * .6f)) {
                double k = i == SPEAR_FLURRY.length - 1 ? .9 : .12;
                t.setDeltaMovement(t.getDeltaMovement().scale(.2).add(f.x * k, i == SPEAR_FLURRY.length - 1 ? .3 : .04, f.z * k));
                t.hurtMarked = true;
                Vec3 at = t.getBoundingBox().getCenter().subtract(f.scale(t.getBbWidth() * .5)).add(0, (i % 3 - 1) * .25, 0);
                fxAt(p, at, FX_HIT, at, f, W_SPEAR, t.getId(), 2 + i);
                at(p, at, ModSounds.ICEMAN_HIT.get(), .7f, 1.3f + .05f * i);
            }
        }
        sound(p, ModSounds.ICEMAN_SPEAR_THRUST.get(), .6f, 1.3f + i * .06f);
        if (i % 2 == 0) sound(p, ModSounds.ICEMAN_COLD_WHOOSH.get(), .22f, 1.4f + i * .05f);
    }
    /** The sword's third swing: a fast full turn, everyone round him. */
    private static void finisherSpin(ServerPlayer p, State s) {
        Vec3 c = p.position().add(0, 1, 0);
        for (LivingEntity t : around(p, c, IcemanConfig.WEAPON_REACH.get() + .5)) {
            if (!hurt(p, t, f(IcemanConfig.SWORD_FINISH_DAMAGE), frost(W_SWORD) * 1.3f)) continue;
            Vec3 away = flat(t.position().subtract(p.position()));
            t.setDeltaMovement(away.x * .7, .3, away.z * .7);
            t.hurtMarked = true;
            fxAt(p, t.getBoundingBox().getCenter(), FX_HIT, t.getBoundingBox().getCenter(), away, W_SWORD, t.getId(), 2);
        }
        fx(p, FX_SPIN, p.position(), facing(p), 0, p.getId(), 0);
        sound(p, ModSounds.ICEMAN_SWORD_SPIN.get(), 1f, 1.1f);
    }

    // ------------------------------------------------------------------ holds
    private static void chargeTick(ServerPlayer p, State s) {
        // The sword on its way down goes into the ground before the hold can let go.
        boolean planting = s.weapon == W_SWORD && s.age >= HOLD_TICKS && s.plant == null;
        if (!s.lmb && !planting) { release(p, s); return; }
        if (s.age < HOLD_TICKS) return;
        int held = s.age - HOLD_TICKS;
        switch (s.weapon) {
            case W_MACE -> {
                float was = s.charge;
                s.charge = Math.min(1, held / (float) MACE_GROW);
                // A new layer of crystals grows over the head every so often: a deep rumble and the ice ticking.
                if ((int) (was * 6) != (int) (s.charge * 6) || held == 0) {
                    sound(p, ModSounds.ICEMAN_GROW_RUMBLE.get(), .45f + .6f * s.charge, 1.1f - .45f * s.charge);
                    sound(p, ModSounds.ICEMAN_CRYSTAL_TICKS.get(), .5f, 1.1f - .3f * s.charge);
                }
                if (held == MACE_GROW) sound(p, ModSounds.ICEMAN_CRACK.get(), .8f, .6f);
            }
            case W_SPEAR -> {
                float was = s.charge;
                s.charge = Math.min(1, held / (float) SPEAR_DRAW);
                // The point growing new crystals as it is drawn back.
                if (held % 8 == 0 && s.charge < 1) sound(p, ModSounds.ICEMAN_CRYSTAL_TICKS.get(), .35f, 1.3f + .3f * s.charge);
                if (was < 1 && s.charge >= 1) sound(p, ModSounds.ICEMAN_CRACK.get(), .5f, 1.6f);
            }
            default -> plantTick(p, s, held);
        }
    }
    /**
     * The sword held: turned point down and driven into the ground ahead of him at PLANT_AT (he kneels over it); from
     * then it is the centre: everyone near is pulled toward it along the ground (harder the closer they come), the cold
     * builds on them, and whoever is dragged up against it takes a blow every PLANT_HIT ticks. It stands until he lets go
     * (or SPIN_MAX); pushed off it, he lets go.
     */
    private static void plantTick(ServerPlayer p, State s, int held) {
        s.charge = Math.min(1, held / (float) SPIN_MAX);
        if (held == PLANT_AT) plant(p, s);
        if (s.plant == null) return;
        if (s.kneel != null && p.position().distanceToSqr(s.kneel) > 2.25) { releaseHold(p, s); return; }
        Vec3 c = s.plant;
        double radius = IcemanConfig.SWORD_PULL_RADIUS.get();
        float frost = frost(W_SWORD);
        for (LivingEntity t : around(p, c.add(0, .6, 0), radius)) {
            Vec3 to = new Vec3(c.x - t.getX(), 0, c.z - t.getZ());
            double d = to.length();
            double close = 1 - Mth.clamp(d / radius, 0, 1);
            IcemanFrost.add(p, t, frost * .04f * (float) (.3 + .7 * close), false);
            if (d > .9 && !IcemanFrost.deep(t)) {
                // Drawn in along the ground, harder the nearer they are (they can still fight it far out).
                double k = .03 + .07 * close;
                Vec3 pull = to.scale(k / d);
                t.setDeltaMovement(t.getDeltaMovement().multiply(.6, 1, .6).add(pull.x, 0, pull.z));
                t.hurtMarked = true;
                // A training dummy has no AI and so never moves by itself: moved straight along (the test can show the pull).
                if (t instanceof Mob m && m.isNoAi() && t.getTags().contains("iceman_test")) t.move(MoverType.SELF, pull.scale(2.5));
            }
            if (held % PLANT_HIT == 0 && d <= PLANT_HURT + t.getBbWidth() * .5 && hurt(p, t, f(IcemanConfig.SWORD_SPIN_DAMAGE), frost * .3f))
                fxAt(p, t.getBoundingBox().getCenter(), FX_HIT, t.getBoundingBox().getCenter(), new Vec3(t.getX() - c.x, 0, t.getZ() - c.z), W_SWORD, t.getId(), 9);
        }
        if (held >= SPIN_MAX) releaseHold(p, s);
    }
    private static final UUID PLANT_PIN = UUID.fromString("5d0c1f4e-6a51-4c2b-9a8e-1c3e2b7d9f10");
    /** Kneeling over the planted sword he cannot walk off (his speed taken while it stands; given back after). */
    static void pin(ServerPlayer p, boolean on) {
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.getModifier(PLANT_PIN) != null;
        if (on && !has) speed.addTransientModifier(new AttributeModifier(PLANT_PIN, "Iceman sword planted", -1, AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!on && has) speed.removeModifier(PLANT_PIN);
    }
    /** The sword goes into the ground PLANT_REACH ahead of his feet (on the ground there): the point every client draws it at. */
    private static void plant(ServerPlayer p, State s) {
        Vec3 f = facing(p);
        Vec3 at = p.position().add(f.scale(PLANT_REACH));
        Vec3 g = ground(p.serverLevel(), at.add(0, .6, 0), 3);
        s.plant = g != null ? g : new Vec3(at.x, p.getY(), at.z);
        s.kneel = p.position();
        s.plantId = nextId++;
        fxAt(p, s.plant, FX_SWORD_PLANT, s.plant, f, 0, p.getId(), s.plantId);
        at(p, s.plant, ModSounds.ICEMAN_GROUND_CRACK.get(), 1.1f, .85f);
        at(p, s.plant, ModSounds.ICEMAN_HIT.get(), .9f, .7f);
        at(p, s.plant, SoundEvents.TRIDENT_HIT_GROUND, .7f, .6f);
        at(p, s.plant, ModSounds.ICEMAN_CRYSTAL_TICKS.get(), .7f, .9f);
    }
    private static void releaseTick(ServerPlayer p, State s) {
        switch (s.weapon) {
            case W_MACE -> {
                if (s.age == MACE_SLAM_HIT) giantSlam(p, s);
                if (s.age >= MACE_SLAM_TICKS) { breakWeapon(p, s, 1 + (MACE_MAX - 1) * s.charge, 1); s.charge = 0; set(s, IDLE); }
            }
            case W_SPEAR -> {
                if (s.age == SPEAR_THROW_AT) throwSpear(p, s);
                if (s.age >= SPEAR_THROW_TICKS) { s.charge = 0; set(s, IDLE); }
            }
            default -> {
                if (s.age == 1) {
                    s.weaponOut = false;
                    s.cooldowns[CD_WEAPON] = Math.max(s.cooldowns[CD_WEAPON], IcemanConfig.CD_WEAPON.get());
                    s.formPending = true;
                }
                if (s.age >= SWORD_BREAK_TICKS) { s.charge = 0; s.kneel = null; set(s, IDLE); }
            }
        }
    }
    /** The grown mace comes down: damage, radius, knockback and the throw up all grow with its size. */
    private static void giantSlam(ServerPlayer p, State s) {
        float k = s.charge, size = 1 + (MACE_MAX - 1) * k;
        Vec3 f = facing(p);
        Vec3 at = p.position().add(f.scale(1.6 + .95 * size));
        Vec3 g = ground(p.serverLevel(), at.add(0, 1.5, 0), 6);
        if (g != null) at = g;
        double radius = IcemanConfig.MACE_SLAM_RADIUS.get() * (.3 + .7 * k);
        float damage = f(IcemanConfig.MACE_SLAM_DAMAGE) * (.35f + .65f * k);
        for (LivingEntity t : around(p, at, radius)) {
            double d = Math.sqrt(t.distanceToSqr(at));
            float fall = (float) Mth.clamp(1 - .5 * d / radius, .5, 1);
            if (!hurt(p, t, damage * fall, frost(W_MACE) * (1 + k))) continue;
            Vec3 away = flat(t.position().subtract(at));
            double knock = (.5 + 1.3 * k) * fall;
            t.setDeltaMovement(away.x * knock, .45 + .75 * k * fall, away.z * knock);
            t.hurtMarked = true;
        }
        fxAt(p, at, FX_SLAM, at, f, size, p.getId(), 1);
        at(p, at, ModSounds.ICEMAN_MACE_SLAM.get(), 1.4f + k, 1f - .35f * k);
        at(p, at, ModSounds.ICEMAN_SHATTER.get(), 1.2f + k, .8f - .2f * k);
        at(p, at, SoundEvents.GENERIC_EXPLODE, .5f + .6f * k, 1.3f - .4f * k);
    }

    // ------------------------------------------------------------------ the thrown spear
    private static void throwSpear(ServerPlayer p, State s) {
        float k = s.charge;
        Vec3 from = hand(p, 0).add(0, .15, 0);
        Vec3 aim = aimPoint(p, 80);
        Vec3 dir = aim.subtract(from).normalize();
        Spear sp = new Spear(nextId++, p, from, dir.scale(1.5 + 2.1 * k), k);
        SPEARS.add(sp);
        s.weaponOut = false;
        s.formPending = true;
        s.cooldowns[CD_WEAPON] = Math.max(s.cooldowns[CD_WEAPON], IcemanConfig.CD_WEAPON.get());
        fxAt(p, from, FX_SPEAR, from, sp.vel, k, p.getId(), sp.id);
        sound(p, ModSounds.FX_WHOOSH_HEAVY.get(), .7f + .4f * k, 1.3f);
    }
    static void tickSpears() {
        for (Iterator<Spear> it = SPEARS.iterator(); it.hasNext(); ) {
            Spear sp = it.next();
            ServerPlayer p = sp.owner;
            if (p.isRemoved()) { it.remove(); continue; }
            sp.age++;
            if (sp.stuckAge >= 0) {
                sp.stuckAge++;
                if (sp.stuckIn != null && sp.stuckIn.isAlive()) sp.pos = sp.stuckIn.position().add(sp.offset);
                // The clients crack and shatter it in stages (IcemanSpearFx); here it is only forgotten.
                if (sp.stuckAge >= SPEAR_STUCK) it.remove();
                continue;
            }
            if (sp.age > 90) { fxAt(p, sp.pos, FX_SHATTER, sp.pos, sp.vel.scale(.2), .7f, -1, 4); it.remove(); continue; }
            Vec3 next = sp.pos.add(sp.vel);
            EntityHitResult eh = entityOnPath(p, sp.pos, next, .35);
            if (eh != null && eh.getEntity() instanceof LivingEntity t) {
                Vec3 at = eh.getLocation();
                hurt(p, t, f(IcemanConfig.SPEAR_THROW_DAMAGE) * (.4f + .6f * sp.charge), frost(W_SPEAR) * 2.2f);
                Vec3 d = sp.vel.normalize();
                t.setDeltaMovement(t.getDeltaMovement().add(d.x * .5, .15, d.z * .5));
                t.hurtMarked = true;
                sp.stuckIn = t;
                sp.offset = at.subtract(t.position());
                stick(sp, at, t);
                continue;
            }
            BlockHitResult bh = p.level().clip(new ClipContext(sp.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (bh.getType() != HitResult.Type.MISS) { stick(sp, bh.getLocation(), null); continue; }
            sp.pos = next;
            sp.vel = sp.vel.add(0, -sp.gravity, 0).scale(.995);
        }
    }
    private static void stick(Spear sp, Vec3 at, LivingEntity in) {
        ServerPlayer p = sp.owner;
        sp.pos = at;
        sp.stuckAge = 0;
        fxAt(p, at, FX_SPEAR_STUCK, at, sp.vel.normalize(), sp.charge, in == null ? -1 : in.getId(), sp.id);
        at(p, at, SoundEvents.TRIDENT_HIT_GROUND, .8f, .8f);
        at(p, at, ModSounds.ICEMAN_HIT.get(), 1f, .9f);
        // The spikes burst out of the ground round it.
        double radius = IcemanConfig.SPEAR_SPIKE_RADIUS.get() * (.6 + .4 * sp.charge);
        Vec3 g = ground(p.serverLevel(), at.add(0, .5, 0), 5);
        Vec3 base = g == null ? at : g;
        for (LivingEntity t : around(p, base, radius)) {
            if (t == in) continue;
            if (!hurt(p, t, f(IcemanConfig.SPEAR_SPIKE_DAMAGE), frost(W_SPEAR))) continue;
            Vec3 away = flat(t.position().subtract(base));
            t.setDeltaMovement(away.x * .35, .5, away.z * .35);
            t.hurtMarked = true;
        }
        fxAt(p, base, FX_SPEAR_SPIKES, base, Vec3.ZERO, (float) radius, -1, sp.id);
        at(p, base, ModSounds.ICEMAN_SPIKES.get(), 1.1f, 1.05f);
        at(p, base, ModSounds.ICEMAN_GROW_RUMBLE.get(), .8f + .4f * sp.charge, .85f);
    }
    static void clear(ServerPlayer p) { SPEARS.removeIf(sp -> sp.owner == p); }
    static ServerLevel level(ServerPlayer p) { return p.serverLevel(); }
}
