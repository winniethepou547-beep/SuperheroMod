package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Locale;

import static com.FIRNI.superheromod.client.render.iceman.IceGrowth.h;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The Ice Armory (E held): not a wheel but a little FROZEN SHRINE grown in the air in front of him. A ragged lintel of
 * ice chunks across the top with icicles of every length hanging off it, a column of leaning crystals at each side, a
 * broken shelf of ice along the bottom, and on it three crystal formations side by side (mace, spear, sword from left to
 * right), each weapon frozen inside its own tall crystal (seen through milky ice and fracture planes), small crystals
 * leaning out round its foot, rime on its lower edges. Snow falls through it, cold mist drifts along the shelf.
 * <p>
 * The mouse moves a small ice shard (left: the mace, the middle: the spear, right: the sword). The crystal under it
 * grows a little, clears so the weapon shows, brightens toward cyan on its edges, frost climbs it, mist turns round it
 * and it glints. The weapon in hand has a snowflake at its foot.
 * <p>
 * Opening: everything grows in like ice does, from frost: the shelf spreads from the middle, the crystals push up out
 * of it (each its own delay), the columns rise, the lintel freezes in from both sides toward the middle and the icicles
 * grow down last; every piece starts white (rime) and clears to blue as it finishes. Closing after a pick: the whole
 * thing freezes over white with cracks running out from the chosen crystal, then shatters: every piece flies off away
 * from it and falls, a burst of splinters, a flash of cold, mist. Closing without a pick: it sinks and melts away into
 * mist. Drawn as flat faceted polygons (two-tone facets: a lit face, a shaded face, milky tips), one batch for the ice
 * and one additive batch for the glints, sized to the screen so it reads at every GUI scale.
 */
public final class IcemanWheel {
    private IcemanWheel() {}
    /** The colours: deep and shaded blue for the bodies, lit blue-cyan faces, pale milky tips, white only as frost. */
    static final int DEEP = 0x12304E, SHADE = 0x22507C, ICE = 0x4A90C6, LIT = 0x86C2EA, PALE = 0xC4E3F6, FROST = 0xEEF8FF, EDGE = 0x8FE4FF;
    /** How long it takes to grow in, to melt away, to freeze over and to shatter (ticks). */
    public static final int OPEN_TICKS = 7, MELT_TICKS = 6, FREEZE_TICKS = 3, SHATTER_TICKS = 11;
    private static final String[] INFO = {"Ağır: 3. vuruş ezme, basılı: büyür", "Hızlı: 3. vuruş seri dürtme, basılı: fırlat", "Orta: 3. vuruş dönüş, basılı: girdap"};
    /** The three formations (design units, the shrine's middle at 0, 0): where they stand, how tall and wide their crystal is. */
    private static final float[] FORM_X = {-66, 0, 66}, FORM_H = {54, 68, 58}, FORM_W = {30, 22, 26};
    private static final float BASE_Y = 34, LINTEL_Y = -64;

    /** How long the closing plays (ticks): a pick shatters, a cancel melts. */
    public static int closeLength(boolean picked) { return picked ? FREEZE_TICKS + SHATTER_TICKS : MELT_TICKS; }
    /**
     * Which weapon the cursor (the wheel's cursor, -30..30 each way) is on: left the mace, the middle (or up) the spear,
     * right the sword; while it is still near the middle, whatever it was (-1 at first: letting go picks nothing).
     */
    public static int pick(float x, float y, int was) {
        if (x * x + y * y <= 36) return was;
        return x < -9 ? W_MACE : x > 9 ? W_SWORD : W_SPEAR;
    }

    // ------------------------------------------------------------------ the hover, eased
    private static final float[] HOT = new float[WEAPONS];
    private static float lastTime = -1;
    /** A fresh opening: nothing lit yet. */
    public static void opened() { java.util.Arrays.fill(HOT, 0); lastTime = -1; }

