package com.FIRNI.superheromod.client.render.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * KUM GEOMETRISI — duvar, el, kaya gibi dunya sekilleri icin ortak cizim.
 *
 * Onceden bu sekiller POSITION_TEX_COLOR ile ciziliyordu ve o shader NE
 * ISIK NE DE YUZ GOLGELEMESI yapiyor. Sonuc her yerde tam parlaklikta,
 * dumduz aydinlanmis kum kutulariydi: gece yaniyorlardi, gunduz de
 * oyunun kendi kum bloklarindan belirgin sekilde daha parlak
 * duruyorlardi.
 *
 * Burasi vanilla blok cizimindeki iki seyi geri getiriyor:
 *
 *  1. DUNYA ISIGI — o noktadaki blok/gok isigi.
 *  2. YUZ GOLGELEMESI — vanilla her blok yuzunu farkli oranda karartir
 *     (ust 1.0, yan 0.8/0.6, alt 0.5). Bu olmadan kup, hacmi olmayan
 *     duz bir leke gibi gorunuyor.
 */
public final class SandGeometry {

    // Vanilla'nin yon golgeleme oranlari
    private static final float SHADE_TOP = 1.00f;
    private static final float SHADE_BOTTOM = 0.50f;
    private static final float SHADE_Z = 0.80f;
    private static final float SHADE_X = 0.60f;

    private SandGeometry() {}

    /** Oyunun kum dokusu, blok atlasindan. */
    public static TextureAtlasSprite sandSprite() {
        return Minecraft.getInstance().getModelManager()
                .getBlockModelShaper()
                .getParticleIcon(Blocks.SAND.defaultBlockState());
    }

    /** O noktadaki paketlenmis isik degeri. */
    public static int lightAt(Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0xF000F0;
        return LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos));
    }

    /**
     * Merkez + uc yari eksenden kutu.
     *
     * Eksenler serbest yonlu olabilir; yuz golgelemesi bu yuzden sabit
     * yon adlarina degil, yuzun GERCEK normaline gore secliyor.
     */
    public static void box(VertexConsumer buf, Matrix4f m, TextureAtlasSprite sprite,
                           int light, Vec3 c, Vec3 hw, Vec3 hh, Vec3 hd,
                           float[] tint, float alpha, float texScale) {
        Vec3 p000 = c.subtract(hw).subtract(hh).subtract(hd);
        Vec3 p100 = c.add(hw).subtract(hh).subtract(hd);
        Vec3 p110 = c.add(hw).add(hh).subtract(hd);
        Vec3 p010 = c.subtract(hw).add(hh).subtract(hd);
        Vec3 p001 = c.subtract(hw).subtract(hh).add(hd);
        Vec3 p101 = c.add(hw).subtract(hh).add(hd);
        Vec3 p111 = c.add(hw).add(hh).add(hd);
        Vec3 p011 = c.subtract(hw).add(hh).add(hd);

        // Doku TEKRAR SAYISI sekli gercek olcusune gore: tek bir 16x16 kum
        // karesi bes blokluk duvara yayilsaydi bulanik bir leke olurdu ve
        // arazideki kum bloklariyla olcek tutmazdi.
        float uw = (float) (hw.length() * 2.0) * texScale;
        float uh = (float) (hh.length() * 2.0) * texScale;
        float ud = (float) (hd.length() * 2.0) * texScale;

        face(buf, m, sprite, light, p001, p101, p111, p011, hd, tint, alpha, uw, uh);
        face(buf, m, sprite, light, p100, p000, p010, p110, hd.scale(-1), tint, alpha, uw, uh);
        face(buf, m, sprite, light, p000, p001, p011, p010, hw.scale(-1), tint, alpha, ud, uh);
        face(buf, m, sprite, light, p101, p100, p110, p111, hw, tint, alpha, ud, uh);
        face(buf, m, sprite, light, p010, p011, p111, p110, hh, tint, alpha, uw, ud);
        face(buf, m, sprite, light, p000, p100, p101, p001, hh.scale(-1), tint, alpha, uw, ud);
    }

    public static void face(VertexConsumer buf, Matrix4f m, TextureAtlasSprite sprite,
                            int light, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal,
                            float[] tint, float alpha, float uRep, float vRep) {
        Vec3 n = normal.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : normal.normalize();
        float shade = shadeFor(n);

        float r = tint[0] * shade;
        float g = tint[1] * shade;
        float bl = tint[2] * shade;

        vert(buf, m, sprite, light, a, 0f, 0f, r, g, bl, alpha);
        vert(buf, m, sprite, light, b, uRep, 0f, r, g, bl, alpha);
        vert(buf, m, sprite, light, c, uRep, vRep, r, g, bl, alpha);
        vert(buf, m, sprite, light, d, 0f, vRep, r, g, bl, alpha);
    }

    /**
     * Yuzun bakis yonune gore vanilla karartmasi.
     *
     * Yon serbest oldugu icin en baskin eksen seciliyor; ara acilarda
     * komsu iki oran arasinda geciyor, boylece dondurulmus parcalarda da
     * kademe kirilmasi olmuyor.
     */
    private static float shadeFor(Vec3 n) {
        float up = (float) n.y;
        float horizontal = (float) Math.sqrt(n.x * n.x + n.z * n.z);

        float vertical = up >= 0 ? SHADE_TOP : SHADE_BOTTOM;

        // Yatayda x ve z yuzleri farkli karariyor
        float hx = (float) Math.abs(n.x);
        float hz = (float) Math.abs(n.z);
        float side = horizontal < 1.0E-4 ? SHADE_Z
                : (SHADE_X * hx + SHADE_Z * hz) / horizontal;

        float t = Math.abs(up);
        return Mth.lerp(t, side, vertical);
    }

    public static void vert(VertexConsumer buf, Matrix4f m, TextureAtlasSprite sprite,
                            int light, Vec3 p, float u, float v,
                            float r, float g, float b, float alpha) {
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(Mth.clamp(r, 0f, 1f), Mth.clamp(g, 0f, 1f),
                        Mth.clamp(b, 0f, 1f), Mth.clamp(alpha, 0f, 1f))
                .uv(sprite.getU(wrap(u) * 16f), sprite.getV(wrap(v) * 16f))
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(1f, 0f, 0f)
                .endVertex();
    }

    /** 0..1 arasina sarar ama tam 1'i 1 birakir (0'a dusmesin). */
    public static float wrap(float value) {
        if (value <= 1f) return Mth.clamp(value, 0f, 1f);
        float frac = value % 1f;
        return frac == 0f ? 1f : frac;
    }
}
