package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.client.render.entity.SandSoldierModel;
import com.FIRNI.superheromod.client.render.ghost.GhostRiderLayer;
import com.FIRNI.superheromod.client.render.hulk.HulkClient;
import com.FIRNI.superheromod.client.render.thor.Mjolnir;
import com.FIRNI.superheromod.client.render.zed.ZedBody;
import com.FIRNI.superheromod.client.render.zed.ZedMotion;
import com.FIRNI.superheromod.core.entity.ModEntities;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleRig;
import com.FIRNI.superheromod.heroes.hulk.HulkAction;
import com.FIRNI.superheromod.heroes.sandman.SandSoldierEntity;
import com.FIRNI.superheromod.heroes.thor.ThorAction;
import com.FIRNI.superheromod.heroes.zed.ZedAction;
import com.FIRNI.superheromod.heroes.panther.PantherAction;
import com.FIRNI.superheromod.heroes.magneto.MagnetoAction;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
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
 * a show of what the hero does, timed from the moment of the lock and eased, nothing snaps:
 * Cyclops sweeps his beam across the floor to the right, then turns and pours the right-click beam
 * out to the left, hand at his visor; Thor whirls Mjolnir, throws it, catches it on the way back,
 * strikes twice and looks out of the frame with lightning in his eyes; Zed casts his Death Mark
 * (sinks into shadow, two shadow copies run in, the X burns, he steps out behind it and it bursts);
 * sand soldiers rise either side of Sandman and a giant one behind him; Hulk claps a shock wave;
 * Ghost Rider rides in on the Hell Cycle and turns his skull to the screen; Black Panther runs his claw
 * combo and releases the suit's stored energy as a sphere. Every hero ends looking
 * out of the frame; the LOCKED IN banner comes after the show.
 */
final class ChampionStage {
    /** How long the banner stays once a show has finished (ticks). */
    static final int BANNER = 36;
    private static final ResourceLocation SAND = new ResourceLocation("minecraft", "textures/block/sand.png");
    private final RemotePlayer actor;
    private HellCycleEntity bike;
    private SandSoldierModel<SandSoldierEntity> soldier;
    private final UUID id = UUID.nameUUIDFromBytes("superheromod:champion_stage".getBytes());
    /** Which way the body faces this frame (degrees), for the effects that leave from its eyes. */
    private float faceYaw = 180, facePitch;

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
    private static float easeIn(float x) { x = Mth.clamp(x, 0, 1); return x * x * x; }
    /** 0 before a, 1 after b, eased between. */
    private static float span(float t, float a, float b) { return ease((t - a) / (b - a)); }
    private static float hash(float n) { double v = Math.sin(n * 12.9898) * 43758.5453; return (float) (v - Math.floor(v)); }

    /** How long each hero's show lasts (ticks); the banner comes after it. */
    static int length(String hero) {
        return switch (hero) {
            case "cyclops" -> 86;
            case "thor" -> 116;
            case "zed" -> 78;
            case "sandman" -> 84;
            case "hulk", "ghost_rider" -> 50;
            case "black_panther" -> 84;
            case "magneto" -> 80;
            case "batman" -> 74;
            case "iceman" -> 66;
            default -> 40;
        };
    }

    /** The sounds of a show, each on its beat: n = whole ticks since LOCK IN. */
    static void cues(String hero, int n) {
        var s = Minecraft.getInstance().getSoundManager();
        switch (hero) {
            case "zed" -> {
                if (n == 0) play(s, SoundEvents.ENDERMAN_TELEPORT, .5f, .5f);
                if (n == 8) play(s, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, .8f);
                if (n == 17) { play(s, SoundEvents.PLAYER_ATTACK_SWEEP, .8f, .9f); play(s, SoundEvents.FIRECHARGE_USE, .6f, .6f); }
                if (n == 44) play(s, SoundEvents.ENDERMAN_TELEPORT, 1.4f, .5f);
                if (n == 48) { play(s, SoundEvents.PLAYER_ATTACK_CRIT, .9f, .9f); play(s, SoundEvents.GENERIC_EXPLODE, 1.6f, .45f); }
            }
            case "thor" -> {
                if (n == 2) play(s, SoundEvents.ELYTRA_FLYING, 1.7f, .35f);
                if (n == 40) play(s, SoundEvents.TRIDENT_THROW, .7f, 1f);
                if (n == 44) play(s, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6f, .3f);
                if (n == 56) { play(s, SoundEvents.TRIDENT_RETURN, .9f, 1f); play(s, SoundEvents.ANVIL_LAND, 1.6f, .25f); }
                if (n == 70) { play(s, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .8f, 1f); play(s, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, .4f); }
                if (n == 82) { play(s, SoundEvents.PLAYER_ATTACK_STRONG, .7f, 1f); play(s, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2f, .5f); }
                if (n == 98) play(s, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.1f, .6f);
            }
            case "cyclops" -> {
                if (n == 4) play(s, SoundEvents.BEACON_ACTIVATE, 1.6f, .6f);
                if (n == 8) play(s, SoundEvents.FIRECHARGE_USE, 1.5f, .5f);
                if (n == 18 || n == 28) play(s, SoundEvents.FIRE_EXTINGUISH, 1.3f, .25f);
                if (n == 38) play(s, SoundEvents.BEACON_POWER_SELECT, 1.4f, .6f);
                if (n == 41) play(s, SoundEvents.BLAZE_SHOOT, .6f, .8f);
                if (n == 50 || n == 60) play(s, SoundEvents.GENERIC_EXPLODE, n == 50 ? 1.5f : 1.2f, .35f);
            }
            case "sandman" -> {
                if (n == 0) { play(s, SoundEvents.SAND_BREAK, .6f, 1f); play(s, SoundEvents.ELYTRA_FLYING, 1.2f, .3f); }
                if (n == 6 || n == 9 || n == 13 || n == 16) play(s, SoundEvents.SAND_FALL, .7f + n * .02f, .9f);
                if (n == 24) play(s, SoundEvents.WARDEN_EMERGE, 1f, .6f);
                if (n == 58) play(s, SoundEvents.RAVAGER_ROAR, .55f, .6f);
            }
            case "hulk" -> {
                if (n == 0) play(s, SoundEvents.RAVAGER_ROAR, .8f, .7f);
                if (n == 13) play(s, SoundEvents.GENERIC_EXPLODE, .6f, .7f);
            }
            case "ghost_rider" -> {
                if (n == 0) { play(s, SoundEvents.BLAZE_AMBIENT, .7f, .8f); play(s, SoundEvents.FIRECHARGE_USE, .8f, .7f); }
                if (n == 10) play(s, SoundEvents.RAVAGER_STEP, .5f, .9f);
                if (n == 28) play(s, SoundEvents.BLAZE_AMBIENT, .5f, .6f);
            }
            case "black_panther" -> {
                if (n == 0 || n == 9) play(s, SoundEvents.PLAYER_ATTACK_SWEEP, n == 0 ? 1.9f : 1.7f, .5f);
                if (n == 3 || n == 12) play(s, SoundEvents.TRIDENT_HIT, 1.85f, .4f);
                if (n == 18) play(s, SoundEvents.PLAYER_ATTACK_SWEEP, 1.35f, .6f);
                if (n == 22) { play(s, SoundEvents.TRIDENT_HIT, 1.5f, .5f); play(s, SoundEvents.PLAYER_ATTACK_STRONG, 1.1f, .6f); }
                if (n == 34) { play(s, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.15f, .7f); play(s, SoundEvents.PLAYER_ATTACK_CRIT, 1.3f, .5f); }
                if (n == 44) { play(s, SoundEvents.BEACON_POWER_SELECT, .6f, .6f); play(s, SoundEvents.CONDUIT_AMBIENT_SHORT, 1.3f, .8f); }
                if (n == 51) play(s, SoundEvents.BEACON_ACTIVATE, 1.4f, .6f);
                if (n == 58) { play(s, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, .7f); play(s, SoundEvents.GENERIC_EXPLODE, .75f, .5f); play(s, SoundEvents.AMETHYST_BLOCK_CHIME, .6f, 1f); }
                if (n == 64) play(s, SoundEvents.BEACON_DEACTIVATE, 1.4f, .5f);
            }
            case "batman" -> {
                // The cape; the Batarangs counted into the hands, thrown wide; a smoke pellet into the floor.
                if (n == 0) play(s, com.FIRNI.superheromod.core.sound.ModSounds.BATMAN_CAPE.get(), 1f, .9f);
                if (n == 12 || n == 16 || n == 20) play(s, SoundEvents.ARMOR_EQUIP_CHAIN, 1.4f + n * .01f, .5f);
                if (n == 25) { play(s, com.FIRNI.superheromod.core.sound.ModSounds.BATMAN_BATARANG.get(), .9f, 1f); play(s, SoundEvents.PLAYER_ATTACK_SWEEP, 1.8f, .4f); }
                if (n == 33) play(s, SoundEvents.TRIDENT_HIT, 1.6f, .5f);
                if (n == 41) play(s, com.FIRNI.superheromod.core.sound.ModSounds.FX_WHOOSH_LIGHT.get(), .9f, .5f);
                if (n == 46) play(s, com.FIRNI.superheromod.core.sound.ModSounds.BATMAN_SMOKE.get(), 1f, .8f);
            }
            case "iceman" -> {
                // Frost gathering, the sword forming in his hand, a cut, the spin, the sword shattering.
                if (n == 0) { play(s, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_FROST.get(), 1f, .9f); play(s, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_FORM.get(), 1f, 1f); }
                if (n == 12) play(s, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_SWORD_SWING.get(), 1f, 1f);
                if (n == 24) play(s, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_SWORD_SPIN.get(), 1f, 1f);
                if (n == 31 || n == 38 || n == 45) play(s, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_SWORD_SWING.get(), 1.1f, .8f);
                if (n == 50) { play(s, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_SHATTER.get(), 1f, 1f); play(s, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_CRACK.get(), .8f, .8f); }
            }
            case "magneto" -> {
                // The scrap rising off the floor; the rods called down; the shield; its burst; the landing.
                if (n == 0) { play(s, SoundEvents.BEACON_POWER_SELECT, 1.3f, .5f); play(s, SoundEvents.ELYTRA_FLYING, 1.5f, .3f); play(s, SoundEvents.CHAIN_STEP, .6f, .6f); }
                if (n == 6) play(s, SoundEvents.IRON_GOLEM_REPAIR, .8f, .4f);
                if (n == 18) { play(s, SoundEvents.ARMOR_EQUIP_NETHERITE, .7f, .8f); play(s, SoundEvents.TRIDENT_RIPTIDE_1, .6f, .4f); }
                if (n == 24 || n == 27 || n == 30) { play(s, SoundEvents.ANVIL_LAND, .5f + n * .01f, .4f); play(s, SoundEvents.TRIDENT_HIT_GROUND, .6f, .5f); }
                if (n == 36) { play(s, SoundEvents.IRON_GOLEM_STEP, .5f, .7f); play(s, SoundEvents.BEACON_ACTIVATE, 1.4f, .5f); play(s, SoundEvents.PISTON_EXTEND, .6f, .5f); }
                if (n == 42 || n == 47) play(s, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, .45f);
                if (n == 54) { play(s, SoundEvents.GENERIC_EXPLODE, .8f, .45f); play(s, SoundEvents.CHAIN_BREAK, .5f, .6f); play(s, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, .25f); }
                if (n == 64) play(s, SoundEvents.ARMOR_EQUIP_IRON, .8f, .5f);
            }
            default -> {}
        }
    }
    private static void play(net.minecraft.client.sounds.SoundManager s, SoundEvent e, float pitch, float volume) { s.play(SimpleSoundInstance.forUI(e, pitch, volume)); }

