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
    public static final ForgeConfigSpec.DoubleValue THERMAL_RANGE, THERMAL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue MINE_DAMAGE, MINE_LAUNCH, MINE_TRIGGER, MINE_SECONDS;
    public static final ForgeConfigSpec.IntValue MINE_MAX;
    public static final ForgeConfigSpec.IntValue CD_SMOKE, CD_FLASH, CD_THERMAL, CD_MINE, CD_GRAPNEL, CD_DODGE;
    public static final ForgeConfigSpec.DoubleValue GRAPNEL_RANGE, STRIKE_UPPER_DAMAGE, STRIKE_KICK_DAMAGE;

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
        BATARANG_SPEED = b.comment("Flying speed").defineInRange("speed", 1.9, .3, 6);
        BATARANG_REFILL_SECONDS = b.comment("Seconds for one Batarang to come back").defineInRange("refillSeconds", 2.0, .1, 60);
        b.pop();
        b.comment("R: gadgets (hold R for the wheel, tap R to use the one picked)").push("gadgets");
        SMOKE_RADIUS = b.comment("Smoke cloud radius").defineInRange("smokeRadius", 7.0, 1, 20);
        SMOKE_SECONDS = b.comment("How long the smoke stays").defineInRange("smokeSeconds", 10.0, 1, 60);
        SMOKE_DAMAGE = b.comment("Damage every second to those inside the smoke").defineInRange("smokeDamage", 1.0, 0, 20);
        FLASH_RADIUS = b.comment("Flash bomb radius (those who can see it)").defineInRange("flashRadius", 10.0, 1, 30);
        FLASH_SECONDS = b.comment("How long the flash blinds").defineInRange("flashSeconds", 4.0, .5, 20);
        THERMAL_RANGE = b.comment("Thermal sensor scan range").defineInRange("thermalRange", 32.0, 4, 96);
        THERMAL_SECONDS = b.comment("How long he sees them through walls").defineInRange("thermalSeconds", 8.0, 1, 60);
        MINE_DAMAGE = b.defineInRange("mineDamage", 7.0, 0, 100);
        MINE_LAUNCH = b.comment("How hard the mine throws them up").defineInRange("mineLaunch", 1.3, 0, 4);
        MINE_TRIGGER = b.comment("How close someone must come to set it off").defineInRange("mineTrigger", 2.2, .5, 8);
        MINE_SECONDS = b.comment("How long a mine waits").defineInRange("mineSeconds", 60.0, 5, 600);
        MINE_MAX = b.comment("Mines he can have out at once").defineInRange("mineMax", 3, 1, 10);
        CD_SMOKE = b.comment("Smoke bomb cooldown (ticks)").defineInRange("smokeCooldown", 300, 0, 6000);
        CD_FLASH = b.comment("Flash bomb cooldown (ticks)").defineInRange("flashCooldown", 240, 0, 6000);
        CD_THERMAL = b.comment("Thermal sensor cooldown (ticks)").defineInRange("thermalCooldown", 300, 0, 6000);
        CD_MINE = b.comment("Mine cooldown (ticks)").defineInRange("mineCooldown", 120, 0, 6000);
        b.pop();
        b.comment("E: the grapnel gun").push("grapnel");
        GRAPNEL_RANGE = b.comment("How far the hook reaches").defineInRange("range", 45.0, 4, 120);
        CD_GRAPNEL = b.comment("Cooldown after a pull (ticks)").defineInRange("cooldown", 20, 0, 1200);
        STRIKE_UPPER_DAMAGE = b.comment("Grapnel strike: the uppercut").defineInRange("uppercutDamage", 5.0, 0, 100);
        STRIKE_KICK_DAMAGE = b.comment("Grapnel strike: the kick").defineInRange("kickDamage", 6.0, 0, 100);
        b.pop();
        b.comment("CTRL: the roll").push("dodge");
        CD_DODGE = b.comment("Cooldown between rolls (ticks)").defineInRange("cooldown", 14, 0, 400);
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
