package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.SandArmorPacket;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SAND ARMOR — aktif yetenek degil, Sandman'in ilerleme/durum sistemi.
 *
 * Dokumandaki tasarim: "Bunu zorunlu bir aktif ability yapmak yerine
 * Sandman'in state/progression sistemi olarak dusunmek daha iyi. Sandman
 * hasar aldikca veya Sand Energy topladikca vucuduna daha fazla kum eklenir."
 *
 * Sand Energy henuz yok, bu yuzden birikim HASAR ALMAKTAN geliyor. Bu ayni
 * zamanda karakterin tank/bruiser kimligiyle ortusuyor: dovuse girdikce
 * kalinlasiyor, dovusten cikinca kum yavasca dokuluyor.
 *
 * Seviye 0-5. Her seviye:
 *   - gelen hasari azaltir
 *   - savrulmayi azaltir
 *   - Sandman'in kendi vuruslarini guclendirir
 *   - yuksek seviyelerde hafifce yavaslatir (agirlik bedeli)
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandArmorController {

    public static final int MAX_LEVEL = 5;

    /** Bir seviye icin gereken birikim. */
    private static final float CHARGE_PER_LEVEL = 8.0f;
    /** Dovus disi sayilmak icin gereken sessizlik. */
    private static final int DECAY_DELAY = 100;
    /** Dovus disinda saniyede erien birikim. */
    private static final float DECAY_PER_SECOND = 1.0f;

    private static final float REDUCTION_PER_LEVEL = 0.04f;   // seviye basina %4
    private static final float BONUS_PER_LEVEL = 0.07f;       // seviye basina %7 vurus
    private static final float KNOCKBACK_RESIST_PER_LEVEL = 0.14f;

    /** Barin tamami — bu deger MAX_LEVEL kademesine denk geliyor. */
    public static final float MAX_CHARGE = CHARGE_PER_LEVEL * MAX_LEVEL;

    private static final class Armor {
        float charge;
        int level;
        int sinceHit;
        float lastSentFill = -1f;
    }

    private static final Map<UUID, Armor> armors = new HashMap<>();

    private SandArmorController() {}

    // ------------------------------------------------------------------

    public static int levelOf(UUID playerId) {
        Armor armor = armors.get(playerId);
        return armor == null ? 0 : armor.level;
    }

    /** Dikey barin doluluk orani 0..1. */
    public static float fillOf(UUID playerId) {
        Armor armor = armors.get(playerId);
        return armor == null ? 0f : Math.min(1f, armor.charge / MAX_CHARGE);
    }

    /**
     * Biriken kumu tamamen bosaltir ve doluluk oranini dondurur.
     *
     * Sand Burst bunu kullaniyor: dikenler atildiktan sonra Sandman zirhtan
     * cikmis oluyor, bar sifirlaniyor.
     */
    public static float consumeAll(ServerPlayer player) {
        Armor armor = armors.get(player.getUUID());
        if (armor == null || armor.charge <= 0f) return 0f;

        float fill = Math.min(1f, armor.charge / MAX_CHARGE);

        armor.charge = 0f;
        armor.level = 0;
        armor.sinceHit = 0;

        broadcast(player, 0, 0f);
        armor.lastSentFill = 0f;

        return fill;
    }

    private static boolean isSandman(Player player) {
        return SandmanCharacter.ID.equals(AbilityManager.getCharacterId(player.getUUID()));
    }

    // ------------------------------------------------------------------
    // Birikim ve etkiler
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // --- Sandman hasar aliyor: zirh birikir ve gelen hasar azalir ---
        if (event.getEntity() instanceof Player victim && isSandman(victim)) {
            Armor armor = armors.computeIfAbsent(victim.getUUID(), id -> new Armor());
            armor.sinceHit = 0;

            int before = armor.level;
            armor.charge = Math.min(MAX_CHARGE, armor.charge + event.getAmount());
            armor.level = (int) Math.min(MAX_LEVEL, armor.charge / CHARGE_PER_LEVEL);

            if (armor.level != before && victim instanceof ServerPlayer server) {
                onLevelChanged(server, armor.level, armor.level > before);
            }

            float reduction = armor.level * REDUCTION_PER_LEVEL;
            event.setAmount(event.getAmount() * (1.0f - reduction));
        }

        // --- Sandman vuruyor: BAR DOLDUKCA vurusu guclenir ---
        // Kademe degil doluluk kullaniliyor; boylece bar dolarken guc surekli
        // artiyor, seviye atlarken sicramiyor.
        if (event.getSource().getEntity() instanceof Player attacker && isSandman(attacker)) {
            float fill = fillOf(attacker.getUUID());
            if (fill > 0f) {
                event.setAmount(event.getAmount()
                        * (1.0f + fill * BONUS_PER_LEVEL * MAX_LEVEL));
            }
        }
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isSandman(player)) return;

        int level = levelOf(player.getUUID());
        if (level <= 0) return;

        float resist = Math.min(0.85f, level * KNOCKBACK_RESIST_PER_LEVEL);
        event.setStrength(event.getStrength() * (1.0f - resist));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (armors.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (Map.Entry<UUID, Armor> entry : armors.entrySet()) {
            Armor armor = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;

            armor.sinceHit++;

            // Dovusten cikinca kum yavasca dokulur
            if (armor.sinceHit > DECAY_DELAY && armor.charge > 0f && armor.sinceHit % 20 == 0) {
                int before = armor.level;
                armor.charge = Math.max(0f, armor.charge - DECAY_PER_SECOND);
                armor.level = (int) Math.min(MAX_LEVEL, armor.charge / CHARGE_PER_LEVEL);

                if (armor.level != before) onLevelChanged(player, armor.level, false);
            }

            // Zirhli Sandman'in uzerinden surekli kum dokulur
            if (armor.level > 0 && armor.sinceHit % 6 == 0
                    && player.level() instanceof ServerLevel level) {
                level.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                        player.getX(), player.getY() + 1.1, player.getZ(),
                        armor.level, 0.35, 0.5, 0.35, 0.01);
            }

            // Bar sahibinin ekraninda akici gorunsun diye doluluk ayrica
            // gonderiliyor; seviye degisimini beklemek bari kademeli yapardi
            float fill = Math.min(1f, armor.charge / MAX_CHARGE);
            if (Math.abs(fill - armor.lastSentFill) > 0.01f) {
                armor.lastSentFill = fill;
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new SandArmorPacket(player.getUUID(), armor.level, fill));
            }
        }
    }

    /** Seviye degisince herkese duyurulur ve geri bildirim verilir. */
    private static void onLevelChanged(ServerPlayer player, int level, boolean gained) {
        broadcast(player, level, fillOf(player.getUUID()));

        if (!(player.level() instanceof ServerLevel level3d)) return;

        if (gained) {
            level3d.playSound(null, player.blockPosition(),
                    SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 0.9f, 0.6f + level * 0.06f);
            level3d.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    20 + level * 6, 0.5, 0.8, 0.5, 0.06);
        } else {
            level3d.playSound(null, player.blockPosition(),
                    SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.5f, 1.1f);
        }
    }

    private static void broadcast(ServerPlayer player, int level, float fill) {
        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new SandArmorPacket(player.getUUID(), level, fill));
    }

    /** Yeni giren oyuncuya mevcut zirh seviyeleri bildirilir. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joiner)) return;

        for (Map.Entry<UUID, Armor> entry : armors.entrySet()) {
            if (entry.getValue().level <= 0) continue;
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> joiner),
                    new SandArmorPacket(entry.getKey(), entry.getValue().level, 0f));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        armors.remove(event.getEntity().getUUID());
        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new SandArmorPacket(event.getEntity().getUUID(), 0, 0f));
    }
}
