package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.heroes.batman.BatmanShock;
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
        // The wrist cannon (BatmanCannonFx): how far both gauntlets are open for this draw.
        BatmanBody.CANNON[0] = BatmanBody.CANNON[1] = s != null && action == CANNON ? BatmanCannonFx.deployed(s, action, t) : 0;
        // The electric gauntlets (BatmanShockFx): how much of them is on, and their charge, for this draw.
        BatmanBody.SHOCK[0] = BatmanBody.SHOCK[1] = s != null ? BatmanShockFx.worn(s, action, t) : 0;
        BatmanBody.shockEnergy = s == null ? 1 : s.energy;
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
            BatmanBody.CANNON[0] = BatmanBody.CANNON[1] = 0;
            BatmanBody.SHOCK[0] = BatmanBody.SHOCK[1] = 0;
            BatmanBody.thermal = 0;
            BatmanGear.thermal = 0;
        }
        // The cape dragged across in front of him (the reflex block's cape deflect).
        float[] sheet = capeSheet(now);
        if (sheet != null) {
            p.pushPose();
            p.translate(sheet[0], sheet[1], -.7f);
            p.mulPose(Axis.YP.rotationDegrees(sheet[2]));
            BatmanBody.firstPersonCapeSheet(p, b, light, 1.2f, 1.0f, sheet[3], time);
            p.popPose();
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
            // The reflex window: both forearms up at the edges of the view, the spikes out.
            case REFLEX -> { guardUp(r, 0); guardUp(l, 1); }
            default -> {}
        }
        // ---- a reflex deflect plays over whatever the arms are doing (the cape sheet itself is drawn in hand())
        var mc = Minecraft.getInstance();
        float[] d = mc.player == null ? null : BatmanReflexFx.deflect(mc.player.getId(), now);
        if (d != null) deflect((int) d[0], d[1]);
        // ---- the wrist cannon (its own block below)
        if (action == CANNON) cannon(t, s);
        // ---- the electric gauntlets: the boxer's guard, the heavy blows, locking on and coming off (its own block below)
        if (s != null && (action == SHOCK_EQUIP || action == SHOCK_UNEQUIP || action == SHOCK_PUNCH || (s.shock && action == IDLE))) shock(action, t, s, time);
        // ---- the sonic trap's remote in the left hand (its own block at the end)
        sonic(action, t, s, gliding, now);
        // Gliding: both hands low and wide, holding the cape's edge.
        if (gliding && action == IDLE) {
            float flap = 1.5f * Mth.sin(time * .21f);
            r.set(.78f, -.72f, -.55f, -78, -58, -25 + flap, .85f);
            l.set(-.78f, -.72f, -.55f, -78, 58, 25 - flap, .85f);
        }
    }
    /** The reflex guard: the forearm up and angled in at that side of the view. */
    private static Arm guardUp(Arm a, int side) {
        float sx = side == 0 ? 1 : -1;
        return a.set(.36f * sx, -.52f, -.6f, -105, 30 * sx, -20 * sx, 1);
    }
    private static final Arm[] DA = {new Arm(), new Arm()}, DB = {new Arm(), new Arm()}, DC = {new Arm(), new Arm()};
    /**
     * A deflect as he sees it, t ticks in: the right gauntlet swept across into the blow and thrown out to the right
     * (the left mirrored), both crossed in an X before the face and the right shoving it aside, a slip to one side with
     * the guard kept up (the view sways with it), or the cape: the right hand reaches back for its edge and drags it
     * across in front (hand() draws the cloth), holds it, lets it drop.
     */
    private static void deflect(int kind, float t) {
        float len = BatmanMotion.deflectLength(kind);
        float w = k(t, 0, 1.2f) * (1 - k(t, len - 3, len));
        if (w <= 0) return;
        float sweep = k(t, 1, 3.5f);
        Arm r = NOW[0], l = NOW[1], wr = DC[0], wl = DC[1];
        guardUp(wr, 0); guardUp(wl, 1);
        switch (kind) {
            case BLOCK_RIGHT, BLOCK_LEFT -> {
                int side = kind == BLOCK_RIGHT ? 0 : 1;
                float sx = side == 0 ? 1 : -1;
                Arm in = DA[side].set(.1f * sx, -.4f, -.6f, -100, 75 * sx, -70 * sx, 1), out = DB[side].set(.74f * sx, -.42f, -.55f, -95, -45 * sx, 15 * sx, 1);
                Arm hit = side == 0 ? wr : wl, other = side == 0 ? wl : wr;
                hit.lerp(guardUp(DB[1 - side], side), in, k(t, 0, 1.2f));
                if (sweep > 0) hit.lerp(in, out, sweep);
                other.y -= .12f; other.shown = .8f;
            }
            case BLOCK_FRONT -> {
                Arm xr = DA[0].set(.07f, -.42f, -.6f, -105, 60, -45, 1), xl = DA[1].set(-.07f, -.4f, -.58f, -105, -60, 45, 1);
                Arm shove = DB[0].set(.72f, -.45f, -.55f, -90, -40, 10, 1);
                wr.lerp(guardUp(DB[1], 0), xr, k(t, 0, 1.2f));
                wl.lerp(guardUp(DC[1], 1), xl, k(t, 0, 1.2f));
                if (sweep > .3f) wr.lerp(xr, shove, k(t, 2.2f, 4.5f));
            }
            case BLOCK_EVADE_R, BLOCK_EVADE_L -> {
                // The hands stay up; the whole view slips aside (BatmanReflexFx sways the camera).
                float s = kind == BLOCK_EVADE_R ? 1 : -1, sway = Mth.sin(Mth.PI * PantherMotion.clamp(t / (len - 1)));
                wr.x -= .1f * s * sway; wl.x -= .1f * s * sway; wr.y -= .06f * sway; wl.y -= .06f * sway;
            }
            default -> {
                // The cape: reaching back to the right for its edge, then dragged high across the face.
                float across = k(t, 1.2f, 4.5f), drop = k(t, len - 5, len);
                Arm back = DA[0].set(.78f, -.55f, -.42f, -55, -60, 0, .95f), held = DB[0].set(-.08f, -.2f, -.62f, -122, 62, -30, .95f);
                wr.lerp(back, held, across);
                wr.y -= .5f * drop;
                wl.set(-.4f, -.62f, -.62f, -95, -20, 15, 1);
            }
        }
        r.toward(wr, w);
        l.toward(wl, w);
    }
    /** How the cape sheet stands in his view now (null when no cape deflect): {x, y, yaw, ripple}. */
    static float[] capeSheet(float now) {
        var mc = Minecraft.getInstance();
        float[] d = mc.player == null ? null : BatmanReflexFx.deflect(mc.player.getId(), now);
        if (d == null || (int) d[0] != BLOCK_CAPE) return null;
        float t = d[1], len = BatmanMotion.deflectLength(BLOCK_CAPE);
        float across = k(t, 1.2f, 4.5f), drop = k(t, len - 5, len);
        return new float[]{Mth.lerp(across, 1.0f, .1f), Mth.lerp(across, -.2f, .02f) - 1.1f * drop, Mth.lerp(across, -65, -6), Math.max(0, 1 - t / 7f)};
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

    // ------------------------------------------------------------------ the wrist cannon (BatmanCannonFx draws the gauntlets)
    private static final Arm[] CANNON_A = {new Arm(), new Arm()}, CANNON_B = {new Arm(), new Arm()}, CANNON_C = {new Arm(), new Arm()};
    /**
     * Both forearms come up low in view, turned in a little, while the gauntlets open (the emitter assemblies sliding out
     * on top; the left follows the right); they rise to the crosshair, slightly converging, as the fire starts and kick
     * a little with each shot of their own hand; after the fire they come down to cool and sink out of sight as the
     * parts close.
     */
    private static void cannon(float t, BatmanClient.State s) {
        float fireEnd = CANNON_DEPLOY + CANNON_FIRE;
        for (int side = 0; side < 2; side++) {
            Arm a = NOW[side];
            float sx = side == 0 ? 1 : -1;
            Arm look = CANNON_A[side].set(.3f * sx, -.6f, -.5f, -72, 14 * sx, -18 * sx, 1);
            Arm aim = CANNON_B[side].set(.25f * sx, -.38f, -.5f, -90, 8 * sx, 0, 1);
            float rec = Math.min(2.5f, BatmanCannonFx.recoil(s, side));
            aim.z += .03f * rec; aim.y += .01f * rec; aim.pitch += 5 * rec;
            float in = ease((t - (side == 0 ? 0 : 1.5f)) / 3f);
            if (t < CANNON_DEPLOY - 3) a.lerp(rest(CANNON_C[side], side, false), look, in);
            else if (t < fireEnd + 2) a.lerp(look, aim, k(t, CANNON_DEPLOY - 3, CANNON_DEPLOY + .5f));
            else {
                Arm cool = CANNON_C[side].set(.32f * sx, -.62f, -.55f, -68, 12 * sx, -10 * sx, .8f);
                a.lerp(aim, cool, k(t, fireEnd + 2, fireEnd + 6));
                a.shown = 1 - k(t, CANNON_TICKS - 8, CANNON_TICKS);
            }
        }
    }

    // ------------------------------------------------------------------ the electric gauntlets (BatmanShockFx draws them)
    private static final Arm[] SHOCK_A = {new Arm(), new Arm()}, SHOCK_B = {new Arm(), new Arm()}, SHOCK_C = {new Arm(), new Arm()};
    /**
     * Each heavy blow in his own view (BatmanShock.S_*): where the striking arm is loaded and where it lands (x, y, z,
     * pitch, yaw, roll); the other fist stays up in the guard. Straights drive into the middle, the uppercut rises, the
     * hooks sweep across from the side, the low hook and the shovel go in under the view, the overhand comes down.
     */
    private static final float[][] SHOCK_LOAD = {
            {.42f, -.72f, -.42f, -60, 16, 10}, {-.4f, -.7f, -.45f, -60, -16, -10}, {.3f, -1.02f, -.6f, -36, 8, 0}, {-.8f, -.58f, -.45f, -86, -70, 80},
            {.9f, -.56f, -.4f, -86, 78, -80}, {-.6f, -.98f, -.5f, -70, -40, 60}, {.38f, -1.0f, -.4f, -50, 10, 0}, {-.45f, -.38f, -.4f, -110, -25, -10}};
    private static final float[][] SHOCK_HIT = {
            {.08f, -.42f, -.9f, -89, 4, 0}, {-.06f, -.42f, -.9f, -89, -4, 0}, {.06f, -.4f, -.76f, -20, 6, 0}, {-.06f, -.5f, -.78f, -90, -62, 85},
            {.06f, -.5f, -.78f, -90, 62, -85}, {-.04f, -.86f, -.82f, -84, -35, 70}, {.06f, -.72f, -.92f, -86, 4, 0}, {-.05f, -.5f, -.86f, -100, -6, 0}};
    /** The boxer's guard in view: both gauntlets up at the lower corners, bouncing a little with his stance. */
    private static Arm shockGuard(Arm a, int side, float time) {
        float sx = side == 0 ? 1 : -1;
        a.set(.36f * sx, -.66f + .012f * Mth.sin(time * .2f), -.55f, -60, 13 * sx, 10 * sx, 1);
        return a;
    }
    private static void shock(int action, float t, BatmanClient.State s, float time) {
        Arm r = NOW[0], l = NOW[1];
        shockGuard(r, 0, time);
        shockGuard(l, 1, time);
        if (action == SHOCK_EQUIP) {
            // Up before him looking down at them as they lock on, drawn apart, slammed together at the clap, then the guard.
            for (int side = 0; side < 2; side++) {
                Arm a = NOW[side];
                float sx = side == 0 ? 1 : -1;
                Arm look = SHOCK_A[side].set(.22f * sx, -.64f, -.5f, -42, 6 * sx, 0, t < 7 ? .55f : 1);
                Arm apart = SHOCK_B[side].set(.5f * sx, -.52f, -.56f, -70, 30 * sx, 20 * sx, 1);
                Arm clap = SHOCK_C[side].set(.07f * sx, -.47f, -.62f, -80, 75 * sx, 80 * sx, 1);
                if (t < 4) a.lerp(rest(A[side], side, false), look, ease(t / 4));
                else if (t < 19) a.copy(look);
                else if (t < 23.2f) a.lerp(look, apart, k(t, 19, 23.2f));
                else if (t < SHOCK_CLAP) a.lerp(apart, clap, PantherMotion.snap(t, 23.2f, SHOCK_CLAP));
                else if (t < SHOCK_CLAP + 1.8f) a.copy(clap);
                else a.lerp(clap, shockGuard(A[side], side, time), k(t, SHOCK_CLAP + 1.8f, SHOCK_EQUIP_TICKS));
                // A small jolt as each part seats; the charge's tremor; pressed together at the clap.
                for (float at : new float[]{4.5f, 6.5f, 8.5f}) { float j = t - at; if (j >= 0 && j < 2) a.wrist += .1f * (float) Math.exp(-j / .5f) * Mth.sin(j * 9); }
                float build = k(t, 11, 24) * (1 - k(t, SHOCK_CLAP - 3, SHOCK_CLAP - 1));
                a.y += .004f * build * Mth.sin(time * 7.1f + side);
            }
        } else if (action == SHOCK_UNEQUIP) {
            // Down before him, the last discharge jerks them, the hands open and sink out of sight.
            for (int side = 0; side < 2; side++) {
                Arm a = NOW[side];
                float sx = side == 0 ? 1 : -1;
                Arm low = SHOCK_A[side].set(.24f * sx, -.68f, -.5f, -40, 6 * sx, 0, .85f);
                if (t < 4) a.lerp(shockGuard(A[side], side, time), low, ease(t / 4));
                else a.copy(low);
                float j = t - BatmanShock.DISCHARGE_AT;
                if (j >= 0 && j < 2.5f) { a.y += .02f * (float) Math.exp(-j / .6f) * Mth.sin(j * 10); a.curl = .3f; }
                a.shown = 1 - k(t, 9, SHOCK_UNEQUIP_TICKS);
            }
        } else if (action == SHOCK_PUNCH) {
            int b = BatmanShock.blow(s.combo), side = BatmanShock.BLOW_SIDE[b];
            float lt = BatmanShockFx.local(t, s.combo), h = BatmanShock.BLOW_HIT[b], len = BatmanShock.BLOW_TICKS[b];
            float[] ld = SHOCK_LOAD[b], ht = SHOCK_HIT[b];
            Arm load = SHOCK_A[side].set(ld[0], ld[1], ld[2], ld[3], ld[4], ld[5], 1), hit = SHOCK_B[side].set(ht[0], ht[1], ht[2], ht[3], ht[4], ht[5], 1);
            Arm a = NOW[side], g = shockGuard(SHOCK_C[side], side, time);
            // PREP (into the load) → ACCELERATION (snapping out) → IMPACT (held for the hit-stop) → RECOVERY (home to the guard).
            if (lt < h * .6f) a.lerp(g, load, ease(lt / (h * .6f)));
            else if (lt < h) a.lerp(load, hit, PantherMotion.snap(lt, h * .6f, h));
            else a.lerp(hit, g, k(lt, h + 1.2f, len));
            if (BatmanShock.landed(s.combo) && lt >= h && lt < h + 1) a.x += .006f * Mth.sin(time * 11);
        }
    }

    // ------------------------------------------------------------------ the sonic trap's remote (BatmanSonicFx draws it)
    private static final Arm[] SONIC_ARM = {new Arm(), new Arm()};
    private static float sonicIn, sonicAt = -1;
    /**
     * While the sonic trap is the gadget picked, the remote is in his left hand, held low in the bottom-left corner of
     * the view (its top and the red button just in sight); in the SONIC move the hand comes up toward the middle, the
     * thumb presses the button (the hand dips with it), then it goes down again. The forearm is turned round (yaw about
     * 180, pitch positive) so the thumb and the remote's top face up and toward him.
     */
    private static void sonic(int action, float t, BatmanClient.State s, boolean gliding, float now) {
        float press = BatmanSonicFx.remote(s, action, t);
        Arm l = NOW[1];
        boolean want = press >= 0 && !gliding && l.hold == BatmanBody.HOLD_NONE;
        float dt = sonicAt < 0 ? 0 : Mth.clamp(now - sonicAt, 0, 3);
        sonicAt = now;
        sonicIn = Mth.clamp(sonicIn + (want ? dt : -dt) / 4f, 0, 1);
        if (!want) {
            // Leaving the remote for another move: that move starts from out of sight, not from a half-turn sweep.
            if (LAST[1].hold == BatmanBody.HOLD_REMOTE) { LAST[1].shown = 0; LAST[1].yaw = l.yaw; LAST[1].pitch = l.pitch; LAST[1].roll = l.roll; LAST[1].hold = BatmanBody.HOLD_NONE; }
            return;
        }
        float up = action == SONIC ? BatmanSonicFx.raise(t) : 0;
        Arm low = SONIC_ARM[0].set(-.44f, -.88f, -.42f, 62, 166, 0, .72f), high = SONIC_ARM[1].set(-.26f, -.6f, -.5f, 56, 158, 0, .72f);
        l.lerp(low, high, up);
        l.curl = .72f + .28f * press;
        l.y -= .012f * press;
        l.wrist = .1f * press;
        l.shown = ease(sonicIn);
        l.hold = BatmanBody.HOLD_REMOTE;
        l.holdArg = press;
        // Coming into the remote from another move: rise from out of sight with the right turn already.
        if (LAST[1].hold != BatmanBody.HOLD_REMOTE && Math.abs(LAST[1].yaw - l.yaw) > 90) { LAST[1].copy(l); LAST[1].shown = 0; }
    }
}
