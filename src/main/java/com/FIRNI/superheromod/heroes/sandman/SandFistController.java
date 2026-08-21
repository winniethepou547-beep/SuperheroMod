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

    /** Tusa basildiktan sonra uzamaya kadar gecen sure (0.2 sn). */
    private static final int CHARGE_END = 4;

    /**
     * Kolun tick basina uzama hizi (blok).
     *
     * Sure yerine HIZ tutuluyor cunku kol artik sabit sureli degil: tus
     * birakilana kadar uzamis kaliyor. Sabit sureli bir egri, degisken
     * uzunlukta anlamsizdi.
     *
     * Deger %50 artirildi: 8 bloga 11 tickte varan hiz "yeterince hizli
     * gitmiyor" geri bildirimiyle 7 ticke indi.
     */
    private static final double EXTEND_SPEED = 1.15;

    /** Geri toplanma hizi — uzamadan biraz yavas, agirlik hissi icin. */
    private static final double RETRACT_SPEED = 0.85;

    /** Balyozun tam boyuna ulasmasi (tick). */
    private static final int HAMMER_TICKS = 12;

    /** Kol en fazla bu kadar tutulabilir; sonra kendiliginden geri gelir. */
    private static final int MAX_HOLD_TICKS = 160;

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
        /** Balyozun olusma orani 0..1 (sadece tam uzunlukta buyur). */
        float hammer = 0f;
        /** Tus birakildi mi — birakilinca kol geri toplanmaya baslar. */
        boolean released = false;

        Strike(UUID player, AbilityConfig cfg) {
            this.player = player;
            this.cfg = cfg;
        }

        Phase phase() {
            if (released) return Phase.RETRACT;
            if (ticks <= CHARGE_END) return Phase.CHARGE;
            return hammer > 0f ? Phase.HOLD : Phase.EXTEND;
        }
    }

    private static final Map<UUID, Strike> strikes = new HashMap<>();

    private SandFistController() {}

    public static void start(ServerPlayer player, AbilityConfig cfg) {
        strikes.put(player.getUUID(), new Strike(player.getUUID(), cfg));

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    /**
     * Tus birakildi.
     *
     * Kol hemen kaybolmuyor, GERI TOPLANIYOR: aniden yok olmasi kolun
     * emildigi degil silindigi izlenimi veriyordu.
     */
    public static void release(ServerPlayer player) {
        Strike strike = strikes.get(player.getUUID());
        if (strike != null) strike.released = true;
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

            // Kol tamamen toplanunca is biter. Sabit toplam sure YOK:
            // yetenegin suresini artik oyuncu belirliyor.
            if (strike.released && strike.length <= 0.01) it.remove();
        }
    }

    private static void tickStrike(ServerPlayer player, Strike strike) {
        ServerLevel level = (ServerLevel) player.level();
        double maxLength = strike.cfg.getDouble("range", 8.0);

        if (strike.released) {
            strike.length = Math.max(0, strike.length - RETRACT_SPEED);
            strike.hammer = Math.max(0f, strike.hammer - 0.12f);
            return;
        }

        // Cok uzun tutulursa kendiliginden geri gelir; yoksa oyuncu kolu
        // surekli uzatip duvar gibi kullanabilirdi
        if (strike.ticks > CHARGE_END + MAX_HOLD_TICKS) {
            strike.released = true;
            return;
        }

        if (strike.ticks <= CHARGE_END) {
            strike.length = 0;
            if (strike.ticks == CHARGE_END) {
                level.playSound(null, player.blockPosition(),
                        SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.5f, 0.7f);
            }
            return;
        }

        if (strike.length < maxLength) {
            strike.length = Math.min(maxLength, strike.length + EXTEND_SPEED);

            // Tam boya ULASILDIGI an balyoz olusmaya baslar
            if (strike.length >= maxLength - 0.001) {
                level.playSound(null, player.blockPosition(),
                        SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.2f, 0.5f);
                strike.hammer = 0.001f;
            }
        } else if (strike.hammer > 0f && strike.hammer < 1f) {
            // BALYOZ: kol tam uzunlukta tutuldugu surece ucta kum toplanir.
            // Sadece elini cekmeyen oyuncu bunu goruyor -- uzatip hemen
            // birakan biri duz yumrukla kaliyor.
            strike.hammer = Math.min(1f, strike.hammer + 1f / HAMMER_TICKS);
        }

        checkHit(player, strike, level);
        breakBlocks(player, strike, level);
    }

    /**
     * Kolun ve balyozun degdigi bloklari kirar.
     *
     * Kum kolu bir kutle: onune cikan araziyi delip gecmeli, yoksa duvara
     * dayanan bir cubuk gibi duruyor. Kirma SADECE UCTA oluyor -- kolun
     * govdesi de kirsaydi oyuncu ilerledikce arkasinda tunel acilirdi.
     *
     * Yaricap balyozla birlikte buyuyor; buyuk bir kutlenin ince bir
     * yumrukla ayni deligi acmasi gorsel yalan olurdu.
     */
    private static void breakBlocks(ServerPlayer player, Strike strike, ServerLevel level) {
        Vec3 fist = armOrigin(player).add(player.getLookAngle().scale(strike.length));
        double radius = 0.8 + strike.hammer * 0.9;

        BlockPos center = BlockPos.containing(fist);
        int r = (int) Math.ceil(radius);
        boolean broke = false;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;

                    BlockPos pos = center.offset(dx, dy, dz);
                    var state = level.getBlockState(pos);

                    if (state.isAir()) continue;
                    // Kirilmaz bloklar ve blok varliklari korunuyor:
                    // yetenek arazi acmali, oyuncunun sandigini yutmamali
                    if (state.getDestroySpeed(level, pos) < 0) continue;
                    if (state.hasBlockEntity()) continue;

                    level.destroyBlock(pos, false);
                    broke = true;
                }
            }
        }

        if (broke) impactBurst(level, fist, strike.hammer);
    }

    /** Kirilan yerde minik patlama + kum saclimasi. */
    private static void impactBurst(ServerLevel level, Vec3 at, float hammer) {
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(SAND_BLOCK, at.x, at.y, at.z,
                8 + (int) (hammer * 8), 0.35, 0.35, 0.35, 0.09);
        level.playSound(null, BlockPos.containing(at),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.8f, 0.7f);
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
        // Balyoz olustukca vurus alani buyur: buyuk bir kutle ince bir
        // yumrukla ayni menzile sahip olsaydi gorsel yalan olurdu
        double radius = strike.cfg.getDouble("radius", 0.9) * (1.0 + strike.hammer * 0.9);

        AABB box = new AABB(fist, fist).inflate(radius);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == player) continue;
            if (target instanceof SandSoldierEntity soldier
                    && player.getUUID().equals(soldier.getOwnerId())) continue;
            if (!strike.hit.add(target.getUUID())) continue;

            // Balyoz hasari da artiriyor -- elini cekmeyen oyuncunun odulu
            target.hurt(level.damageSources().playerAttack(player),
                    strike.cfg.getFloat("damage", 6.0f) * (1f + strike.hammer * 0.6f));

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
                    player.getId(), (float) strike.length, true, strike.hammer));
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
