package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Batman's thermal / detective vision (Arkham Knight reference), on his own client only.
 *
 * Switching on (~0.6 s): the lens vignette comes in at the edges and the colours start to fade, a short forensic glitch
 * (bands slipping, colour split, noise, a pulse of light), then the cold thermal world rises; faint technical marks show
 * in the corners. While on: the world is a dark, cold, low-contrast image; every living thing in range is drawn into a
 * heat layer with a body-heat gradient (chest hottest, then head, limbs and hands cooler; the undead cold, blazes white
 * hot), bright where in sight and dimmer through walls; a thin scan wave runs out along the ground from him now and then
 * and bodies flare as it passes; the one in the crosshair is a little brighter with scan marks round it; a weapon that
 * has just fired is hot and cools down; fire, lava and torches read hot off the image itself. Switching off (~0.4 s):
 * a pulse, the colours come back, the vignette and the corners go.
 *
 * It comes on by itself while his own smoke is out (to see the ones lost in it): every living thing in range, players
 * and mobs alike, glows through the smoke and through walls. Cost: nothing at all while off; while on, one extra pass of
 * the bodies in range (at most 40) into a heat buffer and one full-screen post pass.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanThermal {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation EFFECT = new ResourceLocation(SuperheroMod.MODID, "shaders/post/batman_thermal.json");
    /** Seen through walls within this range. */
    private static final double RANGE = 40;
    private static final int MAX_BODIES = 40, ON_TICKS = 12, OFF_TICKS = 8, PULSE_EVERY = 70;
    private static final double PULSE_SPEED = 1.35, PULSE_REACH = 32;

    private static boolean on;
    private static float switchAt = -100, fromAmount;
    private static float autoUntil = -1;
    private static float lastPulse = -1000;
    private static float lastYaw, lastPitch, motion;
    private static final Map<Integer, Float> SHOTS = new HashMap<>();

    private static PostChain chain;
    private static List<PostPass> passes = List.of();
    private static boolean failed;
    private static int chainW, chainH;
    private static MultiBufferSource.BufferSource heatSource;
    private static final Matrix4f VIEW = new Matrix4f(), PROJ = new Matrix4f();
    private static boolean matricesValid;

    private BatmanThermal() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }

    // ------------------------------------------------------------------ switching
    /** His own smoke is out until then: the vision comes on by itself to see the ones in it. */
    static void smoke(float until) { autoUntil = Math.max(autoUntil, until); }

    private static boolean wanted() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !BatmanClient.isHero(mc.player) || FilmDirector.playing()) return false;
        float t = now();
        return t < autoUntil;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { on = false; autoUntil = -1; return; }
        boolean want = wanted();
        if (want != on) {
            fromAmount = amount();
            on = want; switchAt = now();
            mc.player.playSound(on ? net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT : net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, .35f, on ? 1.9f : 2f);
            mc.player.playSound(com.FIRNI.superheromod.core.sound.ModSounds.BATMAN_MINE.get(), .25f, on ? 1.4f : 1.1f);
            if (on) lastPulse = now() - PULSE_EVERY + 10;
        }
        float t = mc.level.getGameTime();
        SHOTS.values().removeIf(s -> t - s > 60);
        // How fast the view turns (a faint smear while it does).
        float dy = Math.abs(Mth.wrapDegrees(mc.player.getYRot() - lastYaw)) + Math.abs(mc.player.getXRot() - lastPitch);
        motion = Mth.lerp(.3f, motion, Mth.clamp(dy / 25f, 0, 1));
        lastYaw = mc.player.getYRot(); lastPitch = mc.player.getXRot();
        if (on && amount() > .9f && now() - lastPulse > PULSE_EVERY) lastPulse = now();
    }

    /** 0..1: how far the thermal world has replaced the normal one. */
    public static float amount() {
        float e = now() - switchAt;
        if (on) return fromAmount + (1 - fromAmount) * ease((e - ON_TICKS * .25f) / (ON_TICKS * .75f));
        return fromAmount * (1 - ease(e / OFF_TICKS));
    }
    private static float edge() {
        float e = now() - switchAt;
        return on ? ease(e / (ON_TICKS * .25f)) : (1 - ease(e / OFF_TICKS)) * Math.min(1, fromAmount * 2);
    }
    private static float distort() {
        float e = now() - switchAt;
        if (on) return window(e, ON_TICKS * .22f, ON_TICKS * .55f, 1.5f);
        return .5f * (1 - Mth.clamp(e / (OFF_TICKS * .6f), 0, 1)) * Math.min(1, fromAmount * 2);
    }
    private static float pulse() {
        float e = now() - switchAt;
        if (on) return .45f * Math.max(0, 1 - Math.abs(e - ON_TICKS * .3f) / 1.6f);
        return .35f * Math.max(0, 1 - e / 2.5f) * fromAmount;
    }
    private static float ease(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    private static float window(float t, float a, float b, float soft) { return Math.min(ease((t - a) / soft), ease((b - t) / soft)); }

    @SubscribeEvent public static void shot(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() || !(e.getEntity() instanceof AbstractArrow arrow)) return;
        Entity owner = arrow.getOwner();
        if (owner != null) SHOTS.put(owner.getId(), (float) e.getLevel().getGameTime());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        on = false; autoUntil = -1;
        if (chain != null) { chain.close(); chain = null; }
    }

    // ------------------------------------------------------------------ the picture
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc = Minecraft.getInstance();
        float amount = amount();
        matricesValid = false;
        if (amount <= .002f || mc.level == null || mc.player == null || failed) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (!ensureChain(mc, main)) return;
        VIEW.set(e.getPoseStack().last().pose());
        PROJ.set(e.getProjectionMatrix());
        matricesValid = true;
        float partial = e.getPartialTick(), time = now();
        Vec3 cam = e.getCamera().getPosition();
        RenderTarget heat = chain.getTempTarget("heat");
        heatTarget = heat;
        heat.setClearColor(0, 0, 0, 0);
        heat.clear(Minecraft.ON_OSX);
        heat.copyDepthFrom(main);
        heat.bindWrite(false);

        // Everything in the heat layer is drawn world-aligned round the camera; the view's turn goes in the model-view.
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.last().pose().set(VIEW);
        RenderSystem.applyModelViewMatrix();
        try {
            drawHeat(mc, cam, partial, time);
        } catch (Exception ex) {
            LOG.warn("Batman thermal heat pass failed", ex);
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            heatTarget = null;
        }
        main.bindWrite(false);
        uniform("Amount", amount); uniform("Edge", edge()); uniform("Distort", distort()); uniform("Pulse", pulse());
        uniform("Time", (time % 2000) / 20f); uniform("Motion", motion);
        chain.process(partial);
        main.bindWrite(false);
    }

    private static boolean ensureChain(Minecraft mc, RenderTarget main) {
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, EFFECT);
                chainW = chainH = -1;
                passes = findPasses(chain);
            }
            if (chainW != main.width || chainH != main.height) { chain.resize(main.width, main.height); chainW = main.width; chainH = main.height; }
            return true;
        } catch (Exception ex) {
            failed = true;
            LOG.warn("Batman thermal vision could not load its post effect; the vision stays off", ex);
            return false;
        }
    }
    /** The chain's passes (found by type, whatever the field is called in this mapping). */
    @SuppressWarnings("unchecked")
    private static List<PostPass> findPasses(PostChain c) {
        for (Field f : PostChain.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                List<?> list = (List<?>) f.get(c);
                if (list != null && !list.isEmpty() && list.get(0) instanceof PostPass) return (List<PostPass>) list;
            } catch (Exception ignored) {}
        }
        return List.of();
    }
    private static void uniform(String name, float v) {
        for (PostPass p : passes) p.getEffect().safeGetUniform(name).set(v);
    }

    /** The bodies, the scan wave and the hot weapons into the heat layer. */
    private static void drawHeat(Minecraft mc, Vec3 cam, float partial, float time) {
        if (heatSource == null) heatSource = MultiBufferSource.immediate(new BufferBuilder(1 << 18));
        var player = mc.player;
        Vec3 me = player.getPosition(partial);
        double range = RANGE;
        float pulseR = (float) ((time - lastPulse) * PULSE_SPEED), pulseFade = 1 - Mth.clamp(pulseR / (float) PULSE_REACH, 0, 1);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        Entity aimed = aimed(mc, range);
        List<LivingEntity> bodies = new ArrayList<>();
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof LivingEntity l) || !l.isAlive()) continue;
            if (en == player && firstPerson) continue;
            if (en.distanceToSqr(me) > range * range) continue;
            bodies.add(l);
        }
        bodies.sort(Comparator.comparingDouble(b -> b.distanceToSqr(me)));
        if (bodies.size() > MAX_BODIES) bodies = bodies.subList(0, MAX_BODIES);
        var dispatcher = mc.getEntityRenderDispatcher();
        PoseStack q = new PoseStack();
        // Through walls first (dim), then what is in sight (bright, depth-tested against the world).
        for (int pass = 0; pass < 2; pass++) {
            HEAT.type = pass == 0 ? HeatTypes.through() : HeatTypes.visible();
            for (LivingEntity b : bodies) {
                double dist = Math.sqrt(b.distanceToSqr(me));
                float flare = pulseFade * Math.max(0, 1 - Math.abs((float) dist - pulseR) / 2.5f);
                float base = baseHeat(b) * (1 + .55f * flare) * (b == aimed ? 1.22f : 1);
                HEAT.begin(b, cam, partial, base * (pass == 0 ? .62f : 1));
                Vec3 at = b.getPosition(partial);
                float yaw = Mth.lerp(partial, b.yRotO, b.getYRot());
                try { dispatcher.render(b, at.x - cam.x, at.y - cam.y, at.z - cam.z, yaw, partial, q, HEAT, 15728880); }
                catch (Exception ignored) {}
            }
            heatSource.endBatch();
        }
        // The scan wave: a thin band running out along the ground from him.
        VertexConsumer v = heatSource.getBuffer(HeatTypes.visible());
        if (pulseFade > 0 && pulseR > .5f) {
            int n = 72;
            double y0 = me.y - cam.y - .05, y1 = y0 + .45;
            float a = .38f * pulseFade;
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                double x0 = me.x - cam.x + Math.cos(a0) * pulseR, z0 = me.z - cam.z + Math.sin(a0) * pulseR;
                double x1 = me.x - cam.x + Math.cos(a1) * pulseR, z1 = me.z - cam.z + Math.sin(a1) * pulseR;
                v.vertex(x0, y0, z0).color(a, 0, 0, 1).endVertex(); v.vertex(x1, y0, z1).color(a, 0, 0, 1).endVertex();
                v.vertex(x1, y1, z1).color(0, 0, 0, 1).endVertex(); v.vertex(x0, y1, z0).color(0, 0, 0, 1).endVertex();
            }
        }
        // Weapons: a held bow is warm metal; one that has just fired is hot at the hand, cooling over a few seconds.
        for (LivingEntity b : bodies) {
            boolean armed = b.getMainHandItem().getItem() instanceof BowItem || b.getMainHandItem().getItem() instanceof CrossbowItem;
            Float shot = SHOTS.get(b.getId());
            float hot = shot == null ? 0 : Math.max(0, 1 - (time - shot) / 50f);
            if (!armed && hot <= 0) continue;
            Vec3 hand = b.getPosition(partial).add(0, b.getBbHeight() * .62, 0).add(b.getViewVector(partial).scale(.45));
            blob(v, hand.subtract(cam), .18f + .25f * hot, .35f + .9f * hot);
        }
        heatSource.endBatch();
    }
    /** A small hot square facing nowhere in particular (seen from any side as a spot): three crossed quads. */
    private static void blob(VertexConsumer v, Vec3 c, float r, float h) {
        float[][] axes = {{1, 0, 0, 0, 1, 0}, {0, 0, 1, 0, 1, 0}, {1, 0, 0, 0, 0, 1}};
        for (float[] a : axes) {
            double ux = a[0] * r, uy = a[1] * r, uz = a[2] * r, wx = a[3] * r, wy = a[4] * r, wz = a[5] * r;
            v.vertex(c.x - ux - wx, c.y - uy - wy, c.z - uz - wz).color(h, 0, 0, 1).endVertex();
            v.vertex(c.x + ux - wx, c.y + uy - wy, c.z + uz - wz).color(h, 0, 0, 1).endVertex();
            v.vertex(c.x + ux + wx, c.y + uy + wy, c.z + uz + wz).color(h, 0, 0, 1).endVertex();
            v.vertex(c.x - ux + wx, c.y - uy + wy, c.z - uz + wz).color(h, 0, 0, 1).endVertex();
        }
    }
    /** How warm a body is: the living warm, the undead cold, the fiery white hot. */
    private static float baseHeat(LivingEntity b) {
        if (b.fireImmune()) return 1.1f;
        // The undead run cold, but still read as bodies (red, not lost in the dark).
        if (b.getMobType() == MobType.UNDEAD) return .6f;
        if (b.isOnFire()) return 1.15f;
        return .86f;
    }
    private static Entity aimed(Minecraft mc, double range) {
        var p = mc.player;
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        Entity best = null; double bestD = 1e9;
        for (Entity en : mc.level.getEntities(p, new AABB(eye, end).inflate(1.5), x -> x instanceof LivingEntity && x.isAlive())) {
            var hit = en.getBoundingBox().inflate(.4).clip(eye, end);
            if (hit.isPresent()) { double d = hit.get().distanceToSqr(eye); if (d < bestD) { bestD = d; best = en; } }
        }
        return best;
    }

    // ------------------------------------------------------------------ heat render types and the vertex rewriter
    /** The chain's heat target, while the heat pass draws (the render types bind it themselves). */
    private static RenderTarget heatTarget;
    private static final class HeatTypes extends RenderType {
        private HeatTypes() { super("unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {}); }
        /** Draws into the heat target (never the screen), whatever was bound before. */
        private static final OutputStateShard HEAT_TARGET = new OutputStateShard("batman_heat_target",
                () -> { if (heatTarget != null) heatTarget.bindWrite(false); },
                () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false));
        private static RenderType visible, through;
        static RenderType visible() {
            if (visible == null) visible = create("batman_heat", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 18, false, false,
                    CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(ADDITIVE_TRANSPARENCY).setOutputState(HEAT_TARGET)
                            .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false));
            return visible;
        }
        static RenderType through() {
            if (through == null) through = create("batman_heat_through", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 18, false, false,
                    CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(ADDITIVE_TRANSPARENCY).setOutputState(HEAT_TARGET)
                            .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(NO_DEPTH_TEST).createCompositeState(false));
            return through;
        }
    }
    private static final HeatBuffers HEAT = new HeatBuffers();
    /**
     * Stands in for the game's buffers while a body is drawn into the heat layer: every model part it draws comes here
     * and leaves as heat (its colour, texture and light thrown away), shaded by where on the body the vertex is: the
     * chest and the head hottest, legs cooler, the arms and hands (away from the body's axis) cooler still. Name tags,
     * shadows, leads and other non-body layers are dropped.
     */
    private static final class HeatBuffers implements MultiBufferSource, VertexConsumer {
        RenderType type;
        private VertexConsumer out;
        private double x, y, z, y0 = 0, y1 = 1, cx, cz, half = .3;
        private float base;
        private final IdentityHashMap<RenderType, Boolean> skip = new IdentityHashMap<>();

        void begin(LivingEntity b, Vec3 cam, float partial, float heat) {
            AABB box = b.getBoundingBox();
            Vec3 at = b.getPosition(partial);
            y0 = at.y - cam.y; y1 = y0 + Math.max(.3, box.getYsize()); cx = at.x - cam.x; cz = at.z - cam.z;
            half = Math.max(.2, box.getXsize() * .5); base = heat;
        }
        @Override public VertexConsumer getBuffer(RenderType requested) {
            Boolean drop = skip.get(requested);
            if (drop == null) {
                // Only the render type's own name counts ("RenderType[name:state]"): its state lists the texture
                // ("texture[...]"), which once made every textured body look like text and vanish from the heat layer.
                String n = typeName(requested);
                drop = n.startsWith("text") || n.contains("shadow") || n.contains("leash") || n.equals("lines") || n.equals("line_strip")
                        || n.contains("lightning") || n.contains("glint") || n.startsWith("debug") || n.contains("beacon") || n.contains("portal")
                        || n.startsWith("crumbling") || n.startsWith("water_mask");
                skip.put(requested, drop);
            }
            if (drop) return NOOP;
            out = heatSource.getBuffer(type);
            return this;
        }
        private static String typeName(RenderType t) {
            String s = t.toString();
            int a = s.indexOf('['), b = s.indexOf(':', a + 1);
            return a >= 0 && b > a ? s.substring(a + 1, b) : s;
        }
        @Override public VertexConsumer vertex(double x, double y, double z) { this.x = x; this.y = y; this.z = z; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {
            float hf = (float) Mth.clamp((y - y0) / (y1 - y0), 0, 1);
            double dx = x - cx, dz = z - cz;
            float r = (float) (Math.sqrt(dx * dx + dz * dz) / half);
            float core = hf > .8f ? .86f : hf > .46f ? 1f : .5f + .32f * hf / .46f;
            float limb = Mth.clamp(1 - (r - .6f) * .55f, .62f, 1f);
            float h = Mth.clamp(base * core * limb, 0, 1);
            out.vertex(x, y, z).color(h, 0, 0, 1).endVertex();
        }
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }
    private static final VertexConsumer NOOP = new VertexConsumer() {
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    };

    // ------------------------------------------------------------------ the lens marks
    /**
     * Thin, low-key marks in the four corners while the vision is on (not boxes: hairlines, ticks, a crawling scan
     * pattern, a few small symbols), and small scan brackets round the one in the crosshair.
     */
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        float a = amount();
        var mc = Minecraft.getInstance();
        if (a <= .01f || mc.player == null || mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        float t = now(), k = a * Math.min(1, edge());
        int line = HudStyle.alpha(0xFFBFE8FF, .32f * k), warm = HudStyle.alpha(0xFFFFA45A, .38f * k), faint = HudStyle.alpha(0xFF9FC7DD, .16f * k);
        int m = 10, len = 34;
        // Corner brackets.
        for (int cx = 0; cx < 2; cx++) for (int cy = 0; cy < 2; cy++) {
            int x = cx == 0 ? m : w - m, y = cy == 0 ? m : h - m, sx = cx == 0 ? 1 : -1, sy = cy == 0 ? 1 : -1;
            hline(g, x, y, sx * len, line); vline(g, x, y, sy * len, line);
            hline(g, x + sx * 4, y + sy * 4, sx * 10, faint);
            for (int i = 0; i < 5; i++) hline(g, x + sx * 3, y + sy * (12 + i * 4), sx * (i % 2 == 0 ? 5 : 3), faint);
        }
        // Top left: a crawling scan pattern.
        float scan = (t * 1.6f) % 40;
        for (int i = 0; i < 12; i++) {
            int x = m + 16 + i * 3, hh = 2 + (int) (4 * Math.abs(Mth.sin(i * .9f + t * .25f)));
            g.fill(x, m + 16 - hh, x + 1, m + 16, i * 3 < scan ? line : faint);
        }
        // Top right: the thermal indicator, a small level and two ticks.
        int rx = w - m - 52;
        HudStyle.caption(g, mc.font, "THERMAL", rx, m + 14, HudStyle.alpha(0xFFFFB27A, .42f * k), -1);
        for (int i = 0; i < 8; i++) g.fill(rx + i * 5, m + 26, rx + i * 5 + 3, m + 28, i < 2 + (int) (6 * a) ? warm : faint);
        // Bottom left: a slow forensic line sweeping.
        float sweep = (t * .9f) % 60;
        hline(g, m + 14, h - m - 14, 40, faint);
        g.fill((int) (m + 14 + sweep * 40 / 60f), h - m - 17, (int) (m + 15 + sweep * 40 / 60f), h - m - 11, line);
        // Bottom right: small symbols blinking slowly.
        for (int i = 0; i < 3; i++) {
            int x = w - m - 20 - i * 9, y = h - m - 16;
            boolean lit = ((int) (t / 9) + i) % 3 != 0;
            HudStyle.arc(g, x, y, 2, 3, 0, 360, lit ? line : faint);
        }
        // Scan marks round the one in the crosshair.
        Entity aimed = aimed(mc, RANGE);
        if (aimed != null && matricesValid) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 top = aimed.getPosition(e.getPartialTick()).add(0, aimed.getBbHeight() + .1, 0), bot = aimed.getPosition(e.getPartialTick()).add(0, -.05, 0);
            float[] s0 = project(top.subtract(cam), w, h), s1 = project(bot.subtract(cam), w, h);
            if (s0 != null && s1 != null) {
                float hgt = Math.abs(s1[1] - s0[1]), half = Math.max(6, hgt * .32f), x = (s0[0] + s1[0]) / 2, y0 = s0[1], y1 = s1[1];
                int c = HudStyle.alpha(0xFFFFC58A, .55f * a);
                float open = 1 + .15f * Mth.sin(t * .5f);
                bracket(g, x - half * open, y0, 1, 1, c); bracket(g, x + half * open, y0, -1, 1, c);
                bracket(g, x - half * open, y1, 1, -1, c); bracket(g, x + half * open, y1, -1, -1, c);
            }
        }
    }
    private static void bracket(GuiGraphics g, float x, float y, int sx, int sy, int c) {
        hline(g, (int) x, (int) y, sx * 5, c); vline(g, (int) x, (int) y, sy * 5, c);
    }
    private static void hline(GuiGraphics g, int x, int y, int len, int c) { g.fill(Math.min(x, x + len), y, Math.max(x, x + len), y + 1, c); }
    private static void vline(GuiGraphics g, int x, int y, int len, int c) { g.fill(x, Math.min(y, y + len), x + 1, Math.max(y, y + len), c); }
    /** A point relative to the camera to GUI pixels (null behind the camera), with the matrices of this frame. */
    static float[] project(Vec3 rel, int w, int h) {
        Vector4f p = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1);
        VIEW.transform(p); PROJ.transform(p);
        if (p.w <= .01f) return null;
        float nx = p.x / p.w, ny = p.y / p.w;
        return new float[]{(nx * .5f + .5f) * w, (1 - (ny * .5f + .5f)) * h};
    }
}
