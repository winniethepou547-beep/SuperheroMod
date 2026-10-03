package com.FIRNI.superheromod.heroes.panther;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Black Panther's tuning, in config/superheromod-panther.toml (gameplay, for everyone on the server)
 * and config/superheromod-panther-client.toml (what each player sees). Every value is read live.
 * Distances are in blocks, speeds in blocks per tick (20 ticks = 1 second), times in ticks.
 */
public final class PantherConfig {
    // ------------------------------------------------------------------ server
    public static final ForgeConfigSpec COMMON;
    public static final ForgeConfigSpec.BooleanValue FRIENDLY_FIRE;
    public static final ForgeConfigSpec.DoubleValue SPRINT_BONUS;
    public static final ForgeConfigSpec.DoubleValue CLAW_DAMAGE, DOUBLE_DAMAGE, UPPER_DAMAGE, CLAW_REACH, CLAW_KNOCK, UPPER_LIFT;
    public static final ForgeConfigSpec.DoubleValue FRENZY_DAMAGE, FRENZY_RANGE;
    public static final ForgeConfigSpec.IntValue FRENZY_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue POUNCE_SPEED, POUNCE_DISTANCE, CONTACT_DAMAGE, KICK_DAMAGE, KICK_SPEED, KICK_LIFT;
    public static final ForgeConfigSpec.DoubleValue SCRAPE_KEEP, FLIP_HEIGHT;
    public static final ForgeConfigSpec.IntValue SCRAPE_TICKS, POUNCE_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue SPIN_DAMAGE, SPIN_FINAL_DAMAGE, SPIN_DISTANCE, SPIN_HEIGHT, SPIN_REACH, SPIN_KNOCK;
    public static final ForgeConfigSpec.IntValue SPIN_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue ENERGY_MAX, ENERGY_PER_DAMAGE, ENERGY_PER_HIT, RELEASE_MIN;
    public static final ForgeConfigSpec.DoubleValue RELEASE_RADIUS_MIN, RELEASE_RADIUS_MAX, RELEASE_DAMAGE_MIN, RELEASE_DAMAGE_MAX;
    public static final ForgeConfigSpec.DoubleValue RELEASE_KNOCK_MIN, RELEASE_KNOCK_MAX, RELEASE_LIFT_MIN, RELEASE_LIFT_MAX;
    public static final ForgeConfigSpec.IntValue RELEASE_COOLDOWN;
    public static final ForgeConfigSpec.IntValue REFLEX_DURATION, REFLEX_COOLDOWN, REFLEX_MAX_DODGES;
    public static final ForgeConfigSpec.DoubleValue DODGE_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue DODGE_PROJECTILES;

