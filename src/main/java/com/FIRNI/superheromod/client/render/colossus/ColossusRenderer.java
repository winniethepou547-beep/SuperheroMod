package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.ClientColossusData;
import com.FIRNI.superheromod.heroes.sandman.ColossusCrystal;
import com.FIRNI.superheromod.heroes.sandman.ColossusPose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SAND COLOSSUS'U CIZER — oyuncunun yerine.
 *
 * Oyuncunun kendi modeli gizleniyor; yerine {@link ColossusShape}'teki kumdan dev ciziliyor: her kemik koyu,
 * sikistirilmis bir cekirdek ve ustune oturan kum levhalari/topaklari. Kemikler sunucunun da kullandigi
 * {@link ColossusPose} egrilerini izliyor (isabet, yumruk noktasi ve kristal yuvalari ayni yerde kaliyor).
 *
 *  - OLUSMA: cekirdekler yerden yukari dolarken levhalar zeminden donerek yukselen kum akintilariyla gelip
 *    yerine oturuyor (dune, govde, omuzlar, on kollar, eller, kafa). Efektler: {@link ColossusFx}.
 *  - KRISTALLER: {@link ColossusCrystals} — buyuyerek cikar, yavasca doner ve nefes alir, isigi yakalar,
 *    catlayinca parcalar firlar, kirilinca patlar.
 *  - BITIS: form bitince son hali bir "harabe" olarak kaliyor ve tepeden asagi kum topaklarina ayrilip
 *    yere dokuluyor ({@link #dissolve}).
 *
 * Iki gecis var: govde ve kristaller AFTER_ENTITIES'te (dunya isigiyla, entity shader'i), isik/toz/akan kum
 * AFTER_TRANSLUCENT_BLOCKS'ta ({@link ColossusFx}).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ColossusRenderer {
    private static final ResourceLocation SAND = new ResourceLocation("minecraft", "textures/block/sand.png");
    /** Kristal icin ayri doku — kumdan belirgin farkli gorunmeli. */
    private static final ResourceLocation CRYSTAL = new ResourceLocation(SuperheroMod.MODID, "textures/entity/colossus_crystal.png");
    private static final int MAX_RUINS = 4;

    static final Map<UUID, ColossusVisual> LIVE = new HashMap<>();
    static final List<ColossusVisual> RUINS = new ArrayList<>();
    static final ColossusCrystals.Shards SHARDS = new ColossusCrystals.Shards();

    private static final Out OUT = new Out();
    private static final Matrix4f VIEW = new Matrix4f(), FOOT = new Matrix4f(), TMP = new Matrix4f();
    private static final float[] S = new float[8];
    private static ClientLevel lastLevel;

    private ColossusRenderer() {}

    /** Colossus formundaki oyuncunun kendi modeli cizilmez. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (ClientColossusData.isColossus(event.getEntity())) event.setCanceled(true);
    }

    /** Animation clock in ticks (wrapped so a float keeps its precision). */
    static float clock(ClientLevel level, float partial) {
        return (float) (level.getGameTime() % 120000L) + partial;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            LIVE.clear(); RUINS.clear(); SHARDS.clear(); ColossusFx.clear();
            lastLevel = mc.level;
        }
        if (mc.level == null) return;
        float partial = event.getPartialTick();
        float now = clock(mc.level, partial);
        double game = mc.level.getGameTime() + (double) partial;

        for (Player player : mc.level.players()) {
            if (!ClientColossusData.isColossus(player)) continue;
            update(LIVE.computeIfAbsent(player.getUUID(), id -> new ColossusVisual()), player, partial, now, mc);
        }
        // Out of sight for a while (left the area): forget; it starts fresh when it comes back.
        LIVE.values().removeIf(v -> now - v.lastSeen > 40 || now < v.lastSeen - 1);
        RUINS.removeIf(r -> game - r.collapseStart > ColossusBody.COLLAPSE_TICKS || game < r.collapseStart);
        if (LIVE.isEmpty() && RUINS.isEmpty() && SHARDS.empty()) return;

        Vec3 cam = event.getCamera().getPosition();
        VIEW.set(event.getPoseStack().last().pose());
        Frustum frustum = event.getFrustum();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // 1) Every body first (one render type, so nothing is flushed in between)...
        OUT.v = buffers.getBuffer(RenderType.entityCutoutNoCull(SAND));
        for (ColossusVisual v : LIVE.values()) {
            v.drawn = inView(frustum, v);
            if (!v.drawn) continue;
            OUT.glow = ignite(v, now);
            body(v, cam, -1);
        }
        for (ColossusVisual r : RUINS) {
            r.drawn = inView(frustum, r);
            if (!r.drawn) continue;
            OUT.glow = 0;
            body(r, cam, (float) (game - r.collapseStart));
        }
        // 2) ...then the crystals and the flying shards.
        OUT.v = buffers.getBuffer(RenderType.entityCutoutNoCull(CRYSTAL));
        OUT.glow = 0;
        for (ColossusVisual v : LIVE.values()) if (v.drawn) crystals(v, cam, now);
        shards(cam, now, mc);
        buffers.endBatch();
    }

    private static void update(ColossusVisual v, Player player, float partial, float now, Minecraft mc) {
        Vec3 pos = player.getPosition(partial);
        v.x = pos.x; v.y = pos.y; v.z = pos.z;
        float yaw = Mth.rotLerp(partial, player.yRotO, player.getYRot());
        float age = player.tickCount + partial;
        // The dune follows the torso's turn late: the weight of the thing. Same damping at any frame rate;
        // repeated renders at the same moment do not advance it.
        if (v.massAge >= 0 && age >= v.massAge && age - v.massAge < 20) {
            float response = (float) (1.0 - Math.pow(0.94, (age - v.massAge) * 3.0));
            v.massYaw += Mth.wrapDegrees(yaw - v.massYaw) * response;
        } else v.massYaw = yaw;
        v.massAge = age;
        v.yaw = yaw;
        v.progress = ClientColossusData.formProgress(player);
        for (int c = 0; c < ColossusVisual.CRYSTALS; c++) v.states[c] = ClientColossusData.crystalState(player, c);
        boolean right = v.states[ColossusCrystal.RIGHT_SHOULDER.ordinal()] < 3;
        var action = ClientColossusActions.of(player.getUUID());
        ColossusPose pose = ColossusPose.evaluate(age, yaw, v.massYaw, 1,
                action == null ? null : new ColossusPose.Action(action.type, action.ticks, action.duration), partial, right);
        ColossusPose.form(pose, v.progress);
        v.skeleton.set(pose, yaw, right);
        for (ColossusCrystal type : ColossusCrystal.values()) {
            int c = type.ordinal();
            ColossusCrystals.cluster(type.socket(Vec3.ZERO, yaw, 1, pose), c, (float) type.radius * ColossusCrystals.SIZE, now, v.states[c], v.cluster[c]);
        }
        // ISIK: cevrenin isigi (bel hizasindan), FULL_BRIGHT degil — dev dunyayla ayni isikta durmali.
        v.light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos.x, pos.y + 2, pos.z));
        float block = LightTexture.block(v.light) / 15f, sky = LightTexture.sky(v.light) / 15f;
        v.lightK = .25f + .75f * Math.max(block, sky * mc.level.getSkyDarken(partial));
        v.ownHead = player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        v.lastSeen = now;
    }

    private static boolean inView(Frustum frustum, ColossusVisual v) {
        return frustum == null || frustum.isVisible(new AABB(v.x - 8, v.y - 1, v.z - 8, v.x + 8, v.y + 12, v.z + 8));
    }

    /** The eyes light up as the crystals come; they gutter while the head crystal is broken. */
    static float ignite(ColossusVisual v, float now) {
        float k = ColossusBody.smooth((v.progress - .86f) / .06f);
        if (v.states[ColossusCrystal.HEAD.ordinal()] >= 3) k *= .45f + .2f * (float) Math.sin(now * 1.3f);
        return k;
    }

    private static void body(ColossusVisual v, Vec3 cam, float collapse) {
        FOOT.set(VIEW).translate((float) (v.x - cam.x), (float) (v.y - cam.y), (float) (v.z - cam.z));
        OUT.light = v.light;
        ColossusBody.draw(OUT, v.skeleton, FOOT, v.progress, collapse, v.frame, v.ownHead ? ColossusShape.HEAD : -1);
    }

    private static void crystals(ColossusVisual v, Vec3 cam, float now) {
        FOOT.set(VIEW).translate((float) (v.x - cam.x), (float) (v.y - cam.y), (float) (v.z - cam.z));
        // A little light of their own so they never go black at night, without glowing like lamps.
        OUT.light = LightTexture.pack(Math.max(9, LightTexture.block(v.light)), LightTexture.sky(v.light));
        for (int c = 0; c < ColossusVisual.CRYSTALS; c++) {
            if (v.states[c] >= 3) continue;
            float age = v.crystalAge(c);
            if (age <= 0) continue;
            TMP.set(FOOT).mul(v.cluster[c]);
            ColossusCrystals.draw(OUT, TMP, ColossusCrystals.count(v.states[c]), age, now);
        }
    }

    private static void shards(Vec3 cam, float now, Minecraft mc) {
        if (SHARDS.empty()) return;
        ColossusCrystals.Shards sh = SHARDS;
        for (int i = 0; i < ColossusCrystals.Shards.CAPACITY; i++) {
            float a = sh.age(i, now);
            if (a < 0) continue;
            sh.at(i, a, S);
            float size = S[3];
            if (size <= .02f) continue;
            double wx = sh.x[i] + S[0], wy = sh.y[i] + S[1], wz = sh.z[i] + S[2];
            float ax = sh.seed[i] - .5f, ay = .6f, az = ColossusShape.hash((int) (sh.seed[i] * 9999)) - .5f;
            float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            TMP.set(VIEW).translate((float) (wx - cam.x), (float) (wy - cam.y), (float) (wz - cam.z))
                    .rotate(sh.seed[i] * 6.3f + S[5] * sh.spin[i], ax / al, ay / al, az / al)
                    // Once it lies on the ground, it lies down.
                    .rotateX(S[4] > 0 ? 1.45f : 0)
                    .scale(sh.width[i] * size, sh.len[i] * size * .5f, sh.width[i] * size);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(wx, wy + .3, wz));
            OUT.light = LightTexture.pack(Math.max(8, LightTexture.block(light)), LightTexture.sky(light));
            ColossusCrystals.Shards.draw(OUT, TMP);
        }
    }

    // ------------------------------------------------------------------ events from ClientColossusData

    /**
     * A crystal took damage (state from -> to): the shards it loses fly off; breaking it bursts the rest
     * with a flash. Uses the cluster as it was drawn on the last frame.
     */
    public static void crystalChanged(UUID id, int index, int from, int to) {
        Minecraft mc = Minecraft.getInstance();
        ColossusVisual v = LIVE.get(id);
        if (mc.level == null || v == null || index < 0 || index >= ColossusVisual.CRYSTALS || to <= from) return;
        float now = clock(mc.level, 0);
        if (now - v.lastSeen > 10 || v.crystalAge(index) <= 0) return;
        burst(v, index, ColossusCrystals.count(from), ColossusCrystals.count(to), to >= 3, now);
        v.states[index] = to;
    }

    /** The form ended: what he looked like last becomes a ruin that crumbles away; the crystals burst. */
    public static void dissolve(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        ColossusVisual v = LIVE.remove(id);
        if (mc.level == null || v == null) return;
        float now = clock(mc.level, 0);
        if (now - v.lastSeen > 10 || v.progress < .06f) return;
        v.collapseStart = mc.level.getGameTime() + (double) mc.getFrameTime();
        for (int c = 0; c < ColossusVisual.CRYSTALS; c++)
            if (v.states[c] < 3 && v.crystalAge(c) > 4) burst(v, c, ColossusCrystals.count(v.states[c]), 0, true, now);
        while (RUINS.size() >= MAX_RUINS) RUINS.remove(0);
        RUINS.add(v);
    }

    private static void burst(ColossusVisual v, int c, int had, int keeps, boolean broken, float now) {
        float[] p = S;
        int seed = (int) (now * 7) + c * 131;
        for (int j = keeps; j < had; j++) {
            ColossusCrystals.shardPoint(v.cluster[c], j, p);
            float radius = (float) ColossusCrystal.values()[c].radius * ColossusCrystals.SIZE;
            SHARDS.spawn(v.x + p[0], v.y + p[1], v.z + p[2], p[3], p[4], p[5], .16f, p[6] * radius * .9f, p[7] * radius * .8f,
                    v.y, now, seed + j * 17);
        }
        Matrix4f m = v.cluster[c];
        double cx = v.x + m.m30(), cy = v.y + m.m31(), cz = v.z + m.m32();
        if (broken) {
            // The rest of it goes to pieces: small splinters every way.
            for (int k = 0; k < 10; k++) {
                float dx = ColossusShape.hash(seed + k * 5) - .5f, dy = ColossusShape.hash(seed + k * 5 + 1) * .8f, dz = ColossusShape.hash(seed + k * 5 + 2) - .5f;
                float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4f;
                SHARDS.spawn(cx, cy, cz, dx / l, dy / l, dz / l, .2f + .12f * ColossusShape.hash(seed + k), .22f + .18f * ColossusShape.hash(seed + k * 3),
                        .06f + .04f * ColossusShape.hash(seed + k * 7), v.y, now, seed + 400 + k);
            }
        }
        ColossusFx.flash(cx, cy, cz, broken ? 1 : 0, now);
    }

    // ------------------------------------------------------------------

    /** Into Minecraft's entity buffer: the world light, the glow slits lit from inside as they ignite. */
    private static final class Out implements ColossusBody.Sink {
        VertexConsumer v;
        int light;
        float glow;

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float u, float tv, float nx, float ny, float nz, boolean glowing) {
            if (glowing) {
                float k = .22f + .78f * glow;
                int lit = LightTexture.pack(Math.round(Mth.lerp(glow, LightTexture.block(light), 15)), Math.round(Mth.lerp(glow, LightTexture.sky(light), 15)));
                v.vertex(x, y, z, r * k, g * k, b * k, 1, u, tv, OverlayTexture.NO_OVERLAY, lit, nx, ny, nz);
            } else v.vertex(x, y, z, r, g, b, 1, u, tv, OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
        }
    }
}