    // ------------------------------------------------------------------ drawing
    /**
     * Draws it. open = ticks since it opened; close = ticks since it closed (negative while open); chosen = the weapon
     * picked when it closed (-1: closed without one); hovered = the formation under the cursor (-1 none); cursor in the
     * wheel's units (-30..30); time = game time with the partial tick.
     */
    static void draw(GuiGraphics g, Font font, IcemanClient.State s, int sw, int sh, float open, float close, int chosen, int hovered,
                     float curX, float curY, float time) {
        boolean closing = close >= 0;
        MELT = closing && chosen < 0 ? Mth.clamp(close / MELT_TICKS, 0, 1) : 0;
        FREEZE = closing && chosen >= 0 ? Mth.clamp(close / FREEZE_TICKS, 0, 1) : 0;
        SHAT = closing && chosen >= 0 ? Mth.clamp((close - FREEZE_TICKS) / SHATTER_TICKS, 0, 1) : 0;
        if (MELT >= 1 || SHAT >= 1) return;
        float dt = lastTime < 0 ? 0 : Mth.clamp(time - lastTime, 0, 4);
        lastTime = time;
        int lit = closing ? chosen : hovered;
        for (int i = 0; i < WEAPONS; i++) {
            float want = i == lit ? 1 : 0;
            HOT[i] += (want - HOT[i]) * (1 - (float) Math.exp(-dt * (want > HOT[i] ? .55f : .3f)));
        }
        U = Math.min(Mth.clamp(sh / 270f, .8f, 1.7f), sw / 290f);
        OX = sw * .5f; OY = sh * .5f - 6 * U;
        TIME = time; OPEN = open;
        ALPHA = Mth.clamp(open / 2.5f, 0, 1) * (1 - MELT);
        WHITE = .55f * FREEZE + .3f * MELT;
        pieceN = 0;
        SRC_X = chosen >= 0 ? FORM_X[chosen] : 0; SRC_Y = chosen >= 0 ? BASE_Y - FORM_H[chosen] * .5f : 0;

        // The world behind goes dim and cold.
        float dim = Mth.clamp(open / 4, 0, 1) * (1 - MELT) * (1 - SHAT);
        g.fill(0, 0, sw, sh, HudStyle.alpha(0x6A02101E, dim));
        g.flush();
        g.pose().pushPose();
        M = g.pose().last().pose();
        begin(false);
        try {
            backMist();
            columns();
            lintel();
            for (int i = 0; i < WEAPONS; i++) formation(i, s.weapon, i == chosen);
            shelf();
            for (int i = 0; i < WEAPONS; i++) aura(i);
            snow();
            if (FREEZE > 0) cracks(chosen);
            if (SHAT > 0) splinters();
            if (MELT > 0) meltMist();
            plaque(font, s, hovered, chosen);
            if (!closing) cursor(curX, curY);
        } finally { end(); }
        begin(true);
        try {
            for (int i = 0; i < WEAPONS; i++) glints(i);
            if (chosen >= 0) {
                float f = FREEZE * (1 - SHAT);
                piece(0, 0, true);
                glow(SRC_X, SRC_Y, 40 + 50 * SHAT, EDGE, .35f * f + .5f * (1 - SHAT) * Mth.clamp(SHAT * 6, 0, 1));
            }
            if (!closing) {
                piece(0, 0, true);
                glint(curX / 30 * 84, curY / 30 * 46 - 6, 4.5f, .55f);
            }
        } finally { end(); }
        text(g, font, s, hovered, chosen);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ the shrine
    /** Cold mist hanging behind it, drifting slowly. */
    private static void backMist() {
        piece(0, 0, true);
        for (int i = 0; i < 7; i++) {
            float x = -110 + 220 * h(i, 1) + 14 * Mth.sin(TIME * .018f + i * 1.7f), y = -40 + 80 * h(i, 2) + 4 * Mth.sin(TIME * .025f + i);
            puff(x, y, 34 + 26 * h(i, 3), 0x9CC6E8, .1f * grown(1 + h(i, 4) * 2, 4), i);
        }
        // A bank of it low along the shelf.
        for (int i = 0; i < 6; i++) {
            float drift = (TIME * (.25f + .2f * h(i, 9)) + h(i, 8) * 260) % 260 - 130;
            puff(drift, BASE_Y + 4 + 4 * h(i, 7), 22 + 10 * h(i, 6), 0xB4D6F0, .12f * grown(2, 4) * edgeFade(drift, 130), 10 + i);
        }
    }
    /** A column of leaning crystals at each side, rising from the shelf to the lintel. */
    private static void columns() {
        for (int side = -1; side <= 1; side += 2) {
            float x = side * 118;
            int sd = side < 0 ? 11 : 23;
            // Leaning inward, the big one first; one leaning out; a small one tight against the big one.
            element(x + side * 3, BASE_Y + 6, .6f, 3.4f);
            shard(x + side * 3, BASE_Y + 6, -side * .07f, 104, 19, G, sd, .92f);
            element(x + side * 10, BASE_Y + 6, 1.2f, 3f);
            shard(x + side * 10, BASE_Y + 6, side * .2f, 62, 13, G, sd + 1, .9f);
            element(x - side * 6, BASE_Y + 6, 1.8f, 2.8f);
            shard(x - side * 6, BASE_Y + 6, -side * .34f, 38, 10, G, sd + 2, .9f);
            rimeAlong(x - side * 6, BASE_Y + 4, x + side * 12, BASE_Y + 2, sd, 5, 1);
        }
    }
    /** The lintel: ragged chunks of ice frozen in from both sides, crystals on top, icicles hanging under it. */
    private static void lintel() {
        int n = 12;
        for (int k = 0; k < n; k++) {
            float u = (k + .5f) / n, x = -132 + 264 * u + (h(k, 31) - .5f) * 6;
            float delay = 1.6f + (1 - Math.abs(u - .5f) * 2) * 2.4f;
            float y = LINTEL_Y + (h(k, 32) - .5f) * 5 + 3 * Mth.sin(u * 5.1f);
            element(x, y, delay, 2.4f);
            chunk(x, y, 28 + 8 * h(k, 33), 13 + 7 * h(k, 34), 100 + k, G, .93f);
            rimeAlong(x - 13, y - 6, x + 13, y - 7, 160 + k, 4, 1);
            // Crystals pushing up off the top now and then.
            if (h(k, 35) < .45f) {
                element(x, y - 5, delay + 1, 2);
                shard(x + (h(k, 36) - .5f) * 14, y - 5, (h(k, 37) - .5f) * .9f, 7 + 9 * h(k, 38), 4.5f, G, 140 + k, .9f);
            }
        }
        // Icicles of every length (shorter over the crystals so they never touch them).
        int m = 22;
        for (int k = 0; k < m; k++) {
            float x = -124 + 248 * (k + h(k, 41)) / m;
            float u = (x + 132) / 264f, delay = 1.6f + (1 - Math.abs(u - .5f) * 2) * 2.4f + 1.2f + 1.4f * h(k, 42);
            float len = 7 + 26 * h(k, 43) * h(k, 43) + 8 * h(k, 44);
            float room = 80;
            for (int i = 0; i < WEAPONS; i++)
                if (Math.abs(x - FORM_X[i]) < FORM_W[i] * .8f) room = Math.min(room, BASE_Y - FORM_H[i] * 1.09f - 5 - (LINTEL_Y + 6));
            len = Math.max(4, Math.min(len, room));
            element(x, LINTEL_Y + 5, delay, 3);
            shard(x, LINTEL_Y + 5, Mth.PI + (h(k, 45) - .5f) * .16f, len, 3 + 3.5f * h(k, 46), G, 200 + k, .9f);
        }
    }
    /** The shelf along the bottom: broken chunks spreading from the middle out, frost on top, small crystals sticking up. */
    private static void shelf() {
        int n = 13;
        for (int k = 0; k < n; k++) {
            float u = (k + .5f) / n, x = -138 + 276 * u + (h(k, 51) - .5f) * 6;
            float y = BASE_Y + 9 + (h(k, 52) - .5f) * 4;
            element(x, y, Math.abs(u - .5f) * 2.2f, 2.4f);
            chunk(x, y, 28 + 8 * h(k, 53), 15 + 6 * h(k, 54), 300 + k, G, .95f);
            rimeAlong(x - 12, y - 7, x + 12, y - 6, 320 + k, 5, 1);
        }
        for (int k = 0; k < 9; k++) {
            float x = -126 + 252 * h(k, 61);
            boolean clear = true;
            for (int i = 0; i < WEAPONS; i++) if (Math.abs(x - FORM_X[i]) < FORM_W[i]) clear = false;
            if (!clear) continue;
            element(x, BASE_Y + 3, 1 + 2 * h(k, 62), 2.4f);
            shard(x, BASE_Y + 3, (h(k, 63) - .5f) * 1.1f, 8 + 12 * h(k, 64), 5 + 3 * h(k, 65), G, 340 + k, .92f);
        }
    }

    /** One weapon's formation: crystals leaning out behind, the tall crystal with the weapon frozen in it, small ones in front, the foot. */
    private static void formation(int i, int inHand, boolean chosen) {
        float bx = FORM_X[i], by = BASE_Y, hot = HOT[i];
        int sd = 1000 + i * 100;
        float d0 = .8f + i * .5f;
        // Behind: two leaning out to the sides.
        element(bx - FORM_W[i] * .4f, by, d0 + .5f, 2.6f);
        shard(bx - FORM_W[i] * .4f, by, -.42f - .2f * h(sd, 1), FORM_H[i] * (.42f + .12f * h(sd, 2)), 11, G, sd + 1, .9f);
        element(bx + FORM_W[i] * .4f, by, d0 + .9f, 2.6f);
        shard(bx + FORM_W[i] * .4f, by, .38f + .25f * h(sd, 3), FORM_H[i] * (.34f + .14f * h(sd, 4)), 10, G, sd + 2, .9f);
        // The crystal and the weapon in it.
        element(bx, by, d0, 3.6f);
        crystal(i, bx, by, FORM_W[i], FORM_H[i], G, hot, sd + 10);
        // In front: small ones leaning out at its foot.
        for (int k = 0; k < 3; k++) {
            float side = k == 1 ? .3f : k == 0 ? -1 : 1;
            float x = bx + side * FORM_W[i] * .32f;
            element(x, by + 4, d0 + 1.4f + .5f * k, 2.2f);
            shard(x, by + 4, side * (.55f + .3f * h(sd, 20 + k)), 9 + 9 * h(sd, 25 + k), 5.5f + 2 * h(sd, 30 + k), G, sd + 40 + k, .95f);
        }
        // The foot: a low lump of ice it all grows out of.
        element(bx, by + 5, d0 - .4f, 2.4f);
        chunk(bx, by + 5, FORM_W[i] * 1.8f, 11, sd + 50, G, .96f);
        rimeAlong(bx - FORM_W[i] * .8f, by + 1, bx + FORM_W[i] * .8f, by, sd + 55, 7, 1);
        // The weapon in hand: a snowflake at its foot.
        if (i == inHand) {
            element(bx, by + 6, d0 + 2, 2);
            snowflake(bx, by + 6, 4.2f, PALE, .9f);
        }
    }
    /**
     * The tall crystal of one formation with its weapon frozen inside: a lit face and a shaded face either side of an
     * off-centre ridge, a stepped broken top of milky facets, the weapon seen through the ice (clearer and brighter when
     * the cursor is on it), fracture planes across it, rime climbing its lower edges.
     */
    private static void crystal(int i, float bx, float by, float w, float hgt, float g, float hot, int seed) {
        if (g <= .01f) return;
        float sc = 1 + .08f * hot;
        float H = hgt * sc * (.12f + .88f * g), W = w * sc * (.4f + .6f * Math.min(1, g * 1.5f));
        float lean = (h(seed, 1) - .5f) * W * .35f;
        float[] p = P;
        // Outline: base left, left shoulder, top-left facet, apex, top-right facet, right shoulder, base right.
        set(p, 0, bx - W * .5f, by);
        set(p, 1, bx - W * (.52f + .06f * h(seed, 2)), by - H * (.62f + .08f * h(seed, 3)));
        set(p, 2, bx - W * (.2f + .05f * h(seed, 4)) + lean * .7f, by - H * (.87f + .04f * h(seed, 5)));
        set(p, 3, bx + lean + W * .06f * (h(seed, 6) - .5f), by - H);
        set(p, 4, bx + W * (.26f + .06f * h(seed, 7)) + lean * .7f, by - H * (.83f + .05f * h(seed, 8)));
        set(p, 5, bx + W * (.5f + .05f * h(seed, 9)), by - H * (.56f + .08f * h(seed, 10)));
        set(p, 6, bx + W * .52f, by);
        float r0x = bx + W * .08f, r0y = by, r1x = bx + W * .05f + lean * .6f, r1y = by - H * .8f;
        float b = 1 + .28f * hot;
        int litTop = mix(LIT, PALE, .15f + .3f * hot), litBot = mix(DEEP, SHADE, .6f);
        float a = .92f;
        // The lit face, the shaded face (darker bottom to lighter top).
        Q[0] = p[0]; Q[1] = p[1]; Q[2] = p[2]; Q[3] = p[3]; Q[4] = p[4]; Q[5] = p[5]; Q[6] = r1x; Q[7] = r1y; Q[8] = r0x; Q[9] = r0y;
        fanGrad(Q, 5, bright(litTop, b), bright(litBot, b), by - H, by, a, seed + 1);
        Q[0] = r0x; Q[1] = r0y; Q[2] = r1x; Q[3] = r1y; Q[4] = p[8]; Q[5] = p[9]; Q[6] = p[10]; Q[7] = p[11]; Q[8] = p[12]; Q[9] = p[13];
        fanGrad(Q, 5, bright(ICE, b), bright(DEEP, b), by - H, by, a, seed + 2);
        // The broken top: milky facets.
        tri(p[4], p[5], bright(PALE, b), a, p[6], p[7], bright(mix(PALE, FROST, .3f), b), a, r1x, r1y, bright(LIT, b), a);
        tri(r1x, r1y, bright(LIT, b), a, p[6], p[7], bright(PALE, b), a, p[8], p[9], bright(mix(ICE, LIT, .5f), b), a);
        // The weapon inside, then the ice over it.
        float clear = .3f + .6f * hot;
        if (g > .55f) {
            float k = Mth.clamp((g - .55f) / .45f, 0, 1);
            icon(i, bx + lean * .25f, by - H * .47f, H * .29f, clear, k);
        }
        // Milky ice over the lower part and two fracture planes across it (fewer when it is clear).
        float milk = .3f * (1 - .7f * hot);
        Q[0] = p[0]; Q[1] = p[1]; Q[2] = bx - W * .52f; Q[3] = by - H * .3f; Q[4] = bx + W * .5f; Q[5] = by - H * .22f; Q[6] = p[12]; Q[7] = p[13];
        fanGrad(Q, 4, PALE, mix(PALE, ICE, .4f), by - H * .3f, by, milk, seed + 3);
        line(bx - W * .48f, by - H * (.38f + .1f * h(seed, 11)), bx + W * .48f, by - H * (.52f + .12f * h(seed, 12)), .5f, PALE, .35f * (1 - .5f * hot));
        line(bx - W * .3f, by - H * (.7f + .06f * h(seed, 13)), bx + W * .44f, by - H * (.62f + .06f * h(seed, 14)), .4f, FROST, .25f * (1 - .5f * hot));
        // The edges: a pale lit edge, the ridge, and cyan along the outline when the cursor is on it.
        for (int k = 0; k < 6; k++) {
            int col = k < 3 ? PALE : LIT;
            line(p[k * 2], p[k * 2 + 1], p[k * 2 + 2], p[k * 2 + 3], .55f, col, (k < 3 ? .5f : .3f) + .2f * hot);
        }
        line(r0x, r0y, r1x, r1y, .5f, LIT, .35f + .2f * hot);
        line(r1x, r1y, p[6], p[7], .45f, FROST, .4f);
        if (hot > .02f) for (int k = 0; k < 6; k++) line(p[k * 2], p[k * 2 + 1], p[k * 2 + 2], p[k * 2 + 3], .9f, EDGE, .45f * hot);
        // Rime climbing it from the foot (higher when the cursor is on it).
        float frost = H * (.16f + .5f * hot + .9f * FREEZE);
        rimeEdge(p[0], p[1], p[2], p[3], by, frost, seed + 20, 7);
        rimeEdge(p[12], p[13], p[10], p[11], by, frost, seed + 30, 6);
        if (hot > .05f || FREEZE > 0) {
            float f = Math.max(hot, FREEZE);
            fern(p[0] + 1, by - frost * .45f, -Mth.HALF_PI + .5f, 7 * f, seed + 40, .7f * f);
            fern(p[12] - 1, by - frost * .6f, -Mth.HALF_PI - .5f, 6 * f, seed + 41, .7f * f);
        }
    }

    // ------------------------------------------------------------------ the weapons, frozen in
    private static final float[][] MACE = {
            {-.09f, -.05f, .09f, -.05f, .1f, .95f, -.1f, .95f},
            {0, .88f, .17f, 1.06f, 0, 1.28f, -.17f, 1.06f},
            null,
            {-.22f, -.55f, -.11f, -.74f, .11f, -.74f, .22f, -.55f, .11f, -.36f, -.11f, -.36f},
            {-.1f, -1.0f, 0, -1.34f, .1f, -1.0f}};
    private static final boolean[] MACE_TONE = {false, true, false, true, true};
    private static final float[][] SPEAR = {
            {-.055f, -.5f, .055f, -.5f, .055f, 1.25f, -.055f, 1.25f},
            {-.17f, -.46f, .17f, -.46f, .12f, -.6f, -.12f, -.6f},
            {0, -1.36f, .21f, -.98f, .14f, -.7f, 0, -.6f, -.14f, -.7f, -.21f, -.98f},
            {0, -1.3f, .045f, -.95f, 0, -.68f, -.045f, -.95f},
            {-.08f, 1.22f, .08f, 1.22f, 0, 1.48f}};
    private static final boolean[] SPEAR_TONE = {false, true, false, true, true};
    private static final float[][] SWORD = {
            {0, -1.32f, .19f, -1.0f, .18f, .3f, -.18f, .3f, -.19f, -1.0f},
            {0, -1.22f, .04f, -.92f, .04f, .26f, -.04f, .26f, -.04f, -.92f},
            {-.52f, .26f, -.3f, .32f, .3f, .32f, .52f, .26f, .42f, .44f, -.42f, .44f},
            {-.07f, .44f, .07f, .44f, .07f, .9f, -.07f, .9f},
            {0, .86f, .15f, 1.02f, 0, 1.18f, -.15f, 1.02f}};
    private static final boolean[] SWORD_TONE = {false, true, true, false, true};
    static {
        // The mace's head: a jagged star of ice (every point its own length).
        float[] star = new float[24];
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + .13;
            float rr = i % 2 == 0 ? .58f + .1f * h(i, 7) : .34f + .05f * h(i, 8);
            star[i * 2] = (float) Math.cos(a) * rr; star[i * 2 + 1] = -.55f + (float) Math.sin(a) * rr;
        }
        MACE[2] = star;
    }
    /** A weapon upright at (x, y) (its middle), size s (half its height), seen through ice: clear 0 (deep inside) .. 1 (plain). */
    private static void icon(int weapon, float x, float y, float s, float clear, float a) {
        float[][] polys = weapon == W_MACE ? MACE : weapon == W_SPEAR ? SPEAR : SWORD;
        boolean[] tone = weapon == W_MACE ? MACE_TONE : weapon == W_SPEAR ? SPEAR_TONE : SWORD_TONE;
        float frozen = (1 - clear) * .6f;
        int body = mix(mix(LIT, PALE, .35f), ICE, frozen), rim = mix(FROST, LIT, frozen * .8f);
        for (int k = 0; k < polys.length; k++) {
            float[] src = polys[k];
            int n = src.length / 2;
            for (int j = 0; j < n; j++) { Q[j * 2] = x + src[j * 2] * s; Q[j * 2 + 1] = y + src[j * 2 + 1] * s; }
            facets(Q, n, tone[k] ? rim : body, a * (.55f + .4f * clear), 700 + weapon * 31 + k * 7);
        }
    }

