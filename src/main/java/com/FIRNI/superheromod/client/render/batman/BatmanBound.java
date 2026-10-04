package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import com.FIRNI.superheromod.network.packet.BatmanInputPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Bound by Batman's grapnel line (BatmanBind on the server), as the clients see it: the black line wound round the body
 * in tight coils (ankles, knees, hips, the arms pinned at the chest), the claw clamped on the chest, the coils creaking
 * and slipping a little with every tug. The bound player's own client holds their movement, turns each left click into
 * a tug at the line (and never an attack) and shows the struggle under the crosshair: a mouse lighting up on every
 * click and a ring filling as the line loosens.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanBound {
    private static final class Bind { float until, progress, start, tug; int batman; }
    private static final Map<Integer, Bind> BOUND = new HashMap<>();
    private static final int GOLD = 0xFFE8C547;
    private static float shown, freedAt = -1000;
    private static final float[] CLICKS = new float[6];
    private static int nextClick;

    private BatmanBound() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }
    private static int me() { var p = Minecraft.getInstance().player; return p == null ? -1 : p.getId(); }

    static void receive(BatmanFxPacket p) {
        float t = now();
        if (p.power() <= 0) {
            if (BOUND.remove(p.entity()) != null && p.entity() == me()) freedAt = t;
            return;
        }
        Bind b = BOUND.get(p.entity());
        if (b == null) { b = new Bind(); b.start = t; BOUND.put(p.entity(), b); if (p.entity() == me()) shown = 0; }
        if (p.dir().x > b.progress + 1e-3) b.tug = t;
        b.until = t + p.power() + 25;
        b.progress = (float) p.dir().x;
        b.batman = p.id();
    }
    /** Is this body bound right now? */
    public static boolean bound(Entity e) { Bind b = e == null ? null : BOUND.get(e.getId()); return b != null && b.until > now(); }
    /** The local player is bound: left click tugs at the line and never attacks. */
    public static boolean bound() { return bound(Minecraft.getInstance().player); }

    // ------------------------------------------------------------------ the bound player's own client
    @SubscribeEvent public static void keys(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { BOUND.clear(); return; }
        if (!bound() || mc.screen != null) return;
        int presses = 0;
        while (mc.options.keyAttack.consumeClick()) presses++;
        if (presses <= 0) return;
        float t = now();
        for (int i = 0; i < Math.min(3, presses); i++) {
            ModNetworking.CHANNEL.sendToServer(new BatmanInputPacket(IN_BREAK_FREE, 0, 0));
            CLICKS[nextClick] = t + i * .3f; nextClick = (nextClick + 1) % CLICKS.length;
        }
        ClientScreenShake.add(.05f);
        Bind b = BOUND.get(me());
        if (b != null) b.tug = t;
    }
    /** Held on the spot: no walking, no jumping, no sneaking out of it. */
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void hold(MovementInputUpdateEvent e) {
        if (e.getEntity() != Minecraft.getInstance().player || !bound()) return;
        var in = e.getInput();
        in.forwardImpulse = 0; in.leftImpulse = 0; in.jumping = false; in.shiftKeyDown = false;
        in.up = in.down = in.left = in.right = false;
        e.getEntity().setSprinting(false);
    }
    @SubscribeEvent public static void clicks(InputEvent.InteractionKeyMappingTriggered e) {
        if (bound() && (e.isAttack() || e.isUseItem())) { e.setCanceled(true); e.setSwingHand(false); }
    }

    // ------------------------------------------------------------------ the coils, for everyone
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || BOUND.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = now(), partial = e.getPartialTick();
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        var fx = FilmFx.batched();
        FilmContext c = new FilmContext(p, fx, cam, new Vec3(rr.x, rr.y, rr.z), new Vec3(uu.x, uu.y, uu.z), t, 0, partial);
        try {
            for (var en : BOUND.entrySet()) {
                Bind b = en.getValue();
                if (b.until < t) continue;
                Entity body = mc.level.getEntity(en.getKey());
                if (body == null || body == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
                coils(c, body, b, t, partial);
            }
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /**
     * The line wound round the body: four bands of tight coils (ankles, knees, hips, chest with the arms pinned) joined
     * by a diagonal run, wound on over the first few ticks from the feet up; every tug shakes them and they slacken as
     * the struggle goes on. The claw sits on the chest, two bright prongs.
     */
    private static void coils(FilmContext c, Entity body, Bind b, float t, float partial) {
        Vec3 base = body.getPosition(partial);
        double h = Math.max(.6, body.getBbHeight()), r0 = Math.max(.22, body.getBbWidth() * .5 + .06);
        float yaw = Mth.rotLerp(partial, body.yRotO, body.getYRot()) * Mth.DEG_TO_RAD;
        float wound = Mth.clamp((t - b.start) / 6f, 0, 1);
        float tug = Math.max(0, 1 - (t - b.tug) / 5f), loose = .1f * b.progress;
        double shakeX = Math.sin(t * 2.3) * .03 * tug, shakeZ = Math.cos(t * 1.9) * .03 * tug;
        double[] bands = {.12, .32, .52, .72};
        int turns = 3, seg = 14;
        int rope = 0x111214, rim = 0x3a3f46;
        Vec3 last = null;
        for (int k = 0; k < bands.length; k++) {
            if (wound * bands.length < k) break;
            double y0 = h * bands[k];
            // The chest band goes round the arms too (wider).
            double rad = r0 * (k == 3 ? 1.32 : k == 2 ? 1.12 : 1) * (1 + loose);
            for (int i = 0; i < turns * seg; i++) {
                double u0 = i / (double) seg, u1 = (i + 1) / (double) seg;
                Vec3 a = coil(base, yaw, rad, y0, u0, h, shakeX, shakeZ, k), z = coil(base, yaw, rad, y0, u1, h, shakeX, shakeZ, k);
                FilmFx.streak(c, a, z, .028, rope, .97f, .97f, false);
                if (i % 3 == 0) FilmFx.streak(c, a, z, .012, rim, .5f, .5f, false);
                last = z;
            }
            // The run up to the next band.
            if (k + 1 < bands.length && wound * bands.length >= k + 1 && last != null) {
                Vec3 next = coil(base, yaw, r0 * (k + 1 == 3 ? 1.32 : 1.12) * (1 + loose), h * bands[k + 1], 0, h, shakeX, shakeZ, k + 1);
                FilmFx.streak(c, last, next, .026, rope, .97f, .97f, false);
            }
        }
        // The claw on the chest.
        if (wound >= 1) {
            float s = Mth.sin(yaw), co = Mth.cos(yaw);
            Vec3 fwd = new Vec3(-s, 0, co), side = new Vec3(co, 0, s);
            Vec3 claw = base.add(fwd.scale(r0 * 1.36)).add(0, h * .74, 0).add(shakeX, 0, shakeZ);
            FilmFx.streak(c, claw.add(side.scale(-.07)), claw.add(side.scale(.07)), .05, 0x2a2d31, .97f, .97f, false);
            FilmFx.streak(c, claw.add(side.scale(.07)), claw.add(side.scale(.12)).add(0, -.08, 0), .02, 0xc9ced4, .9f, .9f, false);
            FilmFx.streak(c, claw.add(side.scale(-.07)), claw.add(side.scale(-.12)).add(0, -.08, 0), .02, 0xc9ced4, .9f, .9f, false);
            FilmFx.glow(c, claw, .12, 0xffe2a0, .25f + .2f * Mth.sin(t * .4f));
        }
    }
    /** A point on a band's coils: round the body (facing yaw), climbing a little per turn. */
    private static Vec3 coil(Vec3 base, float yaw, double rad, double y0, double u, double h, double sx, double sz, int band) {
        double a = u * Math.PI * 2 + yaw + band * .9;
        double y = y0 + u * h * .035;
        return base.add(Math.cos(a) * rad + sx, y, Math.sin(a) * rad + sz);
    }

    // ------------------------------------------------------------------ the struggle, under the crosshair
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui || !e.getOverlay().id().getPath().equals("hotbar")) return;
        GuiGraphics g = e.getGuiGraphics();
        int cx = e.getWindow().getGuiScaledWidth() / 2, cy = e.getWindow().getGuiScaledHeight() / 2 + 46;
        float time = now();
        Bind b = BOUND.get(me());
        if (b == null || b.until < time) {
            float since = time - freedAt;
            if (since < 40) HudStyle.caption(g, mc.font, "İpten kurtuldun", cx, cy, HudStyle.alpha(0xFFFFFFFF, 1 - Mth.clamp((since - 22) / 18, 0, 1)), 0);
            return;
        }
        shown += (b.progress - shown) * .3f;
        float last = -100;
        for (float k : CLICKS) last = Math.max(last, k);
        float since = time - last;
        boolean lit = since < 2.5f;
        float nudge = since < 2 ? 1 - since / 2 : 0;
        float r = 19;
        HudStyle.arc(g, cx, cy, r - 1, r + 4, 0, 360, 0x66000000);
        HudStyle.arc(g, cx, cy, r, r + 3, 0, 360, 0x30FFFFFF);
        if (shown > .005f) {
            float end = -90 + 360 * Mth.clamp(shown, 0, 1);
            HudStyle.arc(g, cx, cy, r - 1.5f, r + 4.5f, -90, end, HudStyle.alpha(GOLD, .25f));
            HudStyle.arc(g, cx, cy, r, r + 3, -90, end, GOLD);
            HudStyle.arc(g, cx, cy, r - 1, r + 4, end - 8, end, 0xFFFFFFFF);
        }
        for (float k : CLICKS) {
            float q = (time - k) / 8;
            if (q < 0 || q > 1) continue;
            HudStyle.arc(g, cx, cy, r + 3 + 9 * q, r + 4.5f + 9 * q, 0, 360, HudStyle.alpha(0xFFFFFFFF, .6f * (1 - q)));
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy + nudge * 1.2f, 0);
        float scale = 1.5f - .08f * nudge;
        g.pose().scale(scale, scale, 1);
        int x0 = -6, y0 = -9, x1 = 6, y1 = 9;
        rounded(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFFE9E6F0);
        rounded(g, x0, y0, x1, y1, 0xFF22252A);
        float pulse = .35f + .35f * (float) Math.sin(time * .6f);
        int left = lit ? 0xFFFFFFFF : HudStyle.alpha(GOLD, pulse);
        g.fill(x0 + 1, y0 + 1, -1, y0 + 7, left);
        g.fill(x0 + 2, y0, -1, y0 + 1, left);
        g.fill(0, y0 + 1, x1 - 1, y0 + 7, 0xFF383C44);
        g.fill(-1, y0, 0, y0 + 8, 0xFFE9E6F0);
        g.fill(x0, y0 + 7, x1, y0 + 8, 0xFFE9E6F0);
        g.fill(-1, y0 + 2, 0, y0 + 5, 0xFF8E8F9C);
        g.pose().popPose();
        HudStyle.caption(g, mc.font, "Batman'in ipiyle bağlandın", cx, cy - 36, HudStyle.alpha(GOLD, .95f), 0);
        HudStyle.caption(g, mc.font, String.format(Locale.ROOT, "Sol tık spamla! (%d sn)", (int) Math.ceil(Math.max(0, b.until - 25 - time) / 20f)), cx, cy + 28, lit ? 0xFFFFFFFF : HudStyle.TEXT, 0);
    }
    private static void rounded(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0 + 2, y0, x1 - 2, y1, color);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, color);
        g.fill(x0, y0 + 2, x1, y1 - 2, color);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { BOUND.clear(); shown = 0; freedAt = -1000; }
}