    // ------------------------------------------------------------------ client
    public static final ForgeConfigSpec CLIENT;
    public static final ForgeConfigSpec.BooleanValue FLIP_CAMERA, AFTERIMAGES;
    public static final ForgeConfigSpec.DoubleValue SHAKE, EFFECTS;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.comment("Black Panther: rules for everyone on the server").push("rules");
        FRIENDLY_FIRE = b.comment("His attacks also hurt and throw players on his own team").define("friendlyFire", false);
        SPRINT_BONUS = b.comment("How much faster than a normal sprint he runs (0.6 = 60% faster)").defineInRange("sprintBonus", .6, 0, 3);
        b.pop();
        b.comment("Left click: the claw combo and, held with a target close, the frenzy").push("claws");
        CLAW_DAMAGE = b.comment("Right and left claw").defineInRange("damage", 5.0, 0, 100);
        DOUBLE_DAMAGE = b.comment("Both claws at once").defineInRange("doubleDamage", 7.0, 0, 100);
        UPPER_DAMAGE = b.comment("The claw uppercut").defineInRange("uppercutDamage", 9.0, 0, 100);
        CLAW_REACH = b.defineInRange("reach", 3.4, 1, 8);
        CLAW_KNOCK = b.comment("How far a claw strike pushes (speed)").defineInRange("knockback", .25, 0, 3);
        UPPER_LIFT = b.comment("How high the uppercut lifts (upward speed)").defineInRange("uppercutLift", .62, 0, 3);
        FRENZY_DAMAGE = b.comment("Each frenzy strike").defineInRange("frenzyDamage", 2.5, 0, 100);
        FRENZY_RANGE = b.comment("A target must be this close for the frenzy to start or go on").defineInRange("frenzyRange", 3.6, 1, 8);
        FRENZY_COOLDOWN = b.comment("Ticks after a frenzy before the next one").defineInRange("frenzyCooldown", 60, 0, 2000);
        b.pop();
        b.comment("SHIFT: Panther Pounce (flip over the target, kick, they fly and scrape along the ground)").push("pounce");
        POUNCE_SPEED = b.defineInRange("speed", 2.1, .5, 6);
        POUNCE_DISTANCE = b.comment("Longest pounce").defineInRange("distance", 14.0, 2, 40);
        CONTACT_DAMAGE = b.defineInRange("contactDamage", 3.0, 0, 100);
        KICK_DAMAGE = b.defineInRange("kickDamage", 10.0, 0, 100);
        KICK_SPEED = b.comment("How fast the kicked target flies away").defineInRange("kickSpeed", 1.7, 0, 6);
        KICK_LIFT = b.comment("Upward speed of the kicked target").defineInRange("kickLift", .48, 0, 3);
        FLIP_HEIGHT = b.comment("How high over the target's head he passes").defineInRange("flipHeight", 1.1, .3, 4);
        SCRAPE_TICKS = b.comment("How long the target slides along the ground once it comes down").defineInRange("scrapeTicks", 16, 0, 100);
        SCRAPE_KEEP = b.comment("How much of the sliding speed is kept each tick (higher = slides further)").defineInRange("scrapeKeep", .86, .5, .98);
        POUNCE_COOLDOWN = b.defineInRange("cooldown", 120, 0, 4000);
        b.pop();
        b.comment("Q: the spinning triple kick (right, left, right without landing)").push("spinKick");
        SPIN_DAMAGE = b.comment("First and second kick").defineInRange("damage", 5.0, 0, 100);
        SPIN_FINAL_DAMAGE = b.comment("Third kick").defineInRange("finalDamage", 9.0, 0, 100);
        SPIN_DISTANCE = b.comment("How far forward the spin carries him").defineInRange("distance", 3.2, 0, 12);
        SPIN_HEIGHT = b.comment("How high he rises").defineInRange("height", 1.3, 0, 5);
        SPIN_REACH = b.defineInRange("reach", 3.3, 1, 8);
        SPIN_KNOCK = b.comment("How hard the third kick throws").defineInRange("finalKnockback", 1.15, 0, 5);
        SPIN_COOLDOWN = b.defineInRange("cooldown", 140, 0, 4000);
        b.pop();
        b.comment("E: the suit stores kinetic energy from the hits he takes and releases it as a sphere").push("kineticRelease");
        ENERGY_MAX = b.defineInRange("maxEnergy", 100.0, 1, 10000);
        ENERGY_PER_DAMAGE = b.comment("Energy stored per point of damage he takes").defineInRange("energyPerDamage", 6.0, 0, 1000);
        ENERGY_PER_HIT = b.comment("Energy stored per point of damage he deals (0 = only taking hits charges the suit)").defineInRange("energyPerHitDealt", 0.0, 0, 1000);
        RELEASE_MIN = b.comment("Least energy needed for a release").defineInRange("minEnergy", 5.0, 0, 10000);
        RELEASE_RADIUS_MIN = b.comment("Sphere radius with almost no energy").defineInRange("radiusMin", 3.5, .5, 40);
        RELEASE_RADIUS_MAX = b.comment("Sphere radius fully charged").defineInRange("radiusMax", 9.0, .5, 40);
        RELEASE_DAMAGE_MIN = b.defineInRange("damageMin", 4.0, 0, 200);
        RELEASE_DAMAGE_MAX = b.defineInRange("damageMax", 16.0, 0, 200);
        RELEASE_KNOCK_MIN = b.comment("Outward speed of the thrown, little energy").defineInRange("knockbackMin", 1.0, 0, 8);
        RELEASE_KNOCK_MAX = b.comment("Outward speed of the thrown, full energy").defineInRange("knockbackMax", 3.0, 0, 8);
        RELEASE_LIFT_MIN = b.comment("Upward speed added, little energy").defineInRange("liftMin", .4, 0, 4);
        RELEASE_LIFT_MAX = b.comment("Upward speed added, full energy").defineInRange("liftMax", .9, 0, 4);
        RELEASE_COOLDOWN = b.defineInRange("cooldown", 160, 0, 4000);
        b.pop();
        b.comment("R: Panther Reflex (automatic dodges)").push("reflex");
        REFLEX_DURATION = b.defineInRange("duration", 120, 1, 2000);
        REFLEX_COOLDOWN = b.defineInRange("cooldown", 400, 0, 6000);
        REFLEX_MAX_DODGES = b.comment("Most attacks dodged in one activation").defineInRange("maxDodges", 10, 1, 200);
        DODGE_DISTANCE = b.comment("How far a sidestep carries him").defineInRange("dodgeDistance", 1.6, 0, 6);
        DODGE_PROJECTILES = b.comment("Arrows and other projectiles are dodged too").define("dodgeProjectiles", true);
        b.pop();
        COMMON = b.build();

        var c = new ForgeConfigSpec.Builder();
        c.comment("Black Panther: what you see").push("look");
        FLIP_CAMERA = c.comment("The camera swings round the action while he flips over a target").define("flipCamera", true);
        AFTERIMAGES = c.comment("Fast moves leave faint afterimages behind him").define("afterimages", true);
        SHAKE = c.comment("Camera shake (0 = none, 1 = normal)").defineInRange("cameraShake", 1.0, 0, 2);
        EFFECTS = c.comment("Amount of dust and debris (0 = least, 1 = normal)").defineInRange("effects", 1.0, 0, 2);
        c.pop();
        CLIENT = c.build();
    }

    private PantherConfig() {}

    static float f(ForgeConfigSpec.DoubleValue v) { return v.get().floatValue(); }
}