    // ------------------------------------------------------------------ cold air, snow, the hover
    /** Mist turning round the crystal under the cursor, more at its foot, glints on it. */
    private static void aura(int i) {
        float hot = HOT[i];
        if (hot <= .02f) return;
        float bx = FORM_X[i], cy = BASE_Y - FORM_H[i] * .45f;
        piece(bx, cy, false);
        for (int j = 0; j < 5; j++) {
            float a = TIME * .055f + j * 1.257f;
            puff(bx + Mth.cos(a) * FORM_W[i] * .95f, cy + Mth.sin(a) * FORM_H[i] * .32f, 11 + 4 * h(j, 71), 0xC8E2F6, .13f * hot, 20 + j);
        }
        for (int j = 0; j < 3; j++) {
            float u = ((TIME * .02f + j / 3f) % 1f);
            float side = j % 2 == 0 ? -1 : 1;
            puff(bx + side * (6 + 26 * u), BASE_Y - 2 - 3 * u, 9 + 10 * u, 0xD8EBF8, .16f * hot * (1 - u) * Math.min(1, u * 5), 30 + j);
        }
    }
    /** Snow falling slowly through it, swaying (always in front). */
    private static void snow() {
        piece(0, 0, true);
        float k = grown(1, 4);
        for (int i = 0; i < 46; i++) {
            float speed = .22f + .35f * h(i, 81), span = 150;
            float y = -82 + ((TIME * speed + h(i, 82) * span) % span);
            float x = -135 + 270 * h(i, 83) + (2 + 5 * h(i, 84)) * Mth.sin(TIME * (.04f + .04f * h(i, 85)) + i);
            float s = .6f + .9f * h(i, 86);
            float a = .6f * k * edgeFade(y + 12, 70);
            diamond(x, y, s, i % 3 == 0 ? FROST : PALE, a);
        }
    }
    /** While it freezes over: cracks running out from the chosen crystal across everything. */
    private static void cracks(int chosen) {
        piece(0, 0, true);
        float k = FREEZE * (1 - SHAT);
        for (int j = 0; j < 9; j++) {
            float a = Mth.TWO_PI * j / 9 + (h(j, 91) - .5f) * .5f, reach = (40 + 70 * h(j, 92)) * FREEZE;
            float x = SRC_X, y = SRC_Y;
            for (int q = 0; q < 4; q++) {
                float step = reach / 4, wob = (h(j * 7 + q, 93) - .5f) * .9f;
                float nx = x + Mth.cos(a + wob) * step, ny = y + Mth.sin(a + wob) * step * .7f;
                line(x, y, nx, ny, .55f, FROST, .7f * k);
                if (q == 1 && h(j, 94) < .6f) {
                    float ba = a + (h(j, 95) < .5f ? .9f : -.9f);
                    line(nx, ny, nx + Mth.cos(ba) * step * .7f, ny + Mth.sin(ba) * step * .5f, .4f, FROST, .5f * k);
                }
                x = nx; y = ny;
            }
        }
    }
    /** The shatter's splinters and cold: a burst of small shards from the chosen crystal, falling, mist swelling. */
    private static void splinters() {
        float s = SHAT;
        piece(0, 0, true);
        for (int j = 0; j < 46; j++) {
            float a = h(j, 101) * Mth.TWO_PI, v = 40 + 110 * h(j, 102);
            float x = SRC_X + Mth.cos(a) * v * s, y = SRC_Y + Mth.sin(a) * v * s * .8f + 90 * s * s;
            float ang = a + Mth.HALF_PI + (h(j, 103) - .5f) * 8 * s;
            float len = 3 + 6 * h(j, 104);
            shardFlat(x, y, ang, len, 1.4f + 1.4f * h(j, 105), j % 4 == 0 ? FROST : LIT, 1 - s * s);
        }
        for (int j = 0; j < 6; j++) {
            float a = j * 1.05f + .3f;
            puff(SRC_X + Mth.cos(a) * 40 * s, SRC_Y + Mth.sin(a) * 26 * s, 20 + 30 * s, 0xD0E6F8, .22f * (1 - s), 40 + j);
        }
    }
    /** Melting away: mist rising off it. */
    private static void meltMist() {
        piece(0, 0, true);
        for (int j = 0; j < 8; j++) {
            float x = -110 + 220 * h(j, 111), y = BASE_Y - 10 - 60 * h(j, 112) - 20 * MELT;
            puff(x, y, 20 + 18 * MELT, 0xC8E0F4, .16f * Mth.sin(MELT * Mth.PI), 50 + j);
        }
    }
    /** The plaque under the shelf the name stands on: a slab of dark ice with frosted corners. */
    private static void plaque(Font font, IcemanClient.State s, int hovered, int chosen) {
        int show = shownWeapon(s, hovered, chosen);
        float w = Math.max(HudStyle.captionWidth(font, WEAPON_NAMES[show]), font.width(INFO[show]) * .6f) / U * .5f + 18;
        float y0 = BASE_Y + 20, y1 = BASE_Y + 44;
        element(0, y0, 2, 3);
        piece(0, (y0 + y1) * .5f, false);
        float gw = w * (.3f + .7f * G);
        Q[0] = -gw - 6; Q[1] = y0 + 4; Q[2] = -gw + 4; Q[3] = y0; Q[4] = gw - 2; Q[5] = y0 + 1; Q[6] = gw + 7; Q[7] = y0 + 6;
        Q[8] = gw + 3; Q[9] = y1 - 3; Q[10] = gw - 8; Q[11] = y1; Q[12] = -gw + 6; Q[13] = y1 - 1; Q[14] = -gw - 4; Q[15] = y1 - 5;
        fanGrad(Q, 8, SHADE, DEEP, y0, y1, .78f, 777);
        for (int k = 0; k < 8; k++) {
            int j = (k + 1) % 8;
            line(Q[k * 2], Q[k * 2 + 1], Q[j * 2], Q[j * 2 + 1], .5f, k < 3 ? PALE : LIT, k < 3 ? .5f : .25f);
        }
        rimeAlong(-gw - 4, y0 + 3, -gw + 10, y0, 790, 4, 1);
        rimeAlong(gw - 8, y0 + 1, gw + 6, y0 + 5, 795, 4, 1);
    }
    private static int shownWeapon(IcemanClient.State s, int hovered, int chosen) {
        return Mth.clamp(chosen >= 0 ? chosen : hovered >= 0 ? hovered : s.weapon, 0, WEAPONS - 1);
    }
    /** The cursor: a small shard of ice. */
    private static void cursor(float cx, float cy) {
        piece(0, 0, true);
        float x = cx / 30 * 84, y = cy / 30 * 46 - 6;
        puff(x, y, 7, 0xD8EEFF, .25f, 60);
        Q[0] = x; Q[1] = y - 4.5f; Q[2] = x + 2.4f; Q[3] = y - .4f; Q[4] = x; Q[5] = y + 3.6f; Q[6] = x - 2.1f; Q[7] = y;
        facets(Q, 4, PALE, .95f, 801);
        line(Q[0], Q[1], Q[2], Q[3], .4f, FROST, .9f);
    }
    /** The glints on a formation's facets (more when the cursor is on it), its cyan edge light. */
    private static void glints(int i) {
        float g = grown(.8f + i * .5f + 2, 2), hot = HOT[i];
        if (g <= .02f || SHAT > 0) return;
        float bx = FORM_X[i], H = FORM_H[i] * (1 + .08f * hot), W = FORM_W[i];
        piece(bx, BASE_Y - H * .5f, false);
        for (int k = 0; k < 3; k++) {
            int sd = 1200 + i * 10 + k;
            float tw = Math.max(0, Mth.sin(TIME * (.09f + .05f * h(sd, 1)) + h(sd, 2) * 20));
            tw = tw * tw * tw * tw;
            float x = bx + (h(sd, 3) - .5f) * W * .8f, y = BASE_Y - H * (.3f + .65f * h(sd, 4));
            glint(x, y, 3 + 3 * hot, g * (.25f + .6f * hot) * tw);
        }
        if (hot > .02f) glow(bx, BASE_Y - H * .5f, W * 1.4f, EDGE, .12f * hot * g);
    }

