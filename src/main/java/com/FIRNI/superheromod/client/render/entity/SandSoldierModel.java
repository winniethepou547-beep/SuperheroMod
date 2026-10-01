package com.FIRNI.superheromod.client.render.entity;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.anim.RigAsset;
import com.FIRNI.superheromod.heroes.sandman.SandSoldierEntity;
import com.FIRNI.superheromod.heroes.sandman.GiantSandSoldierEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import java.util.Map;

/** Geometry and clips exported from art/blockbench/sand_soldier.bbmodel. */
public class SandSoldierModel<T extends SandSoldierEntity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(SuperheroMod.MODID, "sand_soldier"), "main");
    private static RigAsset asset;
    private final RigAsset rig;
    private final ModelPart root;
    private final Map<String, ModelPart> bones;

    public static LayerDefinition createBodyLayer() {
        asset = RigAsset.load(new ResourceLocation(SuperheroMod.MODID, "rigs/sand_soldier.json"));
        return asset.layer();
    }

    public SandSoldierModel(ModelPart root) {
        this.root = root;
        this.rig = asset;
        this.bones = rig.bind(root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(p -> { p.resetPose(); p.visible = true; });
        float partial = Mth.clamp(ageInTicks - entity.tickCount, 0, 1);
        float spawn = entity.renderProgress(SandSoldierEntity.Visual.SPAWN, partial);
        float crumble = entity.renderProgress(SandSoldierEntity.Visual.CRUMBLE, partial);
        float swing = entity.renderProgress(SandSoldierEntity.Visual.SWING, partial);
        float slam = entity.renderProgress(SandSoldierEntity.Visual.SLAM, partial);
        float aim = entity.renderProgress(SandSoldierEntity.Visual.AIM, partial);
        boolean ranged = entity.getVariant() == SandSoldierEntity.Variant.RANGED;
        bones.get("right_spikes").visible = ranged;
        bones.get("left_spikes").visible = ranged;
        bones.get("slam_mass").visible = slam > 0;
        bones.get("ranged_mass").visible = ranged && aim > 0;
        if (spawn < 1) {
            rig.apply("spawn", spawn * 1.2f, 1, bones);
        } else if (crumble > 0) {
            rig.apply("crumble", crumble * .6f, 1, bones);
        } else {
            float action = Math.max(swing, Math.max(slam, aim)) > 0 ? 1 : 0;
            rig.apply("idle", ageInTicks / 20f, 1 - action, bones);
            rig.apply("walk", limbSwing / (2 * (float)Math.PI) * .6662f,
                    Mth.clamp(limbSwingAmount * 2, 0, 1) * (1 - action), bones);
            if (slam > 0) rig.apply("slam", slam * 2.2f, 1, bones);
            else if (swing > 0) {
                rig.apply(entity.getSwingDirection() == 2 ? "strike_left" : "strike_right", swing * .65f, 1, bones);
                String side = entity.getSwingDirection() == 2 ? "left" : "right";
                float swell = 1 + .10f * Mth.sin(swing * (float)Math.PI);
                bones.get(side + "_upper_arm").xScale *= swell;
                bones.get(side + "_upper_arm").zScale *= swell;
            } else if (aim > 0) rig.apply("aim", aim * 1.1f, 1, bones);
            bones.get("head").yRot += netHeadYaw * Mth.DEG_TO_RAD;
            bones.get("head").xRot += headPitch * Mth.DEG_TO_RAD;
        }
        ModelPart bodyRoot = bones.get("root");
        float bulk = entity instanceof GiantSandSoldierEntity ? 1.12f
                : entity.getVariant() == SandSoldierEntity.Variant.BREAKER ? 1.12f : .94f;
        bodyRoot.xScale *= bulk;
        bodyRoot.zScale *= bulk;
    }

    /**
     * Film posing without an entity: idle and walk cycles blended by weight, then an optional
     * action clip (spawn, crumble, strike_left, strike_right, slam, aim) on top at its own time.
     * Bones can be adjusted afterwards through bone().
     */
    public void filmPose(float idleSeconds, float walkSeconds, float walkWeight, String action, float actionSeconds, float bulk) {
        root.getAllParts().forEach(p -> { p.resetPose(); p.visible = true; });
        bones.get("right_spikes").visible = false;
        bones.get("left_spikes").visible = false;
        bones.get("slam_mass").visible = "slam".equals(action);
        bones.get("ranged_mass").visible = false;
        if ("spawn".equals(action) || "crumble".equals(action)) rig.apply(action, actionSeconds, 1, bones);
        else {
            float other = action == null ? 1 : 0;
            rig.apply("idle", idleSeconds, (1 - walkWeight) * other, bones);
            rig.apply("walk", walkSeconds, walkWeight, bones);
            if (action != null) rig.apply(action, actionSeconds, 1, bones);
        }
        ModelPart bodyRoot = bones.get("root");
        bodyRoot.xScale *= bulk;
        bodyRoot.zScale *= bulk;
    }
    public ModelPart bone(String name) { return bones.get(name); }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, float r, float g, float b, float a) {
        root.render(pose, new SandFaceUV(buffer), light, overlay, r, g, b, a);
    }

    /** Whole vanilla sand tile per face, matching the editor's per-face UV. */
    private static final class SandFaceUV implements VertexConsumer {
        private final VertexConsumer delegate;
        private int vertex;
        SandFaceUV(VertexConsumer delegate) { this.delegate = delegate; }
        public VertexConsumer vertex(double x,double y,double z) { delegate.vertex(x,y,z); return this; }
        public VertexConsumer color(int r,int g,int b,int a) { delegate.color(r,g,b,a); return this; }
        public VertexConsumer uv(float u,float v) {
            int corner = vertex & 3;
            delegate.uv(corner == 0 || corner == 3 ? 1 : 0, corner < 2 ? 0 : 1); return this;
        }
        public VertexConsumer overlayCoords(int u,int v) { delegate.overlayCoords(u,v); return this; }
        public VertexConsumer uv2(int u,int v) { delegate.uv2(u,v); return this; }
        public VertexConsumer normal(float x,float y,float z) { delegate.normal(x,y,z); return this; }
        public void endVertex() { delegate.endVertex(); vertex++; }
        public void defaultColor(int r,int g,int b,int a) { delegate.defaultColor(r,g,b,a); }
        public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
    }
}
