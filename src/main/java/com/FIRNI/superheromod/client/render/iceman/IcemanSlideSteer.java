package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * His own client steers his body through the two slides (the server decides when they start and end and who they
 * touch).
 * <p>
 * The ice slide (SHIFT held): it starts on the key press itself (predicted; dropped again if the server does not take
 * it up), the speed builds by SLIDE_ACCEL to SLIDE_SPEED along his look, turning smoothly after it; he follows his look's
 * pitch by SLIDE_CLIMB, so looking up climbs into the air on the ice ramp and looking down dives; no gravity while he
 * rides (the velocity is set whole every tick, so the game's own fall never builds up); a wall in the way ramps him up
 * over it, a ceiling stops the climb. When it ends he keeps his momentum for a moment and then falls as usual (no fall
 * damage: the server's). His view widens with the speed.
 * <p>
 * The sub-zero slide (CTRL): DASH_DIST over DASH_TICKS along the way his client picked (IcemanClient.dashYaw()), a fast
 * start that eases out, hugging the ground, no jumping.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanSlideSteer {
    private IcemanSlideSteer() {}

    /** Predicted / confirmed: his own ice slide is on, since when (game time). */
    private static boolean riding, keyWas;
    private static long rideStart = -100, endedAt = -100;
    private static float speed, heading, vy;
    private static Vec3 carry = Vec3.ZERO;
    private static int ceiling;
    /** The share of each tick of the sub-zero slide's distance (fast start, easing out), worked out once. */
    private static final float[] STEP = new float[DASH_TICKS];
    static {
        float sum = 0;
        for (int i = 0; i < DASH_TICKS; i++) { STEP[i] = (float) Math.pow(1 - i / (float) DASH_TICKS, 1.5) + .08f; sum += STEP[i]; }
        for (int i = 0; i < DASH_TICKS; i++) STEP[i] *= DASH_DIST / sum;
    }

    /** His own ice slide is being steered now (predicted or confirmed). */
    public static boolean riding() { return riding; }
    /** How fast his own slide is (0..1 of SLIDE_SPEED). */
    public static float speed() { return riding ? speed / SLIDE_SPEED : 0; }
    /** The sub-zero slide is moving him now (for his own client). */
    public static boolean dashing() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        long dt = mc.level.getGameTime() - IcemanClient.dashStart();
        if (dt < 0 || dt >= DASH_TICKS) return false;
        IcemanClient.State s = mc.player == null ? null : IcemanClient.get(mc.player);
        return dt < 4 || s != null && s.action == DASH;
    }

    @SubscribeEvent public static void steer(MovementInputUpdateEvent e) {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (e.getEntity() != p || p == null || mc.level == null) return;
        if (!IcemanClient.isHero(p) || FilmDirector.playing() || !p.isAlive()) { riding = false; keyWas = false; return; }
        IcemanClient.State s = IcemanClient.get(p);
        long now = mc.level.getGameTime();
        var input = e.getInput();
        Vec3 v = p.getDeltaMovement();

        // ---- the sub-zero slide
        long dt = now - IcemanClient.dashStart();
        if (dt >= 0 && dt < DASH_TICKS && (dt < 4 || s != null && s.action == DASH)) {
            if (riding) { riding = false; endedAt = now; }
            float yaw = IcemanClient.dashYaw();
            double step = STEP[(int) dt];
            double vyD = p.onGround() ? (dt == 0 ? -.05 : -.1) : Math.min(v.y, 0);
            p.setDeltaMovement(-Mth.sin(yaw) * step, vyD, Mth.cos(yaw) * step);
            p.fallDistance = 0;
            input.forwardImpulse = input.leftImpulse = 0; input.jumping = false; input.shiftKeyDown = false;
            carry = new Vec3(-Mth.sin(yaw) * step, 0, Mth.cos(yaw) * step);
            keyWas = mc.options.keyShift.isDown();
            return;
        }

        // ---- the ice slide: predicted on the press, kept while the server agrees and the key is held
        boolean key = mc.options.keyShift.isDown() && mc.screen == null && !IcemanClient.wheelOpen();
        boolean server = s != null && s.action == SLIDE;
        boolean may = s == null || (s.action == IDLE || s.action == SLIDE || s.action == BRUSH || s.action == FORM || s.action == WHEEL) && s.cooldowns[CD_SLIDE] <= 0;
        if (!riding && key && (server || !keyWas && may)) start(p, now);
        if (riding && (!key || !server && now - rideStart > 8)) { riding = false; endedAt = now; }
        keyWas = key;

        if (riding) {
            float lookYaw = p.getYRot() * Mth.DEG_TO_RAD, pitch = p.getXRot() * Mth.DEG_TO_RAD;
            // Smooth turning after the look; the speed building up (a wall in the way takes some of it).
            heading += Mth.wrapDegrees((lookYaw - heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * .3f;
            speed = Math.min(SLIDE_SPEED, speed + SLIDE_ACCEL);
            float ep = pitch * SLIDE_CLIMB;
            double wantVy = -Math.sin(ep) * speed;
            double flat = Math.cos(ep) * speed;
            // On the ground and not looking up much: kept on it (over steps, down slopes) at full speed.
            boolean glued = p.onGround() && wantVy <= .03;
            if (glued) { flat = speed; wantVy = -.08; }
            if (p.horizontalCollision) { speed *= .9f; wantVy = Math.max(wantVy, .42); glued = false; }
            if (p.verticalCollision && !p.onGround() && vy > 0) ceiling = 4;
            if (ceiling > 0) { ceiling--; wantVy = Math.min(wantVy, 0); }
            vy = glued ? (float) wantVy : (float) Mth.lerp(.35, vy, wantVy);
            if (ceiling > 0 && vy > 0) vy = 0;
            Vec3 next = new Vec3(-Mth.sin(heading) * flat, vy, Mth.cos(heading) * flat);
            p.setDeltaMovement(next);
            carry = new Vec3(next.x, 0, next.z);
            p.fallDistance = 0;
            input.forwardImpulse = input.leftImpulse = 0; input.jumping = false; input.shiftKeyDown = false;
            IcemanClient.speedFov(speed / SLIDE_SPEED);
            return;
        }

        // ---- stepping off: the momentum carries on for a moment (in the air the game's own drag takes over)
        long since = now - endedAt;
        if (since >= 0 && since < SLIDE_END_TICKS && p.onGround() && carry.lengthSqr() > 1e-4) {
            double k = Math.pow(.8, since + 1);
            p.setDeltaMovement(carry.x * k + v.x * (1 - k) * .5, v.y, carry.z * k + v.z * (1 - k) * .5);
        }
        if (since >= SLIDE_END_TICKS) carry = Vec3.ZERO;
    }
    private static void start(net.minecraft.client.player.LocalPlayer p, long now) {
        riding = true;
        rideStart = now;
        heading = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 v = p.getDeltaMovement();
        // A push-off: never from a standstill.
        speed = Math.max(.25f, (float) Math.sqrt(v.x * v.x + v.z * v.z));
        vy = (float) Math.max(0, v.y);
        ceiling = 0;
        IcemanClient.kickFov(.3f);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { riding = false; keyWas = false; carry = Vec3.ZERO; }
}
