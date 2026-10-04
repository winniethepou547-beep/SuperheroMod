package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.heroes.batman.BatmanConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What Batman's own eyes get, and what his flash does to the others':
 * - THERMAL (after the sensor's pulse): every living thing round it glows orange through walls (the game's outline,
 *   switched on by the mixins through outline()/outlineColor()), the view takes a cold blue cast, and arrows at the edge
 *   of the screen point to the ones out of view.
 * - SMOKE: inside or near his own smoke he sees the ones in it outlined in red; for everyone else the smoke is a wall
 *   (the server blinds players inside it; here it greys their screen too).
 * - FLASH: a white-out that slowly gives the view back.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanVision {
    private static Vec3 thermalAt;
    private static float thermalRange, thermalUntil = -1, thermalStart;
    private static float flashStart = -100, flashLife = 1, flashPower;
    private static final List<Entity> MARKED = new ArrayList<>();
    static final int ORANGE = 0xFF8A2B, RED = 0xFF3B30;

    private BatmanVision() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }

    static void thermal(Vec3 at, float range, float ticks) {
        thermalAt = at; thermalRange = range; thermalStart = now(); thermalUntil = thermalStart + ticks;
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, .5f, 1.9f);
    }
    static void flashed(float power) {
        flashStart = now(); flashPower = Mth.clamp(power, .2f, 1); flashLife = (float) (BatmanConfig.FLASH_SECONDS.get() * 20 * flashPower);
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(), .35f, 2f);
    }
    static void clear() { thermalUntil = -1; flashStart = -100; MARKED.clear(); }
    public static boolean thermalOn() { return now() < thermalUntil; }

    /** Should this entity be drawn with an outline (through walls) on this client? */
    public static boolean outline(Entity e) { return outlineColor(e) >= 0; }
    /** The outline colour for it (RGB), or -1 for none. */
    public static int outlineColor(Entity e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || e == mc.player || !e.level().isClientSide || !(e instanceof LivingEntity l) || !l.isAlive() || !BatmanClient.isHero(mc.player)) return -1;
        float t = now();
        if (t < thermalUntil && thermalAt != null && e.position().distanceTo(thermalAt) < thermalRange) return ORANGE;
        // In his own smoke: the ones inside it are outlined for him.
        BatmanFx.Cloud c = BatmanFx.cloudAt(e.getBoundingBox().getCenter());
        if (c != null && c.owner() == mc.player.getId() && mc.player.position().distanceTo(c.at()) < c.radius() + 16) return RED;
        return -1;
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        float t = now();
        // Smoke round the camera (anyone's but his own): grey, thick.
        BatmanFx.Cloud c = BatmanFx.cloudAt(mc.gameRenderer.getMainCamera().getPosition());
        if (c != null && c.owner() != mc.player.getId()) {
            float age = t - c.start(), k = Math.min(1, age / 10f) * (1 - Mth.clamp((age - c.life()) / 30f, 0, 1));
            g.fill(0, 0, w, h, HudStyle.alpha(0xFF6E7378, .78f * k));
        }
        if (BatmanClient.isHero(mc.player) && t < thermalUntil) thermalHud(g, mc, w, h, t);
        // The flash: blinding white, giving the view back slowly (fast at first, the last of it lingering).
        float since = t - flashStart;
        if (since >= 0 && since < flashLife) {
            float k = 1 - since / flashLife;
            float a = since < 4 ? 1 : (float) Math.pow(k, .6);
            g.fill(0, 0, w, h, HudStyle.alpha(0xFFFFFFFF, a * (.6f + .4f * flashPower)));
        }
    }
    private static void thermalHud(GuiGraphics g, Minecraft mc, int w, int h, float t) {
        float in = Mth.clamp((t - thermalStart) / 6f, 0, 1), out = Mth.clamp((thermalUntil - t) / 15f, 0, 1), k = Math.min(in, out);
        // A cold blue cast with darker edges (Arkham's detective view).
        g.fill(0, 0, w, h, HudStyle.alpha(0xFF0A3550, .28f * k));
        int edge = Math.max(w, h) / 9;
        for (int i = 0; i < 6; i++) {
            int d = edge * i / 6, a = (int) (40 * k * (1 - i / 6f));
            int col = (a << 24) | 0x02121C;
            g.fill(0, d, w, d + edge / 6, col); g.fill(0, h - d - edge / 6, w, h - d, col);
            g.fill(d, 0, d + edge / 6, h, col); g.fill(w - d - edge / 6, 0, w - d, h, col);
        }
        // The scan line sweeping down once at the start.
        float sweep = (t - thermalStart) / 14f;
        if (sweep < 1) g.fill(0, (int) (sweep * h), w, (int) (sweep * h) + 2, HudStyle.alpha(0xFFFF8A2B, .7f * (1 - sweep)));
        HudStyle.caption(g, mc.font, String.format(Locale.ROOT, "TERMAL %.0f", Math.max(0, thermalUntil - t) / 20f), w / 2, 12, HudStyle.alpha(0xFFFF8A2B, k), 0);
        // Arrows at the edge for the ones out of view.
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        var look = camera.getLookVector(); var upV = camera.getUpVector(); var leftV = camera.getLeftVector();
        Vec3 fwd = new Vec3(look.x(), look.y(), look.z()), up = new Vec3(upV.x(), upV.y(), upV.z()), left = new Vec3(leftV.x(), leftV.y(), leftV.z());
        MARKED.clear();
        for (Entity en : mc.level.entitiesForRendering()) if (outlineColor(en) == ORANGE) MARKED.add(en);
        float cx = w / 2f, cy = h / 2f, rx = w / 2f - 22, ry = h / 2f - 22;
        int count = 0;
        for (Entity en : MARKED) {
            Vec3 d = en.getBoundingBox().getCenter().subtract(cam);
            double dist = d.length();
            Vec3 n = d.scale(1 / Math.max(1e-3, dist));
            double f = n.dot(fwd), x = -n.dot(left), y = -n.dot(up);
            if (f > .82) continue;
            double ang = Math.atan2(y, x);
            float ax = cx + (float) Math.cos(ang) * rx, ay = cy + (float) Math.sin(ang) * ry;
            arrow(g, ax, ay, (float) ang, HudStyle.alpha(0xFFFF8A2B, k * (.7f + .3f * Mth.sin(t * .4f + count))));
            g.pose().pushPose();
            g.pose().translate(ax - (float) Math.cos(ang) * 11, ay - (float) Math.sin(ang) * 11, 0);
            g.pose().scale(.6f, .6f, 1);
            String m = String.format(Locale.ROOT, "%.0fm", dist);
            g.drawString(mc.font, m, -mc.font.width(m) / 2, -3, HudStyle.alpha(0xFFFFD2B0, k), false);
            g.pose().popPose();
            if (++count > 24) break;
        }
    }
    /** A chevron pointing along angle a at (x, y). */
    private static void arrow(GuiGraphics g, float x, float y, float a, int col) {
        float c = Mth.cos(a), s = Mth.sin(a);
        float[] pts = {7 * c, 7 * s, -5 * c - 5 * s, -5 * s + 5 * c, -2 * c, -2 * s, -5 * c + 5 * s, -5 * s - 5 * c};
        BatmanWheel.fan(g, x, y, new float[]{pts[0], pts[1], pts[2], pts[3], pts[4], pts[5], pts[6], pts[7]}, 1, col);
    }
}
