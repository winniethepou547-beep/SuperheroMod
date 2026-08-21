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
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * KUM SUTUNU — havada ikinci kez bosluga basinca altindan firlar.
 *
 * Overwatch'taki buz yukselisi gibi calisir: oyuncunun altinda bir kutle
 * olusur ve onu yukari tasir. Kutle GERCEK BLOK DEGIL — haritaya kalici
 * yapi yazmak PvP haritalarinda kabul edilemez; itme dogrudan hiz olarak
 * veriliyor, sutun ise partikul govdesi olarak ciziliyor.
 *
 * Sutun yaklasik 5 saniye durur, sonra parcalanip dagilir.
 *
 * Dusme hasari bu yetenekten SONRA tamamen kapaniyor: oyuncuyu kendi
 * yetenegi havaya atip sonra dususte cezalandirsaydi yetenek kullanilmaz
 * hale gelirdi.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandPillarController {

    /** Sutunun ayakta kalma suresi (~5 saniye). */
    private static final int PILLAR_TICKS = 100;
    /** Sutunun parcalanip yok olma suresi. */
    private static final int SHATTER_TICKS = 14;

    private static final int COOLDOWN_TICKS = 140;

    private static final double BOOST = 1.05;
    private static final double PILLAR_HEIGHT = 4.5;
    private static final double PILLAR_RADIUS = 0.85;

    private static final class Pillar {
        final ServerLevel level;
        final Vec3 base;
        int ticks = 0;
        boolean shattering = false;
        int shatterTicks = 0;

        Pillar(ServerLevel level, Vec3 base) {
            this.level = level;
            this.base = base;
        }
    }

    private static final Map<UUID, Pillar> pillars = new HashMap<>();
    private static final Map<UUID, Integer> cooldowns = new HashMap<>();
    /** Sutunla havalanan oyuncular — bir sonraki inise kadar dusme hasari yok. */
    private static final Map<UUID, Integer> fallGrace = new HashMap<>();

    private SandPillarController() {}

    /** Istemci havada ikinci bosluk basisini gorunce burayi cagirir. */
    public static void launch(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.onGround()) return;
        if (cooldowns.getOrDefault(player.getUUID(), 0) > 0) return;
        if (pillars.containsKey(player.getUUID())) return;

        // Sutun oyuncunun ALTINDAKI ZEMINDEN cikar; havanin ortasindan
        // bitseydi neye basip yukseldigi anlasilmazdi
        Vec3 ground = SandSpikeController.groundUnder(level, player.position());
        Vec3 base = ground != null ? ground : player.position().subtract(0, 1.0, 0);

        pillars.put(player.getUUID(), new Pillar(level, base));
        cooldowns.put(player.getUUID(), COOLDOWN_TICKS);

        // Yukari itis: mevcut dusus hizi SIFIRLANIYOR, yoksa asagi dusen
        // oyuncuda itme yutuluyor ve yetenek calismamis gibi gorunuyordu
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x * 0.6, BOOST, motion.z * 0.6);
        player.hurtMarked = true;
        player.fallDistance = 0f;

        // Dusme hasari muafiyeti: yere degene kadar surer
        fallGrace.put(player.getUUID(), 200);

        level.playSound(null, BlockPos.containing(base),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.5f, 0.7f);
        level.sendParticles(sand(), base.x, base.y + 0.2, base.z,
                30, 0.5, 0.2, 0.5, 0.15);
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

        if (pillars.isEmpty()) return;

        Iterator<Map.Entry<UUID, Pillar>> it = pillars.entrySet().iterator();
        while (it.hasNext()) {
            Pillar pillar = it.next().getValue();

            if (pillar.shattering) {
                pillar.shatterTicks++;
                shatter(pillar);
                if (pillar.shatterTicks >= SHATTER_TICKS) it.remove();
                continue;
            }

            pillar.ticks++;
            draw(pillar);

            if (pillar.ticks >= PILLAR_TICKS) {
                pillar.shattering = true;
                pillar.level.playSound(null, BlockPos.containing(pillar.base),
                        SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.3f, 0.8f);
            }
        }
    }

    /**
     * Dusme hasarini iptal eder.
     *
     * Muafiyet SURE ile sinirli tutuluyor; kalici olsaydi oyuncu yetenegi
     * bir kez kullanip sonsuza dek dusus bagisikligi kazanirdi.
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

    /** Sutunun govdesi — burgulu dizilim ile silindir hissi veriyor. */
    private static void draw(Pillar pillar) {
        // Yukselme animasyonu: ilk tick'lerde sutun asagidan yukari uzuyor
        float grow = Math.min(1f, pillar.ticks / 6f);
        double height = PILLAR_HEIGHT * grow;

        int rings = (int) (height * 3);
        for (int i = 0; i < rings; i++) {
            double y = pillar.base.y + (i / (double) rings) * height;

            // Her halka bir oncekine gore doner; duz dizilim sutunu
            // dumduz bir cizgi gibi gosteriyordu
            double twist = i * 0.8 + pillar.ticks * 0.05;
            for (int k = 0; k < 3; k++) {
                double angle = twist + k * (Math.PI * 2 / 3);
                pillar.level.sendParticles(sand(),
                        pillar.base.x + Math.cos(angle) * PILLAR_RADIUS,
                        y,
                        pillar.base.z + Math.sin(angle) * PILLAR_RADIUS,
                        1, 0.06, 0.06, 0.06, 0.0);
            }
        }

        // Tabanda birikinti — sutunun yerden ciktigini anlatir
        if (pillar.ticks % 4 == 0) {
            pillar.level.sendParticles(sand(),
                    pillar.base.x, pillar.base.y + 0.1, pillar.base.z,
                    3, PILLAR_RADIUS, 0.05, PILLAR_RADIUS, 0.01);
        }
    }

    /** Parcalanma — sutun disari savrulan kum bulutuna donusur. */
    private static void shatter(Pillar pillar) {
        float t = pillar.shatterTicks / (float) SHATTER_TICKS;

        for (int i = 0; i < 12; i++) {
            double y = pillar.base.y + pillar.level.random.nextDouble() * PILLAR_HEIGHT;
            double angle = pillar.level.random.nextDouble() * Math.PI * 2;
            double spread = PILLAR_RADIUS + t * 1.6;

            pillar.level.sendParticles(sand(),
                    pillar.base.x + Math.cos(angle) * spread,
                    y,
                    pillar.base.z + Math.sin(angle) * spread,
                    1, 0.1, 0.1, 0.1, 0.06);
        }

        if (pillar.shatterTicks == 1) {
            pillar.level.sendParticles(fallingSand(),
                    pillar.base.x, pillar.base.y + PILLAR_HEIGHT * 0.5, pillar.base.z,
                    24, 0.6, PILLAR_HEIGHT * 0.4, 0.6, 0.02);
        }
    }

    public static boolean isReady(UUID playerId) {
        return cooldowns.getOrDefault(playerId, 0) <= 0;
    }

    /** Kalan bekleme (tick) — arayuz gostergesi icin. */
    public static int cooldown(UUID playerId) {
        return cooldowns.getOrDefault(playerId, 0);
    }

    public static void clear() {
        pillars.clear();
        cooldowns.clear();
        fallGrace.clear();
    }

    /** Havada suzulen kum — FALLING_DUST blok durumu ister. */
    private static BlockParticleOption fallingSand() {
        return new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }

}
