package com.FIRNI.superheromod.client.render.puppet;

import com.FIRNI.superheromod.core.cinematic.ActorPose;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * SAHNE AKTORU — sinematik sirasinda gercek oyuncunun yerine cizilen kukla.
 *
 * Entity DEGIL. Yer cekimi yok, carpisma yok, Minecraft'in poz repertuari yok.
 * Bu yuzden takla atabilir, blogun icinden gecebilir, havada asili kalabilir
 * ve gecerli olmayan konumlarda durabilir — senaryodaki sahnelerin yarisi
 * ancak boyle mumkun.
 *
 * Gorunum icin temsil ettigi oyuncunun kendi skini kullanilir.
 */
public final class CinematicPuppet {

    /** Temsil edilen oyuncu — skin ve model tipi buradan gelir. */
    public final UUID sourcePlayer;
    public final ResourceLocation skin;
    public final boolean slim;
    public boolean legacyZombieUv;

    /** Dunya konumu (ayak hizasi). */
    public Vec3 position = Vec3.ZERO;
    /** Govdenin baktigi yon (derece). */
    public float yaw;

    /** O anki tam poz. */
    public final ActorPose pose = new ActorPose();

    /**
     * Isik seviyesi 0..15. Kuklayi biz cizdigimiz icin isigi da biz veriyoruz:
     * dunya karanlikta kalirken kukla parlak olabilir. Sinemada buna anahtar
     * isik denir ve gercek entity'de mumkun degildir.
     */
    public int lightLevel = 15;

    /** Colour the whole figure is lit with (1,1,1 = as drawn); films use it as a key light. */
    public float tintR = 1, tintG = 1, tintB = 1;

    /** Sahnede gorunsun mu. */
    public boolean visible = true;
    public float scale = 1;
    public float baseScale = 1;
    public com.FIRNI.superheromod.core.cinematic.CinematicActorTrack track;
    public final com.FIRNI.superheromod.core.cinematic.CinematicActorTrack.Sample sample =
            new com.FIRNI.superheromod.core.cinematic.CinematicActorTrack.Sample();
    /** Non-player fallback retains the original mob mesh instead of applying a player skin to it. */
    public net.minecraft.world.entity.Entity sourceEntity;

    public CinematicPuppet(UUID sourcePlayer, ResourceLocation skin, boolean slim) {
        this.sourcePlayer = sourcePlayer;
        this.skin = skin;
        this.slim = slim;
    }
}
