package com.FIRNI.superheromod.client.render.thor;

import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thor's whole body, drawn in place of the player model (which is hidden for him): broad
 * shoulders, dark Asgardian plate with the six silver discs, bare arms with bracers, a red cape
 * and long blond hair that both stream back with speed, a beard, eyes that light up white-blue,
 * and Mjolnir in his right hand. Every joint comes from ThorMotion through ThorClient.pose.
 * Model space: pixels/16, +y down, -z in front, -x is his right.
 */
public final class ThorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    // Torso and plate
    private static final ModelPart TORSO = GhostMaterials.box(-4.6f, 0, -2.5f, 9.2f, 12, 5);
    private static final ModelPart PLATE = GhostMaterials.box(-4.95f, .3f, -2.95f, 9.9f, 7.6f, 5.9f);
    private static final ModelPart PLATE_LOW = GhostMaterials.box(-4.7f, 7.6f, -2.75f, 9.4f, 2.2f, 5.5f);
    private static final ModelPart COLLAR = GhostMaterials.box(-3.4f, -.6f, -2.0f, 6.8f, 1.2f, 4.4f);
    private static final ModelPart DISC = GhostMaterials.box(-.85f, -.85f, -.3f, 1.7f, 1.7f, .6f);
    private static final ModelPart DISC_RIM = GhostMaterials.box(-1.05f, -1.05f, -.2f, 2.1f, 2.1f, .3f);
    private static final ModelPart SEAM = GhostMaterials.box(-.15f, .6f, -3.05f, .3f, 7f, .2f);
    private static final ModelPart BELT = GhostMaterials.box(-4.8f, 9.6f, -2.8f, 9.6f, 1.6f, 5.6f);
    private static final ModelPart BUCKLE = GhostMaterials.box(-1.0f, 9.4f, -3.05f, 2.0f, 2.0f, .4f);
    private static final ModelPart TASSET = GhostMaterials.box(-2.0f, 0, -.3f, 4.0f, 3.2f, .6f);
    private static final ModelPart GLOW_LINE = GhostMaterials.box(-.12f, -.12f, -.12f, .24f, .24f, .24f);
    // Arms
    private static final ModelPart PAD = GhostMaterials.box(-2.75f, -2.8f, -2.75f, 5.5f, 3.2f, 5.5f);
    private static final ModelPart PAD_RIM = GhostMaterials.box(-2.95f, .2f, -2.95f, 5.9f, .6f, 5.9f);
    private static final ModelPart UPPER_ARM = GhostMaterials.box(-2.2f, -2, -2.2f, 4.4f, 6.2f, 4.4f);
    private static final ModelPart FOREARM = GhostMaterials.box(-2.05f, 0, -2.05f, 4.1f, 5.4f, 4.1f);
    private static final ModelPart BRACER = GhostMaterials.box(-2.35f, 1.0f, -2.35f, 4.7f, 3.8f, 4.7f);
    private static final ModelPart FIST = GhostMaterials.box(-1.95f, 4.9f, -1.95f, 3.9f, 2.2f, 3.9f);
    // Legs
    private static final ModelPart THIGH = GhostMaterials.box(-2.25f, 0, -2.25f, 4.5f, 6.2f, 4.5f);
    private static final ModelPart SHIN = GhostMaterials.box(-2.15f, 0, -2.15f, 4.3f, 6, 4.3f);
    private static final ModelPart BOOT = GhostMaterials.box(-2.4f, 1.6f, -2.85f, 4.8f, 4.5f, 5.3f);
    private static final ModelPart KNEE = GhostMaterials.box(-1.6f, -1.1f, -2.6f, 3.2f, 2.0f, .7f);
    // Head
    private static final ModelPart SKULL = GhostMaterials.box(-4, -8, -4, 8, 8, 8);
    private static final ModelPart NOSE = GhostMaterials.box(-.6f, -4.3f, -4.55f, 1.2f, 1.8f, .6f);
    private static final ModelPart EYE_WHITE = GhostMaterials.box(-1.0f, -.4f, -.1f, 2.0f, .8f, .2f);
    private static final ModelPart IRIS = GhostMaterials.box(-.45f, -.4f, -.15f, .9f, .8f, .2f);
    private static final ModelPart BROW = GhostMaterials.box(-1.25f, -.3f, -.25f, 2.5f, .6f, .5f);
    private static final ModelPart BEARD = GhostMaterials.box(-4.15f, -3.1f, -4.35f, 8.3f, 3.3f, 1.0f);
    private static final ModelPart CHIN = GhostMaterials.box(-3.0f, -.3f, -4.5f, 6.0f, 1.4f, 2.4f);
    private static final ModelPart SIDEBURN = GhostMaterials.box(-4.25f, -5.2f, -3.8f, .7f, 5.2f, 3.0f);
    private static final ModelPart MOUSTACHE = GhostMaterials.box(-2.3f, -2.75f, -4.5f, 4.6f, .6f, .4f);
    private static final ModelPart MOUTH = GhostMaterials.box(-1.25f, -2.1f, -4.42f, 2.5f, 1.0f, .2f);
    private static final ModelPart HAIR_CAP = GhostMaterials.box(-4.45f, -8.65f, -4.45f, 8.9f, 2.3f, 8.9f);
    private static final ModelPart HAIR_BACK = GhostMaterials.box(-4.45f, -7.2f, 2.1f, 8.9f, 7.6f, 2.5f);
    private static final ModelPart HAIR_SIDE = GhostMaterials.box(-4.7f, -7.4f, -2.6f, .95f, 6.0f, 5.0f);
    private static final ModelPart HAIR_FRINGE = GhostMaterials.box(-4.3f, -8.2f, -4.6f, 3.4f, 1.6f, 1.0f);
    private static final ModelPart HAIR_FLOW = GhostMaterials.box(-4.2f, 0, -1.1f, 8.4f, 4.6f, 2.0f);
    private static final ModelPart HAIR_TIP = GhostMaterials.box(-3.6f, 0, -.8f, 7.2f, 2.6f, 1.4f);
    private static final ModelPart HAIR_LOCK = GhostMaterials.box(-.9f, 0, -1.0f, 1.8f, 5.0f, 2.0f);
    // Cape
    private static final ModelPart CAPE_ROW = GhostMaterials.box(-5.2f, 0, 0, 10.4f, 4.5f, .55f);
    private static final ModelPart CAPE_CLASP = GhostMaterials.box(-1.0f, -1.0f, -.6f, 2.0f, 2.0f, 1.0f);

    private static final float[] SKIN = {.86f, .66f, .52f}, HAIR = {.86f, .69f, .36f}, HAIR_DARK = {.7f, .53f, .25f}, BEARD_C = {.72f, .52f, .26f},
            PLATE_C = {.2f, .21f, .25f}, PLATE_DARK = {.13f, .135f, .16f}, SILVER = {.78f, .8f, .85f}, SUIT = {.1f, .1f, .12f},
            LEATHER = {.2f, .14f, .1f}, CAPE = {.62f, .05f, .06f}, CAPE_IN = {.42f, .03f, .04f};
    private static final int FULL = Mjolnir.FULL_BRIGHT;

    /** Cape and hair lag behind the body: lift per player, eased over time. */
    private record Cloth(float lift, float side, float time) {}
    private final Map<Integer, Cloth> cloth = new HashMap<>();

    public ThorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    private static void draw(ModelPart part, PoseStack p, MultiBufferSource b, int light, float[] c) { GhostMaterials.draw(part, p, b, light, c[0], c[1], c[2]); }
    private static void draw(ModelPart part, PoseStack p, MultiBufferSource b, int light, float r, float g, float bl) { GhostMaterials.draw(part, p, b, light, r, g, bl); }
    private static void rot(PoseStack p, float x, float y, float z) { p.mulPose(new Quaternionf().rotationZYX(z, y, x)); }
    private static void px(PoseStack p, double x, double y, double z) { p.translate(x / 16, y / 16, z / 16); }

    @Override
    public void render(PoseStack p, MultiBufferSource b, int light, AbstractClientPlayer e, float walk, float amount, float partial, float time, float yaw, float pitch) {
        if (!ThorClient.isThor(e) || e.isInvisible()) return;
        var model = getParentModel();
        ThorMotion.Pose pose = ThorClient.pose(e, partial, time);
        ThorClient.State state = ThorClient.get(e);
        boolean hammerOut = state != null && state.hammerOut() && ThorClient.ultimateTime(e, partial) < 0;
        boolean flying = state != null && state.flying();
        float power = Math.max(pose.aura, state != null && state.powered() ? .5f : 0);
        Cloth c = cloth(e, time, flying);

        p.pushPose();
        px(p, 0, -pose.rise, 0);
        px(p, 0, 12, 0); p.mulPose(Axis.XP.rotation(pose.bodyPitch)); p.mulPose(Axis.ZP.rotation(pose.bodyRoll)); px(p, 0, -12, 0);

        // ---- legs: the hips drop by the crouch; thighs and knees fold so the feet stay down.
        float drop = Mth.clamp(pose.crouch, -1, 10);
        float fold = (float) Math.acos(Mth.clamp(1 - Math.max(0, drop) / 12f, -1, 1));
        boolean walking = e.onGround() && !flying;
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side < 0;
            float legX = right ? pose.rLegX : pose.lLegX, legZ = right ? pose.rLegZ : pose.lLegZ, knee = right ? pose.rKnee : pose.lKnee;
            if (walking) {
                float swing = Mth.cos(walk * .6662f + (right ? 0 : Mth.PI)) * 1.15f * amount;
                legX += swing;
                knee += Math.max(0, -Mth.sin(walk * .6662f + (right ? 0 : Mth.PI))) * .7f * amount;
            }
            p.pushPose();
            px(p, side * 2.1, 12 + drop, 0);
            rot(p, legX - fold, 0, legZ);
            draw(THIGH, p, b, light, SUIT);
            px(p, 0, 6, 0);
            p.mulPose(Axis.XP.rotation(knee + 2 * fold));
            draw(SHIN, p, b, light, SUIT);
            draw(KNEE, p, b, light, PLATE_C);
            draw(BOOT, p, b, light, LEATHER);
            p.popPose();
        }

        // ---- everything above the hips
        px(p, 0, drop, 0);
        px(p, 0, 12, 0);
        p.mulPose(Axis.YP.rotation(pose.torsoYaw)); p.mulPose(Axis.XP.rotation(pose.torsoPitch)); p.mulPose(Axis.ZP.rotation(pose.torsoRoll));
        px(p, 0, -12, 0);
        torso(p, b, light, time, power);
        cape(p, b, light, c, time);

        float swingArms = walking && state != null && state.action == 0 ? amount : 0;
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side < 0;
            float ax = right ? pose.rArmX : pose.lArmX, ay = right ? pose.rArmY : pose.lArmY, az = right ? pose.rArmZ : pose.lArmZ;
            float elbow = right ? pose.rElbow : pose.lElbow;
            ax += Mth.cos(walk * .6662f + (right ? Mth.PI : 0)) * (right ? .35f : .8f) * swingArms;
            p.pushPose();
            px(p, side * 5.75, 2, 0);
            rot(p, ax, ay, az);
            draw(UPPER_ARM, p, b, light, SKIN);
            draw(PAD, p, b, light, PLATE_C);
            draw(PAD_RIM, p, b, light, SILVER);
            px(p, 0, 4.2, 0);
            p.mulPose(Axis.XP.rotation(-elbow));
            draw(FOREARM, p, b, light, SKIN);
            draw(BRACER, p, b, light, SILVER[0] * .8f, SILVER[1] * .8f, SILVER[2] * .82f);
            draw(FIST, p, b, light, LEATHER);
            if (right && !hammerOut) {
                px(p, 0, 5.9, 0);
                p.mulPose(Axis.YP.rotation(pose.wristY));
                p.mulPose(Axis.XP.rotation(pose.wristX));
                if (pose.spinRing > .02f) spinRing(p, b, pose.spinRing, pose.spinMode >= 2, time);
                Mjolnir.draw(p, b, light, Math.max(power, pose.eyes > .9f ? .5f : 0), time);
            }
            p.popPose();
        }

        // ---- head: keeps looking where the player looks however the chest is turned.
        p.pushPose();
        float headYaw = model.head.yRot - pose.torsoYaw + pose.headYaw;
        float headPitch = model.head.xRot + pose.headPitch - pose.torsoPitch * .5f - pose.bodyPitch * .8f;
        p.mulPose(Axis.YP.rotation(headYaw)); p.mulPose(Axis.XP.rotation(headPitch));
        head(p, b, light, pose, c, time);
        p.popPose();

        if (power > .05f) aura(p, b, power, time);
        p.popPose();
    }

    /** Lift of cape and hair: speed through the air pushes them back, falling lifts them, they sway. */
    private Cloth cloth(AbstractClientPlayer e, float time, boolean flying) {
        Vec3 v = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        double yaw = Math.toRadians(e.yBodyRot);
        double forward = -v.x * Math.sin(yaw) + v.z * Math.cos(yaw), sideways = v.x * Math.cos(yaw) + v.z * Math.sin(yaw);
        float want = (float) Mth.clamp(Math.max(0, forward) * 2.6 + Math.max(0, -v.y) * 1.4 + Math.abs(sideways) * .8 + (flying ? .35 : 0), 0, 1.45);
        Cloth last = cloth.get(e.getId());
        if (cloth.size() > 64) cloth.clear();
        if (last == null || time - last.time > 10) last = new Cloth(want, 0, time);
        float dt = Mth.clamp(time - last.time, 0, 2);
        float rate = want > last.lift ? .35f : .12f;   // whips up fast, settles slowly
        float lift = last.lift + (want - last.lift) * (1 - (float) Math.exp(-dt * rate * 3));
        float side = last.side + ((float) sideways * 2 - last.side) * (1 - (float) Math.exp(-dt * .4f));
        Cloth now = new Cloth(lift, side, time);
        cloth.put(e.getId(), now);
        return now;
    }

    private void torso(PoseStack p, MultiBufferSource b, int light, float time, float power) {
        draw(TORSO, p, b, light, SUIT);
        draw(PLATE, p, b, light, PLATE_C);
        draw(PLATE_LOW, p, b, light, PLATE_DARK);
        draw(COLLAR, p, b, light, PLATE_DARK);
        draw(SEAM, p, b, light, SILVER[0] * .7f, SILVER[1] * .7f, SILVER[2] * .7f);
        // The six discs, three down each side of the chest.
        for (int side = -1; side <= 1; side += 2) for (int row = 0; row < 3; row++) {
            p.pushPose(); px(p, side * 2.55, 1.9 + row * 2.05, -3.0);
            draw(DISC_RIM, p, b, light, PLATE_DARK);
            draw(DISC, p, b, light, SILVER);
            p.popPose();
        }
        draw(BELT, p, b, light, LEATHER);
        draw(BUCKLE, p, b, light, SILVER);
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose(); px(p, side * 2.4, 11.0, -2.75); p.mulPose(Axis.ZP.rotation(side * .08f));
            draw(TASSET, p, b, light, PLATE_DARK);
            p.popPose();
        }
        // Light leaking from the plate's seams while the power is up.
        if (power > .05f) {
            float flick = .6f + .4f * (float) Math.sin(time * 1.7) * (float) Math.sin(time * .53 + 1);
            float a = Mth.clamp(power * flick, 0, 1);
            for (int i = 0; i < 12; i++) {
                double u = i / 11.0;
                p.pushPose(); px(p, -4.95 + u * 9.9, 7.85, -3.0);
                draw(GLOW_LINE, p, b, FULL, .55f * a, .8f * a, a);
                p.popPose();
                p.pushPose(); px(p, 0, .6 + u * 7, -3.08);
                draw(GLOW_LINE, p, b, FULL, .55f * a, .8f * a, a);
                p.popPose();
            }
        }
    }

    /** Four rows of cloth from the shoulders, each bending a little further: it drapes, flows and flaps. */
    private void cape(PoseStack p, MultiBufferSource b, int light, Cloth c, float time) {
        p.pushPose();
        px(p, 0, .2, 2.6);
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose(); px(p, side * 3.9, .6, -.1); draw(CAPE_CLASP, p, b, light, SILVER); p.popPose();
        }
        float flap = .03f + .07f * c.lift();
        p.mulPose(Axis.XP.rotation(.07f + c.lift() * .75f));
        p.mulPose(Axis.ZP.rotation(Mth.clamp(c.side(), -.3f, .3f) * .4f));
        for (int row = 0; row < 4; row++) {
            float wave = (float) Math.sin(time * (.18 + .3 * c.lift()) - row * .9) * flap * (row + 1) * .6f;
            p.mulPose(Axis.XP.rotation(c.lift() * .16f * row + wave));
            draw(CAPE_ROW, p, b, light, row % 2 == 0 ? CAPE : new float[]{CAPE[0] * .9f, CAPE[1], CAPE[2]});
            p.pushPose(); px(p, 0, 0, .5); draw(CAPE_ROW, p, b, light, CAPE_IN); p.popPose();
            px(p, 0, 4.35, 0);
        }
        p.popPose();
    }

    private void head(PoseStack p, MultiBufferSource b, int light, ThorMotion.Pose pose, Cloth c, float time) {
        draw(SKULL, p, b, light, SKIN);
        draw(NOSE, p, b, light, SKIN[0] * .95f, SKIN[1] * .92f, SKIN[2] * .9f);
        draw(BEARD, p, b, light, BEARD_C);
        draw(CHIN, p, b, light, BEARD_C);
        draw(MOUSTACHE, p, b, light, BEARD_C[0] * .92f, BEARD_C[1] * .9f, BEARD_C[2] * .9f);
        if (pose.mouth > .05f) {
            p.pushPose(); px(p, 0, pose.mouth * .6, 0);
            p.scale(1, .4f + pose.mouth * 1.2f, 1);
            draw(MOUTH, p, b, light, .12f, .05f, .05f);
            p.popPose();
        }
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose(); p.scale(side, 1, 1); draw(SIDEBURN, p, b, light, BEARD_C); p.popPose();
        }
        // Eyes: pale blue at rest; white-blue light when the storm is in him.
        float glow = Mth.clamp(pose.eyes, 0, 1);
        int eyeLight = glow > .3f ? FULL : light;
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose(); px(p, side * 1.85, -4.15, -4.05);
            draw(EYE_WHITE, p, b, eyeLight, Mth.lerp(glow, .92f, .85f), Mth.lerp(glow, .92f, .95f), Mth.lerp(glow, .92f, 1f));
            draw(IRIS, p, b, eyeLight, Mth.lerp(glow, .35f, .9f), Mth.lerp(glow, .6f, .97f), Mth.lerp(glow, .9f, 1f));
            p.popPose();
            // Heavy brows angled down toward the nose: a serious face.
            p.pushPose(); px(p, side * 1.9, -5.25, -4.05); p.mulPose(Axis.ZP.rotation(side * -.16f));
            draw(BROW, p, b, light, HAIR_DARK);
            p.popPose();
        }
        if (glow > .05f) eyeLight(p, b, glow, time);
        // Hair: cap, back, sides, a fringe, and long locks that stream back with the cape.
        draw(HAIR_CAP, p, b, light, HAIR);
        draw(HAIR_BACK, p, b, light, HAIR);
        draw(HAIR_FRINGE, p, b, light, HAIR);
        p.pushPose(); p.scale(-1, 1, 1); draw(HAIR_FRINGE, p, b, light, HAIR_DARK); p.popPose();
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose(); p.scale(side, 1, 1); draw(HAIR_SIDE, p, b, light, HAIR); p.popPose();
        }
        float lift = c.lift();
        p.pushPose();
        px(p, 0, .2, 3.3);
        p.mulPose(Axis.XP.rotation(.15f + lift * .95f + (float) Math.sin(time * .25) * .04f * (1 + lift)));
        draw(HAIR_FLOW, p, b, light, HAIR);
        px(p, 0, 4.4, .2);
        p.mulPose(Axis.XP.rotation(lift * .4f + (float) Math.sin(time * .31 + 1) * .08f * (.3f + lift)));
        draw(HAIR_TIP, p, b, light, HAIR_DARK);
        p.popPose();
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose();
            px(p, side * 3.6, -.2, 1.0);
            p.mulPose(Axis.XP.rotation(.1f + lift * .8f));
            p.mulPose(Axis.ZP.rotation(side * .12f));
            draw(HAIR_LOCK, p, b, light, HAIR);
            p.popPose();
        }
    }

    /** Light in and around the eyes, and when they blaze, fine sparks spitting out of them. */
    private void eyeLight(PoseStack p, MultiBufferSource b, float glow, float time) {
        p.pushPose();
        p.scale(1 / 16f, 1 / 16f, 1 / 16f);
        var m = p.last().pose();
        int frame = (int) (time / 2);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 eye = new Vec3(side * 1.85, -4.15, -4.3);
            ThorBolts.cross(b.getBuffer(FilmFx.ADD), m, eye.add(-.8, 0, 0), eye.add(.8, 0, 0), 1.6 * glow, ThorBolts.BODY, .5f * glow);
            ThorBolts.cross(b.getBuffer(FilmFx.ADD), m, eye.add(0, -.5, 0), eye.add(0, .5, 0), 1.2 * glow, ThorBolts.CORE, .45f * glow);
            if (glow > .7f && FilmFx.hash(frame * 3.1 + side) < .45) {
                long seed = frame * 53L + side;
                Vec3 out = eye.add(side * (1.2 + FilmFx.hash(seed) * 1.8), -FilmFx.hash(seed + 1) * 1.6, -.6 - FilmFx.hash(seed + 2));
                ThorBolts.crossBolt(b.getBuffer(FilmFx.ADD), m, ThorBolts.bolt(eye, out, seed, .5, .2, 0), .12, glow);
            }
        }
        p.popPose();
    }

    /** The blur of a whirling hammer: a faint disc of metal and a ring of light where the head runs. */
    private void spinRing(PoseStack p, MultiBufferSource b, float strength, boolean front, float time) {
        p.pushPose();
        p.scale(1 / 16f, 1 / 16f, 1 / 16f);
        var m = p.last().pose();
        double radius = Mjolnir.HEAD_CENTRE.y;
        int n = 28;
        // Beside the head it spins about the hand's x axis (head runs in the hammer's y-z plane);
        // in front of him about the forearm (head runs in the hammer's x-y plane).
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 p0 = ring(a0, radius, front), p1 = ring(a1, radius, front);
            ThorBolts.cross(b.getBuffer(FilmFx.ADD), m, p0, p1, 2.6 * strength, 0x9aa6bf, .16f * strength);
            ThorBolts.cross(b.getBuffer(FilmFx.ADD), m, p0, p1, .7 * strength, ThorBolts.BODY, .3f * strength);
        }
        // Electric rings riding the blur.
        int frame = (int) (time / 2);
        for (int i = 0; i < 2; i++) {
            double start = FilmFx.hash(frame * 7 + i) * Math.PI * 2, span = 1.2 + FilmFx.hash(frame * 3 + i) * 1.4;
            Vec3 a = ring(start, radius, front), z = ring(start + span, radius, front);
            ThorBolts.crossBolt(b.getBuffer(FilmFx.ADD), m, ThorBolts.bolt(a, z, frame * 11L + i, .25, .2, 0), .25, .8f * strength);
        }
        p.popPose();
    }

    private static Vec3 ring(double angle, double radius, boolean front) {
        return front ? new Vec3(Math.sin(angle) * radius, Math.cos(angle) * radius, 0) : new Vec3(0, Math.cos(angle) * radius, Math.sin(angle) * radius);
    }

    /** Lightning crawling over his body while the power is in him. */
    private void aura(PoseStack p, MultiBufferSource b, float power, float time) {
        int frame = (int) (time / 1.5f);
        int count = (int) (2 + power * 7);
        p.pushPose();
        p.scale(1 / 16f, 1 / 16f, 1 / 16f);
        var m = p.last().pose();
        for (int i = 0; i < count; i++) {
            long seed = frame * 977L + i * 131L;
            if (FilmFx.hash(seed * .17) > .35 + .6 * power) continue;
            Vec3 a = bodyPoint(seed), z = bodyPoint(seed + 71);
            if (a.distanceTo(z) > 12) z = a.lerp(z, .5);
            ThorBolts.crossBolt(b.getBuffer(FilmFx.ADD), m, ThorBolts.bolt(a, z, seed, .4, .4, 1), .18 + .1 * power, .9f * power);
        }
        p.popPose();
    }
    /** A point just outside his silhouette (pixels, torso frame). */
    private static Vec3 bodyPoint(long seed) {
        double u = FilmFx.hash(seed * .31 + 1), v = FilmFx.hash(seed * .57 + 2), w = FilmFx.hash(seed * .79 + 3);
        if (w < .45) return new Vec3(-5.5 + u * 11, v * 12, w < .22 ? -3.4 : 3.2);
        if (w < .7) return new Vec3(w < .58 ? -7.8 : 7.8, -2 + v * 12, -2 + u * 4);
        if (w < .85) return new Vec3(-4.5 + u * 9, -8.8 - v * 1.5, -4 + v * 8);
        return new Vec3(-3 + u * 6, 12 + v * 10, -2.5 + u * 5);
    }
}
