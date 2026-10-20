package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * All tunable values of the mod. Written to and read from config/nightsiege.json
 * so the numbers can be changed without rebuilding the mod.
 */
public class SiegeConfig {

    // --- scheduling -------------------------------------------------------
    /** Shortest gap between two sieges, in in-game days of qualifying time. */
    public int minDaysBetweenSieges = 2;
    /** Longest gap. The actual gap is rolled anew after every siege. */
    public int maxDaysBetweenSieges = 5;
    /** Day time at which the warning is sent (12000 = sunset). */
    public int warningDayTime = 12000;
    /** Day time at which the siege starts (15000 = full night). */
    public int startDayTime = 15000;

    // --- core and participants -------------------------------------------
    /** Players needed around the core before the countdown moves at all. */
    public int requiredPlayers = 3;
    /** Radius around the core in which those players are counted. */
    public double coreRadius = 300.0;
    /** Radius that decides who defends once a siege has begun. */
    public double participantRadius = 120.0;
    /** Give the first player who joins a world one core for free. */
    public boolean giveStarterCore = true;

    // --- difficulty -------------------------------------------------------
    public int maxLevel = 20;
    /** Every N in-game days the level rises by one. */
    public int daysPerLevel = 8;

    public int baseDurationSeconds = 240;
    public int durationSecondsPerLevel = 20;
    public int maxDurationSeconds = 600;

    public int baseMobsPerBatch = 6;
    public double mobsPerLevel = 2.0;
    /** Each additional player multiplies the batch size by this much. */
    public double playerScaling = 0.5;
    public int maxLiveMobs = 40;
    public int batchIntervalSeconds = 38;

    public double healthPerLevel = 0.08;
    public double maxHealthMultiplier = 2.5;
    public double damagePerLevel = 0.06;
    public double maxDamageMultiplier = 2.0;

    public double spawnMinDistance = 20.0;
    public double spawnMaxDistance = 40.0;

    // --- punishment -------------------------------------------------------
    /** Radius of the destruction when a siege is lost. */
    public int failureBlastRadius = 12;
    /** Keep chests and other containers intact in the blast, so no items are lost. */
    public boolean failureBlastProtectsContainers = true;

    // --- events -----------------------------------------------------------
    public boolean eventsEnabled = true;
    /** Chance that a siege night carries a special event. */
    public double eventChance = 0.45;
    /** No events below this siege level, so the first nights stay plain. */
    public int eventMinLevel = 3;
    public int bloodMoonWeight = 10;
    public int eliteWeight = 8;
    public int themeWaveWeight = 7;

    public double bloodMoonMobMultiplier = 1.5;
    public double bloodMoonHealthMultiplier = 1.3;
    public double bloodMoonDamageMultiplier = 1.15;

    public double eliteHealthMultiplier = 6.0;
    public double eliteDamageMultiplier = 1.8;
    /** Damage the rest of the wave loses once the champion is dead. */
    public double eliteDeathDamagePenalty = 0.25;

    // --- tools ------------------------------------------------------------
    /** Only mobs carrying a fitting tool can break blocks. */
    public boolean toolGatedDigging = true;
    /** Share of siege mobs that spawn with a tool. */
    public double toolEquipChance = 0.35;
    public int toolPickaxeWeight = 10;
    public int toolAxeWeight = 8;
    public int toolShovelWeight = 0;
    public String pickaxeItem = "minecraft:iron_pickaxe";
    public String axeItem = "minecraft:iron_axe";
    public String shovelItem = "minecraft:iron_shovel";
    /** Off by default: only stone and wood can be broken through. */
    public boolean allowShovelDigging = false;

    // --- digging ----------------------------------------------------------
    public boolean diggingEnabled = true;
    /** Ticks a siege mob needs to chew through one ordinary block. */
    public int digTicksPerBlock = 40;
    /** Block ids never broken by siege mobs. */
    public List<String> protectedBlocks = new ArrayList<>(List.of("minecraft:bedrock"));
    /**
     * Any block whose id contains one of these words is never broken. This is how
     * containers are protected without listing every single one, and it also covers
     * container blocks added by other mods.
     */
    public List<String> protectedBlockKeywords = new ArrayList<>(List.of(
            "chest", "shulker", "barrel", "furnace", "hopper", "dispenser", "dropper",
            "brewing", "crafter", "bookshelf", "lectern", "beehive", "bee_nest",
            "decorated_pot", "campfire", "smoker", "spawner", "vault"));

    // --- skills -----------------------------------------------------------
    public boolean skillsEnabled = true;
    /** Hand every player the written guide the first time they join. */
    public boolean giveGuideBook = true;
    /** Points every surviving defender gets for a cleared siege. */
    public int skillPointsPerSiege = 1;
    /** One extra point for every this many siege levels. */
    public int skillPointsLevelStep = 5;

    // --- mobs -------------------------------------------------------------
    public List<MobEntry> mobs = defaultMobs();

