package com.FIRNI.superheromod.core.entity;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.sandman.GiantSandSoldierEntity;
import com.FIRNI.superheromod.heroes.sandman.SandSoldierEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Modun kendi entity turleri. */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SuperheroMod.MODID);

    public static final RegistryObject<EntityType<SandSoldierEntity>> SAND_SOLDIER =
            ENTITY_TYPES.register("sand_soldier",
                    () -> EntityType.Builder
                            .<SandSoldierEntity>of(SandSoldierEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .build("sand_soldier"));

    /**
     * Dev asker ayri tur olarak kayitli: carpisma kutusu EntityType uzerinden
     * tanimlaniyor, ayni turde "buyuk" bayragi tutmak hitbox'i buyutmezdi.
     */
    public static final RegistryObject<EntityType<GiantSandSoldierEntity>> GIANT_SAND_SOLDIER =
            ENTITY_TYPES.register("giant_sand_soldier",
                    () -> EntityType.Builder
                            .<GiantSandSoldierEntity>of(GiantSandSoldierEntity::new, MobCategory.MISC)
                            .sized(1.35f, 3.9f)
                            .clientTrackingRange(12)
                            .updateInterval(2)
                            .build("giant_sand_soldier"));

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
