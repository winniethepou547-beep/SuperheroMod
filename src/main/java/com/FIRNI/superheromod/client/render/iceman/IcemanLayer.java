package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.gui.Showcase;
import com.FIRNI.superheromod.client.render.batman.BatmanMotion;
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
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Iceman himself, drawn in place of the player model (which is hidden for him): the move's pose (IcemanMotion)
 * crossfaded out of whatever his body was doing, the walk and the air under it (BatmanMotion), his look spread from the
 * chest to the head, the slides turning his whole body along their way; what his hand holds (IcemanWeaponFx.hold), the
 * shell on him (IcemanShellFx.body), the cold gathering in his hands. Frost dust falls off him as he moves. Each draw
 * reports where his hands, eyes, chest and weapon are (for the effects: the brush's stream leaves his hands).
 */
public final class IcemanLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final class Blend {
        int key = -1, previous = IDLE; float changed, fade = 2; Pose from, last;
        float at = -1, run, align, airSince = -1, landAt = -100, landPower, turn, heading; boolean ground = true; long dust;
    }
    private static final Map<Integer, Blend> BLENDS = new HashMap<>();
    /** The points as last drawn (world), and when. */
    private static final class Points { Vec3 right, left, eyes, chest, weaponBase, weaponTip; long time; }
    private static final Map<Integer, Points> POINTS = new HashMap<>();

    public IcemanLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    // ------------------------------------------------------------------ for the effects
    /** Where his hand is in the world (side 0 right, 1 left), as last drawn; null when not drawn lately. */
    public static Vec3 hand(int id, int side) { Points p = fresh(id); return p == null ? null : side == 0 ? p.right : p.left; }
    public static Vec3 eyes(int id) { Points p = fresh(id); return p == null ? null : p.eyes; }
    public static Vec3 chest(int id) { Points p = fresh(id); return p == null ? null : p.chest; }
    /** The weapon's grip and tip in the world, as last drawn (null when none in hand). */
    public static Vec3[] weapon(int id) { Points p = fresh(id); return p == null || p.weaponTip == null ? null : new Vec3[]{p.weaponBase, p.weaponTip}; }
    private static Points fresh(int id) {
        Points p = POINTS.get(id);
        var level = Minecraft.getInstance().level;
        if (p == null || level == null || level.getGameTime() - p.time > 2) return null;
        return p;
    }
    /** Records the points (the layer in third person, IcemanFirstPerson for his own view). */
    static void store(int id, Vec3 right, Vec3 left, Vec3 eyes, Vec3 chest, Vec3 weaponBase, Vec3 weaponTip) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (POINTS.size() > 64) POINTS.clear();
        Points p = POINTS.computeIfAbsent(id, k -> new Points());
        if (right != null) p.right = right;
        if (left != null) p.left = left;
        if (eyes != null) p.eyes = eyes;
        if (chest != null) p.chest = chest;
        p.weaponBase = weaponBase; p.weaponTip = weaponTip;
        p.time = level.getGameTime();
    }

    /** How quickly the body crosses into a move (ticks). */
    private static float crossfade(int from, int to) {
        if (to == STRIKE) return from == STRIKE ? .8f : 1.0f;
        if (to == DASH) return .8f;
        if (to == RELEASE) return .6f;
        if (to == SLIDE) return 2.5f;
        if (to == IDLE && from == SLIDE) return 4f;
        if (to == IDLE && from == SHELL_BREAK) return 4f;
        if (to == IDLE) return 3f;
        return 2f;
    }

    @Override
    public void render(PoseStack p, MultiBufferSource b, int light, AbstractClientPlayer e, float walk, float amount, float partial, float time, float yaw, float pitch) {
        if (!IcemanClient.isHero(e) || e.isInvisible()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        IcemanClient.State s = IcemanClient.get(e);
        boolean shown = Showcase.is(e);
        int action = shown ? Showcase.action() : s == null ? IDLE : s.action;
        float t = shown ? Showcase.time() : s == null ? 0 : IcemanClient.clock(s, partial);
        float now = level.getGameTime() + partial;
        Blend blend = BLENDS.computeIfAbsent(e.getId(), id -> new Blend());
        if (BLENDS.size() > 64) BLENDS.clear();
        float dt = blend.at < 0 ? 0 : Mth.clamp(now - blend.at, 0, 3);
        blend.at = now;

        // ---- how he moves: the heading's turn (the slide banks into it), his speed
        Vec3 vel = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        double flat = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (flat > .05) {
            float heading = (float) Math.atan2(-vel.x, vel.z);
            float d = Mth.wrapDegrees((heading - blend.heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            blend.turn += (Mth.clamp(d, -.3f, .3f) - blend.turn) * (1 - (float) Math.exp(-dt * .5f));
            blend.heading = heading;
        } else blend.turn *= (float) Math.exp(-dt * .3f);

        // ---- under the move: the stance, the walk and run, the air, the landings
        Pose base = IcemanMotion.stance(time);
        boolean armed = s != null && (s.weaponOut() || action == FORM) || shown && !Showcase.emptyHanded();
        int weapon = s == null ? W_SWORD : s.weapon;
        if (armed && (action == IDLE || action == WHEEL)) IcemanMotion.armed(base, weapon);
        boolean ground = e.onGround() || shown;
        if (blend.ground && !ground) blend.airSince = now;
        if (!blend.ground && ground && blend.airSince >= 0 && now - blend.airSince > 5) {
            blend.landAt = now;
            blend.landPower = Mth.clamp((now - blend.airSince) / 14f, .4f, 1);
        }
        blend.ground = ground;
        boolean free = action == IDLE || action == WHEEL || action == FORM;
        float legs = free ? 1 : action == STRIKE || action == BRUSH || action == CHARGE && weapon != W_SWORD ? .6f : 0;
        float arms = action == IDLE ? (armed ? .4f : 1) : 0;
        float wantRun = e.isSprinting() && amount > .3f ? 1 : 0;
        blend.run += (wantRun - blend.run) * (1 - (float) Math.exp(-dt * .3f));
        if (!shown) BatmanMotion.locomotion(base, walk, Math.min(1, amount * 1.3f), blend.run, legs, arms);
        boolean sliding = action == SLIDE || action == DASH;
        if (!ground && !shown && !sliding && (free || action == STRIKE || action == BRUSH))
            BatmanMotion.air(base, PantherMotion.ease((now - blend.airSince) / 2.5f));
        if (free) BatmanMotion.land(base, now - blend.landAt, blend.landPower);

        // ---- the move, crossfaded out of what the body was doing (each swing of the combo counts as a new move)
        var model = getParentModel();
        IcemanMotion.Ctx ctx = new IcemanMotion.Ctx(time, weapon, s == null ? 0 : s.combo, s == null ? (shown ? Showcase.aura() : 0) : s.charge,
                armed, s != null && s.brushTarget >= 0, e.getViewXRot(partial) * Mth.DEG_TO_RAD, blend.turn, (float) flat, e.getId());
        Pose pose = IcemanMotion.sample(action, t, base, ctx);
        int key = action * 1000 + (action == STRIKE ? (s == null ? 0 : s.combo) + weapon * 10 : 0);
        if (blend.key != key) {
            blend.from = blend.last == null ? null : blend.last.copy();
            blend.changed = now;
            blend.previous = blend.key < 0 ? IDLE : blend.key / 1000;
            blend.fade = crossfade(blend.previous, action);
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

        // ---- the slides face along their way
        float align = 0;
        boolean along = false;
        if (!shown && sliding && flat > .08) {
            float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
            float to = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
            align = Mth.wrapDegrees(to - bodyYaw) * Mth.DEG_TO_RAD;
            along = true;
        }
        float since = Math.min(3, dt);
        if (along) blend.align += Mth.wrapDegrees((align - blend.align) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * (1 - (float) Math.exp(-since * .5f));
        else { blend.align *= (float) Math.exp(-since * .4f); if (Math.abs(blend.align) < .004f) blend.align = 0; }

        // ---- his look, spread down the body (not while the body faces along a path or spins)
        boolean spinning = action == CHARGE && weapon == W_SWORD || action == STRIKE && weapon == W_SWORD && ctx.combo() == 2;
        float look = sliding || spinning || action == SHELL || action == SHELL_FORM || action == SHELL_BURST ? .2f : 1;
        float lookYaw = model.head.yRot * look, lookPitch = model.head.xRot * look;
        float spread = action == IDLE || action == WHEEL ? 1 : .5f;
        float addSpine = .12f * lookYaw * spread, addChest = .18f * lookYaw * spread, addPitch = .18f * lookPitch * spread;
        pose.add(SPINE_YAW, addSpine).add(CHEST_YAW, addChest).add(CHEST_PITCH, addPitch);
        float headYaw = lookYaw - addSpine - addChest, headPitch = lookPitch - addPitch;
        blend.last = pose.copy();
        blend.last.add(SPINE_YAW, -addSpine).add(CHEST_YAW, -addChest).add(CHEST_PITCH, -addPitch);

        // ---- what his hand holds, the shell, the cold in his hands
        if (s != null && !shown) {
            IcemanWeaponFx.hold(e.getId(), s, action, t, now);
            IcemanShellFx.body(e.getId(), s, action, t, now);
        } else {
            IcemanBody.WEAPON = armed ? weapon : -1;
            IcemanBody.WEAPON_FORM = action == FORM ? Mth.clamp(t / FORM_TICKS, 0, 1) : 1; IcemanBody.WEAPON_SIZE = 1; IcemanBody.WEAPON_CRACK = 0;
            IcemanBody.SHELL_COVER = 0;
        }
        float glow = 0;
        if (action == BRUSH) glow = PantherMotion.k(t, 0, BRUSH_RAISE);
        else if (action == GROUND) glow = PantherMotion.k(t, GROUND_DOWN - 4, GROUND_DOWN) * (1 - PantherMotion.k(t, GROUND_TICKS - 8, GROUND_TICKS));
        IcemanBody.HAND_GLOW[0] = Math.max(glow, action == FORM ? 1 - PantherMotion.k(t, FORM_TICKS - 2, FORM_TICKS + 2) : 0);
        IcemanBody.HAND_GLOW[1] = glow;

        // ---- draw
        IcemanBody.capture = !shown;
        try {
            IcemanBody.draw(p, b, light, pose, headYaw, headPitch, time, blend.align);
        } finally {
            IcemanBody.capture = false;
            IcemanBody.WEAPON = -1;
            IcemanBody.SHELL_COVER = 0; IcemanBody.SHELL_CRACK = 0; IcemanBody.SHELL_FLASH = 0; IcemanBody.SHELL_GLOW = 0;
            IcemanBody.HAND_GLOW[0] = IcemanBody.HAND_GLOW[1] = 0;
        }
        Vec3 hr = IcemanBody.handRight;
        if (!shown && hr != null && hr.distanceTo(e.getPosition(partial)) < 6) {
            // Not his own body seen from inside his own view: the first person keeps the points then.
            var mc = Minecraft.getInstance();
            boolean own = e == mc.player && mc.options.getCameraType().isFirstPerson();
            if (!own) store(e.getId(), hr, IcemanBody.handLeft, IcemanBody.eyes, IcemanBody.chest, IcemanBody.weaponBase, IcemanBody.weaponTip);
            // Frost dust off him as he moves (now and then, never a stream).
            long tick = level.getGameTime();
            if (flat > .06 && tick != blend.dust && !shown) {
                blend.dust = tick;
                if (IceParticles.rand() < .35f + flat) {
                    Vec3 at = IceParticles.rand() < .5f ? hr : e.getPosition(partial).add(IceParticles.jitter(.25)).add(0, .1 + IceParticles.rand() * 1.4, 0);
                    IceParticles.frostDust(at, vel, (float) Math.min(1.5, .5 + flat * 2));
                }
            }
        }
    }

    public static void clear() { BLENDS.clear(); POINTS.clear(); }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                var renderer = e.getSkin(skin);
                if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) player.addLayer(new IcemanLayer(player));
            }
        }
    }
}
