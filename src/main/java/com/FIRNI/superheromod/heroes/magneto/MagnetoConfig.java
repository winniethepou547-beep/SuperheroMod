package com.FIRNI.superheromod.heroes.magneto;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Magneto's tuning, in config/superheromod-magneto.toml (gameplay, for everyone on the server) and
 * config/superheromod-magneto-client.toml (what each player sees). Every value is read live.
 * Distances are in blocks, speeds in blocks per tick (20 ticks = 1 second), times in ticks.
 */
public final class MagnetoConfig {
    // ------------------------------------------------------------------ server
    public static final ForgeConfigSpec COMMON;
    public static final ForgeConfigSpec.BooleanValue FRIENDLY_FIRE;
    public static final ForgeConfigSpec.DoubleValue FLY_SPEED, FLY_RISE, FLIGHT_REFILL;
    public static final ForgeConfigSpec.IntValue FLIGHT_TIME;
    public static final ForgeConfigSpec.DoubleValue SHARD_DAMAGE, SHARD_SPEED, SPIKE_KNOCKBACK, SPIKE_IMMUNITY, SPIKE_MOB_STUCK, SPIKE_MAX_STUCK;
    public static final ForgeConfigSpec.IntValue SHARD_COOLDOWN, SPIKE_SLOW, SPIKE_CLICKS;
    public static final ForgeConfigSpec.DoubleValue BARRAGE_RANGE, ROD_HEIGHT, ROD_DAMAGE, ROD_RADIUS, ROD_SPREAD;
    public static final ForgeConfigSpec.IntValue BARRAGE_RODS, BARRAGE_CHARGES, BARRAGE_RECHARGE, BARRAGE_GAP;
    public static final ForgeConfigSpec.DoubleValue GRAB_RANGE, SLAM_DAMAGE, SLAM_SPEED, THROW_SPEED, HOLD_MIN, HOLD_MAX, DRAG_SPEED;
    public static final ForgeConfigSpec.IntValue GRAB_TICKS, GRAB_COOLDOWN, SCRAP_PIECES;
    public static final ForgeConfigSpec.BooleanValue SLAM_SHOCKWAVE;
    public static final ForgeConfigSpec.DoubleValue FIST_RANGE, FIST_HEIGHT, PUNCH_DAMAGE, PUNCH_RADIUS, PUNCH_KNOCK;
    public static final ForgeConfigSpec.IntValue FIST_PUNCHES, FIST_TIME, FIST_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue SHIELD_RADIUS, SHIELD_REDUCTION, BURST_DAMAGE, BURST_SPEED;
    public static final ForgeConfigSpec.IntValue SHIELD_COLUMNS, BURST_PIECES, SHIELD_COOLDOWN, SHIELD_MAX;
    public static final ForgeConfigSpec.BooleanValue SHIELD_BLOCKS_PROJECTILES;
    public static final ForgeConfigSpec.IntValue ULT_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue ULT_RANGE, ULT_DAMAGE, ULT_KNOCK;

