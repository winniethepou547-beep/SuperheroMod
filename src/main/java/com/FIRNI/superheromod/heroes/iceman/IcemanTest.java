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
 *   swordplant       the sword in hand and three dummies round about 4 to 6 blocks ahead: hold left click, the sword goes
 *                    into the ground and pulls them toward it (the dummies are moved straight, they have no AI)
 *   shatteredground  a dummy 11 blocks ahead, the cracks run to it
 *   solid            solid ice to try for 20 seconds: a bar of sculpted ice across your way at head height (walk into it),
 *                    and to your right a raised stretch of track rising like a ramp (walk up it and stand on it)
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class IcemanTest {
    private IcemanTest() {}

    private record Dummy(Husk body, long until) {}
    private static final List<Dummy> DUMMIES = new ArrayList<>();

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> test = Commands.literal("test");
        for (String what : new String[]{"shift", "shift_up", "shift_cancel", "shift_air", "shift_descend", "shift_speed", "shift_slope", "brush", "slide", "shell", "shellbreak", "shellburst", "weapons", "mace", "spear", "sword", "swordplant", "shatteredground", "solid"})
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
            case "shift", "shift_up", "shift_cancel", "shift_air", "shift_descend", "shift_speed", "shift_slope" -> {
                ensure(p);
                int mode = switch (what) {
                    case "shift_up" -> AUTO_UP; case "shift_cancel" -> AUTO_CANCEL; case "shift_air" -> AUTO_AIR;
                    case "shift_descend" -> AUTO_DESCEND; case "shift_speed" -> AUTO_SPEED; case "shift_slope" -> AUTO_SLOPE; default -> AUTO_SHIFT;
                };
                IcemanSlide.autopilot(p, IcemanController.state(p), mode);
                if (mode == AUTO_SHIFT || mode == AUTO_SPEED) for (int i = 0; i < 2; i++) dummy(p, 14 + i * 6, (i == 0 ? 1.5 : -1.5));
                say(p, switch (mode) {
                    case AUTO_UP -> "Kendi kendine kayıyorsun: 1 sn sonra SPACE basılı gibi buz yolu sağa doğru çapraz yükselir, sonra aşağı kayarsın. F5 ile dışarıdan izle.";
                    case AUTO_CANCEL -> "Hızlanıp yerde SHIFT bırakılır: ayak sürtme, buz sıçraması, gövde dönüşü, fren ve duruş; buz arkadan çatlayıp çözülür.";
                    case AUTO_AIR -> "Yükselip HAVADA SHIFT bırakılır: buz yolu son noktada kristalleşir, momentumla ayrılıp düşer ve iner (düşme hasarı yok).";
                    case AUTO_DESCEND -> "Yükselir, SPACE bırakılır: kısa tepe, sonra kendi buz yolundan aşağı hızlanarak kayar.";
                    case AUTO_SPEED -> "Uzun düz kayış: yavaş başlar, buz oluşur, hızlanır, en yüksek hıza çıkar (FOV ve rüzgâr artar).";
                    case AUTO_SLOPE -> "Dalgalı yol: SPACE aralıklarla basılır, yukarı - tepe - aşağı - tekrar; hız iniş çıkışla değişir.";
                    default -> "Kendi kendine kayıyorsun: hazırlık, buz oluşumu, hızlanma, sağa ve sola dönüşler, sonra bırakış. Önündeki kuklalara değersen donarlar.";
                });
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
                    default -> "Kılıç: orta hız. 3. vuruş hızlı dönüş. Basılı tut: kılıcı yere saplar, diz çöker; çevredekileri kılıca doğru çeker. Bırakınca kılıç çatlar ve kırılır.";
                });
            }
            case "swordplant" -> {
                ensure(p);
                var s = IcemanController.state(p);
                s.cooldowns[CD_WEAPON] = 0;
                if (s.weapon == W_SWORD) { s.weaponOut = false; s.weapon = W_MACE; }
                IcemanWeapons.select(p, s, W_SWORD);
                dummy(p, 4.5, -2.2);
                dummy(p, 5.5, 2.0);
                dummy(p, 6.2, 0);
                say(p, "Kılıç elinde oluşuyor. Sol tıkı BASILI TUT: kılıcı ucu aşağı çevirir, önüne yere saplar ve diz çöker; zemin donar, "
                        + "kristaller kılıca doğru büyür, sis içeri akar ve kuklalar kılıca doğru sürüklenir (yaklaştıkça daha hızlı). Bırakınca kılıç önce çatlar, sonra parçalanır. F5 ile dışarıdan da izle.");
            }
            case "shatteredground" -> {
                ensure(p);
                Husk d = dummy(p, 11, 0);
                if (d != null) IcemanGround.test(p, d);
                say(p, "Parçalanmış Zemin: çatlaklar kuklaya koşar, buz dikenleri fırlatır. Önce donmuşsa (don ölçer yüksek / 100) çok daha güçlü.");
            }
            case "solid" -> {
                ensure(p);
                solidTest(p);
                say(p, "Katı buz (20 sn): önünde baş hizasında bir buz kütlesi var, içinden geçmeye çalış (geçememelisin). Sağında yükselen bir buz yolu: "
                        + "üstüne çık, üstünde yürü ve dur (düşmemelisin). Okla vurursan ok sekmeli.");
            }
            default -> {}
        }
        return 1;
    }
    /** Ticks the solid test's ice stands. */
    private static final int SOLID_TEST_LIFE = 400;
    /**
     * The solid test: a bar of sculpted ice across his way, 4 blocks ahead at head height, 4 blocks wide (real sculpture
     * boxes); and to his right a raised stretch of track (the slide's own boxes) rising from knee height to 1.6 blocks
     * over 5.6 blocks and running on level, drawn as a thin bar of ice along its top so it can be seen.
     */
    private static void solidTest(ServerPlayer p) {
        Vec3 f = IcemanController.facing(p), right = new Vec3(-f.z, 0, f.x), base = p.position();
        float radius = IcemanConfig.f(IcemanConfig.SCULPT_THICKNESS);
        List<Vec3> bar = new ArrayList<>();
        for (double s = -2; s <= 2.001; s += SCULPT_STEP) bar.add(base.add(f.scale(4)).add(right.scale(s)).add(0, 1.55, 0));
        IcemanBrush.test(p, bar, radius, SOLID_TEST_LIFE, true);
        List<Vec3> look = new ArrayList<>();
        Vec3 start = base.add(right.scale(2.6)).add(f.scale(1.2));
        double length = 8.4;
        for (double d = 0; d <= length + .001; d += IcemanSlide.SOLID_STEP) {
            double top = Math.min(1.6, .3 + d * .23);
            IcemanSlide.solidBox(p, start.add(f.scale(d)).add(0, top - IcemanSlide.SOLID_HEIGHT, 0), (float) IcemanSlide.SOLID_HEIGHT,
                    SOLID_TEST_LIFE + SCULPT_CRACK);
        }
        // What can be seen of it: a thin bar of ice just under the boxes' top.
        for (double d = 0; d <= length + .001; d += SCULPT_STEP) look.add(start.add(f.scale(d)).add(0, Math.min(1.6, .3 + d * .23) - .2, 0));
        IcemanBrush.test(p, look, .2f, SOLID_TEST_LIFE, false);
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
        // An adult on its own feet (never a baby, never riding a chicken).
        d.finalizeSpawn(level, level.getCurrentDifficultyAt(d.blockPosition()), MobSpawnType.COMMAND, new net.minecraft.world.entity.monster.Zombie.ZombieGroupData(false, false), null);
        d.setBaby(false);
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
