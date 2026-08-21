package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

/**
 * KRISTAL KUMESI — devin zayif noktalari.
 *
 * TASARIM: tek bir sivri degil, farkli boyut ve ACILARDA prizmalardan olusan
 * bir KUME. Referanstaki gibi bazilari yukari, bazilari CAPRAZ, bazilari
 * neredeyse YATAY cikiyor. Hepsi ayni yone baksaydi dikili cubuklar gibi
 * durur, kristal hissi olusmazdi.
 *
 * Her prizma dar taban -> genis govde -> sivri uc seklinde uc katmanli;
 * kutuyu 45 derece dondurmek dokuyu egik gosterdigi icin bu yol secildi.
 */
public final class ColossusCrystalModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(SuperheroMod.MODID, "colossus_crystal"), "main");

    private final ModelPart root;
    private final ModelPart cluster;

    /** Hasar arttikca kaybolan prizmalar — kume parca parca kiriliyor. */
    private final ModelPart shardSmallA;
    private final ModelPart shardSmallB;
    private final ModelPart shardMid;
    private final ModelPart shardMain;
    private final ModelPart shardTilted;
    private final ModelPart shardFlat;

    public ColossusCrystalModel(ModelPart root) {
        this.root = root;
        this.cluster = root.getChild("cluster");
        this.shardMain = cluster.getChild("shard_main");
        this.shardTilted = cluster.getChild("shard_tilted");
        this.shardFlat = cluster.getChild("shard_flat");
        this.shardMid = cluster.getChild("shard_mid");
        this.shardSmallA = cluster.getChild("shard_small_a");
        this.shardSmallB = cluster.getChild("shard_small_b");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition cluster = root.addOrReplaceChild("cluster",
                CubeListBuilder.create(), PartPose.ZERO);

        // ANA prizma — en buyugu, hafif egik (tam dik degil)
        prism(cluster, "shard_main", 0f, 0f, 0f, 5.0f, 17f, 0.10f, 0.30f, -0.07f);

        // CAPRAZ prizma — belirgin yatik, referanstaki gibi
        prism(cluster, "shard_tilted", 4.5f, 1f, -1.5f, 4.0f, 13f, 0.35f, -0.55f, 0.62f);

        // NEREDEYSE YATAY prizma — kumeyi yanlara dogru acan parca
        prism(cluster, "shard_flat", -5.0f, 2f, 2.0f, 3.2f, 11f, 1.15f, 0.85f, 0.25f);

        // Orta boy — arka tarafa dogru
        prism(cluster, "shard_mid", -1.5f, 1.5f, 4.0f, 3.6f, 12f, -0.42f, 1.4f, 0.18f);

        // Kucuk parcalar — kumenin tabanini dolduruyor
        prism(cluster, "shard_small_a", -4.2f, 3f, -3.5f, 2.4f, 7f, 0.22f, -1.1f, -0.35f);
        prism(cluster, "shard_small_b", 3.8f, 3.5f, 3.2f, 2.2f, 6f, -0.28f, 0.6f, 0.48f);

        return LayerDefinition.create(mesh, 16, 16);
    }

    /**
     * Tek bir kristal prizmasi: dar taban, genis govde, sivri uc.
     *
     * @param half  govdenin yari genisligi
     * @param len   prizmanin boyu
     */
    private static void prism(PartDefinition parent, String name,
                              float x, float y, float z,
                              float half, float len,
                              float xRot, float yRot, float zRot) {
        float narrow = half * 0.55f;
        float tip = half * 0.30f;

        float bodyLen = len * 0.55f;
        float tipLen = len * 0.30f;
        float baseLen = len * 0.15f;

        parent.addOrReplaceChild(name,
                CubeListBuilder.create()
                        // taban (dar)
                        .texOffs(0, 0).addBox(-narrow, -baseLen, -narrow,
                                (int) (narrow * 2), (int) baseLen, (int) (narrow * 2))
                        // govde (genis)
                        .texOffs(0, 0).addBox(-half, -(baseLen + bodyLen), -half,
                                (int) (half * 2), (int) bodyLen, (int) (half * 2))
                        // uc (sivri)
                        .texOffs(0, 0).addBox(-tip, -(baseLen + bodyLen + tipLen), -tip,
                                (int) (tip * 2), (int) tipLen, (int) (tip * 2)),
                PartPose.offsetAndRotation(x, y, z, xRot, yRot, zRot));
    }

    /**
     * Hasar durumuna gore kumeyi hazirlar.
     *
     * Catlak dokusu eklemek yerine PRIZMALAR TEK TEK KAYBOLUYOR: kume
     * parcalandikca kucuklerden baslayarak dokuluyor. Bu hem daha okunakli
     * hem de "kiriliyor" hissini dogrudan veriyor.
     *
     * @param state 0 saglam, 1 catlak, 2 agir catlak, 3 kirik
     * @return kristal cizilmeli mi
     */
    public boolean prepare(int state) {
        if (state >= 3) return false;

        shardSmallA.visible = state == 0;
        shardSmallB.visible = state == 0;
        shardFlat.visible = state <= 1;
        shardMid.visible = state <= 1;
        shardTilted.visible = true;
        shardMain.visible = true;

        // Kalan parcalar da hasarla birlikte cokuyor
        float shrink = state == 0 ? 1.0f : (state == 1 ? 0.88f : 0.72f);
        cluster.xScale = shrink;
        cluster.yScale = shrink;
        cluster.zScale = shrink;

        return true;
    }

    public ModelPart root() {
        return root;
    }
}
