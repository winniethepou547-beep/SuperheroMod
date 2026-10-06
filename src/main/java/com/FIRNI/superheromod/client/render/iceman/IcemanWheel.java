package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.hud.HudStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/** STUB (to be written): the Ice Armory wheel. */
public final class IcemanWheel {
    private IcemanWheel() {}
    static void draw(GuiGraphics g, Font font, IcemanClient.State s, float cx, float cy, float shown, int hovered, float curX, float curY, float time) {
        float r = 60;
        for (int i = 0; i < WEAPONS; i++) {
            float mid = -90 + i * 120f;
            HudStyle.arc(g, cx, cy, r * .45f, r, mid - 58, mid + 58, HudStyle.alpha(i == hovered ? 0xFF8FD8FF : 0x60FFFFFF, shown));
            double a = Math.toRadians(mid);
            HudStyle.caption(g, font, WEAPON_NAMES[i], (int) (cx + Math.cos(a) * r * .72f), (int) (cy + Math.sin(a) * r * .72f), HudStyle.alpha(0xFFFFFFFF, shown), 0);
        }
        HudStyle.caption(g, font, WEAPON_NAMES[Mth.clamp(hovered >= 0 ? hovered : s.weapon, 0, WEAPONS - 1)], (int) cx, (int) cy, HudStyle.alpha(0xFFD8F2FF, shown), 0);
    }
}
