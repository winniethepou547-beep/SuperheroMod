package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * SOMUT KUM SEKILLERI — cekme eli ve ziplama kayasi.
 *
 * Partikuller kuvveti anlatiyor ama KUTLE anlatmiyor. Elin ve kayanin
 * gercekten "bir sey" olmasi icin geometri gerekiyordu; partikul bulutu
 * ne kadar yogun olursa olsun katı bir cisim gibi okunmuyor.
 *
 * Gecici cizim: model asamasinda yerini Blockbench varliklari alacak.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class SandShapeRenderer {

    private static final float[] SAND_LIGHT = {0.88f, 0.77f, 0.51f};
    private static final float[] SAND_MID = {0.74f, 0.63f, 0.40f};
    private static final float[] ROCK = {0.62f, 0.54f, 0.38f};
    private static final float[] ROCK_HARD = {0.48f, 0.43f, 0.33f};

    private SandShapeRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<SandShapeSyncPacket.Shape> shapes = ClientSandShapeData.get();
        if (shapes.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().last().pose().identity();
        RenderSystem.getModelViewStack().last().normal().identity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (SandShapeSyncPacket.Shape shape : shapes) {
            if (shape.type() == SandShapeSyncPacket.TYPE_HAND) {
                drawHand(buf, matrix, shape);
            } else {
                drawPillar(buf, matrix, shape);
            }
        }

        tes.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // CEKME ELI
    // ------------------------------------------------------------------

    /**
     * Genis yassi avuc + dort parmak.
     *
     * Parmaklar cizimdeki gibi HAFIF KIRIK: her parmak iki bogumdan
     * olusuyor ve ust bogum one dogru kiviriliyor. Duz cubuklar el degil
     * tarak gibi duruyordu.
     *
     * Cekme ilerledikce parmaklar yumruk sikar gibi kapaniyor; en sonda el
     * yuzeye gomulup gorunmez oluyor.
     */
    private static void drawHand(BufferBuilder buf, Matrix4f m, SandShapeSyncPacket.Shape s) {
        float sink = Mth.clamp(s.sink(), 0f, 1f);
        if (sink >= 0.999f) return;

        double yaw = Math.toRadians(s.yaw());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 side = new Vec3(fwd.z, 0, -fwd.x);
        Vec3 up = new Vec3(0, 1, 0);

        float grow = Mth.clamp(s.grow(), 0f, 1f);
        float curl = Mth.clamp(s.curl(), 0f, 1f);
        float alpha = 1f - sink * 0.85f;

        // Gomulme: el asagi ceker, cizim yuzeyin altina kayar
        Vec3 base = new Vec3(s.x(), s.y() - sink * 1.4, s.z());

        // --- AVUC ---
        // Genis, yassi ve hafif egimli. Cizimdeki gibi one dogru incelmiyor;
        // kutle hissi genislikten geliyor.
        double palmHalfWidth = 1.35;
        double palmHalfLen = 1.05;
        double palmThick = 0.22;

        Vec3 palmCenter = base.add(up.scale(0.18 * grow));
        box(buf, m, palmCenter,
                side.scale(palmHalfWidth),
                up.scale(palmThick),
                fwd.scale(palmHalfLen),
                SAND_MID, alpha);

        // --- PARMAKLAR ---
        // Avucun ON KENARINDAN cikiyorlar; ortadan ciksalardi el degil
        // sirtindan diken cikmis bir levha gibi gorunurdu.
        Vec3 knuckleLine = palmCenter.add(fwd.scale(palmHalfLen * 0.85));

        for (int i = 0; i < 4; i++) {
            // -1.5 .. 1.5 araliginda esit dagilim
            double offset = (i - 1.5) / 1.5 * palmHalfWidth * 0.78;
            Vec3 root = knuckleLine.add(side.scale(offset));

            // Ortadaki parmaklar biraz daha uzun — esit uzunluk tarak gibi
            // duruyordu
            double lengthScale = (i == 1 || i == 2) ? 1.0 : 0.85;
            drawFinger(buf, m, root, fwd, side, up, grow, curl, lengthScale, alpha);
        }

        // --- BILEK ---
        // Elin yerden CIKTIGINI anlatan govde; olmayinca el havada
        // yuzuyormus gibi duruyor.
        box(buf, m, base.subtract(up.scale(0.45)).subtract(fwd.scale(palmHalfLen * 0.4)),
                side.scale(palmHalfWidth * 0.55),
                up.scale(0.5),
                fwd.scale(palmHalfLen * 0.5),
                ROCK, alpha);
    }

    /**
     * Tek parmak: iki bogum, ust bogum one kirik.
     *
     * curl arttikca her iki bogum da ice kapanip yumruk olusturuyor.
     */
    private static void drawFinger(BufferBuilder buf, Matrix4f m, Vec3 root,
                                   Vec3 fwd, Vec3 side, Vec3 up,
                                   float grow, float curl, double lengthScale,
                                   float alpha) {
        double thick = 0.22;
        double lower = 0.85 * lengthScale * grow;
        double upper = 0.65 * lengthScale * grow;

        // Alt bogum: yukari, kapanirken one yatiyor
        double lowerLean = 0.25 + curl * 0.85;
        Vec3 lowerDir = up.scale(1.0 - lowerLean * 0.55)
                .add(fwd.scale(lowerLean * 0.6)).normalize();

        Vec3 lowerCenter = root.add(lowerDir.scale(lower * 0.5));
        boxAlong(buf, m, lowerCenter, lowerDir, lower * 0.5, thick, side, SAND_LIGHT, alpha);

        Vec3 joint = root.add(lowerDir.scale(lower));

        // Ust bogum: cizimdeki kiriklik. Kapanirken neredeyse avuca donuyor.
        double upperLean = 0.75 + curl * 1.15;
        Vec3 upperDir = up.scale(1.0 - upperLean * 0.62)
                .add(fwd.scale(upperLean * 0.72)).normalize();

        Vec3 upperCenter = joint.add(upperDir.scale(upper * 0.5));
        boxAlong(buf, m, upperCenter, upperDir, upper * 0.5, thick * 0.86, side,
                SAND_MID, alpha);
    }

    // ------------------------------------------------------------------
    // ZIPLAMA KAYASI
    // ------------------------------------------------------------------

    /**
     * Kum sutunu yerine SOMUT KAYA.
     *
     * Duz bir silindir "kayalasmis kum" gibi durmuyordu; kullanicinin daha
     * once Colossus icin soyledigi kural burada da gecerli: kum dagiliktir.
     * Sutun ust uste dizilmis, her biri kendi acisinda kaydirilmis
     * parcalardan olusuyor ve araya sert kum tasi katmanlari giriyor.
     */
    private static void drawPillar(BufferBuilder buf, Matrix4f m, SandShapeSyncPacket.Shape s) {
        float grow = Mth.clamp(s.grow(), 0f, 1f);
        float sink = Mth.clamp(s.sink(), 0f, 1f);
        if (grow <= 0.01f || sink >= 0.999f) return;

        // Parcalanirken kaya hem soluyor hem disari saciliyor
        float alpha = 1f - sink * 0.9f;
        double scatter = sink * 1.4;

        Vec3 base = new Vec3(s.x(), s.y(), s.z());
        Vec3 up = new Vec3(0, 1, 0);

        int chunks = 7;
        double totalHeight = 4.5 * grow;

        for (int i = 0; i < chunks; i++) {
            double t = i / (double) chunks;
            double y = totalHeight * t;

            // Her parca kendi acisinda: hizalanmis kutular piramit gibi
            // duruyordu ve kullanici bunu daha once acikca reddetti
            double angle = i * 1.9 + s.yaw() * Mth.DEG_TO_RAD;
            double lean = 0.22 * Math.sin(i * 1.3);

            // Yukari dogru hafif daralma — tabani genis, tepesi dar
            double half = 0.95 - t * 0.32;

            Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 perp = new Vec3(-dir.z, 0, dir.x);

            Vec3 center = base
                    .add(up.scale(y + half * 0.5))
                    .add(dir.scale(lean + scatter * (0.4 + t)));

            float[] col = (i % 3 == 1) ? ROCK_HARD : ROCK;

            box(buf, m, center,
                    dir.scale(half),
                    up.scale(half * 0.62),
                    perp.scale(half * 0.85),
                    col, alpha);
        }

        // Tabandaki genis kaide — sutunun yerden ciktigini gosterir
        box(buf, m, base.add(up.scale(0.12)),
                new Vec3(1.25, 0, 0), new Vec3(0, 0.14, 0), new Vec3(0, 0, 1.25),
                ROCK_HARD, alpha);
    }

    // ------------------------------------------------------------------
    // Geometri yardimcilari
    // ------------------------------------------------------------------

    /** Verilen yon boyunca uzanan kutu — parmak bogumlari icin. */
    private static void boxAlong(BufferBuilder buf, Matrix4f m, Vec3 center,
                                 Vec3 dir, double halfLen, double thick,
                                 Vec3 side, float[] col, float alpha) {
        Vec3 across = side.normalize().scale(thick);
        Vec3 depth = dir.cross(side).normalize().scale(thick);
        box(buf, m, center, across, dir.normalize().scale(halfLen), depth, col, alpha);
    }

    private static void box(BufferBuilder buf, Matrix4f m, Vec3 c,
                            Vec3 hw, Vec3 hh, Vec3 hd, float[] col, float alpha) {
        Vec3 p000 = c.subtract(hw).subtract(hh).subtract(hd);
        Vec3 p100 = c.add(hw).subtract(hh).subtract(hd);
        Vec3 p110 = c.add(hw).add(hh).subtract(hd);
        Vec3 p010 = c.subtract(hw).add(hh).subtract(hd);
        Vec3 p001 = c.subtract(hw).subtract(hh).add(hd);
        Vec3 p101 = c.add(hw).subtract(hh).add(hd);
        Vec3 p111 = c.add(hw).add(hh).add(hd);
        Vec3 p011 = c.subtract(hw).add(hh).add(hd);

        quad(buf, m, p001, p101, p111, p011, col, alpha);
        quad(buf, m, p100, p000, p010, p110, col, alpha);
        quad(buf, m, p000, p001, p011, p010, darker(col), alpha);
        quad(buf, m, p101, p100, p110, p111, darker(col), alpha);
        quad(buf, m, p010, p011, p111, p110, lighter(col), alpha);
        quad(buf, m, p000, p100, p101, p001, darker(col), alpha);
    }

    private static float[] darker(float[] col) {
        return new float[]{col[0] * 0.76f, col[1] * 0.76f, col[2] * 0.76f};
    }

    private static float[] lighter(float[] col) {
        return new float[]{Math.min(1f, col[0] * 1.12f),
                Math.min(1f, col[1] * 1.12f), Math.min(1f, col[2] * 1.12f)};
    }

    private static void quad(BufferBuilder buf, Matrix4f m,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float alpha) {
        vert(buf, m, a, col, alpha);
        vert(buf, m, b, col, alpha);
        vert(buf, m, c, col, alpha);
        vert(buf, m, d, col, alpha);
    }

    private static void vert(BufferBuilder buf, Matrix4f m, Vec3 p, float[] col, float alpha) {
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(col[0], col[1], col[2], Mth.clamp(alpha, 0f, 1f)).endVertex();
    }
}
