package com.mki.nightsiege.core;

import java.util.LinkedHashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

/**
 * All tunable values of the mod. Written to and read from config/nightsiege.json
 * so the numbers can be changed without rebuilding the mod.
 */
public class SiegeConfig {

    // --- scheduling -------------------------------------------------------
    /** Old random gap (no longer used: sieges come every daysBetweenSieges days after a vote). */
    public int minDaysBetweenSieges = 2;
    public int maxDaysBetweenSieges = 5;
    /** A siege night is offered every this many in-game days. */
    public int daysBetweenSieges = 3;
    /** Day time of the invitation on a siege day (1000 = early morning). */
    public int inviteDayTime = 1000;
    /** Accepted invitations needed for the night to be played. */
    public int requiredAccepts = 3;
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
    public int maxLevel = 1000;
    /** Up to this level the nights grow at the full rate, above it only by growthAboveSoftCap. */
    public int softCapLevel = 20;
    /** Share of the usual growth per level above softCapLevel (0.25 = a quarter). */
    public double growthAboveSoftCap = 0.25;
    /** Every N in-game days the level rises by one. */
    /** Extra level every this many in-game days; 0 = the level only rises with won nights. */
    public int daysPerLevel = 0;

    public int baseDurationSeconds = 240;
    public int durationSecondsPerLevel = 20;
    public int maxDurationSeconds = 600;

    public int baseMobsPerBatch = 4;
    public double mobsPerLevel = 1.0;
    /** Each additional player multiplies the batch size by this much. */
    public double playerScaling = 0.6;
    public int maxLiveMobs = 40;
    public int batchIntervalSeconds = 38;

    public double healthPerLevel = 0.06;
    public double maxHealthMultiplier = 2.5;
    public double damagePerLevel = 0.04;
    public double maxDamageMultiplier = 2.0;

    public double spawnMinDistance = 20.0;
    public double spawnMaxDistance = 40.0;
    /** How far from the core the base scan looks for walls (set with /nightsiege base radius). */
    public int baseRadius = 64;

    // --- punishment -------------------------------------------------------
    /** A lost night blows up the area around the core (off: a defeat has no punishment). */
    public boolean failureBlast = false;
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
    public int auroraPointSeconds = 180;
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
    /** Colour the attackers' glow red on a blood moon night (off since their own look came in). */
    public boolean bloodMoonGlow = false;
    /** How many mobs of one batch make a spawn sound. The rest spawn quietly. */
    public int audibleSpawnsPerBatch = 3;

    /** Disconnect players whose client does not have the mod. */
    public boolean requireClientMod = true;

    public boolean skillsEnabled = true;
    /** Hand every player the written guide the first time they join. */
    public boolean giveGuideBook = true;
    /** Points every surviving defender gets for a cleared siege. */
    public int skillPointsPerSiege = 0;
    /** Experience for every attacker a player kills. */
    public int skillPointsPerKill = 0;
    /** Survived waves that make one experience point (sieges give experience only this way). */
    public int wavesPerPoint = 2;
    /** Factor on the points from survived waves (1.5 = half as much again); the test kits count with it too. */
    public double pointGainMultiplier = 1.5;
    /** Players who missed nights: waves count (missed + 1) times in their next night, at most this often. */
    public int catchUpMax = 4;
    /** Defenders who die in a siege keep their inventory. */
    public boolean keepInventoryInSiege = true;
    /** Learned levels each defender loses when a night is lost (the level stays, the night comes again). */
    public int defeatSkillLoss = 0;
    /** A champion is worth this many times a normal kill. */
    public int championPointMultiplier = 10;
    /** One extra point for every this many siege levels. */
    public int skillPointsLevelStep = 5;
    /** A guardian earns one kill's worth for every this much damage caught or healing given. */
    public int tankDamagePerPoint = 20;