    // ------------------------------------------------------------------ client
    public static final ForgeConfigSpec CLIENT;
    public static final ForgeConfigSpec.DoubleValue SHAKE, EFFECTS;
    public static final ForgeConfigSpec.BooleanValue FIELD_LINES;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.comment("Magneto: rules for everyone on the server").push("rules");
        FRIENDLY_FIRE = b.comment("His attacks also hurt players on his own team").define("friendlyFire", false);
        b.pop();
        b.comment("Flight (SHIFT or jump twice): he lifts off and floats").push("flight");
        FLY_SPEED = b.comment("Flying speed (blocks per tick; 0.5 = 10 blocks a second)").defineInRange("speed", .5, .05, 3);
        FLY_RISE = b.comment("How fast he rises and sinks").defineInRange("riseSpeed", .32, .05, 2);
        FLIGHT_TIME = b.comment("Longest he can fly in one go (ticks; 160 = 8 seconds); then he glides down").defineInRange("flightTime", 160, 20, 12000);
        FLIGHT_REFILL = b.comment("Seconds on the ground to fill the flight back up from empty").defineInRange("flightRefillSeconds", 6.0, .5, 600);
        b.pop();
        b.comment("Left click: an iron spike built from bits of metal over his hand (0.7 s), then thrown. It sticks in the",
                "one it hits (pushed back, slowed) until they mash left click to pull it out; then they are immune a while").push("ironSpike");
        SHARD_DAMAGE = b.defineInRange("damage", 4.0, 0, 100);
        SHARD_SPEED = b.comment("Flying speed (blocks per tick)").defineInRange("speed", 1.7, .2, 8);
        SHARD_COOLDOWN = b.comment("Ticks from one click to the next (the 14-tick forming included)").defineInRange("cooldown", 30, 14, 400);
        SPIKE_KNOCKBACK = b.comment("How far the hit pushes them back (about, in blocks)").defineInRange("knockbackBlocks", 3.0, 0, 20);
        SPIKE_SLOW = b.comment("Slowness level while it is stuck (0 = Slowness I)").defineInRange("slownessLevel", 2, 0, 9);
        SPIKE_CLICKS = b.comment("Left clicks a player needs to pull it out (it slides back in slowly between clicks)").defineInRange("pullClicks", 12, 1, 100);
        SPIKE_IMMUNITY = b.comment("Seconds after pulling one out in which spikes no longer stick in them").defineInRange("immunitySeconds", 30.0, 0, 600);
        SPIKE_MOB_STUCK = b.comment("Seconds a mob (it cannot click) carries it").defineInRange("mobStuckSeconds", 3.0, .5, 60);
        SPIKE_MAX_STUCK = b.comment("Longest a player carries it whatever happens (seconds)").defineInRange("maxStuckSeconds", 20.0, 1, 600);
        b.pop();
        b.comment("Q: Iron Barrage. Iron rods driven down from the sky onto the spot he aims at").push("ironBarrage");
        BARRAGE_RANGE = b.comment("How far he can aim it").defineInRange("range", 40.0, 4, 120);
        ROD_HEIGHT = b.comment("How high above the spot the rods appear").defineInRange("height", 8.0, 2, 40);
        BARRAGE_RODS = b.comment("Rods in one use").defineInRange("rods", 5, 1, 30);
        BARRAGE_GAP = b.comment("Ticks between rods").defineInRange("gap", 2, 0, 20);
        ROD_SPREAD = b.comment("How far apart the rods land round the spot").defineInRange("spread", 2.2, 0, 10);
        ROD_DAMAGE = b.comment("Damage of each rod's impact").defineInRange("damage", 7.0, 0, 200);
        ROD_RADIUS = b.comment("Radius of each impact").defineInRange("radius", 2.4, .5, 10);
        BARRAGE_CHARGES = b.comment("Uses stored").defineInRange("charges", 3, 1, 10);
        BARRAGE_RECHARGE = b.comment("Ticks to get one use back").defineInRange("recharge", 140, 1, 6000);
        b.pop();
        b.comment("E: Metal Scrap Telekinesis. Scrap wraps the target; he drags them through the air with his aim").push("telekinesis");
        GRAB_RANGE = b.defineInRange("range", 24.0, 2, 80);
        GRAB_TICKS = b.comment("How long he holds them (3 seconds = 60)").defineInRange("holdTicks", 60, 10, 400);
        HOLD_MIN = b.comment("Nearest he holds them").defineInRange("holdMin", 4.0, 1, 30);
        HOLD_MAX = b.comment("Farthest he holds them").defineInRange("holdMax", 14.0, 2, 60);
        DRAG_SPEED = b.comment("Fastest they are dragged (blocks per tick)").defineInRange("dragSpeed", 1.1, .1, 5);
        SLAM_DAMAGE = b.comment("Damage of a slam into the ground or a wall at full speed").defineInRange("slamDamage", 8.0, 0, 200);
        SLAM_SPEED = b.comment("Least speed for a slam to hurt").defineInRange("slamSpeed", .35, 0, 5);
        SLAM_SHOCKWAVE = b.comment("A slam throws a small shockwave").define("slamShockwave", true);
        THROW_SPEED = b.comment("Speed of the throw (left click while holding)").defineInRange("throwSpeed", 2.2, 0, 8);
        SCRAP_PIECES = b.comment("Pieces of scrap that wrap the target").defineInRange("scrapPieces", 16, 2, 60);
        GRAB_COOLDOWN = b.defineInRange("cooldown", 260, 0, 6000);
        b.pop();
        b.comment("R: Giant Iron Fist. It follows his aim; left click punches down").push("ironFist");
        FIST_RANGE = b.comment("How far away it can be").defineInRange("range", 30.0, 4, 100);
        FIST_HEIGHT = b.comment("How high over the ground it floats").defineInRange("height", 5.0, 2, 20);
        FIST_PUNCHES = b.defineInRange("punches", 5, 1, 20);
        PUNCH_DAMAGE = b.defineInRange("damage", 9.0, 0, 200);
        PUNCH_RADIUS = b.defineInRange("radius", 3.6, .5, 12);
        PUNCH_KNOCK = b.comment("How hard a punch throws those round it").defineInRange("knockback", .9, 0, 5);
        FIST_TIME = b.comment("Ticks before an unused fist falls apart").defineInRange("time", 300, 20, 6000);
        FIST_COOLDOWN = b.defineInRange("cooldown", 400, 0, 6000);
        b.pop();
        b.comment("F: Magnetic Iron Shield. Iron columns rise round him; again: they burst outward").push("ironShield");
        SHIELD_COLUMNS = b.defineInRange("columns", 8, 3, 24);
        SHIELD_RADIUS = b.defineInRange("radius", 2.3, 1, 8);
        SHIELD_REDUCTION = b.comment("Share of a blow from close by the columns take (projectiles are stopped outright)").defineInRange("reduction", .7, 0, 1);
        SHIELD_BLOCKS_PROJECTILES = b.comment("Arrows and other projectiles are stopped by the columns").define("blocksProjectiles", true);
        SHIELD_MAX = b.comment("Longest the shield stands before it bursts by itself (ticks)").defineInRange("maxTicks", 600, 20, 12000);
        BURST_PIECES = b.comment("Pieces the columns burst into").defineInRange("pieces", 28, 4, 120);
        BURST_DAMAGE = b.comment("Damage of each piece that hits").defineInRange("pieceDamage", 5.0, 0, 200);
        BURST_SPEED = b.comment("Speed of the pieces").defineInRange("pieceSpeed", 1.5, .2, 6);
        SHIELD_COOLDOWN = b.comment("Ticks after the burst before the shield can rise again").defineInRange("cooldown", 360, 0, 6000);
        b.pop();
        b.comment("X: Magnetic Execution (the film), aimed at the one he looks at").push("magneticExecution");
        ULT_COOLDOWN = b.defineInRange("cooldown", 1800, 0, 72000);
        ULT_RANGE = b.defineInRange("range", 16.0, 2, 60);
        ULT_DAMAGE = b.comment("Share of the target's max health it takes when the metal crushes them (0.7 = 70%)").defineInRange("damage", .7, 0, 1);
        ULT_KNOCK = b.comment("How hard they are thrown when it ends").defineInRange("knockback", 2.0, 0, 8);
        b.pop();
        COMMON = b.build();

        var c = new ForgeConfigSpec.Builder();
        c.comment("Magneto: what you see").push("look");
        SHAKE = c.comment("Camera shake (0 = none, 1 = normal)").defineInRange("cameraShake", 1.0, 0, 2);
        EFFECTS = c.comment("Amount of sparks, dust and metal bits (0 = least, 1 = normal)").defineInRange("effects", 1.0, 0, 2);
        FIELD_LINES = c.comment("Faint magnetic field lines round the metal he controls").define("fieldLines", true);
        c.pop();
        CLIENT = c.build();
    }

    private MagnetoConfig() {}

    static float f(ForgeConfigSpec.DoubleValue v) { return v.get().floatValue(); }
}