    /**
     * Draws the hero in the frame. st = ticks since LOCK IN (negative: no show yet, the body follows the mouse).
     */
    void draw(GuiGraphics g, String hero, int fx0, int fy0, int fx1, int fy1, int mx, int my, float st, float partial) {
        if (actor == null) return;
        int fh = fy1 - fy0;
        boolean showing = st >= 0;
        boolean hulk = "hulk".equals(hero);
        int len = length(hero);
        float zoom = showing && !"sandman".equals(hero) ? 1 + .08f * ease(st / 10f) * (1 - span(st, len - 10, len)) : 1;
        float s = fh * (hulk ? .26f : .36f) * zoom;
        float px = (fx0 + fx1) / 2f, py = fy1 - 6;
        if (showing) px += shake(hero, st) * s / 40f;
        // Facing: after the mouse until the show, then the show's own angles.
        float yaw = 180 + (float) Math.atan((px - mx) / 40f) * 20, pitch = -(float) Math.atan((py - fh * .6f - my) / 40f) * 20, head = yaw;
        if (showing) { float[] f = facing(hero, st); yaw = f[0]; pitch = f[1]; head = f[2]; }
        faceYaw = yaw; facePitch = pitch;
        final float x = px, bodyYaw = yaw, headYaw = head, headPitch = pitch;
        g.enableScissor(fx0, fy0, fx1, fy1);
        try {
            if (showing) behind(g, hero, st, x, py, s, fx0, fy0, fx1, fy1, partial);
            ClientHeroRegistry.set(id, hero);
            choreograph(hero, st);
            if (showing && "ghost_rider".equals(hero)) rideIn(g, st, x, py, s, fx0, headYaw, partial);
            else {
                float z = "sandman".equals(hero) ? 120 : 50;
                Runnable draw = () -> body(g, actor, x, py, z, s, bodyYaw, headYaw, headPitch, Vec3.ZERO, partial);
                if (hulk) HulkClient.asHulk(actor, draw); else draw.run();
                if (showing && "magneto".equals(hero)) magnetoMetal(g, st, x, py, z, s, partial);
            }
            Showcase.stop();
            ClientHeroRegistry.set(id, null);
            if (showing) {
                // In front of everything drawn so far.
                g.pose().pushPose();
                g.pose().translate(0, 0, 600);
                try { front(g, hero, st, x, py, s, fx0, fy0, fx1, fy1, partial); }
                finally { g.pose().popPose(); }
            }
        } catch (Throwable ignored) {
            // A hero that cannot be drawn here still has its art behind.
        } finally {
            Showcase.stop();
            ClientHeroRegistry.set(id, null);
            normal();
            g.disableScissor();
        }
    }

    /** Body yaw, head pitch and head yaw (degrees) through each show; every one ends looking out of the frame. */
    private static float[] facing(String hero, float st) {
        float yaw = 180, pitch = 0, head;
        switch (hero) {
            case "cyclops" -> {
                // Turned to the right for the sweep, the head following the beam down; round to the left for the beam.
                yaw = Mth.lerp(span(st, 0, 5), 180, 140);
                yaw = Mth.lerp(span(st, 34, 40), yaw, 214);
                pitch = sweep(st) * .9f;
                pitch = Mth.lerp(span(st, 34, 40), pitch, 4);
            }
            case "thor" -> {
                yaw = Mth.lerp(span(st, 0, 6), 180, 200);
                yaw = Mth.lerp(span(st, 32, 38), yaw, 216);
                yaw = Mth.lerp(span(st, 58, 64), yaw, 196);
            }
            case "black_panther" -> yaw = Mth.lerp(span(st, 0, 4), 180, 166);
            case "ghost_rider" -> {
                // On the bike the body stays with it; only the skull turns to the screen.
                return new float[]{118, Mth.lerp(span(st, 26, 36), 0, 4), Mth.lerp(span(st, 26, 36), 118, 180)};
            }
            default -> {}
        }
        // Last of all: round to face the screen.
        float turn = switch (hero) { case "thor" -> 88; case "cyclops" -> 70; default -> length(hero) - 16; };
        float end = span(st, turn, turn + 8);
        yaw = Mth.lerp(end, yaw, 180);
        pitch = Mth.lerp(end, pitch, 0);
        head = yaw;
        return new float[]{yaw, pitch, head};
    }
    /** Cyclops' sweep: the angle of the beam below level (screen degrees). */
    private static float sweep(float st) { return Mth.lerp(ease((st - 8) / 26f), -14, 30); }

    /** How far the stage shakes (pixels at a 40-pixel block) at this moment. */
    private static float shake(String hero, float st) {
        float shake = 0;
        float[] hits = switch (hero) {
            case "hulk" -> new float[]{13};
            case "black_panther" -> new float[]{22, 34, 58};
            case "thor" -> new float[]{56, 70, 82, 98};
            case "zed" -> new float[]{18, 48};
            case "cyclops" -> new float[]{50, 60};
            case "sandman" -> new float[]{24, 58};
            case "magneto" -> new float[]{24, 27, 30, 54};
            case "batman" -> new float[]{33, 46};
            case "iceman" -> new float[]{50};
            default -> new float[0];
        };
        for (float hit : hits) if (st > hit) shake += 3 * (float) Math.exp(-(st - hit) / 3.5) * Mth.sin(st * 3.7f);
        return shake;
    }

