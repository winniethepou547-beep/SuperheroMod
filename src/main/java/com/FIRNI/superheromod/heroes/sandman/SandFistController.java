package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SAND FIST — kol uzayip vurur.
 *
 * Onceki surum yerinde savrulan kisa bir yumruktu. Yeni bicim:
 *
 *   CHARGE   20 tick   kol kumla dolar, oyuncu nisan alir
 *   EXTEND    5 tick   kol HIZLICA one uzar, degdigi ilk hedefe vurur
 *   HOLD      3 tick   uzamis halde kisa duraklama (darbe agirligi)
 *   RETRACT   7 tick   kol geri toplanir
 *
 * Sarj bilerek var: bir anda uzayan kol refleksle kacilamaz hale gelir.
 * Sarj suresi rakibe "geliyor" sinyali veriyor, uzama ise cok hizli --
 * yani isabet nisan almaya degil ZAMANLAMAYA bagli.
 *
 * Kolun gorseli partikul DEGIL model: uzayan kisim kum dokulu kutulardan
 * olusan bogumlar ve ucundaki yumruk olarak ciziliyor. Partikul bulutu
 * "kolumun devami" gibi okunmuyordu.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandFistController {

    public enum Phase { CHARGE, EXTEND, HOLD, RETRACT }

    private static final int CHARGE_END = 20;    // 1 saniye
    private static final int EXTEND_END = 25;
    private static final int HOLD_END = 28;
    private static final int TOTAL_TICKS = 35;

    private static final BlockParticleOption SAND_BLOCK =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

    private static final class Strike {
        final UUID player;
        final AbilityConfig cfg;
        int ticks = 0;
        /** Bu vuruşta hasar alanlar — her hedef bir kez. */
        final Set<UUID> hit = new HashSet<>();
        /** Kolun o anki uzunlugu; cizim ve carpisma ayni degeri kullaniyor. */
        double length = 0;

        Strike(UUID player, AbilityConfig cfg) {
            this.player = player;
            this.cfg = cfg;
        }

        Phase phase() {
            if (ticks <= CHARGE_END) return Phase.CHARGE;
            if (ticks <= EXTEND_END) return Phase.EXTEND;
            if (ticks <= HOLD_END) return Phase.HOLD;
            return Phase.RETRACT;
        }
    }

    private static final Map<UUID, Strike> strikes = new HashMap<>();

    private SandFistController() {}

    public static void start(ServerPlayer player, AbilityConfig cfg) {
        strikes.put(player.getUUID(), new Strike(player.getUUID(), cfg));

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    public static boolean isSwinging(UUID playerId) {
        return strikes.containsKey(playerId);
    }

    /** Istemci poz sistemi icin: oyuncu su an hangi fazda? */
    public static Phase phaseOf(UUID playerId) {
        Strike strike = strikes.get(playerId);
        return strike == null ? null : strike.phase();
    }

    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (strikes.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, Strike>> it = strikes.entrySet().iterator();
        while (it.hasNext()) {
            Strike strike = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(strike.player);

            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            strike.ticks++;
            tickStrike(player, strike);

            if (strike.ticks >= TOTAL_TICKS) it.remove();
        }
    }

    private static void tickStrike(ServerPlayer player, Strike strike) {
        ServerLevel level = (ServerLevel) player.level();
        double maxLength = strike.cfg.getDouble("range", 8.0);

        switch (strike.phase()) {
            case CHARGE -> {
                strike.length = 0;
                gatherSand(level, player, strike.ticks / (float) CHARGE_END);

                // Sarjin bittigi an duyulur olmali: rakip bu sesi duyup
                // kacabilmeli, yoksa yetenek okunamaz olur
                if (strike.ticks == CHARGE_END) {
                    level.playSound(null, player.blockPosition(),
                            SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.5f, 0.7f);
                }
            }
            case EXTEND -> {
                float t = (strike.ticks - CHARGE_END) / (float) (EXTEND_END - CHARGE_END);

                // Hizlanan uzama: kol basta agir kalkip sonra firliyor.
                // Dogrusal uzama mekanik duruyordu.
                strike.length = maxLength * (t * t);
                checkHit(player, strike, level);
                trailSand(level, player, strike);
            }
            case HOLD -> {
                strike.length = maxLength;
                checkHit(player, strike, level);
            }
            case RETRACT -> {
                float t = (strike.ticks - HOLD_END) / (float) (TOTAL_TICKS - HOLD_END);
                strike.length = maxLength * (1f - t);
            }
        }
    }

    /**
     * Kolun ucunun o an degdigi hedefler.
     *
     * Sadece UC kontrol ediliyor, kolun tamami degil: kol geri cekilirken
     * uzerinden gectigi herkese tekrar vurmasi gerekmiyor ve "her hedef
     * bir kez" kurali zaten bunu engelliyor.
     */
    private static void checkHit(ServerPlayer player, Strike strike, ServerLevel level) {
        Vec3 fist = armOrigin(player).add(player.getLookAngle().scale(strike.length));
        double radius = strike.cfg.getDouble("radius", 0.9);

        AABB box = new AABB(fist, fist).inflate(radius);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == player) continue;
            if (target instanceof SandSoldierEntity soldier
                    && player.getUUID().equals(soldier.getOwnerId())) continue;
            if (!strike.hit.add(target.getUUID())) continue;

            target.hurt(level.damageSources().playerAttack(player),
                    strike.cfg.getFloat("damage", 6.0f));

            Vec3 push = player.getLookAngle();
            target.setDeltaMovement(
                    push.x * strike.cfg.getDouble("knockback", 0.9),
                    strike.cfg.getDouble("knockbackVertical", 0.35),
                    push.z * strike.cfg.getDouble("knockback", 0.9));
            target.hurtMarked = true;

            level.playSound(null, target.blockPosition(),
                    SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.4f, 0.55f);
            level.sendParticles(SAND_BLOCK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    22, 0.35, 0.4, 0.35, 0.16);
        }
    }

    /**
     * Sarj sirasinda kola cekilen kum.
     *
     * Parcalar disaridan kolun uzerine akiyor; hiz vektoru ICE dogru
     * veriliyor, boylece "toplaniyor" izlenimi olusuyor. Sarj ilerledikce
     * yogunlasiyor -- oyuncu ne kadar hazir oldugunu gorebilmeli.
     */
    private static void gatherSand(ServerLevel level, ServerPlayer player, float progress) {
        Vec3 arm = armOrigin(player);
        int count = 2 + (int) (progress * 5);

        for (int i = 0; i < count; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = 1.2 - progress * 0.7 + level.random.nextDouble() * 0.4;

            double px = arm.x + Math.cos(a) * r;
            double py = arm.y + (level.random.nextDouble() - 0.4) * 0.7;
            double pz = arm.z + Math.sin(a) * r;

            // Hedefe dogru hiz: parcacik kola cekiliyor
            Vec3 pull = arm.subtract(new Vec3(px, py, pz)).normalize().scale(0.12);

            level.sendParticles(SAND_BLOCK, px, py, pz, 0,
                    pull.x, pull.y, pull.z, 1.0);
        }
    }

    /** Uzayan kolun kenarindan dokulen kum. */
    private static void trailSand(ServerLevel level, ServerPlayer player, Strike strike) {
        Vec3 origin = armOrigin(player);
        Vec3 dir = player.getLookAngle();

        int steps = Math.max(2, (int) strike.length);
        for (int i = 1; i <= steps; i++) {
            Vec3 p = origin.add(dir.scale(strike.length * (i / (double) steps)));
            level.sendParticles(SAND_BLOCK, p.x, p.y, p.z, 1, 0.12, 0.12, 0.12, 0.02);
        }
    }

    /**
     * Kolun cikis noktasi — omuz/el hizasi.
     *
     * Goz hizasindan biraz asagi ve SAGA kaydirilmis: tam goz hizasindan
     * ciksaydi birinci sahiste ekranin ortasindan bir cubuk cikiyormus
     * gibi durur, ucuncu sahiste de kol govdenin icinden gecerdi.
     */
    private static Vec3 armOrigin(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();

        return player.getEyePosition(1.0f)
                .add(right.scale(0.36))
                .add(0, -0.3, 0)
                .add(look.scale(0.3));
    }

    /**
     * Kolu cizim listesine ekler.
     *
     * Kol MODEL olarak ciziliyor; kullanici partikulle yapilmamasini
     * acikca istedi. Konum ve yon her tick gonderiliyor, aradaki kareler
     * istemcide yumusatiliyor.
     */
    static void collectShapes(java.util.List<SandShapeSyncPacket.Shape> out) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (Strike strike : strikes.values()) {
            if (strike.length <= 0.05) continue;

            ServerPlayer player = server.getPlayerList().getPlayer(strike.player);
            if (player == null) continue;

            Vec3 origin = armOrigin(player);

            // Kalinlik geri cekilirken azaliyor: kol sabit kalinlikta
            // toplanirsa emilmiyor, siliniyormus gibi duruyor
            float thickness = strike.phase() == Phase.RETRACT ? 0.85f : 1.0f;

            out.add(new SandShapeSyncPacket.Shape(
                    strike.player.hashCode() ^ 0x5A17,
                    SandShapeSyncPacket.TYPE_ARM,
                    origin.x, origin.y, origin.z,
                    player.getYRot(), player.getXRot(),
                    (float) strike.length, thickness, 0f));
        }
    }

    /** Sunucu kapanirken kalinti kalmasin. */
    public static void clear() {
        strikes.clear();
    }

    /** Yumrugun o anki dunya konumu — baska sistemler icin. */
    public static Vec3 fistPosition(ServerPlayer player, double forward) {
        return armOrigin(player).add(player.getLookAngle().scale(forward));
    }
}
