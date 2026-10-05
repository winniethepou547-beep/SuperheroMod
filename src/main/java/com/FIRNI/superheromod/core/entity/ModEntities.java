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

    public static final RegistryObject<EntityType<com.FIRNI.superheromod.heroes.sandman.SettledSandBallEntity>> SETTLED_SAND_BALL =
            ENTITY_TYPES.register("settled_sand_ball", () -> EntityType.Builder
                    .<com.FIRNI.superheromod.heroes.sandman.SettledSandBallEntity>of(
                            com.FIRNI.superheromod.heroes.sandman.SettledSandBallEntity::new, MobCategory.MISC)
                    .sized(4.4f, 4.2f).clientTrackingRange(12).updateInterval(20).build("settled_sand_ball"));

    private ModEntities() {}

    public static final RegistryObject<EntityType<com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity>> HELL_CYCLE =
            ENTITY_TYPES.register("hell_cycle", () -> EntityType.Builder
                    .<com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity>of(
                            com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity::new, MobCategory.MISC)
                    .sized(0.85f, 1.15f).clientTrackingRange(12).updateInterval(1).fireImmune().build("hell_cycle"));

    /** Batman's sonic trap emitter (heroes/batman/SonicEmitterEntity): stands still, can be hit, never saved. */
    public static final RegistryObject<EntityType<com.FIRNI.superheromod.heroes.batman.SonicEmitterEntity>> SONIC_EMITTER =
            ENTITY_TYPES.register("sonic_emitter", () -> EntityType.Builder
                    .<com.FIRNI.superheromod.heroes.batman.SonicEmitterEntity>of(
                            com.FIRNI.superheromod.heroes.batman.SonicEmitterEntity::new, MobCategory.MISC)
                    .sized(1.1f, 2.0f).clientTrackingRange(10).updateInterval(20).fireImmune().noSave().build("sonic_emitter"));

    /** Batman's Batmobile (heroes/batman/BatmobileEntity): the remote takedown's run (every client works out its path itself); never saved. */
    public static final RegistryObject<EntityType<com.FIRNI.superheromod.heroes.batman.BatmobileEntity>> BATMOBILE =
            ENTITY_TYPES.register("batmobile", () -> EntityType.Builder
                    .<com.FIRNI.superheromod.heroes.batman.BatmobileEntity>of(
                            com.FIRNI.superheromod.heroes.batman.BatmobileEntity::new, MobCategory.MISC)
                    .sized(3.0f, 1.7f).clientTrackingRange(12).updateInterval(20).fireImmune().noSave().build("batmobile"));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
