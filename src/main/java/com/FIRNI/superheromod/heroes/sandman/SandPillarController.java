package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * KUM KULESI — havada ikinci kez bosluga basinca altinda yukselir.
 *
 * Kule GERCEK KUM BLOKLARINDAN kuruluyor ve KALICI. Bu, modun geri
 * kalanindaki "blok yazma" kuralinin bilincli istisnasi: diger
 * yeteneklerde birakilan yapi harita tahribati olurdu, burada ise kule
 * yetenegin URUNU -- oyuncu savasirken kendi arazisini insa ediyor.
 *
 * Bicim: 7 blok, yukari dogru INCELEN gokdelen. Her kat rastgele kayiyor
 * ve koseler rastgele eksiliyor; duz bir sutun istenen dagiliklgi
 * vermiyordu.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandPillarController {

    /** Kulenin yuksekligi (blok). */
    private static final int TOWER_HEIGHT = 7;

    /**
     * Baslangic dikey hizi -- 15 blok tepe yuksekligi verir.
     *
     * Minecraft her tick v -= 0.08 sonra v *= 0.98 uyguluyor. Deger
     * formulle degil SIMULASYONLA secildi: surtunme kapali formu bozuyor
     * ve sqrt(2gh) 15 blok icin fazla dusuk cikiyor.
     */
    private static final double LAUNCH_SPEED = 1.7;

    private static final int COOLDOWN_TICKS = 140;

    private static final Map<UUID, Integer> cooldowns = new HashMap<>();
    /** Kuleyle havalanan oyuncular -- bir sonraki inise kadar dusme hasari yok. */
    private static final Map<UUID, Integer> fallGrace = new HashMap<>();

    private SandPillarController() {}

    /** Istemci havada ikinci bosluk basisini gorunce burayi cagirir. */
    public static void launch(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.onGround()) return;
        if (cooldowns.getOrDefault(player.getUUID(), 0) > 0) return;

        Vec3 ground = SandSpikeController.groundUnder(level, player.position());
        Vec3 base = ground != null ? ground : player.position().subtract(0, 1.0, 0);

        buildTower(level, BlockPos.containing(base));
        cooldowns.put(player.getUUID(), COOLDOWN_TICKS);

        // Mevcut dusus hizi SIFIRLANIYOR: asagi dusen oyuncuda itme
        // yutuluyor ve yetenek calismamis gibi gorunuyordu.
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x * 0.7, LAUNCH_SPEED, motion.z * 0.7);
        player.hurtMarked = true;
        player.fallDistance = 0f;

        fallGrace.put(player.getUUID(), 300);

        level.playSound(null, BlockPos.containing(base),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.6f, 0.6f);
        level.sendParticles(sand(), base.x, base.y + 0.2, base.z,
                40, 0.7, 0.3, 0.7, 0.2);
    }

    /**
     * Gercek kum bloklarindan incelen kule.
     *
     * Kule zemin blogunun USTUNDEN basliyor; taban seviyesine yazilsaydi
     * zemin blogunun yerini alir ve yerde delik acardi.
     */
    private static void buildTower(ServerLevel level, BlockPos ground) {
        Random rnd = new Random(ground.getX() * 31L + ground.getZ() * 17L + ground.getY());

        for (int y = 0; y < TOWER_HEIGHT; y++) {
            // Tabanda 2 yaricap, tepeye dogru daralir
            double t = y / (double) (TOWER_HEIGHT - 1);
            int radius = (int) Math.round(2.0 * (1.0 - t * 0.75));

            // Kat kaydirmasi -- katlar tam hizali olunca gokdelen degil
            // dumduz bir kule cikiyordu
            int shiftX = radius > 0 ? rnd.nextInt(3) - 1 : 0;
            int shiftZ = radius > 0 ? rnd.nextInt(3) - 1 : 0;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Koseleri ve rastgele bloklari atla: keskin kare
                    // katlar yerine kirik siluet
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius
                            && rnd.nextInt(3) != 0) continue;
                    if (radius > 0 && rnd.nextInt(9) == 0) continue;

                    BlockPos pos = ground.offset(dx + shiftX, y, dz + shiftZ);
                    if (!canReplace(level, pos)) continue;

                    // Araya sert kum tasi: tek dokulu kule cansiz duruyordu
                    level.setBlockAndUpdate(pos,
                            rnd.nextInt(5) == 0
                                    ? Blocks.SANDSTONE.defaultBlockState()
                                    : Blocks.SAND.defaultBlockState());
                }
            }
        }
    }

    /**
     * Sadece BOS yerlere yaziliyor.
     *
     * Mevcut bloklarin uzerine yazsaydi yetenek bir insa araci degil yikim
     * araci olurdu; oyuncunun yapisini yiyip gecerdi.
     */
    private static boolean canReplace(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
                || level.getBlockState(pos).canBeReplaced();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ServerLifecycleHooks.getCurrentServer() == null) return;

        cooldowns.replaceAll((id, ticks) -> Math.max(0, ticks - 1));
        fallGrace.entrySet().removeIf(e -> {
            e.setValue(e.getValue() - 1);
            return e.getValue() <= 0;
        });
    }

    /**
     * Dusme hasarini iptal eder.
     *
     * Muafiyet SURE ile sinirli; kalici olsaydi oyuncu yetenegi bir kez
     * kullanip sonsuza dek dusus bagisikligi kazanirdi.
     */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!fallGrace.containsKey(player.getUUID())) return;

        event.setCanceled(true);
        fallGrace.remove(player.getUUID());

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(sand(),
                    player.getX(), player.getY() + 0.1, player.getZ(),
                    16, 0.4, 0.1, 0.4, 0.06);
        }
    }

    public static boolean isReady(UUID playerId) {
        return cooldowns.getOrDefault(playerId, 0) <= 0;
    }

    /** Kalan bekleme (tick) -- arayuz gostergesi icin. */
    public static int cooldown(UUID playerId) {
        return cooldowns.getOrDefault(playerId, 0);
    }

    public static void clear() {
        cooldowns.clear();
        fallGrace.clear();
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }
}
