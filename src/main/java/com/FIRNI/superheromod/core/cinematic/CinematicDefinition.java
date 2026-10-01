package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bir sinematigin tam tanimi: cekim listesi + olay listesi.
 *
 * Karaktere ozel hicbir sey icermez; her kahraman kendi tanimini kurar ve
 * CinematicRegistry'e kaydeder. Motor bu tanimi oynatir.
 */
public final class CinematicDefinition {

    public final String id;
    public final List<Shot> shots;
    public final List<Beat> beats;
    public final int totalTicks;

    /** Sinematik boyunca sinema bantlari gosterilsin mi. */
    public final boolean letterbox;
    /** Sahne kurulurken hedefin sabitlenecegi yerel konum (null = oldugu yer). */
    public final Vec3 targetAnchor;

    /**
     * TABAN ATMOSFER — cekim kendi sisini yazmadiysa bu kullanilir.
     *
     * Her cekime tek tek sis yazmak zorunda kalinsaydi pratikte hicbir cekimde
     * yazilmazdi. Sinematigin geneli buradan gelir, cekimler sadece SAPMA
     * yaptiginda kendi degerini verir.
     */
    public final float baseFogNear;
    public final float baseFogFar;
    public final int baseFogColor;

    /** Aktorlerin poz anahtarlari — sirali. */
    public final List<PoseKey> poseKeys;
    public final List<CinematicActorTrack> actorTracks;
    /** Zero keeps the legacy distance; authored scenes can use a fixed virtual stage. */
    public final double stageSpan;
    public final boolean isolatedStage;
    public final List<CinematicSetPiece> setPieces;
    public final List<CinematicHandContact> handContacts;
    public final List<CinematicImpact> impacts;

    /** Sahnedeki iki aktor. */
    public enum Actor { ATTACKER, TARGET }

    /**
     * Bir aktorun belirli bir tick'teki pozu.
     *
     * @param chain eklem basina gecikme — kuvvetin vucuttan gecmesini saglar.
     *              null ise tum eklemler ayni anda hareket eder (mekanik durur).
     */
    public record PoseKey(int tick, Actor actor, ActorPose pose, float[] chain) {}

    private CinematicDefinition(Builder b) {
        this.id = b.id;
        this.actorTracks = List.copyOf(b.actorTracks);
        this.stageSpan = b.stageSpan;
        this.isolatedStage = b.isolatedStage;
        this.setPieces = List.copyOf(b.setPieces);
        this.handContacts = List.copyOf(b.handContacts);
        this.impacts = List.copyOf(b.impacts);
        for(var contact:handContacts) {
            if(actorTracks.stream().noneMatch(t->t.role.equals(contact.sourceRole()))
                    ||actorTracks.stream().noneMatch(t->t.role.equals(contact.targetRole())))
                throw new IllegalArgumentException("Unknown contact actor");
        }
        this.shots = List.copyOf(b.shots);
        this.beats = Collections.unmodifiableList(sorted(b.beats));
        this.letterbox = b.letterbox;
        this.targetAnchor = b.targetAnchor;
        this.baseFogNear = b.baseFogNear;
        this.baseFogFar = b.baseFogFar;
        this.baseFogColor = b.baseFogColor;

        List<PoseKey> keys = new ArrayList<>(b.poseKeys);
        keys.sort((x, y) -> Integer.compare(x.tick, y.tick));
        this.poseKeys = Collections.unmodifiableList(keys);

        int sum = 0;
        for (Shot s : b.shots) sum += s.durationTicks;
        this.totalTicks = sum;
    }

    private static List<Beat> sorted(List<Beat> in) {
        List<Beat> copy = new ArrayList<>(in);
        copy.sort((x, y) -> Integer.compare(x.tick, y.tick));
        return copy;
    }

    /** Verilen zaman cizgisi tick'inde hangi cekimdeyiz? */
    public Cursor cursorAt(float timelineTick) {
        float acc = 0;
        for (int i = 0; i < shots.size(); i++) {
            Shot s = shots.get(i);
            if (timelineTick < acc + s.durationTicks) {
                return new Cursor(i, s, timelineTick - acc);
            }
            acc += s.durationTicks;
        }
        int last = shots.size() - 1;
        return new Cursor(last, shots.get(last), shots.get(last).durationTicks);
    }

