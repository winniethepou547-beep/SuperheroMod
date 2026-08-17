package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cyclops optik isini — surekli custom geometri.
 *
 * Katmanlar ic ice silindirik (capraz quad) kabuklardan olusur:
 *   0-1  koyu dis kabuk   -> ALPHA blend  (koyu tonlarin gorunmesi icin sart)
 *   2-4  parlak ic cekirdek -> ADDITIVE blend (parlama icin)
 *
 * Onemli: koyu renkler additive ile CIZILEMEZ; additive sadece parlaklik ekler,
 * bu yuzden dis kabuk alpha blend ile ayri gecte ciziliyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public class CyclopsBeamWorldRenderer {

    // {yaricap, r, g, b, alpha}
    // Dis katmanlar ic lazere yakinlastirildi: eskiden 0.78 yaricapla cok
    // disarida kaliyordu ve soluk cokgen govdesi ayri bir sekil gibi
    // goruluyordu. Artik cekirdegi sarmalayan ince bir hale.
    private static final float[][] LAYERS = {
            {0.46f, 0.30f, 0.00f, 0.00f, 0.22f}, // dis parlama - koyu bordo
            {0.38f, 0.55f, 0.01f, 0.02f, 0.40f}, // dis kabuk    - koyu kirmizi
            {0.30f, 1.00f, 0.06f, 0.05f, 0.72f}, // ana cekirdek - parlak kirmizi
            {0.17f, 1.00f, 0.45f, 0.38f, 0.88f}, // sicak cekirdek - pembemsi
            {0.07f, 1.00f, 0.93f, 0.88f, 1.00f}, // optik merkez - beyaz
    };

    /** Alpha blend ile cizilecek koyu katman sayisi (bastan itibaren). */
    private static final int DARK_LAYERS = 2;

    /**
     * Koyu dis kabuk ince isinlarda (LMB) guzel duruyor ama kalin isinlarda
     * (RMB/ulti) genis kirli bir koni olusturuyordu. Genislik arttikca koyu
     * katmanlarin gorunurlugunu dusuruyoruz.
     */
    private static float darkLayerScale(float widthMult) {
        if (widthMult <= 0.45f) return 1.0f;
        return Mth.clamp(0.45f / widthMult, 0.22f, 1.0f);
    }

    /** Kesit yuvarlakligi: 6 duzlem = neredeyse dairesel govde. */
    private static final int PLANES_PER_LAYER = 6;

    /** Enerji akisi icin uzunlamasina bolut sayisi. */
    private static final int LENGTH_SEGMENTS = 16;

    /** Enerjinin isin boyunca akma hizi. */
    private static final float FLOW_SPEED = 7.0f;

    private record BeamSegment(Vec3 origin, Vec3 end, float widthMult, float alpha) {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientBeamData.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<BeamSegment> segments = collectSegments(mc, event.getPartialTick());
        if (segments.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float time = System.nanoTime() / 1_000_000_000f;

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().last().pose().identity();
        RenderSystem.getModelViewStack().last().normal().identity();
        RenderSystem.applyModelViewMatrix();

        if (DARK_LAYERS > 0) {
            renderPass(matrix, segments, 0, DARK_LAYERS, false, time);
        }
        renderPass(matrix, segments, DARK_LAYERS, LAYERS.length, true, time);

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();

        poseStack.popPose();
    }

    private static List<BeamSegment> collectSegments(Minecraft mc, float partialTick) {
        List<BeamSegment> segments = new ArrayList<>();

        for (Map.Entry<UUID, ClientBeamData.ChannelBeam> entry : ClientBeamData.getChannelBeams().entrySet()) {
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            if (player == null) continue;

            ClientBeamData.ChannelBeam beam = entry.getValue();
            if (beam.fadeAlpha <= 0) continue;
            if (beam.path.size() < 2) continue;

            // Ilk nokta istemcide goz konumundan tazeleniyor: sunucu tick'i
            // 20Hz oldugu icin kamerayi cevirirken isin kopuk gorunmesin.
            Vec3 smoothOrigin = player.getEyePosition(partialTick)
                    .add(player.getViewVector(partialTick).scale(0.3));

            addPath(segments, beam.path, smoothOrigin, beam.widthMultiplier, beam.fadeAlpha);
        }

        synchronized (ClientBeamData.getFlashBeams()) {
            for (ClientBeamData.FlashBeam flash : ClientBeamData.getFlashBeams()) {
                float alpha;
                if (flash.longFade) {
                    // Uzun izler omur boyunca yumusakca soner
                    alpha = (float) flash.ticksRemaining / flash.maxTicks;
                } else {
                    alpha = Math.min(1.0f,
                            (float) flash.ticksRemaining / Math.min(4, flash.maxTicks));
                }
                addPath(segments, flash.path, null, flash.widthMultiplier, alpha);
            }
        }

        return segments;
    }

    /** Bir polyline'i ardisik ikili parcalara bolerek segment listesine ekler. */
    private static void addPath(List<BeamSegment> out, List<Vec3> path,
                                Vec3 overrideFirst, float width, float alpha) {
        if (path.size() < 2 || alpha <= 0) return;

        for (int i = 0; i < path.size() - 1; i++) {
            Vec3 a = (i == 0 && overrideFirst != null) ? overrideFirst : path.get(i);
            Vec3 b = path.get(i + 1);
            if (a.distanceTo(b) < 0.1) continue;
            out.add(new BeamSegment(a, b, width, alpha));
        }
    }

    private static void renderPass(Matrix4f matrix, List<BeamSegment> segments,
                                   int fromLayer, int toLayer, boolean additive, float time) {
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }
        // Isin kendi katmanlarini kesmesin diye derinlik yazimi kapali
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (BeamSegment seg : segments) {
            addBeamVertices(matrix, buf, seg, fromLayer, toLayer, time);
        }

        tes.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    private static void addBeamVertices(Matrix4f matrix, BufferBuilder buf,
                                        BeamSegment seg, int fromLayer, int toLayer, float time) {
        Vec3 delta = seg.end.subtract(seg.origin);
        double length = delta.length();
        if (length < 0.05) return;

        Vec3 dir = delta.normalize();
        Vec3 arbitrary = Math.abs(dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perp1 = dir.cross(arbitrary).normalize();
        Vec3 perp2 = dir.cross(perp1).normalize();

        // Baslangic animasyonu: isin genisligi 0'dan acilir
        float widthAnim = Mth.clamp(seg.alpha * 1.6f, 0.15f, 1.0f);

        // Uyarlanabilir detay: kisa ve ince parcalar icin cok daha az geometri.
        // Sabit 16 bolut x 6 duzlem, catlak/yildirim gibi yuzlerce kisa parcada
        // milyonlarca vertex uretiyordu.
        int lengthSegments;
        int planes;
        if (seg.widthMult < 0.3f || length < 1.5) {
            lengthSegments = 1;
            planes = 2;
        } else if (length < 6.0) {
            lengthSegments = 4;
            planes = 4;
        } else {
            lengthSegments = LENGTH_SEGMENTS;
            planes = PLANES_PER_LAYER;
        }

        for (int layer = fromLayer; layer < toLayer && layer < LAYERS.length; layer++) {
            float[] ld = LAYERS[layer];
            float baseRadius = ld[0] * seg.widthMult * widthAnim;
            float r = ld[1], g = ld[2], b = ld[3];
            float baseAlpha = ld[4] * seg.alpha;
            if (layer < DARK_LAYERS) {
                baseAlpha *= darkLayerScale(seg.widthMult);
            }

            // Hafif nabiz
            float pulse = 1.0f + (layer < DARK_LAYERS ? 0.10f : 0.05f)
                    * Mth.sin(time * (3.0f + layer * 1.4f));
            float radius = baseRadius * pulse;

            for (int s = 0; s < lengthSegments; s++) {
                double t0 = (double) s / lengthSegments;
                double t1 = (double) (s + 1) / lengthSegments;

                Vec3 p0 = seg.origin.add(dir.scale(length * t0));
                Vec3 p1 = seg.origin.add(dir.scale(length * t1));

                // Enerji akisi: parlaklik hedefe dogru ilerleyen dalga
                float flow = 0.82f + 0.18f * Mth.sin((float) (t0 * 9.0) - time * FLOW_SPEED);
                float a = baseAlpha * flow;

                // Ucta hafif incelme
                float taper0 = (float) (1.0 - 0.12 * t0);
                float taper1 = (float) (1.0 - 0.12 * t1);

                for (int q = 0; q < planes; q++) {
                    double angle = q * Math.PI / planes;
                    Vec3 off = perp1.scale(Math.cos(angle)).add(perp2.scale(Math.sin(angle)));

                    Vec3 o0 = off.scale(radius * taper0);
                    Vec3 o1 = off.scale(radius * taper1);

                    vertex(buf, matrix, p0.add(o0), r, g, b, a);
                    vertex(buf, matrix, p0.subtract(o0), r, g, b, a);
                    vertex(buf, matrix, p1.subtract(o1), r, g, b, a);
                    vertex(buf, matrix, p1.add(o1), r, g, b, a);
                }
            }
        }
    }

    private static void vertex(BufferBuilder buf, Matrix4f matrix, Vec3 p,
                               float r, float g, float b, float a) {
        buf.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }
}
