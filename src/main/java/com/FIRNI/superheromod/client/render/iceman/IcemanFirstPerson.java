package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Iceman's own view: the vanilla hand is gone; his own arms of ice, from the shoulders down, hung just under the camera
 * and moved by the very same poses as his body (IcemanMotion), so every move (the brush's raised hands, a swing of the
 * mace, the spear drawn back, both hands on the spear in the flurry, the slide's arms, the hands to the ground) shows in
 * his view as it does on him. Standing, the right hand (and what it holds) is kept a little forward so it shows at the
 * lower right. While the sword stands planted his arms go down to its hilt in the world itself (IcemanWeaponFx), not here.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanFirstPerson {
    private IcemanFirstPerson() {}

    private static int lastKey = -1;
    private static float changed, fade = 2;
    private static Pose from, last;

    @SubscribeEvent public static void hand(RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !IcemanClient.isHero(mc.player) || FilmDirector.playing()) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        // The sword standing planted: his arms are drawn in the world onto its hilt (IcemanWeaponFx.ownArms), in the
        // world's own projection so the hands meet the hilt exactly; nothing here.
        if (IcemanWeaponFx.ownArmsInWorld()) { last = null; return; }
        var player = mc.player;
        IcemanClient.State s = IcemanClient.get(player);
        int action = s == null ? IDLE : s.action;
        float partial = e.getPartialTick();
        float t = s == null ? 0 : IcemanClient.clock(s, partial);
        float now = mc.level.getGameTime() + partial, time = player.tickCount + partial;
        int weapon = s == null ? W_SWORD : s.weapon;
        boolean armed = s != null && (s.weaponOut() || action == FORM);

        // ---- the pose, as on his body (without the walk; the view bobs by itself)
        Pose base = IcemanMotion.stance(time);
        if (armed && (action == IDLE || action == WHEEL)) IcemanMotion.armed(base, weapon);
        IcemanMotion.Ctx ctx = new IcemanMotion.Ctx(time, weapon, s == null ? 0 : s.combo, s == null ? 0 : s.charge, armed,
                s != null && s.brushTarget >= 0, player.getViewXRot(partial) * Mth.DEG_TO_RAD, 0, 0, player.getId());
        Pose pose = IcemanMotion.sample(action, t, base, ctx);
        // Standing: the right hand forward enough to be seen (with what it holds); the left out of the way.
        if (action == IDLE || action == WHEEL) {
            pose.arm(0, ARM_X, armed ? -.75f : -.55f).arm(0, ARM_Y, .15f).arm(0, ARM_Z, .25f).arm(0, ELBOW, armed ? 1.15f : .95f)
                    .arm(0, WRIST_X, armed ? .55f : .1f).arm(0, CURL, armed ? 1 : .35f);
            float bob = Mth.sin(time * .08f) * .02f;
            pose.armAdd(0, ARM_X, bob);
        }
        // In the shell his crossed forearms would cover the whole view: in his own view they stay low at the bottom edge.
        if (action == SHELL_FORM || action == SHELL || action == SHELL_BURST && t < BURST_STRESS) {
            for (int side = 0; side < 2; side++)
                pose.arm(side, ARM_X, -.5f).arm(side, ARM_Y, -.35f).arm(side, ARM_Z, .15f).arm(side, ELBOW, 1.5f).arm(side, CURL, .8f).arm(side, WRIST_X, .2f);
        }
        int key = action * 1000 + (action == STRIKE ? (s == null ? 0 : s.combo) + weapon * 10 : 0);
        if (key != lastKey) {
            from = last == null ? null : last.copy();
            changed = now;
            fade = action == STRIKE ? .8f : action == IDLE ? 3f : 2f;
            lastKey = key;
        }
        float k = (now - changed) / fade;
        if (from != null && k >= 0 && k < 1) {
            Pose mixed = from.copy();
            mixed.toward(pose, PantherMotion.ease(k));
            pose = mixed;
        }
        last = pose.copy();

        // ---- what the hand holds, the shell, the cold in the hands (as the layer sets them)
        if (s != null) {
            IcemanWeaponFx.hold(player.getId(), s, action, t, now);
            IcemanShellFx.body(player.getId(), s, action, t, now);
        }
        float glow = action == BRUSH ? PantherMotion.k(t, 0, BRUSH_RAISE)
                : action == GROUND ? PantherMotion.k(t, GROUND_DOWN - 4, GROUND_DOWN) * (1 - PantherMotion.k(t, GROUND_TICKS - 8, GROUND_TICKS)) : 0;
        IcemanBody.HAND_GLOW[0] = Math.max(glow, action == FORM ? 1 - PantherMotion.k(t, FORM_TICKS - 2, FORM_TICKS + 2) : 0);
        IcemanBody.HAND_GLOW[1] = glow;

        // ---- the chest's frame just under and behind the camera; the model turned to view space (+y up, -z ahead)
        PoseStack p = e.getPoseStack();
        p.pushPose();
        // The view's own sway is in the stack already; the arms come in a little lower when crouching into the slide.
        float low = action == SLIDE || action == DASH || action == SLIDE_END ? .06f : 0;
        p.translate(0, -.5f - low, .07f);
        p.mulPose(Axis.ZP.rotation(Mth.PI));
        p.scale(1 / 16f, 1 / 16f, 1 / 16f);
        // The chest's own turn and lean from the pose (the swings turn the shoulders), half of it: the view stays steady.
        float[] v = pose.v;
        p.mulPose(Axis.YP.rotation((v[CHEST_YAW] + v[SPINE_YAW] + v[PELVIS_YAW]) * .5f));
        // The sword's finisher spin: his arms (and the blade) sweep round through his view with the whole body's turn.
        boolean spin = weapon == W_SWORD && action == STRIKE && s != null && s.combo == 2;
        if (spin) p.mulPose(Axis.YP.rotation(v[ROOT_YAW]));
        p.mulPose(Axis.XP.rotation((v[CHEST_PITCH] + v[SPINE_PITCH]) * .35f));
        IcemanBody.capture = true;
        try {
            IcemanBody.drawArms(p, e.getMultiBufferSource(), e.getPackedLight(), pose, time, new boolean[]{true, true});
            IcemanLayer.store(player.getId(), IcemanBody.handRight, IcemanBody.handLeft, null, null, IcemanBody.weaponBase, IcemanBody.weaponTip);
        } finally {
            IcemanBody.capture = false;
            IcemanBody.WEAPON = -1;
            IcemanBody.SHELL_COVER = 0;
            IcemanBody.HAND_GLOW[0] = IcemanBody.HAND_GLOW[1] = 0;
            p.popPose();
        }
    }
}
