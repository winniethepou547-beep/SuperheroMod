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

    /** Sahnede gorunsun mu. */
    public boolean visible = true;

    public CinematicPuppet(UUID sourcePlayer, ResourceLocation skin, boolean slim) {
        this.sourcePlayer = sourcePlayer;
        this.skin = skin;
        this.slim = slim;
    }
}

