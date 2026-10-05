package com.FIRNI.superheromod.heroes.batman;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Batman's tuning, in config/superheromod-batman.toml (gameplay, for everyone on the server) and
 * config/superheromod-batman-client.toml (what each player sees). Every value is read live.
 * Distances are in blocks, speeds in blocks per tick (20 ticks = 1 second), times in ticks unless named seconds.
 */
public final class BatmanConfig {
    // ------------------------------------------------------------------ server
    public static final ForgeConfigSpec COMMON;
    public static final ForgeConfigSpec.BooleanValue FRIENDLY_FIRE;
    public static final ForgeConfigSpec.DoubleValue PUNCH_DAMAGE, PUNCH_RAPID_DAMAGE, PUNCH_REACH, PUNCH_KNOCK;
    public static final ForgeConfigSpec.DoubleValue BATARANG_DAMAGE, BATARANG_SPEED, BATARANG_REFILL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue SMOKE_RADIUS, SMOKE_SECONDS, SMOKE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue FLASH_RADIUS, FLASH_SECONDS;
    public static final ForgeConfigSpec.DoubleValue STICKY_DAMAGE, STICKY_LAUNCH, STICKY_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SHOCK_DAMAGE, SHOCK_ENERGY_PER_HIT, SHOCK_DRAIN_SECONDS, SHOCK_EMPTY_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue REFLEX_SECONDS, REFLEX_GAUNTLET_RANGE;
    public static final ForgeConfigSpec.DoubleValue CANNON_DAMAGE, CANNON_RANGE, CANNON_KNOCK, CANNON_MAX_DAMAGE, CANNON_SPREAD;
    public static final ForgeConfigSpec.IntValue CANNON_SHOTS_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue SONIC_DISTANCE, SONIC_RANGE, SONIC_HEALTH, SONIC_SECONDS, SONIC_SLOW, SONIC_SLOW_SECONDS;
    public static final ForgeConfigSpec.IntValue CD_SMOKE, CD_FLASH, CD_SHOCK, CD_CANNON, CD_SONIC, CD_BATMOBILE, CD_GRAPNEL, CD_DODGE, CD_REFLEX;
    public static final ForgeConfigSpec.DoubleValue GRAPNEL_RANGE, STRIKE_UPPER_DAMAGE, STRIKE_KICK_DAMAGE;
    public static final ForgeConfigSpec.IntValue BIND_CLICKS, BIND_MOB_TICKS, BIND_MAX_TICKS;
    /** X: the film. */
    public static final ForgeConfigSpec.DoubleValue ULT_DAMAGE, ULT_RANGE;
    public static final ForgeConfigSpec.IntValue ULT_COOLDOWN;

