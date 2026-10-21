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
    public int eclipseWeight = 7;
    public int fogWeight = 7;
    public int thunderWeight = 7;
    public int netherRiftWeight = 7;
    public int frostWeight = 7;

    /** Clock time when the sun starts to go dark before an eclipse siege. */
    public int eclipseWarningDayTime = 5400;
    /** Clock time when the eclipse siege starts (noon). */
    public int eclipseStartDayTime = 6000;
    public double eclipseMobMultiplier = 1.2;
    /** Share of the eclipse attackers that are phantoms. */
    public double eclipsePhantomShare = 0.15;
    /** Share of a fog night's attackers swapped for spiders and creepers. */
    public double fogSneakerShare = 0.35;
    /** Seconds between lightning strikes on the attackers in a thunder night (at random up to twice as long). */
    public int thunderStrikeSeconds = 4;
    public double thunderStrikeDamage = 8.0;
    /** Tesla coils hit this much harder in a thunder night. */
    public double thunderTeslaMultiplier = 1.5;
    /** Share of a rift night's attackers that come from the Nether. */
    public double netherRiftShare = 0.5;
    public int netherRiftCount = 3;

    // --- bonus events -----------------------------------------------------
    /** Chance of an aurora in a quiet night (no siege due). */
    public double auroraChance = 0.125;
    /** Seconds between the free experience points under the aurora. */
    public int auroraPointSeconds = 60;
    /** Chance each morning that the trader caravan comes, once enough days have passed. */
    public double caravanChance = 0.35;
    public int caravanMinDays = 3;

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
    // --- sound and visuals -------------------------------------------------
    public boolean soundsEnabled = true;
    public boolean particlesEnabled = true;
    /** Colour the attackers' glow red on a blood moon night. */
    public boolean bloodMoonGlow = true;
    /** How many mobs of one batch make a spawn sound. The rest spawn quietly. */
    public int audibleSpawnsPerBatch = 3;

    /** Disconnect players whose client does not have the mod. */
    public boolean requireClientMod = true;

    public boolean skillsEnabled = true;
    /** Hand every player the written guide the first time they join. */
    public boolean giveGuideBook = true;
    /** Points every surviving defender gets for a cleared siege. */
    public int skillPointsPerSiege = 1;
    /** Experience for every attacker a player kills. */
    public int skillPointsPerKill = 1;
    /** A champion is worth this many times a normal kill. */
    public int championPointMultiplier = 10;
    /** One extra point for every this many siege levels. */
    public int skillPointsLevelStep = 5;

    // --- mobs -------------------------------------------------------------
    public List<MobEntry> mobs = defaultMobs();

    /** Dungeon cores hidden in structures of other mods. */
    public boolean dungeonsEnabled = true;
    /** Version of the dungeon list in this file. Older lists are replaced on load. */
    public int dungeonListVersion = 0;
    public static final int CURRENT_DUNGEON_LIST = 1;
    /** Which structures hold dungeons, how hard they are and what lives there. */
    public List<DungeonType> dungeons = DungeonType.defaults();
    /** Perks from core fragments work within this many blocks of the main core. */
    public double perkRadius = 64.0;

    /** Raised whenever the default mob list changes, so old config files get the new list. */
    public static final int CURRENT_MOB_LIST = 2;
    /** Version of the mob list in this file. Older lists are replaced on load. */
    public int mobListVersion = 0;

    /**
     * The default attackers. Easy mobs fade out as the level rises, hard ones
     * grow more common. Entries of mods that are not installed are skipped.
     * {@code a+b} means b riding a.
     */
    public static List<MobEntry> defaultMobs() {
        List<MobEntry> list = new ArrayList<>();
        String vv = "variantsandventures:";
        String tap = "takesapillage:";
        String faf = "friendsandfoes:";
        // Level 1: the classics, fading out later.
        list.add(new MobEntry("minecraft:zombie", 1, 20, -1.0));
        list.add(new MobEntry("minecraft:skeleton", 1, 14, -0.6));
        // Level 2-4: spiders, creepers and the regional variants.
        list.add(new MobEntry("minecraft:spider", 2, 10, -0.3));
        list.add(new MobEntry(vv + "thicket", 2, 6, 0.0));
        list.add(new MobEntry(vv + "verdant", 2, 6, 0.0));
        list.add(new MobEntry("minecraft:creeper", 3, 6, 0.2));
        list.add(new MobEntry("minecraft:husk", 3, 8, 0.0));
        list.add(new MobEntry(vv + "murk", 3, 6, 0.1));
        list.add(new MobEntry(vv + "gelid", 3, 6, 0.1));
        list.add(new MobEntry("minecraft:stray", 4, 6, 0.1));
        list.add(new MobEntry("minecraft:bogged", 4, 6, 0.1));
        list.add(new MobEntry(tap + "skirmisher", 4, 6, 0.3));
        // Level 5-7: the illagers arrive, first riders.
        list.add(new MobEntry("minecraft:pillager", 5, 8, 0.4));
        list.add(new MobEntry(tap + "archer", 5, 6, 0.4));
        list.add(new MobEntry("minecraft:parched", 5, 5, 0.2));
        list.add(new MobEntry("minecraft:camel_husk+minecraft:husk", 5, 3, 0.2));
        list.add(new MobEntry("minecraft:cave_spider", 6, 4, 0.0));
        list.add(new MobEntry("minecraft:spider+minecraft:skeleton", 6, 3, 0.2));
        list.add(new MobEntry("minecraft:vindicator", 6, 6, 0.5));
        list.add(new MobEntry(tap + "legioner", 6, 5, 0.5));
        list.add(new MobEntry("minecraft:witch", 7, 4, 0.2));
        list.add(new MobEntry("minecraft:breeze", 7, 3, 0.3));
        list.add(new MobEntry("minecraft:drowned", 7, 5, 0.0));
        // Level 8-10: casters and wither skeletons.
        list.add(new MobEntry(faf + "iceologer", 8, 3, 0.4));
        list.add(new MobEntry(faf + "illusioner", 8, 3, 0.4));
        list.add(new MobEntry("minecraft:wither_skeleton", 8, 4, 0.5));
        list.add(new MobEntry("minecraft:evoker", 10, 2, 0.4));
        // Level 11+: ravagers with riders and the wildfire.
        list.add(new MobEntry("minecraft:ravager+minecraft:pillager", 11, 1, 0.4));
        list.add(new MobEntry(faf + "wildfire", 13, 1, 0.3));
        list.add(new MobEntry("minecraft:ravager+minecraft:vindicator", 14, 1, 0.3));
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
        eclipseWeight = Math.max(0, eclipseWeight);
        fogWeight = Math.max(0, fogWeight);
        thunderWeight = Math.max(0, thunderWeight);
        netherRiftWeight = Math.max(0, netherRiftWeight);
        frostWeight = Math.max(0, frostWeight);
        eclipseWarningDayTime = clamp(eclipseWarningDayTime, 0, 11000);
        eclipseStartDayTime = clamp(eclipseStartDayTime, eclipseWarningDayTime + 100, 11500);
        eclipseMobMultiplier = clampDouble(eclipseMobMultiplier, 0.5, 4.0);
        eclipsePhantomShare = clampDouble(eclipsePhantomShare, 0.0, 1.0);
        fogSneakerShare = clampDouble(fogSneakerShare, 0.0, 1.0);
        thunderStrikeSeconds = clamp(thunderStrikeSeconds, 1, 120);
        thunderStrikeDamage = clampDouble(thunderStrikeDamage, 0.0, 100.0);
        thunderTeslaMultiplier = clampDouble(thunderTeslaMultiplier, 1.0, 5.0);
        netherRiftShare = clampDouble(netherRiftShare, 0.0, 1.0);
        netherRiftCount = clamp(netherRiftCount, 1, 8);
        auroraChance = clampDouble(auroraChance, 0.0, 1.0);
        auroraPointSeconds = clamp(auroraPointSeconds, 5, 3600);
        caravanChance = clampDouble(caravanChance, 0.0, 1.0);
        caravanMinDays = clamp(caravanMinDays, 1, 100);
        bloodMoonMobMultiplier = Math.max(1.0, bloodMoonMobMultiplier);
        bloodMoonHealthMultiplier = Math.max(1.0, bloodMoonHealthMultiplier);
        bloodMoonDamageMultiplier = Math.max(1.0, bloodMoonDamageMultiplier);
        eliteHealthMultiplier = Math.max(1.0, eliteHealthMultiplier);
        eliteDamageMultiplier = Math.max(1.0, eliteDamageMultiplier);
        eliteDeathDamagePenalty = clampDouble(eliteDeathDamagePenalty, 0.0, 0.9);

        skillPointsPerSiege = Math.max(0, skillPointsPerSiege);
        skillPointsPerKill = Math.max(0, skillPointsPerKill);
        championPointMultiplier = Math.max(1, championPointMultiplier);
        audibleSpawnsPerBatch = Math.max(0, audibleSpawnsPerBatch);
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
        if (dungeons == null || dungeonListVersion < CURRENT_DUNGEON_LIST) {
            dungeons = DungeonType.defaults();
            dungeonListVersion = CURRENT_DUNGEON_LIST;
        }
        dungeons.removeIf(d -> d == null || d.key == null || d.key.isBlank() || d.structure == null);
        perkRadius = clampDouble(perkRadius, 8.0, 512.0);
        if (mobListVersion < CURRENT_MOB_LIST) {
            mobs = defaultMobs();
            mobListVersion = CURRENT_MOB_LIST;
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
