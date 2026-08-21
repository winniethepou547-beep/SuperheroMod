package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

/**
 * SAND COLOSSUS'UN GOVDESI — bacaksiz dev.
 *
 * TASARIM NOTU: onceki surumde alt kum kutlesi ic ice gecen uc kutudan
 * olusuyordu ve duzgun bir PIRAMIT gibi duruyordu. Kum oyle durmaz; dagilir,
 * cokerken duzensiz yigilir ve sikisan yerleri TASLASIR.
 *
 * Bu yuzden kutle simdi:
 *   - farkli boyutlarda, kaydirilmis ve DONDURULMUS bloklardan olusuyor
 *   - araya sert kum tasi cikintileri sikistirilmis
 *   - hicbir katman simetrik degil
 *
 * Ayni mantik kollara ve topuza da uygulandi: duz kutu yerine kirik, kayali
 * yuzeyler.
 *
 * Olculer 10 blokluk (160 piksel) deve gore. Entity modellerinde y ASAGI
 * dogru artar: y=160 ayak hizasi, y=0 tepe.
 */
public final class ColossusModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(SuperheroMod.MODID, "sand_colossus"), "main");

    private final ModelPart root;

    public final ModelPart lowerMass;
    public final ModelPart torso;
    public final ModelPart head;
    public final ModelPart rightArm;
    public final ModelPart leftArm;
    public final ModelPart mace;

    public ColossusModel(ModelPart root) {
        this.root = root;
        this.lowerMass = root.getChild("lower_sand_mass");
        this.torso = root.getChild("torso");
        this.head = torso.getChild("head");
        this.rightArm = torso.getChild("right_arm");
        this.leftArm = torso.getChild("left_arm");
        this.mace = rightArm.getChild("mace");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        buildLowerMass(root);
        PartDefinition torso = buildTorso(root);
        buildHead(torso);
        buildArms(torso);

        return LayerDefinition.create(mesh, 16, 16);
    }

    // ------------------------------------------------------------------

    /**
     * ALT KUM KUTLESI — bacak yerine gecen dagilmis yigin.
     *
     * Duzenli katmanlar yerine farkli aci ve boyutlarda bloklar; her biri
     * kendi ekseninde hafifce dondurulmus. Piramit hissini kiran sey bu
     * donuslerin hicbirinin ayni olmamasi.
     */
    private static void buildLowerMass(PartDefinition root) {
        PartDefinition mass = root.addOrReplaceChild("lower_sand_mass",
                CubeListBuilder.create()
                        // Cekirdek govde — daralarak yukari cikar ama duzensiz
                        .texOffs(0, 0).addBox(-17f, 100f, -15f, 34, 22, 30)
                        .texOffs(0, 0).addBox(-22f, 118f, -20f, 43, 20, 39),
                PartPose.ZERO);

        // Dagilmis yigintilar — her biri farkli aci, boyut ve konumda
        addChunk(mass, "heap_a", -34f, 138f, -18f, 20, 22, 34, 0.10f, 0.35f, -0.08f);
        addChunk(mass, "heap_b", 14f, 134f, -24f, 24, 26, 22, -0.14f, -0.5f, 0.11f);
        addChunk(mass, "heap_c", -12f, 142f, 12f, 30, 18, 24, 0.08f, 0.9f, 0.06f);
        addChunk(mass, "heap_d", 18f, 144f, 6f, 22, 16, 26, -0.06f, 1.4f, -0.12f);
        addChunk(mass, "heap_e", -28f, 150f, -6f, 26, 10, 30, 0.05f, -1.1f, 0.09f);

        // Zemine yayilan ince etek — kutlenin yere degdigi yer
        addChunk(mass, "skirt_a", -38f, 152f, -34f, 76, 8, 40, 0f, 0.18f, 0f);
        addChunk(mass, "skirt_b", -30f, 154f, 2f, 62, 6, 34, 0f, -0.42f, 0f);

        // SERT KUM TASI cikintilari — kumun sikisip taslastigi yerler
        addChunk(mass, "rock_a", -26f, 126f, -26f, 14, 18, 14, 0.22f, 0.6f, 0.18f);
        addChunk(mass, "rock_b", 16f, 132f, 14f, 12, 20, 12, -0.18f, -0.9f, 0.24f);
        addChunk(mass, "rock_c", -8f, 112f, -22f, 11, 15, 11, 0.14f, 1.2f, -0.2f);
        addChunk(mass, "rock_d", 20f, 146f, -20f, 13, 12, 13, -0.25f, 0.3f, 0.16f);
    }

    /** TORSO — genis omuzlu, yuzeyi kirik. */
    private static PartDefinition buildTorso(PartDefinition root) {
        PartDefinition torso = root.addOrReplaceChild("torso",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-24f, 44f, -13f, 48, 36, 26)
                        .texOffs(0, 0).addBox(-19f, 78f, -12f, 38, 26, 24),
                PartPose.offset(0f, 0f, 0f));

        // Gogus levhasi — kristal buraya oturur
        addChunk(torso, "chest_plate", -15f, 52f, -19f, 30, 24, 8, 0.06f, 0f, 0f);

        // Govdeye sikismis kaya cikintilari
        addChunk(torso, "torso_rock_a", -27f, 58f, -6f, 10, 16, 14, 0.12f, 0.4f, 0.3f);
        addChunk(torso, "torso_rock_b", 18f, 66f, -8f, 11, 14, 15, -0.1f, -0.35f, -0.26f);
        addChunk(torso, "torso_rock_c", -10f, 82f, 11f, 20, 14, 9, 0.18f, 0.15f, 0.08f);

        return torso;
    }

    /** KAFA — govdeye gore KUCUK, agirlik hissi bundan geliyor. */
    private static void buildHead(PartDefinition torso) {
        PartDefinition head = torso.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-13f, -24f, -13f, 26, 24, 26),
                PartPose.offset(0f, 44f, 0f));

        // Cene ve alin cikintilari — duz kutu gorunmesin
        addChunk(head, "jaw", -10f, -6f, -16f, 20, 8, 8, 0.14f, 0f, 0f);
        addChunk(head, "brow", -12f, -27f, -11f, 24, 6, 8, -0.1f, 0f, 0.05f);
        addChunk(head, "head_rock_a", -15f, -20f, -4f, 7, 12, 10, 0.2f, 0.5f, 0.25f);
        addChunk(head, "head_rock_b", 9f, -17f, -6f, 8, 10, 11, -0.16f, -0.4f, -0.2f);
    }

    /** KOLLAR — cok kalin, yuzeyi kayali. Topuz sag kolun ucunda. */
    private static void buildArms(PartDefinition torso) {
        PartDefinition right = torso.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-17f, -9f, -10f, 20, 30, 20)
                        .texOffs(0, 0).addBox(-16f, 21f, -9f, 18, 26, 18),
                PartPose.offset(-26f, 52f, 0f));

        PartDefinition left = torso.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3f, -9f, -10f, 20, 30, 20)
                        .texOffs(0, 0).addBox(-2f, 21f, -9f, 18, 26, 18),
                PartPose.offset(26f, 52f, 0f));

        // Omuz yumrulari — kristaller buraya oturur
        addChunk(right, "right_shoulder", -21f, -16f, -13f, 26, 18, 26, 0.08f, 0.3f, 0.12f);
        addChunk(left, "left_shoulder", -5f, -16f, -13f, 26, 18, 26, 0.08f, -0.3f, -0.12f);

        // Kollara sikismis kaya cikintilari
        addChunk(right, "r_rock_a", -22f, 4f, -6f, 9, 14, 13, 0.15f, 0.6f, 0.3f);
        addChunk(right, "r_rock_b", -12f, 28f, -14f, 12, 12, 8, -0.2f, 0.2f, -0.15f);
        addChunk(left, "l_rock_a", 14f, 6f, -7f, 9, 13, 14, -0.12f, -0.55f, -0.28f);
        addChunk(left, "l_rock_b", 1f, 30f, -14f, 12, 11, 8, 0.18f, -0.25f, 0.14f);

        // Eller — kollardan genis, kaba yumruklar
        addChunk(right, "right_hand", -19f, 44f, -12f, 24, 22, 24, 0.05f, 0.2f, 0.06f);
        addChunk(left, "left_hand", -5f, 44f, -12f, 24, 22, 24, 0.05f, -0.2f, -0.06f);

        buildMace(right);
    }

    /**
     * TOPUZ — duz bir sopa ve kure degil, KIRIK KAYA KUTLESI.
     *
     * Bas tek kutu yerine farkli acilarda ust uste binmis bloklardan olusuyor
     * ve dort yana duzensiz dikenler cikiyor.
     */
    private static void buildMace(PartDefinition rightArm) {
        PartDefinition mace = rightArm.addOrReplaceChild("mace",
                CubeListBuilder.create()
                        // Sap — kaba, kopruk
                        .texOffs(0, 0).addBox(-5f, -40f, -5f, 10, 56, 10),
                PartPose.offset(-7f, 52f, 0f));

        // Bas: ust uste binmis, dondurulmus kaya bloklari
        addChunk(mace, "head_core", -16f, -62f, -16f, 32, 26, 32, 0.10f, 0.4f, 0.08f);
        addChunk(mace, "head_a", -20f, -56f, -12f, 12, 18, 24, -0.14f, 0.8f, 0.2f);
        addChunk(mace, "head_b", 8f, -58f, -14f, 14, 20, 26, 0.16f, -0.6f, -0.18f);
        addChunk(mace, "head_c", -14f, -68f, -10f, 26, 14, 22, 0.06f, 1.3f, 0.1f);

        // Duzensiz dikenler
        addChunk(mace, "spike_a", -27f, -52f, -6f, 10, 12, 12, 0.2f, 0.5f, 0.35f);
        addChunk(mace, "spike_b", 19f, -50f, -7f, 11, 13, 13, -0.24f, -0.4f, -0.3f);
        addChunk(mace, "spike_c", -7f, -50f, -27f, 13, 12, 10, 0.28f, 0.2f, 0.15f);
        addChunk(mace, "spike_d", -6f, -54f, 18f, 12, 14, 11, -0.22f, -0.15f, 0.25f);
        addChunk(mace, "spike_e", -8f, -74f, -8f, 16, 12, 16, 0.12f, 0.7f, -0.1f);
    }

    // ------------------------------------------------------------------

    /**
     * Dondurulmus bir blok ekler.
     *
     * Donus SART: eksene hizali kutular kumu duzgun bir piramit gibi
     * gosteriyordu. Kucuk acilar bile yigini dagitiyor.
     */
    private static void addChunk(PartDefinition parent, String name,
                                 float x, float y, float z,
                                 int w, int h, int d,
                                 float xRot, float yRot, float zRot) {
        parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, w, h, d),
                PartPose.rotation(xRot, yRot, zRot));
    }

    public ModelPart root() {
        return root;
    }
}
