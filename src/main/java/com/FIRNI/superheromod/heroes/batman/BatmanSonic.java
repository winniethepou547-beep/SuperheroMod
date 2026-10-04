package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.entity.ModEntities;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * The sonic trap on the server (gadget G_SONIC, action SONIC; Batman v Superman reference): he presses the red button
 * of a remote, two sonic emitters (SonicEmitterEntity) come up out of the ground flanking the target and blast it with
 * sound for SONIC_SECONDS: no damage, but every pulse slows it by SONIC_SLOW for SONIC_SLOW_SECONDS (a player's view shakes,
 * their hearing is swamped: BatmanSonicFx). Effects go out as BatmanFxPacket kinds FX_SONIC_FIRST..FX_SONIC_LAST.
 * <p>
 * Placement: the emitters stand either side of the line from him to the target, a little short of the target (toward
 * him), SONIC_DISTANCE*0.55 out from the line, so the target is between them (for a target about ten blocks off they are
 * about SONIC_DISTANCE from him); each on the nearest solid ground with headroom (never floating).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BatmanSonic {
    private BatmanSonic() {}

    // ------------------------------------------------------------------ effect kinds (inside FX_SONIC_FIRST..FX_SONIC_LAST)
    /** The red button pressed (pos = his left hand, entity = Batman, id = the target). */
    public static final int FX_PRESS = FX_SONIC_FIRST;
    /** A pulse (pos = the chamber's face, dir = where it lands, power 1 = it hit the target, entity = the target hit or -1, id = the emitter). */
    public static final int FX_PULSE = FX_SONIC_FIRST + 1;
    /** An emitter hit (pos = where, power 1 = this hit broke it: it shorts out and blows apart, id = the emitter). */
    public static final int FX_DAMAGED = FX_SONIC_FIRST + 2;

    /** How far out from the line to the target (× SONIC_DISTANCE), how far short of the target, a little scatter (blocks). */
    private static final double SIDE_OUT = .55, SHORT_OF = .35, SCATTER = .6;
    /** How far round the wanted spot to look for ground (blocks across, up, down). */
    private static final int SEARCH = 3, SEARCH_UP = 3, SEARCH_DOWN = 6;

    /** What R decided (the target and the two spots), carried out at SONIC_PRESS. */
    private record Plan(LivingEntity target, Vec3 right, Vec3 left) {}
    private static final Map<UUID, Plan> PLANS = new HashMap<>();
    /** Slowed by the pulses: until this game time. */
    private static final class Slowed { final LivingEntity e; long until; Slowed(LivingEntity e, long until) { this.e = e; this.until = until; } }
    private static final Map<Integer, Slowed> SLOWED = new HashMap<>();
    private static final UUID SLOW = UUID.fromString("5b7d0c3e-2c11-4b2e-9a41-7a1c0f9e4d31");

    // ------------------------------------------------------------------ R: the remote
    /** R with the trap picked: picks the target and the spots; nothing to blast or nowhere to stand = no cooldown spent. */
    static boolean use(ServerPlayer p, BatmanController.State s) {
        LivingEntity t = pick(p);
        if (t == null) { BatmanController.tell(p, "Sonik Tuzak: menzilde hedef yok"); return false; }
        Plan plan = plan(p, t);
        if (plan == null) { BatmanController.tell(p, "Sonik Tuzak: yakında uygun zemin yok"); return false; }
        PLANS.put(p.getUUID(), plan);
        BatmanController.set(s, SONIC);
        BatmanController.sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, .35f, 1.4f);
        return true;
    }
    /** Every tick of the SONIC action: at SONIC_PRESS the button goes down and the emitters start coming up. */
    static void tick(ServerPlayer p, BatmanController.State s) {
        if (s.age != SONIC_PRESS) return;
        Plan plan = PLANS.remove(p.getUUID());
        if (plan == null) return;
        // The target may have died or run off since R: pick again; if nothing is left, the cooldown is given back.
        if (!valid(p, plan.target())) {
            LivingEntity t = pick(p);
            plan = t == null ? null : plan(p, t);
            if (plan == null) {
                s.cooldowns[CD_SONIC] = 0;
                BatmanController.tell(p, "Sonik Tuzak: hedef kayboldu");
                return;
            }
        }
        LivingEntity t = plan.target();
        BatmanController.fx(p, FX_PRESS, BatmanController.hand(p, 1), Vec3.ZERO, 0, p.getId(), t.getId());
        int active = Math.max(1, Math.round(BatmanConfig.f(BatmanConfig.SONIC_SECONDS) * 20));
        float health = BatmanConfig.f(BatmanConfig.SONIC_HEALTH);
        spawn(p, t, plan.right(), 1, active, health);
        spawn(p, t, plan.left(), -1, active, health);
    }
    private static void spawn(ServerPlayer p, LivingEntity t, Vec3 at, int side, int active, float health) {
        SonicEmitterEntity e = ModEntities.SONIC_EMITTER.get().create(p.level());
        if (e == null) return;
        Vec3 d = t.position().subtract(at);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        e.moveTo(at.x, at.y, at.z, yaw, 0);
        e.setup(p, t, side, active, health);
        p.serverLevel().addFreshEntity(e);
    }

    // ------------------------------------------------------------------ the target
    private static boolean valid(ServerPlayer p, LivingEntity t) {
        return t != null && t.isAlive() && !t.isRemoved() && t.level() == p.level() && BatmanController.targetable(p, t)
                && t.distanceTo(p) <= BatmanConfig.SONIC_RANGE.get() * 1.25;
    }
    /** The one in his crosshair; else the one nearest his line of sight in a narrow cone; else the nearest enemy or player in range. */
    private static LivingEntity pick(ServerPlayer p) {
        double range = BatmanConfig.SONIC_RANGE.get();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), end = eye.add(look.scale(range));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, eye, stop, new AABB(eye, stop).inflate(1.2),
                e -> e instanceof LivingEntity l && BatmanController.targetable(p, l));
        if (eh != null && eh.getEntity() instanceof LivingEntity l) return l;
        LivingEntity cone = null, near = null;
        double bestCos = .965, bestDist = range;
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), t -> BatmanController.targetable(p, t) && !(t instanceof ArmorStand))) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range || d < .5) continue;
            if (p.level().clip(new ClipContext(eye, t.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            double cos = to.scale(1 / d).dot(look);
            if (cos > bestCos) { bestCos = cos; cone = t; }
            if ((t instanceof Player || t instanceof Enemy) && d < bestDist) { bestDist = d; near = t; }
        }
        return cone != null ? cone : near;
    }

    // ------------------------------------------------------------------ the spots
    /** The two spots flanking the target (his right, his left), or null when there is no ground for one of them. */
    private static Plan plan(ServerPlayer p, LivingEntity t) {
        ServerLevel level = p.serverLevel();
        Vec3 from = p.position(), to = t.position();
        Vec3 dir = BatmanController.flat(to.subtract(from));
        double dist = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
        double spread = BatmanConfig.SONIC_DISTANCE.get();
        double along = Math.max(1.5, dist - spread * SHORT_OF), out = spread * SIDE_OUT;
        // His right when facing along dir (Minecraft: facing +z, the right is -x).
        Vec3 right = new Vec3(-dir.z, 0, dir.x);
        var rnd = p.getRandom();
        Vec3[] spots = new Vec3[2];
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? 1 : -1;
            double a = along + (rnd.nextDouble() - .5) * 2 * SCATTER, o = out + (rnd.nextDouble() - .5) * 2 * SCATTER;
            Vec3 want = from.add(dir.scale(a)).add(right.scale(side * o)).add(0, to.y - from.y, 0).add(0, .01, 0);
            spots[i] = ground(level, want, i == 1 ? spots[0] : null);
            if (spots[i] == null) return null;
        }
        return new Plan(t, spots[0], spots[1]);
    }
    /** The nearest block top round a spot that is solid, dry and has two blocks of room above it (the block's middle), or null. */
    private static Vec3 ground(ServerLevel level, Vec3 want, Vec3 taken) {
        BlockPos base = BlockPos.containing(want);
        int[] heights = new int[SEARCH_UP + SEARCH_DOWN + 1];
        // Nearest height first: 0, +1, -1, +2, -2 ...
        for (int i = 0, k = 0; k < heights.length; i++) {
            if (i <= SEARCH_DOWN && k < heights.length) heights[k++] = -i;
            if (i > 0 && i <= SEARCH_UP && k < heights.length) heights[k++] = i;
        }
        for (int r = 0; r <= SEARCH; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : heights) {
                        BlockPos pos = base.offset(dx, dy - 1, dz);
                        if (!fits(level, pos)) continue;
                        Vec3 at = new Vec3(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
                        if (taken != null && at.distanceToSqr(taken) < 2.5 * 2.5) continue;
                        return at;
                    }
                }
        return null;
    }
    private static boolean fits(ServerLevel level, BlockPos ground) {
        if (!level.isLoaded(ground)) return false;
        var state = level.getBlockState(ground);
        if (!state.isFaceSturdy(level, ground, Direction.UP)) return false;
        for (int up = 1; up <= 2; up++) {
            BlockPos pos = ground.above(up);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ what the emitters do
    /** A pulse landed on them: slowed (never frozen) for SONIC_SLOW_SECONDS from now. No damage at all. */
    static void blast(LivingEntity t) {
        long until = t.level().getGameTime() + Math.max(1, Math.round(BatmanConfig.f(BatmanConfig.SONIC_SLOW_SECONDS) * 20));
        Slowed s = SLOWED.get(t.getId());
        if (s == null || s.e != t) { SLOWED.put(t.getId(), new Slowed(t, until)); slow(t, true); }
        else s.until = Math.max(s.until, until);
    }
    /** An emitter was hit (broke: it shorts out and blows apart; its clients play that from the synced data and this packet). */
    static void damaged(SonicEmitterEntity e, Vec3 at, boolean broke) {
        send(e, FX_DAMAGED, at, Vec3.ZERO, broke ? 1 : 0, -1);
    }
    /** An effect of one emitter to everyone near it. */
    static void send(SonicEmitterEntity e, int kind, Vec3 pos, Vec3 dir, float power, int entity) {
        Vec3 at = e.position();
        ModNetworking.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 96, e.level().dimension())),
                new BatmanFxPacket(kind, pos, dir, power, entity, e.getId()));
    }

    private static void slow(LivingEntity e, boolean on) {
        AttributeInstance a = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        a.removeModifier(SLOW);
        if (on) a.addTransientModifier(new AttributeModifier(SLOW, "Batman sonic trap", -Mth.clamp(BatmanConfig.SONIC_SLOW.get(), 0, .95), AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SLOWED.isEmpty()) return;
        for (Iterator<Slowed> it = SLOWED.values().iterator(); it.hasNext(); ) {
            Slowed s = it.next();
            if (s.e.isRemoved() || !s.e.isAlive() || s.e.level().getGameTime() >= s.until) {
                it.remove();
                if (!s.e.isRemoved()) slow(s.e, false);
            }
        }
    }
    @SubscribeEvent public static void died(LivingDeathEvent e) { if (SLOWED.remove(e.getEntity().getId()) != null) slow(e.getEntity(), false); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        PLANS.remove(e.getEntity().getUUID());
        if (SLOWED.remove(e.getEntity().getId()) != null) slow(e.getEntity(), false);
    }
}
