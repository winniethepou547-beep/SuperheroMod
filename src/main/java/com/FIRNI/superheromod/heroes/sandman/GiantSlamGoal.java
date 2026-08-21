package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ShockwavePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.EnumSet;

/**
 * DEV ASKERIN AGIR VURUSU.
 *
 * Akis: iki el havaya kalkar -> tepede birlesir -> aralarinda dikenli bir kum
 * kutlesi olusur -> kutle yere iner -> etrafa kum sacilir + kucuk sok dalgasi.
 *
 * Tepedeki BEKLEME bilerek var; kutle kesintisiz inerse darbe hafif kaliyor.
 * Colossus'un topuzunda da ayni prensip kullanildi — bu onun kucuk olcekli
 * hali.
 */
public class GiantSlamGoal extends Goal {

    // Faz sinirlari (tick)
    private static final int RAISE_END = 14;      // eller yukari
    private static final int GATHER_END = 24;     // kutle olusur, dikenler cikar
    private static final int IMPACT_TICK = 30;    // yere iner
    private static final int TOTAL_TICKS = 44;

    private static final int COOLDOWN_TICKS = 110;
    private static final double TRIGGER_RANGE = 5.5;

    private static final float DAMAGE = 9.0f;
    private static final double BLAST_RADIUS = 7.0;

    private final SandSoldierEntity giant;
    private int ticks;
    private int cooldown;
    private boolean impacted;

    public GiantSlamGoal(SandSoldierEntity giant) {
        this.giant = giant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(giant instanceof GiantSandSoldierEntity)) return false;
        if (giant.isForming()) return false;
        if (cooldown-- > 0) return false;

        LivingEntity target = giant.getTarget();
        return target != null && target.isAlive()
                && giant.distanceToSqr(target) <= TRIGGER_RANGE * TRIGGER_RANGE;
    }

    @Override
    public boolean canContinueToUse() {
        return ticks < TOTAL_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        impacted = false;
        giant.getNavigation().stop();
    }

    @Override
    public void stop() {
        ticks = 0;
        impacted = false;
        cooldown = COOLDOWN_TICKS;
        giant.setSlamProgress(0f);
    }

    @Override
    public void tick() {
        ticks++;
        giant.setSlamProgress(Math.min(1f, ticks / (float) TOTAL_TICKS));

        // Vurus boyunca yerinde durur — yururken vurmak agirlik hissini
        // tamamen bozuyordu
        giant.getNavigation().stop();

        LivingEntity target = giant.getTarget();
        if (target != null) {
            giant.getLookControl().setLookAt(target, 20f, 20f);
        }

        if (!(giant.level() instanceof ServerLevel level)) return;

        if (ticks <= RAISE_END) {
            gatherSand(level);
        } else if (ticks <= GATHER_END) {
            formMass(level);
        } else if (ticks >= IMPACT_TICK && !impacted) {
            impacted = true;
            impact(level);
        }
    }

    /** Eller kalkarken cevreden kum toplanir. */
    private void gatherSand(ServerLevel level) {
        if (ticks % 2 != 0) return;

        Vec3 above = giant.position().add(0, giant.getBbHeight() * 1.15, 0);

        for (int i = 0; i < 4; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double r = 2.0 + level.getRandom().nextDouble() * 2.0;

            level.sendParticles(sand(),
                    above.x + Math.cos(angle) * r,
                    above.y - 0.5 + level.getRandom().nextDouble(),
                    above.z + Math.sin(angle) * r,
                    1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** Kutle olusur ve dikenler cikar. */
    private void formMass(ServerLevel level) {
        Vec3 above = giant.position().add(0, giant.getBbHeight() * 1.15, 0);

        level.sendParticles(sand(), above.x, above.y, above.z,
                8, 0.7, 0.6, 0.7, 0.02);

        if (ticks == GATHER_END) {
            level.playSound(null, giant.blockPosition(),
                    SoundEvents.SAND_PLACE, SoundSource.HOSTILE, 1.6f, 0.5f);
        }
    }

    /** Kutle yere iner: hasar, savurma, kum sacilmasi ve sok dalgasi. */
    private void impact(ServerLevel level) {
        Vec3 look = giant.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();

        Vec3 center = giant.position().add(flat.scale(2.4));

        level.playSound(null, BlockPos.containing(center),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.6f, 0.5f);
        level.playSound(null, BlockPos.containing(center),
                SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.8f, 0.4f);

        // Hasar ve savurma
        AABB area = new AABB(center, center).inflate(BLAST_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != giant && e.isAlive() && giant.isValidTarget(e))) {

            double dist = target.position().distanceTo(center);
            double falloff = Math.max(0.3, 1.0 - dist / BLAST_RADIUS);

            target.hurt(level.damageSources().mobAttack(giant),
                    (float) (DAMAGE * falloff));

            Vec3 push = target.position().subtract(center);
            Vec3 pushFlat = new Vec3(push.x, 0, push.z);
            pushFlat = pushFlat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : pushFlat.normalize();

            target.setDeltaMovement(pushFlat.x * 0.75, 0.52, pushFlat.z * 0.75);
            target.hurtMarked = true;
        }

        // Etrafa sacilan kum — olum animasyonundaki gibi gercek bloklar
        for (int i = 0; i < 8; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double speed = 0.20 + level.getRandom().nextDouble() * 0.26;

            FallingBlockEntity block = FallingBlockEntity.fall(level,
                    BlockPos.containing(center).above(),
                    Blocks.SAND.defaultBlockState());

            block.setDeltaMovement(
                    Math.cos(angle) * speed,
                    0.30 + level.getRandom().nextDouble() * 0.28,
                    Math.sin(angle) * speed);
            block.time = 1;
        }

        level.sendParticles(sand(), center.x, center.y + 0.3, center.z,
                70, 1.3, 0.5, 1.3, 0.24);
        level.sendParticles(ParticleTypes.EXPLOSION,
                center.x, center.y + 0.4, center.z, 2, 0.8, 0.2, 0.8, 0);

        // Colossus'un sok dalgasinin KUCUK hali
        ModNetworking.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        center.x, center.y, center.z, BLAST_RADIUS * 4.0, level.dimension())),
                new ShockwavePacket(center, (float) BLAST_RADIUS, 20, 0.45f));
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }
}
