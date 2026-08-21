package com.FIRNI.superheromod.client.render.entity;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.sandman.SandSoldierEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Kum askerinin modeli.
 *
 * Insan oranlarindan esinleniyor ama birebir Steve degil: omuzlar daha genis,
 * uzuvlar daha kalin ve govdeye kum yumrulari eklenmis. Yumrular ana parcalarin
 * COCUGU olarak ekleniyor, boylece animasyonda kendiliginden takip ediyorlar.
 *
 * Doku olarak vanilla kum blogu kullaniliyor; bu yuzden atlas 16x16 ve tum
 * yuzler ayni bolgeden orneklem aliyor. Kum dokusu duzgun bir desen olmadigi
 * icin bu tekrar goze batmiyor ve ayri bir doku dosyasina gerek kalmiyor.
 */
public class SandSoldierModel<T extends SandSoldierEntity> extends EntityModel<T> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(SuperheroMod.MODID, "sand_soldier"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    /** Olusmanin ilk asamasindaki yer kum yigini. */
    private final ModelPart mound;

    private final ModelPart rightSpikes;
    private final ModelPart leftSpikes;
    /** Dev vurusunda ellerin arasinda olusan dikenli kum kutlesi. */
    private final ModelPart slamMass;

    public SandSoldierModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.mound = root.getChild("mound");

        this.rightSpikes = rightArm.getChild("right_spikes");
        this.leftSpikes = leftArm.getChild("left_spikes");
        this.slamMass = root.getChild("slam_mass");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // --- Kafa ---
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8),
                PartPose.ZERO);
        // Kafanin uzerinde duzensiz kum yumrulari
        head.addOrReplaceChild("head_chunk_a",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.0f, -10.0f, -2.0f, 4, 2, 4),
                PartPose.ZERO);
        head.addOrReplaceChild("head_chunk_b",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(2.0f, -7.0f, -5.0f, 3, 3, 2),
                PartPose.ZERO);

        // --- Govde: Steve'den daha genis ve kalin ---
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.5f, 0.0f, -2.5f, 9, 12, 5),
                PartPose.ZERO);
        body.addOrReplaceChild("chest_chunk",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.0f, 1.0f, -4.0f, 6, 5, 2),
                PartPose.ZERO);
        body.addOrReplaceChild("back_chunk",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.0f, 4.0f, 2.5f, 4, 6, 2),
                PartPose.ZERO);

        // --- Kollar: kalin, omuzda kum yumrusu ---
        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -2.0f, -2.5f, 5, 12, 5),
                PartPose.offset(-5.5f, 2.0f, 0.0f));
        rightArm.addOrReplaceChild("right_shoulder",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.0f, -3.5f, -3.5f, 7, 4, 7),
                PartPose.ZERO);

        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.0f, -2.0f, -2.5f, 5, 12, 5),
                PartPose.offset(5.5f, 2.0f, 0.0f));
        leftArm.addOrReplaceChild("left_shoulder",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.0f, -3.5f, -3.5f, 7, 4, 7),
                PartPose.ZERO);

        // AYRI EL PARCASI YOK.
        //
        // Once cekic, sonra ayri bir el kutusu denendi; ikisi de kolun ucuna
        // yapistirilmis yabanci bir kutle gibi durdu ve dev askerde kolla
        // el arasinda gorunur bir BOSLUK biraktı (kol ile elin kalinliklari
        // ayri olceklendigi icin). Kol duz kaliyor; vurus aninda KOLUN
        // TAMAMI hafifce kalinlasiyor. Tek parca oldugu icin bosluk da
        // olusamiyor.

        // --- MENZILLI TURUN OMUZ DIKENLERI: yukari dogru dik cikintilar ---
        // Mermi bunlarin arasindan cikiyor; siluetten tur aninda anlasilmali
        rightArm.addOrReplaceChild("right_spikes",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.5f, -12.0f, -2.0f, 3, 10, 3)
                        .texOffs(0, 0).addBox(-2.0f, -16.0f, -1.5f, 2, 13, 2)
                        .texOffs(0, 0).addBox(-6.0f, -9.0f, 0.5f, 2, 8, 2),
                PartPose.rotation(0.10f, 0f, -0.22f));

        leftArm.addOrReplaceChild("left_spikes",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(1.5f, -12.0f, -2.0f, 3, 10, 3)
                        .texOffs(0, 0).addBox(0.0f, -16.0f, -1.5f, 2, 13, 2)
                        .texOffs(0, 0).addBox(4.0f, -9.0f, 0.5f, 2, 8, 2),
                PartPose.rotation(0.10f, 0f, 0.22f));

        // --- Bacaklar ---
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.5f, 0.0f, -2.5f, 5, 12, 5),
                PartPose.offset(-2.2f, 12.0f, 0.0f));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.5f, 0.0f, -2.5f, 5, 12, 5),
                PartPose.offset(2.2f, 12.0f, 0.0f));

        // --- DEV VURUSUNUN KUM KUTLESI: ellerin arasinda olusur ---
        // Govdeye degil KOKE bagli, cunku iki elin ortasinda durmasi gerekiyor
        root.addOrReplaceChild("slam_mass",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-8.0f, -12.0f, -8.0f, 16, 14, 16)
                        // dort yana cikan dikenler
                        .texOffs(0, 0).addBox(-12.0f, -9.0f, -3.0f, 5, 7, 6)
                        .texOffs(0, 0).addBox(7.0f, -9.0f, -3.0f, 5, 7, 6)
                        .texOffs(0, 0).addBox(-3.0f, -9.0f, -12.0f, 6, 7, 5)
                        .texOffs(0, 0).addBox(-3.0f, -9.0f, 7.0f, 6, 7, 5)
                        // tepe dikeni
                        .texOffs(0, 0).addBox(-4.0f, -18.0f, -4.0f, 8, 7, 8),
                PartPose.offset(0.0f, -8.0f, 0.0f));

        // --- Yer kum yigini (sadece olusmanin basinda) ---
        root.addOrReplaceChild("mound",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-6.0f, 0.0f, -6.0f, 12, 3, 12),
                PartPose.offset(0.0f, 21.0f, 0.0f));

        return LayerDefinition.create(mesh, 16, 16);
    }

    // ------------------------------------------------------------------

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {

        float progress = entity.getSpawnProgress();

        // --- Normal duruş ve yuruyus ---
        head.xRot = headPitch * ((float) Math.PI / 180f);
        head.yRot = netHeadYaw * ((float) Math.PI / 180f);

        rightArm.xRot = Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * 1.4f * limbSwingAmount;
        leftArm.xRot = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        rightLeg.xRot = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        leftLeg.xRot = Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * 1.4f * limbSwingAmount;

        rightArm.zRot = 0.08f;
        leftArm.zRot = -0.08f;
        body.xRot = 0f;

        // Kum kutlesi oldugu icin hafif nefes/oturma hareketi
        float breathe = Mth.sin(ageInTicks * 0.06f) * 0.02f;
        body.y = breathe;

        // Kol olcegi HER KAREDE sifirlanmali: applyHammers taban kalinligi
        // korumak icin CARPIYOR, sifirlanmazsa her karede birikip sonsuza
        // buyurdu. applyBulk kendi mutlak degerlerini bunun ustune yaziyor.
        rightArm.xScale = 1.0f;
        rightArm.zScale = 1.0f;
        leftArm.xScale = 1.0f;
        leftArm.zScale = 1.0f;

        applySpawnStages(progress);
        applyBulk(entity);
        applyHammers(entity);
        // Dev asker de savuruyor: agir vurus arasindaki DUZ OTO SALDIRISI.
        // Agir vurus sirasinda devre disi -- ikisi ust uste binmemeli.
        applySwing(entity, entity.getSwingProgress());
        applySlam(entity);
        applyAimBlock(entity);
    }

    /**
     * DEV ASKERIN AGIR VURUSU.
     *
     * Eller havaya kalkip tepede birlesir, aralarinda dikenli kum kutlesi
     * olusur, sonra hep birlikte yere iner.
     *
     * Kutle KOKE bagli, kollara degil: iki elin TAM ORTASINDA durmasi
     * gerekiyor, tek bir kola baglansa yana kayardi.
     */
    private void applySlam(T entity) {
        float slam = entity.getSlamProgress();

        if (slam <= 0.001f) {
            slamMass.visible = false;
            return;
        }

        // Faz sinirlari GiantSlamGoal ile ayni oranlarda
        float raiseEnd = 14f / 44f;
        float gatherEnd = 24f / 44f;
        float impact = 30f / 44f;

        float armX;      // kollarin kalkma acisi
        float massScale; // kutlenin buyuklugu
        float massY;     // kutlenin yuksekligi
        float massZ;     // kutlenin ONE gitmesi

        if (slam <= raiseEnd) {
            float p = ease(slam / raiseEnd);
            armX = -3.05f * p;
            massScale = 0f;
            massY = -8f;
            massZ = 0f;
        } else if (slam <= gatherEnd) {
            float p = (slam - raiseEnd) / (gatherEnd - raiseEnd);
            armX = -3.05f;
            massScale = ease(p);
            massY = -8f;
            massZ = 0f;
        } else if (slam <= impact) {
            // INIS — hizlanarak, ve ONE dogru.
            // Onceden kutle sadece asagi iniyordu; kafanin ustunde oldugu
            // icin "elinden kayip kafasina dustu" gibi gorunuyordu. Kutlenin
            // kollarla birlikte ONE savrulmasi gerekiyor.
            float p = (slam - gatherEnd) / (impact - gatherEnd);
            p = p * p;
            armX = -3.05f + p * 4.25f;
            massScale = 1f;
            // Zemine kadar in (model uzayinda y asagi dogru artar)
            massY = -8f + p * 30f;
            // Modelde on taraf -Z; kutle devin onune ~2.5 blok gider
            massZ = -p * 34f;
        } else {
            // Toparlanma — kutle yerde dagilir
            float p = ease((slam - impact) / (1f - impact));
            armX = 1.20f * (1f - p);
            massScale = Math.max(0f, 1f - p * 2.2f);
            massY = 22f;
            massZ = -34f;
        }

        rightArm.xRot = armX;
        leftArm.xRot = armX;
        // Eller tepede BIRLESIR — omuzdan ice dogru kapanir
        float close = Math.min(1f, slam / gatherEnd);
        rightArm.zRot = 0.38f * close;
        leftArm.zRot = -0.38f * close;

        body.xRot = armX * 0.10f;

        slamMass.visible = massScale > 0.01f;
        if (slamMass.visible) {
            slamMass.xScale = massScale;
            slamMass.yScale = massScale;
            slamMass.zScale = massScale;
            slamMass.y = massY;
            slamMass.z = massZ;
            // Kutle yavasca doner — durgun durmasin
            slamMass.yRot = slam * 3.2f;
        }
    }

    /**
     * MENZILLI TURUN MERMISI — nisan alirken kafanin ustunde olusan kucuk
     * kum blogu.
     *
     * Dev askerin buyuk kutlesiyle ayni parca kullaniliyor, sadece cok
     * kucuk olcekte: ikisi de "kumdan bir kutle topluyor" fikri, biri dev
     * biri minik.
     */
    private void applyAimBlock(T entity) {
        if (entity.getVariant() != SandSoldierEntity.Variant.RANGED) return;

        float aim = entity.getAimProgress();
        if (aim <= 0.02f) return;

        slamMass.visible = true;

        // Minik blok — devin kutlesinin altida biri
        float scale = 0.16f * aim;
        slamMass.xScale = scale;
        slamMass.yScale = scale;
        slamMass.zScale = scale;

        // Kafanin hemen ustunde
        slamMass.y = -12f;
        slamMass.z = 0f;
        slamMass.yRot = entity.tickCount * 0.18f;
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * CEKIC ELLERI — hedefe yaklasinca acilir, uzaklasinca cozulur.
     *
     * Aniden belirmiyor: olusma animasyonundaki gibi olcekle buyuyor.
     * Menzilli turde cekic yerine omuz dikenleri gorunur.
     */
    private void applyHammers(T entity) {
        boolean ranged = entity.getVariant() == SandSoldierEntity.Variant.RANGED;

        rightSpikes.visible = ranged;
        leftSpikes.visible = ranged;

        if (ranged) {
            // Nisan alirken dikenler geriye yatip yuklenir
            float aim = entity.getAimProgress();
            rightSpikes.xRot = 0.10f - aim * 0.45f;
            leftSpikes.xRot = 0.10f - aim * 0.45f;
        }

        // KOL DUZ KALIR, sadece HAFIFCE kalinlasir.
        //
        // Once cekic, sonra ayri bir el kutusu denendi; ikisi de kolun ucuna
        // takilmis yabanci bir parca gibi durdu ve dev askerde kolla el
        // arasinda gorunur bir BOSLUK biraktı, cunku ikisinin kalinliklari
        // ayri olceklendiriliyordu. Artik ek geometri yok: vuracak kolun
        // TAMAMI vurus aninda az miktarda sisiyor.
        //
        // Carpan olcek kullaniliyor; kolun taban kalinligini applyBulk
        // (dev / BLADE / BREAKER) belirliyor ve o oran korunmali.
        float ready = entity.getHammerProgress();
        float swing = entity.getSwingProgress();
        float punch = swing > 0f ? Mth.sin(swing * (float) Math.PI) : 0f;

        float thick = 1.0f + ready * 0.05f + punch * 0.13f;
        boolean rightSwings = entity.getSwingDirection() != 2;

        thickenArm(rightArm, rightSwings ? thick : 1.0f);
        thickenArm(leftArm, rightSwings ? 1.0f : thick);
    }

    /** Kolun MEVCUT kalinligini carpar; taban orani applyBulk'tan gelir. */
    private static void thickenArm(ModelPart arm, float factor) {
        arm.xScale *= factor;
        arm.zScale *= factor;
    }

    /**
     * SALDIRI SALINIMI — cekic saldan sola veya soldan saga savrulur.
     *
     * Yon askerin kendi rastgele secimi; hep ayni yon robotik duruyordu.
     * Bel de salinimla birlikte doner, yoksa sadece kol oynamis gibi
     * gorunuyor ve vurusta agirlik hissi olusmuyor.
     */
    private void applySwing(T entity, float attack) {
        if (attack <= 0f) {
            body.yRot = 0f;
            return;
        }

        byte dir = entity.getSwingDirection();
        if (dir == 0) return;

        float side = dir == 1 ? 1f : -1f;

        // Uc asama: kurulum -> savurma -> toparlanma. Tek bir sinus egrisi
        // kullanildiginda vurusun nerede basladigi belli olmuyordu.
        float windup = Mth.clamp(attack / 0.35f, 0f, 1f);
        float sweep = Mth.clamp((attack - 0.35f) / 0.40f, 0f, 1f);
        float recover = Mth.clamp((attack - 0.75f) / 0.25f, 0f, 1f);

        // KOL ONCE YATAY DURUMA GETIRILIR. Bu sart:
        // asagi sarkan bir kolda yRot kolu sadece KENDI EKSENINDE dondurur,
        // yani yatay savurma hic gorunmez. Onceki surumde tek gorunen sey
        // xRot'un iki kolu da yukari kaldirmasiydi -- "iki elini alttan
        // yukari kaldiriyor" goruntusu tam olarak bundan geliyordu.
        float lift = -1.45f * (windup - recover * 0.85f);

        // Savurma yayi: bir yandan otekine gecis
        float arc = (sweep * 2f - 1f) * 1.7f * side;

        ModelPart swingArm = side > 0 ? rightArm : leftArm;
        ModelPart idleArm = side > 0 ? leftArm : rightArm;

        swingArm.xRot = lift;
        swingArm.yRot = arc;
        swingArm.zRot = 0.25f * side * (1f - sweep);

        // Diger kol NEREDEYSE HAREKETSIZ. Ikisi birden oynayinca vurus
        // savurma degil "iki elini kaldirma" gibi okunuyordu.
        idleArm.xRot = -0.15f * windup;
        idleArm.yRot = 0f;
        idleArm.zRot = -0.10f * side;

        // Bel savurmayi takip eder; yoksa sadece kol oynamis gibi duruyor
        // ve vurusta agirlik hissi olusmuyor.
        body.yRot = -arc * 0.30f;
        head.yRot += arc * 0.15f;
    }

    /**
     * Dev asker ayni geometriyi kullanir ama oranlari degisir: omuzlar ve
     * govde kalinlasir, kafa govdeye gore KUCULUR. Boylece sadece buyutulmus
     * bir asker degil, agir bir dev gibi duruyor.
     */
    private void applyBulk(T entity) {
        if (entity.isForming()) return;   // olusma asamalari olcegi kendi yonetiyor

        if (!(entity instanceof com.FIRNI.superheromod.heroes.sandman.GiantSandSoldierEntity)) {
            applyVariantShape(entity);
            return;
        }

        body.xScale = 1.18f;
        body.zScale = 1.15f;

        rightArm.xScale = 1.25f;
        rightArm.zScale = 1.25f;
        leftArm.xScale = 1.25f;
        leftArm.zScale = 1.25f;

        rightLeg.xScale = 1.15f;
        rightLeg.zScale = 1.15f;
        leftLeg.xScale = 1.15f;
        leftLeg.zScale = 1.15f;

        head.xScale = 0.88f;
        head.yScale = 0.88f;
        head.zScale = 0.88f;
    }

    /**
     * Asker turune gore govde orani.
     *
     * BLADE ince ve cevik, BREAKER iri ve agir gorunur. Blockbench modelleri
     * gelince buranin yerini gercek geometri alacak; simdilik ayni modeli
     * olceklendirerek iki turu birbirinden ayirt edilebilir yapiyoruz.
     */
    private void applyVariantShape(T entity) {
        switch (entity.getVariant()) {
            case BLADE -> {
                body.xScale = 0.88f;
                body.zScale = 0.85f;
                rightArm.xScale = 0.78f;
                rightArm.zScale = 0.78f;
                leftArm.xScale = 0.78f;
                leftArm.zScale = 0.78f;
                rightLeg.xScale = 0.9f;
                leftLeg.xScale = 0.9f;
            }
            case BREAKER -> {
                body.xScale = 1.22f;
                body.zScale = 1.18f;
                rightArm.xScale = 1.4f;
                rightArm.zScale = 1.4f;
                leftArm.xScale = 1.4f;
                leftArm.zScale = 1.4f;
                rightLeg.xScale = 1.12f;
                leftLeg.xScale = 1.12f;
                // Boynu yok gibi dursun
                head.yScale = 0.92f;
            }
        }
    }

    /**
     * Dokumandaki 8 asamali olusma.
     *
     * Bacak ve govdede sadece olcek degil KONUM da kaydiriliyor: yalnizca
     * yScale kucultulseydi uzuvlar pivotlarindan asagi/yukari sarkardi.
     * Bacaklarin tabani yerde, govdenin tabani kalcada sabit tutuluyor;
     * boylece asker gercekten yerden yukseliyormus gibi duruyor.
     */
    private void applySpawnStages(float progress) {
        boolean complete = progress >= 1.0f;

        // 8. asama: yigin kaybolur
        mound.visible = !complete;
        if (!complete) {
            float moundFade = 1.0f - Mth.clamp((progress - 0.75f) / 0.25f, 0f, 1f);
            float moundGrow = Mth.clamp(progress / 0.125f, 0f, 1f);
            float scale = Math.max(0.01f, moundGrow * moundFade);
            mound.xScale = scale;
            mound.zScale = scale;
            mound.yScale = Math.max(0.01f, moundFade);
        }

        if (complete) {
            resetScales();
            return;
        }

        // 2-3. asama: ayaklar, sonra bacaklar yukselir
        float legs = stage(progress, 0.125f, 0.375f);
        setLimb(rightLeg, legs, -2.2f, 12.0f, 12.0f);
        setLimb(leftLeg, legs, 2.2f, 12.0f, 12.0f);

        // 4. asama: govde olusur — tabani kalcada sabit
        float torso = stage(progress, 0.375f, 0.5f);
        body.visible = torso > 0f;
        body.yScale = Math.max(0.01f, torso);
        body.xScale = Mth.lerp(torso, 0.6f, 1.0f);
        body.zScale = Mth.lerp(torso, 0.6f, 1.0f);
        body.y = 12.0f - 12.0f * torso;

        // 5. asama: kollar
        float arms = stage(progress, 0.5f, 0.625f);
        setLimb(rightArm, arms, -5.5f, 2.0f, 0.0f);
        setLimb(leftArm, arms, 5.5f, 2.0f, 0.0f);

        // 6-7. asama: kafa, sonra yuz detaylari
        float headStage = stage(progress, 0.625f, 0.875f);
        head.visible = headStage > 0f;
        float headScale = Math.max(0.01f, headStage);
        head.xScale = headScale;
        head.yScale = headScale;
        head.zScale = headScale;
        head.y = 12.0f - 12.0f * torso;   // govdeyle birlikte yukselir
    }

    /**
     * Uzvu buyutur ve tabanini sabit tutar.
     *
     * @param anchorDrop uzvun tam boydayken pivotunun asagi kayacagi miktar
     */
    private void setLimb(ModelPart part, float amount, float x, float baseY, float anchorDrop) {
        part.visible = amount > 0f;
        float scale = Math.max(0.01f, amount);

        part.xScale = scale;
        part.yScale = scale;
        part.zScale = scale;
        part.x = x;
        part.y = baseY + anchorDrop * (1.0f - amount);
    }

    private void resetScales() {
        for (ModelPart part : new ModelPart[]{head, body, rightArm, leftArm, rightLeg, leftLeg}) {
            part.visible = true;
            part.xScale = 1f;
            part.yScale = 1f;
            part.zScale = 1f;
        }
        head.y = 0f;
        rightArm.x = -5.5f;
        rightArm.y = 2f;
        leftArm.x = 5.5f;
        leftArm.y = 2f;
        rightLeg.x = -2.2f;
        rightLeg.y = 12f;
        leftLeg.x = 2.2f;
        leftLeg.y = 12f;
    }

    /** progress'in [from, to] araligindaki 0..1 karsiligi. */
    private static float stage(float progress, float from, float to) {
        return Mth.clamp((progress - from) / (to - from), 0f, 1f);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight,
                               int packedOverlay, float r, float g, float b, float a) {
        root.render(pose, buffer, packedLight, packedOverlay, r, g, b, a);
    }
}
