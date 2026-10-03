package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.client.render.film.FilmBackdrop;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import static com.FIRNI.superheromod.client.render.panther.PursuitPath.*;
import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * THE FINAL PURSUIT's stage, drawn by the film every frame in the right order: the city's solid matter, the
 * traffic, the car behind, his car, the two performers, the debris; then the city's light; the glass; the
 * smoke and dust; the cars' lamps; the effects' light last. Panther is PantherBody itself, posed by
 * PursuitMoves, his suit lit by the city's neon and filling with the energy of every bullet it takes.
 */
public final class PursuitStage {
    private PursuitStage() {}

    /** The sky: night over the city, the glow of it on low cloud, the hills and towers far off; thicker after the crash. */
    public static FilmBackdrop.Params backdrop(float t) {
        float tau = scene(t);
        float dust = clamp((tau - BOOM - CRASH) / 30) * (1 - .6f * clamp((t - 700) / 50));
        return FilmBackdrop.Params.city(dust, (float) driveZ(Math.min(tau, BOOM)));
    }

    public static void render(FilmContext c, float t, int seed) {
        float tau = scene(t);
        float time = c.time();
        PursuitShade.camera(c.camera().x, c.camera().y, c.camera().z);
        float dust = clamp((tau - BOOM - CRASH - 4) / 20) * (1 - .7f * clamp((t - PursuitFx.PART_FROM) / 40));
        PursuitShade.haze = 1 + 2.5f * dust;
        PursuitFx.lights(tau, time);
        PursuitCity.solid(c, tau);
        PursuitCar.traffic(c, tau, time);
        PursuitCar.suv(c, tau);
        PursuitCar.hero(c, tau, time);
        panther(c, tau, t);
        thug(c, tau, seed);
        PursuitFx.solid(c, tau, time);
        PursuitCity.light(c, tau, time);
        PursuitCar.heroGlass(c, tau);
        Vec3 revealFrom = t >= PursuitCamera.DUST ? new Vec3(REST_FEET.x - 1.7, .95, REST_FEET.z - 6.5) : null;
        PursuitFx.soft(c, tau, time, revealFrom);
        PursuitCar.heroLights(c, tau);
        PursuitFx.light(c, tau, time);
        PursuitShade.haze = 1;
        PursuitShade.EXTRA.clear();
        PursuitShade.flashR = PursuitShade.flashG = PursuitShade.flashB = 0;
    }

    /** Panther: his body posed, the suit's light (the energy he has taken, the charge, the release, the drain), the neon on him. */
    private static void panther(FilmContext c, float tau, float t) {
        Place place = PursuitPath.panther(tau);
        PantherMotion.Pose pose = PursuitMoves.panther(tau, t);
        PantherBody.Charge charge = PantherBody.CHARGE.reset();
        int hits = hitsBefore(tau);
        charge.level = Math.min(.72f, .1f + .1f * hits);
        if (tau >= ULT_CHARGE) charge.level = charge.level + (1 - charge.level) * PursuitPath.ease((tau - ULT_CHARGE) / (ULT_HOLD - ULT_CHARGE));
        if (tau >= ULT_CHARGE + 12 && tau < BOOM) charge.flow = PursuitPath.clamp((tau - (ULT_CHARGE + 12)) / (ULT_HOLD - ULT_CHARGE - 8));
        float d = tau - BOOM;
        if (d >= 0) {
            charge.flash = 1.5f * (1 - PursuitPath.ease((d - .5f) / 3));
            // After the release: what is left glows on, fading, until it drains away in the last shot.
            charge.level = .5f + .2f * (float) Math.exp(-d / 6);
            charge.drain = PursuitPath.clamp((PursuitPath.real(tau) - PursuitMoves.DRAIN) / 20);
        }
        for (Shot s : SHOTS) {
            if (s.hits() != 0 || tau < s.time() || tau > s.time() + 12) continue;
            charge.pulses.add(new float[]{tau - s.time(), s.side(), PantherBody.order(s.region())});
        }
        Matrix4f body = place.matrix();
        Vec3 chest = PursuitRig.chest(body, pose);
        PursuitShade.lightsNear(chest.x, chest.y, chest.z, 14);
        float[] amb = PursuitShade.ambient(chest.x, chest.y, chest.z);
        PantherBody.reflect = new float[]{Math.min(.5f, amb[0] * .22f), Math.min(.5f, amb[1] * .22f), Math.min(.5f, amb[2] * .22f)};
        PantherBody.clawLength = PursuitMoves.claws(tau);
        PoseStack p = c.pose();
        p.pushPose();
        place(p, place);
        try {
            PantherBody.draw(p, c.buffers(), LightTexture.FULL_BRIGHT, pose, 0, 0, t, PantherBody.NORMAL, 1, 0);
        } finally {
            p.popPose();
            PantherBody.reflect = new float[]{0, 0, 0};
            PantherBody.clawLength = 1;
            PantherBody.CHARGE.reset();
        }
    }

    /** The model frame of a performer on the pose stack (as the player renderer sets it up, so the suit's shading normals turn with it). */
    static void place(PoseStack p, Place place) {
        p.translate(place.feet().x, place.feet().y, place.feet().z);
        p.mulPose(place.frame());
        p.mulPose(com.mojang.math.Axis.YP.rotation(net.minecraft.util.Mth.PI - place.yaw()));
        p.scale(-BODY_SCALE, -BODY_SCALE, BODY_SCALE);
        p.translate(0, -1.501, 0);
    }

    /** The gunman, until he is out of sight high behind the car. */
    private static void thug(FilmContext c, float tau, int seed) {
        if (tau > THROWN + 50) return;
        Place place = gunman(tau);
        PursuitThug.draw(c, place.matrix(), PursuitMoves.gunman(tau), PursuitThug.Look.of(seed));
    }
}
