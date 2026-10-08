package com.FIRNI.superheromod.heroes.iceman;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Iceman's tuning, in config/superheromod-iceman.toml (gameplay, for everyone on the server) and
 * config/superheromod-iceman-client.toml (what each player sees). Every value is read live.
 * Distances are in blocks, speeds in blocks per tick (20 ticks = 1 second), times in ticks unless named seconds.
 * Frost is the frost meter (0..100; 100 = deep freeze).
 */
public final class IcemanConfig {
    // ------------------------------------------------------------------ server
    public static final ForgeConfigSpec COMMON;
    public static final ForgeConfigSpec.BooleanValue FRIENDLY_FIRE;
    // frost meter
    public static final ForgeConfigSpec.DoubleValue FROST_DECAY, FROST_DECAY_DELAY, DEEP_FREEZE_SECONDS, DEEP_SHATTER_BONUS, FROST_SLOW;
    // brush
    public static final ForgeConfigSpec.DoubleValue BRUSH_RANGE, BRUSH_FROST, BRUSH_DAMAGE, SCULPT_DISTANCE, SCULPT_SECONDS, SCULPT_THICKNESS;
    public static final ForgeConfigSpec.IntValue SCULPT_MAX;
    // slides
    public static final ForgeConfigSpec.DoubleValue SLIDE_SECONDS, SLIDE_FROST, SLIDE_LENS_SECONDS, DASH_DAMAGE, DASH_FROST, DASH_KNOCK;
    // weapons
    public static final ForgeConfigSpec.DoubleValue MACE_DAMAGE, MACE_FINISH_DAMAGE, MACE_SLAM_DAMAGE, MACE_SLAM_RADIUS, MACE_FROST,
            SPEAR_DAMAGE, SPEAR_FLURRY_DAMAGE, SPEAR_THROW_DAMAGE, SPEAR_SPIKE_DAMAGE, SPEAR_SPIKE_RADIUS, SPEAR_FROST,
            SWORD_DAMAGE, SWORD_FINISH_DAMAGE, SWORD_SPIN_DAMAGE, SWORD_PULL_RADIUS, SWORD_FROST, WEAPON_REACH;
    // shell
    public static final ForgeConfigSpec.DoubleValue SHELL_HEALTH, SHELL_HEAL, SHELL_SECONDS, BURST_DAMAGE, BURST_RADIUS, BURST_KNOCK, BURST_FROST;
    // shattered ground
    public static final ForgeConfigSpec.DoubleValue GROUND_RANGE, GROUND_DAMAGE, GROUND_LAUNCH, GROUND_FROST, GROUND_RADIUS;
    // cooldowns
    public static final ForgeConfigSpec.IntValue CD_DASH, CD_SHELL, CD_GROUND, CD_SLIDE, CD_WEAPON;

