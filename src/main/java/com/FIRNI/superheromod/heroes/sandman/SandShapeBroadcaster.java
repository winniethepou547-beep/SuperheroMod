package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Somut kum sekillerini istemcilere yayinlar.
 *
 * Tek bir yayin noktasi var cunku el ve kaya AYNI cizici tarafindan
 * ciziliyor; ikisini ayri paketlerle gondermek her karede iki kez
 * temizlenen bir liste olustururdu ve sekiller donerdi.
 *
 * Yayin MESAFEYLE sinirli: haritanin obur ucundaki bir sutunun her tick
 * herkese gonderilmesi gereksiz trafik.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class SandShapeBroadcaster {

    private static final double VIEW_RANGE = 80.0;
    private static final double VIEW_RANGE_SQR = VIEW_RANGE * VIEW_RANGE;

    private SandShapeBroadcaster() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ServerLifecycleHooks.getCurrentServer() == null) return;

        List<SandShapeSyncPacket.Shape> all = new ArrayList<>();
        SandGraspController.collectShapes(all);
        SandPatchController.collectShapes(all);

        for (ServerLevel level : ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                List<SandShapeSyncPacket.Shape> visible = new ArrayList<>();

                for (SandShapeSyncPacket.Shape shape : all) {
                    double dx = shape.x() - player.getX();
                    double dy = shape.y() - player.getY();
                    double dz = shape.z() - player.getZ();
                    if (dx * dx + dy * dy + dz * dz <= VIEW_RANGE_SQR) visible.add(shape);
                }

                // Bos liste de gonderiliyor: sekil bittiginde istemcinin
                // ekranda asili kalan son kareyi temizlemesi gerekiyor.
                // Ama hicbir zaman sekli olmayan oyuncuya bos paket yagdirmak
                // anlamsiz, o yuzden sadece "biraz once vardi" durumunda.
                if (!visible.isEmpty() || wasVisible(player)) {
                    ModNetworking.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new SandShapeSyncPacket(visible));
                }

                setVisible(player, !visible.isEmpty());
            }
        }
    }

    // Oyuncu basina "gecen tick sekil gonderildi mi" hafizasi
    private static final java.util.Set<java.util.UUID> hadShapes = new java.util.HashSet<>();

    private static boolean wasVisible(ServerPlayer player) {
        return hadShapes.contains(player.getUUID());
    }

    private static void setVisible(ServerPlayer player, boolean visible) {
        if (visible) hadShapes.add(player.getUUID());
        else hadShapes.remove(player.getUUID());
    }
}
