package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * SAND BURST — G.
 *
 * Kum zirhinda biriken her seyi tek seferde bosaltir: Sandman'in cevresine
 * halka halinde dikenler firlar. Hem SAYI hem HASAR barin dolulugundan
 * geliyor, yani dovuste ne kadar dayandiysan patlama o kadar buyuk oluyor.
 *
 * Atistan sonra zirh tamamen dokuluyor — bar sifirlaniyor ve Sandman
 * zirhsiz kaliyor. Bu, yeteneginin bedeli: birikimi harcamis oluyorsun.
 */
public class SandBurstAbility extends Ability {

    public SandBurstAbility() {
        super("sandman_sand_burst", AbilityType.INSTANT, AbilitySlot.SKILL_G);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 60);
        // Bar bosken bile birkac diken cikar, dolunca cok daha fazlasi
        config.set("minSpikes", 3);
        config.set("maxSpikes", 14);
        config.set("minDamage", 2.0f);
        config.set("maxDamage", 9.0f);
        config.set("innerRadius", 2.0);
        config.set("outerRadius", 5.5);
        /** Bar bu oranin altindaysa yetenek kullanilamaz. */
        config.set("minFill", 0.1);
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;
        return SandArmorController.fillOf(player.getUUID())
                >= getConfig().getDouble("minFill", 0.1);
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        // Birikimi harca — bu ayni zamanda zirhi dokuyor
        float fill = SandArmorController.consumeAll(player);
        if (fill <= 0f) return;

        AbilityConfig cfg = getConfig();
        ServerLevel level = (ServerLevel) player.level();

        int minSpikes = cfg.getInt("minSpikes", 3);
        int maxSpikes = cfg.getInt("maxSpikes", 14);
        float minDamage = cfg.getFloat("minDamage", 2.0f);
        float maxDamage = cfg.getFloat("maxDamage", 9.0f);
        double inner = cfg.getDouble("innerRadius", 2.0);
        double outer = cfg.getDouble("outerRadius", 5.5);

        int count = minSpikes + Math.round((maxSpikes - minSpikes) * fill);
        float damage = minDamage + (maxDamage - minDamage) * fill;
        double reach = inner + (outer - inner) * fill;

        int placed = 0;
        double startAngle = level.random.nextDouble() * Math.PI * 2;

        for (int i = 0; i < count; i++) {
            // Cevreye esit acilarla dagit; ic ice iki halka olusabilir
            double angle = startAngle + (i / (double) count) * Math.PI * 2;
            double dist = reach * (i % 2 == 0 ? 1.0 : 0.62);

            Vec3 spot = new Vec3(
                    player.getX() + Math.cos(angle) * dist,
                    player.getY(),
                    player.getZ() + Math.sin(angle) * dist);

            Vec3 ground = SandSpikeController.groundUnder(level, spot.add(0, 1.0, 0));
            if (ground == null) continue;

            // Dolu barda dikenler daha uzun cikar
            SandSpikeController.spawnBurst(player, ground, damage, 2.2 + fill * 1.6);
            placed++;
        }

        if (placed == 0) return;

        BlockParticleOption sand =
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

        // Zirhin dokulusu — govdeden disa savrulan kum
        level.sendParticles(sand,
                player.getX(), player.getY() + 1.0, player.getZ(),
                50 + Math.round(60 * fill), 0.6, 0.9, 0.6, 0.2);

        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.6f, 0.5f);
        level.playSound(null, player.blockPosition(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f + fill * 0.6f, 1.3f);
    }
}
