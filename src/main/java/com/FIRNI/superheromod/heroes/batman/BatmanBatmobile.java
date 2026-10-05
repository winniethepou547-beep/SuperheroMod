package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.entity.ModEntities;
import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Batmobile gadget on the server: R with it picked calls it (one per Batman) or, when it is out, sends it away.
 * It comes from behind him, along the way he faces, and parks a few blocks to his right, turned a little across.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BatmanBatmobile {
    private BatmanBatmobile() {}

    private static final Map<UUID, BatmobileEntity> CARS = new HashMap<>();
    /** How far to his right it parks, how far behind him it starts, how far it turns across as it parks (degrees). */
    private static final double BESIDE = 3.6, RUN_UP = 30;
    private static final float ACROSS = 28;

    /** R with the Batmobile picked: true = done (the cooldown is spent). */
    static boolean use(ServerPlayer p, BatmanController.State s) {
        BatmobileEntity car = CARS.get(p.getUUID());
        if (car != null && car.isAlive() && car.level() == p.level()) {
            if (car.leaving()) return false;
            car.leave();
            BatmanController.tell(p, "Batmobil gidiyor");
            return true;
        }
        float yaw = p.getYRot();
        Vec3 fwd = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 park = p.position().add(right.scale(BESIDE)).add(fwd.scale(1.5));
        Vec3 from = park.subtract(fwd.scale(RUN_UP));
        BatmobileEntity fresh = ModEntities.BATMOBILE.get().create(p.level());
        if (fresh == null) return false;
        fresh.setup(p, from, park, yaw, yaw - ACROSS);
        p.level().addFreshEntity(fresh);
        CARS.put(p.getUUID(), fresh);
        p.level().playSound(null, from.x, from.y, from.z, ModSounds.BATMAN_CANNON_CHARGE.get(), SoundSource.NEUTRAL, 1.5f, .5f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, .6f, 1.6f);
        BatmanController.tell(p, "Batmobil geliyor");
        return true;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        BatmobileEntity car = CARS.remove(e.getEntity().getUUID());
        if (car != null) car.discard();
    }
}
