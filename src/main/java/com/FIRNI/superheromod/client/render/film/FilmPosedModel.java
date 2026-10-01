package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.core.cinematic.ActorPose;
import com.FIRNI.superheromod.core.cinematic.JointRotations;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.core.cinematic.ActorPose.*;

/**
 * Plays a film pose on a mob's own model: any mob built from a head, body, two arms and two legs
 * (skeletons, piglins, illagers, golems, endermen...). The chest bends the whole upper body
 * about the hips; each arm and leg points where its elbow or knee would have put the hand or
 * foot, since these models have one bone per limb.
 */
final class FilmPosedModel {
    /** The limbs of one model, in pose-joint order; any of them may be missing. */
    private record Rig(ModelPart head, ModelPart hat, ModelPart body, ModelPart rightArm, ModelPart leftArm,
                       ModelPart rightLeg, ModelPart leftLeg, ModelPart arms, ModelPart folded) {
        ModelPart[] all() { return new ModelPart[]{head, hat, body, rightArm, leftArm, rightLeg, leftLeg, arms}; }
    }
    private FilmPosedModel() {}

    private static Rig rig(EntityModel<?> model) {
        if (model instanceof HumanoidModel<?> h) return new Rig(h.head, h.hat, h.body, h.rightArm, h.leftArm, h.rightLeg, h.leftLeg, null, null);
        if (model instanceof HierarchicalModel<?> m) {
            ModelPart root = m.root();
            ModelPart head = find(root, "head"), body = find(root, "body"), right = find(root, "right_arm"), left = find(root, "left_arm");
            ModelPart folded = find(root, "arms"), arms = right == null && left == null ? folded : null;
            if (head == null || body == null || right == null && left == null && arms == null) return null;
            return new Rig(head, null, body, right, left, find(root, "right_leg"), find(root, "left_leg"), arms, arms == null ? folded : null);
        }
        return null;
    }
    /** A named part anywhere under root (illagers keep arms under the body, golems under the root). */
    private static ModelPart find(ModelPart root, String name) {
        if (root.hasChild(name)) return root.getChild(name);
        for (String parent : new String[]{"body", "head"})
            if (root.hasChild(parent) && root.getChild(parent).hasChild(name)) return root.getChild(parent).getChild(name);
        return null;
    }