    public static List<MobEntry> defaultMobs() {
        List<MobEntry> list = new ArrayList<>();
        // Level 1-2
        list.add(new MobEntry("minecraft:zombie", 1, 20));
        list.add(new MobEntry("minecraft:skeleton", 1, 14));
        // Level 3-4
        list.add(new MobEntry("minecraft:spider", 3, 10));
        list.add(new MobEntry("minecraft:creeper", 3, 6));
        // Level 5-7
        list.add(new MobEntry("minecraft:husk", 5, 10));
        list.add(new MobEntry("minecraft:stray", 5, 8));
        list.add(new MobEntry("minecraft:cave_spider", 6, 6));
        // Level 8-10
        list.add(new MobEntry("minecraft:pillager", 8, 10));
        list.add(new MobEntry("minecraft:vindicator", 8, 8));
        list.add(new MobEntry("minecraft:witch", 9, 5));
        // Level 11-14
        list.add(new MobEntry("minecraft:drowned", 11, 8));
        list.add(new MobEntry("minecraft:wither_skeleton", 12, 6));
        // Level 15+
        list.add(new MobEntry("minecraft:evoker", 15, 3));
        list.add(new MobEntry("minecraft:ravager", 16, 2));
        return list;
    }

    /** Clamps nonsense values so a hand-edited config can never break the mod. */
    public void sanitize() {
        minDaysBetweenSieges = Math.max(1, minDaysBetweenSieges);
        maxDaysBetweenSieges = Math.max(minDaysBetweenSieges, maxDaysBetweenSieges);
        warningDayTime = clamp(warningDayTime, 0, 23999);
        startDayTime = clamp(startDayTime, 0, 23999);
        requiredPlayers = Math.max(1, requiredPlayers);
        coreRadius = Math.max(16.0, coreRadius);
        participantRadius = Math.max(16.0, participantRadius);

        maxLevel = clamp(maxLevel, 1, 1000);
        daysPerLevel = Math.max(1, daysPerLevel);

        baseDurationSeconds = Math.max(30, baseDurationSeconds);
        durationSecondsPerLevel = Math.max(0, durationSecondsPerLevel);
        maxDurationSeconds = Math.max(baseDurationSeconds, maxDurationSeconds);

        baseMobsPerBatch = Math.max(1, baseMobsPerBatch);
        mobsPerLevel = Math.max(0.0, mobsPerLevel);
        playerScaling = Math.max(0.0, playerScaling);
        maxLiveMobs = clamp(maxLiveMobs, 1, 500);
        batchIntervalSeconds = Math.max(5, batchIntervalSeconds);

        healthPerLevel = Math.max(0.0, healthPerLevel);
        maxHealthMultiplier = Math.max(1.0, maxHealthMultiplier);
        damagePerLevel = Math.max(0.0, damagePerLevel);
        maxDamageMultiplier = Math.max(1.0, maxDamageMultiplier);

        spawnMinDistance = Math.max(8.0, spawnMinDistance);
        spawnMaxDistance = Math.max(spawnMinDistance + 4.0, spawnMaxDistance);

        failureBlastRadius = clamp(failureBlastRadius, 0, 64);

        digTicksPerBlock = clamp(digTicksPerBlock, 1, 2000);

        eventChance = clampDouble(eventChance, 0.0, 1.0);
        eventMinLevel = Math.max(1, eventMinLevel);
        bloodMoonWeight = Math.max(0, bloodMoonWeight);
        eliteWeight = Math.max(0, eliteWeight);
        themeWaveWeight = Math.max(0, themeWaveWeight);
        bloodMoonMobMultiplier = Math.max(1.0, bloodMoonMobMultiplier);
        bloodMoonHealthMultiplier = Math.max(1.0, bloodMoonHealthMultiplier);
        bloodMoonDamageMultiplier = Math.max(1.0, bloodMoonDamageMultiplier);
        eliteHealthMultiplier = Math.max(1.0, eliteHealthMultiplier);
        eliteDamageMultiplier = Math.max(1.0, eliteDamageMultiplier);
        eliteDeathDamagePenalty = clampDouble(eliteDeathDamagePenalty, 0.0, 0.9);

        skillPointsPerSiege = Math.max(0, skillPointsPerSiege);
        skillPointsLevelStep = Math.max(1, skillPointsLevelStep);

        toolEquipChance = clampDouble(toolEquipChance, 0.0, 1.0);
        if (!allowShovelDigging) {
            toolShovelWeight = 0;
        }
        toolPickaxeWeight = Math.max(0, toolPickaxeWeight);
        toolAxeWeight = Math.max(0, toolAxeWeight);
        toolShovelWeight = Math.max(0, toolShovelWeight);
        pickaxeItem = blankTo(pickaxeItem, "minecraft:iron_pickaxe");
        axeItem = blankTo(axeItem, "minecraft:iron_axe");
        shovelItem = blankTo(shovelItem, "minecraft:iron_shovel");
        if (protectedBlocks == null) {
            protectedBlocks = new ArrayList<>(List.of("minecraft:bedrock"));
        }
        if (!protectedBlocks.contains("minecraft:bedrock")) {
            protectedBlocks.add("minecraft:bedrock");
        }
        if (protectedBlockKeywords == null) {
            protectedBlockKeywords = new ArrayList<>();
        }
        if (mobs == null || mobs.isEmpty()) {
            mobs = defaultMobs();
        }
        mobs.removeIf(entry -> entry == null || !entry.isValid());
        if (mobs.isEmpty()) {
            mobs = defaultMobs();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
