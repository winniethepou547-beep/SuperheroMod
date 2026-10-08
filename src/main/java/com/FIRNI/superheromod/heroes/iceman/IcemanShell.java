package com.FIRNI.superheromod.heroes.iceman;

import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanController.State;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanConfig.f;
import static com.FIRNI.superheromod.heroes.iceman.IcemanController.*;

/**
 * Q: the cryogenic shell on the server. He folds in (compact) and the ice closes over him from the feet up (SHELL_FORM).
 * The shell has its own health: every blow goes into it (he takes nothing) and he heals inside. Its health gone, it
 * shatters and drops him into a tired hero landing (SHELL_BREAK: knee down, a hand on the ground, ice falling off him,
 * the slow rise). Q again (or its time up): the BURST: stress builds inside it, then it explodes outward, 360 degrees,
 * throwing everyone round him hard.
 */
final class IcemanShell {
    private IcemanShell() {}

    static void press(ServerPlayer p, State s) {
        if (s.action == SHELL_FORM && s.age >= SHELL_FORM_TICKS / 2 || s.action == SHELL) { burst(p, s); return; }
        if (s.action == SHELL_BREAK || s.action == SHELL_BURST || s.action == SHELL_FORM) return;
        if (s.cooldowns[CD_SHELL] > 0) { tell(p, "Kriyojenik Kabuk: " + seconds(s.cooldowns[CD_SHELL])); return; }
        if (s.action == BRUSH) IcemanBrush.release(p, s);
        if (s.action == SLIDE) IcemanSlide.stop(p, s);
        start(p, s);
    }
    static void start(ServerPlayer p, State s) {
        s.charge = 0; s.queued = false;
        s.shellHp = f(IcemanConfig.SHELL_HEALTH);
        set(s, SHELL_FORM);
        sound(p, ModSounds.ICEMAN_SHELL_FORM.get(), 1.2f, 1f);
        sound(p, ModSounds.ICEMAN_FORM_BIG.get(), .8f, .8f);
    }
    static void burst(ServerPlayer p, State s) {
        if (s.action == SHELL_BURST) return;
        set(s, SHELL_BURST);
        sound(p, ModSounds.ICEMAN_SHELL_STRESS.get(), 1.3f, 1f);
    }

