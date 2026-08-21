package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Istemcide cizilecek kum sekilleri -- ARA DEGERLI.
 *
 * Sunucu saniyede 20 guncelleme gonderiyor, ekran 60+ kare ciziyor. Gelen
 * degerler dogrudan cizilirse sekil saniyede 20 kez ziplar ve hareket
 * "kasiyor" gibi gorunur; kullanicinin bildirdigi takilma tam olarak buydu.
 *
 * Burasi son IKI guncellemeyi saklayip aradaki degeri hesapliyor. Eslestirme
 * seklin kimligi uzerinden: kimlik olmasa hangi seklin hangisinin devami
 * oldugu bilinemez ve ara deger uretilemezdi.
 */
public final class ClientSandShapeData {

    /** Sunucu tick suresi (ms). Ara deger bu pencereye yayiliyor. */
    private static final float TICK_MS = 50f;

    private static volatile Map<Integer, SandShapeSyncPacket.Shape> previous = Collections.emptyMap();
    private static volatile Map<Integer, SandShapeSyncPacket.Shape> current = Collections.emptyMap();
    private static volatile long currentTime = 0L;

    private ClientSandShapeData() {}

    public static void set(List<SandShapeSyncPacket.Shape> list) {
        Map<Integer, SandShapeSyncPacket.Shape> next = new HashMap<>();
        for (SandShapeSyncPacket.Shape s : list) next.put(s.id(), s);

        previous = current;
        current = next;
        currentTime = System.currentTimeMillis();
    }

    /**
     * O an cizilecek sekiller, ara degerleri uygulanmis halde.
     *
     * Bir onceki karede olmayan sekil ara degere girmiyor: yeni beliren bir
     * sekli sifirdan suruklemek onu haritanin oteki ucundan ucurur.
     */
    public static List<SandShapeSyncPacket.Shape> get() {
        Map<Integer, SandShapeSyncPacket.Shape> cur = current;
        if (cur.isEmpty()) return Collections.emptyList();

        long age = System.currentTimeMillis() - currentTime;

        // Sunucu susarsa sekiller ekranda asili kalmasin
        if (age > 800L) return Collections.emptyList();

        // Ara deger 0..1 arasi; gecikme olursa son degere kilitlenir
        float t = Math.min(1f, age / TICK_MS);

        Map<Integer, SandShapeSyncPacket.Shape> prev = previous;
        List<SandShapeSyncPacket.Shape> out = new ArrayList<>(cur.size());

        for (SandShapeSyncPacket.Shape now : cur.values()) {
            SandShapeSyncPacket.Shape before = prev.get(now.id());
            out.add(before == null ? now : lerp(before, now, t));
        }
        return out;
    }

    private static SandShapeSyncPacket.Shape lerp(SandShapeSyncPacket.Shape a,
                                                  SandShapeSyncPacket.Shape b, float t) {
        return new SandShapeSyncPacket.Shape(
                b.id(), b.type(),
                a.x() + (b.x() - a.x()) * t,
                a.y() + (b.y() - a.y()) * t,
                a.z() + (b.z() - a.z()) * t,
                lerpAngle(a.yaw(), b.yaw(), t),
                a.grow() + (b.grow() - a.grow()) * t,
                a.curl() + (b.curl() - a.curl()) * t,
                a.sink() + (b.sink() - a.sink()) * t);
    }

    /**
     * Aci ara degeri KISA YOLDAN.
     *
     * Duz ara deger 179 dereceden -179 dereceye giderken sekli tam tur
     * dondururdu -- tek karede firil firil donen bir el.
     */
    private static float lerpAngle(float a, float b, float t) {
        float delta = ((b - a) % 360f + 540f) % 360f - 180f;
        return a + delta * t;
    }

    public static void clear() {
        previous = Collections.emptyMap();
        current = Collections.emptyMap();
    }
}
