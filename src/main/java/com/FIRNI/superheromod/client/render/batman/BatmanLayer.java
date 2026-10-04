package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.gui.Showcase;
import com.FIRNI.superheromod.client.render.cloth.CapeCloth;
import com.FIRNI.superheromod.client.render.magneto.SpikePull;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Batman himself, drawn in place of the player model (which is hidden for him): the move's pose from BatmanMotion
 * crossfaded out of whatever his body was doing (every new blow of the chain flows out of the last), walking and running
 * under it, the air and the landings, the glide blended in over CAPE_OPEN ticks (diving tips him over), his look spread
 * from the chest to the head, the grapnel arm laid exactly on his aim (or on the line while he is pulled along it), the
 * roll and the pull turning his whole body along their way; the gear in his hands; the cape (CapeCloth: simulated in the
 * world, spread into wings on his arms while he glides). Each draw reports where his hands and the gun's muzzle are.
 */
public final class BatmanLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final class Blend {
        int key = -1, previous = IDLE; float changed, fade = 2; Pose from, last;
        float at = -1, glide, dive, run, align, lean, airSince = -1, landAt = -100, landPower; boolean ground = true;
    }
    private static final Map<Integer, Blend> BLENDS = new HashMap<>();
    /** The hands and the muzzle as last drawn (world), and the level time they were drawn at. */
    private static final class Points { Vec3 right, left, muzzle; long time; }
    private static final Map<Integer, Points> POINTS = new HashMap<>();

    private static final CapeCloth.Frame FRAME = new CapeCloth.Frame();
    /** The cape: long and wide, heavy, black, flaring toward a scalloped hem near the ground. */
    public static final CapeCloth.Style CAPE = new CapeCloth.Style().length(1.42f).bottom(1.4f).wingBottom(.5f).thickness(.035f).weight(1.25f)
            .scallop(.09f).folds(.045f).colours(new float[]{.05f, .05f, .056f}, new float[]{.028f, .028f, .032f}, new float[]{.075f, .075f, .082f});

    public BatmanLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    // ------------------------------------------------------------------ for the effects
    /** Where his hand is in the world (side 0 right, 1 left), as last drawn; null when not drawn lately. */
    public static Vec3 hand(int entityId, int side) {
        Points p = fresh(entityId);
        return p == null ? null : side == 0 ? p.right : p.left;
    }
    /** Where the grapnel gun's muzzle is in the world, as last drawn; null when the gun is not in his hand (or not drawn lately). */
    public static Vec3 muzzle(int entityId) {
        Points p = fresh(entityId);
        return p == null ? null : p.muzzle;
    }
    private static Points fresh(int id) {
        Points p = POINTS.get(id);
        var level = Minecraft.getInstance().level;
        if (p == null || level == null || level.getGameTime() - p.time > 2) return null;
        return p;
    }
    /** Records the points (the layer in third person, BatmanFirstPerson for his own view). */
    static void store(int id, Vec3 right, Vec3 left, Vec3 muzzle) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (POINTS.size() > 64) POINTS.clear();
        Points p = POINTS.computeIfAbsent(id, k -> new Points());
        p.right = right; p.left = left; p.muzzle = muzzle; p.time = level.getGameTime();
    }

    /** How quickly the body crosses into a move (ticks). */
    private static float crossfade(int from, int to, int combo) {
        if (to == PUNCH) return from == PUNCH ? (combo >= RAPID ? .6f : .9f) : 1.1f;
        if (to == DODGE) return .5f;
        if (to == GRAPNEL_STRIKE || to == BATARANG || to == GRAPNEL_YANK) return .9f;
        if (to == BATARANG_MULTI && from == BATARANG_CHARGE) return 1.2f;
        if (to == IDLE && from == DODGE) return 2.2f;
        if (to == IDLE) return 3.2f;
        return 2.2f;
    }

    @Override
    public void render(PoseStack p, MultiBufferSource b, int light, AbstractClientPlayer e, float walk, float amount, float partial, float time, float yaw, float pitch) {
        if (!BatmanClient.isHero(e) || e.isInvisible()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        BatmanClient.State s = BatmanClient.get(e);
        boolean shown = Showcase.is(e);
        int action = shown ? Showcase.action() : s == null ? IDLE : s.action;
        float t = shown ? Showcase.time() : s == null ? 0 : BatmanClient.clock(s, partial);
        int combo = s == null ? 0 : s.combo;
        float now = level.getGameTime() + partial;
        Blend blend = BLENDS.computeIfAbsent(e.getId(), id -> new Blend());
        if (BLENDS.size() > 64) BLENDS.clear();
        float dt = blend.at < 0 ? 0 : Mth.clamp(now - blend.at, 0, 3);
        blend.at = now;

        // ---- the glide, eased in and out over the cape's opening; diving (looking down, falling fast) tips him over
        boolean gliding = shown ? Showcase.aura() > .5f : s != null && s.gliding();
        blend.glide = Mth.clamp(blend.glide + (gliding ? dt : -dt) / CAPE_OPEN, 0, 1);
        float glide = PantherMotion.ease(blend.glide);
        Vec3 vel = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        float lookDown = shown ? 0 : Mth.clamp((e.getViewXRot(partial) - 20) / 45f, 0, 1);
        float falling = (float) Mth.clamp((-vel.y - .35) / .6, 0, 1);
        float wantDive = gliding ? Math.max(lookDown, falling) : 0;
        blend.dive += (wantDive - blend.dive) * (1 - (float) Math.exp(-dt * .25f));
        float dive = blend.dive;

        // ---- under the move: the stance (or the glide), the walk and run, the air, the landings
        Pose base = BatmanMotion.stance(time);
        if (glide > 0) base.toward(BatmanMotion.glide(time, dive), glide);
        boolean ground = e.onGround() || shown;
        if (blend.ground && !ground) blend.airSince = now;
        if (!blend.ground && ground && blend.airSince >= 0 && now - blend.airSince > 5) {
            blend.landAt = now;
            blend.landPower = Mth.clamp((now - blend.airSince) / 14f, .4f, 1);
        }
        blend.ground = ground;
        boolean free = action == IDLE || action == WHEEL || action == GRAPNEL_AIM || action == GRAPNEL_FIRE;
        float legs = free ? 1 : action == PUNCH || action == BATARANG || action == BATARANG_CHARGE || action == BATARANG_MULTI || action == GADGET_THROW ? .6f : 0;
        float arms = action == IDLE ? 1 : 0;
        float wantRun = e.isSprinting() && amount > .3f ? 1 : 0;
        blend.run += (wantRun - blend.run) * (1 - (float) Math.exp(-dt * .3f));
        if (!shown && glide < .99f) BatmanMotion.locomotion(base, walk, Math.min(1, amount * 1.3f) * (1 - glide), blend.run, legs, arms);
        if (!ground && !shown && glide < .99f && (free || action == PUNCH || action == BATARANG || action == GADGET_THROW))
            BatmanMotion.air(base, PantherMotion.ease((now - blend.airSince) / 2.5f) * (1 - glide));
        if (free) BatmanMotion.land(base, now - blend.landAt, blend.landPower);

        // ---- the move, crossfaded out of what the body was doing (each new blow of the chain counts as a new move)
        float rollYaw = s == null ? 0 : s.dodgeYaw;
        boolean back = Math.abs(Mth.wrapDegrees(rollYaw * Mth.RAD_TO_DEG)) > 125;
        Pose pose = BatmanMotion.sample(action, t, base, new BatmanMotion.Ctx(time, combo, s == null ? 1 : s.charge, back));
        int key = action * 1000 + (action == PUNCH ? combo : 0);
        if (blend.key != key) {
            blend.from = blend.last == null ? null : blend.last.copy();
            blend.changed = now;
            blend.previous = blend.key < 0 ? IDLE : blend.key / 1000;
            blend.fade = crossfade(blend.previous, action, combo);
            blend.key = key;
        }
        float k = (now - blend.changed) / blend.fade;
        if (blend.from != null && k >= 0 && k < 1) {
            Pose mixed = blend.from.copy();
            mixed.toward(pose, PantherMotion.ease(k));
            // Whole turns are never blended the long way round.
            for (int i : new int[]{ROOT_PITCH, ROOT_YAW}) if (Math.abs(pose.v[i] - blend.from.v[i]) > 3) mixed.v[i] = pose.v[i];
            pose = mixed;
        }
        SpikePull.panther(pose, e, partial);

        // ---- the roll and the pull face along their way; the pull leans him along the line
        float align = 0;
        boolean along = false;
        float lean = 0, elevation = 0;
        if (s != null && !shown && action == DODGE) {
            float a = back ? Mth.wrapDegrees(rollYaw * Mth.RAD_TO_DEG - 180) * Mth.DEG_TO_RAD : rollYaw;
            align = a * PantherMotion.k(t, 0, .8f);
            along = true;
        }
        Vec3 hook = s == null || s.hook == null ? null : s.hookPrev == null ? s.hook : s.hookPrev.lerp(s.hook, Mth.clamp(now - s.received, 0, 1));
        if (s != null && !shown && action == GRAPNEL_PULL && hook != null) {
            Vec3 from = e.getPosition(partial).add(0, 1.4, 0), d = hook.subtract(from);
            double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
            float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
            float to = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            if (horizontal > .3) align = Mth.wrapDegrees(to - bodyYaw) * Mth.DEG_TO_RAD;
            elevation = (float) Math.atan2(d.y, Math.max(1e-3, horizontal));
            lean = Mth.clamp((Mth.HALF_PI - elevation) * .7f, .1f, 1.2f);
            along = true;
        }
        float since = Math.min(3, dt);
        if (along) blend.align = align;
        else { blend.align *= (float) Math.exp(-since * .4f); if (Math.abs(blend.align) < .004f) blend.align = 0; }
        blend.lean += (lean - blend.lean) * (1 - (float) Math.exp(-since * .35f));
        pose.add(ROOT_PITCH, blend.lean);

        // ---- his look, spread down the body (not while the body faces along a path)
        var model = getParentModel();
        float look = action == DODGE || action == GRAPNEL_PULL || action == GRAPNEL_STRIKE ? 0 : 1;
        float lookYaw = model.head.yRot * look, lookPitch = model.head.xRot * look;
        float spread = (action == IDLE || action == WHEEL ? 1 : .5f) * (1 - .7f * glide);
        float addPelvis = .1f * lookYaw * spread * PantherMotion.clamp(pose.get(PLANT)), addSpine = .12f * lookYaw * spread, addChest = .18f * lookYaw * spread,
                addPitch = .18f * lookPitch * spread;
        pose.add(PELVIS_YAW, addPelvis).add(SPINE_YAW, addSpine).add(CHEST_YAW, addChest).add(CHEST_PITCH, addPitch);
        float headYaw = model.head.yRot * look - addPelvis - addSpine - addChest, headPitch = lookPitch - addPitch;

        // ---- the gun arm: laid on the aim, or on the line while he is pulled; the kick of the shot
        int was = blend.previous;
        boolean aimedBefore = was == GRAPNEL_AIM || was == GRAPNEL_FIRE || was == GRAPNEL_PULL || was == GRAPNEL_YANK;
        float aimIn = blend.from == null || aimedBefore ? 1 : PantherMotion.ease((now - blend.changed) / 2.2f);
        if (action == GRAPNEL_AIM || action == GRAPNEL_FIRE) {
            BatmanMotion.aim(pose, 0, BatmanMotion.look(model.head.yRot, model.head.xRot), aimIn);
            if (action == GRAPNEL_FIRE) recoil(pose, BatmanMotion.recoil(t));
        } else if (action == GRAPNEL_YANK) {
            float w = 1 - .45f * PantherMotion.k(t, YANK_PULL, YANK_DOWN + 4) - .55f * PantherMotion.k(t, YANK_TICKS - 8, YANK_TICKS);
            BatmanMotion.aim(pose, 0, BatmanMotion.look(model.head.yRot, model.head.xRot), w);
            recoil(pose, BatmanMotion.recoil(t));
        } else if (action == GRAPNEL_PULL) {
            float c = Mth.cos(elevation), sn = Mth.sin(elevation);
            BatmanMotion.aim(pose, 0, hook == null ? new float[]{0, -.7f, -.7f} : new float[]{0, -sn, -c}, aimIn);
        }
        // The pose the next move fades from (without this frame's look, which is added again every frame).
        blend.last = pose.copy();
        blend.last.add(PELVIS_YAW, -addPelvis).add(SPINE_YAW, -addSpine).add(CHEST_YAW, -addChest).add(CHEST_PITCH, -addPitch);

        // ---- what the hands hold
        boolean gun = !(shown && Showcase.emptyHanded()) && (action == GRAPNEL_AIM || action == GRAPNEL_FIRE || action == GRAPNEL_PULL || action == GRAPNEL_YANK
                || (s != null && s.aiming() && action == IDLE));
        int charge = s == null ? 3 : Math.max(1, s.charge);
        hold(0, BatmanBody.HOLD_NONE, 0);
        hold(1, BatmanBody.HOLD_NONE, 0);
        if (gun) hold(0, BatmanBody.HOLD_GUN, (s != null && s.hook != null) || action == GRAPNEL_PULL || action == GRAPNEL_YANK ? 1 : 0);
        else if (action == BATARANG && t < BATARANG_AT) hold(0, BatmanBody.HOLD_BATARANG, 0);
        else if (action == BATARANG_CHARGE) hold(0, BatmanBody.HOLD_FAN, charge);
        else if (action == BATARANG_MULTI && t < MULTI_AT) { hold(0, BatmanBody.HOLD_FAN, (charge + 1) / 2f); if (charge > 1) hold(1, BatmanBody.HOLD_FAN, charge / 2f); }
        else if (action == GADGET_THROW && t < GADGET_AT) hold(0, BatmanBody.HOLD_PELLET, s == null ? 0 : s.gadget);
        else if (action == MINE_PLACE && t < MINE_AT) hold(0, BatmanBody.HOLD_MINE, .4f);

        // ---- draw: the body (reporting the cape's frame and the hands), then the cape
        BatmanBody.capture = true;
        try {
            BatmanBody.draw(p, b, light, pose, headYaw, headPitch, time, blend.align, FRAME);
        } finally {
            BatmanBody.capture = false;
            hold(0, BatmanBody.HOLD_NONE, 0);
            hold(1, BatmanBody.HOLD_NONE, 0);
        }
        FRAME.spread = glide * (action == IDLE ? 1 : .35f) * (1 - .6f * dive);
        CapeCloth.draw(p, b, light, e, partial, e.getId(), FRAME, CAPE);
        // Only a draw in the world counts (not the one in a menu).
        Vec3 hr = BatmanBody.handRight;
        if (!shown && hr != null && hr.distanceTo(e.getPosition(partial)) < 4)
            store(e.getId(), hr, BatmanBody.handLeft, gun ? BatmanBody.muzzle : null);
    }
    private static void hold(int side, int what, float arg) { BatmanBody.HOLD[side] = what; BatmanBody.HOLD_ARG[side] = arg; }
    /** The shot kicks the gun arm up and the shoulder back. */
    private static void recoil(Pose p, float r) {
        if (r <= .001f) return;
        p.armAdd(0, ARM_X, -.32f * r).armAdd(0, ELBOW, .35f * r).armAdd(0, SH_FWD, -.8f * r).armAdd(0, WRIST_X, -.25f * r).add(CHEST_YAW, .06f * r);
    }

    public static void clear() { BLENDS.clear(); POINTS.clear(); CapeCloth.clear(); }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                var renderer = e.getSkin(skin);
                if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) player.addLayer(new BatmanLayer(player));
            }
        }
    }
}
