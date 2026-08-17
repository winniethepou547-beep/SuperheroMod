package com.FIRNI.superheromod.client.render.arm;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/**
 * BUKULEBILIR KOL — vanilla oyuncu modelinde dirsek eklemi yok, kol tek
 * parca 4x12x4 bir kutu. Burada ayni kolu IKI parcaya boluyoruz:
 *
 *   ust kol : y = -2..4   (6 piksel)
 *   on kol  : y =  4..10  (6 piksel), pivotu y=4 -> DIRSEK
 *
 * Doku eslemesi onemli: 4x12x4 kolun yan yuzleri v=20..32 arasinda. Ust yari
 * icin texOffs(40,16) -> v=20..26, alt yari icin texOffs(40,22) -> v=26..32.
 * Boylece oyuncunun kendi skini bozulmadan bolunuyor.
 *
 * Kol kalinligi: normal model 4, slim (Alex) model 3 piksel.
 */
public final class BendableArm {

    /** Dirsegin kol icindeki y konumu (piksel). */
    public static final float ELBOW_Y = 4.0f;

    private BendableArm() {}

    public static LayerDefinition createLayer(boolean slim, boolean sleeve) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        int width = slim ? 3 : 4;
        float xOrigin = slim ? -2f : -3f;

        // Sleeve (dis katman) hafif buyutulur — vanilla ile ayni deformasyon
        CubeDeformation deform = sleeve ? new CubeDeformation(0.25f) : CubeDeformation.NONE;

        // Sag kol govdesi v=16, sleeve v=32
        int baseV = sleeve ? 32 : 16;

        root.addOrReplaceChild("upper",
                CubeListBuilder.create()
                        .texOffs(40, baseV)
                        .addBox(xOrigin, -2.0f, -2.0f, width, 6, 4, deform),
                PartPose.ZERO);

        // On kol: kutu y=0..6, parca y=4'e kaydirilmis -> pivot dirsekte
        root.addOrReplaceChild("fore",
                CubeListBuilder.create()
                        .texOffs(40, baseV + 6)
                        .addBox(xOrigin, 0.0f, -2.0f, width, 6, 4, deform),
                PartPose.offset(0.0f, ELBOW_Y, 0.0f));

        return LayerDefinition.create(mesh, 64, 64);
    }
}
