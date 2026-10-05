package com.FIRNI.superheromod.core.command;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.batman.BatmanController;
import com.FIRNI.superheromod.heroes.batman.BatmanSonic;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Being on the receiving end of Batman's gadgets, to see what his opponents see (any hero or none):
 *   /etkidene flas    a flash grenade rolls to a stop just ahead of you and goes off (white-out, ringing, stars)
 *   /etkidene sonik   a sonic trap comes up either side of you, as if Batman stood 8 blocks in front, and pulses you
 *   /etkidene sis     a smoke cloud round you, as if someone else's (Batman himself sees through it with thermal vision,
 *                     so try it as another hero or none)
 * Everything works as in a real fight (the flash still needs you to see it); nothing here does damage.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class EffectTestCommands {
    private EffectTestCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("etkidene").requires(s -> s.hasPermission(2))
                .then(Commands.literal("flas").executes(ctx -> run(ctx, 0)))
                .then(Commands.literal("sonik").executes(ctx -> run(ctx, 1)))
                .then(Commands.literal("sis").executes(ctx -> run(ctx, 2))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int what) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        switch (what) {
            case 0 -> BatmanController.testFlash(p);
            case 1 -> {
                if (!BatmanSonic.test(p)) {
                    ctx.getSource().sendFailure(Component.literal("Önünde sonik vericiler için uygun zemin yok; açık bir yerde dene."));
                    return 0;
                }
            }
            default -> BatmanController.testSmoke(p);
        }
        return 1;
    }
}
