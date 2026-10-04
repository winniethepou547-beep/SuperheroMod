package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Q: the reflex block (vibranium-tough gauntlets and the cape). For REFLEX_SECONDS after the key, any attack that comes
 * in from the FRONT HALF of him (180 degrees about where he looks; nothing from behind) is read and deflected: from
 * close (within REFLEX_GAUNTLET_RANGE) onto the spikes of a gauntlet (right, left, or both crossed for straight ahead),
 * from further off with the cape swept in front by his right hand. Projectiles are sent off again at an angle. What has
 * no direction (explosions, falling, fire, drowning, starving, magic and anything that bypasses armour or
 * invulnerability) is never blocked. The result is the server's alone; the clients only draw the deflect (FX_BLOCK).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BatmanReflex {
    /** A projectile sent off again next tick (after the game's own bounce has run). */
    private record Deflect(Projectile p, Vec3 vel) {}
    private static final List<Deflect> DEFLECTS = new ArrayList<>();

    private BatmanReflex() {}

    static void start(ServerPlayer p, BatmanController.State s) {
        if (s.cooldowns[CD_REFLEX] > 0) {
            BatmanController.tell(p, "Refleks Blok: " + String.format(Locale.ROOT, "%.1f", s.cooldowns[CD_REFLEX] / 20f) + " sn");
            return;
        }
        if (s.action == GRAPNEL_PULL || s.action == GRAPNEL_STRIKE || s.action == DODGE || s.action == CANNON) return;
        long now = p.level().getGameTime();
        s.cooldowns[CD_REFLEX] = BatmanConfig.CD_REFLEX.get();
        s.reflexUntil = now + Math.max(1, Math.round(BatmanConfig.REFLEX_SECONDS.get() * 20));
        s.lastDeflect = -100;
        if (s.action == IDLE || s.action == WHEEL || s.action == PUNCH || s.action == SHOCK_PUNCH || s.action == BATARANG_CHARGE) {
            s.charging = false;
            BatmanController.set(s, REFLEX);
        }
        BatmanController.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE, .6f, 1.35f);
        BatmanController.sound(p, ModSounds.BATMAN_CAPE.get(), .35f, 1.5f);
    }

    /** Called for every attack on him: true = deflected (the attack is cancelled). */
    static boolean block(ServerPlayer p, BatmanController.State s, DamageSource src) {
        long now = p.level().getGameTime();
        if (now > s.reflexUntil) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || src.is(DamageTypeTags.BYPASSES_ARMOR) || src.is(DamageTypeTags.IS_EXPLOSION)
                || src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_DROWNING) || src.is(DamageTypeTags.IS_FREEZING)
                || src.is(DamageTypeTags.IS_LIGHTNING) || src.is(DamageTypes.MAGIC) || src.is(DamageTypes.INDIRECT_MAGIC) || src.is(DamageTypes.WITHER)) return false;
        Entity direct = src.getDirectEntity(), attacker = src.getEntity();
        Vec3 from = direct instanceof Projectile pr ? pr.position().subtract(pr.getDeltaMovement().scale(3)) : src.getSourcePosition();
        if (from == null) return false;
        Vec3 eye = p.getEyePosition();
        Vec3 in = new Vec3(from.x - eye.x, 0, from.z - eye.z);
        if (in.lengthSqr() < 1e-6) in = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        in = in.normalize();
        Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        look = look.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : look.normalize();
        // The front half only: whatever comes from behind him gets through.
        if (look.dot(in) < 0) return false;
        double dist = attacker != null ? attacker.distanceTo(p) : from.distanceTo(eye);
        int kind;
        if (dist > BatmanConfig.REFLEX_GAUNTLET_RANGE.get()) kind = BLOCK_CAPE;
        else {
            // Which side: his right is (-cos yaw, 0, -sin yaw) for Minecraft's yaw; straight ahead takes both gauntlets.
            Vec3 right = new Vec3(-look.z, 0, look.x);
            double side = right.dot(in);
            kind = side > .38 ? BLOCK_RIGHT : side < -.38 ? BLOCK_LEFT : BLOCK_FRONT;
        }
        deflect(p, s, kind, in, direct, attacker, dist);
        return true;
    }

    private static void deflect(ServerPlayer p, BatmanController.State s, int kind, Vec3 in, Entity direct, Entity attacker, double dist) {
        long now = p.level().getGameTime();
        // A new move at most every REFLEX_GAP ticks (a hail of arrows does not set off twenty moves); the deflects still count.
        boolean move = now - s.lastDeflect >= REFLEX_GAP;
        if (move) s.lastDeflect = now;
        Vec3 contact = kind == BLOCK_CAPE ? p.position().add(0, 1.1, 0).add(in.scale(.65))
                : kind == BLOCK_FRONT ? p.getEyePosition().add(0, -.25, 0).add(in.scale(.55))
                : BatmanController.hand(p, kind == BLOCK_RIGHT ? 0 : 1).add(in.scale(.25));
        BatmanController.fx(p, FX_BLOCK, contact, in, move ? kind : -kind, p.getId(), 0);
        if (kind == BLOCK_CAPE) {
            BatmanController.at(p, contact, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .9f, .55f);
            BatmanController.at(p, contact, ModSounds.BATMAN_CAPE.get(), .9f, .8f);
            BatmanController.at(p, contact, SoundEvents.ARMOR_EQUIP_LEATHER, 1f, .6f);
        } else {
            BatmanController.at(p, contact, SoundEvents.ANVIL_PLACE, .35f, 1.95f);
            BatmanController.at(p, contact, ModSounds.FX_IMPACT_METAL.get(), .8f, 1.3f + p.getRandom().nextFloat() * .2f);
            BatmanController.at(p, contact, SoundEvents.SHIELD_BLOCK, .7f, 1.45f);
        }
        if (direct instanceof Projectile pr) {
            // Sent off again: mirrored off the gauntlet / cape and thrown aside (and up off the cape), a little slower.
            Vec3 v = pr.getDeltaMovement();
            double speed = Math.max(.6, v.length() * .75);
            Vec3 n = in;
            Vec3 out = v.subtract(n.scale(2 * v.dot(n)));
            Vec3 aside = new Vec3(-in.z, 0, in.x).scale(kind == BLOCK_LEFT ? -1 : 1);
            out = out.normalize().add(aside.scale(kind == BLOCK_CAPE ? .35 : .7)).add(0, kind == BLOCK_CAPE ? .45 : .15, 0);
            DEFLECTS.add(new Deflect(pr, out.normalize().scale(speed)));
            pr.setOwner(p);
        } else if (attacker instanceof LivingEntity l && dist <= BatmanConfig.REFLEX_GAUNTLET_RANGE.get() + 1) {
            // A blow knocked aside: the attacker is pushed off balance a little.
            Vec3 push = l.position().subtract(p.position());
            push = new Vec3(push.x, 0, push.z);
            if (push.lengthSqr() > 1e-6) {
                push = push.normalize().scale(.45);
                l.setDeltaMovement(l.getDeltaMovement().add(push.x, .12, push.z));
                l.hurtMarked = true;
            }
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || DEFLECTS.isEmpty()) return;
        for (Deflect d : DEFLECTS) {
            if (d.p().isRemoved()) continue;
            d.p().setDeltaMovement(d.vel());
            d.p().setPos(d.p().position().add(d.vel().normalize().scale(.4)));
            d.p().hasImpulse = true;
        }
        DEFLECTS.clear();
    }
}
