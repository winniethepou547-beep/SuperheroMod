package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Full-screen procedural backdrop (film_backdrop shader): rushing hell clouds, or space with a
 * burning banded star, its halo and a spinning ring. Computed per pixel every frame, so it
 * moves as smoothly as the game renders. If the shader cannot load, films fall back to a
 * plain gradient instead of failing.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FilmBackdrop {
    public static final int CLOUDS = 0, STAR = 1, ORBIT = 2, PLUNGE = 3, HELL = 4, ABYSS = 5, ARENA = 6, DESERT = 7, PLAIN = 8, CITY = 9, WASTELAND = 10, GOTHAM = 11;
    /** Everything the backdrop needs for one frame. Planet radius and ring are in stage blocks / radii. */
    public record Params(int scene, Vec3 planet, float radius, float ringInner, float ringOuter, float ringSpin, float ringAlpha,
                         float fallSpeed, float streaks, float clouds, float xray, int tint, float tintAmount) {
        /**
         * Dark fog arena on wet stone. The fog is lit red by a light (position, intensity) and by a
         * beam running from that light down +z (strength, radius, length); flash whitens everything.
         */
        public static Params arena(Vec3 light, float intensity, float beam, float beamRadius, float beamLength, float flash, float fog, float darkness) {
            return new Params(ARENA, light, intensity, beam, beamRadius, beamLength, flash, 0, 0, fog, 0, 0, darkness);
        }
        /** Golden-hour desert; storm 0..1 rolls a sandstorm over it, wind sets how fast it blows. */
        public static Params desert(Vec3 sun, float storm, float wind, int tint, float tintAmount) {
            return new Params(DESERT, sun, 1, 0, 0, 0, 0, wind, 0, storm, 0, tint, tintAmount);
        }
        /** An open plain at midday, a blue sky with big white clouds; dust 0..1 hangs in the air, drift moves the clouds. */
        public static Params plain(Vec3 sun, float dust, float drift) {
            return new Params(PLAIN, sun, 1, 0, 0, 0, 0, drift, 0, dust, 0, 0, 0);
        }
        /** A city at night: towers and hills round the horizon, low cloud lit from below; dust 0..1 hangs in the air (z: how far down the road, unused by the sky itself). */
        public static Params city(float dust, float z) {
            return new Params(CITY, Vec3.ZERO, 1, 0, 0, 0, 0, z, 0, dust, 0, 0, 0);
        }
        /**
         * A dead world under a crimson storm: wet black ground, ruins on the horizon, rain. strike = where the lightning
         * comes down (stage space, far off), flash 0..1+ lights the clouds and the ground, bolt 0..1 draws its channel
         * in the sky (seed shapes it); wind rolls the clouds; rain 0..1.
         */
        public static Params wasteland(Vec3 strike, float flash, float seed, float bolt, float wind, float rain) {
            return new Params(WASTELAND, strike, flash, seed, bolt, 0, 0, wind, 0, rain, 0, 0, 0);
        }
        /**
         * Gotham at night (Batman's film): low heavy cloud lit from below by the city, the moon hidden behind it (moon =
         * a direction toward it, show 0..1 how much of it shows through), the skyline all round and, seen from high up,
         * the grid of lit streets below; drift moves the cloud, rain 0..1, flash 0..1 a cold white-blue flash.
         */
        public static Params gotham(Vec3 moon, float show, float drift, float rain, float flash) {
            return new Params(GOTHAM, moon, show, flash, 0, 0, 0, drift, 0, rain, 0, 0, 0);
        }
        public static Params abyss(float streaks) {
            return new Params(ABYSS, Vec3.ZERO, 1, 0, 0, 0, 0, 0, streaks, 0, 0, 0, 0);
        }
        public static Params hell(float streaks, float xray) {
            return new Params(HELL, Vec3.ZERO, 1, 0, 0, 0, 0, 0, streaks, 0, xray, 0, 0);
        }
        public static Params clouds(float fall, float density, float streaks) {
            return new Params(CLOUDS, Vec3.ZERO, 1, 0, 0, 0, 0, fall, streaks, density, 0, 0, 0);
        }
    }
    private static ShaderInstance shader;

    @SubscribeEvent public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(SuperheroMod.MODID, "film_backdrop"), DefaultVertexFormat.POSITION), s -> shader = s);
        } catch (Exception error) {
            shader = null;
            com.mojang.logging.LogUtils.getLogger().error("Film backdrop shader failed to load; films will use a plain sky", error);
        }
    }
    public static boolean ready() { return shader != null; }

    /** Draws behind everything; the camera basis comes from the film's own yaw/pitch. */
    static void draw(Params p, Vec3 camera, Vec3 forward, Vec3 right, Vec3 up, float seconds) {
        var proj = RenderSystem.getProjectionMatrix();
        float tanHalf = 1f / proj.m11(), aspect = proj.m11() / proj.m00();
        shader.safeGetUniform("Time").set(seconds);
        shader.safeGetUniform("Scene").set((float) p.scene());
        shader.safeGetUniform("CamPos").set((float) camera.x, (float) camera.y, (float) camera.z);
        shader.safeGetUniform("CamF").set((float) forward.x, (float) forward.y, (float) forward.z);
        shader.safeGetUniform("CamR").set((float) right.x, (float) right.y, (float) right.z);
        shader.safeGetUniform("CamU").set((float) up.x, (float) up.y, (float) up.z);
        shader.safeGetUniform("Lens").set(tanHalf, aspect);
        shader.safeGetUniform("Planet").set((float) p.planet().x, (float) p.planet().y, (float) p.planet().z, p.radius());
        shader.safeGetUniform("Ring").set(p.ringInner(), p.ringOuter(), p.ringSpin(), p.ringAlpha());
        shader.safeGetUniform("Motion").set(p.fallSpeed(), p.streaks(), p.clouds(), p.xray());
        shader.safeGetUniform("Tint").set((p.tint() >> 16 & 255) / 255f, (p.tint() >> 8 & 255) / 255f, (p.tint() & 255) / 255f, p.tintAmount());
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.disableBlend();
        RenderSystem.setShader(() -> shader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.vertex(-1, -1, 0).endVertex(); b.vertex(1, -1, 0).endVertex(); b.vertex(1, 1, 0).endVertex(); b.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.enableDepthTest();
    }
}