    // ------------------------------------------------------------------ text
    private static void text(GuiGraphics g, Font font, IcemanClient.State s, int hovered, int chosen) {
        float k = Mth.clamp((OPEN - 2) / 3, 0, 1) * (1 - MELT) * (1 - Mth.clamp(SHAT * 3, 0, 1));
        if (k < .05f) return;
        int show = shownWeapon(s, hovered, chosen);
        int x = Math.round(OX), y = Math.round(OY + (BASE_Y + 24) * U);
        HudStyle.caption(g, font, WEAPON_NAMES[show], x, y, HudStyle.alpha(hovered >= 0 || chosen >= 0 ? 0xFFBFE6FF : 0xFFE8F6FF, k), 0);
        g.pose().pushPose();
        g.pose().translate(OX, y + 11, 0);
        g.pose().scale(.6f, .6f, 1);
        String info = INFO[show];
        g.drawString(font, info, -font.width(info) / 2, 0, HudStyle.alpha(0xFFB2CADA, k), false);
        g.pose().popPose();
        HudStyle.caption(g, font, "Sol tık / E bırak: seç", x, Math.round(OY + (BASE_Y + 54) * U), HudStyle.alpha(0xFF8FAEC2, k * .9f), 0);
        int cd = s.cooldowns[CD_WEAPON];
        if (cd > 0 && s.weapon >= 0 && s.weapon < WEAPONS) {
            int ix = Math.round(OX + FORM_X[s.weapon] * U), iy = Math.round(OY + (BASE_Y - FORM_H[s.weapon] - 12) * U);
            HudStyle.caption(g, font, String.format(Locale.ROOT, "%.0f", Math.ceil(cd / 20f)), ix, iy, HudStyle.alpha(0xFFFF9A8A, k), 0);
        }
    }

