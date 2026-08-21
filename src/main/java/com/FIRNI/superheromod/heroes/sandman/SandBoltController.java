package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * MENZILLI KUM ASKERININ MERMISI.
 *
 * Entity degil sunucu tarafi nesne: mermi kisa omurlu ve cok sayida
 * olabiliyor, her biri icin entity kaydi gereksiz yuk olurdu.
 *
 * Mermi YAVAS DEGIL — arkasindaki kum izi hiz hissini veriyor, merminin
 * kendisini yavaslatmak gerekmiyor. Yavas mermi kolayca kacilabilir hale
 * geliyordu.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandBoltController {

    private static final double SPEED = 1.55;
    private static final double MAX_TRAVEL = 42.0;
    private static final float HIT_RADIUS = 0.55f;
    private static final float DAMAGE = 3.5f;

    private static final class Bolt {
        final SandSoldierEntity shooter;
        final ServerLevel level;
        Vec3 pos;
        final Vec3 velocity;
        double travelled = 0;

        Bolt(SandSoldierEntity shooter, ServerLevel level, Vec3 pos, Vec3 velocity) {
            this.shooter = shooter;
            this.level = level;
            this.pos = pos;
            this.velocity = velocity;
        }
    }

    private static final List<Bolt> bolts = new ArrayList<>();

    private SandBoltController() {}

    public static void fire(SandSoldierEntity shooter, Vec3 origin, Vec3 direction) {
        if (!(shooter.level() instanceof ServerLevel level)) return;

        bolts.add(new Bolt(shooter, level, origin, direction.normalize().scale(SPEED)));

        level.playSound(null, shooter.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.2f, 1.5f);
        level.sendParticles(sand(), origin.x, origin.y, origin.z,
                12, 0.2, 0.2, 0.2, 0.06);
    }

    /**
     * KILITLENME ISARETI — sadece ATES ANINDA, hat boyunca tek seferlik.
     *
     * Takip suresince partikul serpilmiyor: hedef hareket ettikce her tick
     * yeni noktalar birakiliyordu ve ekran nokta nokta izlerle doluyordu.
     * Takip cizgisi artik istemcide duz geometri olarak ciziliyor; buradaki
     * nokta nokta iz ise "kilitlendi, ates ediliyor" anini isaretliyor.
     */
    public static void drawLockFlash(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.5) return;

        Vec3 dir = delta.scale(1.0 / length);
        int steps = (int) Math.min(26, length * 1.1);

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 p = from.add(dir.scale(length * t));

            level.sendParticles(ParticleTypes.END_ROD,
                    p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (bolts.isEmpty()) return;
        if (ServerLifecycleHooks.getCurrentServer() == null) return;

        Iterator<Bolt> it = bolts.iterator();
        while (it.hasNext()) {
            Bolt bolt = it.next();

            if (bolt.shooter == null || !bolt.shooter.isAlive()) {
                it.remove();
                continue;
            }

            Vec3 from = bolt.pos;
            Vec3 dir = bolt.velocity.normalize();
            double step = bolt.velocity.length();

            RaycastResult result = RaycastSystem.cast(
                    bolt.level, bolt.shooter, from, dir, step, HIT_RADIUS, false,
                    e -> e instanceof LivingEntity && e != bolt.shooter
                            && bolt.shooter.isValidTarget((LivingEntity) e));

            Vec3 to = result.getHitPosition();

            // KUM IZI — merminin arkasindan dokulen kum
            trail(bolt.level, from, to);

            boolean hit = false;

            if (result.didHitEntity()) {
                for (RaycastResult.EntityHit entityHit : result.getEntityHits()) {
                    if (entityHit.getEntity() instanceof LivingEntity target) {
                        target.hurt(bolt.level.damageSources()
                                .mobAttack(bolt.shooter), DAMAGE);
                        target.hurtMarked = true;
                    }
                }
                hit = true;
            } else if (result.didHitBlock()) {
                hit = true;
            }

            bolt.travelled += from.distanceTo(to);
            bolt.pos = to;

            if (hit || bolt.travelled >= MAX_TRAVEL) {
                burst(bolt.level, to);
                it.remove();
            }
        }
    }

    /**
     * Merminin arkasinda kalan kum izi.
     *
     * Merminin kendisi ucan bir KUM BLOGU olarak okunmali: yogun blok
     * partikulu kutleyi, arkasindaki ince toz ise hizi anlatiyor. Tek basina
     * ince iz birakilinca mermi gorunmuyordu.
     */
    private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int steps = Math.max(3, (int) (delta.length() * 3));

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 p = from.add(delta.scale(t));

            // Kutle: yogun, dagilmayan
            level.sendParticles(sand(), p.x, p.y, p.z, 2, 0.10, 0.10, 0.10, 0.0);

            // Iz: arkaya dogru seyrelen toz
            if (i % 2 == 0) {
                level.sendParticles(sand(), p.x, p.y, p.z,
                        1, 0.22, 0.22, 0.22, 0.02);
            }
        }
    }

    private static void burst(ServerLevel level, Vec3 pos) {
        level.sendParticles(sand(), pos.x, pos.y, pos.z, 20, 0.3, 0.3, 0.3, 0.14);
        level.playSound(null, BlockPos.containing(pos),
                SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 0.9f, 1.1f);
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    }
}
