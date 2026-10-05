package com.FIRNI.superheromod.core.command;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.batman.BatmanController;
import com.FIRNI.superheromod.heroes.panther.PantherController;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Trying the block moves of Batman (Q, reflex block) and Black Panther (R, Panther Reflex) on their own, whichever of the
 * two you are:
 *   /blokdene bos     the block comes on and nothing hits it (the stance alone)
 *   /blokdene yakin   a test zombie in front strikes three times: from the right, straight on, from the left (Batman's
 *                     gauntlet deflects one way each time; Panther parries), then it goes
 *   /blokdene uzak    three arrows from 12 blocks off, a little apart (Batman's cape sweep; Panther parries)
 * The cooldown is ignored and the block comes on again before every hit, so each one is seen.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BlockTestCommands {
    private BlockTestCommands() {}

    /** Something to do a few ticks from now. */
    private record Job(ServerPlayer p, long at, Runnable run) {}
    private static final List<Job> JOBS = new ArrayList<>();

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("blokdene").requires(s -> s.hasPermission(2))
                .then(Commands.literal("bos").executes(ctx -> run(ctx, 0)))
                .then(Commands.literal("yakin").executes(ctx -> run(ctx, 1)))
                .then(Commands.literal("uzak").executes(ctx -> run(ctx, 2))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!BatmanController.isHero(p) && !PantherController.isHero(p)) {
            ctx.getSource().sendFailure(Component.literal("Bu komut Batman ya da Black Panther iken çalışır."));
            return 0;
        }
        long now = p.level().getGameTime();
        switch (mode) {
            case 0 -> block(p);
            case 1 -> {
                // A still zombie at arm's length in front, striking from the right, the front, the left.
                Zombie z = EntityType.ZOMBIE.create(p.level());
                if (z == null) return 0;
                Vec3 fwd = flat(p), right = new Vec3(-fwd.z, 0, fwd.x);
                place(z, p, fwd.scale(1.6));
                z.setNoAi(true); z.setSilent(true); z.setInvulnerable(true); z.setPersistenceRequired();
                z.setCustomName(Component.literal("Blok Testi"));
                p.level().addFreshEntity(z);
                double[] sides = {.9, 0, -.9};
                for (int i = 0; i < 3; i++) {
                    double side = sides[i];
                    long at = now + 8 + i * 22L;
                    JOBS.add(new Job(p, at - 4, () -> { place(z, p, flat(p).scale(1.6).add(new Vec3(-flat(p).z, 0, flat(p).x).scale(side))); block(p); }));
                    JOBS.add(new Job(p, at, () -> { z.swing(net.minecraft.world.InteractionHand.MAIN_HAND); p.hurt(p.damageSources().mobAttack(z), 2); }));
                }
                JOBS.add(new Job(p, now + 8 + 3 * 22L, z::discard));
            }
            default -> {
                for (int i = 0; i < 3; i++) {
                    double side = (i - 1) * 1.5;
                    long at = now + 8 + i * 22L;
                    JOBS.add(new Job(p, at - 6, () -> block(p)));
                    JOBS.add(new Job(p, at, () -> arrow(p, side)));
                }
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(mode == 0 ? "Blok açıldı (vuran yok)." : mode == 1 ? "Yakından üç vuruş geliyor: sağ, ön, sol." : "Uzaktan üç ok geliyor."), false);
        return 1;
    }
    private static void block(ServerPlayer p) {
        if (!BatmanController.testBlock(p)) PantherController.testBlock(p);
    }
    private static Vec3 flat(ServerPlayer p) {
        Vec3 l = p.getLookAngle();
        Vec3 f = new Vec3(l.x, 0, l.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }
    private static void place(Zombie z, ServerPlayer p, Vec3 offset) {
        Vec3 at = p.position().add(offset);
        float yaw = (float) Math.toDegrees(Math.atan2(-(p.getX() - at.x), p.getZ() - at.z));
        z.moveTo(at.x, p.getY(), at.z, yaw, 0);
        z.setYHeadRot(yaw); z.yBodyRot = yaw;
    }
    /** An arrow from 12 blocks in front (side = blocks to his right), straight at his chest. */
    private static void arrow(ServerPlayer p, double side) {
        Vec3 fwd = flat(p), right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 chest = p.position().add(0, p.getBbHeight() * .65, 0);
        Vec3 from = chest.add(fwd.scale(12)).add(right.scale(side)).add(0, .4, 0);
        Arrow a = new Arrow(p.level(), from.x, from.y, from.z);
        Vec3 dir = chest.subtract(from);
        a.shoot(dir.x, dir.y, dir.z, 2.2f, 0);
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        a.setBaseDamage(1);
        p.level().addFreshEntity(a);
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || JOBS.isEmpty()) return;
        for (Job j : new ArrayList<>(JOBS)) {
            if (j.p().isRemoved() || !(j.p().level() instanceof ServerLevel)) { JOBS.remove(j); continue; }
            if (j.p().level().getGameTime() >= j.at()) { JOBS.remove(j); j.run().run(); }
        }
    }
}
