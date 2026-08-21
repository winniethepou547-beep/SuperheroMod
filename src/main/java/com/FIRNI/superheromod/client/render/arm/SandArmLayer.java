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
    private static final float SWELL_PX = 2.0f;

    /** Ucta eklenen fazladan kalinlik — kol uca dogru konik acilir. */
    /**
     * Ucta eklenen fazladan kalinlik.
     *
     * Kalinlasma UCA TOPLANIYOR (kup egri), boyuna esit dagilmiyor: esit
     * dagilinca kol bastan sona genisleyen bir huni gibi duruyordu.
     * Simdi govde neredeyse esit kalinlikta, son bogumlarda belirgin
     * sekilde sisiyor -- kutle ucta.
     */
    private static final float TIP_SWELL_PX = 3.2f;

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

            float remaining = totalPx - i * SEGMENT_PX;
            if (remaining <= 0.05f) break;
            float height = Math.min(SEGMENT_PX, remaining);

            // Uca dogru kalinlasma — KUP EGRI ile uca toplaniyor.
            // Dogrusal olsaydi kol bastan sona genisleyen bir huni gibi
            // duruyordu; simdi govde neredeyse esit, kutle ucta.
            float along = segments <= 1 ? 1f : i / (float) (segments - 1);
            float taper = along * along * along;
            float swell = SWELL_PX + taper * TIP_SWELL_PX + (i % 2 == 0 ? 0.18f : 0f);

            float vShift = (i % 3) * 0.3f;

            box(poseStack, buf, sprite, packedLight,
                    xOrigin - swell, from, -2f - swell,
                    width + swell * 2f, height, 4f + swell * 2f, vShift);

            // ICINDEN CIKAN KAYA PARCALARI.
            //
            // Duz bir kum borusu "icinde kaya var" hissi vermiyordu.
            // Parcalar govdeden DISARI tasiyor ve her biri farkli yonde;
            // hizali olsalardi cikinti degil kaburga gibi gorunurlerdi.
            addChunks(poseStack, buf, sprite, packedLight, i,
                    xOrigin + width * 0.5f, from + height * 0.5f,
                    (width + swell * 2f) * 0.5f);
        }
        poseStack.popPose();
    }

    /**
     * Kolun icinden cikan kaya parcalari.
     *
     * Cizimde kolun her yerinden farkli acilarda bloklar tasiyor; amac
     * "icinde kaya var" izlenimi. Parcalar govde yuzeyinden DISARI cikiyor
     * ve konumlari bogum sirasindan turetiliyor -- rastgele sayi
     * kullanilsaydi her karede yer degistirir, kol kaynayan bir kutle
     * gibi gorunurdu.
     */
    private static void addChunks(PoseStack poseStack, VertexConsumer buf,
                                  TextureAtlasSprite sprite, int light, int index,
                                  float cx, float cy, float halfWidth) {
        // Bogum basina uc parca; her biri govdenin farkli bir yuzunde
        for (int k = 0; k < 3; k++) {
            int seed = index * 7 + k * 13;

            // Yon: govde cevresinde dagilmis acilar
            double angle = (seed % 8) * (Math.PI / 4.0) + (index % 2) * 0.4;
            float ox = (float) Math.cos(angle) * halfWidth;
            float oz = (float) Math.sin(angle) * halfWidth;

            // Boy: parcalar esit olmasin, kirikli siluet olussun
            float size = 2.2f + (seed % 5) * 0.55f;

            // Kol ekseni boyunca kaydirma — hepsi bogumun ortasinda
            // toplanmasin
            float oy = ((seed % 3) - 1) * 1.8f;

            box(poseStack, buf, sprite, light,
                    cx + ox - size * 0.5f, cy + oy - size * 0.5f, oz - size * 0.5f,
                    size, size, size, (seed % 3) * 0.3f);
        }
    }

    /**
     * BALYOZ — kol tam uzunlukta TUTULDUGUNDA ucta olusan kutle.
     *
     * Cizimdeki gibi kola DIK duran uzun bir bas: yatayda genis, dikeyde
     * alcak. Kolun devami olan bir kup degil, gercekten bir alet silueti.
     * Bu yuzden en (side) ekseninde uzuyor, kol ekseninde degil.
     *
     * Basin ortasindan gecen bir boyun var; olmasaydi bas kolun ucuna
     * yapistirilmis gibi durur, sap iceri girmis gibi gorunmezdi.
     *
     * Olcek balyoz orani ile buyur: kutle bir anda belirmiyor, kol tam
     * boyda beklerken kum uzerinde topluyor.
     */
    private static void drawHammer(PoseStack poseStack, VertexConsumer buf,
                                   TextureAtlasSprite sprite, int light,
                                   float xOrigin, float width, float tipY, float hammer) {
        float g = Mth.clamp(hammer, 0f, 1f);

        // Basin olculeri (piksel). Genislik boyunun cok uzerinde; cizimde
        // de bas uzun ve yassi.
        // Balyoz da kolla AYNI ORANDA kalinlasti: kol sismisken bas ayni
        // kalirsa alet degil kolun ucundaki cikinti gibi duruyor.
        float headLen = 30f * g;      // side ekseni boyunca
        float headTall = 13f * g;     // kol ekseni boyunca
        float headDeep = 13f * g;     // derinlik

        float cx = xOrigin + width * 0.5f;

        // BAS: kola dik, ortasi kolun ucunda
        box(poseStack, buf, sprite, light,
                cx - headLen * 0.5f, tipY + 1f, -headDeep * 0.5f,
                headLen, headTall, headDeep, 0.1f);

        // Uc kapaklari — basin iki ucunda hafif genisleme; duz bir kutu
        // balyozdan cok kalas gibi duruyordu
        float capLen = 3.5f * g;
        float capTall = headTall * 1.22f;
        float capDeep = headDeep * 1.22f;

        box(poseStack, buf, sprite, light,
                cx - headLen * 0.5f - capLen, tipY + 1f - (capTall - headTall) * 0.5f,
                -capDeep * 0.5f, capLen, capTall, capDeep, 0.45f);
        box(poseStack, buf, sprite, light,
                cx + headLen * 0.5f, tipY + 1f - (capTall - headTall) * 0.5f,
                -capDeep * 0.5f, capLen, capTall, capDeep, 0.45f);

        // BALYOZUN UZERINDE de kaya parcalari — cizimde cekic tarafi da
        // ayni dokuda ve ayni oranda kalin
        for (int k = 0; k < 5; k++) {
            int seed = k * 11 + 3;
            float ox = ((seed % 5) - 2) * headLen * 0.2f;
            float oz = ((seed % 3) - 1) * headDeep * 0.45f;
            float size = (2.6f + (seed % 4) * 0.7f) * g;

            box(poseStack, buf, sprite, light,
                    cx + ox - size * 0.5f, tipY + 1f + headTall * 0.5f - size * 0.5f,
                    oz - size * 0.5f, size, size, size, (seed % 3) * 0.3f);
        }

        // BOYUN: sapin basa girdigi yer
        float neck = 6.5f * g;
        box(poseStack, buf, sprite, light,
                cx - neck * 0.5f, tipY - 1.5f, -neck * 0.5f,
                neck, 3.5f, neck, 0.72f);
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
                            float sizeX, float sizeY, float sizeZ, float vShift) {
        float x0 = x / PX, x1 = (x + sizeX) / PX;
        float y0 = y / PX, y1 = (y + sizeY) / PX;
        float z0 = z / PX, z1 = (z + sizeZ) / PX;

        // Dokuyu UZUNLUGA ORANTILI esle: her bogumda 0..1 tekrarlansaydi
        // kum deseni bogum sinirlarinda goze batacak sekilde sikisirdi.
        float v0 = vShift;
        float v1 = vShift + sizeY / PX;

        // Yan yuzler
        quad(poseStack, buf, sprite, light, 0f, 0f, -1f,
                x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, v0, v1);
        quad(poseStack, buf, sprite, light, 0f, 0f, 1f,
                x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, v0, v1);
        quad(poseStack, buf, sprite, light, -1f, 0f, 0f,
                x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, v0, v1);
        quad(poseStack, buf, sprite, light, 1f, 0f, 0f,
                x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, v0, v1);

        // Uc yuzler -- kesit oldugu icin dokunun tamamini kullaniyorlar
        quad(poseStack, buf, sprite, light, 0f, -1f, 0f,
                x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0f, 1f);
        quad(poseStack, buf, sprite, light, 0f, 1f, 0f,
                x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f);
    }

    private static void quad(PoseStack poseStack, VertexConsumer buf, TextureAtlasSprite sprite,
                             int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float v0, float v1) {
        // U her zaman dokunun tam genisligi; kol ince oldugu icin yatayda
        // dilimlemenin gorsel karsiligi yok. Dikeyde ise her bogum farkli
        // bir dilim aliyor, tekrar bu sayede fark edilmiyor.
        vertex(poseStack, buf, sprite, light, nx, ny, nz, ax, ay, az, 0f, v0);
        vertex(poseStack, buf, sprite, light, nx, ny, nz, bx, by, bz, 1f, v0);
        vertex(poseStack, buf, sprite, light, nx, ny, nz, cx, cy, cz, 1f, v1);
        vertex(poseStack, buf, sprite, light, nx, ny, nz, dx, dy, dz, 0f, v1);
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