    // ------------------------------------------------------------------ client
    public static final ForgeConfigSpec CLIENT;
    public static final ForgeConfigSpec.DoubleValue SHAKE, EFFECTS;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.comment("Batman: rules for everyone on the server").push("rules");
        FRIENDLY_FIRE = b.comment("His attacks and gadgets also hit players on his own team").define("friendlyFire", false);
        b.pop();
        b.comment("Left click: punches (the chain speeds up into a rapid flurry)").push("punches");
        PUNCH_DAMAGE = b.comment("Damage of a punch in the chain (rises a little blow by blow)").defineInRange("damage", 3.0, 0, 100);
        PUNCH_RAPID_DAMAGE = b.comment("Damage of each blow of the rapid flurry").defineInRange("rapidDamage", 1.6, 0, 100);
        PUNCH_REACH = b.comment("How far his punches reach").defineInRange("reach", 3.3, 1, 8);
        PUNCH_KNOCK = b.comment("Knockback of a punch").defineInRange("knockback", .35, 0, 3);
        b.pop();
        b.comment("Right click: Batarangs (tap: one; held: up to five)").push("batarangs");
        BATARANG_DAMAGE = b.defineInRange("damage", 3.5, 0, 100);
        BATARANG_SPEED = b.comment("Flying speed").defineInRange("flightSpeed", 2.4, .3, 6);
        BATARANG_REFILL_SECONDS = b.comment("Seconds for one Batarang to come back").defineInRange("refillSeconds", 2.0, .1, 60);
        b.pop();
        b.comment("R: gadgets (hold R for the wheel, tap R to use the one picked)").push("gadgets");
        SMOKE_RADIUS = b.comment("Smoke cloud radius").defineInRange("smokeCloudRadius", 16.0, 1, 40);
        SMOKE_SECONDS = b.comment("How long the smoke stays").defineInRange("smokeSeconds", 10.0, 1, 60);
        SMOKE_DAMAGE = b.comment("Damage every second to those inside the smoke").defineInRange("smokeDamage", 1.0, 0, 20);
        FLASH_RADIUS = b.comment("Flash bomb radius (those who can see it)").defineInRange("flashRadius", 10.0, 1, 30);
        FLASH_SECONDS = b.comment("How long the flash blinds").defineInRange("flashSeconds", 4.0, .5, 20);
        CD_SMOKE = b.comment("Smoke bomb cooldown (ticks)").defineInRange("smokeCooldown", 300, 0, 6000);
        CD_FLASH = b.comment("Flash bomb cooldown (ticks)").defineInRange("flashCooldown", 240, 0, 6000);
        b.pop();
        b.comment("Gadget: the electric gauntlets (R on / off; left click becomes a heavy electric boxing combo)").push("electricGauntlets");
        SHOCK_DAMAGE = b.comment("Damage of a charged blow that lands (an uncharged one does about half)").defineInRange("blowDamage", 6.0, 0, 100);
        SHOCK_ENERGY_PER_HIT = b.comment("Energy a blow that lands uses (1 = all of it)").defineInRange("energyPerHit", .23, 0, 1);
        SHOCK_DRAIN_SECONDS = b.comment("Seconds a full charge lasts without hitting anything").defineInRange("idleDrainSeconds", 90.0, 5, 3600);
        SHOCK_EMPTY_COOLDOWN = b.comment("Seconds before they can be put on again once the charge ran out (they come off by themselves, and are charged again when put on)").defineInRange("emptyCooldownSeconds", 15.0, 0, 600);
        CD_SHOCK = b.comment("Cooldown between putting them on and taking them off (ticks)").defineInRange("cooldown", 20, 0, 1200);
        b.pop();
        b.comment("Gadget: the dual wrist cannon (4 seconds of aimed rapid fire from both gauntlets)").push("wristCannon");
        CANNON_DAMAGE = b.comment("Damage of each shot that hits").defineInRange("shotDamageHp", 1.4, 0, 50);
        CANNON_SHOTS_PER_SECOND = b.comment("Shots per second (both hands together)").defineInRange("shotsPerSecond", 24, 2, 60);
        CANNON_RANGE = b.comment("How far the shots reach").defineInRange("range", 40.0, 4, 120);
        CANNON_KNOCK = b.comment("Push of each hit (it adds up)").defineInRange("knockback", .09, 0, 2);
        CANNON_MAX_DAMAGE = b.comment("Most damage one target can take from one volley (it is meant to put them down, not kill)").defineInRange("maxDamagePerVolley", 30.0, 0, 200);
        CANNON_SPREAD = b.comment("Scatter of the shots round the crosshair (degrees)").defineInRange("spreadDegrees", .7, 0, 10);
        CD_CANNON = b.comment("Cooldown (ticks)").defineInRange("cooldown", 400, 0, 12000);
        b.pop();
        b.comment("Gadget: the sonic trap (two emitters rise out of the ground and blast the target with sound: no damage)").push("sonicTrap");
        SONIC_DISTANCE = b.comment("How far from him the two emitters come up").defineInRange("emitterDistance", 8.0, 2, 24);
        SONIC_RANGE = b.comment("How far a target may be for the emitters to reach it").defineInRange("range", 28.0, 4, 64);
        SONIC_HEALTH = b.comment("Health of each emitter (2 = one heart)").defineInRange("emitterHealth", 8.0, 1, 200);
        SONIC_SECONDS = b.comment("How long the emitters stay active").defineInRange("activeSeconds", 4.0, .5, 30);
        SONIC_SLOW = b.comment("Slow on the target per hit (0.75 = 75 % slower)").defineInRange("slow", .75, 0, .95);
        SONIC_SLOW_SECONDS = b.comment("How long each hit's slow lasts").defineInRange("slowSeconds", 1.25, .1, 10);
        CD_SONIC = b.comment("Cooldown (ticks)").defineInRange("cooldown", 500, 0, 12000);
        b.pop();
        b.comment("Gadget: the Batmobile (R calls it: it drives in and parks beside him; R again sends it away). Its other features are still to be decided").push("batmobile");
        CD_BATMOBILE = b.comment("Cooldown between calling it and sending it away (ticks)").defineInRange("cooldown", 60, 0, 12000);
        b.pop();
        b.comment("E: the grapnel gun").push("grapnel");
        GRAPNEL_RANGE = b.comment("How far the hook reaches").defineInRange("range", 45.0, 4, 120);
        CD_GRAPNEL = b.comment("Cooldown after a pull (ticks)").defineInRange("cooldown", 20, 0, 1200);
        STRIKE_UPPER_DAMAGE = b.comment("Grapnel strike: the uppercut").defineInRange("uppercutDamage", 5.0, 0, 100);
        STRIKE_KICK_DAMAGE = b.comment("Grapnel strike: the kick").defineInRange("kickDamage", 6.0, 0, 100);
        STICKY_DAMAGE = b.comment("Grapnel strike: the sticky bomb left on the back of their head (goes off 2.5 s later)").defineInRange("stickyBombDamage", 6.0, 0, 100);
        STICKY_LAUNCH = b.comment("How hard the sticky bomb throws them").defineInRange("stickyBombLaunch", .9, 0, 4);
        STICKY_RADIUS = b.comment("The sticky bomb's blast radius (others near them are hit too)").defineInRange("stickyBombRadius", 3.0, 0, 12);
        BIND_CLICKS = b.comment("Right-click yank: clicks a bound player needs to break free").defineInRange("bindClicks", 12, 1, 100);
        BIND_MOB_TICKS = b.comment("Right-click yank: ticks a bound mob needs to break free").defineInRange("bindMobTicks", 60, 1, 1200);
        BIND_MAX_TICKS = b.comment("Right-click yank: the longest anyone stays bound (ticks)").defineInRange("bindMaxTicks", 160, 10, 2400);
        b.pop();
        b.comment("Q: the reflex block (front half only; gauntlets up close, the cape against what comes from further off)").push("reflexBlock");
        REFLEX_SECONDS = b.comment("How long the window stays open").defineInRange("windowSeconds", 1.0, .1, 5);
        REFLEX_GAUNTLET_RANGE = b.comment("Attacks from within this distance are deflected with the gauntlets, further off with the cape").defineInRange("gauntletRange", 5.0, 0, 64);
        CD_REFLEX = b.comment("Cooldown (ticks)").defineInRange("cooldown", 120, 0, 6000);
        b.pop();
        b.comment("X: the film (Kara Şövalye), aimed at the one he looks at").push("film");
        ULT_DAMAGE = b.comment("Share of the target's max health the Batarang takes when it pins them to the wall (0.7 = 70%)").defineInRange("damage", .7, 0, 1);
        ULT_RANGE = b.comment("How far away the one he aims it at may be").defineInRange("range", 20.0, 2, 60);
        ULT_COOLDOWN = b.comment("Cooldown (ticks, counted from the start of the film)").defineInRange("cooldown", 1800, 0, 72000);
        b.pop();
        b.comment("CTRL: the roll").push("dodge");
        CD_DODGE = b.comment("Cooldown between rolls (ticks, counted from the start of a roll)").defineInRange("rollCooldown", 60, 0, 400);
        b.pop();
        COMMON = b.build();

        var c = new ForgeConfigSpec.Builder();
        c.comment("Batman: what you see").push("look");
        SHAKE = c.comment("Camera shake (0 = none, 1 = normal)").defineInRange("cameraShake", 1.0, 0, 2);
        EFFECTS = c.comment("Amount of smoke, sparks and dust (0 = least, 1 = normal)").defineInRange("effects", 1.0, 0, 2);
        c.pop();
        CLIENT = c.build();
    }

    private BatmanConfig() {}
    static float f(ForgeConfigSpec.DoubleValue v) { return v.get().floatValue(); }
}
