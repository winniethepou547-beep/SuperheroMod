package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Seekable camera evaluation. No previous frame or wall-clock delta is an input. */
public final class CinematicCameraSampler {
    private CinematicCameraSampler() {}
    public record Actors(Vec3 attacker, Vec3 target) {}
    @FunctionalInterface public interface ActorSource { Actors at(float tick); }
    public record Fog(boolean active, float near, float far, float r, float g, float b) {}
    public record Frame(Vec3 position, Vec3 look, float fov, float roll, Fog fog) {}

    public static Frame sample(CinematicDefinition def, StageFrame stage, float tick, ActorSource actors) {
        if (!Float.isFinite(tick)) throw new IllegalArgumentException("Invalid camera time");
        tick = Mth.clamp(tick, 0, def.totalTicks);
        var cursor = def.cursorAt(tick);
        Frame current = raw(def, stage, cursor.shot(), cursor.progress(), tick, actors);
        if (cursor.index() == 0 || cursor.shot().transition == Shot.Transition.CUT) return current;
        // Finish within this shot, including very short shots. Consequently the outgoing
        // endpoint is always its authored endpoint, not an unfinished recursive blend.
        float duration = Math.min(cursor.shot().durationTicks, cursor.shot().transitionSeconds * 20f);
        float t = Mth.clamp(cursor.localTick() / duration, 0, 1);
        if (t >= 1) return current;
        Frame previous = raw(def, stage, def.shots.get(cursor.index() - 1), 1,
                tick - cursor.localTick(), actors);
        t = t * t * (3 - 2 * t);
        return new Frame(previous.position.lerp(current.position, t), previous.look.lerp(current.look, t),
                Mth.lerp(t, previous.fov, current.fov),
                previous.roll + Mth.wrapDegrees(current.roll - previous.roll) * t,
                blendFog(previous.fog, current.fog, t));
    }

    private static Frame raw(CinematicDefinition def, StageFrame stage, Shot shot,
                             float progress, float tick, ActorSource source) {
        float p = shot.easing.apply(progress);
        Actors actors = source.at(tick);
        Vec3 look = switch (shot.lookTarget) {
            case ATTACKER -> actors.attacker.add(shot.lookOffset);
            case TARGET -> actors.target.add(shot.lookOffset);
            case MIDPOINT -> actors.attacker.lerp(actors.target, .5).add(shot.lookOffset);
            case FIXED -> stage.toWorldSpan(shot.lookOffset);
        };
        Vec3 position = stage.toWorldSpan(shot.positionAt(p)).add(CinematicCameraMotion.offset(
                tick, Mth.lerp(p, shot.shakeStart, shot.shakeEnd), shot.breath));
        boolean own = shot.hasFog();
        float near = own ? Mth.lerp(p, shot.fogNearStart, shot.fogNearEnd) : def.baseFogNear;
        float far = own ? Mth.lerp(p, shot.fogFarStart, shot.fogFarEnd) : def.baseFogFar;
        int color = own && shot.fogColor != Shot.NO_COLOR ? shot.fogColor : def.baseFogColor;
        Fog fog = new Fog(Float.isFinite(near) && Float.isFinite(far), near, far,
                color == Shot.NO_COLOR ? -1 : ((color >> 16) & 255) / 255f,
                color == Shot.NO_COLOR ? -1 : ((color >> 8) & 255) / 255f,
                color == Shot.NO_COLOR ? -1 : (color & 255) / 255f);
        return new Frame(position, look, Mth.lerp(p, shot.fovStart, shot.fovEnd),
                Mth.lerp(p, shot.rollStart, shot.rollEnd), fog);
    }

    private static Fog blendFog(Fog a, Fog b, float t) {
        // Unspecified atmosphere belongs to the world renderer; never retain a stale
        // color or invent world fog distances to blend from/to.
        if (t == 0) return a;
        if (!a.active || !b.active) return b;
        boolean colors = a.r >= 0 && b.r >= 0;
        return new Fog(true, Mth.lerp(t, a.near, b.near), Mth.lerp(t, a.far, b.far),
                colors ? Mth.lerp(t, a.r, b.r) : b.r,
                colors ? Mth.lerp(t, a.g, b.g) : b.g,
                colors ? Mth.lerp(t, a.b, b.b) : b.b);
    }
}
