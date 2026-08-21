package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import com.FIRNI.superheromod.client.render.util.SandGeometry;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * KUM SEKILLERI VE GOSTERGELER.
 *
 * Iki ayri cizim gecisi var, bu bilincli:
 *
 *  1. DOKULU gecis — el ve kum alanlari. Duz renkli kutular "kum" gibi
 *     okunmuyordu; oyunun kendi kum dokusu blok atlasindan aliniyor.
 *  2. DUZ RENK gecis — kirmizi ok gostergesi. Gosterge bir cisim degil bir
 *     CIZIM; dokulu olsaydi araziye karisirdi.
 *
 * Gostergeler partikul DEGIL geometri: partikul gostergesi seyrek kaliyor,
 * hedef hareket ettikce dagiliyor ve kum zemininde tamamen kayboluyordu.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class SandShapeRenderer {

    /** Kum tonlari — dokunun uzerine carpan renk olarak biniyor. */
    private static final float[] TINT_LIGHT = {1.05f, 1.00f, 0.85f};
    private static final float[] TINT_MID = {0.88f, 0.82f, 0.66f};
    private static final float[] TINT_DARK = {0.66f, 0.60f, 0.46f};

    private static final float[] MARK_RED = {0.95f, 0.13f, 0.13f};

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

        // ENTITY CIZIM YOLU — sol tiktaki uzayan kolla AYNI.
        //
        // Onceden POSITION_TEX_COLOR kullaniliyordu; o shader ne dunya
        // isigini ne de yuz golgelemesini uyguluyor. Sekiller hem gece
        // yaniyor hem de gunduz oyunun kum bloklarindan parlak duruyordu.
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        TextureAtlasSprite sprite = SandGeometry.sandSprite();

        RenderType sandType = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
        VertexConsumer sand = buffers.getBuffer(sandType);

        for (SandShapeSyncPacket.Shape s : shapes) {
            if (s.type() == SandShapeSyncPacket.TYPE_HAND) {
                drawHand(sand, matrix, s, sprite);
            } else if (s.type() == SandShapeSyncPacket.TYPE_ROCK) {
                drawRock(sand, matrix, s, sprite);
            }
        }
        buffers.endBatch(sandType);

        // Gostergeler AYRI: onlar bir cisim degil CIZIM. Dokulu ve
        // golgeli olsalardi araziye karisirlardi.
        drawMarkers(matrix, shapes);

        pose.popPose();
    }


    // ------------------------------------------------------------------
    // CEKME ELI
    // ------------------------------------------------------------------

    private static void drawHand(VertexConsumer buf, Matrix4f m,
                                 SandShapeSyncPacket.Shape s, TextureAtlasSprite sprite) {
        float sink = Mth.clamp(s.sink(), 0f, 1f);
        if (sink >= 0.999f) return;

        double yaw = Math.toRadians(s.yaw());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 side = new Vec3(fwd.z, 0, -fwd.x);
        Vec3 up = new Vec3(0, 1, 0);

        float grow = Mth.clamp(s.grow(), 0f, 1f);
        float curl = Mth.clamp(s.curl(), 0f, 1f);
        float alpha = 1f - sink * 0.85f;

        Vec3 base = new Vec3(s.x(), s.y() - sink * 1.6, s.z());
        int light = SandGeometry.lightAt(base);

        double palmHalfWidth = 1.9;
        double palmHalfLen = 1.5;
        double palmThick = 0.34;

        Vec3 palmCenter = base.add(up.scale(0.4 * grow));
        SandGeometry.box(buf, m, sprite, light, palmCenter,
                side.scale(palmHalfWidth), up.scale(palmThick), fwd.scale(palmHalfLen),
                TINT_MID, alpha, 1f);

        // Parmaklar avucun ON KENARINDAN cikar; ortadan ciksalardi el degil
        // sirtindan diken cikmis bir levha gibi gorunurdu.
        Vec3 knuckleLine = palmCenter.add(fwd.scale(palmHalfLen * 0.85));

        for (int i = 0; i < 4; i++) {
            double offset = (i - 1.5) / 1.5 * palmHalfWidth * 0.78;
            Vec3 root = knuckleLine.add(side.scale(offset));

            // Ortadaki parmaklar uzun — esit uzunluk tarak gibi duruyordu
            double lengthScale = (i == 1 || i == 2) ? 1.0 : 0.85;
            drawFinger(buf, m, sprite, light, root, fwd, side, up, grow, curl, lengthScale, alpha);
        }

        // Bilek: elin yerden CIKTIGINI anlatir, olmayinca el havada yuzuyor
        SandGeometry.box(buf, m, sprite, light,
                base.subtract(up.scale(0.5)).subtract(fwd.scale(palmHalfLen * 0.4)),
                side.scale(palmHalfWidth * 0.6), up.scale(0.85), fwd.scale(palmHalfLen * 0.5),
                TINT_DARK, alpha, 1f);
    }

    /** Tek parmak: iki bogum, ust bogum one kirik; curl ile kapaniyor. */
    private static void drawFinger(VertexConsumer buf, Matrix4f m, TextureAtlasSprite sprite,
                                   int light, Vec3 root, Vec3 fwd, Vec3 side, Vec3 up,
                                   float grow, float curl, double lengthScale, float alpha) {
        double thick = 0.34;
        double lower = 1.65 * lengthScale * grow;
        double upper = 1.25 * lengthScale * grow;
        if (lower < 0.02) return;

        double lowerLean = 0.25 + curl * 0.85;
        Vec3 lowerDir = up.scale(1.0 - lowerLean * 0.55)
                .add(fwd.scale(lowerLean * 0.6)).normalize();

        boxAlong(buf, m, sprite, light, root.add(lowerDir.scale(lower * 0.5)),
                lowerDir, lower * 0.5, thick, side, TINT_LIGHT, alpha);

        Vec3 joint = root.add(lowerDir.scale(lower));

        double upperLean = 0.75 + curl * 1.15;
        Vec3 upperDir = up.scale(1.0 - upperLean * 0.62)
                .add(fwd.scale(upperLean * 0.72)).normalize();

        boxAlong(buf, m, sprite, light, joint.add(upperDir.scale(upper * 0.5)),
                upperDir, upper * 0.5, thick * 0.86, side, TINT_MID, alpha);

    }

    // ------------------------------------------------------------------
    // FIRLATILAN KAYA
    // ------------------------------------------------------------------

    /**
     * Colossus'un firlattigi kaya.
     *
     * Once partikulle ciziliyordu ve GORUNMUYORDU: kaya hizli oldugu icin
     * partikuller bir iki karede geride kaliyor, oyuncuya yere carpma
     * efektinden baska bir sey ulasmiyordu. Artik somut geometri.
     *
     * Tek kup degil UC parcali kume: dev bir kaya duzgun bir kup olmaz.
     * Parcalar farkli acilarda ve boyutlarda.
     */
    private static void drawRock(VertexConsumer buf, Matrix4f m,
                                 SandShapeSyncPacket.Shape s, TextureAtlasSprite sprite) {
        double radius = s.curl();
        if (radius <= 0.05) return;

        Vec3 center = new Vec3(s.x(), s.y(), s.z());
        int light = SandGeometry.lightAt(center);

        // Yuvarlanma: aci konumdan turetiliyor, boylece kaya ucarken
        // donuyor. Sabit acili bir kutu uzayda kaymis gibi duruyordu.
        double spin = Math.toRadians(s.yaw());

        for (int i = 0; i < 3; i++) {
            double a = spin + i * 2.1;
            double tilt = spin * 0.7 + i * 1.3;

            Vec3 ax = new Vec3(Math.cos(a), Math.sin(tilt) * 0.5, Math.sin(a)).normalize();
            Vec3 az = new Vec3(-ax.z, 0, ax.x).normalize();
            Vec3 ay = az.cross(ax).normalize();

            // Parcalar merkeze gore kaymis; ust uste binmeleri kutleyi
            // tek parca gibi gosteriyor
            double off = radius * 0.35;
            Vec3 c = center
                    .add(ax.scale(Math.cos(i * 2.4) * off))
                    .add(ay.scale(Math.sin(i * 1.7) * off));

            double half = radius * (0.78 - i * 0.13);
            float[] tint = (i == 1) ? TINT_DARK : TINT_MID;

            SandGeometry.box(buf, m, sprite, light, c,
                    ax.scale(half), ay.scale(half), az.scale(half), tint, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------
    // NISAN HALKASI
    // ------------------------------------------------------------------

    /**
     * Kayanin dusecegi yeri gosteren halka.
     *
     * Dev bir kaya firlatiliyordu ama nereye dustugu ancak carptiktan
     * sonra anlasiliyordu. Halka imlecin gosterdigi zemin noktasinda ve
     * yaricapi patlama alaniyla AYNI -- gosterge gercek etkiyi anlatmali,
     * dekoratif bir daire olmamali.
     */
    private static void drawTargetRing(BufferBuilder buf, Matrix4f m,
                                       SandShapeSyncPacket.Shape s) {
        double radius = s.curl();
        if (radius <= 0.1) return;

        Vec3 center = new Vec3(s.x(), s.y() + 0.05, s.z());
        double thickness = 0.22;

        int steps = 40;
        for (int i = 0; i < steps; i++) {
            double a0 = (i / (double) steps) * Math.PI * 2;
            double a1 = ((i + 1) / (double) steps) * Math.PI * 2;

            Vec3 inner0 = center.add(Math.cos(a0) * (radius - thickness), 0,
                    Math.sin(a0) * (radius - thickness));
            Vec3 outer0 = center.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
            Vec3 outer1 = center.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            Vec3 inner1 = center.add(Math.cos(a1) * (radius - thickness), 0,
                    Math.sin(a1) * (radius - thickness));

            vert(buf, m, inner0, MARK_RED, 0.85f);
            vert(buf, m, outer0, MARK_RED, 0.85f);
            vert(buf, m, outer1, MARK_RED, 0.85f);
            vert(buf, m, inner1, MARK_RED, 0.85f);
        }

        // Merkez isareti — halka tek basina "nereye" degil "nerede"
        // diyor; ortadaki capraz hedefi kesinlestiriyor
        double cross = radius * 0.28;
        for (int axis = 0; axis < 2; axis++) {
            Vec3 dir = axis == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
            Vec3 across = axis == 0 ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);

            vert(buf, m, center.subtract(dir.scale(cross)).subtract(across.scale(0.12)),
                    MARK_RED, 0.7f);
            vert(buf, m, center.add(dir.scale(cross)).subtract(across.scale(0.12)),
                    MARK_RED, 0.7f);
            vert(buf, m, center.add(dir.scale(cross)).add(across.scale(0.12)),
                    MARK_RED, 0.7f);
            vert(buf, m, center.subtract(dir.scale(cross)).add(across.scale(0.12)),
                    MARK_RED, 0.7f);
        }
    }

    // ------------------------------------------------------------------
    // KIRMIZI OK GOSTERGESI
    // ------------------------------------------------------------------

    private static void drawMarkers(Matrix4f m, List<SandShapeSyncPacket.Shape> shapes) {
        boolean any = false;
        for (SandShapeSyncPacket.Shape s : shapes) {
            if (s.type() == SandShapeSyncPacket.TYPE_ARROW
                    || s.type() == SandShapeSyncPacket.TYPE_TARGET) {
                any = true;
                break;
            }
        }
        if (!any) return;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (SandShapeSyncPacket.Shape s : shapes) {
            if (s.type() == SandShapeSyncPacket.TYPE_ARROW) drawArrow(buf, m, s);
            else if (s.type() == SandShapeSyncPacket.TYPE_TARGET) drawTargetRing(buf, m, s);
        }

        tes.end();
    }

    /**
     * Tek ok: ucu Sandman'a bakan iki kanat.
     *
     * Cerceve yerine ok kullaniliyor cunku cerceve alanin SINIRINI
     * gosteriyordu ama cekisin hangi yone oldugunu anlatmiyordu.
     */
    private static void drawArrow(BufferBuilder buf, Matrix4f m, SandShapeSyncPacket.Shape s) {
        double yaw = Math.toRadians(s.yaw());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 side = new Vec3(fwd.z, 0, -fwd.x);

        double halfWidth = s.curl();
        double thickness = 0.22;
        float alpha = 0.35f + s.grow() * 0.5f;

        Vec3 tip = new Vec3(s.x(), s.y() + 0.05, s.z());

        for (int arm = -1; arm <= 1; arm += 2) {
            Vec3 end = tip.add(side.scale(halfWidth * arm))
                    .subtract(fwd.scale(halfWidth * 0.9));
            ribbon(buf, m, tip, end, thickness, MARK_RED, alpha);
        }
    }

    /** Zemine yatik, kalinligi olan duz serit. */
    private static void ribbon(BufferBuilder buf, Matrix4f m, Vec3 from, Vec3 to,
                               double thickness, float[] col, float alpha) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) return;

        Vec3 across = new Vec3(-dir.z, 0, dir.x).normalize().scale(thickness);

        vert(buf, m, from.subtract(across), col, alpha);
        vert(buf, m, from.add(across), col, alpha);
        vert(buf, m, to.add(across), col, alpha);
        vert(buf, m, to.subtract(across), col, alpha);
    }

    // ------------------------------------------------------------------
    // Geometri yardimcilari
    // ------------------------------------------------------------------

    /** Verilen yon boyunca uzanan kutu — parmak bogumlari icin. */
    private static void boxAlong(VertexConsumer buf, Matrix4f m, TextureAtlasSprite sprite,
                                 int light, Vec3 center, Vec3 dir, double halfLen,
                                 double thick, Vec3 side, float[] col, float alpha) {
        Vec3 across = side.normalize().scale(thick);
        Vec3 depth = dir.cross(side).normalize().scale(thick);
        SandGeometry.box(buf, m, sprite, light, center,
                across, dir.normalize().scale(halfLen), depth, col, alpha, 1f);
    }

    /**
     * Gosterge vertex'i — DUZ RENK, dokusuz ve golgesiz.
     *
     * Gosterge bir cisim degil bir cizim: dunya isigindan etkilenmemeli,
     * yoksa karanlikta tam da gorulmesi gereken anda kayboluyor.
     */
    private static void vert(BufferBuilder buf, Matrix4f m, Vec3 p, float[] col, float alpha) {
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(col[0], col[1], col[2], Mth.clamp(alpha, 0f, 1f)).endVertex();
    }
}
