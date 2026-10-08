package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * Drawing Iceman's things in the world (after the translucent blocks): the pose moved to world coordinates (camera at
 * the origin of its translation), the model-view reset, the ice context ({@link IceMesh}) and the film effects' context
 * (FilmFx: mist, light, rings) ready. Draw the ice first ({@link #ice()}, then {@link #endIce()}), then the effects
 * ({@link #fx()}), then {@link #close()}.
 * <pre>
 *   IceStage st = IceStage.open(e); if (st == null) return;
 *   try { ...IceMesh shapes with st.ice() (set st.ice().light per thing via IceStage.light(at))...; st.endIce();
 *         ...FilmFx with st.fx()...; } finally { st.close(); }
 * </pre>
 */
public final class IceStage {
    public final PoseStack pose;
    public final Vec3 cam;
    public final float partial, time;
    public final Vec3 right, up;
    private final MultiBufferSource.BufferSource ice;
    private final MultiBufferSource.BufferSource fx;
    private IceMesh.Ctx ctx;
    private FilmContext film;

    private IceStage(RenderLevelStageEvent e) {
        var mc = Minecraft.getInstance();
        pose = e.getPoseStack();
        cam = e.getCamera().getPosition();
        partial = e.getPartialTick();
        time = mc.level.getGameTime() + partial;
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation);
        var uu = new Vector3f(0, 1, 0).rotate(rotation);
        right = new Vec3(rr.x, rr.y, rr.z);
        up = new Vec3(uu.x, uu.y, uu.z);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.last().pose().identity();
        RenderSystem.applyModelViewMatrix();
        ice = mc.renderBuffers().bufferSource();
        fx = FilmFx.batched();
    }
    /** Opens the stage at AFTER_TRANSLUCENT_BLOCKS (null at any other stage or with no world). */
    public static IceStage open(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || Minecraft.getInstance().level == null) return null;
        return new IceStage(e);
    }
    /** The ice context (world coordinates). */
    public IceMesh.Ctx ice() {
        if (ctx == null) { ctx = IceMesh.begin(pose, ice, light(cam)); ctx.time = time; }
        return ctx;
    }
    /** Draws the ice gathered so far (and its light). */
    public void endIce() {
        if (ctx != null) { ctx.end(); ctx = null; }
        IceMesh.endBatches(ice);
    }
    /** The film effects' context (world coordinates). */
    public FilmContext fx() {
        if (film == null) film = new FilmContext(pose, fx, cam, right, up, time, 0, partial);
        return film;
    }
    public void close() {
        try {
            if (ctx != null) endIce();
            if (film != null) fx.endBatch();
        } finally {
            var mv = RenderSystem.getModelViewStack();
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            pose.popPose();
        }
    }
    /** The light (packed, for the ice) at a point of the world. */
    public static int light(Vec3 at) {
        var level = Minecraft.getInstance().level;
        return level == null ? IceMesh.FULL : LevelRenderer.getLightColor(level, BlockPos.containing(at));
    }
    /** How far a point is from the camera, in blocks (for less detail far away). */
    public double distance(Vec3 at) { return at.distanceTo(cam); }
    /** True when a point is far enough away to draw it with less detail. */
    public boolean far(Vec3 at) { return at.distanceToSqr(cam) > Math.pow(com.FIRNI.superheromod.heroes.iceman.IcemanConfig.FAR_DETAIL.get(), 2); }
}