    // ------------------------------------------------------------------ client
    public static final ForgeConfigSpec CLIENT;
    public static final ForgeConfigSpec.DoubleValue EFFECTS, SHAKE, SCREEN_FROST, WALK_MIST;
    public static final ForgeConfigSpec.IntValue FAR_DETAIL;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.comment("Iceman: rules for everyone on the server").push("rules");
        FRIENDLY_FIRE = b.comment("His attacks also hit players on his own team").define("friendlyFire", false);
        b.pop();
        b.comment("The frost meter every enemy carries (0..100; at 100 they are deep frozen)").push("frost");
        FROST_DECAY = b.comment("Frost lost per second once nothing cold has touched them for a while").defineInRange("decayPerSecond", 9.0, 0, 100);
        FROST_DECAY_DELAY = b.comment("Seconds without new frost before it starts to wear off").defineInRange("decayDelaySeconds", 2.5, 0, 30);
        DEEP_FREEZE_SECONDS = b.comment("How long a deep freeze holds them still").defineInRange("deepFreezeSeconds", 2.2, .2, 10);
        DEEP_SHATTER_BONUS = b.comment("A blow on someone deep frozen breaks the ice and does this many times its damage").defineInRange("shatterBonus", 1.5, 1, 5);
        FROST_SLOW = b.comment("How much the frost slows them at 99 (0..1 of their speed; less at lower frost)").defineInRange("maxSlow", .45, 0, .95);
        b.pop();
        b.comment("Right click held: the cryogenic brush").push("brush");
        BRUSH_RANGE = b.comment("How far the cold stream reaches a body").defineInRange("range", 14.0, 2, 40);
        BRUSH_FROST = b.comment("Frost per second the stream adds").defineInRange("frostPerSecond", 34.0, 0, 200);
        BRUSH_DAMAGE = b.comment("Damage per second of the stream").defineInRange("damagePerSecond", 1.5, 0, 50);
        SCULPT_DISTANCE = b.comment("How far in front of him the ice is sculpted (nearer if a wall is in the way)").defineInRange("sculptDistance", 6.0, 2, 16);
        SCULPT_SECONDS = b.comment("How long a sculpture stands before it cracks and breaks up").defineInRange("sculptSeconds", 16.0, 2, 120);
        SCULPT_THICKNESS = b.comment("How thick the sculpted ice grows (blocks, its radius)").defineInRange("sculptThickness", .62, .2, 2);
        SCULPT_MAX = b.comment("Most sculptures one Iceman keeps standing (the oldest breaks)").defineInRange("sculptMax", 4, 1, 16);
        b.pop();
        b.comment("SHIFT held: the ice slide; CTRL: the sub-zero slide").push("slides");
        SLIDE_SECONDS = b.comment("Longest ride on the ice slide").defineInRange("slideSeconds", 9.0, 1, 60);
        SLIDE_FROST = b.comment("Frost given to a body the slide touches").defineInRange("slideFrost", 14.0, 0, 100);
        SLIDE_LENS_SECONDS = b.comment("How long a player touched by the slide sees through the icy lens").defineInRange("slideLensSeconds", 3.5, 0, 20);
        DASH_DAMAGE = b.comment("Sub-zero slide: damage of the hit").defineInRange("dashDamage", 4.0, 0, 100);
        DASH_FROST = b.comment("Sub-zero slide: frost of the hit").defineInRange("dashFrost", 22.0, 0, 100);
        DASH_KNOCK = b.comment("Sub-zero slide: knockback of the hit").defineInRange("dashKnockback", .9, 0, 5);
        b.pop();
        b.comment("Left click: the ice weapons (E: pick mace / spear / sword)").push("weapons");
        WEAPON_REACH = b.comment("How far the mace and the sword reach (the spear reaches a little further)").defineInRange("reach", 3.6, 1, 8);
        MACE_DAMAGE = b.comment("Mace: damage of each of the two swings").defineInRange("maceDamage", 6.0, 0, 100);
        MACE_FINISH_DAMAGE = b.comment("Mace: damage of the two-handed overhead slam (third swing)").defineInRange("maceFinishDamage", 10.0, 0, 100);
        MACE_SLAM_DAMAGE = b.comment("Mace held: damage of the giant mace's slam at full size (less when smaller)").defineInRange("maceSlamDamage", 18.0, 0, 200);
        MACE_SLAM_RADIUS = b.comment("Mace held: radius of the slam at full size").defineInRange("maceSlamRadius", 7.0, 1, 30);
        MACE_FROST = b.comment("Mace: frost per hit").defineInRange("maceFrost", 16.0, 0, 100);
        SPEAR_DAMAGE = b.comment("Spear: damage of each of the two thrusts").defineInRange("spearDamage", 3.5, 0, 100);
        SPEAR_FLURRY_DAMAGE = b.comment("Spear: damage of each thrust of the flurry (third swing)").defineInRange("spearFlurryDamage", 2.2, 0, 100);
        SPEAR_THROW_DAMAGE = b.comment("Spear thrown: damage at full draw (less when drawn less)").defineInRange("spearThrowDamage", 12.0, 0, 200);
        SPEAR_SPIKE_DAMAGE = b.comment("Spear thrown: damage of the cluster of ice crystals that erupts where it strikes").defineInRange("spearSpikeDamage", 5.0, 0, 100);
        SPEAR_SPIKE_RADIUS = b.comment("Spear thrown: radius of the eruption at full draw (less when drawn less)").defineInRange("spearSpikeRadius", 3.8, 0, 15);
        SPEAR_FROST = b.comment("Spear: frost per hit").defineInRange("spearFrost", 9.0, 0, 100);
        SWORD_DAMAGE = b.comment("Sword: damage of each of the two cuts").defineInRange("swordDamage", 4.5, 0, 100);
        SWORD_FINISH_DAMAGE = b.comment("Sword: damage of the spin (third swing), to everyone round him").defineInRange("swordFinishDamage", 7.0, 0, 100);
        SWORD_SPIN_DAMAGE = b.comment("Sword held (planted in the ground): damage every half second to anyone dragged up against the sword").defineInRange("swordSpinDamage", 2.5, 0, 100);
        SWORD_PULL_RADIUS = b.comment("Sword held (planted in the ground): how far round the sword enemies are pulled toward it (harder the closer they are)").defineInRange("swordPullRadius", 6.0, 0, 20);
        SWORD_FROST = b.comment("Sword: frost per hit").defineInRange("swordFrost", 11.0, 0, 100);
        b.pop();
        b.comment("Q: the cryogenic shell (Q again: the burst)").push("shell");
        SHELL_HEALTH = b.comment("The shell's own health (it takes the blows instead of him; a player has 20)").defineInRange("health", 80.0, 1, 2000);
        SHELL_HEAL = b.comment("Health he gets back per second inside it").defineInRange("healPerSecond", 2.0, 0, 50);
        SHELL_SECONDS = b.comment("Longest time inside it (then it bursts by itself)").defineInRange("seconds", 8.0, 1, 60);
        BURST_DAMAGE = b.comment("The burst: damage").defineInRange("burstDamage", 9.0, 0, 200);
        BURST_RADIUS = b.comment("The burst: radius").defineInRange("burstRadius", 6.5, 1, 30);
        BURST_KNOCK = b.comment("The burst: knockback (very strong)").defineInRange("burstKnockback", 2.6, 0, 10);
        BURST_FROST = b.comment("The burst: frost").defineInRange("burstFrost", 30.0, 0, 100);
        b.pop();
        b.comment("R: shattered ground").push("ground");
        GROUND_RANGE = b.comment("How far the cracks run to a target").defineInRange("range", 20.0, 3, 60);
        GROUND_DAMAGE = b.comment("Damage of the spikes (more with high frost; most on someone deep frozen)").defineInRange("damage", 8.0, 0, 200);
        GROUND_LAUNCH = b.comment("How hard the spikes throw them up").defineInRange("launch", 1.05, 0, 4);
        GROUND_FROST = b.comment("Frost the spikes give").defineInRange("frost", 25.0, 0, 100);
        GROUND_RADIUS = b.comment("Radius of the eruption").defineInRange("radius", 2.8, .5, 12);
        b.pop();
        b.comment("Cooldowns (ticks)").push("cooldowns");
        CD_DASH = b.defineInRange("subZeroSlide", 60, 0, 6000);
        CD_SHELL = b.defineInRange("shell", 360, 0, 6000);
        CD_GROUND = b.defineInRange("shatteredGround", 200, 0, 6000);
        CD_SLIDE = b.comment("After the ice slide ends").defineInRange("iceSlide", 40, 0, 6000);
        CD_WEAPON = b.comment("Before a broken or thrown weapon forms again").defineInRange("weaponReform", 24, 0, 6000);
        b.pop();
        COMMON = b.build();

        var c = new ForgeConfigSpec.Builder();
        c.comment("Iceman: what this player sees").push("look");
        EFFECTS = c.comment("Amount of ice effects (shards, mist, frost dust): 0..2").defineInRange("effects", 1.0, 0, 2);
        SHAKE = c.comment("Camera shake strength: 0..2").defineInRange("shake", 1.0, 0, 2);
        SCREEN_FROST = c.comment("How strong the frost on your own screen is when you are frozen: 0..1.5").defineInRange("screenFrost", 1.0, 0, 1.5);
        WALK_MIST = c.comment("Cold vapour trailing behind him as he walks (a lighter breath of the brush's mist): 0 off..2").defineInRange("walkMist", 1.0, 0, 2);
        FAR_DETAIL = c.comment("Beyond this many blocks the ice is drawn with less detail").defineInRange("farDetail", 28, 4, 256);
        c.pop();
        CLIENT = c.build();
    }

    public static float f(ForgeConfigSpec.DoubleValue v) { return v.get().floatValue(); }

    private IcemanConfig() {}
}
