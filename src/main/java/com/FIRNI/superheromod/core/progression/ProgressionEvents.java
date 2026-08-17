package com.FIRNI.superheromod.core.progression;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rank/gold/xp capability'sinin kaydi, oyunculara eklenmesi ve olum/respawn'da korunmasi.
 * onRegisterCapabilities MOD event bus'ta atesleniyor, bu yuzden @SubscribeEvent ile otomatik
 * taranmiyor - SuperheroMod constructor'inda elle modEventBus.addListener ile baglaniyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class ProgressionEvents {

    public static final Capability<PlayerProgressionData> PROGRESSION_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation PROGRESSION_ID = new ResourceLocation(SuperheroMod.MODID, "progression");

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerProgressionData.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            ProgressionProvider provider = new ProgressionProvider();
            event.addCapability(PROGRESSION_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(PROGRESSION_CAPABILITY).ifPresent(oldData ->
                event.getEntity().getCapability(PROGRESSION_CAPABILITY).ifPresent(newData -> {
                    newData.setGold(oldData.getGold());
                    newData.setPvpRating(oldData.getPvpRating());
                    newData.setPvpXp(oldData.getPvpXp());
                    newData.setWins(oldData.getWins());
                    newData.setLosses(oldData.getLosses());
                    newData.setKills(oldData.getKills());
                    newData.setDeaths(oldData.getDeaths());
                    newData.setArenaTokens(oldData.getArenaTokens());
                    newData.setPeakRating(oldData.getPeakRating());
                    newData.setLevel(oldData.getLevel());
                    newData.setPveXp(oldData.getPveXp());
                }));
    }

    private static class ProgressionProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        private final PlayerProgressionData data = new PlayerProgressionCapability();
        private final LazyOptional<PlayerProgressionData> optional = LazyOptional.of(() -> data);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return cap == PROGRESSION_CAPABILITY ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("gold", data.getGold());
            tag.putInt("pvpRating", data.getPvpRating());
            tag.putInt("pvpXp", data.getPvpXp());
            tag.putInt("wins", data.getWins());
            tag.putInt("losses", data.getLosses());
            tag.putInt("kills", data.getKills());
            tag.putInt("deaths", data.getDeaths());
            tag.putInt("arenaTokens", data.getArenaTokens());
            tag.putInt("peakRating", data.getPeakRating());
            tag.putInt("level", data.getLevel());
            tag.putInt("pveXp", data.getPveXp());
            // Legacy compat: also write "rank" and "xp" keys
            tag.putInt("rank", data.getPvpRating());
            tag.putInt("xp", data.getPvpXp());
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.setGold(tag.getInt("gold"));
            // Read new keys first, fall back to legacy keys
            if (tag.contains("pvpRating")) {
                data.setPvpRating(tag.getInt("pvpRating"));
            } else if (tag.contains("rank")) {
                data.setPvpRating(tag.getInt("rank"));
            }
            if (tag.contains("pvpXp")) {
                data.setPvpXp(tag.getInt("pvpXp"));
            } else if (tag.contains("xp")) {
                data.setPvpXp(tag.getInt("xp"));
            }
            data.setWins(tag.getInt("wins"));
            data.setLosses(tag.getInt("losses"));
            data.setKills(tag.getInt("kills"));
            data.setDeaths(tag.getInt("deaths"));
            data.setArenaTokens(tag.getInt("arenaTokens"));
            data.setPeakRating(tag.getInt("peakRating"));
            if (tag.contains("level")) {
                data.setLevel(tag.getInt("level"));
            }
            data.setPveXp(tag.getInt("pveXp"));
        }

        void invalidate() {
            optional.invalidate();
        }
    }
}
