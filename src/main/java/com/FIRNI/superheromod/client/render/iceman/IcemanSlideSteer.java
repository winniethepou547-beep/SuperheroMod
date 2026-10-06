package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * His own client steers his body through the two slides (the server decides when they start and end and who they
 * touch), and gives his view its small touches while he rides.
 * <p>
 * The 3D ice slide (SHIFT held, IcemanClient.shiftHeld(): the key or the test autopilot): it starts on the press itself
 * (predicted; dropped if the server does not take it up). For SLIDE_PREP ticks he only gets ready (the ice forming);
 * then a push-off, and the speed builds by SLIDE_ACCEL to SLIDE_SPEED along the mouse's way (turned to smoothly, a sharp
 * turn costing a little speed: SLIDE_TURN_LOSS), gaining on the way down (SLIDE_GRAVITY times the slope) and losing
 * some climbing. The look's pitch is never followed: SPACE held (IcemanClient.spaceHeld(), never the vanilla jump while
 * he rides) raises the track's pitch by SLIDE_PITCH_RATE a tick up to SLIDE_ASCENT_MAX; let go, it bends over a short
 * crest and down (SLIDE_PITCH_FALL a tick, to -SLIDE_DESCENT_MAX) until it meets the ground and runs along it. No
 * gravity while he rides (the velocity is his own every tick); a step in the way ramps him over it; a ceiling flattens
 * the climb.
 * <p>
 * Let go: on the ground he brakes over SLIDE_END_TICKS (the speed bleeding away, the walk handed back gradually); in the
 * air he leaves the ice with a last push along it (keeping part of the speed, a descent's included) and falls as usual.
 * <p>
 * His view: FOV a little wider with speed, climbing and diving; a slight upward tilt rising, a soft downward tilt
 * falling, a very slight lag in sharp turns (third person), thin streaks at the screen's edges at speed; everything eases
 * back when he stops. The camera stays his normal gameplay camera and the controls stay his.
 * <p>
 * The sub-zero slide (CTRL): DASH_DIST over DASH_TICKS along IcemanClient.dashYaw(), a fast start easing out, hugging the
 * ground, no jumping.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanSlideSteer {
    private IcemanSlideSteer() {}

    /** Riding (predicted or confirmed), since when; the key last tick. */
    private static boolean riding, shiftWas;
    private static long rideStart = -100;
    /** Speed (blocks a tick), heading (radians, world yaw), the track's pitch (radians, positive up), the ground's slope under him. */
    private static float speed, heading, pitch, slope, turnRate;
    private static boolean grounded;
    private static Vec3 lastPos;
    /** The way out: 0 none, 1 braking on the ground, 2 leaving the ice in the air; since when, how fast, which way. */
    private static int exitKind;
    private static long exitStart = -100;
    private static float exitSpeed;
    private static Vec3 exitDir = Vec3.ZERO;
    /** The sub-zero slide's distance per tick (fast start, easing out), worked out once. */
    private static final float[] STEP = new float[DASH_TICKS];
    static {
        float sum = 0;
        for (int i = 0; i < DASH_TICKS; i++) { STEP[i] = (float) Math.pow(1 - i / (float) DASH_TICKS, 1.5) + .08f; sum += STEP[i]; }
        for (int i = 0; i < DASH_TICKS; i++) STEP[i] *= DASH_DIST / sum;
    }

    // ------------------------------------------------------------------ for the effects and the poses
    /** His own ice slide is on (predicted or confirmed), getting ready included. */
    public static boolean riding() { return riding; }
    /** Ticks since his own slide began (getting ready the first SLIDE_PREP), or -1. */
    public static float rideAge() { var mc = Minecraft.getInstance(); return !riding || mc.level == null ? -1 : mc.level.getGameTime() - rideStart; }
    /** How fast his own slide is (0..1 of SLIDE_SPEED, a little over going down). */
    public static float speed() { return riding ? speed / SLIDE_SPEED : 0; }
    /** The track's pitch under him (radians, positive up; 0 on the ground). */
    public static float pitch() { return riding ? pitch : 0; }
    /** How fast his own slide is turning (radians a tick, positive to his right). */
    public static float turn() { return riding ? turnRate : 0; }
    /** His own way out of the slide: 0 none, 1 braking on the ground, 2 off the ice in the air; and its age. */
    public static int exitKind() { return exitKind; }
    public static float exitAge() { var mc = Minecraft.getInstance(); return mc.level == null ? 99 : mc.level.getGameTime() - exitStart; }
    /** The sub-zero slide is moving him now (his own client). */
    public static boolean dashing() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        long dt = mc.level.getGameTime() - IcemanClient.dashStart();
        if (dt < 0 || dt >= DASH_TICKS) return false;
        IcemanClient.State s = mc.player == null ? null : IcemanClient.get(mc.player);
        return dt < 4 || s != null && s.action == DASH;
    }

    // ------------------------------------------------------------------ steering
    @SubscribeEvent public static void steer(MovementInputUpdateEvent e) {
        var mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || e.getEntity() != p || mc.level == null) return;
        if (!IcemanClient.isHero(p) || FilmDirector.playing() || !p.isAlive()) { riding = false; shiftWas = false; exitKind = 0; return; }
        IcemanClient.State s = IcemanClient.get(p);
        long now = mc.level.getGameTime();
        var input = e.getInput();
        Vec3 v = p.getDeltaMovement();
        // The ground's slope under him, from how he really moved last tick.
        Vec3 pos = p.position();
        if (lastPos != null) {
            double fx = pos.x - lastPos.x, fz = pos.z - lastPos.z, fl = Math.sqrt(fx * fx + fz * fz);
            if (fl > .05) slope = Mth.lerp(.5f, slope, (float) Math.atan2(pos.y - lastPos.y, fl));
        }
        lastPos = pos;

        // ---- the sub-zero slide
        long dt = now - IcemanClient.dashStart();
        if (dt >= 0 && dt < DASH_TICKS && (dt < 4 || s != null && s.action == DASH)) {
            riding = false; exitKind = 0;
            float yaw = IcemanClient.dashYaw();
            double step = STEP[(int) dt];
            double vy = p.onGround() ? (dt == 0 ? -.05 : -.1) : Math.min(v.y, 0);
            p.setDeltaMovement(-Mth.sin(yaw) * step, vy, Mth.cos(yaw) * step);
            p.fallDistance = 0;
            input.forwardImpulse = input.leftImpulse = 0; input.jumping = false; input.shiftKeyDown = false;
            shiftWas = IcemanClient.shiftHeld();
            return;
        }

        // ---- the ice slide: predicted on the press, kept while the server agrees and SHIFT is held
        boolean key = IcemanClient.shiftHeld() && mc.screen == null && !IcemanClient.wheelOpen();
        boolean server = s != null && s.action == SLIDE;
        boolean may = s == null || (s.action == IDLE || s.action == SLIDE || s.action == SLIDE_END || s.action == BRUSH || s.action == FORM || s.action == WHEEL)
                && s.cooldowns[CD_SLIDE] <= 0;
        if (!riding && key && (server || !shiftWas && may)) start(p, now);
        if (riding && (!key || !server && now - rideStart > 8)) exit(p, now);
        shiftWas = key;
        if (riding) { ride(p, input, v, now); return; }
        leave(p, input, v, now);
    }
    private static void start(LocalPlayer p, long now) {
        riding = true;
        rideStart = now;
        heading = p.getYRot() * Mth.DEG_TO_RAD;
        speed = 0; pitch = 0; turnRate = 0;
        exitKind = 0;
    }
    private static void ride(LocalPlayer p, net.minecraft.client.player.Input input, Vec3 v, long now) {
        float age = now - rideStart;
        input.forwardImpulse = input.leftImpulse = 0; input.jumping = false; input.shiftKeyDown = false;
        p.fallDistance = 0;
        grounded = p.onGround();
        if (age < SLIDE_PREP) {
            // Getting ready: he settles where he is while the ice forms out ahead of his feet.
            heading = p.getYRot() * Mth.DEG_TO_RAD;
            p.setDeltaMovement(v.x * .45, v.y, v.z * .45);
            return;
        }
        // Turning smoothly after the mouse; a sharp turn costs a little speed.
        float want = p.getYRot() * Mth.DEG_TO_RAD;
        float d = Mth.wrapDegrees((want - heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        float step = d * .32f;
        heading += step;
        turnRate = step;
        // START -> SLIDE -> ACCELERATION -> MAX SPEED: a push-off, then building up (more slowly near the top).
        if (speed < .14f) speed = .14f;
        if (speed < SLIDE_SPEED) speed = Math.min(SLIDE_SPEED, speed + SLIDE_ACCEL * (.5f + .5f * (1 - speed / SLIDE_SPEED)));
        // Downhill (on the ground or the track's own descent) gains; climbing costs some, never stalling him.
        float hill = grounded && pitch <= .02f ? slope : pitch;
        float gain = -Mth.sin(hill) * SLIDE_GRAVITY;
        if (gain < 0) gain *= .45f;
        speed += gain;
        speed *= 1 - SLIDE_TURN_LOSS * Math.max(0, Math.abs(step) - .03f) * .5f;
        if (speed > SLIDE_SPEED && gain <= .002f) speed -= (speed - SLIDE_SPEED) * .05f;
        // Climbing never stalls him on his own ramp.
        speed = Mth.clamp(speed, pitch > .1f ? .3f : 0, SLIDE_SPEED * 1.45f);
        // The track's pitch: SPACE raises it (limited); let go, over a short crest and down to the ground.
        boolean space = IcemanClient.spaceHeld();
        if (space) pitch = Math.min(SLIDE_ASCENT_MAX, pitch + SLIDE_PITCH_RATE * (pitch < 0 ? 2 : 1));
        else if (grounded && pitch <= .05f) pitch *= .5f;
        else pitch = Math.max(-SLIDE_DESCENT_MAX, pitch - SLIDE_PITCH_FALL);
        if (grounded && pitch < 0 && !space) pitch = 0;
        // A step in the way: an ice ramp over it; a ceiling: the climb flattens.
        if (p.horizontalCollision) { speed *= .92f; if (grounded || pitch < .3f) pitch = Math.max(pitch, .45f); }
        if (p.verticalCollision && !grounded && v.y > 0) pitch = Math.min(pitch, 0);
        double flat = Math.cos(pitch) * speed, vy = Math.sin(pitch) * speed;
        // Running along the ground: kept on it (over small steps, down slopes) at full speed.
        if (grounded && pitch <= .02f) { flat = speed; vy = -.08; }
        p.setDeltaMovement(-Mth.sin(heading) * flat, vy, Mth.cos(heading) * flat);
        // The view: a little wider with speed, climbing and diving.
        float k = .45f * speed / SLIDE_SPEED + .35f * Math.max(0, pitch) / SLIDE_ASCENT_MAX + .3f * Math.max(0, -pitch) / SLIDE_DESCENT_MAX;
        IcemanClient.speedFov(Math.min(1, k));
    }
    /** SHIFT let go (or the server ended it): braking on the ground, or a last push along the ice in the air. */
    private static void exit(LocalPlayer p, long now) {
        riding = false;
        exitStart = now;
        boolean ground = p.onGround() || !p.level().noCollision(p, p.getBoundingBox().move(0, -.3, 0));
        Vec3 dir = new Vec3(-Mth.sin(heading), 0, Mth.cos(heading));
        exitDir = dir;
        exitSpeed = speed;
        if (now - rideStart < SLIDE_PREP || speed < .05f) { exitKind = 0; return; }
        if (ground) { exitKind = 1; return; }
        exitKind = 2;
        // Off the ice in the air: a last push along it (a descent keeps more), then the game's own fall.
        double flat = Math.cos(pitch) * speed * .8, vy = Math.sin(pitch) * speed * (pitch < 0 ? .7 : .45);
        p.setDeltaMovement(dir.x * flat, vy, dir.z * flat);
        pitch = 0;
    }
    /** After the slide: the braking on the ground hands the walk back gradually; in the air nothing is held. */
    private static void leave(LocalPlayer p, net.minecraft.client.player.Input input, Vec3 v, long now) {
        float since = now - exitStart;
        if (exitKind == 1) {
            if (since >= SLIDE_END_TICKS || !p.onGround() && since > 2) { exitKind = 0; return; }
            float k = since / SLIDE_END_TICKS;
            double sp = exitSpeed * Math.pow(1 - k, 1.6);
            p.setDeltaMovement(exitDir.x * sp, v.y, exitDir.z * sp);
            input.forwardImpulse *= k; input.leftImpulse *= k;
            if (k < .5f) input.jumping = false;
            p.fallDistance = 0;
        } else if (exitKind == 2 && (p.onGround() || since > 80)) exitKind = 0;
    }

    // ------------------------------------------------------------------ his view
    private static float camPitch, camYawLag, lagYaw = Float.NaN, camAt = -1;
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e) {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null || !IcemanClient.isHero(p) || IcemanClient.wheelOpen() || FilmDirector.playing()) { camPitch = camYawLag = 0; lagYaw = Float.NaN; return; }
        float now = mc.level.getGameTime() + (float) e.getPartialTick();
        float dt = camAt < 0 ? 0 : Mth.clamp(now - camAt, 0, 3);
        camAt = now;
        // The tilt: up while rising, softly down while falling (off the ice, or diving down the track).
        float want = 0;
        if (riding) {
            if (pitch > 0) want = -3.2f * pitch / SLIDE_ASCENT_MAX;
            else if (pitch < 0 && !grounded) want = 2.6f * -pitch / SLIDE_DESCENT_MAX;
        } else if (exitKind == 2 && !p.onGround()) want = Mth.clamp((float) -p.getDeltaMovement().y / .8f, 0, 1) * 3f;
        camPitch += (want - camPitch) * (1 - (float) Math.exp(-dt * .18f));
        // A very slight lag behind sharp turns (third person only: in his own eyes the aim stays true).
        float yaw = (float) e.getYaw();
        if (Float.isNaN(lagYaw)) lagYaw = yaw;
        lagYaw += Mth.wrapDegrees(yaw - lagYaw) * (1 - (float) Math.exp(-dt * .6f));
        float lag = riding && !mc.options.getCameraType().isFirstPerson() ? Mth.clamp(Mth.wrapDegrees(lagYaw - yaw), -2.5f, 2.5f) : 0;
        camYawLag += (lag - camYawLag) * (1 - (float) Math.exp(-dt * .4f));
        if (Math.abs(camPitch) < .01f && Math.abs(camYawLag) < .01f) return;
        float fp = mc.options.getCameraType().isFirstPerson() ? .4f : 1;
        e.setPitch(e.getPitch() + camPitch * fp);
        e.setYaw(e.getYaw() + camYawLag * .6f);
    }
    /** Thin streaks at the screen's edges at speed (a touch of motion blur), never over the middle. */
    @SubscribeEvent public static void edges(RenderGuiEvent.Pre e) {
        var mc = Minecraft.getInstance();
        if (!riding || mc.player == null || mc.level == null || FilmDirector.playing()) return;
        float k = Mth.clamp((speed / SLIDE_SPEED - .55f) / .5f, 0, 1) + (pitch < 0 && !grounded ? .3f * -pitch / SLIDE_DESCENT_MAX : 0);
        if (k <= .02f) return;
        k = Math.min(1, k);
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        float time = mc.level.getGameTime() + e.getPartialTick();
        // A soft pale band at each side.
        for (int i = 0; i < 6; i++) {
            int a = (int) (k * 20 * (1 - i / 6f));
            if (a <= 0) continue;
            int col = a << 24 | 0xe6f4ff;
            g.fill(i * 4, 0, i * 4 + 4, h, col);
            g.fill(w - i * 4 - 4, 0, w - i * 4, h, col);
        }
        // Streaks rushing back along the edges.
        int n = (int) (6 + 10 * k);
        for (int i = 0; i < n; i++) {
            double h1 = IceMesh.hash(i * 3.71), h2 = IceMesh.hash(i * 9.13);
            float ph = (float) ((time * (.06 + .05 * h2) * (1 + k) + h1) % 1.0);
            boolean left = i % 2 == 0;
            int y = (int) (h * (.08 + .84 * IceMesh.hash(i * 5.3 + Math.floor(time * (.06 + .05 * h2) * (1 + k) + h1))));
            int len = (int) (18 + 40 * k * (float) h2), x0 = (int) ((w * .2f) * (1 - ph));
            int a = (int) (k * 70 * Mth.sin(ph * Mth.PI));
            if (a <= 2) continue;
            int col = a << 24 | 0xf2f9ff;
            if (left) g.fill(Math.max(0, x0 - len), y, x0, y + 1, col);
            else g.fill(w - x0, y, Math.min(w, w - x0 + len), y + 1, col);
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        riding = false; shiftWas = false; exitKind = 0; lastPos = null; camPitch = camYawLag = 0; lagYaw = Float.NaN; camAt = -1;
    }
}
