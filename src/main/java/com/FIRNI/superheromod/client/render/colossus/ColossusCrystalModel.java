package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

/**
 * KRISTAL GEOMETRISI — devin zayif noktalari.
 *
 * Kumdan BELIRGIN sekilde farkli gorunmesi sart: oyuncu nereye vuracagini
 * bir bakista anlamali. Bu yuzden kristal hem sivri (kutu degil, elmas
 * gorunumlu) hem de ayri renkte ciziliyor.
 *
 * Elmas gorunumu icin kutuyu 45 derece dondurmek yerine, ust uste iki
 * piramitimsi katman kullaniliyor: ortasi genis, iki ucu dar. Kutu
 * dondurmek dokuyu egik gosteriyor ve kum yuzeyine oturmuyordu.
 */
public final class ColossusCrystalModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(SuperheroMod.MODID, "colossus_crystal"), "main");

    private final ModelPart root;
    private final ModelPart body;
    /** Kirilma seviyesine gore acilan catlak parcalari. */
    private final ModelPart crackLight;
    private final ModelPart crackHeavy;

    public ColossusCrystalModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("crystal_body");
        this.crackLight = body.getChild("crack_light");
        this.crackHeavy = body.getChild("crack_heavy");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Sivri elmas: dar taban -> genis orta -> dar tepe
        PartDefinition body = root.addOrReplaceChild("crystal_body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3f, -2f, -3f, 6, 4, 6)     // taban
                        .texOffs(0, 0).addBox(-5f, -8f, -5f, 10, 6, 10)   // orta
                        .texOffs(0, 0).addBox(-3f, -13f, -3f, 6, 5, 6)    // tepe
                        .texOffs(0, 0).addBox(-1.5f, -16f, -1.5f, 3, 3, 3), // uc
                PartPose.ZERO);

        // Catlak katmanlari — hasar arttikca gorunur hale gelir
        body.addOrReplaceChild("crack_light",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.4f, -9f, -1.0f, 11, 7, 2),
                PartPose.ZERO);

        body.addOrReplaceChild("crack_heavy",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.0f, -14f, -5.4f, 2, 12, 11),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 16, 16);
    }

    /**
     * Hasar durumuna gore modeli hazirlar.
     *
     * @param state 0 saglam, 1 catlak, 2 agir catlak, 3 kirik
     * @return kristal cizilmeli mi (kirik olan cizilmez)
     */
    public boolean prepare(int state) {
        if (state >= 3) return false;

        crackLight.visible = state >= 1;
        crackHeavy.visible = state >= 2;

        // Hasar aldikca kristal biraz cokar
        float shrink = state == 0 ? 1.0f : (state == 1 ? 0.92f : 0.82f);
        body.xScale = shrink;
        body.yScale = shrink;
        body.zScale = shrink;

        return true;
    }

    public ModelPart root() {
        return root;
    }
}
