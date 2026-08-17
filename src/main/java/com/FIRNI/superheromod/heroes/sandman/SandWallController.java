package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.SandWallSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * SAND WALL — hayali onizleme, onay, ve dikenli firlatma.
 *
 * Akis:
 *   1. SHIFT        -> onunde HAYALI duvar belirir (henuz somut degil)
 *   2. Sol tik      -> duvar gerceklesir  |  Sag tik -> iptal
 *   3. SHIFT tekrar -> DIS YUZEYDE dikenler cikar, kisa bir bekleme, sonra
 *                      duvar o yone hizla ucar; yolundakilere hasar + savurma
 *
 * Dikenler firlatma tusuna basilmadan CIKMAZ; duran duvar dikensizdir.
 *
 * Duvar gercek entity degil, sunucu tarafinda tutulan kontrollu bir nesne.
 * Bloklara yazmiyoruz (dokumandaki grief/harita bozulma gerekcesi), carpisma
 * elle yapiliyor: duran duvar icine giren varliklari disari itiyor, ucan duvar
 * onune cikanlari savuruyor. Mermiler duvarda soner.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandWallController {

    public enum State { PREVIEW, SOLID, SPIKING, FLYING }

    /** Dikenler ciktiktan sonra firlamadan onceki kisa bekleme. */
    private static final int SPIKE_DELAY = 9;
    private static final int FLY_TICKS = 26;
    private static final double FLY_SPEED = 1.15;
    /** Tek tickte kaldirilacak azami blok — yapiyi yararken sunucu bogulmasin. */
    private static final int MAX_PLOW_PER_TICK = 70;

    private static final BlockParticleOption SAND_BLOCK =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

    private static final class Wall {
        final UUID owner;
        final ServerLevel level;

        Vec3 center;
        /** Dis yuzeyin baktigi yon — firlatma bu yone olur. */
        Vec3 facing;
        float yaw;

        final float halfWidth;
        final float halfHeight;
        final float halfDepth;

        State state = State.PREVIEW;
        int stateTicks = 0;
        int lifetime;

        float health;
        final float maxHealth;
        final float flyDamage;

        /** Ucan duvarin ayni hedefi tekrar tekrar savurmasini engeller. */
        final Set<UUID> struck = new HashSet<>();

        Wall(UUID owner, ServerLevel level, AbilityConfig cfg) {
            this.owner = owner;
            this.level = level;
            this.halfWidth = cfg.getFloat("width", 5.0f) * 0.5f;
            this.halfHeight = cfg.getFloat("height", 3.4f) * 0.5f;
            this.halfDepth = cfg.getFloat("depth", 0.8f) * 0.5f;
            this.maxHealth = cfg.getFloat("wallHealth", 60.0f);
            this.health = this.maxHealth;
            this.lifetime = cfg.getInt("lifetimeTicks", 240);
            this.flyDamage = cfg.getFloat("flyDamage", 7.0f);
        }

        float flyDamage() { return flyDamage; }

        /** Dikenlerin cikma orani 0..1 — sadece SPIKING/FLYING'de artar. */
        float spikeAmount() {
            if (state == State.SPIKING) return Math.min(1f, stateTicks / (float) SPIKE_DELAY);
            return state == State.FLYING ? 1f : 0f;
        }

        AABB box() {
            // Yonden bagimsiz kaba kutu: en genis olcuyu her eksende kullanmak
            // yerine yatayda genislik, dikeyde yukseklik yeterli
            double h = Math.max(halfWidth, halfDepth);
            return new AABB(
                    center.x - h, center.y - halfHeight, center.z - h,
                    center.x + h, center.y + halfHeight, center.z + h);
        }
    }

    private static final Map<UUID, Wall> walls = new HashMap<>();

    private SandWallController() {}

    // ------------------------------------------------------------------
    // Giris noktalari
    // ------------------------------------------------------------------

    /** SHIFT: onizleme yoksa ac, duvar duruyorsa firlat. */
    public static void press(ServerPlayer player, AbilityConfig cfg) {
        Wall wall = walls.get(player.getUUID());

        if (wall == null) {
            beginPreview(player, cfg);
        } else if (wall.state == State.SOLID) {
            launch(player);
        }
    }

    private static void beginPreview(ServerPlayer player, AbilityConfig cfg) {
        Wall wall = new Wall(player.getUUID(), (ServerLevel) player.level(), cfg);
        placeInFront(player, wall, cfg.getDouble("distance", 3.2));
        walls.put(player.getUUID(), wall);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SAND_STEP, SoundSource.PLAYERS, 0.9f, 1.4f);
    }

    public static void confirm(ServerPlayer player) {
        Wall wall = walls.get(player.getUUID());
        if (wall == null || wall.state != State.PREVIEW) return;

        wall.state = State.SOLID;
        wall.stateTicks = 0;

        wall.level.playSound(null, BlockPos.containing(wall.center),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.5f, 0.55f);
        burst(wall, 30, 0.2);
    }

    public static void cancel(ServerPlayer player) {
        Wall wall = walls.get(player.getUUID());
        if (wall == null || wall.state != State.PREVIEW) return;

        walls.remove(player.getUUID());
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    /** Dikenleri cikarir; kisa beklemeden sonra duvar firlar. */
    public static void launch(ServerPlayer player) {
        Wall wall = walls.get(player.getUUID());
        if (wall == null || wall.state != State.SOLID) return;

        wall.state = State.SPIKING;
        wall.stateTicks = 0;
        wall.struck.clear();

        wall.level.playSound(null, BlockPos.containing(wall.center),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.6f, 0.75f);
    }

    public static boolean hasPreview(UUID playerId) {
        Wall wall = walls.get(playerId);
        return wall != null && wall.state == State.PREVIEW;
    }

    public static boolean hasWall(UUID playerId) {
        return walls.containsKey(playerId);
    }

    /** Sand Travel bu duvarlari capa olarak kullanabilir. */
    public static List<Vec3> anchorPoints(ServerLevel level) {
        List<Vec3> out = new ArrayList<>();
        for (Wall wall : walls.values()) {
            if (wall.level == level && wall.state == State.SOLID) out.add(wall.center);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (walls.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, Wall>> it = walls.entrySet().iterator();
        while (it.hasNext()) {
            Wall wall = it.next().getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(wall.owner);

            if (owner == null) {
                it.remove();
                continue;
            }

            wall.stateTicks++;
            wall.lifetime--;

            boolean done = switch (wall.state) {
                case PREVIEW -> {
                    // Onizleme oyuncuyu takip eder — nereye bakarsan oraya kurulur
                    placeInFront(owner, wall, 3.2);
                    yield false;
                }
                case SOLID -> {
                    blockEntities(wall, owner);
                    stopProjectiles(wall);
                    if (wall.stateTicks % 10 == 0) trickle(wall);
                    yield wall.lifetime <= 0 || wall.health <= 0;
                }
                case SPIKING -> {
                    blockEntities(wall, owner);
                    spikeGrowFx(wall);
                    if (wall.stateTicks >= SPIKE_DELAY) {
                        wall.state = State.FLYING;
                        wall.stateTicks = 0;
                        wall.level.playSound(null, BlockPos.containing(wall.center),
                                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.1f, 1.4f);
                    }
                    yield false;
                }
                case FLYING -> tickFlying(wall, owner);
            };

            if (done) {
                crumble(wall);
                it.remove();
            }
        }

        sync(server);
    }

    /** Onizleme/duvar oyuncunun onune, bakisina dik olarak yerlesir. */
    private static void placeInFront(ServerPlayer player, Wall wall, double distance) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();

        wall.facing = flat;
        wall.yaw = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));

        Vec3 base = player.position().add(flat.scale(distance));
        wall.center = new Vec3(base.x, base.y + wall.halfHeight, base.z);
    }

    private static boolean tickFlying(Wall wall, ServerPlayer owner) {
        wall.center = wall.center.add(wall.facing.scale(FLY_SPEED));

        AABB box = wall.box();
        List<Entity> caught = wall.level.getEntities((Entity) null, box,
                e -> e != owner && e.isAlive());

        for (Entity entity : caught) {
            if (entity instanceof Projectile projectile) {
                projectile.discard();
                continue;
            }
            if (!(entity instanceof LivingEntity target)) continue;
            if (!wall.struck.add(target.getUUID())) continue;

            target.hurt(owner.damageSources().playerAttack(owner), wall.flyDamage());
            target.setDeltaMovement(
                    wall.facing.x * 1.5,
                    0.55,
                    wall.facing.z * 1.5);
            target.hurtMarked = true;

            Vec3 p = target.position().add(0, target.getBbHeight() * 0.5, 0);
            wall.level.sendParticles(SAND_BLOCK, p.x, p.y, p.z, 16, 0.4, 0.4, 0.4, 0.18);
        }

        // Onune cikan yapiyi durmadan yarip gecer
        plowBlocks(wall);

        if (wall.stateTicks % 2 == 0) burst(wall, 6, 0.5);

        // Bloga carpinca durmuyor: mesafesini doldurana kadar gidiyor
        return wall.stateTicks >= FLY_TICKS;
    }

    /**
     * Ucan duvar onune cikan her seyi dumduz eder.
     *
     * Bloklar destroyBlock ile degil setBlock ile kaldiriliyor: destroyBlock
     * her blok icin ayri kirilma sesi ve partikuli yolluyor, yuzlerce blokta
     * bu hem gurultu hem yuk oluyordu. Kirilma hissini duvarin kendi kum
     * patlamasi veriyor. Kirilmaz bloklar (bedrock vb.) korunuyor.
     */
    private static void plowBlocks(Wall wall) {
        AABB box = wall.box();

        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        int removed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX && removed < MAX_PLOW_PER_TICK; x++) {
            for (int y = minY; y <= maxY && removed < MAX_PLOW_PER_TICK; y++) {
                for (int z = minZ; z <= maxZ && removed < MAX_PLOW_PER_TICK; z++) {
                    pos.set(x, y, z);
                    var state = wall.level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(wall.level, pos) < 0) continue;

                    wall.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    removed++;
                }
            }
        }

        if (removed > 0) {
            wall.level.sendParticles(SAND_BLOCK,
                    wall.center.x, wall.center.y, wall.center.z,
                    Math.min(24, removed * 2),
                    wall.halfWidth * 0.9, wall.halfHeight * 0.9, wall.halfDepth, 0.25);

            wall.level.playSound(null, BlockPos.containing(wall.center),
                    SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1.4f, 0.6f);
        }
    }

    /** Duran duvar: icine giren varliklari disari iter. */
    private static void blockEntities(Wall wall, ServerPlayer owner) {
        List<LivingEntity> inside = wall.level.getEntitiesOfClass(
                LivingEntity.class, wall.box(), e -> e != owner && e.isAlive());

        for (LivingEntity entity : inside) {
            Vec3 out = entity.position().subtract(wall.center);
            Vec3 flat = new Vec3(out.x, 0, out.z);
            if (flat.lengthSqr() < 1.0E-4) flat = wall.facing;
            flat = flat.normalize();

            entity.setDeltaMovement(
                    flat.x * 0.35,
                    entity.getDeltaMovement().y,
                    flat.z * 0.35);
            entity.hurtMarked = true;
        }
    }

    /** Mermiler duvarda soner — dokumandaki projectile blocking. */
    private static void stopProjectiles(Wall wall) {
        for (Entity entity : wall.level.getEntities((Entity) null, wall.box(),
                e -> e instanceof Projectile)) {
            entity.discard();
            wall.health -= 2.0f;
            Vec3 p = entity.position();
            wall.level.sendParticles(SAND_BLOCK, p.x, p.y, p.z, 6, 0.15, 0.15, 0.15, 0.05);
        }
    }

    private static void trickle(Wall wall) {
        wall.level.sendParticles(SAND_BLOCK,
                wall.center.x, wall.center.y + wall.halfHeight, wall.center.z,
                3, wall.halfWidth * 0.7, 0.1, wall.halfDepth, 0.02);
    }

    private static void spikeGrowFx(Wall wall) {
        Vec3 face = wall.center.add(wall.facing.scale(wall.halfDepth + 0.3));
        wall.level.sendParticles(SAND_BLOCK,
                face.x, face.y, face.z,
                8, wall.halfWidth * 0.8, wall.halfHeight * 0.7, 0.1, 0.04);
    }

    private static void burst(Wall wall, int count, double speed) {
        wall.level.sendParticles(SAND_BLOCK,
                wall.center.x, wall.center.y, wall.center.z,
                count, wall.halfWidth * 0.8, wall.halfHeight * 0.8, wall.halfDepth, speed);
    }

    private static void crumble(Wall wall) {
        wall.level.sendParticles(SAND_BLOCK,
                wall.center.x, wall.center.y, wall.center.z,
                45, wall.halfWidth * 0.9, wall.halfHeight * 0.9, wall.halfDepth, 0.22);
        wall.level.playSound(null, BlockPos.containing(wall.center),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    /** Tum aktif duvarlari yakindaki oyunculara gonderir. */
    private static void sync(net.minecraft.server.MinecraftServer server) {
        if (walls.isEmpty()) return;

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            List<SandWallSyncPacket.Entry> visible = new ArrayList<>();

            for (Wall wall : walls.values()) {
                if (wall.level != viewer.level()) continue;
                // Onizleme SADECE sahibine gorunur — hayali duvar bir plan
                if (wall.state == State.PREVIEW && !wall.owner.equals(viewer.getUUID())) continue;
                if (wall.center.distanceToSqr(viewer.position()) > 96 * 96) continue;

                visible.add(new SandWallSyncPacket.Entry(
                        wall.center, wall.yaw,
                        wall.halfWidth, wall.halfHeight, wall.halfDepth,
                        wall.spikeAmount(),
                        Math.max(0f, wall.health / wall.maxHealth),
                        wall.state == State.PREVIEW));
            }

            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new SandWallSyncPacket(visible));
        }
    }
}
