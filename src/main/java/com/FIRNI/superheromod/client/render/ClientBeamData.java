package com.FIRNI.superheromod.client.render;

import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientBeamData {

    private static final Map<UUID, ChannelBeam> channelBeams = new ConcurrentHashMap<>();
    private static final List<FlashBeam> flashBeams = Collections.synchronizedList(new ArrayList<>());

    public static class ChannelBeam {
        public List<Vec3> path = List.of();
        public float widthMultiplier;
        public int noUpdateTicks = 0;
        public float fadeAlpha = 0f;
    }

    public static class FlashBeam {
        public final List<Vec3> path;
        public final float widthMultiplier;
        public int ticksRemaining;
        public final int maxTicks;
        public final boolean longFade;

        public FlashBeam(List<Vec3> path, float widthMult, int durationTicks, boolean longFade) {
            this.path = path;
            this.widthMultiplier = widthMult;
            this.ticksRemaining = durationTicks;
            this.maxTicks = durationTicks;
            this.longFade = longFade;
        }
    }

    public static void setChannelBeam(UUID playerId, List<Vec3> path, float widthMult) {
        ChannelBeam beam = channelBeams.computeIfAbsent(playerId, k -> new ChannelBeam());
        beam.path = path;
        beam.widthMultiplier = widthMult;
        beam.noUpdateTicks = 0;
    }

    public static void clearChannelBeam(UUID playerId) {
        ChannelBeam beam = channelBeams.get(playerId);
        if (beam != null) {
            beam.fadeAlpha = -1f;
        }
    }

    public static void addFlashBeam(List<Vec3> path, float widthMult,
                                    int durationTicks, boolean longFade) {
        if (path.size() < 2) return;
        flashBeams.add(new FlashBeam(path, widthMult, durationTicks, longFade));
    }

    public static Map<UUID, ChannelBeam> getChannelBeams() {
        return channelBeams;
    }

    public static List<FlashBeam> getFlashBeams() {
        return flashBeams;
    }

    public static void tick() {
        synchronized (flashBeams) {
            flashBeams.removeIf(fb -> {
                fb.ticksRemaining--;
                return fb.ticksRemaining <= 0;
            });
        }

        Iterator<Map.Entry<UUID, ChannelBeam>> it = channelBeams.entrySet().iterator();
        while (it.hasNext()) {
            ChannelBeam cb = it.next().getValue();
            cb.noUpdateTicks++;

            if (cb.fadeAlpha < 0) {
                it.remove();
                continue;
            }

            if (cb.fadeAlpha < 1.0f) {
                cb.fadeAlpha = Math.min(1.0f, cb.fadeAlpha + 0.3f);
            }

            if (cb.noUpdateTicks > 5) {
                it.remove();
            }
        }
    }

    public static void clear() {
        channelBeams.clear();
        flashBeams.clear();
    }
}
