package com.FIRNI.superheromod.client.render.arm;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/**
 * UZAYAN KOLUN EK PARCALARI.
 *
 * Kol kum blogu dokusuyla cizilmisti ve kotu duruyordu: uc uca dizilmis
 * kum kupleri "kol" gibi degil, havada duran bloklar gibi okunuyordu.
 * Artik uzayan kisim OYUNCUNUN KENDI SKININDEN ciziliyor -- yani kol
 * gercekten uzuyor.
 *
 * Doku eslemesi {@link BendableArm} ile ayni mantikta: 4x12x4 kolun on kol
 * yarisi texOffs(40, baseV + 6) ile aliniyor. Ayni dilim tekrar tekrar
 * kullanilinca kol kesintisiz uzuyormus gibi gorunuyor -- ek parca
 * oyuncunun teni/giysisi ile ayni renkte cikiyor.
 *
 * Bogum yuksekligi 6 piksel; vanilla on kolla ayni. Farkli olsaydi
 * dokudaki doku tekrari kaymis gorunurdu.
 */
public final class SandArm {

    /** Tek bogumun yuksekligi (piksel). */
    public static final float SEGMENT_H = 6.0f;

    private SandArm() {}

    public static LayerDefinition createLayer(boolean slim, boolean sleeve) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        int width = slim ? 3 : 4;
        float xOrigin = slim ? -2f : -3f;

        CubeDeformation deform = sleeve ? new CubeDeformation(0.25f) : CubeDeformation.NONE;
        int baseV = sleeve ? 32 : 16;

        // Bogum: pivotu ust kenarinda, boylece parca asagi dogru uzuyor
        root.addOrReplaceChild("segment",
                CubeListBuilder.create()
                        .texOffs(40, baseV + 6)
                        .addBox(xOrigin, 0.0f, -2.0f, width, 6, 4, deform),
                PartPose.ZERO);

        // YUMRUK: kolun ucundaki kutle. Onsuz kol duz bir boru gibi
        // bitiyor ve neyin vurdugu belli olmuyor. Hafifce sisirilmis ayni
        // dilim kullaniliyor ki dokusu kolun devami olarak okunsun.
        root.addOrReplaceChild("fist",
                CubeListBuilder.create()
                        .texOffs(40, baseV + 6)
                        .addBox(xOrigin, 0.0f, -2.0f, width, 5, 4,
                                new CubeDeformation(sleeve ? 1.15f : 0.9f)),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }
}