    public record Cursor(int index, Shot shot, float localTick) {
        public float progress() {
            return Math.min(1f, localTick / Math.max(1, shot.durationTicks));
        }
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private final List<Shot> shots = new ArrayList<>();
        private final List<Beat> beats = new ArrayList<>();
        private boolean letterbox = true;
        private Vec3 targetAnchor;
        private float baseFogNear = Shot.NO_FOG;
        private float baseFogFar = Shot.NO_FOG;
        private int baseFogColor = Shot.NO_COLOR;
        private final List<PoseKey> poseKeys = new ArrayList<>();
        private final List<CinematicActorTrack> actorTracks = new ArrayList<>();
        private double stageSpan;
        private boolean isolatedStage;
        public Builder isolatedStage() { isolatedStage=true;return this; }
        private final List<CinematicSetPiece> setPieces=new ArrayList<>();
        private final List<CinematicHandContact> handContacts=new ArrayList<>();
        private final List<CinematicImpact> impacts=new ArrayList<>();
        public Builder impact(CinematicImpact impact) {
            if(impacts.size()>=32)throw new IllegalArgumentException("Impact budget exceeded");
            impacts.add(java.util.Objects.requireNonNull(impact));return this;
        }
        public Builder contact(CinematicHandContact contact) {
            if(handContacts.size()>=64)throw new IllegalArgumentException("Contact budget exceeded");
            handContacts.add(java.util.Objects.requireNonNull(contact));return this;
        }

        public Builder setPiece(CinematicSetPiece piece) {
            if(setPieces.size()>=4) throw new IllegalArgumentException("Set piece budget exceeded");
            setPieces.add(java.util.Objects.requireNonNull(piece));return this;
        }

        public Builder stageSpan(double span) {
            if(!Double.isFinite(span)||span<1.5) throw new IllegalArgumentException("Invalid stage span");
            stageSpan=span; return this;
        }

        public Builder actor(CinematicActorTrack track) {
            if(actorTracks.size()>=32) throw new IllegalArgumentException("Actor budget exceeded");
            for(var existing:actorTracks) {
                if(existing.role.equals(track.role) || (track.binding!=CinematicActorTrack.Binding.SAND
                        &&existing.binding==track.binding)) throw new IllegalArgumentException("Duplicate actor binding");
            }
            actorTracks.add(track); return this;
        }

        private Builder(String id) {
            this.id = id;
        }

        public Builder shot(Shot s) {
            shots.add(s);
            return this;
        }

        public Builder beat(Beat b) {
            beats.add(b);
            return this;
        }

        public Builder beats(Beat... bs) {
            Collections.addAll(beats, bs);
            return this;
        }

        public Builder letterbox(boolean v) {
            this.letterbox = v;
            return this;
        }

        /** Hedefi sahne kurulurken bu yerel konuma yerlestir. */
        public Builder anchorTarget(Vec3 local) {
            this.targetAnchor = local;
            return this;
        }

        /**
         * Sinematigin geneline sinen atmosfer.
         *
         * @param near sisin basladigi mesafe (blok) — kuculdukce sahne kapanir
         * @param far  gorusun tamamen kapandigi mesafe (blok)
         * @param rgb  sis rengi 0xRRGGBB — sahnenin tonu
         */
        /**
         * Aktorun bu tick'te alacagi poz.
         *
         * Gecis, bir onceki ayni aktor anahtarindan buraya kadar surer;
         * zincir verilirse eklemler sirayla hareket eder.
         */
        public Builder pose(int tick, Actor actor, ActorPose pose, float[] chain) {
            poseKeys.add(new PoseKey(tick, actor, pose, chain));
            return this;
        }

        public Builder pose(int tick, Actor actor, ActorPose pose) {
            return pose(tick, actor, pose, null);
        }

        public Builder atmosphere(float near, float far, int rgb) {
            this.baseFogNear = near;
            this.baseFogFar = far;
            this.baseFogColor = rgb;
            return this;
        }

        public CinematicDefinition build() {
            if (shots.isEmpty()) {
                throw new IllegalStateException("Sinematik '" + id + "' cekim icermiyor");
            }
            return new CinematicDefinition(this);
        }
    }
}
