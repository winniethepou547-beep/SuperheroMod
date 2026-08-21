package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import com.FIRNI.superheromod.network.packet.SandArmSyncPacket;
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
            // Darbe geri bildirimi SADECE isabet aninda. Cevredeki
            // surekli kum partikulleri kaldirildi: kol artik model
            // oldugu icin partikuller onu gizliyor ve dagitiyordu.
            level.playSound(null, target.blockPosition(),
                    SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.4f, 0.55f);
            level.sendParticles(SAND_BLOCK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    22, 0.35, 0.4, 0.35, 0.16);
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
     * Kolun uzunlugunu istemcilere bildirir.
     *
     * Sadece UZUNLUK gonderiliyor, konum degil: kol dunya uzayinda degil
     * oyuncu modelinin katmani olarak ciziliyor, konumu kolun kendisi
     * belirliyor. Onceki surum dunya koordinati gonderiyordu ve kol
     * govdeden kopuk duruyordu.
     */
    static void collectArms(java.util.List<SandArmSyncPacket.Entry> out) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (Strike strike : strikes.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(strike.player);
            if (player == null) continue;

            // active bayragi uzunluktan AYRI: sarj sirasinda kol henuz
            // uzamamis oluyor ama poz zaten ileri bakmali
            out.add(new SandArmSyncPacket.Entry(
                    player.getId(), (float) strike.length, true));
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
