package com.FIRNI.superheromod.heroes.hulk;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Hulk's tuning, in config/superheromod-hulk.toml (server rules) and
 * config/superheromod-hulk-client.toml (what each player sees). Every value is read live.
 */
public final class HulkConfig {
    // ------------------------------------------------------------------ server
    public static final ForgeConfigSpec COMMON;
    public static final ForgeConfigSpec.BooleanValue FRIENDLY_FIRE, BLOCK_DAMAGE, BREAK_IN_REGIONS;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue MAX_HARDNESS, HEAVY_RESISTANCE;
    public static final ForgeConfigSpec.BooleanValue FLYING_BLOCKS, DEBRIS_LANDS;
    public static final ForgeConfigSpec.IntValue MAX_DEBRIS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BANNED_BLOCKS;

    public static final ForgeConfigSpec.DoubleValue PUNCH_DAMAGE, PUNCH_REACH, PUNCH_KNOCKBACK;
    public static final ForgeConfigSpec.DoubleValue CHARGED_DAMAGE, CHARGED_RANGE, CHARGED_KNOCKBACK;
    public static final ForgeConfigSpec.IntValue CHARGED_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue GUARD_FRONT, GUARD_BACK, GUARD_DRAIN, GUARD_REGEN;
    public static final ForgeConfigSpec.BooleanValue GUARD_EXPLOSIONS, GUARD_FALLS;
    public static final ForgeConfigSpec.DoubleValue CLAP_DAMAGE, CLAP_RANGE, CLAP_KNOCKBACK;
    public static final ForgeConfigSpec.IntValue CLAP_STUN, CLAP_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue POUND_DAMAGE, POUND_RANGE, POUND_LAUNCH;
    public static final ForgeConfigSpec.IntValue POUND_WIDTH, POUND_DEPTH, POUND_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue LEAP_POWER, LANDING_DAMAGE, LANDING_RADIUS;
    public static final ForgeConfigSpec.IntValue LANDING_CRATER, LEAP_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue ROCK_DAMAGE, ROCK_SPLASH, ROCK_SPEED, ROCK_RANGE, ROCK_RADIUS;
    public static final ForgeConfigSpec.IntValue ROCK_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue ROCK_TAKES_BLOCK;
    public static final ForgeConfigSpec.DoubleValue ULT_RANGE, ULT_CRASH_SHARE, ULT_SMASH_SHARE;
    public static final ForgeConfigSpec.IntValue ULT_DELAY, ULT_COOLDOWN, ULT_CRATER;
    public static final ForgeConfigSpec.IntValue TRANSFORM_COOLDOWN;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.comment("Hulk: rules for everyone on the server").push("rules");
        FRIENDLY_FIRE = b.comment("Hulk's attacks also hurt and push players on his own team").define("friendlyFire", false);
        BLOCK_DAMAGE = b.comment("Hulk's attacks may break and dig blocks at all (off = no block is ever changed)").define("blockDamage", true);
        BREAK_IN_REGIONS = b.comment("Blocks may be changed inside /superhero regions (hub, arenas)").define("breakInRegions", false);
        MAX_BLOCKS = b.comment("Most blocks a single attack may change").defineInRange("maxBlocksPerAttack", 140, 0, 800);
        MAX_HARDNESS = b.comment("Hardest block he can break (stone is 1.5, obsidian 50)").defineInRange("maxHardness", 3.0, 0, 50);
        BANNED_BLOCKS = b.comment("Blocks Hulk never changes (ids like minecraft:chest)")
                .defineListAllowEmpty("bannedBlocks", List.of("minecraft:bedrock", "minecraft:obsidian", "minecraft:crying_obsidian",
                        "minecraft:end_portal_frame", "minecraft:spawner", "minecraft:barrier"), o -> o instanceof String);
        HEAVY_RESISTANCE = b.comment("How much big, heavy creatures resist being thrown (0 = no difference, 1 = strongly)")
                .defineInRange("heavyResistance", .7, 0, 1);
        FLYING_BLOCKS = b.comment("Blocks torn out of the ground fly through the air as real falling blocks (off = they just break)")
                .define("flyingBlocks", true);
        DEBRIS_LANDS = b.comment("Flying blocks settle where they land (off = they shatter on landing and leave nothing)")
                .define("debrisLands", false);
        MAX_DEBRIS = b.comment("Most flying blocks in the air at once, for the whole server").defineInRange("maxFlyingBlocks", 260, 0, 1000);
        b.pop();
        b.push("punches");
        PUNCH_DAMAGE = b.defineInRange("damage", 7.0, 0, 100);
        PUNCH_REACH = b.defineInRange("reach", 3.8, 1, 8);
        PUNCH_KNOCKBACK = b.defineInRange("knockback", 1.0, 0, 5);
        CHARGED_DAMAGE = b.comment("Damage where the charged punch's shock wave bursts, at full charge").defineInRange("chargedDamage", 24.0, 0, 200);
        CHARGED_RANGE = b.comment("How far the charged punch's shock wave travels at full charge").defineInRange("chargedRange", 16.0, 4, 40);
        CHARGED_KNOCKBACK = b.defineInRange("chargedKnockback", 2.4, 0, 8);
        CHARGED_COOLDOWN = b.comment("Ticks (20 = 1 second)").defineInRange("chargedCooldown", 60, 0, 2000);
        b.pop();
        b.push("guard");
        GUARD_FRONT = b.comment("Share of damage from the front that the guard stops").defineInRange("front", .75, 0, 1);
        GUARD_BACK = b.comment("Share of damage from behind that the guard stops").defineInRange("back", .2, 0, 1);
        GUARD_DRAIN = b.comment("Stamina used per tick while guarding (out of 100)").defineInRange("drain", .9, 0, 10);
        GUARD_REGEN = b.comment("Stamina recovered per tick when not guarding").defineInRange("regen", .7, 0, 10);
        GUARD_EXPLOSIONS = b.comment("The guard also softens explosions").define("explosions", true);
        GUARD_FALLS = b.comment("The guard also softens falls").define("falls", false);
        b.pop();
        b.push("thunderclap");
        CLAP_DAMAGE = b.defineInRange("damage", 11.0, 0, 200);
        CLAP_RANGE = b.comment("How far the wall of air travels").defineInRange("range", 22.0, 4, 48);
        CLAP_KNOCKBACK = b.defineInRange("knockback", 2.8, 0, 8);
        CLAP_STUN = b.comment("Ticks the wave slows and dazes those it hits").defineInRange("stun", 50, 0, 400);
        CLAP_COOLDOWN = b.defineInRange("cooldown", 160, 0, 4000);
        b.pop();
        b.push("groundPound");
        POUND_DAMAGE = b.defineInRange("damage", 11.0, 0, 200);
        POUND_RANGE = b.comment("Blocks the earth wave travels").defineInRange("range", 22.0, 3, 48);
        POUND_LAUNCH = b.defineInRange("launch", 1.15, 0, 4);
        POUND_WIDTH = b.comment("Width of the trench it leaves (0 = no trench)").defineInRange("trenchWidth", 6, 0, 12);
        POUND_DEPTH = b.comment("Depth of the trench in blocks").defineInRange("trenchDepth", 20, 0, 40);
        POUND_COOLDOWN = b.defineInRange("cooldown", 200, 0, 4000);
        b.pop();
        b.push("leap");
        LEAP_POWER = b.comment("Scales the whole leap").defineInRange("power", 1.0, .3, 2.5);
        LANDING_DAMAGE = b.comment("Landing damage after the highest fall").defineInRange("landingDamage", 14.0, 0, 200);
        LANDING_RADIUS = b.defineInRange("landingRadius", 7.5, 1, 16);
        LANDING_CRATER = b.comment("Radius of the shallow crater a hard landing digs (0 = none)").defineInRange("crater", 3, 0, 5);
        LEAP_COOLDOWN = b.defineInRange("cooldown", 16, 0, 2000);
        b.pop();
        b.push("rock");
        ROCK_DAMAGE = b.comment("Damage to the body it hits").defineInRange("damage", 16.0, 0, 200);
        ROCK_SPLASH = b.comment("Damage to those near the impact").defineInRange("splash", 8.0, 0, 200);
        ROCK_SPEED = b.defineInRange("speed", 1.7, .5, 5);
        ROCK_RANGE = b.defineInRange("range", 45.0, 5, 120);
        ROCK_RADIUS = b.defineInRange("impactRadius", 4.5, .5, 10);
        ROCK_TAKES_BLOCK = b.comment("Pulling the rock up removes the block it came from").define("takesBlock", true);
        ROCK_COOLDOWN = b.defineInRange("cooldown", 100, 0, 4000);
        b.pop();
        b.push("gammaRage");
        ULT_RANGE = b.comment("How far the target may be").defineInRange("range", 16.0, 3, 40);
        ULT_CRASH_SHARE = b.comment("Share of the target's max health taken when it hits the ground").defineInRange("crashShare", .25, 0, 1);
        ULT_SMASH_SHARE = b.comment("Share of max health taken by Hulk's two-handed smash").defineInRange("smashShare", .5, 0, 1);
        ULT_DELAY = b.comment("Ticks between the target's crash and Hulk's smash (40 = 2 seconds)").defineInRange("smashDelay", 40, 15, 100);
        ULT_CRATER = b.comment("Radius of the real crater the smash digs (the blast itself looks much bigger)").defineInRange("crater", 4, 0, 8);
        ULT_COOLDOWN = b.defineInRange("cooldown", 900, 0, 20000);
        b.pop();
        b.push("form");
        TRANSFORM_COOLDOWN = b.comment("Ticks before Banner/Hulk can change again").defineInRange("cooldown", 40, 0, 2000);
        b.pop();
        COMMON = b.build();
    }

    // ------------------------------------------------------------------ client
    public static final ForgeConfigSpec CLIENT;
    public static final ForgeConfigSpec.DoubleValue EFFECTS, SHAKE;
    public static final ForgeConfigSpec.BooleanValue CALM_CAMERA, HUD;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.comment("Hulk: what you see").push("visuals");
        EFFECTS = b.comment("Amount of particles and effects (0 = minimal, 1 = full)").defineInRange("effects", 1.0, 0, 2);
        SHAKE = b.comment("Camera shake strength (0 = off)").defineInRange("cameraShake", 1.0, 0, 2);
        CALM_CAMERA = b.comment("Gentler camera moves in the Gamma Rage film (no whip pans, less shake)").define("calmCinematicCamera", false);
        HUD = b.comment("Show the Hulk HUD").define("hud", true);
        b.pop();
        CLIENT = b.build();
    }

    /**
     * A setting's value, or its default while the file is not loaded yet (or not at all on this side):
     * Hulk never crashes the game over a setting.
     */
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> value) {
        try { return value.get(); } catch (IllegalStateException | NullPointerException e) { return value.getDefault(); }
    }

    private HulkConfig() {}
}
