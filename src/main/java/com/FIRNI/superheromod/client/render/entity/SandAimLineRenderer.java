package com.FIRNI.superheromod.client.render.entity;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.sandman.SandSoldierEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * MENZILLI ASKERIN NISAN CIZGISI.
 *
 * Partikul yerine GEOMETRI: partikuller her tick yeniden serpiliyor ve hedef
 * hareket ettikce arkada nokta nokta izler birakiyordu — ekran karmasaya
 * donuyordu. Duz bir seritin her karede yeniden cizilmesi hem puruzsuz hem
 * de iz birakmiyor.
 *
 * Serit sarj arttikca kalinlasiyor ve parlakligi artiyor, boylece ates anina
 * yaklasildigi anlasiliyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class SandAimLineRenderer {

    private SandAimLineRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float partial = mc.getFrameTime();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        boolean any = false;
        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof SandSoldierEntity soldier)) continue;

            float aim = soldier.getAimProgress();
            if (aim <= 0.02f) continue;

            int targetId = soldier.getAimTargetId();
            if (targetId < 0) continue;

            Entity target = mc.level.getEntity(targetId);
            if (target == null) continue;

            if (!any) {
                any = true;
                beginDraw(pose, cam);
                buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }

            Vec3 from = soldier.getPosition(partial)
                    .add(0, soldier.getBbHeight() * 0.85, 0);
            Vec3 to = target.getPosition(partial)
                    .add(0, target.getBbHeight() * 0.6, 0);

            drawBeam(buf, pose.last().pose(), from, to, aim);
        }

        if (!any) return;

        tes.end();
        endDraw(pose);
    }

    private static void beginDraw(PoseStack pose, Vec3 cam) {
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().last().pose().identity();
        RenderSystem.getModelViewStack().last().normal().identity();
        RenderSystem.applyModelViewMatrix();

        // Toplamali: cizgi isik gibi gorunsun
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void endDraw(PoseStack pose) {
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        pose.popPose();
    }

    /**
     * Kameraya donuk ince serit.
     *
     * Serit kameraya bakacak sekilde dondurulmezse belirli acilardan tamamen
     * kayboluyor; bu yuzden genislik ekseni kamera yonune gore hesaplaniyor.
     */
    private static void drawBeam(BufferBuilder buf, Matrix4f m,
                                 Vec3 from, Vec3 to, float aim) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 1.0E-6) return;

        Vec3 dir = delta.normalize();
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        Vec3 toCam = camPos.subtract(from);
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-6) return;
        side = side.normalize();

        // Sarj arttikca kalinlasip parlar
        double width = 0.018 + 0.045 * aim;
        float alpha = 0.25f + 0.55f * aim;

        Vec3 w = side.scale(width);

        // Uc kismi hedefte biraz daralsin — okun ucu gibi
        Vec3 wEnd = side.scale(width * 0.45);

        vert(buf, m, from.add(w), alpha * 0.5f);
        vert(buf, m, to.add(wEnd), alpha);
        vert(buf, m, to.subtract(wEnd), alpha);
        vert(buf, m, from.subtract(w), alpha * 0.5f);
    }

    private static void vert(BufferBuilder buf, Matrix4f m, Vec3 p, float alpha) {
        // Kum sarisina calan sicak beyaz
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(1.0f, 0.88f, 0.55f, Mth.clamp(alpha, 0f, 1f))
                .endVertex();
    }
}
