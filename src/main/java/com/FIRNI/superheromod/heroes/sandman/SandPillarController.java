package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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

    /**
     * Z tusuna basilinca cagrilir.
     *
     * Havada olma sarti KALDIRILDI: yetenek artik kendi tusunda ve yerden
     * de kullanilabilmeli. Ziplama tusuna bagliyken havada olmak dogal bir
     * sartti, simdi gereksiz bir kisitlama olurdu.
     */
    public static void launch(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (cooldowns.getOrDefault(player.getUUID(), 0) > 0) return;

        Vec3 ground = SandSpikeController.groundUnder(level, player.position());
        Vec3 base = ground != null ? ground : player.position().subtract(0, 1.0, 0);
        BlockPos origin = BlockPos.containing(base);

        BlockPos top = buildTower(level, origin);
        cooldowns.put(player.getUUID(), COOLDOWN_TICKS);

        // OYUNCU KULENIN TEPESINE TASINIYOR.
        //
        // Onceden oyuncu bulundugu yerde kaliyordu ve kule ONUN ETRAFINA
        // kuruluyordu -- yani kulenin ICINDE sikisiyordu. Kule zaten
        // ayaklarinin altinda yukseliyor; dogru davranis oyuncunun onun
        // uzerine cikmasi.
        // Tepe blogu kaymis olabilir; oyuncu ORIJINE degil gercek tepeye
        // konuyor, yoksa yine kulenin yan yuzune girebilirdi.
        player.teleportTo(top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5);

        // Mevcut dusus hizi SIFIRLANIYOR: asagi dusen oyuncuda itme
        // yutuluyor ve yetenek calismamis gibi gorunuyordu.
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x * 0.7, LAUNCH_SPEED, motion.z * 0.7);
        player.hurtMarked = true;
        player.fallDistance = 0f;

        fallGrace.put(player.getUUID(), 300);

        blastBelow(level, origin);
    }

    /**
     * Kulenin dibindeki patlama.
     *
     * Sadece gorsel -- blok kirmiyor, hasar vermiyor. Kule yukari
     * firlarken tabaninda hicbir sey olmuyordu ve hareket "sessizce
     * yukselen bir asansor" gibi duruyordu; itisin nereden geldigi
     * gorunmuyordu.
     */
    private static void blastBelow(ServerLevel level, BlockPos origin) {
        Vec3 c = new Vec3(origin.getX() + 0.5, origin.getY() + 0.4, origin.getZ() + 0.5);

        level.playSound(null, origin,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 1.5f);
        level.playSound(null, origin,
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.7f, 0.55f);

        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.9, 0.15, 0.9, 0.0);

        // Yanlara savrulan kum halkasi — patlamanin yonu disari
        for (int i = 0; i < 26; i++) {
            double angle = (i / 26.0) * Math.PI * 2;
            level.sendParticles(sand(),
                    c.x + Math.cos(angle) * 1.1, c.y, c.z + Math.sin(angle) * 1.1,
                    2, 0.15, 0.1, 0.15, 0.22);
        }

        level.sendParticles(sand(), c.x, c.y, c.z, 40, 0.8, 0.3, 0.8, 0.2);
    }

    /**
     * Gercek kum bloklarindan incelen kule.
     *
     * Kule zemin blogunun USTUNDEN basliyor; taban seviyesine yazilsaydi
     * zemin blogunun yerini alir ve yerde delik acardi.
     */
    private static BlockPos buildTower(ServerLevel level, BlockPos ground) {
        Random rnd = new Random(ground.getX() * 31L + ground.getZ() * 17L + ground.getY());

        // KADEMELI DARALMA — duz konik daralma "kum tepesi" gibi duruyordu.
        //
        // Gokdelen silueti kesintisiz incelmeden gelmiyor: birkac kat ayni
        // genislikte kalip sonra BIRDEN daraliyor. Basamak basamak
        // daralma cizimdeki kirikligi veren sey.
        int[] radii = {2, 2, 1, 1, 1, 0, 0};

        int shiftX = 0;
        int shiftZ = 0;

        for (int y = 0; y < TOWER_HEIGHT; y++) {
            int radius = radii[Math.min(y, radii.length - 1)];

            // Kaymalar BIRIKIYOR: her kat bir oncekine gore kayiyor, hepsi
            // merkeze gore degil. Boylece kule yukari dogru hafifce
            // savruluyor, dik bir boru gibi durmuyor.
            if (radius > 0 && rnd.nextInt(2) == 0) {
                shiftX += rnd.nextInt(3) - 1;
                shiftZ += rnd.nextInt(3) - 1;
            }
            // Kayma sinirlandiriliyor, yoksa ust katlar tabandan tamamen
            // kopup havada asili kaliyor
            shiftX = Mth.clamp(shiftX, -1, 1);
            shiftZ = Mth.clamp(shiftZ, -1, 1);

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Koseleri ve rastgele bloklari atla: keskin kare
                    // katlar yerine kirik siluet
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius
                            && radius > 0 && rnd.nextInt(3) != 0) continue;
                    if (radius > 1 && rnd.nextInt(8) == 0) continue;

                    BlockPos pos = ground.offset(dx + shiftX, y, dz + shiftZ);
                    if (!canReplace(level, pos)) continue;

                    // Araya sert kum tasi: tek doku cansiz duruyordu
                    level.setBlockAndUpdate(pos,
                            rnd.nextInt(5) == 0
                                    ? Blocks.SANDSTONE.defaultBlockState()
                                    : Blocks.SAND.defaultBlockState());
                }
            }
        }

        // TEPE KATI HER ZAMAN DOLU.
        //
        // Oyuncu buraya isinlaniyor; rastgele eksiltme tepede delik
        // birakirsa oyuncu kulenin icine duser -- duzeltmeye calistigimiz
        // sikisma sorununun ta kendisi.
        BlockPos top = ground.offset(shiftX, TOWER_HEIGHT - 1, shiftZ);
        if (canReplace(level, top)) {
            level.setBlockAndUpdate(top, Blocks.SANDSTONE.defaultBlockState());
        }
        return top;
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
