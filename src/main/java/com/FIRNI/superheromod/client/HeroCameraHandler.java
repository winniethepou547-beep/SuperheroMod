package com.FIRNI.superheromod.client;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.lang.reflect.Field;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public class HeroCameraHandler {

    private static Field cameraPositionField;
    private static boolean fieldResolved = false;

    private static final float RIGHT_OFFSET = 0.75f;
    private static final float UP_OFFSET = 0.1f;

    /** Ulti sirasinda kameranin geri acilma mesafesi (blok). */
    private static final double MAX_ULT_ZOOM = 5.5;
    private static float ultZoom = 0f;

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        // Sinematik sirasinda kamerayi CinematicCameraHandler suruyor
        if (com.FIRNI.superheromod.client.render.cinematic.CinematicClient.isRunning()) return;
        if (com.FIRNI.superheromod.client.render.ClientColossusData.isLocalColossus()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) return;
        if (mc.player == null) return;

        Camera camera = event.getCamera();
        if(mc.player.getVehicle() instanceof com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity bike) {
            float pitch=net.minecraft.util.Mth.lerp((float)event.getPartialTick(),bike.xRotO,bike.getXRot());
            // Interpolated heading: the raw tick value made the chase camera stutter in turns.
            double yaw=Math.toRadians(net.minecraft.util.Mth.rotLerp((float)event.getPartialTick(),bike.yRotO,bike.getYRot()));
            Vec3 pivot=mc.player.getEyePosition((float)event.getPartialTick());
            Vec3 offset=camera.getPosition().subtract(pivot).yRot((float)yaw).xRot((float)Math.toRadians(-pitch)).yRot((float)-yaw);
            Vec3 wantedPosition=pivot.add(offset);
            var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(pivot,wantedPosition,
                net.minecraft.world.level.ClipContext.Block.VISUAL,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)wantedPosition=hit.getLocation().lerp(pivot,.08);
            setCameraPosition(camera,wantedPosition);event.setPitch(event.getPitch()+pitch);
            event.setRoll(event.getRoll()+bike.lean((float)event.getPartialTick())*.22f);return;
        }
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Vec3 pos = camera.getPosition();

        // Ulti sirasinda kamera geriye acilir ki nereye attigin gorunsun;
        // bitince aniden degil, yumusak animasyonla geri gelir.
        boolean ultiActive = com.FIRNI.superheromod.client.hud.ClientUltimateState.isAnyPhase();
        float wanted = ultiActive ? 1f : 0f;
        ultZoom += (wanted - ultZoom) * (ultiActive ? 0.10f : 0.055f);

        double back = ultZoom * MAX_ULT_ZOOM;
        Vec3 lookDir = new Vec3(camera.getLookVector());

        double newX = pos.x - left.x() * RIGHT_OFFSET + up.x() * UP_OFFSET - lookDir.x * back;
        double newY = pos.y - left.y() * RIGHT_OFFSET + up.y() * UP_OFFSET
                - lookDir.y * back + ultZoom * 1.2;
        double newZ = pos.z - left.z() * RIGHT_OFFSET + up.z() * UP_OFFSET - lookDir.z * back;

        setCameraPosition(camera, new Vec3(newX, newY, newZ));
    }

    private static void setCameraPosition(Camera camera, Vec3 pos) {
        if (!fieldResolved) {
            fieldResolved = true;
            try {
                cameraPositionField = Camera.class.getDeclaredField("position");
                cameraPositionField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                for (Field f : Camera.class.getDeclaredFields()) {
                    if (f.getType() == Vec3.class) {
                        cameraPositionField = f;
                        cameraPositionField.setAccessible(true);
                        break;
                    }
                }
            }
        }
        if (cameraPositionField != null) {
            try {
                cameraPositionField.set(camera, pos);
            } catch (Exception ignored) {}
        }
    }
}