    /** Which of their own actions the heroes play, and when. */
    private void choreograph(String hero, float st) {
        if (st < 0) { Showcase.stop(); return; }
        switch (hero) {
            case "zed" -> {
                if (st < 6) Showcase.play(actor, ZedAction.MARK_LOCK, st);
                else if (st < 46) Showcase.play(actor, ZedAction.MARK_HIDDEN, st - 6);
                else if (st < 58) Showcase.play(actor, ZedAction.MARK_STRIKE, st - 46);
                else Showcase.play(actor, ZedAction.IDLE, st);
            }
            case "thor" -> {
                if (st < 36) Showcase.play(actor, ThorAction.CHARGE, st);
                else if (st < 56) { Showcase.play(actor, ThorAction.THROW, st - 34); Showcase.emptyHanded(st >= 40); }
                else if (st < 64) Showcase.play(actor, ThorAction.CATCH, st - 56);
                else if (st < 76) Showcase.play(actor, ThorAction.SWING_RIGHT, st - 64);
                else if (st < 88) Showcase.play(actor, ThorAction.SWING_LEFT, st - 76);
                else { Showcase.play(actor, ThorAction.IDLE, st - 88); Showcase.glow(span(st, 92, 98), .5f * span(st, 92, 98)); }
            }
            case "hulk" -> Showcase.play(actor, HulkAction.THUNDERCLAP, Math.min(st, 31));
            // Cyclops: the hand goes to the visor while a beam is out.
            case "cyclops" -> Showcase.play(actor, st >= 4 && st < 36 || st >= 37 && st < 70 ? 1 : 0, st);
            case "black_panther" -> {
                // The claw combo, then the stored energy released.
                if (st < 9) Showcase.play(actor, PantherAction.CLAW_RIGHT, st);
                else if (st < 18) Showcase.play(actor, PantherAction.CLAW_LEFT, st - 9);
                else if (st < 28) Showcase.play(actor, PantherAction.CLAW_DOUBLE, st - 18);
                else if (st < 44) Showcase.play(actor, st < 41 ? PantherAction.CLAW_UPPER : PantherAction.IDLE, st < 41 ? st - 28 : st);
                else if (st < 58) { Showcase.play(actor, PantherAction.RELEASE_CHARGE, st - 44); Showcase.glow(1, .2f + .8f * span(st, 44, 56)); }
                else if (st < 66) { Showcase.play(actor, PantherAction.RELEASE, st - 58); Showcase.glow(1, 0); }
                else if (st < 80) { Showcase.play(actor, PantherAction.RELEASE_RECOVER, st - 66); Showcase.glow(.1f, 0); }
                else { Showcase.play(actor, PantherAction.IDLE, st); Showcase.glow(.1f, 0); }
            }
            case "iceman" -> {
                float t = st;
                Showcase.emptyHanded(t < 1);
                if (t < 12) Showcase.play(actor, com.FIRNI.superheromod.heroes.iceman.IcemanAction.FORM, t);
                else if (t < 24) Showcase.play(actor, com.FIRNI.superheromod.heroes.iceman.IcemanAction.STRIKE, t - 12);
                else if (t < 50) { Showcase.play(actor, com.FIRNI.superheromod.heroes.iceman.IcemanAction.CHARGE, t - 24 + com.FIRNI.superheromod.heroes.iceman.IcemanAction.HOLD_TICKS); Showcase.glow(0, Math.min(1, (t - 24) / 26f)); }
                else if (t < 58) { Showcase.play(actor, com.FIRNI.superheromod.heroes.iceman.IcemanAction.RELEASE, t - 50); Showcase.emptyHanded(true); }
                else { Showcase.play(actor, com.FIRNI.superheromod.heroes.iceman.IcemanAction.IDLE, t); Showcase.emptyHanded(true); }
            }
            case "batman" -> {
                float t = st;
                if (t < 10) Showcase.play(actor, com.FIRNI.superheromod.heroes.batman.BatmanAction.IDLE, t);
                else if (t < 22) Showcase.play(actor, com.FIRNI.superheromod.heroes.batman.BatmanAction.BATARANG_CHARGE, t - 10);
                else if (t < 34) Showcase.play(actor, com.FIRNI.superheromod.heroes.batman.BatmanAction.BATARANG_MULTI, t - 22);
                else if (t < 37) Showcase.play(actor, com.FIRNI.superheromod.heroes.batman.BatmanAction.IDLE, t);
                else if (t < 48) Showcase.play(actor, com.FIRNI.superheromod.heroes.batman.BatmanAction.GADGET_THROW, t - 37);
                else Showcase.play(actor, com.FIRNI.superheromod.heroes.batman.BatmanAction.IDLE, t);
            }
            case "magneto" -> {
                // Lifts off as the scrap rises round him (the hand stirring it); calls the rods down out of the sky; the
                // shield (plates round a violet sphere); bursts it; sets down again and faces the screen.
                float fly = st < 62 ? 1 : 0;
                if (st < 16) Showcase.play(actor, MagnetoAction.GRAB, Math.min(st, MagnetoAction.GRAB_TICKS - 1));
                else if (st < 34) Showcase.play(actor, MagnetoAction.BARRAGE, st - 16);
                else if (st < 54) Showcase.play(actor, st < 50 ? MagnetoAction.SHIELD_RAISE : MagnetoAction.SHIELD, st < 50 ? st - 36 : st - 50);
                else if (st < 66) Showcase.play(actor, MagnetoAction.BURST, st - 54);
                else Showcase.play(actor, MagnetoAction.IDLE, st);
                Showcase.glow(0, fly);
            }
            default -> Showcase.stop();
        }
    }

    /** The Hell Cycle sliding in from the left with him on it, braking hard in the middle, fire behind. */
    private void rideIn(GuiGraphics g, float st, float px, float py, float s, int fx0, float headYaw, float partial) {
        var level = Minecraft.getInstance().level;
        if (bike == null || bike.level() != level) bike = new HellCycleEntity(ModEntities.HELL_CYCLE.get(), level);
        float k = easeOut(st / 14f);
        float x = Mth.lerp(k, fx0 - s * 2.2f, px - s * .2f);
        float speed = 3 * (1 - k) + .15f;
        bike.filmPose(speed, st * (1.4f - k));
        float bikeYaw = 118;
        Vec3 seat = HellCycleRig.riderFeet(bikeYaw, 0, 0);
        body(g, bike, x, py, 50, s * .85f, bikeYaw, bikeYaw, 0, Vec3.ZERO, partial);
        GhostRiderLayer.filmBike = bike;
        try { body(g, actor, x, py, 50, s * .85f, bikeYaw, headYaw, 0, seat, partial); }
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
        // The skull's fire flares as it turns to look out.
        if (st > 26 && st < 44) {
            float a = (float) Math.sin(Math.PI * (st - 26) / 18);
            light();
            glowDisc(g, x - s * .05f, py - s * 1.45f, s * .45f, 0xFFFF8A20, .35f * a);
            normal();
        }
    }

