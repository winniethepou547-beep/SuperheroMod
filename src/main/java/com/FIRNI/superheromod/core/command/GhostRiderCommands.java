package com.FIRNI.superheromod.core.command;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.ghostrider.PenanceStare;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Test commands for Ghost Rider moves.
 *   /ghostrider penance            spawn a still test dummy in front and perform Penance Stare on it
 *   /ghostrider penance <target>   perform it on a chosen entity
 *   /ghostrider dummy              spawn a still test dummy in front (for chains, F, punch)
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class GhostRiderCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ghostrider").requires(s -> s.hasPermission(2))
                .then(Commands.literal("penance")
                        .executes(ctx -> penance(ctx, null))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> penance(ctx, EntityArgument.getEntity(ctx, "target")))))
                .then(Commands.literal("dummy").executes(ctx -> {
                    dummy(ctx.getSource().getPlayerOrException(), 3);
                    ctx.getSource().sendSuccess(() -> Component.literal("Test dummy spawned."), false);
                    return 1;
                })));
    }
    private static int penance(CommandContext<CommandSourceStack> ctx, net.minecraft.world.entity.Entity chosen) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LivingEntity target = chosen instanceof LivingEntity living ? living : dummy(player, 2.2);
        if (target == null || !PenanceStare.start(player, target)) {
            ctx.getSource().sendFailure(Component.literal("Penance Stare could not start (target busy, too far or invalid)."));
            return 0;
        }
        return 1;
    }
    /** A zombie that stands still, does not burn in daylight and stays until removed. */
    private static LivingEntity dummy(ServerPlayer player, double distance) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 at = player.position().add(forward.scale(distance));
        Zombie zombie = EntityType.ZOMBIE.create(player.level());
        if (zombie == null) return null;
        zombie.moveTo(at.x, at.y, at.z, player.getYRot() + 180, 0);
        zombie.setYHeadRot(player.getYRot() + 180); zombie.yBodyRot = player.getYRot() + 180;
        zombie.setNoAi(true); zombie.setPersistenceRequired(); zombie.setSilent(true);
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
        zombie.setCustomName(Component.literal("Test Dummy"));
        player.level().addFreshEntity(zombie);
        return zombie;
    }
}
