package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Isinin izleyecegi yolu kurar:
 *  - yuzeye carparsa gelis acisiyla seker
 *  - canliya carparsa 10 blok icindeki en yakin baska canliya kilitlenir
 *
 * Sonuc hem gorsel yol (noktalar) hem de vurulan hedefler.
 */
public final class BeamChainBuilder {

    public static final double SEEK_RADIUS = 10.0;

    public static final class Chain {
        public final List<Vec3> path = new ArrayList<>();
        public final List<LivingEntity> hits = new ArrayList<>();
    }

    private BeamChainBuilder() {}

    /**
     * @param maxBounces  yuzeyden kac kez sekebilir
     * @param maxChains   kac canliya zincirlenebilir
     */
    public static Chain build(ServerPlayer shooter, Vec3 origin, Vec3 direction,
                              double range, float radius,
                              int maxBounces, int maxChains) {
        Level level = shooter.level();
        Chain chain = new Chain();
        chain.path.add(origin);

        Set<UUID> alreadyHit = new HashSet<>();
        Vec3 pos = origin;
        Vec3 dir = direction;

        int bounces = 0;
        int chains = 0;
        int guard = 0;

        while (guard++ < 12) {
            RaycastResult result = RaycastSystem.cast(
                    level, shooter, pos, dir, range, radius, false,
                    e -> e instanceof LivingEntity && e != shooter
                            && !alreadyHit.contains(e.getUUID()));

            Vec3 hitPos = result.getHitPosition();
            chain.path.add(hitPos);

            // 1) Canliya carptik -> kaydet, sonra yakindaki baska canliya kilitlen
            LivingEntity struck = firstLiving(result, alreadyHit);
            if (struck != null) {
                alreadyHit.add(struck.getUUID());
                chain.hits.add(struck);

                if (chains >= maxChains) break;

                LivingEntity next = findNearest(level, shooter,
                        struck.getEyePosition(), alreadyHit);
                if (next == null) break;

                Vec3 from = struck.getEyePosition();
                Vec3 to = next.getEyePosition();

                chain.path.add(from);
                pos = from;
                dir = to.subtract(from).normalize();
                range = from.distanceTo(to) + 1.0;
                chains++;
                continue;
            }

            // 2) Yuzeye carptik -> gelis acisiyla sek
            if (result.didHitBlock() && result.getBlockFace() != null && bounces < maxBounces) {
                // Once sekme noktasi civarinda canli var mi diye bak
                LivingEntity near = findNearest(level, shooter, hitPos, alreadyHit);
                if (near != null && chains < maxChains) {
                    Vec3 to = near.getEyePosition();
                    chain.path.add(to);
                    chain.hits.add(near);
                    alreadyHit.add(near.getUUID());
                    chains++;

                    pos = to;
                    dir = to.subtract(hitPos).normalize();
                    range = SEEK_RADIUS;
                    continue;
                }

                pos = hitPos.add(Vec3.atLowerCornerOf(
                        result.getBlockFace().getNormal()).scale(0.05));
                dir = RaycastSystem.reflect(dir, result.getBlockFace());
                bounces++;
                continue;
            }

            break;
        }

        return chain;
    }

    /** Verilen noktanin 10 blok yaricapindaki en yakin uygun canli. */
    public static LivingEntity findNearest(Level level, ServerPlayer shooter,
                                           Vec3 from, Set<UUID> exclude) {
        AABB box = new AABB(from, from).inflate(SEEK_RADIUS);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != shooter && e.isAlive() && !exclude.contains(e.getUUID()));

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double d = e.getEyePosition().distanceTo(from);
            if (d <= SEEK_RADIUS && d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** Bir noktadan yaricap icindeki TUM uygun canlilar (dallanma icin). */
    public static List<LivingEntity> findAllNear(Level level, ServerPlayer shooter,
                                                 Vec3 from, Set<UUID> exclude, int limit) {
        AABB box = new AABB(from, from).inflate(SEEK_RADIUS);
        List<LivingEntity> candidates = new ArrayList<>(
                level.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != shooter && e.isAlive() && !exclude.contains(e.getUUID())));

        candidates.removeIf(e -> e.getEyePosition().distanceTo(from) > SEEK_RADIUS);
        candidates.sort(Comparator.comparingDouble(e -> e.getEyePosition().distanceTo(from)));

        return candidates.size() > limit ? candidates.subList(0, limit) : candidates;
    }

    private static LivingEntity firstLiving(RaycastResult result, Set<UUID> exclude) {
        for (RaycastResult.EntityHit hit : result.getEntityHits()) {
            if (hit.getEntity() instanceof LivingEntity le
                    && !exclude.contains(le.getUUID())) {
                return le;
            }
        }
        return null;
    }
}