    // --- mobs -------------------------------------------------------------
    public List<MobEntry> mobs = defaultMobs();
    /**
     * Siege monsters that put effects on defenders (hunger, poison, slowness,
     * freezing, wither, weakness, blindness) come as a plain monster of the night instead.
     */
    public boolean replaceEffectMobs = true;
    /** Which monster comes instead of which. Mounts ("a+b") are replaced part by part. */
    public Map<String, String> effectMobReplacements = defaultEffectMobReplacements();
    /** No natural monsters, phantoms or patrols around the core from the warning until the night is over. */
    public boolean blockNaturalSpawnsInSiege = true;
    /** How far around the core natural spawns are held back. */
    public double naturalSpawnBlockRadius = 160.0;
    /** From this level on the effect monsters slowly come back (below it they are always replaced). */
    public int effectMobsFromLevel = 10;
    /** Share of effect monsters that stay real, per level from effectMobsFromLevel on (0.1 = 10 %, 20 %, ...). */
    public double effectMobShareStep = 0.1;

    // ---------------- Version B: night kits and reward crates ----------------

    /** Defenders hand in their own inventory for the night and get the kit of their class. */
    public boolean nightKits = true;
    /** Rarity weights of a crate (common, uncommon, rare, epic, legendary) for a normal night. */
    public int[] crateOddsNormal = {60, 25, 10, 4, 1};
    /** The same for an event night. */
    public int[] crateOddsEvent = {45, 30, 16, 7, 2};
    /** The same for a fragment night. */
    public int[] crateOddsFragment = {30, 30, 22, 13, 5};
    /** Share of class gains against general gains in a crate. */
    public double crateClassShare = 0.7;
    /** Extra roll of every crate from this level on: an infernite ingot. */
    public int infernitCrateLevel = 20;
    /** Chance of that extra roll (0.05 = 5 %). */
    public double infernitCrateChance = 0.05;
    /** How far a fallen defender may watch from the core. */
    public double spectatorRadius = 80.0;
    /**
     * Melee life steal every warrior (but the berserker, who has his own) and the shield
     * fighter get from the start (share of the melee damage dealt). 0 switches it off.
     */
    public double meleeLifesteal = 0.08;
    /** The most damage any player's skills can take away together (0.70 = at most -70 %). */
    public double maxDamageReduction = 0.70;
    /** Items picked up in the night that are kept afterwards (besides armour and weapons of diamond or better). */
    public List<String> valuableDrops = new ArrayList<>(List.of(
            "minecraft:ender_pearl", "minecraft:totem_of_undying", "minecraft:emerald", "minecraft:diamond"));
    /** Armour and weapons of diamond or better that monsters drop are kept as well. */
    public boolean keepDiamondGear = true;