    // ------------------------------------------------------------------ the shows, behind and in front of the body
    private void behind(GuiGraphics g, String hero, float st, float px, float py, float s, int fx0, int fy0, int fx1, int fy1, float partial) {
        int len = length(hero);
        float in = ease(st / 6f), out = 1 - span(st, len - 8, len + 8);
        switch (hero) {
            case "sandman" -> {
                sand(g, st, px, py, s, false, .7f * in * out);
                army(g, st, px, py, s, fx0, fy0, fx1, partial);
            }
            case "thor" -> {
                light();
                glowDisc(g, px, py - s * 1.2f, s * 1.5f, 0xFF7FB8FF, .16f * in * out + .2f * span(st, 20, 34) * (1 - span(st, 40, 50)));
                // The sky answers his look: one great bolt behind him into the ground.
                float age = st - 98;
                if (age >= 0 && age < 7) {
                    float a = 1 - age / 7;
                    float bx = px + s * 1.1f;
                    bolt(g, bx - s * .4f, fy0 - 4, bx, py - 2, 98, s * .14f, 0xFF8CC8FF, a);
                    bolt(g, bx - s * .4f, fy0 - 4, bx, py - 2, 98, s * .04f, 0xFFFFFFFF, a);
                    glowDisc(g, bx, py - 2, s * .9f, 0xFFBFE0FF, .6f * a);
                }
                normal();
            }
            case "hulk" -> { light(); glowDisc(g, px, py - s * 1.2f, s * 1.6f, 0xFF4FE03A, .18f * in * out); normal(); }
            case "zed" -> { light(); glowDisc(g, px, py - s * 1.0f, s * 1.4f, 0xFFB01020, .22f * in * out); normal(); }
            case "cyclops" -> { light(); glowDisc(g, px, py - s * 1.4f, s * 1.3f, 0xFFFF2030, .12f * in * out); normal(); }
            case "black_panther" -> { light(); glowDisc(g, px, py - s * 1.1f, s * 1.5f, 0xFF7A3CFF, (.16f + .3f * span(st, 44, 58) * (1 - span(st, 58, 70))) * in * out); normal(); }
            case "magneto" -> { light(); glowDisc(g, px, py - s * 1.3f, s * 1.6f, 0xFF9A50FF, (.14f + .3f * span(st, 36, 46) * (1 - span(st, 54, 64))) * in * out); normal(); }
            default -> {}
        }
    }
    private void front(GuiGraphics g, String hero, float st, float px, float py, float s, int fx0, int fy0, int fx1, int fy1, float partial) {
        switch (hero) {
            case "zed" -> zed(g, st, px, py, s, fx0, fx1, partial);
            case "thor" -> thor(g, st, px, py, s, fx0, fy0, fx1, fy1, partial);
            case "hulk" -> hulk(g, st, px, py, s, fx0, fx1);
            case "cyclops" -> cyclops(g, st, px, py, s, fx0, fx1);
            case "sandman" -> {
                sand(g, st, px, py, s, true, .6f * ease(st / 6f) * (1 - span(st, length(hero) - 8, length(hero) + 8)));
                risings(g, st, px, py, s);
            }
            case "black_panther" -> panther(g, st, px, py, s, fx0, fy0, fx1, fy1);
            case "magneto" -> magnetoLight(g, st, px, py, s);
            case "batman" -> batman(g, st, px, py, s);
            case "iceman" -> iceman(g, st, px, py, s);
            case "ghost_rider" -> { if (st > 12) { light(); glowDisc(g, px, py - s * .9f, s * 1.3f, 0xFFFF6A10, .25f * (1 - ease((st - 30) / 14f))); normal(); } }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ Batman
    /** Five Batarangs fanning out of his hands across the frame, then a smoke pellet bursting at his feet into a grey cloud. */
    private void batman(GuiGraphics g, float st, float px, float py, float s) {
        normal();
        float throwAge = st - 25;
        if (throwAge >= 0 && throwAge < 12) {
            float k = easeOut(throwAge / 9f), a = 1 - Mth.clamp((throwAge - 7) / 5f, 0, 1);
            for (int i = 0; i < 5; i++) {
                float ang = (float) Math.toRadians(-150 + i * 30);
                float x = px + Mth.cos(ang) * s * (.3f + 2.4f * k), y = py - s * 1.25f + Mth.sin(ang) * s * (.2f + 1.1f * k);
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(throwAge * 1.4f + i));
                com.FIRNI.superheromod.client.render.batman.BatmanWheel.batarang(g, 0, 0, s * .16f, com.FIRNI.superheromod.client.hud.HudStyle.alpha(0xFF1A1C20, a), 1);
                g.pose().popPose();
            }
        }
        float smokeAge = st - 46;
        if (smokeAge >= 0) {
            float grow = easeOut(smokeAge / 14f), fade = 1 - Mth.clamp((smokeAge - 16) / 12f, 0, 1);
            for (int i = 0; i < 14; i++) {
                float h1 = (float) FilmFxHash.h(i * 3 + 1), h2 = (float) FilmFxHash.h(i * 3 + 2);
                float x = px + (h1 - .5f) * s * 3.2f * grow, y = py - s * (.1f + h2 * .9f) * grow;
                glowDisc(g, x, y, s * (.5f + .5f * h2) * (.4f + .8f * grow), 0xFF8B9096, .5f * fade);
            }
        }
    }
    // ------------------------------------------------------------------ Iceman
    /** Frost breath round him as the sword forms; snow and shards flung round in a disc through the spin; the sword's shards bursting out. */
    private void iceman(GuiGraphics g, float st, float px, float py, float s) {
        light();
        if (st < 12) glowDisc(g, px + s * .35f, py - s * 1.05f, s * (.2f + .4f * ease(st / 10f)), 0xFF8FD8FF, .45f * (1 - span(st, 9, 12)));
        normal();
        if (st >= 24 && st < 52) {
            float k = Math.min(1, (st - 24) / 8f) * (1 - span(st, 48, 52));
            for (int i = 0; i < 26; i++) {
                float a = (float) (FilmFxHash.h(i) * Math.PI * 2) + st * (.35f + .2f * (float) FilmFxHash.h(i + 40));
                float r = s * (.6f + 1.3f * (float) FilmFxHash.h(i + 7));
                float x = px + Mth.cos(a) * r, y = py - s * (.3f + .9f * (float) FilmFxHash.h(i + 13)) + Mth.sin(a) * r * .18f;
                glowDisc(g, x, y, s * (.04f + .04f * (float) FilmFxHash.h(i + 3)), 0xFFEFF8FF, .8f * k);
            }
        }
        float burst = st - 50;
        if (burst >= 0 && burst < 14) {
            float k = easeOut(burst / 10f), a = 1 - Mth.clamp((burst - 6) / 8f, 0, 1);
            light(); glowDisc(g, px + s * .4f, py - s * 1.0f, s * (.3f + .8f * k), 0xFFBFE8FF, .6f * (1 - k)); normal();
            for (int i = 0; i < 14; i++) {
                float ang = (float) (FilmFxHash.h(i + 60) * Math.PI * 2);
                float x = px + s * .4f + Mth.cos(ang) * s * 1.6f * k, y = py - s * 1.0f + Mth.sin(ang) * s * 1.2f * k + burst * burst * s * .004f;
                line(g, x, y, x + Mth.cos(ang) * s * .12f, y + Mth.sin(ang) * s * .12f, s * .05f, 0xFFDDF3FF, a);
            }
        }
    }
    private static final class FilmFxHash { static double h(double n) { double x = Math.sin(n * 12.9898) * 43758.5453; return x - Math.floor(x); } }

    // ------------------------------------------------------------------ Zed: Death Mark
    /**
     * He sinks into a pool of his own shadow; two shadow copies of him run in from both sides and meet
     * where he stood; the red X burns there while he is gone (1.5 s); he steps out behind it, crouched,
     * blades out, and the X bursts.
     */
    private void zed(GuiGraphics g, float st, float px, float py, float s, int fx0, int fx1, float partial) {
        float cx = px, cy = py - s * 1.0f;
        // The pool he sinks into, and the smoke he goes down in.
        float pool = ease(st / 4f) * (1 - span(st, 14, 22));
        if (pool > .01f) {
            normal(); oval(g, px, py - 2, s * .7f * pool, s * .14f * pool, 0xFF050307, .85f);
            light(); ringFill(g, px, py - 2, s * .7f * pool, s * .14f * pool, s * .05f, 0xFFE0303A, .6f * pool); normal();
        }
        if (st < 10) for (int i = 0; i < 10; i++) {
            float k = (st + hash(i) * 4) / 10f;
            if (k > 1) continue;
            blob(g, px + (hash(i + 3) - .5f) * s * .8f, py - s * (.2f + 1.4f * k), s * (.2f + .15f * hash(i + 5)), 0xFF07050A, .5f * (1 - k));
        }
        // Two shadow copies run in from either side and meet in the middle.
        if (st > 7 && st < 20) {
            float k = easeIn((st - 7) / 11f);
            for (int side = -1; side <= 1; side += 2) {
                float from = side < 0 ? fx0 - s * .7f : fx1 + s * .7f;
                float x = Mth.lerp(k, from, px + side * s * .12f);
                float back = Mth.lerp(easeIn(Math.max(0, (st - 9.5f) / 11f)), from, px + side * s * .12f);
                // Shadow torn off behind them, and the red line their eyes draw.
                for (int i = 0; i < 7; i++) {
                    float bx = Mth.lerp(i / 7f, x, back);
                    blob(g, bx, py - s * (.5f + .6f * hash(i + side * 9)), s * (.22f + .1f * hash(i + 4)), 0xFF07050A, .45f * (1 - i / 7f));
                }
                light();
                line(g, back, py - s * 1.46f, x, py - s * 1.46f, s * .05f, 0xFFFF2A1C, .8f);
                line(g, back, py - s * 1.46f, x, py - s * 1.46f, s * .015f, 0xFFFFF0DC, .7f);
                normal();
                zedShadow(g, x, py, s, side < 0 ? 90 : 270, actor.tickCount + partial, .85f * (1 - span(st, 16, 19)));
            }
        }
        // They strike home: the X.
        if (st > 17 && st < 56) {
            float hit = st - 18;
            light();
            if (hit > 0 && hit < 8) glowDisc(g, cx, cy, s * 1.1f, 0xFFFF2030, .7f * (1 - hit / 8));
            float stroke1 = easeOut((st - 18) / 3f), stroke2 = easeOut((st - 21) / 3f);
            float burst = span(st, 48, 55);
            float r = s * .5f * (1 + .7f * burst), a = 1 - burst;
            float pulse = .8f + .2f * Mth.sin(st * .9f);
            for (int k = 0; k < 2; k++) {
                float grow = k == 0 ? stroke1 : stroke2;
                if (grow <= 0) continue;
                float dx = k == 0 ? -r : r;
                float x0 = cx + dx, y0 = cy - r, x1 = Mth.lerp(grow, x0, cx - dx), y1 = Mth.lerp(grow, y0, cy + r);
                line(g, x0, y0, x1, y1, s * .22f, 0xFFB0101A, .55f * a * pulse);
                line(g, x0, y0, x1, y1, s * .09f, 0xFFFF2A2A, .95f * a);
                line(g, x0, y0, x1, y1, s * .03f, 0xFFFFE8E0, a);
            }
            glowDisc(g, cx, cy, s * .7f, 0xFFFF2030, .25f * a * pulse * stroke2);
            // Embers lifting off it while it burns.
            if (st > 22 && st < 48) for (int i = 0; i < 8; i++) {
                float life = ((st - 22) * .08f + hash(i)) % 1;
                float ex = cx + (hash(i + 2) - .5f) * s * .9f, ey = cy + s * .4f - life * s * 1.2f;
                glowDisc(g, ex, ey, s * .04f, 0xFFFF5030, .8f * (1 - life));
            }
            // It bursts: a flash, shards of red flung out, a ring.
            if (st > 48) {
                float b = (st - 48) / 8f;
                glowDisc(g, cx, cy, s * 1.4f, 0xFFFF3030, .8f * (1 - b));
                for (int i = 0; i < 10; i++) {
                    float ang = i * .63f + hash(i) * .3f, d = s * (.3f + 1.5f * easeOut(b) * (.6f + .5f * hash(i + 7)));
                    float sx = cx + Mth.cos(ang) * d, sy = cy + Mth.sin(ang) * d * .8f;
                    line(g, sx, sy, sx + Mth.cos(ang) * s * .25f, sy + Mth.sin(ang) * s * .2f, s * .05f, 0xFFFF3A30, 1 - b);
                }
                ringFill(g, cx, cy, s * (.4f + 1.4f * easeOut(b)), s * (.3f + 1.1f * easeOut(b)), s * .08f, 0xFFFF2A2A, .7f * (1 - b));
            }
            normal();
        }
        // The smoke he steps out of.
        if (st > 44 && st < 56) {
            float k = (st - 44) / 12f;
            for (int i = 0; i < 9; i++) {
                float ang = i * .7f;
                blob(g, px + Mth.cos(ang) * s * (.3f + .6f * k), py - s * (.4f + .7f * hash(i)) + Mth.sin(ang) * s * .1f, s * (.25f + .1f * hash(i + 2)), 0xFF07050A, .5f * (1 - k));
            }
        }
    }
    /** One of Zed's shadow copies, mid-lunge, drawn with his own body in shadow. */
    private static void zedShadow(GuiGraphics g, float x, float y, float s, float yaw, float time, float alpha) {
        if (alpha <= .01f) return;
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(x, y, -200);
        p.mulPoseMatrix(new Matrix4f().scaling(s, s, -s));
        p.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        p.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        p.scale(-.9375f, -.9375f, .9375f);
        p.translate(0, -1.501, 0);
        Lighting.setupForEntityInInventory();
        try {
            ZedBody.draw(p, g.bufferSource(), 15728880, ZedMotion.dash(time), 0, 0, 0, 0, time, ZedBody.SHADOW, alpha);
            g.flush();
        } finally {
            p.popPose();
            Lighting.setupFor3DItems();
        }
    }

    // ------------------------------------------------------------------ Black Panther
    /**
     * Black Panther: four claw trails with every strike (eight for the double, rising for the uppercut);
     * then the suit's energy gathering round him and bursting out as a sphere with its Wakandan lattice.
     */
    private void panther(GuiGraphics g, float st, float px, float py, float s, int fx0, int fy0, int fx1, int fy1) {
        light();
        float[][] strikes = {{3, -1}, {12, 1}, {22, 0}, {34, 2}};
        for (float[] k : strikes) {
            float age = st - k[0] + 1.5f;
            if (age < 0 || age > 5) continue;
            float draw = easeOut(age / 2f), a = 1 - Mth.clamp((age - 1.5f) / 3.5f, 0, 1);
            int hands = k[1] == 0 ? 2 : 1;
            for (int h = 0; h < hands; h++) {
                float sgn = k[1] == 0 ? (h == 0 ? 1 : -1) : k[1];
                float cx = px + (k[1] == 0 ? sgn * s * .25f : 0), cy = py - s * 1.1f;
                float x0, y0, x1, y1;
                if (k[1] == 2) { x0 = cx - s * .2f; y0 = py - s * .4f; x1 = cx + s * .15f; y1 = py - s * 2.2f; }
                else { x0 = cx + sgn * s * .8f; y0 = cy - s * .65f; x1 = cx - sgn * s * .7f; y1 = cy + s * .55f; }
                float nx = -(y1 - y0), ny = x1 - x0, len = (float) Math.hypot(nx, ny);
                nx = nx / len * s * .09f; ny = ny / len * s * .09f;
                for (int i = 0; i < 4; i++) {
                    float o = i - 1.5f;
                    float ax = x0 + nx * o, ay = y0 + ny * o, bx = Mth.lerp(draw, x0, x1) + nx * o, by = Mth.lerp(draw, y0, y1) + ny * o;
                    line(g, ax, ay, bx, by, s * .06f, 0xFF9A5CFF, .5f * a);
                    line(g, ax, ay, bx, by, s * .018f, 0xFFF0EEFF, .95f * a);
                }
            }
        }
        // The charge: light running up him, the eyes and the lines brightening, a tremble of violet round him.
        if (st > 44 && st < 59) {
            float k = (st - 44) / 14f;
            glowDisc(g, px, py - s * 1.1f, s * (.6f + .8f * k), 0xFF8A4CFF, .35f * k);
            for (int i = 0; i < 6; i++) {
                float ang = i * 1.047f + st * .2f, r = s * (1.2f - .7f * k);
                float x = px + Mth.cos(ang) * r, y = py - s * 1.1f + Mth.sin(ang) * r * .9f;
                line(g, x, y, Mth.lerp(.35f, x, px), Mth.lerp(.35f, y, py - s * 1.1f), s * .025f, 0xFFC0A0FF, .6f * k);
            }
        }
        // The release: a white core, the sphere racing out, its lattice, a ring along the ground.
        if (st > 58 && st < 74) {
            float age = st - 58, out = easeOut(Math.min(1, age / 6f)), fade = age < 6 ? 1 : 1 - (age - 6) / 10f;
            float r = s * (.5f + 2.6f * out), cx = px, cy = py - s * 1.1f;
            if (age < 3) glowDisc(g, cx, cy, s * 1.6f, 0xFFFFFFFF, .9f * (1 - age / 3));
            glowDisc(g, cx, cy, r, 0xFF7A3CFF, .25f * fade);
            ringFill(g, cx, cy, r, r, s * .14f, 0xFF9A5CFF, .7f * fade);
            ringFill(g, cx, cy, r, r, s * .04f, 0xFFFFFFFF, .6f * fade);
            for (int i = 0; i < 12; i++) {
                float a0 = i * .5236f + age * .02f, a1 = a0 + .5236f;
                float ix = cx + Mth.cos(a0) * r * .62f, iy = cy + Mth.sin(a0) * r * .62f;
                line(g, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, ix, iy, s * .02f, 0xFFD0C0FF, .5f * fade);
                line(g, ix, iy, cx + Mth.cos(a1) * r * .62f, cy + Mth.sin(a1) * r * .62f, s * .02f, 0xFFD0C0FF, .45f * fade);
                line(g, ix, iy, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, s * .015f, 0xFFB090FF, .35f * fade);
            }
            ringFill(g, px, py - 2, r * 1.1f, r * .22f, s * .12f, 0xFF9A5CFF, .6f * fade);
            if (age < 4) g.fill(fx0, fy0, fx1, fy1, alphaOf(0xFFE8DDFF, .3f * (1 - age / 4)));
        }
        normal();
    }

    // ------------------------------------------------------------------ Thor
    /** Where the thrown hammer is (x, y) at a moment of the show, and how far it has spun. */
    private static float[] hammerAt(float st, float px, float py, float s, int fx0) {
        float rx = px - s * .55f, ry = py - s * 1.8f;          // where it leaves his hand
        float cx = px - s * .42f, cy = py - s * 1.2f;          // where he catches it
        float far = fx0 - s * .9f, high = py - s * 2.1f;
        float x, y;
        if (st < 48) {
            float k = easeOut((st - 40) / 8f);
            x = Mth.lerp(k, rx, far); y = Mth.lerp(k, ry, high);
        } else {
            float k = Mth.clamp((st - 48) / 8f, 0, 1);
            k = k * k;
            x = Mth.lerp(k, far, cx); y = Mth.lerp(k, high, cy) - Mth.sin(Mth.PI * k) * s * .3f;
        }
        return new float[]{x, y, (st - 40) * 1.25f};
    }
    /**
     * Thor: Mjolnir whirled up to a blur at his side, thrown out of the frame trailing lightning and
     * caught on its way back; two blows, each with its crack of light; then he looks out of the frame
     * and the storm comes into his eyes.
     */
    private void thor(GuiGraphics g, float st, float px, float py, float s, int fx0, int fy0, int fx1, int fy1, float partial) {
        float time = actor.tickCount + partial;
        light();
        // The whirl: wind round it, and once it is at full speed, sparks thrown off the wheel.
        if (st > 4 && st < 38) {
            float k = span(st, 4, 30), hx = px - s * .5f, hy = py - s * 1.0f;
            for (int i = 0; i < 3; i++) {
                float start = st * (.6f + .2f * k) + i * 2.1f;
                arc(g, hx, hy, s * (.5f + .1f * i), s * (.5f + .1f * i), start, start + 1.4f, s * .03f, 0xFFDDE8FF, .35f * k);
            }
            if (st > 22) for (int i = 0; i < 2; i++) {
                int seed = (int) (st * 2) + i * 13;
                float ang = hash(seed) * Mth.TWO_PI, r = s * .5f;
                float ax = hx + Mth.cos(ang) * r, ay = hy + Mth.sin(ang) * r;
                bolt(g, ax, ay, ax + Mth.cos(ang) * s * .35f, ay + Mth.sin(ang) * s * .35f, seed, s * .025f, 0xFFBFE0FF, .9f);
            }
        }
        // The throw: a flash where it leaves his hand.
        if (st > 39 && st < 45) glowDisc(g, px - s * .55f, py - s * 1.8f, s * .6f, 0xFFDDEEFF, .7f * (1 - (st - 39) / 6));
        normal();
        // In flight: the hammer itself, tumbling, with lightning trailing it.
        if (st >= 40 && st < 56.5f) {
            light();
            float[] last = null;
            for (int j = 0; j <= 10; j++) {
                float at = st - j * .5f;
                if (at < 40) break;
                float[] h = hammerAt(at, px, py, s, fx0);
                if (last != null) {
                    float a = 1 - j / 10f;
                    line(g, last[0], last[1], h[0], h[1], s * .14f * a, 0xFF6FA8FF, .35f * a);
                    line(g, last[0], last[1], h[0], h[1], s * .04f * a, 0xFFFFFFFF, .8f * a);
                }
                last = h;
            }
            float[] h = hammerAt(st, px, py, s, fx0);
            glowDisc(g, h[0], h[1], s * .5f, 0xFF8CC8FF, .4f);
            int seed = (int) (st * 3);
            bolt(g, h[0], h[1], h[0] + (hash(seed) - .5f) * s * .8f, h[1] + (hash(seed + 1) - .5f) * s * .8f, seed, s * .02f, 0xFFDDEEFF, .9f);
            normal();
            hammer(g, h[0], h[1], s, h[2], time);
        }
        light();
        // The catch.
        if (st > 56 && st < 63) {
            float k = (st - 56) / 7f, hx = px - s * .42f, hy = py - s * 1.2f;
            glowDisc(g, hx, hy, s * .7f, 0xFFDDEEFF, .8f * (1 - k));
            for (int i = 0; i < 8; i++) {
                float ang = i * .785f + .3f, d = s * (.15f + .5f * easeOut(k));
                line(g, hx + Mth.cos(ang) * d, hy + Mth.sin(ang) * d, hx + Mth.cos(ang) * (d + s * .15f), hy + Mth.sin(ang) * (d + s * .15f), s * .03f, 0xFFFFF4D8, 1 - k);
            }
        }
        // Two blows: a crescent of light the hammer head draws, a crack where it lands.
        for (int b = 0; b < 2; b++) {
            float hit = b == 0 ? 70 : 82, age = st - hit;
            if (age < -3 || age > 6) continue;
            float draw = easeOut((age + 3) / 4f), fade = 1 - Mth.clamp(age / 6f, 0, 1);
            float a0 = b == 0 ? Mth.PI * 1.05f : -.05f, a1 = b == 0 ? -.05f : Mth.PI * 1.05f;
            float end = Mth.lerp(draw, a0, a1);
            arc(g, px, py - s * 1.05f, s * 1.0f, s * .35f, Math.min(a0, end), Math.max(a0, end), s * .12f, 0xFF7FB8FF, .6f * fade);
            arc(g, px, py - s * 1.05f, s * 1.0f, s * .35f, Math.min(a0, end), Math.max(a0, end), s * .035f, 0xFFFFFFFF, .9f * fade);
            if (age >= 0) {
                float hx = px + (b == 0 ? s * .9f : -s * .9f), hy = py - s * 1.0f;
                glowDisc(g, hx, hy, s * .8f, 0xFFBFE0FF, .8f * fade);
                for (int i = 0; i < 3; i++) {
                    int seed = (int) hit * 7 + i;
                    bolt(g, hx, hy, hx + (b == 0 ? 1 : -1) * s * (.4f + .5f * hash(seed)), hy + (hash(seed + 1) - .5f) * s * .9f, seed, s * .025f, 0xFFDDEEFF, fade);
                }
            }
        }
        // The look: his own eyes light up (the layer draws them); the bolt behind him lights the whole frame for a moment.
        if (st > 92) {
            float age = st - 98;
            if (age >= 0 && age < 5) g.fill(fx0, fy0, fx1, fy1, alphaOf(0xFFDDEEFF, .35f * (1 - age / 5)));
        }
        normal();
    }
    /** Mjolnir itself, tumbling end over end in the frame. */
    private static void hammer(GuiGraphics g, float x, float y, float s, float spin, float time) {
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(x, y, 0);
        p.scale(s, s, s);
        p.mulPose(Axis.ZP.rotation(spin));
        p.mulPose(Axis.YP.rotation(.6f));
        p.translate(0, -6 / 16f, 0);
        Lighting.setupForEntityInInventory();
        try {
            RenderSystem.runAsFancy(() -> Mjolnir.draw(p, g.bufferSource(), Mjolnir.FULL_BRIGHT, 1, time));
            g.flush();
        } finally {
            p.popPose();
            Lighting.setupFor3DItems();
        }
    }

    // ------------------------------------------------------------------ Magneto
    private static float mhash(int n) { double x = Math.sin(n * 12.9898 + 78.233) * 43758.5453; return (float) (x - Math.floor(x)); }
    /**
     * Magneto's metal, in 3D round the body (same space as the body: blocks from his feet, -z toward the screen): scrap
     * rising off the floor into an orbit, three rods called down into the floor in front of him, the shield's plates
     * coming up out of the floor round him, then flung outward when it bursts.
     */
    private static void magnetoMetal(GuiGraphics g, float st, float x, float y, float z, float s, float partial) {
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(x, y, z);
        p.mulPoseMatrix(new Matrix4f().scaling(s, s, -s));
        p.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        Lighting.setupForEntityInInventory();
        int light = 15728880;
        try {
            var buffers = g.bufferSource();
            var v = com.FIRNI.superheromod.client.render.magneto.MetalMesh.buffer(buffers);
            // The scrap: up off the floor, round him, faster as he works; it falls away when the shield takes over.
            float gone = span(st, 34, 40);
            for (int i = 0; i < 10 && gone < 1; i++) {
                float rise = easeOut((st - i * .8f) / 12f), a = mhash(i * 3) * 6.283f + st * (.06f + .03f * mhash(i * 5)) * (1 + 2 * span(st, 14, 24));
                float r = 1.2f + .7f * mhash(i * 7), h = Mth.lerp(rise, .05f, .5f + 1.9f * mhash(i * 11)) - gone * 2;
                p.pushPose();
                p.translate(Mth.cos(a) * r, h, Mth.sin(a) * r);
                p.mulPose(Axis.XP.rotation(st * .1f + i)); p.mulPose(Axis.ZP.rotation(st * .07f + i * 2));
                if (i % 3 == 0) com.FIRNI.superheromod.client.render.magneto.MetalMesh.rod(p, v, light, .9f, i);
                else com.FIRNI.superheromod.client.render.magneto.MetalMesh.scrap(p, v, light, i * 13, .3f);
                p.popPose();
            }
            // The rods: down out of the sky, one after another, stuck slanting in the floor in front of him.
            float[][] rods = {{-1.5f, -1.1f, 22}, {1.6f, -.9f, 25}, {.2f, -1.9f, 28}};
            for (int i = 0; i < 3; i++) {
                float k = Mth.clamp((st - rods[i][2] + 6) / 6f, 0, 1);
                if (k <= 0 || st > 70) continue;
                float fall = 1 - k * k, sink = 1 - span(st, 62, 70);
                p.pushPose();
                p.translate(rods[i][0] + fall * .8f, .9f + fall * 7 - (1 - sink) * 2.2f, rods[i][1]);
                p.mulPose(Axis.ZP.rotation(.25f * (i % 2 == 0 ? 1 : -1)));
                p.mulPose(Axis.XP.rotation(-.15f));
                p.mulPose(Axis.YP.rotation(st * (1 - k) * .4f + i));
                p.mulPose(Axis.XP.rotation((float) Math.PI));
                com.FIRNI.superheromod.client.render.magneto.MetalMesh.rod(p, v, light, 2.6f, i * 3);
                p.popPose();
            }
            // The plates: up out of the floor round him, circling; at the burst, thrown outward tumbling.
            if (st >= 36 && st < 68) {
                for (int i = 0; i < 8; i++) {
                    float age = st - 36, a = 6.283f * i / 8 + age * .05f;
                    float up = easeOut((age - i * .6f) / 10f), out = st > 54 ? (st - 54) * .55f : 0;
                    float r = 1.55f + out, h = Mth.lerp(up, -1.4f, .55f + (i % 2) * .9f) + (st > 54 ? (st - 54) * .08f : 0);
                    p.pushPose();
                    p.translate(Mth.cos(a) * r, h, Mth.sin(a) * r);
                    p.mulPose(Axis.YP.rotation((float) Math.PI / 2 - a));
                    p.mulPose(Axis.XP.rotation(-.18f + (1 - up) * 2 + out * .5f));
                    p.mulPose(Axis.ZP.rotation(out * .7f * (i % 2 == 0 ? 1 : -1)));
                    com.FIRNI.superheromod.client.render.magneto.MetalMesh.plate(p, v, light, .8f, 1.1f, i);
                    p.popPose();
                }
            }
            g.flush();
        } finally {
            p.popPose();
            Lighting.setupFor3DItems();
        }
    }
    /** In front: the violet sphere of the shield (bright rim, faint body, a crawl of lightning), its pop, the rods' dust. */
    private void magnetoLight(GuiGraphics g, float st, float px, float py, float s) {
        float cy = py - s * 1.05f;
        light();
        // Each rod striking the floor: a flash and dust.
        for (float hit : new float[]{24, 27, 30}) {
            float a = 1 - Mth.clamp((st - hit) / 10f, 0, 1);
            if (st >= hit && a > 0) glowDisc(g, px + (hit == 24 ? 1.5f : hit == 27 ? -1.6f : -.2f) * s, py - 2, s * .7f, 0xFFE0D8FF, .5f * a);
        }
        float shield = span(st, 36, 44) * (1 - span(st, 53, 55));
        if (shield > .01f) {
            float r = s * (1.5f + .05f * Mth.sin(st * .4f)) * (.3f + .7f * easeOut((st - 36) / 8f));
            glowDisc(g, px, cy, r, 0xFF6A30D0, .16f * shield);
            ringFill(g, px, cy, r, r, s * .1f, 0xFFC9A6FF, .45f * shield);
            ringFill(g, px, cy, r * .96f, r * .96f, s * .03f, 0xFFF4ECFF, .5f * shield);
            for (int i = 0; i < 3; i++) {
                int slot = (int) (st / 3) + i * 17;
                double a0 = mhash(slot) * 6.283, a1 = a0 + .6 + mhash(slot + 1);
                bolt(g, px + (float) Math.cos(a0) * r * .95f, cy + (float) Math.sin(a0) * r * .95f, px + (float) Math.cos(a1) * r * .7f, cy + (float) Math.sin(a1) * r * .7f, slot, 1.2f, 0xFFE8D8FF, .7f * shield);
            }
        }
        float pop = st - 54;
        if (pop >= 0 && pop < 8) {
            float k = pop / 8;
            ringFill(g, px, cy, s * (1.5f + 1.6f * k), s * (1.5f + 1.6f * k), s * .12f * (1 - k), 0xFFC9A6FF, .7f * (1 - k));
            glowDisc(g, px, cy, s * 2.2f, 0xFFFFFFFF, .35f * (1 - k));
        }
        normal();
    }

    // ------------------------------------------------------------------ Hulk
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

    // ------------------------------------------------------------------ Cyclops
    /** Where the beam leaves: between his eyes, following the way he faces and looks. */
    private float[] eyes(float px, float py, float s) {
        float turn = (180 - faceYaw) * Mth.DEG_TO_RAD, look = facePitch * Mth.DEG_TO_RAD;
        return new float[]{px + Mth.sin(turn) * .25f * s * Mth.cos(look), py - s * 1.53f + Mth.sin(look) * .2f * s};
    }
    /**
     * Cyclops: hand to the visor; the beam sweeps across the floor to the right, scorching a line into
     * it; he turns, and the right-click beam pours out to the left, thickening as it holds, shock
     * rings running down it, bursting where it leaves the frame.
     */
    private void cyclops(GuiGraphics g, float st, float px, float py, float s, int fx0, int fx1) {
        float[] e = eyes(px, py, s);
        float ex = e[0], ey = e[1];
        light();
        float charge = span(st, 3, 7) * (1 - span(st, 32, 37)) + span(st, 37, 41) * (1 - span(st, 66, 72));
        glowDisc(g, ex, ey, s * .22f + s * .06f * Mth.sin(st * 1.3f), 0xFFFF3040, .9f * charge);
        // The sweep to the right, down across the floor.
        if (st > 7 && st < 36) {
            float k = span(st, 7, 9) * (1 - span(st, 32, 35));
            float[] end = sweepEnd(st, ex, ey, py, fx1);
            float w = s * .08f * (1 + .15f * Mth.sin(st * 2.3f)) * k;
            line(g, ex, ey, end[0], end[1], w * 3.2f, 0xFFFF1A28, .35f * k);
            line(g, ex, ey, end[0], end[1], w * 1.6f, 0xFFFF3A40, .8f * k);
            line(g, ex, ey, end[0], end[1], w * .5f, 0xFFFFE8E0, k);
            glowDisc(g, end[0], end[1], s * .45f, 0xFFFF5030, .6f * k);
            // Where it touches the floor: the scorch it leaves, still glowing behind it, and sparks.
            if (end[2] > 0) for (int i = 0; i < 10; i++) {
                float h = hash(i + (int) (st * 3)), sx = end[0] + (h - .5f) * s * .2f, sy = end[1] - 2;
                line(g, sx, sy, sx - s * (.1f + .3f * h), sy - s * (.15f + .35f * hash(i * 3 + (int) st)), 1, 0xFFFFC0A0, .9f * k);
            }
        }
        if (st > 14) for (int j = 0; j < 30; j++) {
            float at = 14 + j * .8f;
            if (at > Math.min(st, 33)) break;
            float[] mark = sweepEnd(at, px + s * .16f, py - s * 1.53f, py, fx1);
            if (mark[2] <= 0) continue;
            float cool = 1 - Mth.clamp((st - at) / 40f, 0, 1);
            glowDisc(g, mark[0], py - 2, s * .12f, 0xFFFF6A20, .7f * cool);
        }
        // The right-click beam, out to the left.
        if (st > 39 && st < 72) {
            float k = span(st, 39, 42) * (1 - span(st, 66, 71));
            float grow = .5f + span(st, 42, 62);
            float tx = fx0 - 6, ty = ey + (ex - tx) * .07f;
            float w = s * .075f * grow * (1 + .12f * Mth.sin(st * 2.7f)) * k;
            line(g, ex, ey, tx, ty, w * 3.6f, 0xFFFF1A28, .35f * k);
            line(g, ex, ey, tx, ty, w * 1.7f, 0xFFFF3A40, .85f * k);
            line(g, ex, ey, tx, ty, w * .55f, 0xFFFFF0E8, k);
            // Shock rings running down it.
            for (int i = 0; i < 4; i++) {
                float run = ((st - 40) / 6f + i * .25f) % 1;
                float rx = Mth.lerp(run, ex, tx), ry = Mth.lerp(run, ey, ty);
                arc(g, rx, ry, s * .05f * grow, w * 2.4f, -Mth.HALF_PI, Mth.HALF_PI * 3, s * .02f, 0xFFFFB0A0, .7f * k * (1 - run));
            }
            float pulse = .8f + .2f * Mth.sin(st * 1.9f);
            glowDisc(g, tx + 4, ty, s * .7f * grow, 0xFFFF4030, .6f * k * pulse);
            for (int i = 0; i < 12; i++) {
                float h = hash(i + (int) (st * 3)), sx = tx + 4 + h * 10, sy = ty + (hash(i * 3 + (int) st) - .5f) * s * .7f * grow;
                line(g, sx, sy, sx + s * .25f * h, sy + (h - .5f) * s * .3f, 1, 0xFFFFC0A0, .8f * k);
            }
        }
        normal();
    }
    /** Where the sweeping beam ends: the frame's right edge, or the floor; [2] is 1 on the floor. */
    private static float[] sweepEnd(float st, float ex, float ey, float py, int fx1) {
        float a = sweep(st) * Mth.DEG_TO_RAD, c = Mth.cos(a), sn = Mth.sin(a);
        float toEdge = (fx1 + 6 - ex) / Math.max(.05f, c);
        float toFloor = sn > .01f ? (py - 2 - ey) / sn : Float.MAX_VALUE;
        float t = Math.min(toEdge, toFloor);
        return new float[]{ex + c * t, ey + sn * t, toFloor < toEdge ? 1 : 0};
    }

    // ------------------------------------------------------------------ Sandman
    /** The soldiers either side of him and the giant behind: where, how big, when each starts to rise. */
    private static final float[][] ARMY = {
            // x (in blocks from him), scale, born (ticks), feet raised (blocks), depth (z)
            {-1.05f, .92f, 6, 0, 80}, {1.05f, .92f, 9, 0, 80}, {-1.85f, .8f, 13, .1f, 60}, {1.85f, .8f, 16, .1f, 60}};
    private static final float GIANT_BORN = 24, GIANT_RISEN = 58, GIANT_SCALE = 1.35f;

    /** The soldiers rising out of the ground either side of him, and the giant rising behind him. */
    private void army(GuiGraphics g, float st, float px, float py, float s, int fx0, int fy0, int fx1, float partial) {
        var model = soldierModel();
        float idle = (actor.tickCount + partial) / 20f;
        // Nothing of them shows below the ground line while they come up out of it.
        g.enableScissor(fx0, fy0, fx1, (int) py + 1);
        try {
            if (st > GIANT_BORN) {
                if (st < GIANT_RISEN) model.filmPose(idle, 0, 0, "spawn", (st - GIANT_BORN) / (GIANT_RISEN - GIANT_BORN) * 1.2f, 1.12f);
                else {
                    // Risen: chest up, arms thrown wide, a roar over his head.
                    model.filmPose(idle, 0, 0, null, 0, 1.12f);
                    float up = span(st, GIANT_RISEN, GIANT_RISEN + 10);
                    for (String side : new String[]{"right", "left"}) {
                        model.bone(side + "_upper_arm").zRot += (side.equals("right") ? 1.05f : -1.05f) * up;
                        model.bone(side + "_upper_arm").xRot += -.35f * up;
                        model.bone(side + "_forearm").xRot += -.5f * up;
                    }
                    model.bone("chest").xRot += -.18f * up;
                    model.bone("head").xRot += -.25f * up;
                }
                soldier(g, model, px, py - s * .3f, 20, s, GIANT_SCALE, 180, .78f, 1);
            }
            for (float[] a : ARMY) {
                float born = a[2];
                if (st < born) continue;
                if (st < born + 24) model.filmPose(idle, 0, 0, "spawn", (st - born) / 20f, .94f);
                else model.filmPose(idle + a[0], 0, 0, null, 0, .94f);
                float yaw = Mth.lerp(span(st, length("sandman") - 16, length("sandman") - 8), 180 - a[0] * 8, 180);
                soldier(g, model, px + a[0] * s, py - a[3] * s, a[4], s, a[1], yaw, a[3] > 0 ? .86f : .95f, 1);
            }
        } finally {
            g.disableScissor();
        }
    }
    /** Sand thrown up where each of them breaks out of the ground, and pouring off the giant as it rises. */
    private void risings(GuiGraphics g, float st, float px, float py, float s) {
        for (float[] a : ARMY) {
            float k = (st - a[2]) / 14f;
            if (k < 0 || k > 1) continue;
            float x = px + a[0] * s, y = py - a[3] * s;
            for (int i = 0; i < 18; i++) {
                float h = hash(i + a[2] * 7), vx = (h - .5f) * s * 1.1f, vy = s * (.6f + .9f * hash(i + 3 + a[2]));
                float gx = x + vx * k, gy = y - vy * k + s * 1.4f * k * k, size = 1 + 1.5f * hash(i * 1.3f);
                g.fill((int) gx, (int) gy, (int) (gx + size), (int) (gy + size), alphaOf(h > .5f ? 0xFFE8C080 : 0xFFC8984E, 1 - k));
            }
            blob(g, x, y - s * .15f, s * .55f * easeOut(k), 0xFFD8B070, .35f * (1 - k));
        }
        if (st > GIANT_BORN && st < GIANT_RISEN + 14) {
            float pour = span(st, GIANT_BORN, GIANT_BORN + 6) * (1 - span(st, GIANT_RISEN, GIANT_RISEN + 14));
            for (int i = 0; i < 30; i++) {
                float fall = (hash(i) + st * .05f) % 1;
                float x = px + (hash(i + 3) - .5f) * s * 1.6f, top = py - s * (1.2f + 1.6f * hash(i + 7));
                float y = Mth.lerp(fall, top, py);
                g.fill((int) x, (int) y, (int) x + 1, (int) (y + 3), alphaOf(0xFFD8B070, .5f * pour * (1 - fall)));
            }
        }
    }
    private SandSoldierModel<SandSoldierEntity> soldierModel() {
        if (soldier == null) soldier = new SandSoldierModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(SandSoldierModel.LAYER));
        return soldier;
    }
    /** The shared soldier model as it is posed now, standing at (x, y) in the frame. */
    private static void soldier(GuiGraphics g, SandSoldierModel<SandSoldierEntity> model, float x, float y, float z, float s, float scale, float yaw, float tint, float alpha) {
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(x, y, z);
        p.mulPoseMatrix(new Matrix4f().scaling(s, s, -s));
        p.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        p.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        p.scale(-scale, -scale, scale);
        p.translate(0, -1.501, 0);
        Lighting.setupForEntityInInventory();
        try {
            model.renderToBuffer(p, g.bufferSource().getBuffer(RenderType.entityTranslucent(SAND)), 15728880, OverlayTexture.NO_OVERLAY,
                    tint, tint * .93f, tint * .8f, alpha);
            g.flush();
        } finally {
            p.popPose();
            Lighting.setupFor3DItems();
        }
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
    }

