package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BeamSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PropulsionBurstAbility extends Ability {

    /** Dash sirasindaki oyuncu durumu. Yon kilitli tutulur, sadece A/D degisince yenilenir. */
    private static final class DashState {
        /**
         * Itis yonu — TAM 3B. Yatay ve dikey ayri tutulmuyor: lazer bakisin
         * tam tersine ittigi icin tek vektor yeterli ve tam asagi/yukari
         * bakista da dogru sonuc veriyor.
         */
        Vec3 direction = Vec3.ZERO;
        int lastStrafeSign = 0;
        Vec3 lastTrailPos = null;
        boolean initialized = false;
    }

    private static final Map<UUID, DashState> dashStates = new HashMap<>();

    /** Bir dash sirasinda ayni hedefe tekrar tekrar vurmayi engeller. */
    private static final Set<UUID> rammed = new HashSet<>();

    public PropulsionBurstAbility() {
        super("cyclops_propulsion", AbilityType.CHANNELED, AbilitySlot.SHIFT);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 80);
        config.set("maxDurationTicks", 40);
        config.set("backwardForce", 1.35);
        config.set("sideForce", 1.55);
        // Dikey itis yataya gore biraz kisik tutulur; yoksa yere bakinca
        // roket gibi firliyor
        config.set("verticalScale", 0.8);
        config.set("initialBurstForce", 1.9);
        config.set("aoeRadius", 3.5);
        config.set("aoeKnockback", 0.8);
        config.set("aoeDamage", 3.0f);
        config.set("trailDurationTicks", 20);
        config.set("trailMinDistance", 0.25);
        config.set("eyeBeamLength", 5.0);
        // Dash sirasinda carpilan hedefler
        config.set("rammRadius", 1.6);
        config.set("rammKnockback", 1.9);
        config.set("rammVertical", 0.45);
        config.set("rammDamage", 4.0f);
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        AbilityConfig cfg = getConfig();
        double burst = cfg.getDouble("initialBurstForce", 1.2);
        double aoeRadius = cfg.getDouble("aoeRadius", 3.5);
        double aoeKb = cfg.getDouble("aoeKnockback", 0.8);
        float aoeDmg = cfg.getFloat("aoeDamage", 3.0f);

        ServerLevel level = (ServerLevel) player.level();

        Vec3 lookDir = player.getLookAngle();
        // Itis lazerin acisina gore: bakisin tam tersi yonde, dikey bilesen dahil.
        // Yere bakinca yukari, havaya bakinca asagi iter.
        Vec3 pushDir = lookDir.scale(-1).normalize();

        DashState st = new DashState();
        st.direction = pushDir;
        st.lastStrafeSign = 0;
        st.lastTrailPos = player.getEyePosition(1.0f).add(lookDir.scale(0.5));
        st.initialized = true;
        dashStates.put(player.getUUID(), st);

        player.setDeltaMovement(
                pushDir.x * burst,
                pushDir.y * burst + 0.25,
                pushDir.z * burst);
        player.hurtMarked = true;

        level.playSound(null, player.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 1.2f);

        // Acilis patlamasi gozun ONUNDEN ciksin, kafanin icinden degil
        Vec3 eyePos = player.getEyePosition(1.0f).add(lookDir.scale(0.45));
        Vec3 beamEnd = eyePos.add(lookDir.scale(3.0));
        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                BeamSyncPacket.flash(eyePos, beamEnd, 1.0f, cfg.getInt("trailDurationTicks", 20)));

        CyclopsBeamRenderer.renderEyeMuzzle(level,
                player.getEyePosition(1.0f), lookDir, 3.0f);

        AABB aoe = player.getBoundingBox().inflate(aoeRadius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aoe,
                e -> e != player && e.distanceTo(player) <= aoeRadius);

        for (LivingEntity target : nearby) {
            target.hurt(player.damageSources().playerAttack(player), aoeDmg);
            Vec3 awayDir = target.position().subtract(player.position()).normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(
                    awayDir.x * aoeKb, 0.2, awayDir.z * aoeKb));
            target.hurtMarked = true;
        }
    }

    @Override
    protected void onTick(ServerPlayer player, int ticksActive) {
        AbilityConfig cfg = getConfig();
        DashState st = dashStates.get(player.getUUID());
        if (st == null || !st.initialized) return;

        double backForce = cfg.getDouble("backwardForce", 0.85);
        double sideForce = cfg.getDouble("sideForce", 1.05);
        int maxTicks = cfg.getInt("maxDurationTicks", 40);

        int strafeSign = (int) Math.signum(player.xxa);

        // Yon SADECE A/D girisi degisince yeniden hesaplanir. Her tick bakis
        // vektorunden hesaplamak kamerayi cevirince titremeye sebep oluyordu.
        if (strafeSign != st.lastStrafeSign) {
            if (strafeSign == 0) {
                // Bakisin tam tersi — dikey bilesen dahil
                st.direction = player.getLookAngle().scale(-1).normalize();
            } else {
                // Yana kayma her zaman yatay. Yon bakis vektorunden degil
                // YAW'dan aliniyor: tam asagi bakarken bakis vektorunun yatay
                // bileseni sifir oldugu icin yon hesaplanamiyordu.
                st.direction = yawSide(player).scale(strafeSign);
            }
            st.lastStrafeSign = strafeSign;
        }

        double decay = Math.max(0.35, 1.0 - (ticksActive / (double) maxTicks));
        double speed = (strafeSign == 0 ? backForce : sideForce) * decay;
        double verticalScale = cfg.getDouble("verticalScale", 0.8);

        // Itis tek vektorden uygulanir; boylece tam asagi bakinca hareket
        // TAM YUKARI olur, yatay bir kacak kalmaz. Yatay bakista dikey bilesen
        // sifir oldugundan yercekimi de etkisiz kalir (dash sirasinda dusulmez).
        Vec3 dir = st.direction;
        player.setDeltaMovement(
                dir.x * speed,
                dir.y * speed * verticalScale,
                dir.z * speed);
        player.hurtMarked = true;
        player.fallDistance = 0f;
        player.resetFallDistance();

        // Dash bittikten sonraki ilk inisde dusme hasari alinmasin
        CyclopsFallDamageHandler.grantFallImmunity(player.getUUID());
        player.fallDistance = 0f;

        rammEntities(player, cfg, st.direction);

        // Trail: gercek hareket yolunu takip eder, bakis yonunu degil.
        // Itisi saglayan lazer GOZDEN cikar (bakisin tersine dogru iteriz).
        // Gozun tam merkezinden baslarsa 3. sahista kafanin icinde kaliyor,
        // bu yuzden bakis yonunde one kaydiriliyor.
        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition(1.0f).add(look.scale(0.45));
        Vec3 laserEnd = eye.add(look.scale(cfg.getDouble("eyeBeamLength", 5.0)));

        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                BeamSyncPacket.flash(eye, laserEnd, 1.0f, 3));

        // Kalin isin: partikuller TAM goz hizasindan saciliyor
        if (ticksActive % 2 == 0) {
            CyclopsBeamRenderer.renderEyeMuzzle((ServerLevel) player.level(),
                    player.getEyePosition(1.0f), look, 2.5f);
        }

        // Iz de gozun ONUNDEN cikar. Tam goz merkezinden cikinca kafanin
        // icinden gecip arkada gorunuyordu.
        Vec3 currentPos = player.getEyePosition(1.0f).add(look.scale(0.5));
        double minDist = cfg.getDouble("trailMinDistance", 0.25);
        if (st.lastTrailPos != null && st.lastTrailPos.distanceTo(currentPos) >= minDist) {
            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    BeamSyncPacket.flash(st.lastTrailPos, currentPos, 1.0f,
                            cfg.getInt("trailDurationTicks", 20)));
            st.lastTrailPos = currentPos;
        }
    }

    /** Dash sirasinda carpilan hedefleri dash yonunde sertce iter. */
    private void rammEntities(ServerPlayer player, AbilityConfig cfg, Vec3 dashDir) {
        double radius = cfg.getDouble("rammRadius", 1.6);
        double kb = cfg.getDouble("rammKnockback", 1.9);
        double kbY = cfg.getDouble("rammVertical", 0.45);
        float dmg = cfg.getFloat("rammDamage", 4.0f);

        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> hits = player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());

        for (LivingEntity target : hits) {
            if (rammed.contains(target.getUUID())) continue;
            rammed.add(target.getUUID());

            target.hurt(player.damageSources().playerAttack(player), dmg);
            target.setDeltaMovement(
                    dashDir.x * kb,
                    kbY,
                    dashDir.z * kb);
            target.hurtMarked = true;
        }
    }

    @Override
    protected void onChannelStop(ServerPlayer player) {
        dashStates.remove(player.getUUID());
        rammed.clear();
    }

    /**
     * Oyuncunun sagini gosteren YATAY vektor.
     *
     * Bakis vektorunden turetilmiyor: tam asagi/yukari bakarken bakisin yatay
     * bileseni sifirlanir ve yon tanimsiz kalir. Yaw ise her zaman gecerlidir.
     */
    private static Vec3 yawSide(ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        // Ileri = (-sin yaw, 0, cos yaw); sag = onun 90 derece sagi
        return new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
    }
}
