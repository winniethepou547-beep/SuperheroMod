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
 * Dokumandaki hiyerarsi korunuyor: alt kum kutlesi, torso, kafa, iki dev kol,
 * eller ve topuz. Bacak YOK; alt kisim asagi dogru genisleyen bir kum
 * kutlesi.
 *
 * Model kodla kuruluyor cunku Blockbench tarafinda bu parca zorlayici.
 * Olculer 10 blokluk (160 piksel) deve gore:
 *   alt kutle   y = 104 .. 160
 *   torso       y =  40 .. 104
 *   kafa        y =   8 ..  40
 *   omuzlar     y ~  48
 *
 * NOT: entity modellerinde y ASAGI dogru artar, yani y=160 ayak hizasi,
 * y=0 tepe. Bu yuzden sayilar ters okunur.
 *
 * Doku vanilla kum blogu (16x16) — tum yuzler ayni bolgeden orneklem alir,
 * kum duzensiz oldugu icin tekrar goze batmaz ve ayri doku dosyasi
 * gerekmez.
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
    public final ModelPart rightHand;
    public final ModelPart leftHand;
    public final ModelPart mace;

    public ColossusModel(ModelPart root) {
        this.root = root;
        this.lowerMass = root.getChild("lower_sand_mass");
        this.torso = root.getChild("torso");
        this.head = torso.getChild("head");
        this.rightArm = torso.getChild("right_arm");
        this.leftArm = torso.getChild("left_arm");
        this.rightHand = rightArm.getChild("right_hand");
        this.leftHand = leftArm.getChild("left_hand");
        this.mace = rightHand.getChild("mace");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // --- ALT KUM KUTLESI: bacak yerine gecen, asagi genisleyen yigin ---
        // Ust uste uc katman; her katman daha genis, kenarlar kaydirilmis ki
        // duzgun bir koni degil duzensiz bir kutle gorunsun.
        PartDefinition lower = root.addOrReplaceChild("lower_sand_mass",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-20f, 104f, -20f, 40, 20, 40)
                        .texOffs(0, 0).addBox(-26f, 124f, -26f, 52, 18, 52)
                        .texOffs(0, 0).addBox(-32f, 142f, -32f, 64, 18, 64),
                PartPose.ZERO);

        // Kutlenin cevresine tasan duzensiz yigintilar
        lower.addOrReplaceChild("mass_chunk_a",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-38f, 148f, -14f, 12, 12, 28),
                PartPose.ZERO);
        lower.addOrReplaceChild("mass_chunk_b",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(26f, 146f, -18f, 12, 14, 30),
                PartPose.ZERO);

        // --- TORSO: genis omuzlu, asagi daralan agir govde ---
        PartDefinition torso = root.addOrReplaceChild("torso",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-24f, 44f, -13f, 48, 36, 26)
                        .texOffs(0, 0).addBox(-19f, 80f, -11f, 38, 26, 22),
                PartPose.offset(0f, 0f, 0f));

        // Gogus cikintisi — kristal buraya oturacak
        torso.addOrReplaceChild("chest_plate",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-14f, 54f, -18f, 28, 22, 6),
                PartPose.ZERO);

        // --- KAFA: govdeye gore KUCUK, one egik duran agir bir kutle ---
        PartDefinition head = torso.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-13f, -26f, -13f, 26, 26, 26),
                PartPose.offset(0f, 44f, 0f));

        head.addOrReplaceChild("head_crest",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-9f, -33f, -6f, 18, 8, 14),
                PartPose.ZERO);

        // --- KOLLAR: cok kalin, dizlere kadar inen ---
        PartDefinition rightArm = torso.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-16f, -8f, -9f, 18, 46, 18),
                PartPose.offset(-26f, 52f, 0f));

        PartDefinition leftArm = torso.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2f, -8f, -9f, 18, 46, 18),
                PartPose.offset(26f, 52f, 0f));

        // Omuz yumrulari — kristaller buraya oturacak
        rightArm.addOrReplaceChild("right_shoulder",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-19f, -14f, -12f, 24, 16, 24),
                PartPose.ZERO);
        leftArm.addOrReplaceChild("left_shoulder",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5f, -14f, -12f, 24, 16, 24),
                PartPose.ZERO);

        // --- ELLER: kollardan daha genis, kaba yumruklar ---
        rightArm.addOrReplaceChild("right_hand",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-18f, 0f, -11f, 22, 20, 22),
                PartPose.offset(0f, 38f, 0f));

        leftArm.addOrReplaceChild("left_hand",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4f, 0f, -11f, 22, 20, 22),
                PartPose.offset(0f, 38f, 0f));

        // --- TOPUZ: sag elin icinde, ABARTILI buyuk ---
        PartDefinition mace = rightArm.getChild("right_hand")
                .addOrReplaceChild("mace",
                        CubeListBuilder.create()
                                // sap
                                .texOffs(0, 0).addBox(-4f, -44f, -4f, 8, 56, 8),
                        PartPose.offset(-7f, 10f, 0f));

        // Topuz basi + cikintilar
        mace.addOrReplaceChild("mace_head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-15f, -62f, -15f, 30, 26, 30)
                        // dort yana cikan dikenler
                        .texOffs(0, 0).addBox(-21f, -54f, -6f, 6, 12, 12)
                        .texOffs(0, 0).addBox(15f, -54f, -6f, 6, 12, 12)
                        .texOffs(0, 0).addBox(-6f, -54f, -21f, 12, 12, 6)
                        .texOffs(0, 0).addBox(-6f, -54f, 15f, 12, 12, 6),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 16, 16);
    }

    public ModelPart root() {
        return root;
    }
}
