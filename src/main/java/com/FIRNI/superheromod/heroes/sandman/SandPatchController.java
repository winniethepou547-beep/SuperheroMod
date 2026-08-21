package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * KUM ALANLARI — yeteneklerin degdigi yerde kalan kum.
 *
 * Asker olurken yere gercek kum bloklari saciliyordu ve bu goruntu
 * kullaniciya "kumun izi kalmali" fikrini verdi. Ancak alanlar GERCEK BLOK
 * yazmiyor: harita uzerinde kalici tahribat birakmak PvP haritalarinda
 * kabul edilemez. Alan sadece bir konum + yaricap kaydi; gorseli partikul,
 * etkisi ise dogrudan uygulanan yavaslatma.
 *
 * KRITIK KURAL: kum, Sandman ONA YAKINKEN yavaslatir.
 *
 * Bu bilerek boyle. Kum kendi basina tuzak degil, Sandman'in KONTROL
 * ETTIGI arazi. Uzaklasinca kum olu bir dekora donusuyor, geri gelince
 * tekrar canlaniyor — boylece oyuncu alanlarini "tasiyarak" savasiyor,
 * bir kere serpip unutmuyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandPatchController {

    /** Sandman bu mesafedeyse kum canlidir. */
    private static final double CONTROL_RANGE = 10.0;
    private static final double CONTROL_RANGE_SQR = CONTROL_RANGE * CONTROL_RANGE;

    /** Yavaslatma etkisi surekli tazeleniyor; kisa tutmak yeterli. */
    private static final int SLOW_DURATION = 25;
    private static final int SLOW_AMPLIFIER = 1;    // Slowness II

    private static final int DEFAULT_LIFETIME = 200;

    private static final class Patch {
        final UUID ownerId;
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        /** Cizim kimligi -- kareler arasi yumusatma bunun uzerinden. */
        final int id;
        /** Sekizgenin donusu; her yama ayni acida olunca dizilim yapay duruyordu. */
        final float angle;
        int ticksLeft;
        /** Sandman menzilde mi — gorsel yogunlugu buna gore degisiyor. */
        boolean active;

        Patch(UUID ownerId, ServerLevel level, Vec3 center, double radius, int lifetime) {
            this.ownerId = ownerId;
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.ticksLeft = lifetime;
            this.id = NEXT_ID++;
            this.angle = (id * 37) % 360;
        }
    }

    private static final List<Patch> patches = new ArrayList<>();
    private static int NEXT_ID = 500_000;

    private SandPatchController() {}

    /** Yeteneklerin carptigi yere kum birakmasi icin tek giris noktasi. */
    public static void drop(ServerPlayer owner, Vec3 center, double radius) {
        drop(owner, center, radius, DEFAULT_LIFETIME);
    }

    public static void drop(ServerPlayer owner, Vec3 center, double radius, int lifetime) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        if (radius <= 0) return;

        // Zemini GERCEKTEN kuma cevir. Cizilen katman denendi ve
        // reddedildi: uzerine yatirilmis saydam bir doku gibi duruyordu,
        // arazinin kendisi degismiyordu. Kum izi kalici ve gercek.
        paveWithSand(level, center, radius);

        // Ayni yere ust uste yigilmasin: yakin alan varsa onu tazele
        for (Patch patch : patches) {
            if (patch.level == level && patch.ownerId.equals(owner.getUUID())
                    && patch.center.distanceToSqr(center) < 1.5) {
                patch.ticksLeft = Math.max(patch.ticksLeft, lifetime);
                return;
            }
        }

        patches.add(new Patch(owner.getUUID(), level, center, radius, lifetime));
    }

    /**
     * Yuzeydeki bloklari kumla degistirir.
     *
     * Sadece EN UST katman degisiyor ve sadece SAGLAM zemin: havada
     * duran bitki, kapi, sandik gibi seyler kuma cevrilseydi yetenek
     * arazi boyama degil yikim olurdu. Derine inilmiyor -- kum bir
     * KAPLAMA, bir krater degil.
     */
    private static void paveWithSand(ServerLevel level, Vec3 center, double radius) {
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                // Kare tarandi, DAIRE ile suzuluyor: kose bloklari da
                // kuma donerse yama kare gorunuyordu
                if (dx * dx + dz * dz > radius * radius) continue;

                // Zemini bul: merkez yuksekligi egimli arazide her sutun
                // icin dogru olmayabilir, o yuzden birkac blok aranıyor
                BlockPos surface = findSurface(level, origin.offset(dx, 0, dz));
                if (surface == null) continue;

                if (!isPaveable(level, surface)) continue;
                level.setBlockAndUpdate(surface, Blocks.SAND.defaultBlockState());
            }
        }
    }

    /** Verilen sutunda, ustunde hava olan en yakin saglam blok. */
    private static BlockPos findSurface(ServerLevel level, BlockPos around) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = around.offset(0, dy, 0);
            if (level.getBlockState(pos).isAir()) continue;
            if (!level.getBlockState(pos.above()).isAir()
                    && !level.getBlockState(pos.above()).canBeReplaced()) continue;
            return pos;
        }
        return null;
    }

    /**
     * Hangi bloklar kuma cevrilebilir.
     *
     * Beyaz liste degil kara liste kullaniliyor cunku harita her turlu
     * bloktan olusabilir; ama KIRILMAZ ve OYUNCU YAPISI olan seyler
     * korunuyor.
     */
    private static boolean isPaveable(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.is(Blocks.SAND)) return false;              // zaten kum

        // Kirilmaz bloklar (bedrock, barrier...) dokunulmaz
        if (state.getDestroySpeed(level, pos) < 0) return false;

        // Blok varliklari: sandik, firin, tabela... oyuncunun esyasi
        if (state.hasBlockEntity()) return false;

        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (patches.isEmpty()) return;
        if (ServerLifecycleHooks.getCurrentServer() == null) return;

        Iterator<Patch> it = patches.iterator();
        while (it.hasNext()) {
            Patch patch = it.next();

            if (--patch.ticksLeft <= 0) {
                dissolve(patch);
                it.remove();
                continue;
            }

            ServerPlayer owner = patch.level.getServer()
                    .getPlayerList().getPlayer(patch.ownerId);

            patch.active = owner != null
                    && owner.isAlive()
                    && owner.level() == patch.level
                    && owner.position().distanceToSqr(patch.center) <= CONTROL_RANGE_SQR;

            if (patch.active) applySlow(patch, owner);

        }
    }

    private static void applySlow(Patch patch, ServerPlayer owner) {
        AABB box = new AABB(
                patch.center.x - patch.radius, patch.center.y - 1.2, patch.center.z - patch.radius,
                patch.center.x + patch.radius, patch.center.y + 2.0, patch.center.z + patch.radius);

        for (LivingEntity target : patch.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target == owner) continue;
            if (target instanceof SandSoldierEntity soldier
                    && owner.getUUID().equals(soldier.getOwnerId())) continue;

            // Kare kutu ile arandi, DAIRE ile dogrulaniyor: kose kesitlerinde
            // kumun disinda kalanlar da yavaslardi
            double dx = target.getX() - patch.center.x;
            double dz = target.getZ() - patch.center.z;
            if (dx * dx + dz * dz > patch.radius * patch.radius) continue;

            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION, SLOW_AMPLIFIER,
                    false, false, true));
        }
    }

    /**
     * Alanin YAVASLATMASI biter.
     *
     * Yere serilen kum KALICI: arazi gercekten degisti ve geri alinmiyor.
     * Biten sey sadece Sandman kontrolu -- kum artik siradan zemin.
     */
    private static void dissolve(Patch patch) {
        patch.level.sendParticles(sand(),
                patch.center.x, patch.center.y + 0.4, patch.center.z,
                (int) (patch.radius * 6), patch.radius * 0.5, 0.1, patch.radius * 0.5, 0.02);
    }

    /** Sunucu kapanirken kalinti kalmasin. */
    public static void clear() {
        patches.clear();
    }

    /** Havada suzulen kum — FALLING_DUST blok durumu ister. */
    private static BlockParticleOption fallingSand() {
        return new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }
}
