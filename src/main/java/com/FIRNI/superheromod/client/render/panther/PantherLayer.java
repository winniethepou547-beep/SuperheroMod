package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.client.gui.Showcase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * Black Panther himself, drawn in place of the player model (which is hidden for him). Each frame: the
 * move's pose from PantherMotion; a crossfade out of whatever his body was doing when the move changed
 * (a strike flows out of the last one, nothing snaps); his legs and arms running, prowling, tucking in
 * the air and absorbing landings underneath; the hit he just took; his look spread down the body (the
 * pelvis turns a little, the chest more, the head the rest). Moves that carry him along a path turn his
 * whole body to face along it. Each draw reports his claw tips, toes and eyes for their trails and
 * leaves afterimages behind fast moves.
 */
public final class PantherLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final class Blend {
        int action = -1; float changed, fade = 2, airSince = -1, landAt = -100, runAt; boolean ground = true; float run;
        float align, alignAt;
        Pose from, last;
        float lastGhost = -10;
    }
    private static final Map<Integer, Blend> BLENDS = new HashMap<>();

    /** How much of the glass the others see of him in his camouflage (his own view: all of it). */
    private static final float OTHERS_SEE = .38f;

    public PantherLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    /** How quickly the body crosses into a move (ticks). */
    private static float crossfade(int from, int to) {
        if (to == DODGE) return .7f;
        if (clawing(to) || to == POUNCE_LOAD || to == SPIN_LOAD || to == RELEASE_CHARGE) return 1.1f;
        if (to == POUNCE || to == POUNCE_FLIP || to == POUNCE_KICK || to == SPIN || to == RELEASE) return .9f;
        if (to == IDLE && (from == FRENZY || clawing(from))) return 2.6f;
        return 2.2f;
    }

    @Override
    public void render(PoseStack p, MultiBufferSource b, int light, AbstractClientPlayer e, float walk, float amount, float partial, float time, float yaw, float pitch) {
        if (!PantherClient.isHero(e)) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        PantherClient.State s = PantherClient.get(e);
        // Invisible for his camouflage: still drawn, as glass. Invisible for any other reason: not drawn.
        boolean camo = s != null && s.camoLeft > 0;
        if (e.isInvisible() && !camo) return;
        // In the camouflage: he sees himself as the glass; the others only from close (CAMO_SEEN blocks, a faint shimmer), not at all further off.
        float seen = 1;
        var viewer = Minecraft.getInstance().player;
        if (camo && viewer != null && viewer != e && !Showcase.is(e)) {
            double d = viewer.getPosition(partial).distanceTo(e.getPosition(partial));
            seen = OTHERS_SEE * (1 - ease((float) (d - (CAMO_SEEN - .6f)) / .6f));
            if (seen <= .005f) return;
        }
        int action = s == null ? IDLE : PantherClient.action(s);
        float t = s == null ? 0 : PantherClient.clock(s, partial);
        boolean shown = Showcase.is(e);
        if (shown) { action = Showcase.action(); t = Showcase.time(); }
        float now = level.getGameTime() + partial;
        float combat = s == null || shown ? (shown ? 1 : 0) : 1 - ease((s.quiet - 60) / 40f);
        float reflex = s == null || s.reflexLeft <= 0 ? 0 : Math.min(1, s.reflexLeft / 8f);
        Ctx ctx = new Ctx(time, combat, reflex, s == null ? 0 : s.flags, s == null ? 0 : s.threatYaw, shown ? 1 : s == null ? 0 : s.released);
        Pose pose = PantherMotion.sample(action, t, ctx);

        Blend blend = BLENDS.computeIfAbsent(e.getId(), id -> new Blend());
        if (BLENDS.size() > 64) BLENDS.clear();
        if (blend.action != action) {
            blend.from = blend.last == null ? null : blend.last.copy();
            blend.changed = now;
            // Coming out of a move into the stance, the crossfade is the follow-through.
            blend.fade = crossfade(blend.action, action);
            blend.action = action;
        }
        float k = (now - blend.changed) / blend.fade;
        if (blend.from != null && k >= 0 && k < 1) {
            Pose mixed = blend.from.copy();
            mixed.toward(pose, ease(k));
            // Whole turns are never blended the long way round.
            for (int i : new int[]{ROOT_PITCH, ROOT_YAW}) mixed.v[i] = Math.abs(pose.v[i] - blend.from.v[i]) > 3 ? pose.v[i] : mixed.v[i];
            pose = mixed;
        }
        blend.last = pose.copy();

        // ---- running, prowling, the air and the landings, under the move
        boolean ground = e.onGround() || shown;
        if (blend.ground && !ground) blend.airSince = now;
        if (!blend.ground && ground && blend.airSince >= 0 && now - blend.airSince > 5 && (action == IDLE || clawing(action))) blend.landAt = now;
        blend.ground = ground;
        float legs = action == IDLE ? 1 : clawing(action) && action != CROSS ? .7f : action == SNEAK ? .55f
                : action == POUNCE_LAND || action == SPIN_LAND || action == RELEASE_RECOVER ? .4f : 0;
        float arms = action == IDLE ? 1 : action == POUNCE_LAND || action == RELEASE_RECOVER ? .3f : 0;
        float want = e.isSprinting() && amount > .3f ? 1 : 0;
        float dt = Math.max(0, Math.min(2, now - blend.runAt));
        blend.runAt = now;
        blend.run += (want - blend.run) * (1 - (float) Math.exp(-dt * .3f));
        if (!shown) locomotion(pose, walk, Math.min(1, amount * 1.3f), blend.run, legs, arms, time);
        if (!ground && !shown && (action == IDLE || clawing(action))) {
            float air = ease((now - blend.airSince) / 2.5f);
            pose.add(PLANT, -air).legAdd(0, LEG_X, -.55f * air).legAdd(0, KNEE, 1.1f * air).legAdd(1, LEG_X, -.2f * air).legAdd(1, KNEE, .7f * air)
                    .legAdd(0, ANKLE, .4f * air).legAdd(1, ANKLE, .5f * air).add(SPINE_PITCH, .12f * air);
            for (int side = 0; side < 2; side++) pose.armAdd(side, ARM_Z, .35f * air).armAdd(side, ARM_X, -.2f * air);
        }
        float landed = now - blend.landAt;
        if (landed >= 0 && landed < 14) {
            float w = (float) Math.exp(-landed / 3.2f) * snap(landed, 0, .8f);
            pose.add(CROUCH, 4.5f * w).add(SPINE_PITCH, .25f * w).add(HEAD_PITCH, -.2f * w);
            for (int side = 0; side < 2; side++) pose.armAdd(side, ARM_Z, .3f * w);
        }
        // The second jump's flip.
        if (!shown) PantherMotion.flipJump(pose, PantherFx.jumpAge(e.getId(), now));
        if (s != null && !shown) PantherMotion.hurt(pose, (int) Math.min(100, s.hurtAge + Math.max(0, level.getGameTime() - s.received)), s.hurtPower, s.hurtYaw);

        // ---- his look, spread down the body: pelvis a little, then the spine and chest, the head the rest.
        var model = getParentModel();
        boolean path = action == POUNCE || action == POUNCE_FLIP || action == POUNCE_KICK || action == POUNCE_LAND || action == POUNCE_MISS
                || action == SPIN || action == SPIN_LAND || action == POUNCE_LOAD || action == DASH || action == CROSS;
        float look = path ? 0 : 1;
        float lookYaw = model.head.yRot * look, lookPitch = model.head.xRot * look;
        float spread = action == IDLE || clawing(action) || action == DODGE || action == SNEAK ? 1 : .4f;
        pose.add(PELVIS_YAW, .12f * lookYaw * spread * ground(pose)).add(SPINE_YAW, .14f * lookYaw * spread).add(CHEST_YAW, .2f * lookYaw * spread)
                .add(CHEST_PITCH, .2f * lookPitch * spread);
        float headYaw = lookYaw * (1 - (.12f * ground(pose) + .14f + .2f) * spread), headPitch = lookPitch * (1 - .2f * spread);

        // ---- moves along a path face along it, whatever the player's view does meanwhile
        float align = 0;
        if (s != null && !shown && path) {
            float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
            float facing = (float) Math.toDegrees(Math.atan2(-s.dir.x, s.dir.z));
            if (action == POUNCE_KICK || action == POUNCE_LAND) facing += 180;
            align = Mth.wrapDegrees(facing - bodyYaw) * Mth.DEG_TO_RAD;
        }
        // Exact along a path (the flip's half twist hands over to it at the kick); eased back out of it afterwards.
        float since = Math.max(0, Math.min(3, now - blend.alignAt));
        blend.alignAt = now;
        if (path) blend.align = align;
        else {
            blend.align *= (float) Math.exp(-since * .45f);
            if (Math.abs(blend.align) < .005f) blend.align = 0;
        }

        // ---- the light in the suit
        PantherBody.Charge charge = PantherBody.CHARGE.reset();
        if (s != null) {
            charge.level = s.energy;
            if (action == RELEASE_CHARGE) { charge.flow = clamp(t / CHARGE_TICKS); charge.level = Math.max(charge.level, s.released); }
            if (action == RELEASE) { charge.flash = 1.4f * (1 - k(t, 1, 3)); charge.level = 0; }
            for (float[] pu : s.pulses) charge.pulses.add(new float[]{now - pu[0], pu[1], pu[2]});
            if (s.reflexLeft > 0) charge.level = Math.max(charge.level, .1f + .05f * Mth.sin(now * .5f));
        }
        if (shown) { charge.level = Showcase.eyes() >= 0 ? Showcase.aura() : .25f; if (action == RELEASE_CHARGE) charge.flow = clamp(t / CHARGE_TICKS); if (action == RELEASE) charge.flash = 1.4f * (1 - k(t, 1, 3)); }
        pose.set(EYES, Math.max(pose.get(EYES), reflex * .45f + (s != null ? .25f * s.energy : 0)));

        PantherBody.capture = true;
        PantherBody.eyeRight = PantherBody.toeRight = PantherBody.toeLeft = null;
        java.util.Arrays.fill(PantherBody.CLAWS, null);
        try {
            body(p, b, light, e.getId(), pose, headYaw, headPitch, time, now, camo, seen, blend.align, charge);
        } finally {
            PantherBody.capture = false;
        }
        // Only a draw in the world counts (not the one in a menu).
        if (shown || PantherBody.eyeRight == null || PantherBody.eyeRight.distanceTo(e.getPosition(partial)) > 4) return;
        if (clawing(action) || action == DODGE) PantherFx.claws(e.getId(), PantherBody.CLAWS, now, action == FRENZY ? 1.3f : action == CLAW_UPPER || action == CLAW_DOUBLE || action == CROSS ? 1.4f : 1);
        if (action == POUNCE_KICK || action == SPIN || action == POUNCE_FLIP) PantherFx.kicks(e.getId(), PantherBody.toeRight, PantherBody.toeLeft, now, action == POUNCE_KICK ? 1.3f : 1);
        if (fast(action) && !camo && now - blend.lastGhost >= .5f) {
            blend.lastGhost = now;
            float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) + blend.align * Mth.RAD_TO_DEG;
            PantherFx.ghost(e.getId(), e.getPosition(partial), bodyYaw, pose, headYaw, headPitch, now, time);
        }
    }
    private static float ground(Pose p) { return clamp(p.get(PLANT)); }

    /**
     * The body as it is this frame: the suit; the camouflage (glass) and its fading in and out; or, just after a
     * hit tore the camouflage, the glitch: the body stuttering in and out with cyan and magenta copies of it
     * jittering either side.
     */
    private static void body(PoseStack p, MultiBufferSource b, int light, int id, Pose pose, float headYaw, float headPitch, float time, float now,
                             boolean camo, float seen, float align, PantherBody.Charge charge) {
        float[] change = PantherFx.camoChange(id);     // {level time, kind: 1 on, 0 off, -1 torn}
        float since = change == null ? 99 : now - change[0];
        int kind = change == null ? 0 : (int) change[1];
        float savedLevel = charge.level;
        if (camo) {
            charge.level = 0;
            PantherBody.draw(p, b, light, pose, headYaw, headPitch, time, PantherBody.CAMO, seen, align);
            // Fading into it: a pale copy of the suit melting away.
            if (kind == 1 && since < 10) {
                PantherBody.tint = new float[]{.55f, .62f, .75f};
                PantherBody.draw(p, b, light, pose, headYaw, headPitch, time, PantherBody.GHOST, .9f * (1 - since / 10f) * Math.min(1, seen * 2), align);
                PantherBody.tint = new float[]{.14f, .07f, .28f};
            }
            charge.level = savedLevel;
            return;
        }
        if (kind == -1 && since < 12) {
            // The glitch: stuttering frames, colour-split copies jumping about.
            int frame = (int) (now * 2.5f);
            float strength = 1 - since / 12f;
            boolean shown = PantherFx.hash(frame * 1.7f + id) > .3f * strength;
            if (shown) PantherBody.draw(p, b, light, pose, headYaw, headPitch, time, PantherBody.NORMAL, 1, align);
            for (int i = 0; i < 2; i++) {
                float jx = (PantherFx.hash(frame * 3.1f + i * 7 + id) - .5f) * 3.5f * strength, jy = (PantherFx.hash(frame * 5.3f + i * 11) - .5f) * 1.2f * strength;
                p.pushPose();
                p.translate((i == 0 ? -1.2f : 1.2f) * strength / 16 + jx / 16, jy / 16, 0);
                PantherBody.tint = i == 0 ? new float[]{.15f, .95f, 1f} : new float[]{1f, .2f, .85f};
                PantherBody.draw(p, b, light, pose, headYaw, headPitch, time, PantherBody.GHOST, .5f * strength, align);
                p.popPose();
            }
            PantherBody.tint = new float[]{.14f, .07f, .28f};
            return;
        }
        PantherBody.draw(p, b, light, pose, headYaw, headPitch, time, PantherBody.NORMAL, 1, align);
        // Coming out of it at its natural end: the glass filling back in.
        if (kind == 0 && change != null && since < 8) {
            PantherBody.tint = new float[]{.55f, .62f, .75f};
            PantherBody.draw(p, b, light, pose, headYaw, headPitch, time, PantherBody.GHOST, .5f * (1 - since / 8f), align);
            PantherBody.tint = new float[]{.14f, .07f, .28f};
        }
    }

    /**
     * Under the move: a low athletic run (leaning in, knees driving, arms pumping, the chest countering the
     * hips, a bounce in every stride) or a prowl (soft knees, shoulders rolling, head steady).
     */
    private static void locomotion(Pose p, float walk, float amount, float run, float legs, float arms, float time) {
        if (amount < .01f || legs + arms < .01f) return;
        float phase = walk * .6662f;
        float sin = Mth.sin(phase), cos = Mth.cos(phase);
        float prowl = amount * (1 - run), sprint = amount * run;
        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? 1 : -1;
            float swing = sg * cos;
            float lift = Math.max(0, sg * sin);       // the knee drives up on the forward swing
            p.legAdd(side, LEG_X, (swing * (.55f * prowl + 1.0f * sprint) + (side == 0 ? -.2f : .26f) * amount) * legs)
                    .legAdd(side, KNEE, (lift * (.55f * prowl + 1.45f * sprint)) * legs)
                    .legAdd(side, ANKLE, (Math.max(0, sg * cos) * .35f * sprint) * legs);
            p.armAdd(side, ARM_X, (-swing * (.25f * prowl + .95f * sprint)) * arms)
                    .armAdd(side, ELBOW, (.35f * sprint) * arms).armAdd(side, ARM_Z, -.15f * sprint * arms).armAdd(side, CURL, .25f * sprint * arms);
        }
        p.add(PLANT, -.55f * sprint * legs).add(SPINE_PITCH, .22f * sprint * legs).add(LIFT, (.9f * sprint + .25f * prowl) * Math.abs(sin) * legs)
                .add(CHEST_YAW, -.14f * cos * sprint * arms).add(PELVIS_YAW, .1f * cos * sprint * legs).add(HEAD_YAW, .06f * cos * sprint)
                .add(CHEST_ROLL, .05f * cos * prowl * arms).add(PELVIS_ROLL, -.03f * cos * prowl * legs).add(CROUCH, (.6f * prowl + .4f * sprint) * legs)
                .add(PELVIS_YAW, .22f * amount * legs).add(CHEST_YAW, -.1f * amount * arms).add(ROOT_PITCH, .2f * sprint * legs);
    }

    public static void clear() { BLENDS.clear(); }
}
