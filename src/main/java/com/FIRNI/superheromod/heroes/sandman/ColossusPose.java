package com.FIRNI.superheromod.heroes.sandman;
import net.minecraft.util.Mth;
/** Shared skeletal pose: rendering and crystal collision evaluate the same attack curves. */
public final class ColossusPose {
 public static final class Part {
  public float x,y,z,xRot,yRot,zRot,xScale=1,yScale=1,zScale=1;
  public boolean visible=true,skipDraw=false;
 }
 public record Action(byte type,int ticks,int duration) {
  public float progress(float partial){return Math.min(1f,(ticks+partial)/Math.max(1,duration));}
 }
 public final Part lowerMass=new Part();
 public final Part torso=new Part();
 public final Part head=new Part();
 public final Part rightArm=new Part();
 public final Part leftArm=new Part();
 public final Part rightForearm=new Part();
 public final Part leftForearm=new Part();
 public final Part mace=new Part();
 public final Part sword=new Part();
 public static ColossusPose evaluate(float age,float yaw,float massYaw,float growth,Action action,float partial,boolean right){
  ColossusPose p=new ColossusPose();p.sword.visible=false;
  animate(p,age,yaw,massYaw,growth);if(action!=null)applyAction(p,action,partial,right);return p;
 }
    private static void animate(ColossusPose m, float age, float yaw,
                                float massYawSmoothed, float growth) {
        // 1) Nefes — torso cok yavas yukari/asagi suzulur
        float breathe = Mth.sin(age * 0.045f);
        m.torso.y += breathe * 1.6f;
        m.head.xRot = 0.12f + breathe * 0.03f;   // kafa hafif one egik

        // 2) Kollar agirliga gore salinir, ikisi ayni fazda DEGIL
        m.rightArm.xRot = Mth.sin(age * 0.040f) * 0.07f;
        m.rightArm.zRot = 0.10f + Mth.sin(age * 0.031f) * 0.025f;
        m.leftArm.xRot = Mth.sin(age * 0.036f + 1.4f) * 0.07f;
        m.leftArm.zRot = -0.12f + Mth.sin(age * 0.028f) * 0.025f;
        m.rightForearm.xRot = -.10f;
        m.leftForearm.xRot = -.14f;

        // 3) Alt kutle gecikmeli doner — torso once, kum sonra
        m.lowerMass.yRot = (float) Math.toRadians(Mth.wrapDegrees(massYawSmoothed - yaw));
        // Kutle nefesle birlikte hafifce yayilir
        float spread = 1.0f + breathe * 0.02f;
        m.lowerMass.xScale = spread;
        m.lowerMass.zScale = spread;

        // Topuz elde hafifce sallanir
        m.mace.xRot = Mth.sin(age * 0.033f) * 0.05f;
    }

    /**
     * SALDIRI ANIMASYONLARI.
     *
     * Zaman cizelgeleri sunucudaki faz sinirlariyla ayni tutuldu; aksi halde
     * darbe sesi ile kolun indigi an tutmuyor ve vurus sahte gorunuyor.
     */
    private static void applyAction(ColossusPose m, Action action,
                                    float partial, boolean maceRight) {
        float t = action.progress(partial);

        if (action.type == 0) {
            maceSwing(m, t, maceRight);
        } else if (action.type == 1) {
            rockThrow(m, .43f + t * .57f, maceRight);
        } else if (action.type == com.FIRNI.superheromod.network.packet.ColossusActionPacket.ROCK_HOLD) {
            rockThrow(m, .43f * Math.min(1f, (action.ticks + partial) / 9f), maceRight);
        } else if (action.type == com.FIRNI.superheromod.network.packet.ColossusActionPacket.SWORD_STAB) {
            float tick = action.ticks + partial;
            float grow = ease(tick / 12f) * (1f - ease((tick - 30f) / 14f));
            m.mace.visible = false;
            m.sword.visible = grow > .001f;
            m.sword.yScale = grow;
            float contact = ease((tick-12f)/6f)*(1-ease((tick-30f)/14f));
            float sweep = ease((tick-18f)/12f);
            m.rightArm.xRot = -.9f * contact;
            m.rightArm.yRot = (1f-2f*sweep) * contact;
            m.rightArm.y = 40f * contact;
            m.rightArm.z = -40f * contact;
            m.rightArm.zRot = 0;
            m.rightArm.skipDraw = grow>.08f;
            m.rightForearm.visible = grow<=.08f;
            m.rightForearm.xRot = 0;
            m.torso.xRot = 0;
            m.leftArm.xRot = -.3f * contact;
            m.head.xRot = .22f * grow;
        }
    }

