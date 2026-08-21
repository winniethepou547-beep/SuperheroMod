package com.FIRNI.superheromod.client.render.arm;

import com.FIRNI.superheromod.client.render.ClientSandArmData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;

/**
 * UZAYAN KUM KOLU.
 *
 * Kol OYUNCU MODELININ KATMANI olarak ciziliyor, dunya uzayinda degil.
 * Omuz donusumunun (translateAndRotate) icine girildigi icin ek parcalar
 * kolun tam ucundan devam ediyor ve oyuncu hareket etse de bedenden
 * kopmuyor.
 *
 * Geometri ModelPart ile DEGIL elle uretiliyor. Sebep doku: ModelPart'in
 * UV'leri 64x64 skin duzenine gore pisiriliyor, oysa uzayan kisim oyunun
 * KUM DOKUSUYLA kaplanmali. Kum blok atlasinda yasadigi icin UV'lerin
 * calisma aninda sprite'tan alinmasi gerekiyor -- pisirilmis bir mesh
 * bunu yapamaz.
 *
 * Bicim yine vanilla kol olculerinde: 4x4 piksel kesit (slim'de 3x4),
 * 6 piksellik bogumlar. Boylece uzayan kisim baska bir oyundan gelmis
 * gibi durmuyor, sadece kum kaplanmis bir kol gibi duruyor.
 */
