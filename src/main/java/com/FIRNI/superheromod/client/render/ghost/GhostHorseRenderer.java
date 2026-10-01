package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.UndeadHorseRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Any horse ridden by the Ghost Rider becomes a burning skeleton horse. */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class GhostHorseRenderer {
    private static final ResourceLocation BONES = new ResourceLocation("minecraft", "textures/entity/horse/horse_skeleton.png");
    private static UndeadHorseRenderer skeleton;
    private static boolean drawing;

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
            skeleton = new UndeadHorseRenderer(e.getContext(), ModelLayers.SKELETON_HORSE) {
                @Override public ResourceLocation getTextureLocation(AbstractHorse horse) { return BONES; }
            };
        }
    }
    private static boolean ridden(AbstractHorse horse) {
        for (var passenger : horse.getPassengers())
            if (passenger instanceof Player player && "ghost_rider".equals(ClientHeroRegistry.get(player.getUUID()))) return true;
        return false;
    }
    @SubscribeEvent public static void render(RenderLivingEvent.Pre<?, ?> e) {
        if (drawing || skeleton == null || !(e.getEntity() instanceof AbstractHorse horse) || !ridden(horse)) return;
        e.setCanceled(true);
        float partial = e.getPartialTick();
        drawing = true;
        try {
            skeleton.render(horse, Mth.rotLerp(partial, horse.yRotO, horse.getYRot()), partial, e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight());
        } finally { drawing = false; }
        flames(horse, partial, e.getPoseStack(), e);
    }
    /** Mane, head, tail and hooves burn; the fire bends back with the gallop. */
    private static void flames(AbstractHorse horse, float partial, PoseStack pose, RenderLivingEvent.Pre<?, ?> e) {
        float body = Mth.rotLerp(partial, horse.yBodyRotO, horse.yBodyRot);
        Vec3 forward = Vec3.directionFromRotation(0, body), right = forward.cross(new Vec3(0, 1, 0));
        float time = horse.tickCount + partial;
        Vec3 velocity = horse.getDeltaMovement();
        double run = Math.min(1, velocity.horizontalDistance() * 4);
        float swing = (float) Math.sin(horse.walkAnimation.position(partial) * .66f) * (float) run;
        Vec3[] points = {
                forward.scale(.62).add(0, 1.78, 0), forward.scale(.78).add(0, 1.96, 0), forward.scale(.95).add(0, 2.08, 0),
                forward.scale(1.12).add(0, 1.62, 0),
                forward.scale(-.88).add(0, 1.32, 0), forward.scale(-1.05).add(0, 1.0, 0),
                forward.scale(.62 + swing * .18).add(right.scale(.28)).add(0, .12, 0),
                forward.scale(.62 - swing * .18).add(right.scale(-.28)).add(0, .12, 0),
                forward.scale(-.62 - swing * .18).add(right.scale(.28)).add(0, .12, 0),
                forward.scale(-.62 + swing * .18).add(right.scale(-.28)).add(0, .12, 0)};
        float[] scales = {.55f, .65f, .7f, .5f, .6f, .5f, .3f, .3f, .3f, .3f};
        var buffers = e.getMultiBufferSource();
        // Wind in the flame's own (upside-down) model space, opposite to travel.
        float bendX = (float) (-velocity.x * 30), bendZ = (float) (-velocity.z * 30);
        for (int i = 0; i < points.length; i++) {
            pose.pushPose();
            pose.translate(points[i].x, points[i].y, points[i].z);
            pose.scale(1, -1, 1);
            GhostMaterials.flames(pose, buffers, time + i * 7, Mth.clamp(bendX, -8, 8), Mth.clamp(bendZ, -8, 8), scales[i]);
            pose.popPose();
        }
        GhostLights.request(horse.getPosition(partial).add(0, 1.7, 0), 13);
    }
}
