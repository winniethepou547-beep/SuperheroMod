package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.core.world.SurfaceBreaker;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BeamSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
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
 * Optic Burst — hitscan OLMAYAN seri atis. Her mermi gercek bir mermi gibi
 * yol alir; her tick bir miktar ilerler ve gectigi parca icin carpisma testi
 * yapilir. Gorsel olarak ayni ince lazerin kisa bir izi cizilir.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class RapidFireController {

    private static final int BURST_COUNT = 30;      // 10 -> 30
    private static final int FIRE_INTERVAL = 1;     // mermiler arasi tick
    private static final double SPEED = 5.5;        // blok / tick (2.2 -> 5.5)
    private static final double MAX_TRAVEL = 45.0;
    /** Yarim kalp — seri atis oldugu icin tek mermi az vurur. */
    private static final float DAMAGE = 1.0f;

    // Carptigi yuzeyde acilan minik patlama (sol tikla ayni)
    private static final double BREAK_RADIUS = 1.15;
    private static final int BREAK_MAX_BLOCKS = 4;       // adet arttigi icin dusuruldu
    // Yaricap buyuk tutuluyor ki iskalamasin; dagilim ise ip gibi duz
    // gorunmesin diye orta seviyede (0.006 cok azdi, 0.025 cok fazlaydi).
    private static final float RADIUS = 0.55f;
    private static final float SPREAD = 0.014f;
    private static final double KNOCKBACK = 0.25;

    /** Ates edilmeyi bekleyen seri. */
    private static final class Burst {
        final UUID shooter;
        int remaining = BURST_COUNT;
        int cooldown = 0;

        Burst(UUID shooter) { this.shooter = shooter; }
    }

    /** Havada ilerleyen tek mermi. */
    private static final class Round {
        final UUID shooter;
        Vec3 pos;
        final Vec3 velocity;
        double travelled = 0;

        Round(UUID shooter, Vec3 pos, Vec3 velocity) {
            this.shooter = shooter;
            this.pos = pos;
            this.velocity = velocity;
        }
    }

    private static final List<Burst> bursts = new ArrayList<>();
    private static final List<Round> rounds = new ArrayList<>();

    public static void startBurst(ServerPlayer player) {
        bursts.add(new Burst(player.getUUID()));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (bursts.isEmpty() && rounds.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        tickBursts(server);
        tickRounds(server);
    }

    private static void tickBursts(net.minecraft.server.MinecraftServer server) {
        Iterator<Burst> it = bursts.iterator();
        while (it.hasNext()) {
            Burst burst = it.next();
            ServerPlayer shooter = server.getPlayerList().getPlayer(burst.shooter);
            if (shooter == null) {
                it.remove();
                continue;
            }

            if (burst.cooldown > 0) {
                burst.cooldown--;
                continue;
            }

            fireRound(shooter);
            burst.remaining--;
            burst.cooldown = FIRE_INTERVAL;

            if (burst.remaining <= 0) it.remove();
        }
    }

    private static void fireRound(ServerPlayer shooter) {
        ServerLevel level = (ServerLevel) shooter.level();

        Vec3 eye = RaycastSystem.getEyeOrigin(shooter);
        Vec3 look = RaycastSystem.getLookDirection(shooter);

        Vec3 dir = look.add(
                (level.random.nextDouble() - 0.5) * SPREAD * 2,
                (level.random.nextDouble() - 0.5) * SPREAD * 2,
                (level.random.nextDouble() - 0.5) * SPREAD * 2).normalize();

        rounds.add(new Round(shooter.getUUID(), eye.add(dir.scale(0.35)), dir.scale(SPEED)));

        level.playSound(null, shooter.blockPosition(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.55f, 1.9f);
        CyclopsBeamRenderer.renderOriginFlash(level, eye.add(dir.scale(0.35)));
    }

    /**
     * Seri atista her mermi hasar vermeli. Vanilla hurt() cagrisi 10 tick
     * dokunulmazlik biraktigi icin arka arkaya gelen mermiler yok sayiliyordu.
     * invulnerableTime sifirlaniyor; yine de reddedilirse can dogrudan dusuruluyor.
     */
    private static void applyBurstDamage(ServerPlayer shooter, LivingEntity target, Vec3 dir) {
        target.invulnerableTime = 0;
        target.hurtTime = 0;

        boolean applied = target.hurt(
                shooter.damageSources().playerAttack(shooter), DAMAGE);

        if (!applied && target.isAlive()) {
            // Guvenlik agi: dokunulmazlik yine de engellediyse elle uygula
            float remaining = target.getHealth() - DAMAGE;
            target.setHealth(Math.max(0f, remaining));
            if (remaining <= 0f) {
                target.die(shooter.damageSources().playerAttack(shooter));
            }
        }

        target.invulnerableTime = 0;
        target.hurtTime = 0;

        // Knockback YOK: her mermi itince hedef isin hattindan cikiyor ve
        // sonraki mermiler iskaliyordu. Seri atista itis olmamali.
        target.hurtMarked = true;
    }

    private static void tickRounds(net.minecraft.server.MinecraftServer server) {
        Iterator<Round> it = rounds.iterator();
        while (it.hasNext()) {
            Round round = it.next();
            ServerPlayer shooter = server.getPlayerList().getPlayer(round.shooter);
            if (shooter == null) {
                it.remove();
                continue;
            }

            ServerLevel level = (ServerLevel) shooter.level();
            Vec3 from = round.pos;
            Vec3 dir = round.velocity.normalize();
            double step = round.velocity.length();

            // Bu tick'te kat edilen parcayi carpisma icin tara
            RaycastResult result = RaycastSystem.cast(
                    level, shooter, from, dir, step, RADIUS, false,
                    e -> e instanceof LivingEntity && e != shooter);

            Vec3 to = result.getHitPosition();

            // Merminin kisa izi
            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> shooter),
                    BeamSyncPacket.flash(from, to, 0.35f, 2));

            boolean stop = false;

            if (result.didHitEntity()) {
                for (RaycastResult.EntityHit hit : result.getEntityHits()) {
                    if (hit.getEntity() instanceof LivingEntity target) {
                        applyBurstDamage(shooter, target, dir);
                    }
                }
                stop = true;
            } else if (result.didHitBlock()) {
                // Yuzeye carpti — sol tiktaki gibi minik patlama acar ve kirar
                SurfaceBreaker.chip(level, shooter, to, BREAK_RADIUS, BREAK_MAX_BLOCKS);
                stop = true;
            }

            round.travelled += from.distanceTo(to);
            round.pos = to;

            if (stop || round.travelled >= MAX_TRAVEL) {
                CyclopsBeamRenderer.renderImpact(level, to);
                it.remove();
            }
        }
    }
}
