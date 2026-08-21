package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ShockwavePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * SAND COLOSSUS'UN TOPUZU — devin normal saldirisi.
 *
 * Dokumandaki akis: dev kol geri cekilir, topuz yukari kalkar, KISA BIR
 * ANTICIPATION, topuz asagi iner, impact karesinde AOE sok dalgasi.
 *
 * Anticipation duraklamasi bilerek var: topuz kesintisiz inerse darbe hafif
 * kaliyor. Tepede kisa bir bekleme agirlik hissini veren sey.
 *
 * Omuz kristali kirildiysa o kol zayiflar — vurus hem gec gelir hem az
 * hasar verir. Kristal kirmanin somut karsiligi bu.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class ColossusMaceController {

    // Faz sinirlari (tick)
    private static final int RAISE_END = 10;        // topuz yukari kalkar
    private static final int ANTICIPATION_END = 16; // tepede bekleme
    private static final int IMPACT_TICK = 21;      // topuz yere iner
    private static final int TOTAL_TICKS = 32;

    private static final float BASE_DAMAGE = 11.0f;
    /** Devin topuzu — alan bilerek COK genis, ultinin agirligi buradan geliyor. */
    private static final double SHOCKWAVE_RADIUS = 16.0;
    /** Zayiflamis kolda hasar ve menzil bu oranla carpilir. */
    private static final float WEAK_ARM_FACTOR = 0.55f;

    /**
     * Topuzun ONE UZANDIGI mesafe.
     *
     * Dev 10 blok boyunda ve kollari uzun; vurus noktasi ayak dibinde
     * kalirsa topuzun uzunlugu hic hissedilmiyordu.
     */
    private static final double MACE_REACH = 9.0;

    /** Havaya kalkacak azami blok — sunucu bogulmasin. */
    private static final int MAX_LIFTED_BLOCKS = 90;
    /** Bloklarin sokulecegi yaricap (carpma noktasi cevresinde). */
    private static final double BLOCK_LIFT_RADIUS = 6.5;

    private static final class Swing {
        final UUID player;
        final boolean rightArm;
        int ticks = 0;
        boolean impacted = false;

        Swing(UUID player, boolean rightArm) {
            this.player = player;
            this.rightArm = rightArm;
        }
    }

    private static final Map<UUID, Swing> swings = new HashMap<>();

    private ColossusMaceController() {}

    public static boolean isSwinging(UUID playerId) {
        return swings.containsKey(playerId);
    }

    /** Colossus formunda sol tik buraya gelir. */
    public static void swing(ServerPlayer player) {
        if (swings.containsKey(player.getUUID())) return;

        // Sag kol kirildiysa sol kolla vurur — dev iki kollu
        boolean rightArm = !SandColossusController.isArmWeakened(player.getUUID(), true);

        swings.put(player.getUUID(), new Swing(player.getUUID(), rightArm));

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.4f, 0.42f);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (swings.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, Swing>> it = swings.entrySet().iterator();
        while (it.hasNext()) {
            Swing swing = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(swing.player);

            // Form bittiyse savurma da biter
            if (player == null || !SandColossusController.isColossus(swing.player)) {
                it.remove();
                continue;
            }

            swing.ticks++;
            tickSwing(player, swing);

            if (swing.ticks >= TOTAL_TICKS) it.remove();
        }
    }

    private static void tickSwing(ServerPlayer player, Swing swing) {
        if (!(player.level() instanceof ServerLevel level)) return;

        if (swing.ticks <= RAISE_END) {
            // Topuz yukari kalkiyor — cevresinde kum toplaniyor
            if (swing.ticks % 2 == 0) {
                Vec3 mace = macePosition(player, swing, 1.0);
                level.sendParticles(sand(), mace.x, mace.y, mace.z,
                        6, 0.6, 0.6, 0.6, 0.04);
            }
            return;
        }

        if (swing.ticks <= ANTICIPATION_END) {
            // Tepede bekleme — agirlik hissi buradan geliyor
            if (swing.ticks == RAISE_END + 1) {
                level.playSound(null, player.blockPosition(),
                        SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.2f, 0.35f);
            }
            return;
        }

        if (swing.ticks < IMPACT_TICK) {
            // Topuz iniyor — hizli, arkasinda kum izi
            Vec3 mace = macePosition(player, swing, 0.35);
            level.sendParticles(sand(), mace.x, mace.y, mace.z,
                    8, 0.5, 0.7, 0.5, 0.10);
            return;
        }

        if (!swing.impacted) {
            swing.impacted = true;
            impact(level, player, swing);
        }
    }

    /** Topuzun o anki kaba konumu — sadece efekt icin. */
    private static Vec3 macePosition(ServerPlayer player, Swing swing, double heightFactor) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();

        // Kol yonune gore yana kaydir
        Vec3 side = new Vec3(-flat.z, 0, flat.x).scale(swing.rightArm ? -2.6 : 2.6);

        return player.position()
                .add(side)
                .add(flat.scale(2.2))
                .add(0, ColossusCrystal.COLOSSUS_HEIGHT * heightFactor, 0);
    }

    /** Topuz yere carpar — AOE sok dalgasi. */
    private static void impact(ServerLevel level, ServerPlayer player, Swing swing) {
        boolean weak = SandColossusController.isArmWeakened(player.getUUID(), swing.rightArm);

        float damage = weak ? BASE_DAMAGE * WEAK_ARM_FACTOR : BASE_DAMAGE;
        double radius = weak ? SHOCKWAVE_RADIUS * WEAK_ARM_FACTOR : SHOCKWAVE_RADIUS;

        // Vurus noktasi: devin ONUNDE, kol boyu kadar uzakta
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();

        double reach = weak ? MACE_REACH * 0.7 : MACE_REACH;
        Vec3 center = groundUnder(level, player.position().add(flat.scale(reach)));

        level.playSound(null, BlockPos.containing(center),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.4f, 0.28f);
        level.playSound(null, BlockPos.containing(center),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 2.2f, 0.32f);
        level.playSound(null, BlockPos.containing(center),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.6f, 0.55f);

        SandColossusController.groundSlam(level, player, center, radius, damage);

        // Cizilen sok dalgasi: merkezden disa buyuyen beyaz halkalar + sarsinti
        ModNetworking.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        center.x, center.y, center.z, radius * 3.0, level.dimension())),
                new ShockwavePacket(center, (float) radius, 26, 0.85f));

        liftBlocks(level, player, center, weak);

        // Carpma noktasindan yukari firlayan kum
        level.sendParticles(sand(),
                center.x, center.y + 0.3, center.z,
                110, 2.0, 0.8, 2.0, 0.30);
        level.sendParticles(ParticleTypes.EXPLOSION,
                center.x, center.y + 0.5, center.z, 5, 1.8, 0.4, 1.8, 0);
    }

    /**
     * Carpma noktasindaki bloklar Y EKSENINDE havaya kalkip dagilir.
     *
     * Hiz agirlikli olarak YUKARI veriliyor, disa dogru degil: yatay firlatma
     * "patlama" gibi duruyordu, oysa topuz yere DIKEY iniyor. Bloklarin once
     * yukari firlayip sonra dagilmasi darbenin yonunu dogru anlatiyor.
     */
    private static void liftBlocks(ServerLevel level, ServerPlayer player,
                                   Vec3 center, boolean weak) {
        double radius = weak ? BLOCK_LIFT_RADIUS * WEAK_ARM_FACTOR : BLOCK_LIFT_RADIUS;
        int max = weak ? MAX_LIFTED_BLOCKS / 2 : MAX_LIFTED_BLOCKS;

        BlockPos origin = BlockPos.containing(center);
        int r = (int) Math.ceil(radius);
        int lifted = 0;

        for (int dx = -r; dx <= r && lifted < max; dx++) {
            for (int dz = -r; dz <= r && lifted < max; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq > radius * radius) continue;

                // Her sutunda sadece EN USTTEKI blok kalkar; komple kazmak
                // krater aciyordu, burada istenen sey savrulan enkaz
                BlockPos surface = topSolid(level, origin.offset(dx, 2, dz));
                if (surface == null) continue;

                BlockState state = level.getBlockState(surface);
                if (state.isAir() || state.getDestroySpeed(level, surface) < 0) continue;

                level.setBlock(surface, Blocks.AIR.defaultBlockState(), 3);

                FallingBlockEntity fb = FallingBlockEntity.fall(level, surface, state);
                double dist = Math.sqrt(distSq);
                double falloff = Math.max(0.25, 1.0 - dist / radius);

                // Agirlikli olarak YUKARI, hafifce disa
                double outX = dist < 0.1 ? 0 : dx / dist;
                double outZ = dist < 0.1 ? 0 : dz / dist;

                fb.setDeltaMovement(
                        outX * 0.18 * falloff + (level.random.nextDouble() - 0.5) * 0.10,
                        0.85 * falloff + level.random.nextDouble() * 0.45,
                        outZ * 0.18 * falloff + (level.random.nextDouble() - 0.5) * 0.10);
                fb.setHurtsEntities(1.5f, 5);
                fb.time = 1;
                lifted++;
            }
        }
    }

    /**
     * Vurus noktasini ZEMINE oturtur.
     *
     * Uzaga uzanan topuz egimli arazide havada patliyordu; carpma noktasinin
     * zemini bulmasi gerekiyor.
     */
    private static Vec3 groundUnder(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point.add(0, 2, 0));
        for (int i = 0; i < 10; i++) {
            if (!level.getBlockState(pos.below()).isAir()) {
                return new Vec3(point.x, pos.getY(), point.z);
            }
            pos = pos.below();
        }
        return point;
    }

    /** Verilen noktadan asagi inip ilk kati blogu bulur. */
    private static BlockPos topSolid(ServerLevel level, BlockPos start) {
        BlockPos pos = start;
        for (int i = 0; i < 6; i++) {
            if (!level.getBlockState(pos).isAir()) return pos;
            pos = pos.below();
        }
        return null;
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }
}
