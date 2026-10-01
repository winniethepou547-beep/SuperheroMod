package com.FIRNI.superheromod.client.render.puppet;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

/**
 * SAHNE AKTORUNUN MODELI — oyuncunun kendi skinini kullanir ama vanilla
 * modelden cok daha fazla eklemi vardir.
 *
 * ModelPart hierarchy supplies the joint transforms used by poses and IK.
 * The body and limbs are rendered by PuppetSkinSurface as continuous weighted
 * surfaces, rather than rendering the separate joint cubes below. The head
 * remains rigid. Each limb has two influences:
 *
 *   govde  -> gogus + kalca      (bel donusu ve geriye bukulme)
 *   kollar -> ust kol + on kol   (dirsek)
 *   bacaklar -> ust + alt        (diz — comelme ve direnme icin sart)
 *
 * Surface UVs cover the complete limb; there are no internal elbow/knee caps.
 */
public final class PuppetModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(SuperheroMod.MODID, "cinematic_puppet"), "main");
    public static final ModelLayerLocation LAYER_SLIM = new ModelLayerLocation(
            new ResourceLocation(SuperheroMod.MODID, "cinematic_puppet_slim"), "slim");

    private final ModelPart root;

    private final ModelPart head;
    private final ModelPart chest;
    private final ModelPart hips;
    private final ModelPart rightUpperArm, rightLowerArm;
    private final ModelPart leftUpperArm, leftLowerArm;
    private final ModelPart rightUpperLeg, rightLowerLeg;
    private final ModelPart leftUpperLeg, leftLowerLeg;
    private final PuppetSkinSurface[] surfaces;
    private final org.joml.Matrix4f[] global = new org.joml.Matrix4f[10];
    private final org.joml.Matrix4f[] skin = new org.joml.Matrix4f[10];

    public PuppetModel(ModelPart root, boolean slim) {
        this.root = root;
        this.hips = root.getChild("hips");
        this.chest = hips.getChild("chest");
        this.head = chest.getChild("head");
        this.rightUpperArm = chest.getChild("right_upper_arm");
        this.rightLowerArm = rightUpperArm.getChild("right_lower_arm");
        this.leftUpperArm = chest.getChild("left_upper_arm");
        this.leftLowerArm = leftUpperArm.getChild("left_lower_arm");
        this.rightUpperLeg = hips.getChild("right_upper_leg");
        this.rightLowerLeg = rightUpperLeg.getChild("right_lower_leg");
        this.leftUpperLeg = hips.getChild("left_upper_leg");
        this.leftLowerLeg = leftUpperLeg.getChild("left_lower_leg");
        for(int i=0;i<10;i++) { global[i]=new org.joml.Matrix4f(); skin[i]=new org.joml.Matrix4f(); }
        int width=slim?3:4;
        surfaces=new PuppetSkinSurface[]{
                new PuppetSkinSurface(-4,0,-2,8,12,4,16,16,6,true),
                new PuppetSkinSurface(slim?-7:-8,0,-2,width,12,4,40,16,6,false),
                new PuppetSkinSurface(4,0,-2,width,12,4,32,48,6,false),
                new PuppetSkinSurface(-3.9f,12,-2,4,12,4,0,16,18,false),
                new PuppetSkinSurface(-.1f,12,-2,4,12,4,16,48,18,false)
        };
    }

    /**
     * @param slim Alex modeli (3 piksel kol) mi
     */
    public static LayerDefinition createLayer(boolean slim) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        int armW = slim ? 3 : 4;
        // Slim kolda kutu bir piksel ice kayar
        float armX = slim ? -2f : -3f;
        float leftArmX = slim ? -1f : -1f;

        // ÖLÇÜ REFERANSI (vanilla ile birebir ayni olmali):
        //   kafa    y = -8 .. 0
        //   govde   y =  0 .. 12   (gogus 0..6, kalca 6..12)
        //   bacak   y = 12 .. 24   (ust 12..18, alt 18..24)
        // Ayak y=24'te olmazsa model yere gomulur ve kafa gercek oyuncunun
        // kafasindan alcakta kalir — isin de kafanin ustunden cikiyormus gibi
        // gorunur.

        // --- Kalca: govdenin ALT yarisi. Pivot kalca ekleminde (y=12). ---
        PartDefinition hips = root.addOrReplaceChild("hips",
                CubeListBuilder.create().texOffs(16, 22)
                        .addBox(-4.0f, -6.0f, -2.0f, 8, 6, 4),
                PartPose.offset(0.0f, 12.0f, 0.0f));

        // --- Gogus: govdenin UST yarisi. Pivot BELDE (y=6), boylece
        //     govde belden bukuluyor. ---
        PartDefinition chest = hips.addOrReplaceChild("chest",
                CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4.0f, -6.0f, -2.0f, 8, 6, 4),
                PartPose.offset(0.0f, -6.0f, 0.0f));

        chest.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8),
                PartPose.offset(0.0f, -6.0f, 0.0f));

        // --- Sag kol: ust kol goguste, on kol DIRSEKTE ---
        PartDefinition rUpper = chest.addOrReplaceChild("right_upper_arm",
                CubeListBuilder.create().texOffs(40, 16)
                        .addBox(armX, -2.0f, -2.0f, armW, 6, 4),
                PartPose.offset(-5.0f, -4.0f, 0.0f));
        rUpper.addOrReplaceChild("right_lower_arm",
                CubeListBuilder.create().texOffs(40, 22)
                        .addBox(armX, 0.0f, -2.0f, armW, 6, 4),
                PartPose.offset(0.0f, 4.0f, 0.0f));

        PartDefinition lUpper = chest.addOrReplaceChild("left_upper_arm",
                CubeListBuilder.create().texOffs(32, 48)
                        .addBox(leftArmX, -2.0f, -2.0f, armW, 6, 4),
                PartPose.offset(5.0f, -4.0f, 0.0f));
        lUpper.addOrReplaceChild("left_lower_arm",
                CubeListBuilder.create().texOffs(32, 54)
                        .addBox(leftArmX, 0.0f, -2.0f, armW, 6, 4),
                PartPose.offset(0.0f, 4.0f, 0.0f));

        // --- Bacaklar: ust bacak kalcada, alt bacak DIZDE ---
        PartDefinition rLeg = hips.addOrReplaceChild("right_upper_leg",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-2.0f, 0.0f, -2.0f, 4, 6, 4),
                PartPose.offset(-1.9f, 0.0f, 0.0f));
        rLeg.addOrReplaceChild("right_lower_leg",
                CubeListBuilder.create().texOffs(0, 22)
                        .addBox(-2.0f, 0.0f, -2.0f, 4, 6, 4),
                PartPose.offset(0.0f, 6.0f, 0.0f));

        PartDefinition lLeg = hips.addOrReplaceChild("left_upper_leg",
                CubeListBuilder.create().texOffs(16, 48)
                        .addBox(-2.0f, 0.0f, -2.0f, 4, 6, 4),
                PartPose.offset(1.9f, 0.0f, 0.0f));
        lLeg.addOrReplaceChild("left_lower_leg",
                CubeListBuilder.create().texOffs(16, 54)
                        .addBox(-2.0f, 0.0f, -2.0f, 4, 6, 4),
                PartPose.offset(0.0f, 6.0f, 0.0f));

        return LayerDefinition.create(mesh, 64, 64);
    }

    // ------------------------------------------------------------------

    /** Pozu modele yazar. */
    public void apply(ActorPose pose) {
        set(head, pose, ActorPose.HEAD);
        set(chest, pose, ActorPose.CHEST);
        set(hips, pose, ActorPose.HIPS);
        set(rightUpperArm, pose, ActorPose.RIGHT_UPPER_ARM);
        set(rightLowerArm, pose, ActorPose.RIGHT_LOWER_ARM);
        set(leftUpperArm, pose, ActorPose.LEFT_UPPER_ARM);
        set(leftLowerArm, pose, ActorPose.LEFT_LOWER_ARM);
        set(rightUpperLeg, pose, ActorPose.RIGHT_UPPER_LEG);
        set(rightLowerLeg, pose, ActorPose.RIGHT_LOWER_LEG);
        set(leftUpperLeg, pose, ActorPose.LEFT_UPPER_LEG);
        set(leftLowerLeg, pose, ActorPose.LEFT_LOWER_LEG);

        // Comelme: kalca asagi iner, model piksel biriminde oldugu icin
        // blok cinsinden gelen deger 16 ile carpiliyor
        hips.y = 12.0f + pose.crouch * 16.0f;
    }

    private static void set(ModelPart part, ActorPose pose, int joint) {
        part.xRot = pose.rot[joint][0];
        part.yRot = pose.rot[joint][1];
        part.zRot = pose.rot[joint][2];
    }

    public void render(PoseStack poseStack, VertexConsumer buffer,
                       int packedLight, int packedOverlay) {
        // Preserve the existing IK/pose hierarchy, but deform a closed skin around it.
        transform(global[0].identity(),hips);
        transform(global[1].set(global[0]),chest);
        transform(global[2].set(global[1]),rightUpperArm);
        transform(global[3].set(global[2]),rightLowerArm);
        transform(global[4].set(global[1]),leftUpperArm);
        transform(global[5].set(global[4]),leftLowerArm);
        transform(global[6].set(global[0]),rightUpperLeg);
        transform(global[7].set(global[6]),rightLowerLeg);
        transform(global[8].set(global[0]),leftUpperLeg);
        transform(global[9].set(global[8]),leftLowerLeg);
        inverseBind(0,0,12); inverseBind(1,0,6);
        inverseBind(2,-5,2); inverseBind(3,-5,6);
        inverseBind(4,5,2); inverseBind(5,5,6);
        inverseBind(6,-1.9f,12); inverseBind(7,-1.9f,18);
        inverseBind(8,1.9f,12); inverseBind(9,1.9f,18);
        for(int i=0;i<surfaces.length;i++)
            surfaces[i].render(poseStack,buffer,skin[i*2],skin[i*2+1],packedLight,packedOverlay);
        poseStack.pushPose();
        hips.translateAndRotate(poseStack);
        chest.translateAndRotate(poseStack);
        head.render(poseStack,buffer,packedLight,packedOverlay);
        poseStack.popPose();
    }

    private static void transform(org.joml.Matrix4f matrix,ModelPart part) {
        matrix.translate(part.x/16,part.y/16,part.z/16)
                .rotateZYX(part.zRot,part.yRot,part.xRot).scale(part.xScale,part.yScale,part.zScale);
    }

    private void inverseBind(int index,float x,float y) {
        skin[index].set(global[index]).translate(-x/16,-y/16,0);
    }
}