    /** True if it could pose and draw the entity this way; false means the caller must fall back. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean draw(FilmContext c, LivingEntity entity, Vec3 feet, float yaw, ActorPose pose, float scale, int tint) {
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer living)) return false;
        EntityModel model = living.getModel();
        Rig rig = rig(model);
        if (rig == null) return false;
        ResourceLocation texture = living.getTextureLocation(entity);
        float partial = c.partial(), age = entity.tickCount + partial;
        model.attackTime = 0;
        model.riding = false;
        model.young = entity.isBaby();
        model.prepareMobModel(entity, 0, 0, partial);
        model.setupAnim(entity, 0, 0, age, 0, 0);
        for (ModelPart part : rig.all()) if (part != null) part.resetPose();
        try {
            // Illagers fold their arms into one part; the film needs the two free arms instead.
            if (rig.folded() != null) { rig.folded().visible = false; if (rig.rightArm() != null) rig.rightArm().visible = true; if (rig.leftArm() != null) rig.leftArm().visible = true; }
            ModelPart[] free = rig.arms() != null ? freeArms(model) : null;
            if (free != null) rig.arms().visible = false;
            apply(rig, pose, free);
            if (model instanceof PlayerModel<?> p) {
                p.jacket.copyFrom(p.body); p.rightSleeve.copyFrom(p.rightArm); p.leftSleeve.copyFrom(p.leftArm);
                p.rightPants.copyFrom(p.rightLeg); p.leftPants.copyFrom(p.leftLeg);
            }
            var stack = c.pose();
            stack.pushPose();
            stack.translate(feet.x, feet.y - pose.crouch * scale, feet.z);
            stack.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            if (pose.bodyRoll != 0) stack.mulPose(Axis.ZP.rotationDegrees(pose.bodyRoll));
            if (pose.bodyPitch != 0) stack.mulPose(Axis.XP.rotationDegrees(pose.bodyPitch));
            stack.scale(-scale, -scale, scale);
            stack.translate(0, -1.501, 0);
            float r = (tint >> 16 & 255) / 255f, g = (tint >> 8 & 255) / 255f, b = (tint & 255) / 255f;
            var buffer = c.buffers().getBuffer(model.renderType(texture));
            model.renderToBuffer(stack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, 1);
            if (free != null) for (ModelPart arm : free) arm.render(stack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, 1);
            stack.popPose();
        } finally {
            // Hand the model back untouched for the world's own rendering.
            for (ModelPart part : rig.all()) if (part != null) part.resetPose();
            if (rig.arms() != null) rig.arms().visible = true;
        }
        return true;
    }

    /** Two free arms per villager-shaped texture size, cut from the same skin as the folded arms. */
    private static final java.util.Map<Integer, ModelPart[]> FREE_ARMS = new java.util.HashMap<>();
    private static ModelPart[] freeArms(EntityModel<?> model) {
        int height = model instanceof net.minecraft.client.model.WitchModel<?> ? 128 : 64;
        return FREE_ARMS.computeIfAbsent(height, h -> {
            var mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
            var root = mesh.getRoot();
            // Upper arm from the folded arm's side piece, the hand from its crossbar.
            root.addOrReplaceChild("right_arm", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                    .texOffs(44, 22).addBox(-2, -2, -2, 4, 8, 4).texOffs(40, 38).addBox(-2, 6, -2, 4, 4, 4),
                    net.minecraft.client.model.geom.PartPose.offset(-6, 2, 0));
            root.addOrReplaceChild("left_arm", net.minecraft.client.model.geom.builders.CubeListBuilder.create().mirror()
                    .texOffs(44, 22).addBox(-2, -2, -2, 4, 8, 4).texOffs(40, 38).addBox(-2, 6, -2, 4, 4, 4),
                    net.minecraft.client.model.geom.PartPose.offset(6, 2, 0));
            ModelPart baked = net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, h).bakeRoot();
            return new ModelPart[]{baked.getChild("right_arm"), baked.getChild("left_arm")};
        });
    }

    private static Quaternionf joint(ActorPose pose, int joint) {
        return new Quaternionf().rotationZYX(pose.rot[joint][2], pose.rot[joint][1], pose.rot[joint][0]);
    }
    /** One bone standing in for two: the upper rotation, turned so the bone points at the end of the lower one. */
    private static Quaternionf limb(ActorPose pose, int upper, int lower) {
        Vector3f reach = joint(pose, lower).transform(new Vector3f(0, 1, 0)).add(0, 1, 0);
        if (reach.lengthSquared() < 1e-6f) return joint(pose, upper);
        return joint(pose, upper).mul(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), reach.normalize()));
    }
    private static void set(ModelPart part, Quaternionf rotation) {
        Vector3f euler = JointRotations.toEuler(rotation, new Vector3f());
        part.xRot = euler.x; part.yRot = euler.y; part.zRot = euler.z;
    }
    /** Carries a part round the hip pivot with the upper body, then sets its own rotation on top. */
    private static void upper(ModelPart part, Vector3f hip, Quaternionf trunk, Quaternionf own) {
        if (part == null) return;
        Vector3f at = new Vector3f(part.x, part.y, part.z).sub(hip);
        trunk.transform(at).add(hip);
        part.x = at.x; part.y = at.y; part.z = at.z;
        set(part, new Quaternionf(trunk).mul(own));
    }
    private static void apply(Rig rig, ActorPose pose, ModelPart[] free) {
        // The hips: where the legs hang from, or twelve pixels under the neck.
        float hipY = rig.rightLeg() != null ? rig.rightLeg().y : rig.leftLeg() != null ? rig.leftLeg().y : rig.body().y + 12;
        Vector3f hip = new Vector3f(0, hipY, 0);
        Quaternionf hips = joint(pose, HIPS), trunk = new Quaternionf(hips).mul(joint(pose, CHEST));
        upper(rig.body(), hip, trunk, new Quaternionf());
        upper(rig.head(), hip, trunk, joint(pose, HEAD));
        upper(rig.hat(), hip, trunk, joint(pose, HEAD));
        upper(rig.rightArm(), hip, trunk, limb(pose, RIGHT_UPPER_ARM, RIGHT_LOWER_ARM));
        upper(rig.leftArm(), hip, trunk, limb(pose, LEFT_UPPER_ARM, LEFT_LOWER_ARM));
        if (free != null) {
            // The folded villager arms are hidden; two free arms take the pose like anyone else's.
            for (ModelPart arm : free) arm.resetPose();
            upper(free[0], hip, trunk, limb(pose, RIGHT_UPPER_ARM, RIGHT_LOWER_ARM));
            upper(free[1], hip, trunk, limb(pose, LEFT_UPPER_ARM, LEFT_LOWER_ARM));
        }
        if (rig.rightLeg() != null) set(rig.rightLeg(), new Quaternionf(hips).mul(limb(pose, RIGHT_UPPER_LEG, RIGHT_LOWER_LEG)));
        if (rig.leftLeg() != null) set(rig.leftLeg(), new Quaternionf(hips).mul(limb(pose, LEFT_UPPER_LEG, LEFT_LOWER_LEG)));
    }
}
