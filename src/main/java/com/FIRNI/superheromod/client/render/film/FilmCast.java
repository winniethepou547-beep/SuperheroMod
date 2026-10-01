package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.render.puppet.CinematicPuppet;
import com.FIRNI.superheromod.client.render.puppet.PuppetRenderer;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * A performer on a film stage. Players and zombie-shaped mobs become a jointed puppet in their
 * own skin, so the film can bend them at waist, elbows and knees; anything else is drawn with
 * its own model and can only be tilted as a whole.
 */
public final class FilmCast {
    private final Entity source;
    private final CinematicPuppet puppet;

    private FilmCast(Entity source, CinematicPuppet puppet) { this.source = source; this.puppet = puppet; }

    public static FilmCast of(Entity entity) {
        if (entity == null) return null;
        if (entity instanceof AbstractClientPlayer player)
            return new FilmCast(entity, new CinematicPuppet(player.getUUID(), player.getSkinTextureLocation(), "slim".equals(player.getModelName())));
        if (entity instanceof Zombie zombie && !(entity instanceof ZombifiedPiglin)) {
            var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(zombie);
            var puppet = new CinematicPuppet(entity.getUUID(), renderer.getTextureLocation(zombie), false);
            puppet.legacyZombieUv = true;
            puppet.baseScale = zombie.isBaby() ? .5f : 1f;
            return new FilmCast(entity, puppet);
        }
        return new FilmCast(entity, null);
    }
    public Entity entity() { return source; }
    public boolean jointed() { return puppet != null; }
    public float height() { return puppet != null ? 1.85f * puppet.baseScale : source.getBbHeight(); }

    /** Draws the performer standing at feet, facing yaw (0 = +z), lit with the colour tint. */
    public void draw(FilmContext c, Vec3 feet, float yaw, ActorPose pose, float scale, int tint) {
        float r = (tint >> 16 & 255) / 255f, g = (tint >> 8 & 255) / 255f, b = (tint & 255) / 255f;
        if (puppet != null) {
            // The rig lowers its own hips by the crouch; the feet stay where they are.
            puppet.position = feet;
            puppet.yaw = yaw;
            puppet.pose.set(pose);
            puppet.scale = scale * puppet.baseScale;
            puppet.tintR = r; puppet.tintG = g; puppet.tintB = b;
            PuppetRenderer.drawStaged(c.pose(), c.buffers(), puppet);
            return;
        }
        // Anything with a head, a body and arms plays the pose on its own model, limb by limb.
        if (source instanceof LivingEntity living && FilmPosedModel.draw(c, living, feet, yaw, pose, scale, tint)) return;
        // Other creatures act it out with the whole body: leaning with the chest, sinking with the
        // crouch, twisting with the shoulders.
        float chest = (float) Math.toDegrees(pose.rot[ActorPose.CHEST][0] + pose.rot[ActorPose.HIPS][0]);
        float twist = (float) Math.toDegrees(pose.rot[ActorPose.CHEST][1]);
        c.pose().pushPose();
        c.pose().translate(feet.x, feet.y, feet.z);
        c.pose().scale(1, Math.max(.5f, 1 - pose.crouch * .6f), 1);
        c.pose().translate(0, source.getBbHeight() * .5 * scale, 0);
        c.pose().mulPose(Axis.YP.rotationDegrees(-yaw));
        c.pose().mulPose(Axis.XP.rotationDegrees(-pose.bodyPitch + chest * .7f));
        c.pose().mulPose(Axis.ZP.rotationDegrees(pose.bodyRoll + twist * .25f));
        c.pose().mulPose(Axis.YP.rotationDegrees(yaw));
        c.pose().translate(0, -source.getBbHeight() * .5 * scale, 0);
        actor(c, source, yaw, scale);
        c.pose().popPose();
    }

    /** Draws a real entity at the pose origin with temporary rotations; shadows and hurt flash off. */
    public static void actor(FilmContext c, Entity entity, float facing, float scale) {
        var mc = Minecraft.getInstance();
        var dispatcher = mc.getEntityRenderDispatcher();
        float yRot = entity.getYRot(), yRotO = entity.yRotO, xRot = entity.getXRot(), xRotO = entity.xRotO;
        float body = 0, bodyO = 0, head = 0, headO = 0;
        int hurt = 0;
        if (entity instanceof LivingEntity living) {
            body = living.yBodyRot; bodyO = living.yBodyRotO; head = living.yHeadRot; headO = living.yHeadRotO; hurt = living.hurtTime;
            living.yBodyRot = living.yBodyRotO = living.yHeadRot = living.yHeadRotO = facing;
            living.hurtTime = 0;
        }
        entity.setYRot(facing); entity.yRotO = facing; entity.setXRot(0); entity.xRotO = 0;
        dispatcher.setRenderShadow(false);
        try {
            c.pose().pushPose();
            c.pose().scale(scale, scale, scale);
            dispatcher.render(entity, 0, 0, 0, facing, c.partial(), c.pose(), c.buffers(), 15728880);
            c.pose().popPose();
        } finally {
            dispatcher.setRenderShadow(mc.options.entityShadows().get());
            entity.setYRot(yRot); entity.yRotO = yRotO; entity.setXRot(xRot); entity.xRotO = xRotO;
            if (entity instanceof LivingEntity living) {
                living.yBodyRot = body; living.yBodyRotO = bodyO; living.yHeadRot = head; living.yHeadRotO = headO; living.hurtTime = hurt;
            }
        }
    }

    /**
     * Pose keys over film time. Each key is reached at its time, after blending from the pose
     * before it over its own blend length; per-joint delays let force travel through the body
     * (a hit lands in the chest first, the feet last).
     */
    public static final class Track {
        private record Key(float time, float blend, ActorPose pose, float[] delays) {}
        private final List<Key> keys = new ArrayList<>();
        private final ActorPose scratch = new ActorPose();
        public Track key(float time, float blend, ActorPose pose) { return key(time, blend, pose, null); }
        public Track key(float time, float blend, ActorPose pose, float[] delays) { keys.add(new Key(time, blend, pose, delays)); return this; }
        public ActorPose sample(float t) {
            if (keys.isEmpty()) { scratch.reset(); return scratch; }
            Key previous = keys.get(0);
            if (t <= previous.time) { scratch.set(previous.pose); return scratch; }
            for (int i = 1; i < keys.size(); i++) {
                Key next = keys.get(i);
                float start = Math.max(previous.time, next.time - next.blend);
                if (t < next.time) {
                    if (t <= start) { scratch.set(previous.pose); return scratch; }
                    ActorPose.lerp(previous.pose, next.pose, (t - start) / Math.max(.001f, next.time - start), next.delays, scratch);
                    return scratch;
                }
                previous = next;
            }
            scratch.set(previous.pose);
            return scratch;
        }
    }
}
