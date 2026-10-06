package com.FIRNI.superheromod.heroes.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.hero.HeroIdentitySync;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.CameraStatePacket;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * /iceman test ... : every Iceman power on demand, each saying what it shows. Those that need a target put a training
 * dummy (a still husk with a lot of health, "Buz Kuklası") in front of you, removed after 90 seconds; those that are about
 * Iceman's own moves make you Iceman first. The victim's side (frost on your own screen, the slide's lens) works as
 * any hero or none.
 *   brush            a dummy 7 blocks ahead: hold right click on it (the stream), or into the air (sculpting)
 *   frost [0..100]   your own frost meter set (default 50): the stages on your body and your screen; 100 = deep freeze
 *   slide            the icy lens of the slide on your own screen, and dummies along a line to slide through
 *   shell            the shell forms round you at once
 *   shellhit [n]     the shell takes n damage (default 20): its hit reactions, light / heavy / very heavy
 *   shellbreak       the shell's health to zero: it shatters, the tired landing, the rise
 *   shellburst       the shell bursts (the 360 degree explosion)
 *   weapons          the next weapon of the wheel, and three dummies
 *   mace|spear|sword that weapon in hand, and a dummy
 *   shatteredground  a dummy 11 blocks ahead, the cracks run to it
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class IcemanTest {
    private IcemanTest() {}

    private record Dummy(Husk body, long until) {}
    private static final List<Dummy> DUMMIES = new ArrayList<>();

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> test = Commands.literal("test");
        for (String what : new String[]{"brush", "slide", "shell", "shellbreak", "shellburst", "weapons", "mace", "spear", "sword", "shatteredground"})
            test.then(Commands.literal(what).executes(ctx -> run(ctx, what, -1)));
        test.then(Commands.literal("frost").executes(ctx -> run(ctx, "frost", 50))
                .then(Commands.argument("amount", FloatArgumentType.floatArg(0, 100)).executes(ctx -> run(ctx, "frost", FloatArgumentType.getFloat(ctx, "amount")))));
        test.then(Commands.literal("shellhit").executes(ctx -> run(ctx, "shellhit", 20))
                .then(Commands.argument("damage", FloatArgumentType.floatArg(0, 2000)).executes(ctx -> run(ctx, "shellhit", FloatArgumentType.getFloat(ctx, "damage")))));
        event.getDispatcher().register(Commands.literal("iceman").requires(s -> s.hasPermission(2)).then(test));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String what, float amount) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        switch (what) {
            case "frost" -> {
                IcemanFrost.set(p, amount);
                int stage = stage(amount, amount >= FROST_MAX);
                say(p, "Don ölçerin: " + Math.round(amount) + " (" + new String[]{"yok", "hafif: nefes buharı, ayakta kırağı", "kristaller bacakta, omuzda buz",
                        "kollarda ve gövdede buz, ekran kenarı buğulu", "ağır buz, ekran köşeleri donuk, çatırtılar", "DERİN DONMA: buz kalıbı, kısa süre hareketsiz"}[stage]
                        + "). Kendini üçüncü şahıs kamerada da gör (F5).");
            }
            case "slide" -> {
                IcemanController.fxTo(p, FX_LENS, p.position().add(p.getLookAngle().scale(4)), Vec3.ZERO, 1, -1, 80);
                IcemanFrost.set(p, 20);
                ensure(p);
                for (int i = 0; i < 3; i++) dummy(p, 8 + i * 3.5, (i - 1) * 1.2);
                say(p, "Ekranındaki buzlu mercek: kaydırağın değdiği birinin gördüğü. Önündeki kuklaların içinden SHIFT basılı kayarak geç.");
            }
            case "brush" -> { ensure(p); dummy(p, 7, 0); say(p, "Sağ tık basılı: kuklaya akış (don ölçeri hızla dolar, 100'de derin donma); boşluğa doğru: fareyi gezdirerek buz heykeli."); }
            case "shell" -> { ensure(p); IcemanShell.testShell(p, IcemanController.state(p)); say(p, "Kriyojenik Kabuk: ayaktan başa kapanır. Q tekrar: patlama. /iceman test shellhit 5 / 15 / 40 ile vuruş tepkileri."); }
            case "shellhit" -> { ensure(p); IcemanShell.testHit(p, IcemanController.state(p), amount); say(p, "Kabuk " + Math.round(amount) + " hasar aldı (" + (amount < 4 ? "hafif" : amount < 10 ? "ağır" : "çok ağır") + " tepki)."); }
            case "shellbreak" -> { ensure(p); IcemanShell.testBreak(p, IcemanController.state(p)); say(p, "Kabuğun canı sıfır: parçalanır, yorgun kahraman inişi, yavaşça kalkış."); }
            case "shellburst" -> { ensure(p); IcemanShell.testBurst(p, IcemanController.state(p)); say(p, "Kabuk patlaması: iç gerilim, uğultu, sonra 360 derece patlama."); }
            case "weapons" -> {
                ensure(p);
                var s = IcemanController.state(p);
                int next = (s.weapon + 1) % WEAPONS;
                s.cooldowns[CD_WEAPON] = 0;
                IcemanWeapons.select(p, s, next);
                for (int i = 0; i < 3; i++) dummy(p, 4.5, (i - 1) * 2.2);
                say(p, WEAPON_NAMES[next] + ": sol tık 1-2 (sağdan sola, soldan sağa), 3. vuruş bitirici; sol tık basılı: güçlü hali. E basılı: silah çarkı.");
            }
            case "mace", "spear", "sword" -> {
                ensure(p);
                var s = IcemanController.state(p);
                int w = what.equals("mace") ? W_MACE : what.equals("spear") ? W_SPEAR : W_SWORD;
                s.cooldowns[CD_WEAPON] = 0;
                if (s.weapon == w) { s.weaponOut = false; s.weapon = (w + 1) % WEAPONS; }
                IcemanWeapons.select(p, s, w);
                dummy(p, w == W_SPEAR ? 9 : 4.5, 0);
                say(p, switch (w) {
                    case W_MACE -> "Gürz: ağır. 3. vuruş iki elle tepeden ezme (gürz kırılır). Basılı tut: katman katman büyür (3 kat), bırak: dev ezme.";
                    case W_SPEAR -> "Mızrak: hızlı. 3. vuruş seri dürtme. Basılı tut: geri çekip fırlat (ne kadar uzun, o kadar düz), saplandığı yerde buz dikenleri.";
                    default -> "Kılıç: orta hız. 3. vuruş hızlı dönüş. Basılı tut: Garen gibi dönerek çevrendekileri içeri çeker, sonunda kılıç kırılır.";
                });
            }
            case "shatteredground" -> {
                ensure(p);
                Husk d = dummy(p, 11, 0);
                if (d != null) IcemanGround.test(p, d);
                say(p, "Parçalanmış Zemin: çatlaklar kuklaya koşar, buz dikenleri fırlatır. Önce donmuşsa (don ölçer yüksek / 100) çok daha güçlü.");
            }
            default -> {}
        }
        return 1;
    }
    private static void say(ServerPlayer p, String text) { p.sendSystemMessage(Component.literal("§b[Iceman testi] §f" + text)); }
    /** Makes the player Iceman (if not already). */
    private static void ensure(ServerPlayer p) {
        if (IcemanController.isHero(p)) return;
        AbilityManager.assignCharacter(p, ID);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new CameraStatePacket(true));
        HeroIdentitySync.broadcast(p);
    }
    /** A training dummy ahead (dist blocks along his facing, side blocks to his right), facing him. */
    private static Husk dummy(ServerPlayer p, double dist, double side) {
        ServerLevel level = p.serverLevel();
        Vec3 f = IcemanController.facing(p), right = new Vec3(-f.z, 0, f.x);
        Vec3 at = p.position().add(f.scale(dist)).add(right.scale(side));
        Vec3 g = IcemanController.ground(level, at.add(0, 2, 0), 8);
        if (g != null) at = g;
        Husk d = EntityType.HUSK.create(level);
        if (d == null) return null;
        d.moveTo(at.x, at.y, at.z, p.getYRot() + 180, 0);
        d.setYHeadRot(p.getYRot() + 180);
        d.finalizeSpawn(level, level.getCurrentDifficultyAt(d.blockPosition()), MobSpawnType.COMMAND, null, null);
        d.setNoAi(true);
        d.setPersistenceRequired();
        d.setCustomName(Component.literal("Buz Kuklası"));
        d.setCustomNameVisible(true);
        var hp = d.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(300);
        d.setHealth(300);
        d.addTag("iceman_test");
        level.addFreshEntity(d);
        DUMMIES.add(new Dummy(d, level.getGameTime() + 1800));
        return d;
    }
    static void tick(MinecraftServer server) {
        if (DUMMIES.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Iterator<Dummy> it = DUMMIES.iterator(); it.hasNext(); ) {
            Dummy d = it.next();
            if (d.body().isRemoved()) { it.remove(); continue; }
            if (now > d.until()) { d.body().discard(); it.remove(); }
        }
    }
}
