package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.SandWallSyncPacket;
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
import java.util.Random;

/**
 * Kum duvarlarini cizer.
 *
 * Onizleme saydam ve soluk; gercek duvar opak kum tonlarinda. Dikenler dis
 * yuzeyden cikan piramitler olarak ciziliyor ve sadece firlatma hazirliginda
 * gorunuyor.
 *
 * Bu gecici bir cizim: model asamasinda yerini sand_wall.bbmodel alacak.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class SandWallRenderer {

    // Doku artik gercek kum oldugu icin renkler DOKUYU BOYAMIYOR, sadece
    // hafif golgeleme yapiyor. Onceki koyu degerler kum dokusunu
    // camurlastiriyordu; 1.0 civari degerler kumu kendi renginde birakiyor.
    private static final float[] SAND_LIGHT = {1.0f, 1.0f, 1.0f};
    private static final float[] SAND_MID = {0.92f, 0.90f, 0.86f};
    private static final float[] SAND_DARK = {0.66f, 0.62f, 0.55f};

    /** Onizleme: dokunun uzerine sari bir ton -- "henuz gercek degil". */
    private static final float[] PREVIEW = {1.0f, 0.92f, 0.55f};

    private SandWallRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<SandWallSyncPacket.Entry> walls = ClientSandWallData.get();
        if (walls.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().last().pose().identity();
        RenderSystem.getModelViewStack().last().normal().identity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.disableCull();

        // DOKULU CIZIM -- sol tiktaki uzayan kolla ayni yontem.
        //
        // Duvar duz renkli kutulardan olusuyordu ve "kum" gibi degil
        // renkli bir blok gibi okunuyordu. Doku oyunun blok atlasindan.
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

        TextureAtlasSprite sprite = sandSprite();
        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();

        // ---- KATI DUVARLAR: opak, derinlik yazan ----
        //
        // Onceden hepsi saydam ve derinlik yazmadan ciziliyordu; onay
        // sonrasi duvar hala onizleme gibi parliyor ve icinden her sey
        // gorunuyordu. Kati duvar KATI cizilmeli: harman kapali, derinlik
        // acik. Kum dokusu ancak boyle kendi rengiyle gorunuyor.
        boolean anySolid = false;
        for (SandWallSyncPacket.Entry wall : walls) {
            if (!wall.preview()) { anySolid = true; break; }
        }

        if (anySolid) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);

            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (SandWallSyncPacket.Entry wall : walls) {
                if (!wall.preview()) drawWall(buf, matrix, wall, sprite);
            }
            tes.end();
        }

        // ---- ONIZLEME: saydam, derinlik yazmayan ----
        boolean anyPreview = false;
        for (SandWallSyncPacket.Entry wall : walls) {
            if (wall.preview()) { anyPreview = true; break; }
        }

        if (anyPreview) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(false);

            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (SandWallSyncPacket.Entry wall : walls) {
                if (wall.preview()) drawWall(buf, matrix, wall, sprite);
            }
            tes.end();
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        pose.popPose();
    }

    private static void drawWall(BufferBuilder buf, Matrix4f m, SandWallSyncPacket.Entry wall,
                                 TextureAtlasSprite sprite) {
        double yaw = Math.toRadians(wall.yaw());
        // Dis yuzey normali ve duvarin yan ekseni
        Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 side = new Vec3(facing.z, 0, -facing.x);
        Vec3 up = new Vec3(0, 1, 0);

        boolean preview = wall.preview();
        // Kati duvar TAM OPAK. Yarim saydam kalinca onay oncesi haliyle
        // ayni parlaklikta duruyor ve "kum" gibi okunmuyordu.
        float alpha = preview ? 0.32f : 1.0f;

        // Hasar aldikca koyulasip soluklasir
        float health = Mth.clamp(wall.health(), 0f, 1f);
        float[] body = preview ? PREVIEW : (health > 0.5f ? SAND_MID : SAND_DARK);
        float[] edge = preview ? PREVIEW : SAND_LIGHT;

        Vec3 c = wall.center();
        Vec3 hw = side.scale(wall.halfWidth());
        Vec3 hh = up.scale(wall.halfHeight());
        Vec3 hd = facing.scale(wall.halfDepth());

        box(buf, m, sprite, c, hw, hh, hd, body, alpha);

        // Kenar vurgusu — duvarin sinirlari belli olsun
        box(buf, m, sprite, c, hw.scale(1.02), hh.scale(0.06), hd.scale(1.02),
                edge, alpha * 0.8f);
        box(buf, m, sprite, c.add(0, wall.halfHeight() * 0.95, 0),
                hw.scale(1.02), hh.scale(0.05), hd.scale(1.02), edge, alpha * 0.8f);

        float spike = wall.spike();
        if (spike > 0.01f) {
            drawSpikes(buf, m, sprite, wall, facing, side, up, spike, alpha);
        }
    }

    /** Dis yuzeyden cikan dikenler — sadece firlatma hazirliginda gorunur. */
    private static void drawSpikes(BufferBuilder buf, Matrix4f m, TextureAtlasSprite sprite,
                                   SandWallSyncPacket.Entry wall,
                                   Vec3 facing, Vec3 side, Vec3 up, float spike, float alpha) {
        Random rng = new Random(Double.doubleToLongBits(wall.center().x) ^ 0x5A17D);

        int cols = 5;
        int rows = 3;
        double maxLen = 1.1 * spike;

        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                double u = (col + 0.5) / cols * 2.0 - 1.0;
                double v = (r + 0.5) / rows * 2.0 - 1.0;

                // Duzenli izgara yerine hafif dagilim — kum dikeni duzgun olmaz
                u += (rng.nextDouble() - 0.5) * 0.18;
                v += (rng.nextDouble() - 0.5) * 0.25;

                Vec3 base = wall.center()
                        .add(side.scale(u * wall.halfWidth() * 0.85))
                        .add(up.scale(v * wall.halfHeight() * 0.8))
                        .add(facing.scale(wall.halfDepth()));

                double len = maxLen * (0.65 + rng.nextDouble() * 0.55);
                double thick = 0.16 + rng.nextDouble() * 0.10;

                Vec3 tip = base.add(facing.scale(len));

                Vec3 a = base.add(side.scale(thick)).add(up.scale(thick));
                Vec3 b = base.add(side.scale(thick)).add(up.scale(-thick));
                Vec3 d = base.add(side.scale(-thick)).add(up.scale(-thick));
                Vec3 e = base.add(side.scale(-thick)).add(up.scale(thick));

                tri(buf, m, sprite, a, b, tip, SAND_LIGHT, alpha);
                tri(buf, m, sprite, b, d, tip, SAND_MID, alpha);
                tri(buf, m, sprite, d, e, tip, SAND_DARK, alpha);
                tri(buf, m, sprite, e, a, tip, SAND_MID, alpha);
            }
        }
    }

    /**
     * Kum dokusu, oyunun kendi blok atlasindan.
     *
     * Kendi doku dosyasi eklemek yerine oyunun kumu kullaniliyor; kaynak
     * paketi degistiren oyuncuda duvar da degisiyor ve arazideki kumla
     * ayni tonu tutuyor.
     */
    private static TextureAtlasSprite sandSprite() {
        return Minecraft.getInstance().getModelManager()
                .getBlockModelShaper()
                .getParticleIcon(Blocks.SAND.defaultBlockState());
    }

    /** Merkez + uc yari eksenden kutu. */
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

        // Genis yuzlerde doku BIRDEN COK kez tekrarlaniyor: tek bir 16x16
        // kum karesi bes blokluk duvara yayilsaydi bulanik bir leke gibi
        // gorunurdu. Tekrar sayisi duvarin gercek olcusunden geliyor,
        // boylece doku arazideki kum bloklariyla ayni olcekte kaliyor.
        float uRep = (float) (hw.length() * 2.0);
        float vRep = (float) (hh.length() * 2.0);
        float dRep = (float) (hd.length() * 2.0);

        face(buf, m, sprite, p001, p101, p111, p011, col, alpha, uRep, vRep);
        face(buf, m, sprite, p100, p000, p010, p110, col, alpha, uRep, vRep);
        face(buf, m, sprite, p000, p001, p011, p010, darker(col), alpha, dRep, vRep);
        face(buf, m, sprite, p101, p100, p110, p111, darker(col), alpha, dRep, vRep);
        face(buf, m, sprite, p010, p011, p111, p110, col, alpha, uRep, dRep);
        face(buf, m, sprite, p000, p100, p101, p001, darker(col), alpha, uRep, dRep);
    }

    private static float[] darker(float[] col) {
        return new float[]{col[0] * 0.78f, col[1] * 0.78f, col[2] * 0.78f};
    }

    private static void face(BufferBuilder buf, Matrix4f m, TextureAtlasSprite sprite,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float alpha,
                             float uRep, float vRep) {
        vert(buf, m, sprite, a, 0f, 0f, col, alpha);
        vert(buf, m, sprite, b, uRep, 0f, col, alpha);
        vert(buf, m, sprite, c, uRep, vRep, col, alpha);
        vert(buf, m, sprite, d, 0f, vRep, col, alpha);
    }

    private static void tri(BufferBuilder buf, Matrix4f m, TextureAtlasSprite sprite,
                            Vec3 a, Vec3 b, Vec3 c, float[] col, float alpha) {
        // Dortgen arayuzunde ucgen: son nokta tekrarlaniyor
        vert(buf, m, sprite, a, 0f, 0f, col, alpha);
        vert(buf, m, sprite, b, 1f, 0f, col, alpha);
        vert(buf, m, sprite, c, 0.5f, 1f, col, alpha);
        vert(buf, m, sprite, c, 0.5f, 1f, col, alpha);
    }

    /**
     * Doku koordinati sprite'in atlas dilimine esleniyor.
     *
     * Tekrar icin kesirli kisim aliniyor; dilim disina tasmak atlastaki
     * KOMSU dokuyu cizdirirdi.
     */
    private static void vert(BufferBuilder buf, Matrix4f m, TextureAtlasSprite sprite,
                             Vec3 p, float u, float v, float[] col, float alpha) {
        float uu = sprite.getU(wrap(u) * 16f);
        float vv = sprite.getV(wrap(v) * 16f);

        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .uv(uu, vv)
                .color(Mth.clamp(col[0], 0f, 1f), Mth.clamp(col[1], 0f, 1f),
                        Mth.clamp(col[2], 0f, 1f), Mth.clamp(alpha, 0f, 1f))
                .endVertex();
    }

    /** 0..1 arasina sarar ama tam 1'i 1 olarak birakir (0'a dusmesin). */
    private static float wrap(float value) {
        if (value <= 1f) return Mth.clamp(value, 0f, 1f);
        float frac = value % 1f;
        return frac == 0f ? 1f : frac;
    }
}
