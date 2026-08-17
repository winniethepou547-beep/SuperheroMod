package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * GIANT SAND SOLDIER — tek ve cok agir bir kum devi cagirir.
 *
 * Dokumandaki cagirma kosulu seceneklerinden "ozel cooldown/ability" secildi:
 * Sand Energy sistemi henuz yok, geldiginde bu yetenek ona baglanabilir.
 * Ayni anda tek dev tutulabiliyor — dev askerin degeri nadirliginden geliyor.
 */
public class GiantSandSoldierAbility extends Ability {

    public GiantSandSoldierAbility() {
        super("sandman_giant_soldier", AbilityType.INSTANT, AbilitySlot.SKILL_X);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 500);      // 25 saniye
        config.set("maxAlive", 1);
        config.set("distance", 3.5);
        config.set("lifetimeTicks", 700);      // 35 saniye
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;
        return countAlive(player) < getConfig().getInt("maxAlive", 1);
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        if (countAlive(player) >= getConfig().getInt("maxAlive", 1)) return;

        // Oyuncunun onunde, yeterli bosluk olan bir noktaya
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();

        Vec3 spot = player.position().add(flat.scale(getConfig().getDouble("distance", 3.5)));
        Vec3 ground = SandSpikeController.groundUnder(level, spot.add(0, 1.5, 0));
        if (ground == null) return;

        GiantSandSoldierEntity giant =
                GiantSandSoldierEntity.createGiant(level, player, ground.x, ground.y, ground.z);
        if (giant == null) return;

        giant.setLifetime(getConfig().getInt("lifetimeTicks", 700));
        level.addFreshEntity(giant);

        // Buyuk kum sutunu yukseliyor
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                ground.x, ground.y + 0.2, ground.z,
                60, 0.9, 0.4, 0.9, 0.14);

        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.8f, 0.35f);
    }

    private int countAlive(ServerPlayer owner) {
        AABB area = new AABB(owner.blockPosition()).inflate(64.0);
        List<GiantSandSoldierEntity> nearby = owner.level().getEntitiesOfClass(
                GiantSandSoldierEntity.class, area,
                e -> e.isAlive() && owner.getUUID().equals(e.getOwnerId()));
        return nearby.size();
    }
}
