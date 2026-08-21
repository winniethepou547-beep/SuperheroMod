package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ColossusActionPacket;
import com.FIRNI.superheromod.network.packet.ShockwavePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * COLOSSUS'UN KAYA FIRLATMASI — sag tik.
 *
 * Dev, topuz TUTMAYAN eliyle kendi govdesinden bir kaya kutlesi kopariyor ve
 * savurarak firlatiyor. Kaya carptigi yerde parcalanip alan hasari veriyor;
 * yakalananlar sersemliyor.
 *
 * SERSEMLETME uc parcadan olusuyor ve ucu de ayni anda gerekiyor:
 *   yavaslama    -> kacamiyor
 *   saldiri hizi -> karsilik veremiyor
 *   kamera sarsintisi -> nisan alamiyor
 * Sadece yavaslama verilse oyuncu yerinde durup rahatca vurmaya devam
 * ederdi; sarsinti olmadan da "sersemledim" hissi olusmuyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class ColossusRockController {

    /** Kolun geriye cekilip savrulmasi — firlatma bu kareden once yok. */
    private static final int WINDUP_TICKS = 9;

    private static final double ROCK_SPEED = 1.35;
    private static final double MAX_TRAVEL = 48.0;
    private static final float ROCK_RADIUS = 0.9f;

    private static final float IMPACT_DAMAGE = 9.0f;
    private static final double BLAST_RADIUS = 6.5;

    /** Sersemleme suresi (tick). */
    private static final int STUN_TICKS = 70;

    /** Zayiflamis kolda hasar ve alan bu oranla carpilir. */
    private static final float WEAK_ARM_FACTOR = 0.6f;

    /** Kolun geri cekilme asamasi. */
    private static final class Windup {
        final UUID player;
        int ticks = 0;
        Windup(UUID player) { this.player = player; }
    }

    /** Havada ilerleyen kaya. */
    private static final class Rock {
        final UUID owner;
        Vec3 pos;
        final Vec3 velocity;
        final boolean weak;
        double travelled = 0;

        Rock(UUID owner, Vec3 pos, Vec3 velocity, boolean weak) {
            this.owner = owner;
            this.pos = pos;
            this.velocity = velocity;
            this.weak = weak;
        }
    }

    private static final List<Windup> windups = new ArrayList<>();
    private static final List<Rock> rocks = new ArrayList<>();

    private ColossusRockController() {}

    public static boolean isThrowing(UUID playerId) {
        for (Windup w : windups) {
            if (w.player.equals(playerId)) return true;
        }
        return false;
    }

    /** Colossus formunda sag tik buraya gelir. */
    public static void throwRock(ServerPlayer player) {
        if (isThrowing(player.getUUID())) return;

        windups.add(new Windup(player.getUUID()));

        // Istemci animasyonu: kol geri cekilip savrulur
        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new ColossusActionPacket(player.getUUID(),
                        ColossusActionPacket.ROCK_THROW, WINDUP_TICKS + 12));

        // Govdeden kaya kopuyor
        if (player.level() instanceof ServerLevel level) {
            Vec3 hand = handPosition(player);
            level.sendParticles(sand(), hand.x, hand.y, hand.z,
                    40, 0.8, 0.8, 0.8, 0.08);
            level.playSound(null, player.blockPosition(),
                    SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.6f, 0.35f);
        }
    }

    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (windups.isEmpty() && rocks.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        tickWindups(server);
        tickRocks(server);
    }

    private static void tickWindups(net.minecraft.server.MinecraftServer server) {
        Iterator<Windup> it = windups.iterator();
        while (it.hasNext()) {
            Windup windup = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(windup.player);

            if (player == null || !SandColossusController.isColossus(windup.player)) {
                it.remove();
                continue;
            }

            windup.ticks++;

            if (player.level() instanceof ServerLevel level) {
                // Kol geriye cekilirken elde kaya buyur
                Vec3 hand = handPosition(player);
                level.sendParticles(sand(), hand.x, hand.y, hand.z,
                        5, 0.4, 0.4, 0.4, 0.03);
            }

            if (windup.ticks >= WINDUP_TICKS) {
                release(player);
                it.remove();
            }
        }
    }

    /** Kaya savrularak firlatilir. */
    private static void release(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        // Topuz TUTMAYAN el: sag kol kirilmadiysa topuz sagda, kaya solda
        boolean maceInRight = !SandColossusController.isArmWeakened(player.getUUID(), true);
        boolean weak = SandColossusController.isArmWeakened(player.getUUID(), !maceInRight);

        Vec3 origin = handPosition(player);
        Vec3 dir = RaycastSystem.getLookDirection(player);

        rocks.add(new Rock(player.getUUID(), origin, dir.scale(ROCK_SPEED), weak));

        level.playSound(null, player.blockPosition(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.1f);
        level.sendParticles(sand(), origin.x, origin.y, origin.z,
                25, 0.5, 0.5, 0.5, 0.15);
    }

    private static void tickRocks(net.minecraft.server.MinecraftServer server) {
        Iterator<Rock> it = rocks.iterator();
        while (it.hasNext()) {
            Rock rock = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(rock.owner);

            if (owner == null) {
                it.remove();
                continue;
            }

            ServerLevel level = (ServerLevel) owner.level();
            Vec3 from = rock.pos;
            Vec3 dir = rock.velocity.normalize();
            double step = rock.velocity.length();

            RaycastResult result = RaycastSystem.cast(
                    level, owner, from, dir, step, ROCK_RADIUS, false,
                    e -> e instanceof LivingEntity && e != owner);

            Vec3 to = result.getHitPosition();

            // KAYA TICK BASINA DEGIL, ARA NOKTALARLA ciziliyor.
            //
            // Onceden partikuller sadece VARIS noktasina birakiliyordu;
            // kaya saniyede 20 kez isinlanan bir kume gibi gorunuyor ve
            // hareket "kasiyor" izlenimi veriyordu. Simdi tick icindeki
            // yol boyunca dagitiliyor, yani kaya araligi gercekten
            // katediyormus gibi okunuyor.
            int steps = Math.max(2, (int) (from.distanceTo(to) * 2.5));
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;
                Vec3 p = from.add(to.subtract(from).scale(t));

                level.sendParticles(rockChunk(), p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.0);
                if (i % 2 == 0) {
                    level.sendParticles(sand(), p.x, p.y, p.z, 2, 0.35, 0.35, 0.35, 0.02);
                }
            }

            boolean hit = result.didHitEntity() || result.didHitBlock();

            rock.travelled += from.distanceTo(to);
            rock.pos = to;

            if (hit || rock.travelled >= MAX_TRAVEL) {
                shatter(level, owner, to, rock.weak);
                it.remove();
            }
        }
    }

    /** Kaya parcalanir: alan hasari + sersemletme. */
    private static void shatter(ServerLevel level, ServerPlayer owner,
                                Vec3 center, boolean weak) {
        float damage = weak ? IMPACT_DAMAGE * WEAK_ARM_FACTOR : IMPACT_DAMAGE;
        double radius = weak ? BLAST_RADIUS * WEAK_ARM_FACTOR : BLAST_RADIUS;

        level.playSound(null, BlockPos.containing(center),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.8f, 0.55f);
        level.playSound(null, BlockPos.containing(center),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.8f, 0.45f);

        // Parcalanan kaya
        level.sendParticles(sand(), center.x, center.y, center.z,
                80, 1.4, 1.4, 1.4, 0.30);
        level.sendParticles(ParticleTypes.EXPLOSION,
                center.x, center.y, center.z, 3, 0.8, 0.8, 0.8, 0);

        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != owner && e.isAlive())) {

            double dist = target.position().distanceTo(center);
            double falloff = Math.max(0.3, 1.0 - dist / radius);

            target.hurt(owner.damageSources().playerAttack(owner),
                    (float) (damage * falloff));

            applyStun(target);

            Vec3 push = target.position().subtract(center);
            Vec3 flat = new Vec3(push.x, 0, push.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
            target.setDeltaMovement(flat.x * 0.5, 0.35, flat.z * 0.5);
            target.hurtMarked = true;
        }

        // Halka + kamera sarsintisi — sersemlemenin ucuncu parcasi
        ModNetworking.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        center.x, center.y, center.z, radius * 4.0, level.dimension())),
                new ShockwavePacket(center, (float) radius, 20, 0.6f));
    }

    /**
     * SERSEMLETME.
     *
     * Yavaslama kacmayi, kazma yorgunlugu ise SALDIRI HIZINI dusuruyor —
     * vanilla'da Mining Fatigue seviye basina saldiri hizini da azaltiyor.
     * Korluk gibi agir bir etki bilerek kullanilmadi; gormeyi engellemek
     * sersemletmeden ote, oyuncuyu tamamen devre disi birakiyordu.
     */
    private static void applyStun(LivingEntity target) {
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 3, false, true));
        target.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, STUN_TICKS, 2, false, true));
        target.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION, STUN_TICKS, 0, false, false));
    }

    /** Kayanin cikacagi el — devin omuz hizasinda ve yaninda. */
    private static Vec3 handPosition(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();

        // Topuz sagdaysa kaya SOL elde
        boolean maceInRight = !SandColossusController.isArmWeakened(player.getUUID(), true);
        double side = maceInRight ? 2.9 : -2.9;

        Vec3 sideVec = new Vec3(-flat.z, 0, flat.x).scale(side);

        return player.position()
                .add(sideVec)
                .add(flat.scale(1.5))
                .add(0, ColossusCrystal.COLOSSUS_HEIGHT * 0.62, 0);
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }

    /** Kayalasmis kum — govdeden kopan parca bundan. */
    private static BlockParticleOption rockChunk() {
        return new BlockParticleOption(ParticleTypes.BLOCK,
                Blocks.SANDSTONE.defaultBlockState());
    }
}
