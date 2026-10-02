package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.client.render.ghost.GhostRiderLayer;
import com.FIRNI.superheromod.client.render.hulk.HulkClient;
import com.FIRNI.superheromod.core.entity.ModEntities;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleRig;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * The stage in the champion select's big frame: a stand-in body (its own identity, so the player's
 * real hero is never touched) dressed as the chosen hero, turning after the mouse; and after LOCK IN,
 * a short show of what the hero does: Zed whirls his blades, cuts and throws a shuriken; Thor raises
 * Mjolnir and the sky answers; Hulk claps a shock wave across the frame; Cyclops fires his beam; a
 * sand storm spins up round Sandman; Ghost Rider rides in on the Hell Cycle trailing fire. Everything
 * is timed from the moment of the lock and eased, nothing snaps.
 */
final class ChampionStage {
    /** How long a show lasts (ticks). */
    static final int SHOW = 46;
    private final RemotePlayer actor;
    private HellCycleEntity bike;
    private final UUID id = UUID.nameUUIDFromBytes("superheromod:champion_stage".getBytes());

    ChampionStage() {
        var mc = Minecraft.getInstance();
        actor = mc.level == null ? null : new RemotePlayer(mc.level, new GameProfile(id, "Champion"));
    }
    void tick() {
        if (actor != null) { actor.tickCount++; }
        if (bike != null) bike.tickCount++;
    }
    void close() { ClientHeroRegistry.set(id, null); Showcase.stop(); }

