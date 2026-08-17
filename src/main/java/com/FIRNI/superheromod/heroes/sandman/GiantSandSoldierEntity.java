package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.entity.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * GIANT SAND SOLDIER — normal kum askerinin agir versiyonu.
 *
 * Ayni sinifi genisletiyor: yol bulma, hedefleme, 8 asamali olusma ve dagilma
 * mantigi aynen miras aliniyor. Degisen sadece olcek, dayaniklilik, hiz ve
 * olustugu anda yere caktigi sok dalgasi.
 *
 * Ayri EntityType olarak kaydediliyor cunku carpisma kutusu tur uzerinden
 * tanimlanir; ayni turde "buyuk" bayragi tutmak hitbox'i buyutmezdi.
 */
public class GiantSandSoldierEntity extends SandSoldierEntity {

    private static final double SHOCKWAVE_RADIUS = 4.5;
    private static final float SHOCKWAVE_DAMAGE = 6.0f;

    public GiantSandSoldierEntity(EntityType<? extends GiantSandSoldierEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createGiantAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.MOVEMENT_SPEED, 0.20)      // agir, yavas
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.6)     // savurarak vurur
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    /** Olusmayi tamamlayinca yere agir sekilde iner ve sok dalgasi cikarir. */
    @Override
    protected void onFormed() {
        if (!(level() instanceof ServerLevel server)) return;

        server.playSound(null, blockPosition(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4f, 0.5f);
        server.playSound(null, blockPosition(),
                SoundEvents.SAND_PLACE, SoundSource.HOSTILE, 1.6f, 0.4f);

        BlockParticleOption sand =
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

        // Yerde disa kosan halka
        for (int i = 0; i < 28; i++) {
            double a = i / 28.0 * Math.PI * 2;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            server.sendParticles(sand,
                    getX() + dx * 2.2, getY() + 0.2, getZ() + dz * 2.2,
                    2, dx * 0.3, 0.05, dz * 0.3, 0.14);
        }

        AABB area = new AABB(position(), position()).inflate(SHOCKWAVE_RADIUS);
        List<LivingEntity> caught = server.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive() && isShockTarget(e));

        for (LivingEntity target : caught) {
            target.hurt(damageSources().mobAttack(this), SHOCKWAVE_DAMAGE);

            Vec3 push = target.position().subtract(position());
            Vec3 flat = new Vec3(push.x, 0, push.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();

            target.setDeltaMovement(flat.x * 0.9, 0.6, flat.z * 0.9);
            target.hurtMarked = true;
        }
    }

    /** Sok dalgasi sahibi ve kardes askerleri vurmaz. */
    private boolean isShockTarget(LivingEntity candidate) {
        if (getOwnerId() != null && candidate.getUUID().equals(getOwnerId())) return false;
        if (candidate instanceof SandSoldierEntity other) {
            return getOwnerId() == null || !getOwnerId().equals(other.getOwnerId());
        }
        return true;
    }

    public static GiantSandSoldierEntity createGiant(ServerLevel level, Player owner,
                                                     double x, double y, double z) {
        GiantSandSoldierEntity soldier = ModEntities.GIANT_SAND_SOLDIER.get().create(level);
        if (soldier == null) return null;

        soldier.moveTo(x, y, z, owner.getYRot(), 0f);
        soldier.setOwner(owner.getUUID());
        return soldier;
    }
}