    // ------------------------------------------------------------------ growth and pieces
    private static float U, OX, OY, TIME, OPEN, ALPHA, WHITE, MELT, FREEZE, SHAT, SRC_X, SRC_Y;
    /** The element being drawn: its growth (0..1) and its rime whitening while it grows. */
    private static float G = 1, EW;
    /** Growth of something with this delay and duration on the opening's clock (0..1, eased). */
    private static float grown(float delay, float dur) { return IceGrowth.grow(OPEN, delay, dur); }
    /** Starts an element anchored at (x, y): its growth G, its whitening (rime first, clearing as it grows), its piece. */
    private static void element(float x, float y, float delay, float dur) {
        G = grown(delay, dur);
        piece(x, y, false);
        EW = (1 - G) * .8f;
    }
    private static float pcx, pcy, pdx, pdy, pc = 1, ps, pa = 1;
    private static int pieceN;
    /**
     * The piece being drawn (centre x, y): while it shatters every piece flies off away from the chosen crystal with
     * its own speed, spin and fall; while it melts it sinks. fixed = never moves (mist, snow, the cursor).
     */
    private static void piece(float x, float y, boolean fixed) {
        pcx = x; pcy = y; pdx = pdy = 0; pc = 1; ps = 0; pa = 1; EW = 0;
        if (fixed) return;
        int seed = ++pieceN;
        if (SHAT > 0) {
            float dx = x - SRC_X, dy = y - SRC_Y, l = Mth.sqrt(dx * dx + dy * dy) + 1;
            float s = Mth.clamp(SHAT * (1.15f + .3f * h(seed, 5)) - .1f * l / 140, 0, 1);
            float vx = dx / l + (h(seed, 1) - .5f) * .7f, vy = dy / l * .7f - .45f + (h(seed, 2) - .5f) * .6f;
            float v = 50 + 90 * h(seed, 3) - l * .12f;
            pdx = vx * v * s; pdy = vy * v * s + 120 * s * s;
            float rot = (h(seed, 4) - .5f) * 6 * s;
            pc = Mth.cos(rot); ps = Mth.sin(rot);
            pa = 1 - s * s;
        } else if (MELT > 0) {
            pdy = MELT * MELT * (6 + 8 * h(seed, 6));
            float sx = 1 - .25f * MELT;
            pc = sx;
        }
    }

