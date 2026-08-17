package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Kahraman kusanildiginda giyilen skin.
 *
 * Oyuncunun kendi skini yerine karakterin dokusu cizilir. Doku yoksa
 * (henuz cizilmemisse) sessizce oyuncunun kendi skinine geri donulur —
 * pembe-siyah "eksik doku" karesi cikmaz.
 */
public final class HeroSkins {

    /** Skin dosyalarinin yeri: assets/superheromod/textures/entity/&lt;id&gt;.png */
    private static final String PATH = "textures/entity/";

    /** characterId -> doku. Yoksa oyuncunun kendi skini kullanilir. */
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    /** Dokunun gercekten var olup olmadigi — her karede sorgulanmasin diye. */
    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

    static {
        register("cyclops");
        register("sandman");
    }

    private HeroSkins() {}

    private static void register(String characterId) {
        TEXTURES.put(characterId,
                new ResourceLocation(SuperheroMod.MODID, PATH + characterId + ".png"));
    }

    /**
     * Bu oyuncunun kusanmasi gereken skin; kahraman degilse veya dokusu
     * eksikse null.
     */
    @Nullable
    public static ResourceLocation skinFor(Player player) {
        String characterId = ClientHeroRegistry.get(player.getUUID());
        if (characterId == null || characterId.isEmpty()) return null;

        ResourceLocation texture = TEXTURES.get(characterId);
        if (texture == null) return null;

        return exists(texture) ? texture : null;
    }

    private static boolean exists(ResourceLocation texture) {
        Boolean cached = EXISTS.get(texture);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        boolean found = mc.getResourceManager().getResource(texture).isPresent();
        EXISTS.put(texture, found);
        return found;
    }

    /** Kaynak paketi yeniden yuklendiginde (F3+T) tekrar bakilsin. */
    public static void invalidate() {
        EXISTS.clear();
    }

    /** Kaynak yeniden yukleme ve sunucudan ayrilma baglantilari. */
    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {}

        /** Sunucudan ayrilinca kimlik tablosu bosaltilir. */
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientHeroRegistry.clear();
            invalidate();
        }
    }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        /** F3+T sonrasi skin dosyasi eklendiyse yeniden bakilsin. */
        @SubscribeEvent
        public static void onRegisterReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(
                    (ResourceManagerReloadListener) manager -> invalidate());
        }
    }
}
