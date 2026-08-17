package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

import java.util.*;

/**
 * Sand Spike'larin yasam dongusu.
 *
 * Zaman cizelgesi (dokumandaki asamalarin tick karsiligi):
 *   RISE   0-7    diken yer altindan yukselir, boyu 0 -> tam
 *   IMPACT 5      hasar ve yukari firlatma (diken ~%70 cikmisken)
 *   HOLD   8-37   ayakta kalir, icinde duranlari yavaslatir (alan reddi)
 *   SINK   38-45  dagilarak coker
 *
 * Diken gercek blok YAZMIYOR: dokumanda blok spam'inden kacinilmasi isteniyor
 * ve PvP haritalarinda kalici hasar birakmasi istenmiyor. Su an gorsel
 * partikulle temsil ediliyor; model/animasyon asamasinda buranin yerine
 * custom renderer veya entity gelecek. Mekanik tarafi degismeyecek.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandSpikeController {

    private static final int RISE_TICKS = 8;
    private static final int IMPACT_TICK = 5;
    private static final int HOLD_END = 37;
    private static final int TOTAL_TICKS = 45;

    /** Bir oyuncunun ayni anda ayakta tutabilecegi diken sayisi. */
    private static final int MAX_PER_PLAYER = 3;

    private static final DustParticleOptions SAND_DUST =
            new DustParticleOptions(new Vector3f(0.85f, 0.74f, 0.48f), 1.2f);
    private static final DustParticleOptions SAND_DARK =
            new DustParticleOptions(new Vector3f(0.62f, 0.52f, 0.32f), 1.4f);

    private static final BlockParticleOption SAND_BLOCK =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

    private static final class Spike {
        final UUID owner;
        final ServerLevel level;
        final Vec3 base;
        final float damage;
        final double radius;
        final double knockUp;
        final double knockback;
        final double height;

        int ticks = 0;
        /** Ayni diken ayni hedefe tekrar vurmasin. */
        final Set<UUID> hit = new HashSet<>();

        Spike(UUID owner, ServerLevel level, Vec3 base, AbilityConfig cfg) {
            this.owner = owner;
            this.level = level;
            this.base = base;
            this.damage = cfg.getFloat("damage", 5.0f);
            this.radius = cfg.getDouble("radius", 1.7);
            this.knockUp = cfg.getDouble("knockUp", 0.85);
            this.knockback = cfg.getDouble("knockback", 0.35);
            this.height = cfg.getDouble("height", 3.0);
        }

        /** 0..1 — dikenin o anki gorunur boy orani. */
        double extent() {
            if (ticks <= RISE_TICKS) return ticks / (double) RISE_TICKS;
            if (ticks <= HOLD_END) return 1.0;
            return Math.max(0.0, 1.0 - (ticks - HOLD_END) / (double) (TOTAL_TICKS - HOLD_END));
        }
    }

    private static final List<Spike> spikes = new ArrayList<>();

    private SandSpikeController() {}

    // ------------------------------------------------------------------

    public static void spawn(ServerPlayer player, Vec3 ground, AbilityConfig cfg) {
        ServerLevel level = (ServerLevel) player.level();

        // Eski dikenler birikmesin — en eskisi dusurulur
        long mine = spikes.stream().filter(s -> s.owner.equals(player.getUUID())).count();
        if (mine >= MAX_PER_PLAYER) {
            for (Iterator<Spike> it = spikes.iterator(); it.hasNext(); ) {
                if (it.next().owner.equals(player.getUUID())) {
                    it.remove();
                    break;
                }
            }
        }

        spikes.add(new Spike(player.getUUID(), level, ground, cfg));

        level.playSound(null, BlockPos.containing(ground),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.3f, 0.55f);
    }

    /**
     * Verilen noktanin altindaki ilk kati zeminin UST yuzeyi.
     * Bulamazsa null — yetenek de o zaman kullanilamaz.
     */
    public static Vec3 groundUnder(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);

        // Once yukari dogru bosluga cik (nokta blogun icinde kalmis olabilir)
        int up = 0;
        while (up < 3 && !level.getBlockState(pos).isAir()) {
            pos = pos.above();
            up++;
        }

        for (int i = 0; i < 8; i++) {
            BlockPos below = pos.below();
            if (!level.getBlockState(below).isAir()) {
                return new Vec3(pos.getX() + 0.5, below.getY() + 1.0, pos.getZ() + 0.5);
            }
            pos = below;
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (spikes.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Spike> it = spikes.iterator();
        while (it.hasNext()) {
            Spike spike = it.next();
            spike.ticks++;

            tickSpike(spike, server.getPlayerList().getPlayer(spike.owner));

            if (spike.ticks >= TOTAL_TICKS) it.remove();
        }
    }

    private static void tickSpike(Spike spike, ServerPlayer owner) {
        double extent = spike.extent();

        drawSpike(spike, extent);

        if (spike.ticks == IMPACT_TICK) {
            applyImpact(spike, owner);
        } else if (spike.ticks > RISE_TICKS && spike.ticks <= HOLD_END) {
            applyAreaDenial(spike, owner);
        }

        if (spike.ticks == HOLD_END + 1) {
            spike.level.playSound(null, BlockPos.containing(spike.base),
                    SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f);
        }
    }

    /**
     * Dikenin gorsel govdesi: yukari dogru daralan halkalar.
     * Model asamasinda burasi gercek geometriyle degistirilecek.
     */
    private static void drawSpike(Spike spike, double extent) {
        if (extent <= 0.01) return;

        ServerLevel level = spike.level;
        double top = spike.height * extent;
        int layers = Math.max(2, (int) (top * 3));

        for (int i = 0; i < layers; i++) {
            double t = i / (double) layers;
            double y = spike.base.y + top * t;
            // Tabanda genis, tepede sivri
            double r = spike.radius * 0.75 * (1.0 - t * 0.85);

            int points = Math.max(3, (int) (r * 7));
            for (int p = 0; p < points; p++) {
                double a = (p / (double) points) * Math.PI * 2 + t * 1.6;
                double x = spike.base.x + Math.cos(a) * r;
                double z = spike.base.z + Math.sin(a) * r;

                level.sendParticles(t < 0.5 ? SAND_DARK : SAND_DUST,
                        x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        // Yukselirken tabandan sacilan kum
        if (spike.ticks <= RISE_TICKS) {
            level.sendParticles(SAND_BLOCK,
                    spike.base.x, spike.base.y + 0.1, spike.base.z,
                    6, spike.radius * 0.6, 0.1, spike.radius * 0.6, 0.08);
        }
    }

    /** Hasar + yukari firlatma; diken basina hedef basina bir kez. */
    private static void applyImpact(Spike spike, ServerPlayer owner) {
        if (owner == null) return;

        ServerLevel level = spike.level;
        AABB box = new AABB(spike.base, spike.base)
                .inflate(spike.radius, spike.height * 0.5 + 1.0, spike.radius);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive());

        for (LivingEntity target : targets) {
            if (!spike.hit.add(target.getUUID())) continue;

            target.hurt(owner.damageSources().playerAttack(owner), spike.damage);

            // Disa dogru hafif itis + belirgin yukari firlatma
            Vec3 out = target.position().subtract(spike.base);
            Vec3 flat = new Vec3(out.x, 0, out.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();

            target.setDeltaMovement(
                    flat.x * spike.knockback,
                    spike.knockUp,
                    flat.z * spike.knockback);
            target.hurtMarked = true;
        }

        level.sendParticles(SAND_BLOCK,
                spike.base.x, spike.base.y + spike.height * 0.5, spike.base.z,
                24, spike.radius * 0.7, spike.height * 0.35, spike.radius * 0.7, 0.16);

        level.playSound(null, BlockPos.containing(spike.base),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
    }

    /**
     * Alan reddi: diken ayaktayken icinde duran dusmanlar yavaslar.
     *
     * Gercek carpisma kutusu yerine yavaslatma kullaniliyor cunku diken su an
     * blok da entity de degil. Model/entity asamasinda burasi gercek
     * collision'a cevrilebilir.
     */
    private static void applyAreaDenial(Spike spike, ServerPlayer owner) {
        if (owner == null || spike.ticks % 10 != 0) return;

        AABB box = new AABB(spike.base, spike.base)
                .inflate(spike.radius, spike.height * 0.5, spike.radius);

        List<LivingEntity> inside = spike.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive());

        for (LivingEntity target : inside) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
        }
    }
}