    // ------------------------------------------------------------------ shapes
    private static final float[] P = new float[16], Q = new float[32];
    private static void set(float[] p, int i, float x, float y) { p[i * 2] = x; p[i * 2 + 1] = y; }
    /**
     * A crystal standing out of (bx, by) toward angle (radians, 0 up, positive leaning right; PI hangs down), length len,
     * width w, grown g: a lit left face and a shaded right face either side of its ridge, uneven shoulders, a point off
     * its axis with a milky facet, a pale edge on the lit side.
     */
    private static void shard(float bx, float by, float ang, float len, float w, float g, int seed, float a) {
        if (g <= .01f) return;
        float L = len * (.15f + .85f * g), W = w * (.45f + .55f * Math.min(1, g * 1.6f));
        float ux = Mth.sin(ang), uy = -Mth.cos(ang), vx = Mth.cos(ang), vy = Mth.sin(ang);
        float s1 = .6f + .14f * h(seed, 1), s2 = .52f + .16f * h(seed, 2), t = (h(seed, 3) - .5f) * .3f * W;
        float hw = W * .5f;
        float b0x = bx - vx * hw, b0y = by - vy * hw;
        float b1x = bx + vx * hw * (.85f + .25f * h(seed, 4)), b1y = by + vy * hw * (.85f + .25f * h(seed, 4));
        float q0x = bx + ux * L * s1 - vx * hw * (.9f + .2f * h(seed, 5)), q0y = by + uy * L * s1 - vy * hw * (.9f + .2f * h(seed, 5));
        float q1x = bx + ux * L * s2 + vx * hw * (.9f + .2f * h(seed, 6)), q1y = by + uy * L * s2 + vy * hw * (.9f + .2f * h(seed, 6));
        float tx = bx + ux * L + vx * t, ty = by + uy * L + vy * t;
        float m0x = bx + vx * W * .08f, m0y = by + vy * W * .08f;
        float sm = Math.min(s1, s2);
        float m1x = bx + ux * L * sm + vx * (t * .6f + W * .05f), m1y = by + uy * L * sm + vy * (t * .6f + W * .05f);
        int litC = mix(ICE, LIT, .55f + .3f * h(seed, 7)), shadeC = mix(SHADE, ICE, .3f * h(seed, 8)), foot = DEEP;
        // The lit face, the shaded face (darker at the foot), the point's facets.
        tri(b0x, b0y, mix(foot, litC, .45f), a, q0x, q0y, litC, a, m1x, m1y, litC, a);
        tri(b0x, b0y, mix(foot, litC, .45f), a, m1x, m1y, litC, a, m0x, m0y, mix(foot, litC, .4f), a);
        tri(m0x, m0y, mix(foot, shadeC, .4f), a, m1x, m1y, shadeC, a, q1x, q1y, shadeC, a);
        tri(m0x, m0y, mix(foot, shadeC, .4f), a, q1x, q1y, shadeC, a, b1x, b1y, mix(foot, shadeC, .5f), a);
        tri(q0x, q0y, mix(LIT, PALE, .5f), a, tx, ty, PALE, a, m1x, m1y, LIT, a);
        tri(m1x, m1y, ICE, a, tx, ty, mix(LIT, PALE, .3f), a, q1x, q1y, ICE, a);
        line(q0x, q0y, tx, ty, .4f, FROST, .35f * a);
        line(b0x, b0y, q0x, q0y, .4f, PALE, .3f * a);
    }
    /** A loose splinter of ice (the shatter): a thin flat diamond along angle. */
    private static void shardFlat(float x, float y, float ang, float len, float w, int col, float a) {
        float ux = Mth.cos(ang), uy = Mth.sin(ang), vx = -uy, vy = ux;
        float ax = x + ux * len * .6f, ay = y + uy * len * .6f, bx = x - ux * len * .4f, by = y - uy * len * .4f;
        tri(ax, ay, col, a, x + vx * w * .5f, y + vy * w * .5f, mix(col, PALE, .3f), a, bx, by, col, a);
        tri(ax, ay, mix(col, DEEP, .35f), a, bx, by, mix(col, DEEP, .3f), a, x - vx * w * .5f, y - vy * w * .5f, mix(col, DEEP, .4f), a);
    }
    /** A ragged chunk of ice (lintel, shelf, foot): a lumpy polygon, a flattish top lit, darker below, a pale top edge. */
    private static void chunk(float cx, float cy, float w, float hgt, int seed, float g, float a) {
        if (g <= .01f) return;
        float W = w * (.3f + .7f * g), H = hgt * (.35f + .65f * g);
        int n = 7;
        for (int i = 0; i < n; i++) {
            float an = Mth.PI + Mth.TWO_PI * (i + (h(seed, i) - .5f) * .5f) / n;
            float x = Mth.cos(an) * W * .5f * (.88f + .26f * h(seed, 10 + i)), y = Mth.sin(an) * H * .5f * (.8f + .35f * h(seed, 20 + i));
            if (y < 0) y *= .75f;
            Q[i * 2] = cx + x; Q[i * 2 + 1] = cy + y;
        }
        fanGrad(Q, n, mix(LIT, PALE, .2f * h(seed, 30)), mix(DEEP, SHADE, .5f), cy - H * .5f, cy + H * .5f, a, seed);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            if (Q[i * 2 + 1] < cy && Q[j * 2 + 1] < cy) line(Q[i * 2], Q[i * 2 + 1], Q[j * 2], Q[j * 2 + 1], .5f, PALE, .45f * a);
        }
    }
    /** Rime along a line: small white specks (count of them), some a little bigger. */
    private static void rimeAlong(float x0, float y0, float x1, float y1, int seed, int count, float a) {
        for (int i = 0; i < count; i++) {
            float u = (i + h(seed, i)) / count;
            float s = .5f + .9f * h(seed, 20 + i) * h(seed, 20 + i);
            diamond(Mth.lerp(u, x0, x1) + (h(seed, 40 + i) - .5f) * 2, Mth.lerp(u, y0, y1) + (h(seed, 60 + i) - .5f) * 1.6f, s, FROST, .8f * a * G);
        }
    }
    /** Rime on a crystal's edge from its foot up to height frost (above base y). */
    private static void rimeEdge(float x0, float y0, float x1, float y1, float base, float frost, int seed, int count) {
        for (int i = 0; i < count; i++) {
            float u = h(seed, i) * .9f;
            float x = Mth.lerp(u, x0, x1), y = Mth.lerp(u, y0, y1);
            float above = base - y;
            if (above > frost) continue;
            float k = 1 - above / Math.max(1, frost);
            diamond(x + (h(seed, 30 + i) - .5f) * 1.5f, y, .5f + 1.1f * k * h(seed, 50 + i), FROST, .85f * Math.min(1, k * 2.5f));
        }
    }
    /** A fern of frost (a stem with branches at 60 degrees, as ice crystals grow on glass). */
    private static void fern(float x, float y, float ang, float len, int seed, float a) {
        if (len < .5f || a <= .02f) return;
        float ex = x + Mth.cos(ang) * len, ey = y + Mth.sin(ang) * len;
        line(x, y, ex, ey, .3f, FROST, a);
        for (int k = 1; k <= 3; k++) {
            float u = k / 4f, bx = Mth.lerp(u, x, ex), by = Mth.lerp(u, y, ey), bl = len * .4f * (1 - u * .6f) * (.7f + .5f * h(seed, k));
            for (int s = -1; s <= 1; s += 2) {
                float ba = ang + s * Mth.PI / 3;
                line(bx, by, bx + Mth.cos(ba) * bl, by + Mth.sin(ba) * bl, .25f, FROST, a * .8f);
            }
        }
    }
    /** A six-armed snowflake. */
    private static void snowflake(float x, float y, float r, int col, float a) {
        for (int i = 0; i < 6; i++) {
            float an = Mth.PI * i / 3 + Mth.PI / 6, c = Mth.cos(an), s = Mth.sin(an);
            line(x, y, x + c * r, y + s * r, .5f, col, a * G);
            float bx = x + c * r * .55f, by = y + s * r * .55f;
            for (int q = -1; q <= 1; q += 2) {
                float ba = an + q * .8f;
                line(bx, by, bx + Mth.cos(ba) * r * .3f, by + Mth.sin(ba) * r * .3f, .35f, col, a * G * .8f);
            }
        }
    }
    /** A small flat diamond (a snowflake, a speck of rime). */
    private static void diamond(float x, float y, float s, int col, float a) {
        if (a <= .01f) return;
        tri(x, y - s, col, a, x + s * .8f, y, col, a, x, y + s, col, a);
        tri(x, y - s, col, a, x, y + s, col, a, x - s * .8f, y, col, a);
    }
    /** A soft round puff (mist), brightest in the middle, its rim a little uneven. */
    private static void puff(float x, float y, float r, int col, float a, int seed) {
        if (a <= .005f) return;
        int n = 12;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float r0 = r * (.85f + .3f * h(seed, i)), r1 = r * (.85f + .3f * h(seed, (i + 1) % n));
            tri(x, y, col, a, x + Mth.cos(a0) * r0, y + Mth.sin(a0) * r0 * .8f, col, 0, x + Mth.cos(a1) * r1, y + Mth.sin(a1) * r1 * .8f, col, 0);
        }
    }
    /** A polygon filled from its middle, coloured from top to bottom (y0 the top colour's height, y1 the bottom's), each triangle its own shade. */
    private static void fanGrad(float[] p, int n, int top, int bottom, float y0, float y1, float a, int seed) {
        float mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += p[i * 2]; my += p[i * 2 + 1]; }
        mx /= n; my /= n;
        float span = Math.max(1e-3f, y1 - y0);
        int cm = mix(top, bottom, Mth.clamp((my - y0) / span, 0, 1));
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float f = .9f + .2f * h(seed, i);
            int ci = bright(mix(top, bottom, Mth.clamp((p[i * 2 + 1] - y0) / span, 0, 1)), f);
            int cj = bright(mix(top, bottom, Mth.clamp((p[j * 2 + 1] - y0) / span, 0, 1)), f);
            tri(mx, my, bright(cm, f), a, p[i * 2], p[i * 2 + 1], ci, a, p[j * 2], p[j * 2 + 1], cj, a);
        }
    }
    /** A polygon as faceted ice: a fan from its middle, every facet its own shade (a cut look). */
    private static void facets(float[] p, int n, int col, float a, int seed) {
        float mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += p[i * 2]; my += p[i * 2 + 1]; }
        mx /= n; my /= n;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float f = .74f + .4f * h(seed, i);
            int c = bright(col, f);
            tri(mx, my, bright(col, f + .08f), a, p[i * 2], p[i * 2 + 1], c, a, p[j * 2], p[j * 2 + 1], c, a);
        }
    }
    /** A thin line (a quad) from a to b, width w. */
    private static void line(float x0, float y0, float x1, float y1, float w, int col, float a) {
        if (a <= .01f) return;
        float dx = x1 - x0, dy = y1 - y0, l = Mth.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f) return;
        float nx = -dy / l * w * .5f, ny = dx / l * w * .5f;
        tri(x0 - nx, y0 - ny, col, a, x1 - nx, y1 - ny, col, a, x1 + nx, y1 + ny, col, a);
        tri(x0 - nx, y0 - ny, col, a, x1 + nx, y1 + ny, col, a, x0 + nx, y0 + ny, col, a);
    }
    /** (additive) A four-pointed glint. */
    private static void glint(float x, float y, float s, float a) {
        if (a <= .01f) return;
        for (int k = 0; k < 2; k++) {
            float ux = k == 0 ? s : 0, uy = k == 0 ? 0 : s, wx = k == 0 ? 0 : s * .14f, wy = k == 0 ? s * .14f : 0;
            tri(x, y, FROST, a, x + ux, y + uy, FROST, 0, x + wx, y + wy, FROST, a * .6f);
            tri(x, y, FROST, a, x + wx, y + wy, FROST, a * .6f, x - ux, y - uy, FROST, 0);
            tri(x, y, FROST, a, x - ux, y - uy, FROST, 0, x - wx, y - wy, FROST, a * .6f);
            tri(x, y, FROST, a, x - wx, y - wy, FROST, a * .6f, x + ux, y + uy, FROST, 0);
        }
    }
    /** (additive) A soft glow. */
    private static void glow(float x, float y, float r, int col, float a) { puff(x, y, r, col, a, 99); }

    // ------------------------------------------------------------------ colour
    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }
    private static int bright(int c, float f) {
        int r = Math.min(255, (int) ((c >> 16 & 255) * f)), g = Math.min(255, (int) ((c >> 8 & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
        return r << 16 | g << 8 | b;
    }
    private static float edgeFade(float v, float half) { return Mth.clamp((half - Math.abs(v)) / (half * .25f), 0, 1); }

    // ------------------------------------------------------------------ the batch
    private static BufferBuilder B;
    private static Matrix4f M;
    private static void begin(boolean additive) {
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        B = Tesselator.getInstance().getBuilder();
        B.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }
    private static void end() {
        if (B != null) BufferUploader.drawWithShader(B.end());
        B = null;
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
    private static void tri(float ax, float ay, int ca, float aa, float bx, float by, int cb, float ab, float cx, float cy, int cc, float ac) {
        v(ax, ay, ca, aa); v(bx, by, cb, ab); v(cx, cy, cc, ac);
    }
    /** One corner: the piece's flight, the design units onto the screen, the whitening (rime, freezing over, melting). */
    private static void v(float x, float y, int col, float a) {
        float rx = x - pcx, ry = y - pcy;
        float px = pcx + rx * pc - ry * ps + pdx, py = pcy + rx * ps + ry * pc + pdy;
        float w = Math.min(1, WHITE + EW);
        float r = (col >> 16 & 255) / 255f, g = (col >> 8 & 255) / 255f, b = (col & 255) / 255f;
        r += (.9f - r) * w; g += (.96f - g) * w; b += (1 - b) * w;
        B.vertex(M, OX + px * U, OY + py * U, 0).color(r, g, b, Mth.clamp(a * ALPHA * pa, 0, 1)).endVertex();
    }
}
