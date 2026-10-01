package com.FIRNI.superheromod.core.command;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.cinematic.CinematicDirector;
import com.FIRNI.superheromod.heroes.sandman.SandArmyPreview;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SuperheroMod.MODID)
public final class CinematicPreviewCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cinematic").requires(s->s.hasPermission(2))
                .then(Commands.literal("stop").executes(ctx->{
                    CinematicDirector.abort(ctx.getSource().getPlayerOrException().getUUID()); return 1;
                }))
                .then(Commands.literal("pause").executes(ctx -> control(ctx, CinematicDirector.Transport.PAUSE, 0)))
                .then(Commands.literal("resume").executes(ctx -> control(ctx, CinematicDirector.Transport.RESUME, 0)))
                .then(Commands.literal("seek").then(Commands.argument("seconds", FloatArgumentType.floatArg(0, 600))
                        .executes(ctx -> control(ctx, CinematicDirector.Transport.SEEK, FloatArgumentType.getFloat(ctx,"seconds") * 20))))
                .then(Commands.literal("step").then(Commands.argument("ticks", FloatArgumentType.floatArg(-12000, 12000))
                        .executes(ctx -> control(ctx, CinematicDirector.Transport.STEP, FloatArgumentType.getFloat(ctx,"ticks")))))
                .then(Commands.literal("speed").then(Commands.argument("rate", FloatArgumentType.floatArg(.1f, 2))
                        .executes(ctx -> control(ctx, CinematicDirector.Transport.SPEED, FloatArgumentType.getFloat(ctx,"rate")))))
                .then(Commands.literal("preview").then(Commands.literal("sand_army")
                        .then(Commands.argument("target",EntityArgument.entity())
                                .then(Commands.literal("at").then(Commands.argument("seconds",FloatArgumentType.floatArg(0,600))
                                        .executes(ctx -> startAt(ctx,FloatArgumentType.getFloat(ctx,"seconds")))))
                                .executes(ctx->{
                            var player=ctx.getSource().getPlayerOrException();
                            var entity=EntityArgument.getEntity(ctx,"target");
                            if(!(entity instanceof LivingEntity target) || player.distanceToSqr(entity)>1024
                                    || !com.FIRNI.superheromod.heroes.sandman.SandArmySession.start(player,target)) {
                                ctx.getSource().sendFailure(Component.literal("Sahne baslatilamadi: 32 blok icinde, mesgul olmayan baska bir canli sec."));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(()->Component.literal("Sand Army filmi: hasar yok. Durdurmak icin H"),false);
                            return 1;
                        })))));
    }

    private static int startAt(CommandContext<CommandSourceStack> ctx, float seconds) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var entity = EntityArgument.getEntity(ctx,"target");
        if (!(entity instanceof LivingEntity target) || player.distanceToSqr(entity) > 1024
                || !CinematicDirector.startPreview(SandArmyPreview.ID,player,target)) {
            ctx.getSource().sendFailure(Component.literal("32 blok icinde mesgul olmayan baska bir canli sec."));
            return 0;
        }
        CinematicDirector.control(player,CinematicDirector.Transport.SEEK,seconds * 20);
        return 1;
    }

    private static int control(CommandContext<CommandSourceStack> ctx, CinematicDirector.Transport action,
                               float value) throws CommandSyntaxException {
        if (!CinematicDirector.control(ctx.getSource().getPlayerOrException(), action, value)) {
            ctx.getSource().sendFailure(Component.literal("Once H ile bir prova baslat. Bu kontroller sadece kendi hasarsiz provanda calisir."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Sinematik prova: " + action.name().toLowerCase(java.util.Locale.ROOT)
                + (action == CinematicDirector.Transport.SEEK || action == CinematicDirector.Transport.STEP
                || action == CinematicDirector.Transport.SPEED ? " " + value : "")), false);
        return 1;
    }
}