    // ------------------------------------------------------------------ drawing an entity in the frame
    /**
     * An entity standing at (x, y) in the GUI at depth z, scale pixels per block, its body facing yaw and
     * its head headYaw, offset (blocks, world axes).
     */
    private static void body(GuiGraphics g, Entity e, float x, float y, float z, float scale, float yaw, float headYaw, float headPitch, Vec3 offset, float partial) {
        if (e instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = yaw;
            living.yHeadRot = living.yHeadRotO = headYaw;
        }
        e.setYRot(yaw); e.yRotO = yaw; e.setXRot(headPitch); e.xRotO = headPitch;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        g.pose().pushPose();
        g.pose().translate(x, y, z);
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
    private static void glowDisc(GuiGraphics g, float x, float y, float r, int c, float a) { oval(g, x, y, r, r, c, a); }
    /** A soft oval: full in the middle, nothing at the edge. */
    private static void oval(GuiGraphics g, float x, float y, float rx, float ry, int c, float a) {
        if (a <= .01f || rx <= .5f || ry <= .2f) return;
        BufferBuilder b = begin(VertexFormat.Mode.TRIANGLES);
        Matrix4f m = g.pose().last().pose();
        int n = 28;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            v(b, m, x, y, c, a); v(b, m, x + Mth.cos(a1) * rx, y + Mth.sin(a1) * ry, c, 0); v(b, m, x + Mth.cos(a0) * rx, y + Mth.sin(a0) * ry, c, 0);
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
}
