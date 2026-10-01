package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.SandGraspPreviewPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SAND GRASP — sag tikin yeni hali (eski Sand Spike'in yerine).
 *
 * Sandman'in onune DIKDORTGEN bir alan cizilir. Onaylandiginda alanin UZAK
 * UCUNDAN dev bir kum eli firlar, alandaki herkese vurur ve hepsini
 * Sandman'a DOGRU ceker.
 *
 * Neden cekme yonu ONEMLI: Sandman yakin dovus karakteri. Eski diken
 * hedefi havaya atip UZAKLASTIRIYORDU, yani karakterin kendi oyun planiyla
 * celisiyordu. El artik dusmani kucagina getiriyor; ardindan gelen yumruk,
 * asker ve duvar yetenekleri anlam kazaniyor.
 *
 * Onay/iptal akisi Sand Wall ile ayni: yetenek once HAYALI alani acar,
 * sol tik onaylar, sag tik iptal eder. Nisan alma suresi bilerek serbest —
 * oyuncu aci ayarlayabilmeli.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandGraspController {

    /** Onizleme kendiliginden kapanmadan once bekleyecegi sure. */
    private static final int PREVIEW_TIMEOUT = 100;

    /** El uzak uctan cikip Sandman'a dogru sureklenirken gecen sure. */
    private static final int ERUPT_TICKS = 30;

    /**
     * Elin yerden cikip tam boyuna ulasmasi.
     *
     * Ayri bir asama: onceki surumde el ayni anda hem cikiyor hem
     * surukleniyordu, animasyon "cok hizli, belli olmuyor" olarak geri
     * geldi. Once cikar, SONRA ceker.
     */
    private static final int RISE_TICKS = 10;

    private enum State { PREVIEW, ERUPTING }

    private static final class Grasp {
        final UUID ownerId;
        final ServerLevel level;
        final AbilityConfig cfg;

        /** Dikdortgenin Sandman'a yakin ucunun ORTASI. */
        Vec3 near = Vec3.ZERO;
        /** Dikdortgenin uzak ucunun ORTASI — el buradan cikar. */
        Vec3 far = Vec3.ZERO;
        /** Alanin ileri yonu (near -> far), birim. */
        Vec3 forward = new Vec3(0, 0, 1);
        /** Alanin yan yonu, birim. */
        Vec3 side = new Vec3(1, 0, 0);

        State state = State.PREVIEW;
        int ticks = 0;
        final Set<UUID> struck = new HashSet<>();

        Grasp(UUID ownerId, ServerLevel level, AbilityConfig cfg) {
            this.ownerId = ownerId;
            this.level = level;
            this.cfg = cfg;
        }
    }

    private static final Map<UUID, Grasp> grasps = new HashMap<>();

    private SandGraspController() {}

    // ------------------------------------------------------------------
    // Giris noktalari
    // ------------------------------------------------------------------

    public static void press(ServerPlayer player, AbilityConfig cfg) {
        if (grasps.containsKey(player.getUUID())) return;

        Grasp grasp = new Grasp(player.getUUID(), (ServerLevel) player.level(), cfg);
        aim(player, grasp);
        grasps.put(player.getUUID(), grasp);

        setClientPreview(player, true);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SAND_STEP, SoundSource.PLAYERS, 0.9f, 1.5f);
    }

    public static void confirm(ServerPlayer player) {
        Grasp grasp = grasps.get(player.getUUID());
        if (grasp == null || grasp.state != State.PREVIEW) return;

        grasp.state = State.ERUPTING;
        grasp.ticks = 0;
        grasp.struck.clear();

        setClientPreview(player, false);

        grasp.level.playSound(null, BlockPos.containing(grasp.far),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.6f, 0.5f);
    }

    public static void cancel(ServerPlayer player) {
        Grasp grasp = grasps.remove(player.getUUID());
        if (grasp == null) return;

        setClientPreview(player, false);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.7f, 1.3f);
    }

    public static boolean hasPreview(UUID playerId) {
        Grasp grasp = grasps.get(playerId);
        return grasp != null && grasp.state == State.PREVIEW;
    }

    // ------------------------------------------------------------------
    // Nisan
    // ------------------------------------------------------------------

    /**
     * Dikdortgeni oyuncunun BAKIS YONUNE gore yerlestirir.
     *
     * Yalnizca yatay bakis kullaniliyor: dikey acidan etkilenseydi yere
     * bakinca alan ayaklarin dibinde toplaniyor, gokyuzune bakinca havada
     * kaliyordu. Alan her zaman zeminde ve onde duruyor.
     */
    private static void aim(ServerPlayer player, Grasp grasp) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)).normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);

        double length = grasp.cfg.getDouble("length", 9.0);
        double startGap = grasp.cfg.getDouble("startGap", 1.2);

        Vec3 feet = new Vec3(player.getX(), player.getY(), player.getZ());
        grasp.near = feet.add(forward.scale(startGap));
        grasp.far = feet.add(forward.scale(startGap + length));
        grasp.forward = forward;
        grasp.side = side;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (grasps.isEmpty()) return;
        if (ServerLifecycleHooks.getCurrentServer() == null) return;

        Iterator<Map.Entry<UUID, Grasp>> it = grasps.entrySet().iterator();
        while (it.hasNext()) {
            Grasp grasp = it.next().getValue();
            ServerPlayer owner = grasp.level.getServer()
                    .getPlayerList().getPlayer(grasp.ownerId);

            if (owner == null || !owner.isAlive()) {
                it.remove();
                continue;
            }

            grasp.ticks++;

            if (grasp.state == State.PREVIEW) {
                // Onizleme oyuncuyu TAKIP EDER: sabit kalsaydi oyuncu donunce
                // alan arkasinda kalir, nisan almak imkansizlasirdi
                aim(owner, grasp);

                if (grasp.ticks > PREVIEW_TIMEOUT) {
                    setClientPreview(owner, false);
                    it.remove();
                }
                continue;
            }

            tickErupt(grasp, owner);

            if (grasp.ticks > RISE_TICKS + ERUPT_TICKS + 8) {
                it.remove();
            }
        }
    }


    /**
     * EL UZAK UCTAN CIKAR VE SANDMAN'A DOGRU SUREKLENIR.
     *
     * Yon bilerek boyle: el once en uzaktaki dusmani yakalar, geri
     * gelirken aradaki herkesi toplar. Yakindan uzaga gitseydi alandaki
     * dusmanlar dagilma yonunde itilirdi.
     */
    private static void tickErupt(Grasp grasp, ServerPlayer owner) {
        // IKI ASAMA: once el yerden cikar, sonra ceker.
        //
        // Onceki surumde ikisi ayni anda oluyordu ve toplam 12 tick
        // suruyordu; animasyon "cok hizli, belli olmuyor" diye geri geldi.
        // Cikis ayrilinca elin bicimi gorulebiliyor, cekis de yavasladi.
        if (grasp.ticks <= RISE_TICKS) {
            drawHand(grasp, grasp.far, 0f);
            return;
        }

        float progress = Math.min(1f,
                (grasp.ticks - RISE_TICKS) / (float) ERUPT_TICKS);

        // Elin o anki konumu: uzak uctan yakin uca.
        // Hizlanma egrisi: el once agir kalkar, sonra hizlanir. Duz
        // dogrusal hareket makine gibi duruyordu.
        float eased = progress * progress * (3f - 2f * progress);
        Vec3 hand = grasp.far.add(
                grasp.near.subtract(grasp.far).scale(eased));

        drawHand(grasp, hand, progress);

        // Elin gectigi kesitte kalanlar vurulur — her hedef bir kez
        double reach = grasp.cfg.getDouble("width", 3.0) * 0.5 + 0.6;
        AABB box = new AABB(
                hand.x - reach, hand.y - 1.0, hand.z - reach,
                hand.x + reach, hand.y + 3.4, hand.z + reach);

        for (LivingEntity target : grasp.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == owner) continue;
            if (target instanceof SandSoldierEntity soldier
                    && owner.getUUID().equals(soldier.getOwnerId())) continue;
            if (!grasp.struck.add(target.getUUID())) continue;

            strike(grasp, owner, target);
        }

        // ELIN GECTIGI KIRMIZI ALAN KUMA DONUSUR.
        //
        // Gosterge tehlikeyi ONCEDEN gosteriyor; el gectikce o alan
        // gercekten Sandman'in arazisi oluyor. Alan tek parca degil
        // parca parca birakiliyor, boylece donusum ilerledikce
        // izlenebiliyor.
        if (grasp.ticks % 2 == 0) {
            Vec3 ground = SandSpikeController.groundUnder(
                    grasp.level, hand.add(0, 1.0, 0));
            if (ground != null) {
                SandPatchController.drop(owner, ground,
                        grasp.cfg.getDouble("width", 3.0) * 0.5);
            }
        }
    }

    /** Hasar + Sandman'a dogru cekis. */
    private static void strike(Grasp grasp, ServerPlayer owner, LivingEntity target) {
        float damage = grasp.cfg.getFloat("damage", 6.0f);
        target.hurt(grasp.level.damageSources().playerAttack(owner), damage);

        // CEKIS: hedefi Sandman'in konumuna dogru surukler.
        //
        // Sabit bir vektor yerine hedeften oyuncuya olan gercek yon
        // kullaniliyor; boylece alanin kenarindakiler de merkeze toplaniyor.
        Vec3 pull = owner.position().subtract(target.position());
        double dist = pull.length();
        if (dist < 0.001) return;

        double strength = grasp.cfg.getDouble("pull", 1.15);
        Vec3 impulse = pull.scale(1.0 / dist).scale(strength);

        // Hafif yukari bileseni: tamamen yatay itis zeminde takiliyordu
        target.setDeltaMovement(impulse.x, Math.max(0.28, impulse.y * 0.4), impulse.z);
        target.hurtMarked = true;   // hiz degisimi istemciye bildirilmezse etki gorunmuyor

        grasp.level.sendParticles(sand(),
                target.getX(), target.getY() + 1.0, target.getZ(),
                18, 0.3, 0.4, 0.3, 0.1);
    }

    // ------------------------------------------------------------------
    // Gorsel
    // ------------------------------------------------------------------

    /** Dikdortgen zemin gostergesi — dort kenar partikulle ciziliyor. */


    /**
     * Elin cevresindeki kum.
     *
     * Elin KENDISI artik geometri olarak ciziliyor (SandShapeRenderer);
     * burasi sadece etrafa savrulan dokuntu. Partikuller once elin govdesi
     * olarak kullaniliyordu ve somut cisim gibi okunmuyordu — ikisi birden
     * yogun kalirsa bu sefer sekil partikul bulutunun icinde kayboluyor.
     */
    private static void drawHand(Grasp grasp, Vec3 hand, float progress) {
        double halfWidth = grasp.cfg.getDouble("width", 3.0) * 0.5;

        Vec3 base = SandSpikeController.groundUnder(grasp.level, hand.add(0, 1.5, 0));
        if (base == null) base = hand;
        else base = new Vec3(hand.x, base.y, hand.z);

        // Elin yerden ciktigi yerde kum savrulur
        grasp.level.sendParticles(sand(),
                base.x, base.y + 0.25, base.z, 5, halfWidth * 0.6, 0.15, halfWidth * 0.6, 0.05);

        // Surukleme izi — elin arkasinda kalan yarik
        Vec3 trail = base.subtract(grasp.forward.scale(0.8));
        grasp.level.sendParticles(sand(),
                trail.x, trail.y + 0.1, trail.z, 3, 0.4, 0.05, 0.4, 0.02);
    }

    private static void setClientPreview(ServerPlayer player, boolean active) {
        ModNetworking.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SandGraspPreviewPacket(active));
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }

    /** Sunucu kapanirken kalinti kalmasin. */

    /**
     * SOMUT EL — cizimdeki gibi genis avuc + dort kirik parmak.
     *
     * Partikul bulutu kuvveti anlatiyordu ama kutle anlatmiyordu; el artik
     * geometri olarak da ciziliyor. Parmaklar cekme ilerledikce yumruk
     * sikar gibi kapaniyor ve en sonda el yuzeye gomulup kayboluyor.
     */
    static void collectShapes(java.util.List<
            com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.Shape> out) {
        for (Grasp grasp : grasps.values()) {
            // Kimlik oyuncunun kimliginden turetiliyor: kareler arasi
            // eslestirme buna dayaniyor ve ayni el her karede ayni kimligi
            // tasimali, yoksa yumusatma calismaz.
            int baseId = grasp.ownerId.hashCode();

            if (grasp.state == State.PREVIEW) {
                collectArrows(grasp, baseId, out);
                continue;
            }

            float grow;
            float progress;

            if (grasp.ticks <= RISE_TICKS) {
                // CIKIS asamasi: el yerinde durur ve yukselir
                grow = grasp.ticks / (float) RISE_TICKS;
                progress = 0f;
            } else {
                grow = 1f;
                progress = Math.min(1f,
                        (grasp.ticks - RISE_TICKS) / (float) ERUPT_TICKS);
            }

            float eased = progress * progress * (3f - 2f * progress);
            Vec3 hand = grasp.far.add(grasp.near.subtract(grasp.far).scale(eased));

            Vec3 base = SandSpikeController.groundUnder(grasp.level, hand.add(0, 1.5, 0));
            if (base == null) base = hand;
            // Ground lookup snaps to block centres. Only its height belongs to
            // the moving hand; quantizing X/Z made it hop one block at a time.
            else base = new Vec3(hand.x, base.y, hand.z);

            // El SANDMAN'A BAKAR: parmaklar cekis yonunde kapanmali,
            // ters baksaydi dusmani iterek kapaniyormus gibi gorunurdu
            Vec3 dir = grasp.near.subtract(grasp.far);
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));

            float sink = progress > 0.78f ? (progress - 0.78f) / 0.22f : 0f;

            out.add(new com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.Shape(
                    baseId,
                    com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.TYPE_HAND,
                    base.x, base.y, base.z, yaw, 0f, grow, progress, sink));
        }
    }

    /**
     * Onizleme oklari.
     *
     * Partikul yerine sekil gonderiliyor: partikul gostergesi seyrek
     * kaliyor, oyuncu donunce dagiliyor ve kum zemininde kayboluyordu.
     * Istemci bunlari duz kirmizi serit olarak ciziyor.
     */
    private static void collectArrows(Grasp grasp, int baseId, java.util.List<
            com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.Shape> out) {
        double halfWidth = grasp.cfg.getDouble("width", 3.0) * 0.5;
        double length = grasp.near.distanceTo(grasp.far);
        int chevrons = Math.max(3, (int) (length / 2.2));

        // Oklarin ucu Sandman'a bakar
        Vec3 dir = grasp.near.subtract(grasp.far);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));

        for (int c = 0; c < chevrons; c++) {
            double t = (c + 0.5) / chevrons;
            Vec3 tip = grasp.far.add(grasp.near.subtract(grasp.far).scale(t));

            Vec3 ground = SandSpikeController.groundUnder(grasp.level, tip.add(0, 1.5, 0));
            if (ground == null) ground = tip;

            // Nabiz sirali: oklar tek tek parliyor ve akis Sandman'a
            // dogru ilerliyormus gibi gorunuyor. Hepsi ayni anda yanip
            // sonseydi gosterge sadece titrerdi.
            float pulse = 0.5f + 0.5f * Mth.sin(grasp.ticks * 0.28f - c * 0.9f);

            out.add(new com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.Shape(
                    baseId + 1000 + c,
                    com.FIRNI.superheromod.network.packet.SandShapeSyncPacket.TYPE_ARROW,
                    ground.x, ground.y, ground.z, yaw, 0f, pulse, (float) halfWidth, 0f));
        }
    }
    public static void clear() {
        grasps.clear();
    }

    public static List<Vec3> activeCenters() {
        List<Vec3> out = new ArrayList<>();
        for (Grasp g : grasps.values()) out.add(g.far);
        return out;
    }
}
