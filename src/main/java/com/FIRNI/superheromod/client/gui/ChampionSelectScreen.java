package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ChampionLockPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * CHAMPION SELECT (P): the roster as a scrolling grid of splash-art cards (three across, "?" cards
 * waiting for the heroes to come), the chosen hero large on the right (its art behind its real model,
 * which follows the mouse), its skills along the bottom (as many slots as it has skills; point at one
 * to read it) and LOCK IN. The look is a comic page torn out of the multiverse: slanted panels, ink
 * frames, halftone dots and a print slightly off register, everything in the chosen hero's colour.
 */
public final class ChampionSelectScreen extends Screen {
    public static final KeyMapping KEY = new KeyMapping("key.superheromod.champion_select", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.superheromod");
    private static final int INK = 0xFF07060B, PAPER = 0xFFEDE6DD, MUTED = 0xFF8E8796, CYAN = 0xFF2CE0FF, MAGENTA = 0xFFFF2C9C;

    private int selected, skill;
    private float scroll, scrollTarget, time;
    private int lockedAt = -1, ticks;
    private float pick = 1;
    private final ChampionStage stage = new ChampionStage();

    public ChampionSelectScreen() {
        super(Component.literal("Champion Select"));
        var mc = Minecraft.getInstance();
        String current = mc.player == null ? null : ClientHeroRegistry.get(mc.player.getUUID());
        for (int i = 0; i < Champions.ALL.size(); i++) if (Champions.ALL.get(i).id().equals(current)) selected = i;
    }

    @Override public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ layout
    private int pad() { return 10; }
    private int top() { return 34; }
    private int barH() { return 34; }
    private int barY() { return height - pad() - barH(); }
    private int descY() { return barY() - 32; }
    private int panelBottom() { return descY() - 6; }
    private int leftW() { return (int) ((width - pad() * 3) * .56f); }
    private int rightX() { return pad() * 2 + leftW(); }
    private int lockW() { return Math.min(130, width / 5); }
    private int cardW() { return (leftW() - 10 - 2 * 4) / 3; }
    private int cardH() { return Math.max(24, (int) (cardW() * .64f)); }
    private int slots() { return Math.max(Champions.MIN_SLOTS, (Champions.ALL.size() + 3 + 2) / 3 * 3); }
    private int contentH() { return (slots() + 2) / 3 * (cardH() + 4) - 4; }
    private int gridH() { return panelBottom() - top() - 8; }
    private float maxScroll() { return Math.max(0, contentH() - gridH()); }

    @Override public void tick() {
        ticks++;
        stage.tick();
        if (lockedAt >= 0) {
            String hero = Champions.ALL.get(selected).id();
            int n = ticks - lockedAt, show = ChampionStage.length(hero);
            ChampionStage.cues(hero, n);
            // The show first; LOCKED IN only once it has finished.
            if (n == show) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4f, .5f));
            if (n > show + ChampionStage.BANNER) onClose();
        }
    }
    @Override public void removed() { stage.close(); super.removed(); }
    /** Ticks since LOCK IN, or -1. */
    private float shown(float partial) { return lockedAt < 0 ? -1 : ticks - lockedAt + partial; }

    // ------------------------------------------------------------------ drawing
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        time = ticks + partial;
        scroll += (scrollTarget - scroll) * .35f;
        pick = Math.min(1, pick + .12f);
        Champions.Champion hero = Champions.ALL.get(selected);
        int accent = hero.accent();
        background(g, accent);
        title(g, accent);
        grid(g, mouseX, mouseY, accent);
        preview(g, hero, mouseX, mouseY, accent);
        skills(g, hero, mouseX, mouseY, accent);
        lockIn(g, hero, mouseX, mouseY, accent);
        float st = shown(partial);
        if (st >= 0) {
            // The rest of the screen steps back while the hero shows what it does.
            float dim = ease(st / 6f) * .55f;
            g.fill(0, 0, rightX() - 4, height, alpha(0xFF030208, dim));
            g.fill(rightX() - 4, barY() - 36, width, height, alpha(0xFF030208, dim));
            float after = st - ChampionStage.length(hero.id());
            if (after >= 0) {
                // In front of the hero's body and the show's effects (they are drawn deep into the screen).
                g.pose().pushPose();
                g.pose().translate(0, 0, 1000);
                banner(g, hero, after);
                g.pose().popPose();
            }
        }
    }

    private static float ease(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    /**
     * LOCKED IN: a slanted band of ink with the hero's colour along its edges sweeps in across the
     * lower part of the frame, the hero's name stamps down on it (a little too big, then settling),
     * a shine runs across it, and it slides away at the end.
     */
    private void banner(GuiGraphics g, Champions.Champion c, float st) {
        int x0 = rightX() - 6, x1 = width - pad() + 6;
        float cy = panelBottom() - 34;
        float in = 1 - (float) Math.pow(1 - Mth.clamp(st / 6f, 0, 1), 3), out = ease((st - ChampionStage.BANNER + 4) / 6f);
        float shift = (1 - in) * -(x1 - x0) * 1.2f + out * (x1 - x0) * 1.2f;
        float h = 30, slant = 14;
        g.pose().pushPose();
        g.pose().translate(shift, 0, 0);
        quad(g, x0 + slant, cy - h / 2, x1 + slant, cy - h / 2, x1 - slant, cy + h / 2, x0 - slant, cy + h / 2, 0xF0070510);
        quad(g, x0 + slant, cy - h / 2, x1 + slant, cy - h / 2, x1 + slant - 1.2f, cy - h / 2 + 2, x0 + slant - 1.2f, cy - h / 2 + 2, c.accent());
        quad(g, x0 - slant + 1.2f, cy + h / 2 - 2, x1 - slant + 1.2f, cy + h / 2 - 2, x1 - slant, cy + h / 2, x0 - slant, cy + h / 2, c.accent());
        // The shine running across.
        float sweep = (st - 5) / 7f;
        if (sweep > 0 && sweep < 1) {
            float sx = x0 + (x1 - x0) * sweep;
            quad(g, sx + slant, cy - h / 2, sx + slant + 10, cy - h / 2, sx - slant + 10, cy + h / 2, sx - slant, cy + h / 2, 0x50FFFFFF);
        }
        float mid = (x0 + x1) / 2f;
        spaced(g, "KİLİTLENDİ", mid, cy - 11, .7f, 2.2f, c.accent());
        float stamp = 1 + .5f * (1 - ease((st - 3) / 4f));
        float a = Mth.clamp((st - 2) / 2f, 0, 1);
        centred(g, c.name(), mid - 1.2f, cy - 3, 1.5f * stamp, alpha(CYAN, .6f * a), false);
        centred(g, c.name(), mid + 1.2f, cy - 2.4f, 1.5f * stamp, alpha(MAGENTA, .6f * a), false);
        centred(g, c.name(), mid, cy - 3, 1.5f * stamp, alpha(PAPER, a), false);
        g.pose().popPose();
    }
    /** Letters set wide apart, centred. */
    private void spaced(GuiGraphics g, String s, float cx, float y, float scale, float gap, int colour) {
        float w = 0;
        for (char ch : s.toCharArray()) w += font.width(String.valueOf(ch)) * scale + gap;
        float x = cx - (w - gap) / 2;
        for (char ch : s.toCharArray()) { text(g, String.valueOf(ch), x, y, scale, colour, false); x += font.width(String.valueOf(ch)) * scale + gap; }
    }

    /** Night between worlds: a deep gradient, drifting rift lines in the hero's colour, halftone dots. */
    private void background(GuiGraphics g, int accent) {
        g.fillGradient(0, 0, width, height, 0xFF0D0919, 0xFF030208);
        for (int i = 0; i < 7; i++) {
            float y = (i * 53 + time * (.3f + i * .07f)) % (height + 80) - 40;
            quad(g, 0, y, width, y - width * .18f, width, y - width * .18f + 1.5f + i % 3, 0, y + 1.5f + i % 3, alpha(i % 2 == 0 ? accent : CYAN, .08f + .03f * (i % 3)));
        }
        int step = 9;
        for (int y = top(); y < height; y += step)
            for (int x = (y / step % 2) * step / 2; x < width; x += step) {
                float k = (float) y / height;
                int s = k > .55f ? 2 : 1;
                if ((x * 7 + y * 13) % 5 == 0) g.fill(x, y, x + s, y + s, alpha(accent, .05f + .07f * k));
            }
    }

    private void title(GuiGraphics g, int accent) {
        // Printed slightly off register: cyan and magenta echoes behind the white.
        float x = pad() + 2, y = 9;
        text(g, "CHAMPION SELECT", x - 1.2f, y, 1.6f, alpha(CYAN, .8f), false);
        text(g, "CHAMPION SELECT", x + 1.2f, y + .6f, 1.6f, alpha(MAGENTA, .8f), false);
        text(g, "CHAMPION SELECT", x, y, 1.6f, PAPER, false);
        text(g, "Multiverse'ten kahramanını seç", x + 170, y + 5, 1f, MUTED, false);
        quad(g, pad(), top() - 6, pad() + 160, top() - 6, pad() + 156, top() - 4, pad() - 4, top() - 4, accent);
    }

    /** The roster: three splash-art cards a row, then the "?" cards; scrolls with the wheel. */
    private void grid(GuiGraphics g, int mx, int my, int accent) {
        int x0 = pad(), y0 = top(), w = leftW(), h = gridH() + 8;
        panel(g, x0, y0, x0 + w, y0 + h, accent);
        int cw = cardW(), ch = cardH(), gy = y0 + 4;
        g.enableScissor(x0 + 2, y0 + 2, x0 + w - 2, y0 + h - 2);
        for (int i = 0; i < slots(); i++) {
            int col = i % 3, row = i / 3;
            int cx = x0 + 4 + col * (cw + 4), cy = gy + row * (ch + 4) - (int) scroll;
            if (cy + ch < y0 || cy > y0 + h) continue;
            boolean hover = mx >= cx && mx < cx + cw && my >= cy && my < cy + ch && my >= y0 && my < y0 + h;
            if (i < Champions.ALL.size()) card(g, Champions.ALL.get(i), i, cx, cy, cw, ch, hover);
            else unknown(g, cx, cy, cw, ch, hover);
        }
        g.disableScissor();
        // Scrollbar.
        float max = maxScroll();
        if (max > 0) {
            int bx = x0 + w - 6, by = y0 + 4, bh = h - 8;
            g.fill(bx, by, bx + 3, by + bh, 0x40FFFFFF);
            int thumb = Math.max(18, (int) (bh * gridH() / (float) contentH()));
            int ty = by + (int) ((bh - thumb) * scroll / max);
            g.fill(bx, ty, bx + 3, ty + thumb, accent);
        }
    }
    private void card(GuiGraphics g, Champions.Champion c, int index, int x, int y, int w, int h, boolean hover) {
        boolean chosen = index == selected;
        // The art, cropped to the card: a little closer in when pointed at.
        float zoom = hover || chosen ? .9f : 1;
        int uw = (int) (512 * zoom), vh = (int) (uw * h / (float) w);
        int u = (512 - uw) / 2, v = (int) ((512 - vh) * .32f);
        RenderSystem.enableBlend();
        float lit = chosen || hover ? 1 : .72f;
        RenderSystem.setShaderColor(lit, lit, lit, 1);
        g.blit(c.splash(), x, y, w, h, u, v, uw, vh, 512, 512);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // Name on an ink banner.
        g.fillGradient(x, y + h - 14, x + w, y + h, 0x00000000, 0xE0050308);
        text(g, c.name(), x + 4, y + h - 10, .75f, chosen ? c.accent() : PAPER, true);
        String current = Minecraft.getInstance().player == null ? null : ClientHeroRegistry.get(Minecraft.getInstance().player.getUUID());
        if (c.id().equals(current)) {
            int tw = (int) (font.width("SENİN") * .6f) + 6;
            g.fill(x + w - tw - 2, y + 2, x + w - 2, y + 10, alpha(c.accent(), .9f));
            text(g, "SENİN", x + w - tw + 1, y + 3.5f, .6f, INK, false);
        }
        frame(g, x, y, x + w, y + h, chosen ? c.accent() : hover ? 0xFFFFFFFF : 0xFF2A2433, chosen ? 2 : 1);
        if (chosen) corners(g, x - 2, y - 2, x + w + 2, y + h + 2, c.accent());
    }
    private void unknown(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        g.fillGradient(x, y, x + w, y + h, 0xFF16121F, 0xFF09070E);
        // Fine diagonal hatching, like an unprinted panel.
        for (int k = 0; k < w + h; k += 5) line(g, x + Math.max(0, k - h), y + Math.min(h, k), x + Math.min(w, k), y + Math.max(0, k - w), 0x14FFFFFF);
        centred(g, "?", x + w / 2f, y + h / 2f - 9, 2.4f, hover ? 0xFFB9B2C4 : 0xFF4E465C, true);
        centred(g, "YAKINDA", x + w / 2f, y + h - 10, .6f, 0xFF5E566C, false);
        frame(g, x, y, x + w, y + h, 0xFF231E2C, 1);
    }

    /** The chosen hero, large: name, title, its art filling the frame and its real model in front. */
    private void preview(GuiGraphics g, Champions.Champion c, int mx, int my, int accent) {
        int x0 = rightX(), y0 = top(), x1 = width - pad(), y1 = panelBottom();
        panel(g, x0, y0, x1, y1, accent);
        float slide = 1 - (1 - pick) * (1 - pick);
        float nameY = y0 + 6;
        float nx = (x0 + x1) / 2f + (1 - slide) * 30;
        centred(g, c.name(), nx - 1.2f, nameY, 2f, alpha(CYAN, .7f * slide), false);
        centred(g, c.name(), nx + 1.2f, nameY + .6f, 2f, alpha(MAGENTA, .7f * slide), false);
        centred(g, c.name(), nx, nameY, 2f, alpha(PAPER, slide), false);
        centred(g, c.title(), (x0 + x1) / 2f, nameY + 20, .8f, alpha(c.accent(), slide), false);
        int fx0 = x0 + 6, fy0 = y0 + 40, fx1 = x1 - 6, fy1 = y1 - 6;
        int fw = fx1 - fx0, fh = fy1 - fy0;
        if (fw < 20 || fh < 20) return;
        // The art covering the frame.
        int uw, vh;
        if (fw > fh) { uw = 512; vh = (int) (512f * fh / fw); } else { vh = 512; uw = (int) (512f * fw / fh); }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(.55f, .55f, .55f, slide);
        g.blit(c.splash(), fx0, fy0, fw, fh, (512 - uw) / 2f, (512 - vh) * .3f, uw, vh, 512, 512);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        g.fillGradient(fx0, fy0 + fh / 2, fx1, fy1, 0x00000000, 0xC0050308);
        // The hero itself, in its own body: turning after the mouse, or showing what it does after LOCK IN.
        stage.draw(g, c.id(), fx0, fy0, fx1, fy1, mx, my, shown(Minecraft.getInstance().getFrameTime()), Minecraft.getInstance().getFrameTime());
        frame(g, fx0, fy0, fx1, fy1, c.accent(), 1);
        corners(g, fx0 - 2, fy0 - 2, fx1 + 2, fy1 + 2, c.accent());
    }

    /** As many skill slots as the hero has; the one pointed at (or clicked) is read out above them. */
    private void skills(GuiGraphics g, Champions.Champion c, int mx, int my, int accent) {
        List<Champions.Skill> list = c.skills();
        int x0 = pad(), x1 = width - pad() - lockW() - 8, y = barY(), h = barH();
        int n = list.size(), slant = 8;
        float w = (x1 - x0 + slant) / (float) n;
        int hovered = -1;
        for (int i = 0; i < n; i++) {
            float sx = x0 + i * w, ex = sx + w - slant - 2;
            boolean hover = mx >= sx + slant && mx < ex && my >= y && my < y + h;
            if (hover) hovered = i;
            boolean on = hover || (hovered < 0 && i == skill);
            int fill = on ? alpha(c.accent(), .9f) : 0xE0120E1A;
            quad(g, sx + slant, y, ex + slant, y, ex, y + h, sx, y + h, fill);
            quadFrame(g, sx + slant, y, ex + slant, y, ex, y + h, sx, y + h, on ? 0xFFFFFFFF : alpha(c.accent(), .6f));
            Champions.Skill s = list.get(i);
            float keyScale = s.key().length() > 2 ? .9f : 1.5f;
            text(g, s.key(), sx + slant + 4, y + 4, keyScale, on ? INK : c.accent(), false);
            String name = s.name();
            float avail = ex - sx - slant - 6;
            while (name.length() > 3 && font.width(name) * .7f > avail) name = name.substring(0, name.length() - 1);
            if (!name.equals(s.name())) name = name.substring(0, Math.max(1, name.length() - 1)) + "…";
            text(g, name, sx + slant / 2f + 4, y + h - 10, .7f, on ? INK : PAPER, false);
        }
        // The description strip.
        Champions.Skill s = list.get(hovered >= 0 ? hovered : Math.min(skill, n - 1));
        int dy = descY(), dx1 = width - pad();
        g.fill(x0, dy, dx1, dy + 28, 0xD00A0812);
        g.fill(x0, dy, x0 + 3, dy + 28, c.accent());
        text(g, s.key() + "  ·  " + s.name().toUpperCase(), x0 + 8, dy + 3, .85f, c.accent(), false);
        List<FormattedCharSequence> lines = font.split(Component.literal(s.text()), (int) ((dx1 - x0 - 16) / .8f));
        g.pose().pushPose();
        g.pose().translate(x0 + 8, dy + 13, 0);
        g.pose().scale(.8f, .8f, 1);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), 0, i * 9, PAPER, false);
        g.pose().popPose();
    }

    private void lockIn(GuiGraphics g, Champions.Champion c, int mx, int my, int accent) {
        int x1 = width - pad(), x0 = x1 - lockW(), y = barY(), h = barH(), slant = 10;
        boolean hover = mx >= x0 && mx < x1 && my >= y && my < y + h;
        float pulse = .5f + .5f * Mth.sin(time * .15f);
        int top = hover ? 0xFFFFE08A : 0xFFF2B640, bottom = hover ? 0xFFE0402C : 0xFFB8261E;
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        vertex(b, m, x0 + slant, y, top); vertex(b, m, x0, y + h, bottom); vertex(b, m, x1 - slant, y + h, bottom); vertex(b, m, x1, y, top);
        BufferUploader.drawWithShader(b.end());
        quadFrame(g, x0 + slant, y, x1, y, x1 - slant, y + h, x0, y + h, alpha(0xFFFFFFFF, .5f + .5f * pulse));
        centred(g, "LOCK IN", (x0 + x1) / 2f + 1, y + h / 2f - 6 + 1, 1.6f, 0x80000000, false);
        centred(g, "LOCK IN", (x0 + x1) / 2f, y + h / 2f - 6, 1.6f, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------ input
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (lockedAt >= 0) return true;
        // Cards.
        int x0 = pad(), y0 = top(), w = leftW(), h = gridH() + 8;
        if (mx >= x0 && mx < x0 + w && my >= y0 && my < y0 + h) {
            int cw = cardW(), ch = cardH();
            for (int i = 0; i < Champions.ALL.size(); i++) {
                int cx = x0 + 4 + i % 3 * (cw + 4), cy = y0 + 4 + i / 3 * (ch + 4) - (int) scroll;
                if (mx >= cx && mx < cx + cw && my >= cy && my < cy + ch) {
                    if (i != selected) { selected = i; skill = 0; pick = 0; click(1.2f); }
                    return true;
                }
            }
            return true;
        }
        // Skills.
        Champions.Champion c = Champions.ALL.get(selected);
        int bx0 = pad(), bx1 = width - pad() - lockW() - 8;
        if (my >= barY() && my < barY() + barH() && mx >= bx0 && mx < bx1) {
            float sw = (bx1 - bx0 + 8) / (float) c.skills().size();
            skill = Mth.clamp((int) ((mx - bx0) / sw), 0, c.skills().size() - 1);
            click(1.5f);
            return true;
        }
        // LOCK IN.
        if (my >= barY() && my < barY() + barH() && mx >= width - pad() - lockW() && mx < width - pad()) {
            ModNetworking.CHANNEL.sendToServer(new ChampionLockPacket(c.id()));
            lockedAt = ticks;
            ChampionStage.cues(c.id(), 0);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        scrollTarget = Mth.clamp(scrollTarget - (float) delta * (cardH() + 4) * .6f, 0, maxScroll());
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    private void click(float pitch) { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch)); }

    // ------------------------------------------------------------------ drawing helpers
    static int alpha(int colour, float a) { return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (colour & 0xFFFFFF); }
    private void text(GuiGraphics g, String s, float x, float y, float scale, int colour, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, 0, 0, colour, shadow);
        g.pose().popPose();
    }
    private void centred(GuiGraphics g, String s, float x, float y, float scale, int colour, boolean shadow) {
        text(g, s, x - font.width(s) * scale / 2, y, scale, colour, shadow);
    }
    /** A slanted comic panel: ink fill, a coloured spine, halftone in one corner. */
    private void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int accent) {
        g.fill(x0, y0, x1, y1, 0xC80B0814);
        g.fill(x0, y0, x0 + 2, y1, alpha(accent, .8f));
        for (int y = y1 - 30; y < y1 - 2; y += 4)
            for (int x = x1 - 40; x < x1 - 2; x += 4)
                if ((x - (x1 - 40)) + (y - (y1 - 30)) > 30) g.fill(x, y, x + 1, y + 1, alpha(accent, .35f));
        frame(g, x0, y0, x1, y1, 0xFF1E1928, 1);
    }
    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int colour, int t) {
        g.fill(x0, y0, x1, y0 + t, colour); g.fill(x0, y1 - t, x1, y1, colour);
        g.fill(x0, y0, x0 + t, y1, colour); g.fill(x1 - t, y0, x1, y1, colour);
    }
    private static void corners(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        int l = 7;
        g.fill(x0, y0, x0 + l, y0 + 2, colour); g.fill(x0, y0, x0 + 2, y0 + l, colour);
        g.fill(x1 - l, y0, x1, y0 + 2, colour); g.fill(x1 - 2, y0, x1, y0 + l, colour);
        g.fill(x0, y1 - 2, x0 + l, y1, colour); g.fill(x0, y1 - l, x0 + 2, y1, colour);
        g.fill(x1 - l, y1 - 2, x1, y1, colour); g.fill(x1 - 2, y1 - l, x1, y1, colour);
    }
    private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, int c) {
        b.vertex(m, x, y, 0).color((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f).endVertex();
    }
    /** A filled four-cornered shape (clockwise from top-left). */
    private static void quad(GuiGraphics g, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c) {
        if ((c >>> 24) == 0) return;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        vertex(b, m, x0, y0, c); vertex(b, m, x3, y3, c); vertex(b, m, x2, y2, c); vertex(b, m, x1, y1, c);
        BufferUploader.drawWithShader(b.end());
    }
    private static void quadFrame(GuiGraphics g, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c) {
        line(g, x0, y0, x1, y1, c); line(g, x1, y1, x2, y2, c); line(g, x2, y2, x3, y3, c); line(g, x3, y3, x0, y0, c);
    }
    private static void line(GuiGraphics g, float ax, float ay, float bx, float by, int c) {
        float dx = bx - ax, dy = by - ay, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < .01f) return;
        float nx = -dy / len * .5f, ny = dx / len * .5f;
        quad(g, ax + nx, ay + ny, bx + nx, by + ny, bx - nx, by - ny, ax - nx, ay - ny, c);
    }

    // ------------------------------------------------------------------ the key
    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }
    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
    public static final class Opener {
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            var mc = Minecraft.getInstance();
            while (KEY.consumeClick()) if (mc.screen == null && mc.player != null) mc.setScreen(new ChampionSelectScreen());
        }
    }
}