    private static float ease(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float easeOut(float x) { x = Mth.clamp(x, 0, 1); return 1 - (1 - x) * (1 - x) * (1 - x); }
    private static float hash(float n) { double v = Math.sin(n * 12.9898) * 43758.5453; return (float) (v - Math.floor(v)); }

    /** The sounds of each show, played once at LOCK IN. */
    static void sounds(String hero) {
        var s = Minecraft.getInstance().getSoundManager();
        switch (hero) {
            case "zed" -> { play(s, SoundEvents.PLAYER_ATTACK_SWEEP, .9f, 1f); play(s, SoundEvents.ENDERMAN_TELEPORT, .6f, .5f); play(s, SoundEvents.TRIDENT_THROW, 1.4f, .7f); }
            case "thor" -> { play(s, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.1f, .6f); play(s, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, .7f); }
            case "hulk" -> { play(s, SoundEvents.RAVAGER_ROAR, .8f, .7f); play(s, SoundEvents.GENERIC_EXPLODE, .7f, .6f); }
            case "cyclops" -> { play(s, SoundEvents.BEACON_ACTIVATE, 1.6f, .7f); play(s, SoundEvents.BLAZE_SHOOT, .7f, .8f); }
            case "sandman" -> { play(s, SoundEvents.SAND_BREAK, .6f, 1f); play(s, SoundEvents.ELYTRA_FLYING, 1.2f, .4f); }
            case "ghost_rider" -> { play(s, SoundEvents.BLAZE_AMBIENT, .7f, .8f); play(s, SoundEvents.FIRECHARGE_USE, .8f, .7f); play(s, SoundEvents.RAVAGER_STEP, .5f, .9f); }
            default -> {}
        }
    }
    private static void play(net.minecraft.client.sounds.SoundManager s, SoundEvent e, float pitch, float volume) { s.play(SimpleSoundInstance.forUI(e, pitch, volume)); }

    /**
     * Draws the hero in the frame. st = ticks since LOCK IN (negative: no show yet, the body follows the mouse).
     */
    void draw(GuiGraphics g, String hero, int fx0, int fy0, int fx1, int fy1, int mx, int my, float st, float partial) {
        if (actor == null) return;
        int fw = fx1 - fx0, fh = fy1 - fy0;
        boolean showing = st >= 0;
        boolean hulk = "hulk".equals(hero);
        float zoom = showing ? 1 + .1f * ease(st / 10f) * (1 - ease((st - SHOW + 6) / 6f)) : 1;
        float s = fh * (hulk ? .26f : .36f) * zoom;
        float px = (fx0 + fx1) / 2f, py = fy1 - 6;
        // A shock shakes the stage.
        float shake = 0;
        if (showing && hulk && st > 13) shake = 4 * (float) Math.exp(-(st - 13) / 4) * Mth.sin(st * 3.1f);
        if (showing && "thor".equals(hero)) for (float hit : new float[]{8, 16, 24}) if (st > hit) shake += 2 * (float) Math.exp(-(st - hit) / 2.5) * Mth.sin(st * 4.3f);
        px += shake;
        // Facing: after the mouse until the show, then the show's own angle.
        float yaw = 180 + (float) Math.atan((px - mx) / 40f) * 20, pitch = -(float) Math.atan((py - fh * .6f - my) / 40f) * 20;
        if (showing) {
            yaw = switch (hero) { case "cyclops" -> 125; case "zed" -> 160; case "thor" -> 195; case "ghost_rider" -> 118; default -> 180; };
            pitch = 0;
        }
        final float x = px, faceYaw = yaw, facePitch = pitch;
        g.enableScissor(fx0, fy0, fx1, fy1);
        try {
            if (showing) behind(g, hero, st, x, py, s, fx0, fy0, fx1, fy1);
            ClientHeroRegistry.set(id, hero);
            choreograph(hero, st);
            if (showing && "ghost_rider".equals(hero)) rideIn(g, st, x, py, s, fx0, partial);
            else {
                Runnable draw = () -> body(g, actor, x, py, s, faceYaw, facePitch, Vec3.ZERO, partial);
                if (hulk) HulkClient.asHulk(actor, draw); else draw.run();
            }
            if (showing) front(g, hero, st, x, py, s, fx0, fy0, fx1, fy1);
        } catch (Throwable ignored) {
            // A hero that cannot be drawn here still has its art behind.
        } finally {
            Showcase.stop();
            ClientHeroRegistry.set(id, null);
            g.disableScissor();
        }
    }

    /** Which of their own actions the heroes with their own animations play, and when. */
    private void choreograph(String hero, float st) {
        if (st < 0) { Showcase.stop(); return; }
        switch (hero) {
            case "zed" -> {
                if (st < 10) Showcase.play(actor, com.FIRNI.superheromod.heroes.zed.ZedAction.SPIN, st);
                else if (st < 22) Showcase.play(actor, com.FIRNI.superheromod.heroes.zed.ZedAction.SLASH_FINISH, st - 10);
                else if (st < 31) Showcase.play(actor, com.FIRNI.superheromod.heroes.zed.ZedAction.THROW, st - 22);
                else Showcase.play(actor, com.FIRNI.superheromod.heroes.zed.ZedAction.IDLE, st);
            }
            case "thor" -> Showcase.play(actor, com.FIRNI.superheromod.heroes.thor.ThorAction.BEAM, Math.min(st, 44));
            case "hulk" -> Showcase.play(actor, com.FIRNI.superheromod.heroes.hulk.HulkAction.THUNDERCLAP, Math.min(st, 31));
            default -> Showcase.stop();
        }
    }

    /** The Hell Cycle sliding in from the left with him on it, braking hard in the middle, fire behind. */
    private void rideIn(GuiGraphics g, float st, float px, float py, float s, int fx0, float partial) {
        var level = Minecraft.getInstance().level;
        if (bike == null || bike.level() != level) bike = new HellCycleEntity(ModEntities.HELL_CYCLE.get(), level);
        float k = easeOut(st / 14f);
        float x = Mth.lerp(k, fx0 - s * 2.2f, px - s * .2f);
        float speed = 3 * (1 - k) + .15f;
        bike.filmPose(speed, st * (1.4f - k));
        float bikeYaw = 118;
        Vec3 seat = HellCycleRig.riderFeet(bikeYaw, 0, 0);
        body(g, bike, x, py, s * .85f, bikeYaw, 0, Vec3.ZERO, partial);
        GhostRiderLayer.filmBike = bike;
        try { body(g, actor, x, py, s * .85f, bikeYaw, 0, seat, partial); }
        finally { GhostRiderLayer.filmBike = null; }
        // The fire it leaves on the road.
        float trail = 1 - ease((st - 18) / 16f);
        if (trail > .01f) {
            light();
            for (int i = 0; i < 26; i++) {
                float tx = x - s * .9f - i * s * .16f;
                if (tx < fx0 - 10) break;
                float hgt = s * (.35f + .25f * hash(i + (int) (st * 1.7f))) * (1 - i / 26f) * trail;
                flame(g, tx, py - 2, s * .09f, hgt, 0xFFFF7A1A, .7f * (1 - i / 26f));
                flame(g, tx, py - 2, s * .05f, hgt * .6f, 0xFFFFE08A, .6f * (1 - i / 26f));
            }
            normal();
        }
        // Sparks off the tyres as it brakes.
        if (st > 8 && st < 20) {
            light();
            for (int i = 0; i < 14; i++) {
                float a = (st - 8) / 12f, h = hash(i * 3.3f);
                float sx = x - s * .5f - a * s * (.6f + h), sy = py - 3 - a * s * .4f * h + a * a * s * .3f;
                line(g, sx, sy, sx - s * .12f, sy + s * .04f, 1.2f, 0xFFFFC060, (1 - a) * .9f);
            }
            normal();
        }
    }

    // ------------------------------------------------------------------ the shows, behind and in front of the body
    private void behind(GuiGraphics g, String hero, float st, float px, float py, float s, int fx0, int fy0, int fx1, int fy1) {
        float in = ease(st / 6f), out = 1 - ease((st - SHOW + 8) / 8f);
        switch (hero) {
            case "sandman" -> sand(g, st, px, py, s, false, in * out);
            case "thor" -> { if (st > 6) { light(); glowDisc(g, px - s * .35f, py - s * 2.3f, s * .9f, 0xFF7FB8FF, .35f * out); normal(); } }
            case "hulk" -> { light(); glowDisc(g, px, py - s * 1.2f, s * 1.6f, 0xFF4FE03A, .18f * in * out); normal(); }
            case "zed" -> { light(); glowDisc(g, px, py - s * 1.0f, s * 1.4f, 0xFFB01020, .22f * in * out); normal(); }
            default -> {}
        }
    }
    private void front(GuiGraphics g, String hero, float st, float px, float py, float s, int fx0, int fy0, int fx1, int fy1) {
        switch (hero) {
            case "zed" -> zed(g, st, px, py, s, fx1);
            case "thor" -> thor(g, st, px, py, s, fy0);
            case "hulk" -> hulk(g, st, px, py, s, fx0, fx1);
            case "cyclops" -> cyclops(g, st, px, py, s, fx1);
            case "sandman" -> sand(g, st, px, py, s, true, ease(st / 6f) * (1 - ease((st - SHOW + 8) / 8f)));
            case "ghost_rider" -> { if (st > 12) { light(); glowDisc(g, px, py - s * .9f, s * 1.3f, 0xFFFF6A10, .25f * (1 - ease((st - 30) / 14f))); normal(); } }
            default -> {}
        }
    }

    /** Zed: red crescents and torn shadow round him as he whirls; a diagonal cut; a shuriken thrown across the frame. */
    private void zed(GuiGraphics g, float st, float px, float py, float s, int fx1) {
        if (st > 2 && st < 14) {
            float k = (st - 2) / 12f, a = (1 - k) * (1 - k);
            for (int i = 0; i < 9; i++) {
                float ang = i * .7f + k * 3, r = s * (.4f + .9f * easeOut(k * 1.4f)) * (.8f + .4f * hash(i));
                blob(g, px + Mth.cos(ang) * r, py - s * (.3f + .5f * hash(i + 2)) + Mth.sin(ang) * r * .25f, s * (.18f + .1f * hash(i + 5)), 0xFF07050A, .55f * a);
            }
            light();
            for (int i = 0; i < 3; i++) {
                float start = i * 2.1f + k * 5;
                arc(g, px, py - s * (.15f + .12f * i), s * (.6f + .7f * easeOut(k * 1.5f)), s * (.16f + .18f * easeOut(k * 1.5f)), start, start + 2.1f, s * .05f, 0xFFFF2A3C, .95f * a);
                arc(g, px, py - s * (.15f + .12f * i), s * (.6f + .7f * easeOut(k * 1.5f)), s * (.16f + .18f * easeOut(k * 1.5f)), start + .3f, start + 1.8f, s * .015f, 0xFFFFF0F4, .8f * a);
            }
            normal();
        }
        if (st > 14 && st < 21) {
            float k = (st - 14) / 7f, a = 1 - k;
            light();
            float x0 = px - s * .9f, y0 = py - s * 1.9f, x1 = px + s * .8f, y1 = py - s * .3f;
            line(g, x0, y0, Mth.lerp(easeOut(k * 3), x0, x1), Mth.lerp(easeOut(k * 3), y0, y1), s * .05f, 0xFFFF2A3C, a);
            line(g, x0, y0, Mth.lerp(easeOut(k * 3), x0, x1), Mth.lerp(easeOut(k * 3), y0, y1), s * .015f, 0xFFFFFFFF, a);
            normal();
        }
        if (st > 25 && st < 36) {
            float k = (st - 25) / 9f;
            float sx = Mth.lerp(easeOut(k), px + s * .3f, fx1 + s * .6f), sy = py - s * 1.1f - s * .1f * k;
            light();
            line(g, Mth.lerp(.5f, px + s * .3f, sx), sy, sx, sy, s * .05f, 0xFFFF2A3C, .8f);
            normal();
            blob(g, sx - s * .3f, sy, s * .2f, 0xFF07050A, .4f);
            star(g, sx, sy, s * .32f, st * 1.2f);
        }
    }
    /** Thor: Mjolnir raised, the sky answers with bolt after bolt, lightning crawling over him. */
    private void thor(GuiGraphics g, float st, float px, float py, float s, int fy0) {
        float tipX = px - s * .35f, tipY = py - s * 2.35f;
        light();
        for (float hit : new float[]{8, 16, 24}) {
            float age = st - hit;
            if (age < 0 || age > 5) continue;
            float a = 1 - age / 5;
            bolt(g, tipX + (hash(hit) - .5f) * s, fy0 - 4, tipX, tipY, (int) hit, s * .06f, 0xFF8CC8FF, a);
            bolt(g, tipX + (hash(hit) - .5f) * s, fy0 - 4, tipX, tipY, (int) hit, s * .02f, 0xFFFFFFFF, a);
            glowDisc(g, tipX, tipY, s * .7f, 0xFFBFE0FF, .7f * a);
        }
        if (st > 8 && st < SHOW - 4) {
            for (int i = 0; i < 3; i++) {
                int seed = (int) (st * 2) + i * 17;
                float ax = px + (hash(seed) - .5f) * s * .9f, ay = py - s * (.3f + 1.4f * hash(seed + 1));
                bolt(g, ax, ay, ax + (hash(seed + 2) - .5f) * s * .5f, ay + (hash(seed + 3) - .5f) * s * .5f, seed, s * .012f, 0xFFBFE0FF, .8f);
            }
        }
        normal();
    }
    /** Hulk: the clap: rings of air and dust racing out along the ground, chunks thrown. */
    private void hulk(GuiGraphics g, float st, float px, float py, float s, int fx0, int fx1) {
        if (st < 13) return;
        for (int r = 0; r < 2; r++) {
            float k = (st - 13 - r * 3) / 14f;
            if (k < 0 || k > 1) continue;
            float rx = Mth.lerp(easeOut(k), s * .4f, (fx1 - fx0) * .75f), ry = rx * .2f, a = (1 - k);
            ringFill(g, px, py - 2, rx, ry, s * .12f, 0xFF8A7A5A, .45f * a);
            light();
            ringFill(g, px, py - 2, rx, ry, s * .05f, 0xFF7CFF5A, .8f * a);
            normal();
        }
        float k = (st - 13) / 20f;
        if (k < 1) for (int i = 0; i < 12; i++) {
            float side = i % 2 == 0 ? 1 : -1, h = hash(i * 7.1f);
            float x = px + side * s * (.3f + 2.2f * k * (.6f + h)), y = py - 4 - s * (1.3f * k * (.5f + h)) + s * 1.6f * k * k;
            g.fill((int) x, (int) y, (int) (x + 2 + 3 * h), (int) (y + 2 + 3 * h), alphaOf(0xFF5A4630, 1 - k));
        }
    }
    /** Cyclops: the visor charges, then a thick beam to the edge of the frame, sparks where it ends. */
    private void cyclops(GuiGraphics g, float st, float px, float py, float s, int fx1) {
        float ex = px + s * .16f, ey = py - s * 1.52f;
        light();
        float charge = ease(st / 5f) * (1 - ease((st - 40) / 6f));
        glowDisc(g, ex, ey, s * .25f + s * .1f * Mth.sin(st * 1.3f), 0xFFFF3040, .9f * charge);
        if (st > 5 && st < 42) {
            float k = ease((st - 5) / 3f) * (1 - ease((st - 36) / 6f));
            float w = s * .1f * (1 + .15f * Mth.sin(st * 2.3f)) * k;
            line(g, ex, ey, fx1 + 4, ey + s * .05f, w * 3.2f, 0xFFFF1A28, .35f * k);
            line(g, ex, ey, fx1 + 4, ey + s * .05f, w * 1.6f, 0xFFFF3A40, .8f * k);
            line(g, ex, ey, fx1 + 4, ey + s * .05f, w * .5f, 0xFFFFE8E0, k);
            for (int i = 0; i < 10; i++) {
                float h = hash(i + (int) (st * 3)), sx = fx1 - 4 - h * 8, sy = ey + (hash(i * 3 + (int) st) - .5f) * s * .5f;
                line(g, sx, sy, sx - s * .2f * h, sy + (h - .5f) * s * .2f, 1, 0xFFFFC0A0, .8f * k);
            }
            glowDisc(g, fx1, ey, s * .6f, 0xFFFF4030, .5f * k);
        }
        normal();
    }
    /** Sandman: a storm of sand spinning up round him, grains in front and behind. */
    private void sand(GuiGraphics g, float st, float px, float py, float s, boolean front, float strength) {
        if (strength <= .01f) return;
        int n = 170;
        for (int i = 0; i < n; i++) {
            float h = (i + .5f) / n;
            float ang = i * 2.399f + st * (.22f + .1f * hash(i));
            float sin = Mth.sin(ang);
            if (front != sin > 0) continue;
            float rad = s * (.42f + .45f * h) * (1 + .15f * Mth.sin(st * .3f + i));
            float x = px + Mth.cos(ang) * rad, y = py - h * s * 2.3f + sin * rad * .16f - st * .2f * hash(i + 9);
            float size = 1 + 1.6f * hash(i * 1.7f);
            g.fill((int) x, (int) y, (int) (x + size), (int) (y + size), alphaOf(hash(i) > .5f ? 0xFFE8C080 : 0xFFC8984E, strength * (front ? .9f : .55f)));
        }
        if (front && st > 3 && st < 15) {
            float k = (st - 3) / 12f;
            for (int i = 0; i < 8; i++) blob(g, px + (hash(i) - .5f) * s * 2 * k, py - s * .2f - s * .6f * k * hash(i + 3), s * .3f, 0xFFD8B070, .35f * (1 - k));
        }
    }

    // ------------------------------------------------------------------ drawing an entity in the frame
    /** An entity standing at (x, y) in the GUI, scale pixels per block, facing yaw, offset (blocks, world axes). */
    private static void body(GuiGraphics g, Entity e, float x, float y, float scale, float yaw, float headPitch, Vec3 offset, float partial) {
        if (e instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = yaw;
            living.yHeadRot = living.yHeadRotO = yaw;
        }
        e.setYRot(yaw); e.yRotO = yaw; e.setXRot(headPitch); e.xRotO = headPitch;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        g.pose().pushPose();
        g.pose().translate(x, y, 50);
        g.pose().mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        g.pose().mulPose(new Quaternionf().rotateZ((float) Math.PI));
        Lighting.setupForEntityInInventory();
        dispatcher.overrideCameraOrientation(new Quaternionf());
        dispatcher.setRenderShadow(false);
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(e, offset.x, offset.y, offset.z, yaw, partial, g.pose(), g.bufferSource(), 15728880));
            g.flush();
        } finally {
            dispatcher.setRenderShadow(true);
            g.pose().popPose();
            Lighting.setupFor3DItems();
        }
    }

    // ------------------------------------------------------------------ 2D light and matter
    static int alphaOf(int colour, float a) { return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (colour & 0xFFFFFF); }
    private static void light() { RenderSystem.enableBlend(); RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); }
    private static void normal() { RenderSystem.defaultBlendFunc(); }
    private static BufferBuilder begin(VertexFormat.Mode mode) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(mode, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }
    private static void v(BufferBuilder b, Matrix4f m, float x, float y, int c, float a) {
        b.vertex(m, x, y, 0).color((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, Mth.clamp(a, 0, 1)).endVertex();
    }
    /** A soft round light: full in the middle, nothing at the edge. */
    private static void glowDisc(GuiGraphics g, float x, float y, float r, int c, float a) {
        if (a <= .01f || r <= .5f) return;
        BufferBuilder b = begin(VertexFormat.Mode.TRIANGLES);
        Matrix4f m = g.pose().last().pose();
        int n = 28;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            v(b, m, x, y, c, a); v(b, m, x + Mth.cos(a1) * r, y + Mth.sin(a1) * r, c, 0); v(b, m, x + Mth.cos(a0) * r, y + Mth.sin(a0) * r, c, 0);
        }
        BufferUploader.drawWithShader(b.end());
    }
    private static void blob(GuiGraphics g, float x, float y, float r, int c, float a) { normal(); glowDisc(g, x, y, r, c, a); }
    /** A line with soft edges, bright along its middle. */
    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float w, int c, float a) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < .01f || a <= .01f) return;
        float nx = -dy / len * w * .5f, ny = dx / len * w * .5f;
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        Matrix4f m = g.pose().last().pose();
        for (int side = -1; side <= 1; side += 2) {
            v(b, m, x0, y0, c, a); v(b, m, x1, y1, c, a); v(b, m, x1 + nx * side, y1 + ny * side, c, 0); v(b, m, x0 + nx * side, y0 + ny * side, c, 0);
        }
        BufferUploader.drawWithShader(b.end());
    }
    /** A crescent: part of an ellipse, thick in the middle, sharp at both ends. */
    private static void arc(GuiGraphics g, float cx, float cy, float rx, float ry, float a0, float a1, float w, int c, float a) {
        if (a <= .01f) return;
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        Matrix4f m = g.pose().last().pose();
        int n = 20;
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n, t1 = (i + 1) / (float) n;
            float b0 = a0 + (a1 - a0) * t0, b1 = a0 + (a1 - a0) * t1, w0 = w * Mth.sin(Mth.PI * t0), w1 = w * Mth.sin(Mth.PI * t1);
            float c0 = Mth.cos(b0), s0 = Mth.sin(b0), c1 = Mth.cos(b1), s1 = Mth.sin(b1);
            v(b, m, cx + c0 * (rx - w0), cy + s0 * (ry - w0 * .3f), c, a * t0); v(b, m, cx + c1 * (rx - w1), cy + s1 * (ry - w1 * .3f), c, a * t1);
            v(b, m, cx + c1 * (rx + w1), cy + s1 * (ry + w1 * .3f), c, a * t1); v(b, m, cx + c0 * (rx + w0), cy + s0 * (ry + w0 * .3f), c, a * t0);
        }
        BufferUploader.drawWithShader(b.end());
    }
    /** A flat ring on the ground (an ellipse band, bright in its middle). */
    private static void ringFill(GuiGraphics g, float cx, float cy, float rx, float ry, float w, int c, float a) {
        if (a <= .01f) return;
        BufferBuilder b = begin(VertexFormat.Mode.QUADS);
        Matrix4f m = g.pose().last().pose();
        int n = 40;
        for (int i = 0; i < n; i++) {
            float b0 = Mth.TWO_PI * i / n, b1 = Mth.TWO_PI * (i + 1) / n;
            float c0 = Mth.cos(b0), s0 = Mth.sin(b0), c1 = Mth.cos(b1), s1 = Mth.sin(b1);
            for (int side = -1; side <= 1; side += 2) {
                v(b, m, cx + c0 * rx, cy + s0 * ry, c, a); v(b, m, cx + c1 * rx, cy + s1 * ry, c, a);
                v(b, m, cx + c1 * (rx + side * w), cy + s1 * (ry + side * w * .25f), c, 0); v(b, m, cx + c0 * (rx + side * w), cy + s0 * (ry + side * w * .25f), c, 0);
            }
        }
        BufferUploader.drawWithShader(b.end());
    }
    /** A tongue of flame rising from (x, y). */
    private static void flame(GuiGraphics g, float x, float y, float w, float h, int c, float a) {
        BufferBuilder b = begin(VertexFormat.Mode.TRIANGLES);
        Matrix4f m = g.pose().last().pose();
        v(b, m, x - w, y, c, a); v(b, m, x + w, y, c, a); v(b, m, x + w * .3f, y - h, c, 0);
        BufferUploader.drawWithShader(b.end());
    }
    /** A jagged bolt from one point to another. */
    private static void bolt(GuiGraphics g, float x0, float y0, float x1, float y1, int seed, float w, int c, float a) {
        float px = x0, py = y0;
        int n = 8;
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        for (int i = 1; i <= n; i++) {
            float k = i / (float) n;
            float jag = i == n ? 0 : (hash(seed * 31 + i) - .5f) * len * .18f;
            float nx = Mth.lerp(k, x0, x1) + jag, ny = Mth.lerp(k, y0, y1);
            line(g, px, py, nx, ny, w, c, a);
            px = nx; py = ny;
        }
    }
    /** Zed's thrown shuriken: four hooked blades round a hub, spinning. */
    private static void star(GuiGraphics g, float x, float y, float r, float spin) {
        BufferBuilder b = begin(VertexFormat.Mode.TRIANGLES);
        Matrix4f m = g.pose().last().pose();
        for (int i = 0; i < 4; i++) {
            float a = spin + i * Mth.HALF_PI;
            float hub = r * .22f;
            float bx = x + Mth.cos(a - .5f) * hub, by = y + Mth.sin(a - .5f) * hub;
            float tx = x + Mth.cos(a + .55f) * r, ty = y + Mth.sin(a + .55f) * r;
            float cx2 = x + Mth.cos(a + .25f) * r * .62f, cy2 = y + Mth.sin(a + .25f) * r * .62f;
            v(b, m, x, y, 0xFF9EA2AC, 1); v(b, m, bx, by, 0xFFDCDFE6, 1); v(b, m, tx, ty, 0xFFFFFFFF, 1);
            v(b, m, x, y, 0xFF5A5E68, 1); v(b, m, tx, ty, 0xFFFFFFFF, 1); v(b, m, cx2, cy2, 0xFF8A8E98, 1);
        }
        BufferUploader.drawWithShader(b.end());
        glowDiscSolid(g, x, y, r * .16f, 0xFF2A2C32);
    }
    private static void glowDiscSolid(GuiGraphics g, float x, float y, float r, int c) {
        BufferBuilder b = begin(VertexFormat.Mode.TRIANGLES);
        Matrix4f m = g.pose().last().pose();
        int n = 12;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            v(b, m, x, y, c, 1); v(b, m, x + Mth.cos(a1) * r, y + Mth.sin(a1) * r, c, 1); v(b, m, x + Mth.cos(a0) * r, y + Mth.sin(a0) * r, c, 1);
        }
        BufferUploader.drawWithShader(b.end());
    }
}