    static void tick(ServerPlayer p, State s) {
        // Held still inside (his client holds the keys too).
        if (s.action != SHELL_BREAK || s.age < BREAK_LAND + 6) {
            p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
            p.hurtMarked = s.age % 4 == 0;
        }
        switch (s.action) {
            case SHELL_FORM -> { if (s.age >= SHELL_FORM_TICKS) set(s, SHELL); }
            case SHELL -> {
                if (s.age % 20 == 0 && p.getHealth() < p.getMaxHealth()) p.heal(f(IcemanConfig.SHELL_HEAL));
                if (s.testBreak) { s.testBreak = false; s.shellHp = 0; shatter(p, s, Vec3.ZERO); return; }
                if (s.age >= IcemanConfig.SHELL_SECONDS.get() * 20) burst(p, s);
            }
            case SHELL_BURST -> {
                if (s.age % 20 == 0 && p.getHealth() < p.getMaxHealth()) p.heal(f(IcemanConfig.SHELL_HEAL));
                if (s.age == BURST_STRESS) explode(p, s);
                if (s.age >= BURST_TICKS) end(p, s);
            }
            case SHELL_BREAK -> { if (s.age >= BREAK_TICKS) end(p, s); }
            default -> {}
        }
    }
    private static void end(ServerPlayer p, State s) {
        s.shellHp = 0;
        s.cooldowns[CD_SHELL] = Math.max(s.cooldowns[CD_SHELL], IcemanConfig.CD_SHELL.get());
        set(s, IDLE);
    }
    /** KRAAAK: everyone round him thrown away hard, hit and frosted; the shell's pieces fly. */
    private static void explode(ServerPlayer p, State s) {
        Vec3 c = p.position().add(0, 1, 0);
        double r = IcemanConfig.BURST_RADIUS.get(), knock = IcemanConfig.BURST_KNOCK.get();
        for (LivingEntity t : around(p, c, r)) {
            double d = Math.sqrt(t.distanceToSqr(c));
            float fall = (float) Math.max(.45, 1 - .55 * d / r);
            hurt(p, t, f(IcemanConfig.BURST_DAMAGE) * fall, f(IcemanConfig.BURST_FROST) * fall);
            Vec3 away = flat(t.position().subtract(p.position()));
            t.setDeltaMovement(away.x * knock * fall, .55 + .35 * fall, away.z * knock * fall);
            t.hurtMarked = true;
        }
        s.shellHp = 0;
        fx(p, FX_SHELL_BURST, p.position(), Vec3.ZERO, (float) r, p.getId(), 0);
        sound(p, ModSounds.ICEMAN_SHELL_BURST.get(), 2f, 1f);
        sound(p, ModSounds.ICEMAN_SHATTER.get(), 1.6f, .7f);
        sound(p, SoundEvents.GENERIC_EXPLODE, .7f, 1.3f);
    }
    /** Its health gone: it shatters and he drops into the tired landing. */
    private static void shatter(ServerPlayer p, State s, Vec3 from) {
        s.shellHp = 0;
        set(s, SHELL_BREAK);
        fx(p, FX_SHELL_BREAK, p.position(), from, 1, p.getId(), 0);
        sound(p, ModSounds.ICEMAN_SHELL_BREAK.get(), 1.6f, 1f);
        sound(p, SoundEvents.GLASS_BREAK, 1.2f, .6f);
    }
    /** A blow on him while the shell is up: the shell takes it (true: cancel it for him). */
    static boolean absorb(ServerPlayer p, State s, DamageSource source, float amount) {
        boolean up = s.action == SHELL || s.action == SHELL_BURST || s.action == SHELL_FORM && s.age >= 5;
        if (!up || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (s.action == SHELL_BURST) return true;
        // Like the body's own moment of invulnerability after a blow: within it only what is more than the last blow counts.
        long now = p.level().getGameTime();
        Hit last = HITS.get(p.getUUID());
        float counted = amount;
        if (last != null && now - last.at < 10) {
            if (amount <= last.amount) return true;
            counted = amount - last.amount;
        }
        HITS.put(p.getUUID(), new Hit(now, amount));
        s.shellHp -= counted;
        Vec3 from = source.getSourcePosition() == null ? Vec3.ZERO : flat(source.getSourcePosition().subtract(p.position()));
        fx(p, FX_SHELL_HIT, p.position(), from, amount, p.getId(), 0);
        float pitch = amount < 4 ? 1.3f : amount < 10 ? 1f : .75f;
        sound(p, ModSounds.ICEMAN_SHELL_HIT.get(), amount < 4 ? .8f : 1.3f, pitch);
        if (amount >= 10) sound(p, ModSounds.ICEMAN_CRACK.get(), 1.1f, .7f);
        if (s.shellHp <= 0) shatter(p, s, from);
        return true;
    }
    private record Hit(long at, float amount) {}
    private static final java.util.Map<java.util.UUID, Hit> HITS = new java.util.HashMap<>();

    // ------------------------------------------------------------------ tests (/iceman test shell...)
    static void testShell(ServerPlayer p, State s) { s.cooldowns[CD_SHELL] = 0; if (!shell(s)) start(p, s); }
    static void testHit(ServerPlayer p, State s, float damage) {
        if (!shell(s)) testShell(p, s);
        if (s.action == SHELL_FORM) set(s, SHELL);
        s.shellHp -= damage;
        fx(p, FX_SHELL_HIT, p.position(), new Vec3(0, 0, 1), damage, p.getId(), 0);
        sound(p, ModSounds.ICEMAN_SHELL_HIT.get(), 1.1f, damage < 4 ? 1.3f : damage < 10 ? 1f : .75f);
        if (s.shellHp <= 0) shatter(p, s, new Vec3(0, 0, 1));
    }
    static void testBreak(ServerPlayer p, State s) { if (!shell(s)) testShell(p, s); set(s, SHELL); s.testBreak = true; }
    static void testBurst(ServerPlayer p, State s) { if (!shell(s)) testShell(p, s); if (s.action == SHELL_FORM) set(s, SHELL); burst(p, s); }
}