public class SandArmLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Vanilla kol 12 piksel; ek parcalar bunun ucundan basliyor. */
    private static final float ARM_LENGTH_PX = 10.0f;
    /** Tek bogumun yuksekligi (piksel) — vanilla on kolla ayni. */
    private static final float SEGMENT_PX = 6.0f;
    private static final float PX = 16.0f;

    /** Cok uzun kolda parca sayisi patlamasin. */
    private static final int MAX_SEGMENTS = 26;

    /**
     * Kum kabugu kolun kendisinden biraz kalin.
     *
     * Ayni olcude olsaydi vanilla kolun sleeve katmaniyla ayni duzlemde
     * kalir ve z-fighting yapardi; ayrica kum "uzerine sarilmis" gibi
     * durmazdi.
     */
    private static final float SWELL_PX = 0.6f;

    public SandArmLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        float lengthBlocks = ClientSandArmData.lengthOf(player.getId());
        if (lengthBlocks <= 0.02f) return;

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        if (!model.rightArm.visible) return;

        boolean slim = "slim".equals(player.getModelName());
        float width = slim ? 3f : 4f;
        float xOrigin = slim ? -2f : -3f;

        TextureAtlasSprite sprite = sandSprite();
        VertexConsumer buf = buffer.getBuffer(
                RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));

        poseStack.pushPose();

        // Omuz uzayina gir. Konum ve donus KOLUN KENDISINDEN geliyor;
        // bu yuzden ek parcalar kolun ucunda dogru acida duruyor.
        model.rightArm.translateAndRotate(poseStack);

        float totalPx = lengthBlocks * PX;
        int segments = Math.min(MAX_SEGMENTS, (int) Math.ceil(totalPx / SEGMENT_PX));

        for (int i = 0; i < segments; i++) {
            float from = ARM_LENGTH_PX + i * SEGMENT_PX;

            // Son bogum tasmasin: kalan uzunluk kadar kisaliyor, yoksa
            // kol hedeflenenden uzun gorunuyordu
            float remaining = totalPx - i * SEGMENT_PX;
            if (remaining <= 0.05f) break;
            float height = Math.min(SEGMENT_PX, remaining);

            // Bogumlar sirayla azicik farkli kalinlikta: tamamen duz bir
            // boru yerine kum yigilmasi gibi kirikli bir siluet
            float swell = SWELL_PX + (i % 2 == 0 ? 0.18f : 0f);

            box(poseStack, buf, sprite, packedLight,
                    xOrigin - swell, from, -2f - swell,
                    width + swell * 2f, height, 4f + swell * 2f);
        }

        // YUMRUK — kolun ucundaki kutle. Onsuz kol duz bir boru gibi
        // bitiyor ve neyin vurdugu belli olmuyor.
        float fistSwell = SWELL_PX + 1.1f;
        box(poseStack, buf, sprite, packedLight,
                xOrigin - fistSwell, ARM_LENGTH_PX + totalPx - 1f, -2f - fistSwell,
                width + fistSwell * 2f, 5f, 4f + fistSwell * 2f);

        poseStack.popPose();
    }

    /**
     * Kum dokusu, oyunun kendi blok atlasindan.
     *
     * Kendi doku dosyasi eklemek yerine oyunun kumu kullaniliyor; kaynak
     * paketi degistiren oyuncuda kol da degisiyor ve arazideki kumla ayni
     * tonu tutuyor.
     */
    private static TextureAtlasSprite sandSprite() {
        return Minecraft.getInstance().getModelManager()
                .getBlockModelShaper()
                .getParticleIcon(Blocks.SAND.defaultBlockState());
    }

    // ------------------------------------------------------------------
    // Geometri
    // ------------------------------------------------------------------

    /**
     * Model uzayinda kutu.
     *
     * Olculer PIKSEL cinsinden aliniyor (vanilla model duzeni) ve burada
     * bloga cevriliyor; ModelPart de ayni donusumu yapiyor, boylece
     * olculer vanilla kol tanimlariyla dogrudan karsilastirilabilir
     * kaliyor.
     */
    private static void box(PoseStack poseStack, VertexConsumer buf, TextureAtlasSprite sprite,
                            int light, float x, float y, float z,
                            float sizeX, float sizeY, float sizeZ) {
        float x0 = x / PX, x1 = (x + sizeX) / PX;
        float y0 = y / PX, y1 = (y + sizeY) / PX;
        float z0 = z / PX, z1 = (z + sizeZ) / PX;

        // Dokuyu UZUNLUGA ORANTILI esle: her bogumda 0..1 tekrarlansaydi
        // kum deseni bogum sinirlarinda goze batacak sekilde sikisirdi.
        float vSpan = sizeY / PX;

        // Yan yuzler
        quad(poseStack, buf, sprite, light, 0f, 0f, -1f,
                x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 1f, vSpan);
        quad(poseStack, buf, sprite, light, 0f, 0f, 1f,
                x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 1f, vSpan);
        quad(poseStack, buf, sprite, light, -1f, 0f, 0f,
                x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, 1f, vSpan);
        quad(poseStack, buf, sprite, light, 1f, 0f, 0f,
                x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1f, vSpan);

        // Uc yuzler
        quad(poseStack, buf, sprite, light, 0f, -1f, 0f,
                x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 1f, 1f);
        quad(poseStack, buf, sprite, light, 0f, 1f, 0f,
                x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 1f, 1f);
    }

    private static void quad(PoseStack poseStack, VertexConsumer buf, TextureAtlasSprite sprite,
                             int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float uSpan, float vSpan) {
        vertex(poseStack, buf, sprite, light, nx, ny, nz, ax, ay, az, 0f, 0f);
        vertex(poseStack, buf, sprite, light, nx, ny, nz, bx, by, bz, uSpan, 0f);
        vertex(poseStack, buf, sprite, light, nx, ny, nz, cx, cy, cz, uSpan, vSpan);
        vertex(poseStack, buf, sprite, light, nx, ny, nz, dx, dy, dz, 0f, vSpan);
    }

    private static void vertex(PoseStack poseStack, VertexConsumer buf, TextureAtlasSprite sprite,
                               int light, float nx, float ny, float nz,
                               float px, float py, float pz, float u, float v) {
        PoseStack.Pose pose = poseStack.last();

        // Sprite atlasin bir dilimi; 0..1 araligi 0..16 piksele esleniyor.
        //
        // Deger KIRPILIYOR, sarmalanmiyor: modulo kullanilsaydi 1.0 tam
        // olarak 0'a duser ve yuzun U araligi cokerdi. Buradaki tum
        // araliklar zaten 0..1 icinde kaldigi icin kirpma dogru davranis.
        // Atlas dilimi disina tasmak komsu dokuyu cizdirirdi.
        float uu = sprite.getU(Mth.clamp(u, 0f, 1f) * 16f);
        float vv = sprite.getV(Mth.clamp(v, 0f, 1f) * 16f);

        buf.vertex(pose.pose(), px, py, pz)
                .color(255, 255, 255, 255)
                .uv(uu, vv)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }
}
