package com.FIRNI.superheromod.core.sound;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own sound effects (synthesised by tools/sounds/synth.py: nothing recorded or taken from elsewhere; the files
 * are in assets/superheromod/sounds/, listed in sounds.json). They are layered with vanilla sounds where those help.
 * Regenerate the files and this list together: the names here are the sounds.json keys.
 */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SuperheroMod.MODID);

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SuperheroMod.MODID, name)));
    }

    // fx
    public static final RegistryObject<SoundEvent> FX_WHOOSH_LIGHT = reg("fx.whoosh_light");
    public static final RegistryObject<SoundEvent> FX_WHOOSH_HEAVY = reg("fx.whoosh_heavy");
    public static final RegistryObject<SoundEvent> FX_IMPACT_HEAVY = reg("fx.impact_heavy");
    public static final RegistryObject<SoundEvent> FX_IMPACT_METAL = reg("fx.impact_metal");
    public static final RegistryObject<SoundEvent> FX_ELECTRIC_ZAP = reg("fx.electric_zap");
    public static final RegistryObject<SoundEvent> FX_ELECTRIC_CRACKLE = reg("fx.electric_crackle");
    public static final RegistryObject<SoundEvent> FX_ENERGY_SWELL = reg("fx.energy_swell");
    public static final RegistryObject<SoundEvent> FX_ENERGY_BOOM = reg("fx.energy_boom");
    // magneto
    public static final RegistryObject<SoundEvent> MAGNETO_MAGNETIC_HUM = reg("magneto.magnetic_hum");
    public static final RegistryObject<SoundEvent> MAGNETO_METAL_RISE = reg("magneto.metal_rise");
    public static final RegistryObject<SoundEvent> MAGNETO_METAL_CLANG_BIG = reg("magneto.metal_clang_big");
    public static final RegistryObject<SoundEvent> MAGNETO_METAL_SHING = reg("magneto.metal_shing");
    public static final RegistryObject<SoundEvent> MAGNETO_SHIELD_UP = reg("magneto.shield_up");
    public static final RegistryObject<SoundEvent> MAGNETO_SHIELD_HIT = reg("magneto.shield_hit");
    public static final RegistryObject<SoundEvent> MAGNETO_SHIELD_BURST = reg("magneto.shield_burst");
    public static final RegistryObject<SoundEvent> MAGNETO_TELEKINESIS_GRAB = reg("magneto.telekinesis_grab");
    public static final RegistryObject<SoundEvent> MAGNETO_FLING = reg("magneto.fling");
    public static final RegistryObject<SoundEvent> MAGNETO_FIST_ASSEMBLE = reg("magneto.fist_assemble");
    public static final RegistryObject<SoundEvent> MAGNETO_FIST_SLAM = reg("magneto.fist_slam");
    public static final RegistryObject<SoundEvent> MAGNETO_ROD_WHISTLE = reg("magneto.rod_whistle");
    public static final RegistryObject<SoundEvent> MAGNETO_ROD_IMPALE = reg("magneto.rod_impale");
    // panther
    public static final RegistryObject<SoundEvent> PANTHER_CLAW_SLASH = reg("panther.claw_slash");
    public static final RegistryObject<SoundEvent> PANTHER_CLAW_HIT = reg("panther.claw_hit");
    public static final RegistryObject<SoundEvent> PANTHER_VIBRANIUM_ABSORB = reg("panther.vibranium_absorb");
    public static final RegistryObject<SoundEvent> PANTHER_KINETIC_RELEASE = reg("panther.kinetic_release");
    public static final RegistryObject<SoundEvent> PANTHER_POUNCE = reg("panther.pounce");
    public static final RegistryObject<SoundEvent> PANTHER_KICK_IMPACT = reg("panther.kick_impact");
    public static final RegistryObject<SoundEvent> PANTHER_CAMO_ON = reg("panther.camo_on");
    public static final RegistryObject<SoundEvent> PANTHER_CAMO_OFF = reg("panther.camo_off");
    public static final RegistryObject<SoundEvent> PANTHER_DASH = reg("panther.dash");
    // thor
    public static final RegistryObject<SoundEvent> THOR_HAMMER_WHOOSH = reg("thor.hammer_whoosh");
    public static final RegistryObject<SoundEvent> THOR_HAMMER_IMPACT = reg("thor.hammer_impact");
    public static final RegistryObject<SoundEvent> THOR_LIGHTNING_CRACKLE = reg("thor.lightning_crackle");
    public static final RegistryObject<SoundEvent> THOR_HAMMER_CATCH = reg("thor.hammer_catch");
    // hulk
    public static final RegistryObject<SoundEvent> HULK_HULK_PUNCH = reg("hulk.hulk_punch");
    public static final RegistryObject<SoundEvent> HULK_GROUND_SLAM = reg("hulk.ground_slam");
    public static final RegistryObject<SoundEvent> HULK_THUNDERCLAP = reg("hulk.thunderclap");
    public static final RegistryObject<SoundEvent> HULK_LEAP = reg("hulk.leap");
    // zed
    public static final RegistryObject<SoundEvent> ZED_BLADE_SLASH = reg("zed.blade_slash");
    public static final RegistryObject<SoundEvent> ZED_SHADOW_WHOOSH = reg("zed.shadow_whoosh");
    public static final RegistryObject<SoundEvent> ZED_SHURIKEN_WHIR = reg("zed.shuriken_whir");
    public static final RegistryObject<SoundEvent> ZED_MARK_BURST = reg("zed.mark_burst");
    // cyclops
    public static final RegistryObject<SoundEvent> CYCLOPS_OPTIC_BEAM = reg("cyclops.optic_beam");
    public static final RegistryObject<SoundEvent> CYCLOPS_OPTIC_BLAST = reg("cyclops.optic_blast");
    // ghost
    public static final RegistryObject<SoundEvent> GHOST_CHAIN_WHIP = reg("ghost.chain_whip");
    public static final RegistryObject<SoundEvent> GHOST_HELLFIRE = reg("ghost.hellfire");
    // sandman
    public static final RegistryObject<SoundEvent> SANDMAN_SAND_WHOOSH = reg("sandman.sand_whoosh");
    public static final RegistryObject<SoundEvent> SANDMAN_SAND_IMPACT = reg("sandman.sand_impact");
    // batman
    public static final RegistryObject<SoundEvent> BATMAN_PUNCH = reg("batman.punch");
    public static final RegistryObject<SoundEvent> BATMAN_BATARANG = reg("batman.batarang");
    public static final RegistryObject<SoundEvent> BATMAN_GRAPNEL = reg("batman.grapnel");
    public static final RegistryObject<SoundEvent> BATMAN_SMOKE = reg("batman.smoke");
    public static final RegistryObject<SoundEvent> BATMAN_FLASH = reg("batman.flash");
    public static final RegistryObject<SoundEvent> BATMAN_MINE = reg("batman.mine");
    public static final RegistryObject<SoundEvent> BATMAN_CAPE = reg("batman.cape");
    // batman: the wrist cannon
    public static final RegistryObject<SoundEvent> BATMAN_CANNON_DEPLOY = reg("batman.cannon_deploy");
    public static final RegistryObject<SoundEvent> BATMAN_CANNON_CHARGE = reg("batman.cannon_charge");
    public static final RegistryObject<SoundEvent> BATMAN_CANNON_SHOT = reg("batman.cannon_shot");
    public static final RegistryObject<SoundEvent> BATMAN_CANNON_HUM = reg("batman.cannon_hum");
    public static final RegistryObject<SoundEvent> BATMAN_CANNON_FINAL = reg("batman.cannon_final");
    public static final RegistryObject<SoundEvent> BATMAN_CANNON_STOP = reg("batman.cannon_stop");
    public static final RegistryObject<SoundEvent> BATMAN_CANNON_RETRACT = reg("batman.cannon_retract");
    // batman: the sonic trap (BatmanSonic / BatmanSonicFx)
    public static final RegistryObject<SoundEvent> BATMAN_SONIC_BEEP = reg("batman.sonic_beep");
    public static final RegistryObject<SoundEvent> BATMAN_SONIC_RUMBLE = reg("batman.sonic_rumble");
    public static final RegistryObject<SoundEvent> BATMAN_SONIC_RISE = reg("batman.sonic_rise");
    public static final RegistryObject<SoundEvent> BATMAN_SONIC_LOCK = reg("batman.sonic_lock");
    public static final RegistryObject<SoundEvent> BATMAN_SONIC_HUM = reg("batman.sonic_hum");
    public static final RegistryObject<SoundEvent> BATMAN_SONIC_PULSE = reg("batman.sonic_pulse");
    public static final RegistryObject<SoundEvent> BATMAN_SONIC_HIT = reg("batman.sonic_hit");
    public static final RegistryObject<SoundEvent> BATMAN_SONIC_RING = reg("batman.sonic_ring");
    public static final RegistryObject<SoundEvent> BATMAN_SONIC_BREAK = reg("batman.sonic_break");
    public static final RegistryObject<SoundEvent> BATMAN_SONIC_RETRACT = reg("batman.sonic_retract");

    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
