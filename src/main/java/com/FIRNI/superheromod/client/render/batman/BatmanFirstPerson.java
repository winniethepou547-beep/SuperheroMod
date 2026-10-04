package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Batman's own view: the vanilla hand is gone; his gauntlets (fins and all) come into view for what he does. Every blow
 * of the chain drives a fist into the middle of the view and back (the hook sweeps across, the uppercut rises, the
 * elbow crosses low; the flurry alternates), the guard stays up at the edges for a while after a fight; the Batarang is
 * in his hand at the left before the whip; the held fan between both hands; the pellet lobbed overhand; the mine set
 * down; the grapnel gun held out to the crosshair (kicking on the shot), the left hand hauling the line in the yank;
 * the strike's uppercut; gliding, both hands low and wide holding the cape's edge. Nothing in the roll. In thermal
 * vision the gauntlets go cool blue-black and the gun's electronics glow warm.
 * Each arm is placed by its elbow in view space (+x right, +y up, -z ahead, blocks) and turned (pitch about x first,
 * then yaw about y; pitch -90 lays the forearm straight ahead, positive yaw turns it to the left).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanFirstPerson {
    private BatmanFirstPerson() {}

    /** One arm's placement: elbow (x, y, z), pitch, yaw, roll (degrees), wrist bend, fist, how much is in view (0 sunk out of sight). */
    private static final class Arm {
        float x, y, z, pitch, yaw, roll, wrist, curl, shown;
        int hold; float holdArg;
        Arm set(float x, float y, float z, float pitch, float yaw, float roll, float curl) {
            this.x = x; this.y = y; this.z = z; this.pitch = pitch; this.yaw = yaw; this.roll = roll; this.curl = curl;
            wrist = 0; shown = 1; hold = BatmanBody.HOLD_NONE; holdArg = 0;
            return this;
        }
        void copy(Arm o) { x = o.x; y = o.y; z = o.z; pitch = o.pitch; yaw = o.yaw; roll = o.roll; wrist = o.wrist; curl = o.curl; shown = o.shown; hold = o.hold; holdArg = o.holdArg; }
        void toward(Arm o, float k) {
            x += (o.x - x) * k; y += (o.y - y) * k; z += (o.z - z) * k; pitch += (o.pitch - pitch) * k; yaw += (o.yaw - yaw) * k; roll += (o.roll - roll) * k;
            wrist += (o.wrist - wrist) * k; curl += (o.curl - curl) * k; shown += (o.shown - shown) * k;
            if (k > .5f) { hold = o.hold; holdArg = o.holdArg; }
        }
        void lerp(Arm a, Arm b, float k) { copy(a); toward(b, k); }
    }
    private static final Arm[] NOW = {new Arm(), new Arm()}, FROM = {new Arm(), new Arm()}, LAST = {new Arm(), new Arm()}, A = {new Arm(), new Arm()}, B = {new Arm(), new Arm()};
    private static int lastKey = -1;
    private static float changed, lastFight = -1000;

    private static float ease(float x) { return PantherMotion.ease(x); }
    private static float k(float t, float a, float b) { return PantherMotion.k(t, a, b); }

    @SubscribeEvent public static void hand(RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !BatmanClient.isHero(mc.player) || FilmDirector.playing()) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        BatmanClient.State s = BatmanClient.get(mc.player);
        int action = s == null ? IDLE : s.action;
        float partial = e.getPartialTick();
        float t = s == null ? 0 : BatmanClient.clock(s, partial);
        float now = mc.level.getGameTime() + partial, time = mc.player.tickCount + partial;
        int combo = s == null ? 0 : s.combo;
        if (action == PUNCH || action == GRAPNEL_STRIKE) lastFight = now;
        boolean gliding = s != null && s.gliding();

        // Where each arm is for this move, crossfaded out of the last one.
        pose(action, t, combo, s, now, gliding, time);
        int key = action * 1000 + (action == PUNCH ? combo : 0) + (gliding ? 500 : 0);
        if (key != lastKey) {
            for (int i = 0; i < 2; i++) FROM[i].copy(LAST[i]);
            changed = now;
            lastKey = key;
        }
        float fade = action == PUNCH ? .7f : action == IDLE ? 3f : 1.6f;
        float blend = ease((now - changed) / fade);
        for (int i = 0; i < 2; i++) {
            if (blend < 1) { Arm m = A[i]; m.copy(FROM[i]); m.toward(NOW[i], blend); m.hold = NOW[i].hold; m.holdArg = NOW[i].holdArg; NOW[i].copy(m); }
            LAST[i].copy(NOW[i]);
        }

        PoseStack p = e.getPoseStack();
        MultiBufferSource b = e.getMultiBufferSource();
        int light = e.getPackedLight();
        float thermal = Mth.clamp(BatmanVision.thermalAmount(), 0, 1);
        BatmanBody.thermal = thermal;
        BatmanGear.thermal = thermal;
        BatmanBody.capture = true;
        BatmanBody.handRight = BatmanBody.handLeft = BatmanBody.muzzle = null;
        try {
            for (int side = 0; side < 2; side++) {
                Arm a = NOW[side];
                if (a.shown <= .02f) continue;
                float sx = side == 0 ? 1 : -1;
                float bob = .008f * Mth.sin(time * .1f + side);
                p.pushPose();
                p.translate(a.x, a.y - .7f * (1 - a.shown) + bob, a.z);
                p.mulPose(Axis.YP.rotationDegrees(a.yaw));
                p.mulPose(Axis.XP.rotationDegrees(a.pitch));
                p.mulPose(Axis.ZP.rotationDegrees(a.roll));
                BatmanBody.HOLD[side] = a.hold;
                BatmanBody.HOLD_ARG[side] = a.holdArg;
                BatmanBody.firstPersonArm(p, b, light, side, a.wrist, 0, a.curl);
                if (gliding && a.hold == BatmanBody.HOLD_NONE && action == IDLE) {
                    p.translate(0, 4.8f / 16, 0);
                    BatmanBody.firstPersonCape(p, b, light, side, time);
                }
                p.popPose();
                BatmanBody.HOLD[side] = BatmanBody.HOLD_NONE;
                BatmanBody.HOLD_ARG[side] = 0;
            }
        } finally {
            BatmanBody.capture = false;
            BatmanBody.thermal = 0;
            BatmanGear.thermal = 0;
        }
        // His own rope and Batarangs start from his hands as he sees them.
        if (BatmanBody.handRight != null || BatmanBody.handLeft != null)
            BatmanLayer.store(mc.player.getId(), BatmanBody.handRight, BatmanBody.handLeft, BatmanBody.muzzle);
    }

    /** Fills NOW with this move's placement of both arms. */
    private static void pose(int action, float t, int combo, BatmanClient.State s, float now, boolean gliding, float time) {
        Arm r = NOW[0], l = NOW[1];
        // At rest: out of sight below; after a fight the guard stays up at the edges a while.
        boolean guard = now - lastFight < 40;
        rest(r, 0, guard);
        rest(l, 1, guard);
        switch (action) {
            case PUNCH -> punch(blow(combo), t, punchTicks(combo));
            case BATARANG -> {
                Arm wind = A[0].set(-.12f, -.72f, -.55f, -55, 85, -20, .85f), out = B[0].set(.42f, -.55f, -.78f, -86, -38, 0, .2f);
                wind.hold = BatmanBody.HOLD_BATARANG;
                if (t < 1.6f) r.lerp(guardOr(0), wind, ease(t / 1.6f));
                else if (t < BATARANG_AT) r.copy(wind);
                else { r.lerp(wind, out, PantherMotion.snap(t, BATARANG_AT, BATARANG_AT + 1.2f)); r.hold = BatmanBody.HOLD_NONE; }
                if (t > BATARANG_AT + 2) { Arm away = B[1]; away.copy(out); away.shown = 0; r.lerp(out, away, k(t, BATARANG_AT + 2, BATARANG_TICKS)); }
            }
            case BATARANG_CHARGE -> {
                float in = ease(t / 3);
                float tension = PantherMotion.clamp(t / 20f), tremor = .004f * tension * Mth.sin(time * 2.3f);
                r.lerp(rest(A[0], 0, false), B[0].set(.2f, -.82f + tremor, -.58f, -52, 32, -10, .85f), in);
                l.lerp(rest(A[1], 1, false), B[1].set(-.2f, -.82f - tremor, -.58f, -52, -32, 10, .85f), in);
                r.hold = BatmanBody.HOLD_FAN; r.holdArg = s == null ? 1 : Math.max(1, s.charge);
            }
            case BATARANG_MULTI -> {
                int n = s == null ? 2 : Math.max(2, s.charge);
                Arm cr = A[0].set(.2f, -.8f, -.58f, -52, 32, -10, .85f), cl = A[1].set(-.2f, -.8f, -.58f, -52, -32, 10, .85f);
                Arm or = B[0].set(.62f, -.6f, -.72f, -86, -42, 0, .25f), ol = B[1].set(-.62f, -.6f, -.72f, -86, 42, 0, .25f);
                if (t < MULTI_AT) {
                    r.copy(cr); l.copy(cl);
                    r.hold = BatmanBody.HOLD_FAN; r.holdArg = (n + 1) / 2f; l.hold = BatmanBody.HOLD_FAN; l.holdArg = n / 2f;
                } else {
                    float o = PantherMotion.snap(t, MULTI_AT, MULTI_AT + 1.3f), down = k(t, MULTI_AT + 3, MULTI_TICKS);
                    r.lerp(cr, or, o); l.lerp(cl, ol, o);
                    r.hold = l.hold = BatmanBody.HOLD_NONE;
                    r.shown = l.shown = 1 - down;
                }
            }
            case GADGET_THROW -> {
                Arm back = A[0].set(.48f, -.42f, -.28f, -12, -10, 0, .9f), lob = B[0].set(.3f, -.46f, -.86f, -104, 6, 0, .2f);
                back.hold = BatmanBody.HOLD_PELLET; back.holdArg = s == null ? 0 : s.gadget;
                if (t < 2.2f) r.lerp(guardOr(0), back, ease(t / 2.2f));
                else if (t < GADGET_AT) r.copy(back);
                else { r.lerp(back, lob, PantherMotion.snap(t, GADGET_AT, GADGET_AT + 1.4f)); r.hold = BatmanBody.HOLD_NONE; r.shown = 1 - k(t, GADGET_AT + 2.5f, GADGET_TICKS); }
                l.set(-.4f, -.62f, -.8f, -84, 8, 0, .4f).shown = 1 - k(t, GADGET_AT + 1, GADGET_TICKS);
            }
            case MINE_PLACE -> {
                Arm carry = A[0].set(.28f, -.72f, -.62f, -62, 6, 0, .55f), set = B[0].set(.22f, -1.02f, -.74f, -104, 6, 0, .3f);
                carry.hold = BatmanBody.HOLD_MINE; carry.holdArg = .4f;
                if (t < MINE_AT) r.lerp(carry, set, k(t, 2.5f, MINE_AT));
                else { r.copy(set); r.shown = 1 - k(t, MINE_AT, MINE_AT + 3); }
                if (t < MINE_AT) { r.hold = BatmanBody.HOLD_MINE; r.holdArg = .4f; r.shown = k(t, 0, 2); }
            }
            case GRAPNEL_AIM, GRAPNEL_FIRE, GRAPNEL_PULL, GRAPNEL_YANK -> {
                float rec = action == GRAPNEL_FIRE || action == GRAPNEL_YANK ? BatmanMotion.recoil(t) : 0;
                if (action == GRAPNEL_PULL) r.set(.26f, -.36f, -.6f, -100, 12, 0, 1);
                else r.set(.3f, -.42f + .03f * rec, -.56f + .08f * rec, -92 + 16 * rec, 12, 0, 1);
                r.hold = BatmanBody.HOLD_GUN;
                r.holdArg = (s != null && s.hook != null) || action == GRAPNEL_PULL || action == GRAPNEL_YANK ? 1 : 0;
                if (action == GRAPNEL_YANK) {
                    Arm reach = A[1].set(-.14f, -.5f, -.82f, -90, -8, 0, .3f), haul = B[1].set(-.5f, -.98f, -.32f, -40, -20, 0, 1);
                    if (t < YANK_PULL) l.lerp(rest(A[0], 1, false), reach, ease(t / YANK_PULL));
                    else if (t < YANK_DOWN + 4) { l.lerp(reach, haul, PantherMotion.snap(t, YANK_PULL, YANK_DOWN)); l.curl = 1; }
                    else { l.copy(haul); l.shown = 1 - k(t, YANK_DOWN + 4, YANK_TICKS - 4); }
                } else l.set(-.36f, -.7f, -.72f, -78, -6, 0, .6f).shown = action == GRAPNEL_PULL ? 0 : .9f;
            }
            case GRAPNEL_STRIKE -> {
                Arm cock = A[0].set(.42f, -.95f, -.5f, -50, 0, 0, 1), up = B[0].set(.14f, -.36f, -.76f, -22, 8, 0, 1);
                if (t < STRIKE_UPPER) r.lerp(guardOr(0), cock, ease(t / STRIKE_UPPER));
                else { r.lerp(cock, up, PantherMotion.snap(t, STRIKE_UPPER, STRIKE_UPPER + 1.2f)); r.shown = 1 - k(t, STRIKE_JUMP, STRIKE_JUMP + 3); }
                l.copy(guardOr(1));
                l.shown = 1 - k(t, STRIKE_JUMP, STRIKE_JUMP + 3);
            }
            case DODGE -> { r.shown = 0; l.shown = 0; }
            default -> {}
        }
        // Gliding: both hands low and wide, holding the cape's edge.
        if (gliding && action == IDLE) {
            float flap = 1.5f * Mth.sin(time * .21f);
            r.set(.78f, -.72f, -.55f, -78, -58, -25 + flap, .85f);
            l.set(-.78f, -.72f, -.55f, -78, 58, 25 - flap, .85f);
        }
    }
    /** Out of sight, or (after a fight) the guard at the edges of the view. */
    private static Arm rest(Arm a, int side, boolean guard) {
        float sx = side == 0 ? 1 : -1;
        a.set(.44f * sx, -.84f, -.6f, -64, 10 * sx, 8 * sx, 1);
        a.shown = guard ? 1 : 0;
        return a;
    }
    private static final Arm[] G = {new Arm(), new Arm()}, C = {new Arm(), new Arm()};
    private static Arm guardOr(int side) { return rest(G[side], side, true); }

    /** One blow of the chain: the fist driven in fast, held through, recoiled back into the guard. */
    private static void punch(int blow, float t, int len) {
        float h = PUNCH_HIT * len;
        float in = PantherMotion.snap(t, h * .35f, h), out = k(t, h + (len - h) * .3f, len);
        float go = in * (1 - out);
        Arm r = NOW[0], l = NOW[1];
        rest(r, 0, true);
        rest(l, 1, true);
        switch (blow) {
            case B_LEFT -> l.lerp(rest(A[1], 1, true), B[1].set(-.1f, -.44f, -.86f, -88, -4, 0, 1), go);
            case B_HOOK -> {
                Arm load = A[0].set(.78f, -.58f, -.45f, -86, 70, -80, 1), hit = B[0].set(.06f, -.5f, -.78f, -90, 62, -85, 1);
                float wind = ease(t / Math.max(.5f, h * .35f));
                if (t < h * .35f) r.lerp(rest(A[1], 0, true), load, wind); else r.lerp(load, hit, in);
                if (out > 0) r.lerp(hit, rest(A[1], 0, true), out);
            }
            case B_UPPER -> {
                Arm low = A[1].set(-.32f, -1.05f, -.68f, -38, -6, 0, 1), up = B[1].set(-.08f, -.42f, -.78f, -22, -6, 0, 1);
                if (t < h * .35f) l.lerp(rest(C[1], 1, true), low, ease(t / Math.max(.5f, h * .35f))); else l.lerp(low, up, in);
                if (out > 0) l.lerp(up, rest(C[1], 1, true), out);
            }
            case B_ELBOW -> {
                Arm load = A[0].set(.62f, -.62f, -.45f, -90, 100, -85, 1), hit = B[0].set(-.04f, -.6f, -.62f, -90, 110, -85, 1);
                r.lerp(load, hit, in);
                if (t < h * .35f) r.lerp(rest(C[0], 0, true), load, ease(t / Math.max(.5f, h * .35f)));
                if (out > 0) r.lerp(hit, rest(C[0], 0, true), out);
            }
            default -> r.lerp(rest(A[0], 0, true), B[0].set(.1f, -.44f, -.86f, -88, 4, 0, 1), go);
        }
    }
}
