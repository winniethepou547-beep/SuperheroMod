package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * SAND SOLDIERS — Sandman elini yere koyar, cevresindeki kumdan savascilar
 * yukselir.
 *
 * Askerler {@link SandSoldierEntity} olarak dogar ve olusma animasyonlarini
 * kendileri isletir; burasi sadece nereye ve kac tane cikacaklarina karar
 * verir.
 */
public class SandSoldiersAbility extends Ability {

    /**
     * Cagirma sirasi: dortlu bir takimda iki yakin dovus, iki menzilli.
     *
     * Sabit sira, rastgele secime tercih edildi — rastgelede bazen dort
     * menzilli veya dort yakin dovusçu cikiyor ve takimin karakteri her
     * seferinde degisiyordu.
     */
    private static final SandSoldierEntity.Variant[] VARIANT_ORDER = {
            SandSoldierEntity.Variant.BLADE,
            SandSoldierEntity.Variant.RANGED,
            SandSoldierEntity.Variant.BREAKER,
            SandSoldierEntity.Variant.RANGED
    };

    public SandSoldiersAbility() {
        super("sandman_sand_soldiers", AbilityType.INSTANT, AbilitySlot.SKILL_C);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 200);
        config.set("count", 4);
        config.set("maxAlive", 6);
        config.set("spawnRadius", 2.6);
        config.set("lifetimeTicks", 500);   // 25 saniye
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        int count = getConfig().getInt("count", 4);
        int maxAlive = getConfig().getInt("maxAlive", 6);
        double radius = getConfig().getDouble("spawnRadius", 2.6);
        int lifetime = getConfig().getInt("lifetimeTicks", 500);

        // Sunucuyu bogmamak icin ayni anda ayakta kalabilecek asker sinirli
        int alive = countAlive(level, player);
        int allowed = Math.max(0, maxAlive - alive);
        if (allowed <= 0) {
            // Sessizce basarisiz olmasin: neden cagrilmadigini oyuncu bilmeli
            report(player, "Zaten " + alive + " asker ayakta (sinir " + maxAlive + ")");
            return;
        }

        int toSpawn = Math.min(count, allowed);
        int spawned = 0;
        int noGround = 0;

        for (int i = 0; i < toSpawn; i++) {
            // Oyuncunun cevresine esit acilarla dagit
            double angle = (i / (double) toSpawn) * Math.PI * 2 + level.random.nextDouble() * 0.4;
            double dist = radius * (0.7 + level.random.nextDouble() * 0.5);

            Vec3 spot = new Vec3(
                    player.getX() + Math.cos(angle) * dist,
                    player.getY(),
                    player.getZ() + Math.sin(angle) * dist);

            Vec3 ground = SandSpikeController.groundUnder(level, spot.add(0, 1.0, 0));
            if (ground == null) {
                // Zemin bulunamadi — oyuncunun kendi hizasina dus.
                // Eskiden burada sessizce vazgeciliyordu ve asker hic
                // cikmiyordu; oyuncu neden olmadigini anlayamiyordu.
                ground = new Vec3(spot.x, player.getY(), spot.z);
                noGround++;
            }

            SandSoldierEntity soldier =
                    SandSoldierEntity.create(level, player, ground.x, ground.y, ground.z);
            if (soldier == null) {
                report(player, "HATA: asker olusturulamadi (entity turu kayitli mi?)");
                return;
            }

            // Dort askerin ikisi MENZILLI, ikisi yakin dovus. Turler sirayla
            // veriliyor: rastgele birakinca bazen hepsi ayni turden cikiyor
            // ve takimin dengesi her seferinde degisiyordu.
            soldier.setVariant(VARIANT_ORDER[i % VARIANT_ORDER.length]);

            soldier.setLifetime(lifetime);

            if (!level.addFreshEntity(soldier)) {
                report(player, "HATA: asker dunyaya eklenemedi");
                return;
            }
            spawned++;

            // Yerden yukselirken taban kumu savrulur
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                    ground.x, ground.y + 0.1, ground.z,
                    22, 0.4, 0.15, 0.4, 0.08);
        }

        report(player, spawned + " asker cagrildi"
                + (noGround > 0 ? " (" + noGround + " tanesi zemin bulamadi)" : ""));

        if (spawned == 0) return;

        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);

        // Sandman'in elini yere koydugu nokta
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                player.getX(), player.getY() + 0.1, player.getZ(),
                30, 0.6, 0.1, 0.6, 0.1);
    }

    /**
     * Oyuncuya durum bildirir.
     *
     * Cagirma sessizce basarisiz olabildigi icin (sinir dolu, zemin yok,
     * entity olusmadi) bunlarin gorunur olmasi sart — aksi halde "hicbir sey
     * olmuyor" deneyimi cikiyor ve sebebi anlasilmiyor.
     */
    private static void report(ServerPlayer player, String message) {
        player.displayClientMessage(
                Component.literal("[Sandman] " + message).withStyle(ChatFormatting.GOLD), true);
    }

    private static int countAlive(ServerLevel level, ServerPlayer owner) {
        AABB area = new AABB(owner.blockPosition()).inflate(48.0);
        List<SandSoldierEntity> nearby =
                level.getEntitiesOfClass(SandSoldierEntity.class, area,
                        e -> e.isAlive() && owner.getUUID().equals(e.getOwnerId()));
        return nearby.size();
    }
}
