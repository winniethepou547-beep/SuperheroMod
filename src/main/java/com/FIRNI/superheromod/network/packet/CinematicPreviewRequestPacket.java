package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.core.cinematic.CinematicDirector;
import com.FIRNI.superheromod.heroes.sandman.SandArmyPreview;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Debug rehearsal input. The server selects a visible target; clients never supply entity IDs. */
public final class CinematicPreviewRequestPacket {
    public static void encode(CinematicPreviewRequestPacket message,FriendlyByteBuf buffer) {}
    public static CinematicPreviewRequestPacket decode(FriendlyByteBuf buffer) { return new CinematicPreviewRequestPacket(); }
    public static void handle(CinematicPreviewRequestPacket message,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();
        context.enqueueWork(()->{
            var player=context.getSender();
            if(player==null)return;
            if(!player.hasPermissions(2)) {
                player.displayClientMessage(Component.literal("Sinematik prova icin hileler/operator yetkisi gerekir."),true);
                return;
            }
            if(com.FIRNI.superheromod.core.film.FilmSessions.playing(player.getUUID(),com.FIRNI.superheromod.heroes.sandman.SandArmySession.ID)) {
                com.FIRNI.superheromod.core.film.FilmSessions.stop(player);return;
            }
            if(CinematicDirector.isBusy(player.getUUID()) || com.FIRNI.superheromod.core.film.FilmSessions.busy(player.getUUID()))return;
            // Bound repeated lookups even if a modified client sends many requests.
            long now=player.serverLevel().getGameTime();
            var data=player.getPersistentData();
            String key="superheromod:preview_request_tick";
            if(data.contains(key) && now>=data.getLong(key) && now-data.getLong(key)<10)return;
            data.putLong(key,now);
            Vec3 eye=player.getEyePosition(),look=player.getLookAngle();
            LivingEntity best=null;double bestScore=-Double.MAX_VALUE;
            for(var entity:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(24),
                    e->e!=player&&e.isAlive()&&!e.isSpectator()&&!CinematicDirector.isBusy(e.getUUID())&&!com.FIRNI.superheromod.core.film.FilmSessions.busy(e.getUUID()))) {
                Vec3 delta=entity.getBoundingBox().getCenter().subtract(eye);
                double distance=delta.length();
                if(distance>24||distance<.01||!player.hasLineOfSight(entity))continue;
                double alignment=look.dot(delta.scale(1/distance));
                if(alignment<.9)continue;
                double score=alignment*100-distance*.1;
                if(score>bestScore){best=entity;bestScore=score;}
            }
            if(best==null)player.displayClientMessage(Component.literal("24 blok icindeki bir NPC'ye bakip H'ye bas."),true);
            else if(com.FIRNI.superheromod.heroes.sandman.SandArmySession.start(player,best))
                player.displayClientMessage(Component.literal("Sand Army provasi — H: durdur. Hasar verilmez."),true);
        });
        context.setPacketHandled(true);
    }
}
