package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.iceman.IcemanConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The cold he carries: as he walks, the air behind him freezes into a light trail of the brush's vapour (the same
 * blue-white mist and a few glinting ice crystals as IcemanBrushFx's plume, far less of it). It rolls off his back and
 * legs, is left behind, drags a little after him and sinks as it thins (cold air falls). Its amount follows his speed and
 * is eased in and out, so it never pops on or off when he starts, stops or jumps. Not while the brush, a slide or the dash
 * already makes their own cold. Everyone sees it; his own is kept low behind him so it never fogs his view.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanWalkFx {
    private IcemanWalkFx() {}

    /** Horizontal speed (blocks a tick) at which the trail is full: about a walk. */
    private static final double FULL_SPEED = .2;
    /** Each Iceman's trail strength (eased), and the fraction of a puff carried to the next tick. */
    private static final Map<Integer, float[]> TRAILS = new HashMap<>();
    private static ClientLevel lastLevel;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { TRAILS.clear(); lastLevel = mc.level; }
        if (mc.level == null || mc.isPaused()) return;
        double amount = IcemanConfig.WALK_MIST.get();
        for (var en : IcemanClient.states().entrySet()) {
            int id = en.getKey();
            Entity who = mc.level.getEntity(id);
            if (who == null || !IcemanClient.isHero(who) || who.isSpectator()) continue;
            TRAILS.computeIfAbsent(id, k -> new float[2]);
        }
        for (Iterator<Map.Entry<Integer, float[]>> it = TRAILS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            int id = en.getKey();
            float[] t = en.getValue();
            Entity who = mc.level.getEntity(id);
            IcemanClient.State s = IcemanClient.states().get(id);
            if (who == null || s == null || !IcemanClient.isHero(who)) { it.remove(); continue; }
            boolean me = IcemanClient.isMe(id);
            boolean busy = s.action == BRUSH || s.action == SLIDE || s.action == DASH
                    || (me && (IcemanSlideSteer.riding() || IcemanSlideSteer.dashing()));
            double dx = who.getX() - who.xo, dz = who.getZ() - who.zo;
            double speed = Math.sqrt(dx * dx + dz * dz);
            float want = busy || who.isInWater() || amount <= 0 ? 0 : (float) Math.min(1, speed / FULL_SPEED);
            t[0] += (want - t[0]) * (want > t[0] ? .18f : .1f);
            if (t[0] < .02f) { t[1] = 0; continue; }
            emit(who, t, (float) amount, new Vec3(dx, 0, dz), me && mc.options.getCameraType().isFirstPerson());
        }
    }

    /**
     * One tick of the trail: about one puff of vapour every two ticks at a walk (the brush sends three or four a tick),
     * fainter and smaller than the brush's, and now and then an ice crystal glinting out.
     */
    private static void emit(Entity who, float[] t, float amount, Vec3 vel, boolean ownView) {
        float k = t[0];
        float yaw = (who instanceof LivingEntity le ? le.yBodyRot : who.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
        Vec3 side = new Vec3(-back.z, 0, back.x);
        Vec3 feet = who.position();
        double top = ownView ? .85 : 1.25;
        t[1] += .55f * k * amount;
        int puffs = (int) t[1];
        t[1] -= puffs;
        puffs = Math.min(puffs, IceParticles.count(puffs, feet));
        for (int i = 0; i < puffs; i++) {
            double up = .15 + (top - .15) * IceParticles.rand();
            Vec3 at = feet.add(back.scale(.22 + .12 * IceParticles.rand())).add(side.scale((IceParticles.rand() - .5) * .5)).add(0, up, 0);
            Vec3 v = vel.scale(.25).add(back.scale(.012 + .012 * IceParticles.rand())).add(IceParticles.jitter(.006)).add(0, -.006 - .004 * IceParticles.rand(), 0);
            IceParticles.mist(at, v, .16f + .1f * IceParticles.rand(), .016f, (.08f + .05f * k) * Math.min(1, amount), 28 + (int) (IceParticles.rand() * 18));
        }
        if (IceParticles.rand() < .3f * k * amount && IceParticles.count(1, feet) > 0) {
            Vec3 at = feet.add(back.scale(.25)).add(side.scale((IceParticles.rand() - .5) * .45)).add(0, .2 + (top - .2) * IceParticles.rand(), 0);
            IceParticles.crystalDust(at, vel.scale(.2).add(IceParticles.jitter(.012)).add(0, -.004, 0), .014f + .012f * IceParticles.rand(),
                    (IceParticles.rand() - .5f) * .4f, 14 + (int) (IceParticles.rand() * 12));
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { TRAILS.clear(); lastLevel = null; }
}