    /**
     * TOPUZ VURUSU: kaldir -> TEPEDE BEKLE -> indir -> toparlan.
     *
     * Tepedeki bekleme bilerek var; topuz kesintisiz inerse darbe hafif
     * kaliyor. Agirlik hissini veren sey o duraklama.
     */
    private static void maceSwing(ColossusPose m, float t, boolean maceRight) {
        Part arm = maceRight ? m.rightArm : m.leftArm;
        Part other = maceRight ? m.leftArm : m.rightArm;

        float armX;
        float torsoLean;

        if (t < 0.31f) {
            // Kaldirma (0-10 tick)
            float p = ease(t / 0.31f);
            armX = lerp(p, 0f, -2.45f);
            torsoLean = lerp(p, 0f, -0.16f);
        } else if (t < 0.50f) {
            // Tepede bekleme (10-16 tick) — hafif titreme
            armX = -2.45f;
            torsoLean = -0.16f;
        } else if (t < 0.66f) {
            // Inis (16-21 tick) — hizli
            float p = (t - 0.50f) / 0.16f;
            p = p * p;   // hizlanarak insin
            armX = lerp(p, -2.45f, -0.10f);
            torsoLean = lerp(p, -0.16f, 0.34f);
        } else {
            // Toparlanma (21-32 tick)
            float p = ease((t - 0.66f) / 0.34f);
            armX = lerp(p, -0.10f, 0f);
            torsoLean = lerp(p, 0.34f, 0f);
        }

        arm.xRot = armX;
        arm.zRot += maceRight ? -0.18f : 0.18f;
        (maceRight ? m.rightForearm : m.leftForearm).xRot = -.38f * Mth.sin(t * Mth.PI);
        m.torso.xRot = torsoLean * .15f;
        // The body follows the strike downward; the new shorter fist must actually reach the floor.
        float contact = t < .5f ? 0 : t < .66f ? ease((t-.5f)/.16f) : 1-ease((t-.66f)/.34f);
        m.torso.y += 56f * contact;
        m.torso.z -= 64f * contact;
        m.head.xRot = 0.12f + torsoLean * 0.5f;

        // Diger kol dengeleme icin ters yone gider
        other.xRot = -armX * 0.22f;
    }

    /**
     * KAYA FIRLATMA: kolu geri cek -> savur -> toparlan.
     *
     * Govde de doner; sadece kol hareket ederse firlatma guclu gorunmuyor.
     */
    private static void rockThrow(ColossusPose m, float t, boolean maceRight) {
        // Kaya topuz TUTMAYAN elde
        Part arm = maceRight ? m.leftArm : m.rightArm;
        float side = maceRight ? 1f : -1f;

        float armX;
        float twist;

        if (t < 0.43f) {
            // Geri cekme (0-9 tick)
            float p = ease(t / 0.43f);
            armX = lerp(p, 0f, 0.95f);
            twist = lerp(p, 0f, 0.34f * side);
        } else if (t < 0.60f) {
            // Savurma — cok hizli
            float p = (t - 0.43f) / 0.17f;
            p = p * p;
            armX = lerp(p, 0.95f, -2.10f);
            twist = lerp(p, 0.34f * side, -0.30f * side);
        } else {
            // Toparlanma
            float p = ease((t - 0.60f) / 0.40f);
            armX = lerp(p, -2.10f, 0f);
            twist = lerp(p, -0.30f * side, 0f);
        }

        arm.xRot = armX;
        arm.zRot += 0.20f * side;
        (maceRight ? m.leftForearm : m.rightForearm).xRot = -.65f * Mth.sin(t * Mth.PI);
        m.torso.yRot = twist;
        m.head.yRot = -twist * 0.4f;
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    /** Gather, collapse onto both palms, then push the torso upright. Lower dune stays planted. */
    public static void form(ColossusPose m,float progress) {
        if(progress>=1)return;
        float fall=ease((progress-.40f)/.20f);
        float stand=ease((progress-.72f)/.28f);
        float brace=fall*(1-stand);
        float bend=.82f*brace;
        m.mace.visible=false;
        m.torso.xRot=bend;
        m.torso.y=90f*(1-Mth.cos(bend))+64f*brace;
        m.torso.z=-90f*Mth.sin(bend);
        m.rightArm.xRot=-1.72f*brace;
        m.leftArm.xRot=-1.72f*brace;
        m.rightArm.zRot=.06f*brace;
        m.leftArm.zRot=-.06f*brace;
        m.rightForearm.xRot=0;
        m.leftForearm.xRot=0;
        m.head.xRot=-.22f*brace;
    }

    /** Yumusak giris/cikis. */
    private static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }


}
