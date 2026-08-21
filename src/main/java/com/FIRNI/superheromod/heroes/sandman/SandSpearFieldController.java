package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
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
 * HER ASAMA KADEMELI. Onceki surumde alan isaretleniyor, sonra tek bir
 * karede hem zemin kuma donuyor hem butun sarkitlar beliriyordu; sonuc
 * "pat diye olan" ve okunamayan bir yetenekti.
 *
 * Akis:
 *   DOSEME   zemin ONDEN ARKAYA sira sira kuma doner
 *   YUKSELIS sarkitlar dalga halinde yerden cikar, tepeleri onde
 *   BEKLEME  kisa duraklama
 *   INIS     kutle kisalir, en son kalan sey TEPE
 *
 * Sarkit yukselirken tepesi onde gidiyor: gercek bir kazik topraktan
 * once ucuyla cikar. Inerken sira bozulmuyor, sutun kisaliyor ve tepe
 * en sona kaliyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandSpearFieldController {

    /** Cerceve gorunup zemin dosenirken gecen sure. */
    private static final int PAVE_TICKS = 16;
    /** Tek sarkitin tam boya ulasmasi. */
    private static final int RISE_TICKS = 6;
    /** Tam boyda bekleme. */
    private static final int HOLD_TICKS = 20;
    /** Geri gomulme suresi. */
    private static final int SINK_TICKS = 8;

    /** Sarkit dalgasinin alan boyunca yayilma suresi. */
    private static final int WAVE_SPREAD = 12;

    private static final int MAX_SPEAR_HEIGHT = 4;

    private static final int SLOW_DURATION = 30;
    private static final int SLOW_AMPLIFIER = 1;

    /** Tek bir sarkit sutunu. */
    private static final class Spear {
        final BlockPos base;
        /** Dalga icindeki gecikmesi — alan boyunca yayilmayi saglar. */
        final int delay;
        final int height;
        /** Su an yerde duran blok sayisi. */
        int drawn = 0;
        boolean damaged = false;

        Spear(BlockPos base, int delay, int height) {
            this.base = base;
            this.delay = delay;
            this.height = height;
        }
    }

    private static final class Field {
        final UUID ownerId;
        final ServerLevel level;
        final AbilityConfig cfg;

        final Vec3 near;
        final Vec3 far;
        final Vec3 forward;
        final Vec3 side;

        final List<Spear> spears = new ArrayList<>();
        final Set<UUID> struck = new HashSet<>();

        int ticks = 0;
        /** Doseme ne kadar ilerledi (0..1). */
        float paved = 0f;
        boolean spearsBuilt = false;

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

        double length() {
            return near.distanceTo(far);
        }

        double halfWidth() {
            return cfg.getDouble("width", 5.0) * 0.5;
        }

        int riseStart() {
            return PAVE_TICKS;
        }

        int sinkStart() {
            return riseStart() + WAVE_SPREAD + RISE_TICKS + HOLD_TICKS;
        }

        int endTick() {
            return sinkStart() + WAVE_SPREAD + SINK_TICKS + 2;
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

        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.2f, 1.1f);
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

            tickPaving(field, owner);
            tickSpears(field, owner);
            applySlow(field, owner);

            if (field.ticks >= field.endTick()) {
                clearSpears(field);
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // DOSEME
    // ------------------------------------------------------------------

    /**
     * Kum ONDEN ARKAYA ilerleyerek doseniyor.
     *
     * Tek karede tum alani kaplamak "pat diye oldu" hissi veriyordu.
     * Ilerleme yonu oyuncudan uzaga: kum onun elinden cikip yayiliyormus
     * gibi okunuyor.
     */
    private static void tickPaving(Field field, ServerPlayer owner) {
        if (owner == null) return;
        if (field.paved >= 1f) return;

        float target = Math.min(1f, field.ticks / (float) PAVE_TICKS);
        double length = field.length();
        double halfWidth = field.halfWidth();

        int across = Math.max(2, (int) Math.ceil(halfWidth * 2));

        double from = field.paved * length;
        double to = target * length;

        for (double d = from; d < to; d += 0.9) {
            for (int k = 0; k <= across; k++) {
                double u = k / (double) across - 0.5;

                Vec3 spot = field.near.add(field.forward.scale(d))
                        .add(field.side.scale(u * halfWidth * 2));

                Vec3 ground = SandSpikeController.groundUnder(
                        field.level, spot.add(0, 1.5, 0));
                if (ground == null) continue;

                SandPatchController.drop(owner, ground, 1.1,
                        field.cfg.getInt("patchTicks", 220));

                // Doseme cephesinde kum firliyor — ilerleme gorulebilmeli
                field.level.sendParticles(sand(),
                        ground.x, ground.y + 0.25, ground.z,
                        3, 0.3, 0.1, 0.3, 0.04);
            }
        }

        field.paved = target;
    }

    // ------------------------------------------------------------------
    // SARKITLAR
    // ------------------------------------------------------------------

    /**
     * Sarkitlar dalga halinde cikar, bekler ve geri gomulur.
     *
     * Her sutunun kendi gecikmesi var ve gecikme oyuncuya olan mesafeden
     * turetiliyor: dalga onden arkaya ilerliyor, hepsi ayni anda
     * firlamiyor.
     */
    private static void tickSpears(Field field, ServerPlayer owner) {
        if (field.ticks < field.riseStart()) return;

        if (!field.spearsBuilt) {
            buildSpearList(field);
            field.spearsBuilt = true;

            field.level.playSound(null, BlockPos.containing(field.near),
                    SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.PLAYERS, 1.5f, 0.8f);
        }

        boolean sinking = field.ticks >= field.sinkStart();

        for (Spear spear : field.spears) {
            int wanted;

            if (!sinking) {
                int elapsed = field.ticks - field.riseStart() - spear.delay;
                wanted = elapsed <= 0 ? 0
                        : Math.min(spear.height,
                                1 + (elapsed * spear.height) / RISE_TICKS);
            } else {
                // INIS: sutun kisaliyor, en son kalan sey TEPE
                int elapsed = field.ticks - field.sinkStart() - spear.delay;
                wanted = elapsed <= 0 ? spear.height
                        : Math.max(0, spear.height
                                - (elapsed * spear.height) / SINK_TICKS);
            }

            if (wanted == spear.drawn) continue;

            drawSpear(field, spear, wanted);
            spear.drawn = wanted;

            if (!sinking && wanted >= spear.height && !spear.damaged) {
                spear.damaged = true;
                damageAt(field, owner, spear);
            }
        }
    }

    /**
     * Alani izgaraya bolup sarkit konumlarini secer.
     *
     * SIKI IZGARA: onceki surumde rastgele serpilmis az sayida sutun
     * vardi ve alanin cogu bos kaliyordu.
     */
    private static void buildSpearList(Field field) {
        double length = field.length();
        double halfWidth = field.halfWidth();
        var rnd = field.level.random;

        double stepAlong = 1.25;
        double stepAcross = 1.25;

        int rows = Math.max(2, (int) (length / stepAlong));
        int cols = Math.max(2, (int) (halfWidth * 2 / stepAcross));

        for (int r = 0; r <= rows; r++) {
            for (int c = 0; c <= cols; c++) {
                double t = r / (double) rows;
                double u = c / (double) cols - 0.5;

                // Kucuk kayma: tam izgara yapay duruyor, kaydirinca dogal
                // dagilim cikiyor ama bosluk olusmuyor
                double jitterA = (rnd.nextDouble() - 0.5) * stepAlong * 0.35;
                double jitterC = (rnd.nextDouble() - 0.5) * stepAcross * 0.35;

                Vec3 spot = field.near
                        .add(field.forward.scale(length * t + jitterA))
                        .add(field.side.scale(u * halfWidth * 2 + jitterC));

                Vec3 ground = SandSpikeController.groundUnder(
                        field.level, spot.add(0, 1.5, 0));
                if (ground == null) continue;

                BlockPos base = BlockPos.containing(ground);
                if (!isFree(field.level, base)) continue;

                // Gecikme mesafeye bagli: dalga onden arkaya ilerliyor
                int delay = (int) (t * WAVE_SPREAD);
                int height = 2 + rnd.nextInt(MAX_SPEAR_HEIGHT - 1);

                field.spears.add(new Spear(base, delay, height));
            }
        }
    }

    /**
     * Sutunu istenen yukseklige gore yeniden yazar.
     *
     * Kalinlik yukaridan asagi INCELIYOR: en ustte TIP, altinda FRUSTUM,
     * sonra MIDDLE/BASE. Boylece sutun buyurken tepesi onde gidiyor ve
     * her boyda sivri uclu duruyor -- duz bir kutu yigini gibi degil.
     */
    private static void drawSpear(Field field, Spear spear, int height) {
        // Once eskisini temizle; sutun kisaliyorsa artan bloklar kalmasin
        for (int y = 0; y < spear.height; y++) {
            BlockPos pos = spear.base.above(y);
            if (field.level.getBlockState(pos).is(Blocks.POINTED_DRIPSTONE)) {
                field.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }

        for (int y = 0; y < height; y++) {
            BlockPos pos = spear.base.above(y);
            if (!isFree(field.level, pos)) continue;

            int fromTop = height - 1 - y;
            DripstoneThickness thickness = switch (fromTop) {
                case 0 -> DripstoneThickness.TIP;
                case 1 -> DripstoneThickness.FRUSTUM;
                case 2 -> DripstoneThickness.MIDDLE;
                default -> DripstoneThickness.BASE;
            };

            field.level.setBlock(pos, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
                    .setValue(PointedDripstoneBlock.THICKNESS, thickness), 2);
        }

        if (height > 0) {
            field.level.sendParticles(sand(),
                    spear.base.getX() + 0.5, spear.base.getY() + 0.2, spear.base.getZ() + 0.5,
                    4, 0.25, 0.1, 0.25, 0.05);
        }
    }

    /** Sutun tam boya ulasinca cevresindekilere vurur. */
    private static void damageAt(Field field, ServerPlayer owner, Spear spear) {
        if (owner == null) return;

        Vec3 center = new Vec3(spear.base.getX() + 0.5,
                spear.base.getY() + spear.height * 0.5, spear.base.getZ() + 0.5);
        AABB box = new AABB(center, center).inflate(1.0, spear.height, 1.0);

        float damage = field.cfg.getFloat("damage", 8.0f);

        for (LivingEntity target : field.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == owner) continue;
            if (target instanceof SandSoldierEntity soldier
                    && field.ownerId.equals(soldier.getOwnerId())) continue;

            // HEDEF basina tek hasar: hedef birden cok sutunun menzilinde
            // olabilir ve her biri ayri vursaydi aninda olurdu
            if (!field.struck.add(target.getUUID())) continue;

            target.hurt(field.level.damageSources().playerAttack(owner), damage);
            target.setDeltaMovement(target.getDeltaMovement().x, 0.5,
                    target.getDeltaMovement().z);
            target.hurtMarked = true;
        }
    }

    /** Kalan sarkitlari temizler — arazi eski haline doner. */
    private static void clearSpears(Field field) {
        for (Spear spear : field.spears) {
            for (int y = 0; y < spear.height; y++) {
                BlockPos pos = spear.base.above(y);
                if (field.level.getBlockState(pos).is(Blocks.POINTED_DRIPSTONE)) {
                    field.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        field.spears.clear();
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced()
                || state.is(Blocks.POINTED_DRIPSTONE);
    }

    // ------------------------------------------------------------------
    // Yavaslatma ve gosterge
    // ------------------------------------------------------------------

    private static void applySlow(Field field, ServerPlayer owner) {
        double halfWidth = field.halfWidth();
        double length = field.length();

        Vec3 center = field.near.add(field.forward.scale(length * 0.5));
        double reach = Math.max(halfWidth, length * 0.5) + 1.0;
        AABB box = new AABB(center, center).inflate(reach, 2.5, reach);

        for (LivingEntity target : field.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (owner != null && target == owner) continue;
            if (target instanceof SandSoldierEntity soldier
                    && field.ownerId.equals(soldier.getOwnerId())) continue;

            // Kaba kutu ile arandi, DIKDORTGEN ile dogrulaniyor
            Vec3 rel = target.position().subtract(field.near);
            double alongDist = rel.dot(field.forward);
            double acrossDist = rel.dot(field.side);

            if (alongDist < -0.5 || alongDist > length + 0.5) continue;
            if (Math.abs(acrossDist) > halfWidth) continue;

            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION, SLOW_AMPLIFIER,
                    false, false, true));
        }
    }

    /**
     * Alan gostergesi — INCE KIRMIZI CIZGILERDEN DIKDORTGEN.
     *
     * Cerceve tek uzun cizgi degil parcalardan olusuyor: tek parca
     * egimli arazide havada asili kalirdi, parcalar tek tek zemine
     * oturuyor.
     */
    static void collectShapes(List<SandShapeSyncPacket.Shape> out) {
        int id = 900_000;

        for (Field field : fields) {
            double halfWidth = field.halfWidth();
            double length = field.length();

            float yaw = (float) Math.toDegrees(
                    Math.atan2(-field.forward.x, field.forward.z));

            // Sarkitlar ciktiktan sonra gosterge sonuyor: gosterge
            // "olacak" demek, "oluyor" demek degil
            float pulse = field.ticks >= field.riseStart() ? 0.2f
                    : 0.55f + 0.45f * Mth.sin(field.ticks * 0.45f);

            int along = Math.max(4, (int) (length / 1.2));
            int across = Math.max(2, (int) (halfWidth * 2 / 1.2));

            for (int i = 0; i <= along; i++) {
                double t = i / (double) along;
                Vec3 spine = field.near.add(field.forward.scale(length * t));

                addMark(field, out, id++, spine.add(field.side.scale(halfWidth)),
                        yaw, pulse, 0.9f);
                addMark(field, out, id++, spine.subtract(field.side.scale(halfWidth)),
                        yaw, pulse, 0.9f);
            }

            for (int k = 0; k <= across; k++) {
                double u = k / (double) across - 0.5;
                Vec3 offset = field.side.scale(u * halfWidth * 2);

                addMark(field, out, id++, field.near.add(offset), yaw + 90f, pulse, 0.9f);
                addMark(field, out, id++, field.far.add(offset), yaw + 90f, pulse, 0.9f);
            }
        }
    }

    private static void addMark(Field field, List<SandShapeSyncPacket.Shape> out,
                                int id, Vec3 point, float yaw, float pulse, float len) {
        Vec3 ground = SandSpikeController.groundUnder(field.level, point.add(0, 1.5, 0));
        if (ground == null) ground = point;

        out.add(new SandShapeSyncPacket.Shape(
                id, SandShapeSyncPacket.TYPE_RECT,
                ground.x, ground.y, ground.z,
                yaw, 0f, pulse, len, 0f));
    }

    public static void clear() {
        fields.clear();
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }
}