    /** Five non-negative weights with at least one above zero, else the defaults. */
    private static int[] odds(int[] given, int[] fallback) {
        if (given == null || given.length != 5) {
            return fallback;
        }
        int total = 0;
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) {
            out[i] = Math.max(0, Math.min(10000, given[i]));
            total += out[i];
        }
        return total <= 0 ? fallback : out;
    }

    /** The rarity weights of a crate kind. */
    public int[] crateOdds(CrateKind kind) {
        return switch (kind) {
            case EVENT -> crateOddsEvent;
            case FRAGMENT -> crateOddsFragment;
            default -> crateOddsNormal;
        };
    }

    public static Map<String, String> defaultEffectMobReplacements() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("minecraft:husk", "minecraft:zombie");
        map.put("minecraft:stray", "minecraft:skeleton");
        map.put("minecraft:bogged", "minecraft:skeleton");
        map.put("minecraft:parched", "minecraft:skeleton");
        map.put("minecraft:cave_spider", "minecraft:spider");
        map.put("minecraft:witch", "minecraft:pillager");
        map.put("minecraft:wither_skeleton", "minecraft:vindicator");
        map.put("variantsandventures:thicket", "minecraft:zombie");
        map.put("variantsandventures:gelid", "minecraft:zombie");
        map.put("variantsandventures:verdant", "minecraft:skeleton");
        map.put("friendsandfoes:iceologer", "minecraft:vindicator");
        map.put("friendsandfoes:illusioner", "minecraft:pillager");
        return map;
    }

    /** Share of effect monsters that stay real at this level: 0 below effectMobsFromLevel, then step by step up to 1. */
    public double effectMobShare(int level) {
        if (level < effectMobsFromLevel) {
            return 0.0;
        }
        return Math.min(1.0, (level - effectMobsFromLevel + 1) * Math.max(0.0, effectMobShareStep));
    }

    /** Like {@link #withoutEffects(String)}, but from effectMobsFromLevel on some effect monsters stay real. */
    public String withoutEffects(String id, int level, java.util.Random random) {
        double keep = effectMobShare(level);
        if (keep >= 1.0 || (keep > 0.0 && random != null && random.nextDouble() < keep)) {
            return id;
        }
        return withoutEffects(id);
    }

    /** The monster that really comes for a picked one, without effect monsters when they are switched off. */
    public String withoutEffects(String id) {
        if (!replaceEffectMobs || id == null || effectMobReplacements == null || effectMobReplacements.isEmpty()) {
            return id;
        }
        String[] parts = id.split("\\+");
        for (int i = 0; i < parts.length; i++) {
            String instead = effectMobReplacements.get(parts[i].trim());
            if (instead != null && !instead.isBlank()) {
                parts[i] = instead.trim();
            }
        }
        return String.join("+", parts);
    }

    /** Dungeon cores hidden in the structures of Dungeons and Taverns. */
    public boolean dungeonsEnabled = true;
    /** A special night (night fragment set into the core): more monsters, more experience. */
    public double specialNightMobMultiplier = 1.3;
    /** Share of the night's monsters the defenders must kill; a held night below it comes again. 0 turns it off. */
    public double killQuotaShare = 0.6;
    /** Balancing targets for /nightsiege balance: share of nights won, of held nights with the kill goal, deaths per night, allowed spread of subclass damage. */
    public double targetWinRate = 0.75;
    // ---------------- Balancing on the server (no new client needed) ----------------
    /** Short guide written into the config file. */
    public String tuningHelp = "classDamage/subclassDamage: Faktor auf den Schaden, den Spieler dieser Klasse/Unterklasse an Monstern machen. "
            + "classDamageTaken/subclassDamageTaken: Faktor auf den Schaden, den sie von Monstern bekommen. "
            + "skillTweaks: je Skill-ID amount (Wert je Stufe), cost (Grundkosten), costStep (Aufschlag je Stufe), maxLevel. "
            + "Aendern, speichern, dann /nightsiege reload - gilt sofort, ohne neue Mod-Version.";
    public Map<String, Double> classDamage = new LinkedHashMap<>();
    public Map<String, Double> classDamageTaken = new LinkedHashMap<>();
    public Map<String, Double> subclassDamage = new LinkedHashMap<>();
    public Map<String, Double> subclassDamageTaken = new LinkedHashMap<>();
    /** Per skill id: new values for its amount per level, base cost, cost step or highest level. */
    public Map<String, SkillTweak> skillTweaks = new LinkedHashMap<>();

    /** One skill changed on the server; empty fields keep the built-in value. */
    public static class SkillTweak {
        public Double amount;
        public Integer cost;
        public Integer costStep;
        public Integer maxLevel;
    }

    public static final String[] CLASS_KEYS = {"warrior", "archer", "guardian", "engineer"};
    public static final String[] SUBCLASS_KEYS = {"blademaster", "berserker", "axe", "paladin", "swift", "heavy", "bolter",
            "trapper", "shieldfighter", "bulwark", "medic", "warden", "gunner", "electrician", "demolition", "alchemist"};

    /** Factor on the damage a player of this class and subclass deals to monsters. */
    public double damageFactor(String clazz, String subclass) {
        return factor(classDamage, clazz) * factor(subclassDamage, subclass);
    }

    /** Factor on the damage a player of this class and subclass takes from monsters. */
    public double damageTakenFactor(String clazz, String subclass) {
        return factor(classDamageTaken, clazz) * factor(subclassDamageTaken, subclass);
    }

    private static double factor(Map<String, Double> map, String key) {
        if (map == null || key == null || key.isBlank()) {
            return 1.0;
        }
        Double v = map.get(key.toLowerCase(java.util.Locale.ROOT));
        return v == null ? 1.0 : v;
    }

    private static Map<String, Double> filled(Map<String, Double> map, String[] keys) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String key : keys) {
            Double v = map == null ? null : map.get(key);
            out.put(key, v == null || v.isNaN() ? 1.0 : Math.max(0.1, Math.min(5.0, v)));
        }
        return out;
    }

    /** /nightsiege test hands out kits and tops up skill points to what the level would have given (playtests). */
    public boolean testKits = true;
    /**
     * Seconds between /nightsiege test and the first wave: weapons and traps come
     * at once, so traps can be set and positions taken. 0 = the night starts at once.
     */
    public int testPrepSeconds = 60;
    public double targetGoalRate = 0.8;
    public double targetDeathsPerNight = 1.0;
    public double targetClassSpread = 0.25;
    public int specialNightXpMultiplier = 2;
    /** Old core perks (from the first dungeons); off, the fragments now make special nights. */
    public boolean perksEnabled = false;
    /**
     * Version of the general settings in this file. Version 1 switched the
     * dungeons off (they wait for better structures); set dungeonsEnabled back
     * to true to have them again.
     */
    public int settingsVersion = 0;
    /** Version of the dungeon list in this file. Older lists are replaced on load. */
    public int dungeonListVersion = 0;
    public static final int CURRENT_DUNGEON_LIST = 2;
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
        daysBetweenSieges = clamp(daysBetweenSieges, 1, 100);
        inviteDayTime = clamp(inviteDayTime, 0, 11000);
        requiredAccepts = clamp(requiredAccepts, 1, 100);
        softCapLevel = clamp(softCapLevel, 1, 1000);
        growthAboveSoftCap = clampDouble(growthAboveSoftCap, 0.0, 1.0);
        naturalSpawnBlockRadius = clampDouble(naturalSpawnBlockRadius, 16.0, 1024.0);
        effectMobsFromLevel = clamp(effectMobsFromLevel, 1, 1000);
        effectMobShareStep = clampDouble(effectMobShareStep, 0.0, 1.0);
        maxDaysBetweenSieges = Math.max(minDaysBetweenSieges, maxDaysBetweenSieges);
        warningDayTime = clamp(warningDayTime, 0, 23999);
        startDayTime = clamp(startDayTime, 0, 23999);
        requiredPlayers = Math.max(1, requiredPlayers);
        coreRadius = Math.max(16.0, coreRadius);
        participantRadius = Math.max(16.0, participantRadius);

        maxLevel = clamp(maxLevel, 1, 1000);
        daysPerLevel = Math.max(0, daysPerLevel);

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
        baseRadius = clamp(baseRadius <= 0 ? 64 : baseRadius, 16, 192);

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
        testPrepSeconds = clamp(testPrepSeconds, 0, 600);
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
        tankDamagePerPoint = Math.max(1, tankDamagePerPoint);
        specialNightMobMultiplier = clampDouble(specialNightMobMultiplier, 1.0, 5.0);
        killQuotaShare = clampDouble(killQuotaShare, 0.0, 1.0);
        classDamage = filled(classDamage, CLASS_KEYS);
        classDamageTaken = filled(classDamageTaken, CLASS_KEYS);
        subclassDamage = filled(subclassDamage, SUBCLASS_KEYS);
        subclassDamageTaken = filled(subclassDamageTaken, SUBCLASS_KEYS);
        if (skillTweaks == null) {
            skillTweaks = new LinkedHashMap<>();
        }
        targetWinRate = clampDouble(targetWinRate <= 0 ? 0.75 : targetWinRate, 0.05, 1.0);
        targetGoalRate = clampDouble(targetGoalRate <= 0 ? 0.8 : targetGoalRate, 0.05, 1.0);
        targetDeathsPerNight = clampDouble(targetDeathsPerNight <= 0 ? 1.0 : targetDeathsPerNight, 0.1, 20.0);
        targetClassSpread = clampDouble(targetClassSpread <= 0 ? 0.25 : targetClassSpread, 0.05, 2.0);
        specialNightXpMultiplier = clamp(specialNightXpMultiplier, 1, 10);
        if (settingsVersion < 1) {
            dungeonsEnabled = false;
            settingsVersion = 1;
        }
        if (settingsVersion < 2) {
            bloodMoonGlow = false;   // the blood moon mobs have their own look now
            settingsVersion = 2;
        }
        if (settingsVersion < 3) {
            dungeonsEnabled = true;   // back on, in the structures of Dungeons and Taverns
            settingsVersion = 3;
        }
        if (settingsVersion < 4) {
            // The balancing of October 2026: experience only from survived waves, a flatter climb.
            SiegeConfig fresh = new SiegeConfig();
            daysPerLevel = fresh.daysPerLevel;
            baseMobsPerBatch = fresh.baseMobsPerBatch;
            mobsPerLevel = fresh.mobsPerLevel;
            playerScaling = fresh.playerScaling;
            healthPerLevel = fresh.healthPerLevel;
            damagePerLevel = fresh.damagePerLevel;
            auroraPointSeconds = fresh.auroraPointSeconds;
            skillPointsPerSiege = fresh.skillPointsPerSiege;
            skillPointsPerKill = fresh.skillPointsPerKill;
            settingsVersion = 4;
        }
        if (settingsVersion < 5) {
            // Version A (October 2026): no cap at level 20, a defeat has no punishment.
            maxLevel = 1000;
            failureBlast = false;
            defeatSkillLoss = 0;
            settingsVersion = 5;
        }
        if (settingsVersion < 6) {
            // Version B2 (24 October 2026): the shield fighter was far too weak (Simon's test).
            if (subclassDamage.getOrDefault("shieldfighter", 1.0) == 1.0) {
                subclassDamage.put("shieldfighter", 1.25);
            }
            if (subclassDamageTaken.getOrDefault("shieldfighter", 1.0) == 1.0) {
                subclassDamageTaken.put("shieldfighter", 0.8);
            }
            settingsVersion = 6;
        }
        if (settingsVersion < 7) {
            // 24 October 2026, after the melee simulation: warriors take 15 % less from monsters.
            if (classDamageTaken.getOrDefault("warrior", 1.0) == 1.0) {
                classDamageTaken.put("warrior", 0.85);
            }
            settingsVersion = 7;
        }
        if (Double.isNaN(meleeLifesteal)) {
            meleeLifesteal = 0.08;
        }
        meleeLifesteal = Math.min(1.0, Math.max(0.0, meleeLifesteal));
        if (Double.isNaN(maxDamageReduction) || maxDamageReduction <= 0) {
            maxDamageReduction = 0.70;
        }
        maxDamageReduction = Math.min(0.95, Math.max(0.1, maxDamageReduction));
        crateOddsNormal = odds(crateOddsNormal, new int[]{60, 25, 10, 4, 1});
        crateOddsEvent = odds(crateOddsEvent, new int[]{45, 30, 16, 7, 2});
        crateOddsFragment = odds(crateOddsFragment, new int[]{30, 30, 22, 13, 5});
        if (Double.isNaN(crateClassShare)) {
            crateClassShare = 0.7;
        }
        crateClassShare = Math.min(1.0, Math.max(0.0, crateClassShare));
        infernitCrateLevel = clamp(infernitCrateLevel, 1, 1000);
        if (Double.isNaN(infernitCrateChance)) {
            infernitCrateChance = 0.05;
        }
        infernitCrateChance = Math.min(1.0, Math.max(0.0, infernitCrateChance));
        if (Double.isNaN(spectatorRadius) || spectatorRadius < 16) {
            spectatorRadius = 80.0;
        }
        if (valuableDrops == null) {
            valuableDrops = new ArrayList<>(List.of(
                    "minecraft:ender_pearl", "minecraft:totem_of_undying", "minecraft:emerald", "minecraft:diamond"));
        }
        wavesPerPoint = clamp(wavesPerPoint, 1, 20);
        if (Double.isNaN(pointGainMultiplier) || pointGainMultiplier <= 0) {
            pointGainMultiplier = 1.0;
        }
        pointGainMultiplier = Math.min(10.0, Math.max(0.1, pointGainMultiplier));
        if (effectMobReplacements == null) {
            effectMobReplacements = defaultEffectMobReplacements();
        }
        catchUpMax = clamp(catchUpMax, 1, 10);
        defeatSkillLoss = clamp(defeatSkillLoss, 0, 20);

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
