package com.FIRNI.superheromod.heroes.sandman;

import net.minecraft.world.phys.Vec3;

/**
 * SAND COLOSSUS'UN ZAYIF NOKTALARI.
 *
 * Dokumandaki ana mekanik: dev cok yuksek dayanikliliga sahip ama vucudundaki
 * kristaller normal govdeye gore 2 KAT hasar alir. Boylece oyuncular sadece
 * can eritmez, devin vucudunu stratejik olarak parcalar.
 *
 * Her kristalin kirilmasinin AYRI bir bedeli var; hangi kristali once
 * kirdigin devin nasil zayifladigini belirliyor.
 */
public enum ColossusCrystal {

    // Konumlar DEVIN 10 BLOKLUK olcegine gore:
    //   alt kum kutlesi  0.0 - 3.5
    //   govde            3.5 - 7.5
    //   kafa             7.5 - 9.5
    //   omuzlar          ~7.0 hizasinda

    /** Gogus — kirilinca ultinin SURESI kisalir ve zirh duser. */
    CHEST("Gogus", new Vec3(0.0, 5.60, -1.70), 0.60, 40f),

    /** Sag omuz — kirilinca sag kolun vurusu zayiflar ve yavaslar. */
    RIGHT_SHOULDER("Sag Omuz", new Vec3(-2.90, 7.00, 0.0), 0.55, 30f),

    /** Sol omuz — ayni sekilde sol kol icin. */
    LEFT_SHOULDER("Sol Omuz", new Vec3(2.90, 7.00, 0.0), 0.55, 30f),

    /** Kafa — kirilinca dev kisa sure sersemler. */
    HEAD("Kafa", new Vec3(0.0, 8.50, -1.00), 0.40, 26f),

    /** Sirt — arkadan saldiranlar icin firsat. */
    BACK("Sirt", new Vec3(0.0, 6.00, 1.70), 0.65, 34f);

    /** Devin toplam boyu (blok). Kamera ve carpisma kutusu buna gore. */
    public static final float COLOSSUS_HEIGHT = 10.0f;
    /** Devin govde genisligi (blok). */
    public static final float COLOSSUS_WIDTH = 3.6f;

    /** Devin merkezine gore yerel konum (blok). z negatif = on taraf. */
    public final Vec3 offset;
    /** Isabet yaricapi (blok). */
    public final double radius;
    /** Kristalin dayanikliligi. */
    public final float maxHealth;
    public final String displayName;

    ColossusCrystal(String displayName, Vec3 offset, double radius, float maxHealth) {
        this.displayName = displayName;
        this.offset = offset;
        this.radius = radius;
        this.maxHealth = maxHealth;
    }

    /**
     * Gorsel hasar durumu — modelde catlak seviyesi olarak gosterilecek.
     *
     * 0 saglam, 1 catlak, 2 agir catlak, 3 kirik
     */
    public static int stateOf(float health, float maxHealth) {
        if (health <= 0f) return 3;
        float ratio = health / maxHealth;
        if (ratio > 0.66f) return 0;
        if (ratio > 0.33f) return 1;
        return 2;
    }

    /**
     * Kristalin dunya konumu.
     *
     * Devin baktigi yone gore donduruluyor; aksi halde arkadan saldiran biri
     * gogus kristaline vurabilirdi.
     */
    public Vec3 worldPosition(Vec3 base, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        // Standart Y ekseni donusu (Minecraft yaw yonune uygun)
        double x = offset.x * cos - offset.z * sin;
        double z = offset.x * sin + offset.z * cos;

        return base.add(x, offset.y, z);
    }

    /** Bone-local socket, including the complete parent rotations and growth transform. */
    public org.joml.Matrix4f socket(Vec3 base, float yaw, float growth, ColossusPose pose) {
        var m = new org.joml.Matrix4f().translation((float)base.x,(float)base.y,(float)base.z)
                .scale(growth).rotateY((float)Math.toRadians(180-yaw)).scale(-1,-1,1)
                .translate(0,-COLOSSUS_HEIGHT,0);
        transform(m,pose.torso,0,0,0);
        switch(this) {
            case CHEST -> m.translate(0,54/16f,-17/16f);
            case BACK -> m.translate(0,55/16f,16/16f);
            case HEAD -> { transform(m,pose.head,0,28,0); m.translate(0,-10/16f,-12/16f); }
            case RIGHT_SHOULDER -> { transform(m,pose.rightArm,-28,46,0); m.translate(-8/16f,4/16f,-10/16f); }
            case LEFT_SHOULDER -> { transform(m,pose.leftArm,28,46,0); m.translate(8/16f,4/16f,-10/16f); }
        }
        return m;
    }
    private static void transform(org.joml.Matrix4f m,ColossusPose.Part p,float x,float y,float z) {
        m.translate((x+p.x)/16,(y+p.y)/16,(z+p.z)/16)
                .rotateZ(p.zRot).rotateY(p.yRot).rotateX(p.xRot).scale(p.xScale,p.yScale,p.zScale);
    }
    public Vec3 worldPosition(net.minecraft.world.entity.player.Player player) {
        var pose=serverPose(player);
        var v=socket(player.position(),player.getYRot(),1,pose).transformPosition(new org.joml.Vector3f());
        return new Vec3(v.x,v.y,v.z);
    }
    public static ColossusPose serverPose(net.minecraft.world.entity.player.Player player) {
        var action = ColossusSwordController.action(player.getUUID());
        if(action==null)action=ColossusMaceController.action(player.getUUID());
        if(action==null)action=ColossusRockController.action(player.getUUID());
        var pose=ColossusPose.evaluate(player.tickCount,player.getYRot(),player.getYRot(),1,action,0,
                !SandColossusController.isArmWeakened(player.getUUID(),true));
        ColossusPose.form(pose,SandColossusController.formProgress(player.getUUID()));
        return pose;
    }
    public static Vec3 handPosition(net.minecraft.world.entity.player.Player player,boolean right) {
        var pose=serverPose(player);
        return handPosition(player.position(),player.getYRot(),pose,right);
    }
    public static Vec3 handPosition(Vec3 base,float yaw,ColossusPose pose,boolean right) {
        var m=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw))
                .scale(-1,-1,1).translate(0,-COLOSSUS_HEIGHT,0);
        transform(m,pose.torso,0,0,0);
        transform(m,right?pose.rightArm:pose.leftArm,right?-28:28,46,0);
        transform(m,right?pose.rightForearm:pose.leftForearm,right?-8:8,26,0);
        var v=m.transformPosition(new org.joml.Vector3f(0,26/16f,0));
        return base.add(v.x,v.y,v.z);
    }
    public static Vec3 swordTip(net.minecraft.world.entity.player.Player player) {
        return swordTip(player.position(),player.getYRot(),serverPose(player));
    }
    public static Vec3 swordTip(Vec3 base,float yaw,ColossusPose pose) {
        var m=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw))
                .scale(-1,-1,1).translate(0,-COLOSSUS_HEIGHT,0);
        transform(m,pose.torso,0,0,0);
        transform(m,pose.rightArm,-28,46,0);
        var v=m.transformPosition(new org.joml.Vector3f(0,120/16f*pose.sword.yScale,0));
        return base.add(v.x,v.y,v.z);
    }
}
