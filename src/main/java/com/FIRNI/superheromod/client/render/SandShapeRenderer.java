package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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

        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().last().pose().identity();
        RenderSystem.getModelViewStack().last().normal().identity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        drawTextured(matrix, shapes);
        drawMarkers(matrix, shapes);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // Dokulu gecis
    // ------------------------------------------------------------------

    private static void drawTextured(Matrix4f m, List<SandShapeSyncPacket.Shape> shapes) {
        boolean any = false;
        for (SandShapeSyncPacket.Shape s : shapes) {
            if (s.type() != SandShapeSyncPacket.TYPE_ARROW) {
                any = true;
                break;
            }
        }
        if (!any) return;

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

        TextureAtlasSprite sprite = sandSprite();

        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (SandShapeSyncPacket.Shape s : shapes) {
            if (s.type() == SandShapeSyncPacket.TYPE_HAND) {
                drawHand(buf, m, s, sprite);
            } else if (s.type() == SandShapeSyncPacket.TYPE_PATCH) {
                drawPatch(buf, m, s, sprite);
            }
        }

        tes.end();
    }

    /**
     * Kum dokusu, oyunun kendi blok atlasindan.
     *
     * Kendi doku dosyamizi eklemek yerine oyunun kumunu kullaniyoruz:
     * boylece kaynak paketi degistiren oyuncuda sekiller de degisiyor ve
     * sekil her zaman arazideki kumla ayni tonu tutuyor.
     */
    private static TextureAtlasSprite sandSprite() {
        return Minecraft.getInstance().getModelManager()
                .getBlockModelShaper()
                .getParticleIcon(Blocks.SAND.defaultBlockState());
    }

    // ------------------------------------------------------------------
    // CEKME ELI
    // ------------------------------------------------------------------

    private static void drawHand(BufferBuilder buf, Matrix4f m,
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

        double palmHalfWidth = 1.9;
        double palmHalfLen = 1.5;
        double palmThick = 0.34;

        Vec3 palmCenter = base.add(up.scale(0.4 * grow));
        box(buf, m, sprite, palmCenter,
                side.scale(palmHalfWidth), up.scale(palmThick), fwd.scale(palmHalfLen),
                TINT_MID, alpha);

        // Parmaklar avucun ON KENARINDAN cikar; ortadan ciksalardi el degil
        // sirtindan diken cikmis bir levha gibi gorunurdu.
        Vec3 knuckleLine = palmCenter.add(fwd.scale(palmHalfLen * 0.85));

        for (int i = 0; i < 4; i++) {
            double offset = (i - 1.5) / 1.5 * palmHalfWidth * 0.78;
            Vec3 root = knuckleLine.add(side.scale(offset));

            // Ortadaki parmaklar uzun — esit uzunluk tarak gibi duruyordu
            double lengthScale = (i == 1 || i == 2) ? 1.0 : 0.85;
            drawFinger(buf, m, sprite, root, fwd, side, up, grow, curl, lengthScale, alpha);
        }

        // Bilek: elin yerden CIKTIGINI anlatir, olmayinca el havada yuzuyor
        box(buf, m, sprite,
                base.subtract(up.scale(0.5)).subtract(fwd.scale(palmHalfLen * 0.4)),
                side.scale(palmHalfWidth * 0.6), up.scale(0.85), fwd.scale(palmHalfLen * 0.5),
                TINT_DARK, alpha);
    }

    /** Tek parmak: iki bogum, ust bogum one kirik; curl ile kapaniyor. */
    private static void drawFinger(BufferBuilder buf, Matrix4f m, TextureAtlasSprite sprite,
                                   Vec3 root, Vec3 fwd, Vec3 side, Vec3 up,
                                   float grow, float curl, double lengthScale, float alpha) {
        double thick = 0.34;
        double lower = 1.65 * lengthScale * grow;
        double upper = 1.25 * lengthScale * grow;
        if (lower < 0.02) return;

        double lowerLean = 0.25 + curl * 0.85;
        Vec3 lowerDir = up.scale(1.0 - lowerLean * 0.55)
                .add(fwd.scale(lowerLean * 0.6)).normalize();

        boxAlong(buf, m, sprite, root.add(lowerDir.scale(lower * 0.5)),
                lowerDir, lower * 0.5, thick, side, TINT_LIGHT, alpha);

        Vec3 joint = root.add(lowerDir.scale(lower));

        double upperLean = 0.75 + curl * 1.15;
        Vec3 upperDir = up.scale(1.0 - upperLean * 0.62)
                .add(fwd.scale(upperLean * 0.72)).normalize();

        boxAlong(buf, m, sprite, joint.add(upperDir.scale(upper * 0.5)),
                upperDir, upper * 0.5, thick * 0.86, side, TINT_MID, alpha);
    }

    // ------------------------------------------------------------------
    // KUM ALANI
    // ------------------------------------------------------------------

    /**
     * Yetenegin biraktigi kum.
     *
     * Once partikulle cizildi ve kullanici "cektigi yer kum olmuyor" dedi:
     * seyrek partikul zemini KAPLAMIYOR, sadece uzerinde toz gibi
     * duruyordu. Artik zemine oturan dokulu bir katman — gercekten kumla
     * kaplanmis gibi gorunuyor ama haritaya blok yazmiyor.
     */
    private static void drawPatch(BufferBuilder buf, Matrix4f m,
                                  SandShapeSyncPacket.Shape s, TextureAtlasSprite sprite) {
        double radius = s.curl();
        if (radius <= 0.05) return;

        // grow: Sandman menzilde mi. Uzaktayken kum sonuk ama GORUNUR —
        // oyuncu yaklasirsa canlanacagini gorebilmeli.
        float alpha = (0.5f + s.grow() * 0.45f) * (1f - Mth.clamp(s.sink(), 0f, 1f));
        float[] tint = s.grow() > 0.5f ? TINT_MID : TINT_DARK;

        // Zeminin hemen uzerinde: tam zemin hizasinda cizilseydi zemin
        // dokusuyla titrerdi
        Vec3 center = new Vec3(s.x(), s.y() + 0.02, s.z());

        // Kare degil sekizgen — kare kum yamasi yapay duruyordu
        int sides = 8;
        for (int i = 0; i < sides; i++) {
            double a0 = (i / (double) sides) * Math.PI * 2 + s.yaw() * Mth.DEG_TO_RAD;
            double a1 = ((i + 1) / (double) sides) * Math.PI * 2 + s.yaw() * Mth.DEG_TO_RAD;

            Vec3 p0 = center.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius);
            Vec3 p1 = center.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);

            // Dortgen arayuzunde ucgen: son nokta tekrarlaniyor
            vertTex(buf, m, center, sprite, 0.5f, 0.5f, tint, alpha);
            vertTex(buf, m, p0, sprite, 0f, 0f, tint, alpha);
            vertTex(buf, m, p1, sprite, 1f, 0f, tint, alpha);
            vertTex(buf, m, p1, sprite, 1f, 0f, tint, alpha);
        }
    }

    // ------------------------------------------------------------------
    // KIRMIZI OK GOSTERGESI
    // ------------------------------------------------------------------

    private static void drawMarkers(Matrix4f m, List<SandShapeSyncPacket.Shape> shapes) {
        boolean any = false;
        for (SandShapeSyncPacket.Shape s : shapes) {
            if (s.type() == SandShapeSyncPacket.TYPE_ARROW) {
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
            if (s.type() != SandShapeSyncPacket.TYPE_ARROW) continue;
            drawArrow(buf, m, s);
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

    private static void boxAlong(BufferBuilder buf, Matrix4f m, TextureAtlasSprite sprite,
                                 Vec3 center, Vec3 dir, double halfLen, double thick,
                                 Vec3 side, float[] col, float alpha) {
        Vec3 across = side.normalize().scale(thick);
        Vec3 depth = dir.cross(side).normalize().scale(thick);
        box(buf, m, sprite, center, across, dir.normalize().scale(halfLen), depth, col, alpha);
    }

    private static void box(BufferBuilder buf, Matrix4f m, TextureAtlasSprite sprite, Vec3 c,
                            Vec3 hw, Vec3 hh, Vec3 hd, float[] col, float alpha) {
        Vec3 p000 = c.subtract(hw).subtract(hh).subtract(hd);
        Vec3 p100 = c.add(hw).subtract(hh).subtract(hd);
        Vec3 p110 = c.add(hw).add(hh).subtract(hd);
        Vec3 p010 = c.subtract(hw).add(hh).subtract(hd);
        Vec3 p001 = c.subtract(hw).subtract(hh).add(hd);
        Vec3 p101 = c.add(hw).subtract(hh).add(hd);
        Vec3 p111 = c.add(hw).add(hh).add(hd);
        Vec3 p011 = c.subtract(hw).add(hh).add(hd);

        face(buf, m, sprite, p001, p101, p111, p011, col, alpha);
        face(buf, m, sprite, p100, p000, p010, p110, col, alpha);
        face(buf, m, sprite, p000, p001, p011, p010, darker(col), alpha);
        face(buf, m, sprite, p101, p100, p110, p111, darker(col), alpha);
        face(buf, m, sprite, p010, p011, p111, p110, lighter(col), alpha);
        face(buf, m, sprite, p000, p100, p101, p001, darker(col), alpha);
    }

    private static void face(BufferBuilder buf, Matrix4f m, TextureAtlasSprite sprite,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float alpha) {
        vertTex(buf, m, a, sprite, 0f, 0f, col, alpha);
        vertTex(buf, m, b, sprite, 1f, 0f, col, alpha);
        vertTex(buf, m, c, sprite, 1f, 1f, col, alpha);
        vertTex(buf, m, d, sprite, 0f, 1f, col, alpha);
    }

    /** Sprite'in atlas icindeki dilimine 0..1 araligini esler. */
    private static void vertTex(BufferBuilder buf, Matrix4f m, Vec3 p,
                                TextureAtlasSprite sprite, float u, float v,
                                float[] col, float alpha) {
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .uv(sprite.getU(u * 16f), sprite.getV(v * 16f))
                .color(Mth.clamp(col[0], 0f, 1f), Mth.clamp(col[1], 0f, 1f),
                        Mth.clamp(col[2], 0f, 1f), Mth.clamp(alpha, 0f, 1f))
                .endVertex();
    }

    private static void vert(BufferBuilder buf, Matrix4f m, Vec3 p, float[] col, float alpha) {
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(col[0], col[1], col[2], Mth.clamp(alpha, 0f, 1f)).endVertex();
    }

    private static float[] darker(float[] col) {
        return new float[]{col[0] * 0.76f, col[1] * 0.76f, col[2] * 0.76f};
    }

    private static float[] lighter(float[] col) {
        return new float[]{col[0] * 1.12f, col[1] * 1.12f, col[2] * 1.12f};
    }
}
