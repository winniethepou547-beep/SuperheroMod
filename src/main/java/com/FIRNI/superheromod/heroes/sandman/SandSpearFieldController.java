package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SAND SPEARS — R.
 *
 * Onunde dikdortgen bir alan isaretlenir. Alan aninda kuma doner ve
 * icindekiler yavaslar; BIR SANIYE SONRA yerden sarkitlar firlayip hasar
 * verir.
 *
 * Gecikme bilerek var: alan once "burasi tehlikeli" diyor, dusman
 * kacacak zamani buluyor. Aninda vursaydi kacinilmaz bir alan hasari
 * olurdu; simdi yavaslatma ile kacisi zorlastirmak yetenegin asil isi.
 *
 * Sarkitlar oyunun kendi sivri damla tasini (pointed dripstone)
 * kullaniyor: kendi modelimizi cizmek yerine oyunun varligi
 * kullanildigi icin arazi ile ayni dilde duruyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandSpearFieldController {

    /** Isaretten sarkitlarin cikmasina kadar gecen sure. */
    private static final int WARN_TICKS = 20;
    /** Sarkitlarin ayakta kalma suresi. */
    private static final int SPEAR_TICKS = 40;

    private static final int SLOW_DURATION = 30;
    private static final int SLOW_AMPLIFIER = 1;

    private static final class Field {
        final UUID ownerId;
        final ServerLevel level;
        final AbilityConfig cfg;

        final Vec3 near;
        final Vec3 far;
        final Vec3 forward;
        final Vec3 side;

        int ticks = 0;
        boolean erupted = false;
        final Set<UUID> struck = new HashSet<>();

        Field(UUID ownerId, ServerLevel level, AbilityConfig cfg,
              Vec3 near, Vec3 far, Vec3 forward, Vec3 side) {
            this.ownerId = ownerId;
            this.level = level;
            this.cfg = cfg;
            this.near = near;
            this.far = far;
            this.forward = forward;
            this.side = side;
        }
    }

    private static final List<Field> fields = new ArrayList<>();

    private SandSpearFieldController() {}

    public static void cast(ServerPlayer player, AbilityConfig cfg) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // Alan yatay bakisa gore yerlesiyor. Dikey aci kullanilsaydi yere
        // bakinca alan ayaklarin dibinde toplanir, gokyuzune bakinca
        // havada kalirdi.
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)).normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);

        double length = cfg.getDouble("length", 10.0);
        double gap = cfg.getDouble("startGap", 1.5);

        Vec3 feet = new Vec3(player.getX(), player.getY(), player.getZ());
        Vec3 near = feet.add(forward.scale(gap));
        Vec3 far = feet.add(forward.scale(gap + length));

        fields.add(new Field(player.getUUID(), level, cfg, near, far, forward, side));

        // Alan ANINDA kuma doner — isaret sadece sarkitlar icin
        paveField(player, level, cfg, near, far, forward, side);

        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.4f, 0.8f);
    }

    /** Alani gercek kumla kaplar; kum izi kalici. */
    private static void paveField(ServerPlayer owner, ServerLevel level, AbilityConfig cfg,
                                  Vec3 near, Vec3 far, Vec3 forward, Vec3 side) {
        double halfWidth = cfg.getDouble("width", 5.0) * 0.5;
        double length = near.distanceTo(far);

        int along = (int) Math.ceil(length);
        int across = (int) Math.ceil(halfWidth * 2);

        for (int i = 0; i <= along; i++) {
            for (int k = 0; k <= across; k++) {
                double t = i / (double) along;
                double u = k / (double) across - 0.5;

                Vec3 spot = near.add(forward.scale(length * t))
                        .add(side.scale(u * halfWidth * 2));

                Vec3 ground = SandSpikeController.groundUnder(level, spot.add(0, 1.5, 0));
                if (ground == null) continue;

                // Kum alanlari zaten yavaslatmayi ve kaplamayi yonetiyor;
                // burada ayri bir sistem kurmuyoruz
                SandPatchController.drop(owner, ground, 1.2,
                        cfg.getInt("patchTicks", 220));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (fields.isEmpty()) return;
        if (ServerLifecycleHooks.getCurrentServer() == null) return;

        Iterator<Field> it = fields.iterator();
        while (it.hasNext()) {
            Field field = it.next();
            ServerPlayer owner = field.level.getServer()
                    .getPlayerList().getPlayer(field.ownerId);

            field.ticks++;

            applySlow(field, owner);

            if (!field.erupted && field.ticks >= WARN_TICKS) {
                field.erupted = true;
                erupt(field, owner);
            }

            if (field.ticks >= WARN_TICKS + SPEAR_TICKS) it.remove();
        }
    }

    /** Alandaki herkes yavaslar — sarkitlardan kacmayi zorlastirir. */
    private static void applySlow(Field field, ServerPlayer owner) {
        for (LivingEntity target : targetsIn(field, owner)) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION, SLOW_AMPLIFIER,
                    false, false, true));
        }
    }

    /** Sarkitlar firlar ve alanda kalanlara vurur. */
    private static void erupt(Field field, ServerPlayer owner) {
        double halfWidth = field.cfg.getDouble("width", 5.0) * 0.5;
        double length = field.near.distanceTo(field.far);
        float damage = field.cfg.getFloat("damage", 8.0f);

        for (LivingEntity target : targetsIn(field, owner)) {
            if (!field.struck.add(target.getUUID())) continue;

            target.hurt(field.level.damageSources().playerAttack(owner), damage);

            // Yukari savurma: sarkit hedefi yerden kaldiriyor
            target.setDeltaMovement(target.getDeltaMovement().x, 0.55,
                    target.getDeltaMovement().z);
            target.hurtMarked = true;
        }

        // Sarkitlarin gorseli: alan boyunca dagilmis sivri kum sutunlari
        int count = (int) (length * halfWidth * 1.2);
        for (int i = 0; i < count; i++) {
            double t = field.level.random.nextDouble();
            double u = field.level.random.nextDouble() * 2 - 1;

            Vec3 spot = field.near.add(field.forward.scale(length * t))
                    .add(field.side.scale(u * halfWidth));

            Vec3 ground = SandSpikeController.groundUnder(field.level, spot.add(0, 1.5, 0));
            if (ground == null) continue;

            field.level.sendParticles(sand(), ground.x, ground.y + 0.8, ground.z,
                    12, 0.18, 0.7, 0.18, 0.08);
        }

        field.level.playSound(null, BlockPos.containing(field.near),
                SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.PLAYERS, 1.6f, 0.7f);
    }

    private static List<LivingEntity> targetsIn(Field field, ServerPlayer owner) {
        double halfWidth = field.cfg.getDouble("width", 5.0) * 0.5;
        double length = field.near.distanceTo(field.far);

        Vec3 center = field.near.add(field.forward.scale(length * 0.5));
        double reach = Math.max(halfWidth, length * 0.5) + 1.0;

        AABB box = new AABB(center, center).inflate(reach, 2.5, reach);

        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity target : field.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (owner != null && target == owner) continue;
            if (target instanceof SandSoldierEntity soldier
                    && field.ownerId.equals(soldier.getOwnerId())) continue;

            // Kaba kutu ile arandi, DIKDORTGEN ile dogrulaniyor: kosede
            // kalanlar alanin disinda oldugu halde vuruluyordu
            Vec3 rel = target.position().subtract(field.near);
            double alongDist = rel.dot(field.forward);
            double acrossDist = rel.dot(field.side);

            if (alongDist < -0.5 || alongDist > length + 0.5) continue;
            if (Math.abs(acrossDist) > halfWidth) continue;

            out.add(target);
        }
        return out;
    }

    /**
     * Kirmizi gosterge ve sarkitlar cizim listesine ekleniyor.
     *
     * Gosterge cekme yetenegindekiyle AYNI bicimde: oyuncu iki yetenegi
     * ayni dilde okumali.
     */
    static void collectShapes(List<SandShapeSyncPacket.Shape> out) {
        int id = 900_000;

        for (Field field : fields) {
            double halfWidth = field.cfg.getDouble("width", 5.0) * 0.5;
            double length = field.near.distanceTo(field.far);

            float yaw = (float) Math.toDegrees(
                    Math.atan2(-field.forward.x, field.forward.z));

            // UYARI asamasinda gosterge yaniyor; sarkitlar ciktiktan sonra
            // sonuyor -- gosterge "olacak" demek, "oluyor" demek degil
            float pulse = field.erupted ? 0.25f
                    : 0.5f + 0.5f * Mth.sin(field.ticks * 0.4f);

            int chevrons = Math.max(3, (int) (length / 2.0));
            for (int c = 0; c < chevrons; c++) {
                double t = (c + 0.5) / chevrons;
                Vec3 tip = field.near.add(field.forward.scale(length * t));

                Vec3 ground = SandSpikeController.groundUnder(
                        field.level, tip.add(0, 1.5, 0));
                if (ground == null) ground = tip;

                out.add(new SandShapeSyncPacket.Shape(
                        id++, SandShapeSyncPacket.TYPE_ARROW,
                        ground.x, ground.y, ground.z,
                        yaw, 0f, pulse, (float) halfWidth, 0f));
            }
        }
    }

    public static void clear() {
        fields.clear();
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }
}
