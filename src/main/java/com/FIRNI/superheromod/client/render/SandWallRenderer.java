package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.SandWallSyncPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.FIRNI.superheromod.client.render.util.SandGeometry;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Random;

/**
 * Kum duvarlarini cizer.
 *
 * Onizleme saydam ve soluk; gercek duvar opak kum tonlarinda. Dikenler dis
 * yuzeyden cikan piramitler olarak ciziliyor ve sadece firlatma hazirliginda
 * gorunuyor.
 *
 * Bu gecici bir cizim: model asamasinda yerini sand_wall.bbmodel alacak.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class SandWallRenderer {

    // Doku artik gercek kum oldugu icin renkler DOKUYU BOYAMIYOR, sadece
    // hafif golgeleme yapiyor. Onceki koyu degerler kum dokusunu
    // camurlastiriyordu; 1.0 civari degerler kumu kendi renginde birakiyor.
    private static final float[] SAND_LIGHT = {1.0f, 1.0f, 1.0f};
    private static final float[] SAND_MID = {0.92f, 0.90f, 0.86f};
    private static final float[] SAND_DARK = {0.66f, 0.62f, 0.55f};

    /** Onizleme: dokunun uzerine sari bir ton -- "henuz gercek degil". */
    private static final float[] PREVIEW = {1.0f, 0.92f, 0.55f};

    private SandWallRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<SandWallSyncPacket.Entry> walls = ClientSandWallData.get();
        if (walls.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        // ENTITY CIZIM YOLU — sol tiktaki uzayan kolla AYNI.
        //
        // Onceden POSITION_TEX_COLOR ile ciziliyordu; o shader ne dunya
        // isigini ne de yuz golgelemesini uyguluyor. Duvar hem gece
        // yaniyor hem de gunduz oyunun kendi kum bloklarindan belirgin
        // sekilde parlak duruyordu. entityCutoutNoCull ise vanilla blok
        // aydinlatmasinin aynisini yapiyor.
        MultiBufferSource.BufferSource buffers =
                mc.renderBuffers().bufferSource();

        TextureAtlasSprite sprite = SandGeometry.sandSprite();

        // Kati duvarlar opak, onizleme saydam gecisten ciziliyor
        VertexConsumer solid = buffers.getBuffer(
                RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        for (SandWallSyncPacket.Entry wall : walls) {
            if (!wall.preview()) drawWall(solid, matrix, wall, sprite);
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));

        VertexConsumer ghost = buffers.getBuffer(
                RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        for (SandWallSyncPacket.Entry wall : walls) {
            if (wall.preview()) drawWall(ghost, matrix, wall, sprite);
        }
        buffers.endBatch(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));

        pose.popPose();
    }

    private static void drawWall(VertexConsumer buf, Matrix4f m, SandWallSyncPacket.Entry wall,
                                 TextureAtlasSprite sprite) {
        double yaw = Math.toRadians(wall.yaw());
        // Dis yuzey normali ve duvarin yan ekseni
        Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 side = new Vec3(facing.z, 0, -facing.x);
        Vec3 up = new Vec3(0, 1, 0);

        boolean preview = wall.preview();
        // Kati duvar TAM OPAK. Yarim saydam kalinca onay oncesi haliyle
        // ayni parlaklikta duruyor ve "kum" gibi okunmuyordu.
        float alpha = preview ? 0.32f : 1.0f;

        // Hasar aldikca koyulasip soluklasir
        float health = Mth.clamp(wall.health(), 0f, 1f);
        int light = SandGeometry.lightAt(wall.center());

        float[] body = preview ? PREVIEW : (health > 0.5f ? SAND_MID : SAND_DARK);
        float[] edge = preview ? PREVIEW : SAND_LIGHT;

        Vec3 c = wall.center();
        Vec3 hw = side.scale(wall.halfWidth());
        Vec3 hh = up.scale(wall.halfHeight());
        Vec3 hd = facing.scale(wall.halfDepth());

        SandGeometry.box(buf, m, sprite, light, c, hw, hh, hd, body, alpha, 1f);

        // Kenar vurgusu — duvarin sinirlari belli olsun
        SandGeometry.box(buf, m, sprite, light, c,
                hw.scale(1.02), hh.scale(0.06), hd.scale(1.02), edge, alpha * 0.8f, 1f);
        SandGeometry.box(buf, m, sprite, light, c.add(0, wall.halfHeight() * 0.95, 0),
                hw.scale(1.02), hh.scale(0.05), hd.scale(1.02), edge, alpha * 0.8f, 1f);

        float spike = wall.spike();
        if (spike > 0.01f) {
            drawSpikes(buf, m, sprite, light, wall, facing, side, up, spike, alpha);
        }
    }

    /** Dis yuzeyden cikan dikenler — sadece firlatma hazirliginda gorunur. */
    private static void drawSpikes(VertexConsumer buf, Matrix4f m, TextureAtlasSprite sprite,
                                   int light, SandWallSyncPacket.Entry wall,
                                   Vec3 facing, Vec3 side, Vec3 up, float spike, float alpha) {
        Random rng = new Random(Double.doubleToLongBits(wall.center().x) ^ 0x5A17D);

        int cols = 5;
        int rows = 3;
        double maxLen = 1.1 * spike;

        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                double u = (col + 0.5) / cols * 2.0 - 1.0;
                double v = (r + 0.5) / rows * 2.0 - 1.0;

                // Duzenli izgara yerine hafif dagilim — kum dikeni duzgun olmaz
                u += (rng.nextDouble() - 0.5) * 0.18;
                v += (rng.nextDouble() - 0.5) * 0.25;

                Vec3 base = wall.center()
                        .add(side.scale(u * wall.halfWidth() * 0.85))
                        .add(up.scale(v * wall.halfHeight() * 0.8))
                        .add(facing.scale(wall.halfDepth()));

                double len = maxLen * (0.65 + rng.nextDouble() * 0.55);
                double thick = 0.16 + rng.nextDouble() * 0.10;

                // Diken artik ucgen piramit degil KISALAN KUTU dizisi.
                //
                // Ucgenler dortgen arayuzunde son noktayi tekrarlayarak
                // ciziliyordu ve normalleri anlamsizdi; yuz golgelemesi
                // devreye girince bu kabul edilemez hale geldi.
                int steps = 3;
                for (int i = 0; i < steps; i++) {
                    double t0 = i / (double) steps;
                    double t1 = (i + 1) / (double) steps;
                    double mid = (t0 + t1) * 0.5;

                    double half = thick * (1.0 - mid * 0.75);
                    Vec3 c = base.add(facing.scale(len * mid));

                    SandGeometry.box(buf, m, sprite, light, c,
                            side.scale(half), up.scale(half),
                            facing.scale(len * (t1 - t0) * 0.5),
                            i == 1 ? SAND_MID : SAND_LIGHT, alpha, 1f);
                }
            }
        }
    }
}
