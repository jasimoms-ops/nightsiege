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
    /**
     * From this world level on every registered player may call an extra siege for tonight with
     * the button at the core (invitation and vote as usual, the regular rhythm stays).
     */
    public int extraSiegeFromLevel = 20;
    /**
     * Extra sieges that may be called per in-game day; 0 = the button is switched off. More than
     * one is not possible: the vote of an extra siege runs until sunset like the regular one.
     */
    public int extraSiegePerDay = 1;
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

    /** The champion of a dungeon: this much tougher and harder hitting than its kind. */
    public double eliteHealthMultiplier = 6.0;
    public double eliteDamageMultiplier = 1.8;

    // --- boss nights (Simon, 25 October 2026; the champion night is gone) ---
    public boolean bossesEnabled = true;
    /** Every this many sieges is a boss night (a lost one comes again until the boss falls). */
    public int bossEvery = 6;
    /** Seconds before the end of the night at which the boss comes, with a last big wave (at most half the night). */
    public int bossFinaleSeconds = 180;
    /** Size of the last wave that comes with the boss, as a share of a normal wave. */
    public double bossFinaleWave = 1.5;
    /** Factor on the health of every boss. */
    public double bossHealthMultiplier = 1.0;
    /** Extra boss health for every defender beyond the first (0.5 = +50 %). */
    public double bossHealthPerExtraPlayer = 0.5;
    /** Factor on the damage of every boss (blows and abilities). */
    public double bossDamageMultiplier = 1.0;

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
    /** The killing blow on a boss is worth this many times a normal kill. */
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
    /** Chance of a second crate for each winner of a night (Simon, 27 October 2026: "Loot-Drop zu selten"). */
    public double bonusCrateChance = 0.35;
    /** Extra roll of every crate from this level on: an infernite ingot. */
    public int infernitCrateLevel = 20;
    /** Chance of that extra roll (0.05 = 5 %). */
    public double infernitCrateChance = 0.05;
    /** How far a fallen defender may watch from the core. */
    public double spectatorRadius = 80.0;
    /**
     * Melee life steal every warrior (but the berserker, who has his own) and the shield
     * fighter get from the start: share of a slain monster's maximum life healed on each kill. 0 switches it off.
     */
    public double meleeLifesteal = 0.08;
    /**
     * The blademaster has no life steal on his hits; a kill in the middle of a combo heals him by
     * this share of the monster's maximum life instead.
     * @deprecated no longer read (balance update, 26 October 2026): use {@code skillTweaks.w_combo.values.killheal}.
     */
    @Deprecated
    public double comboKillLifesteal = 0.15;
    /**
     * The berserker's life steal on every hit while he is below half his life (Simon, 25 October 2026).
     * @deprecated no longer read (balance update, 26 October 2026): use {@code skillTweaks.bloodlust.values.steal}.
     */
    @Deprecated
    public double berserkerLifesteal = 0.12;
    /** Zombies (husks, drowned …) and spiders hit warriors and guardians this many times harder (Simon, 25 October 2026). */
    public double zombieSpiderVsMelee = 2.0;
    /** Rüstungsbruch (Simon, 26 October 2026): a zombie hit or creeper blast takes this share of a player's armour … */
    public double armorBreakShare = 0.3;
    /** … for this many seconds; another hit starts the time again (no stacking). 0 turns it off. */
    public double armorBreakSeconds = 3.0;
    /** Vexes and phantoms go first for defenders who stand this many blocks above the ground around them … */
    public int skyHunterHeight = 3;
    /** … within this many blocks (0 turns it off). */
    public double skyHunterRange = 24.0;
    /** Phantoms in every siege night (Simon, 27 October 2026): this many with the second wave … */
    public int nightPhantoms = 2;
    /** … and this many more per siege level (rounded down); 0 and 0 turn it off. */
    public double nightPhantomsPerLevel = 0.25;

    // ------------------------------------------------------------------
    // war camp and siege machines (concept 27 October 2026)
    // ------------------------------------------------------------------

    /** Chance that a war camp stands from the day before a siege (0 = never). */
    public double campChance = 0.2;
    /** Blocks between the core and the camp. */
    public int campMinDistance = 80;
    public int campMaxDistance = 150;
    /** Guards (vindicators): this many, plus some for every further signed-up player. */
    public int campGuards = 8;
    public int campGuardsPerPlayer = 3;
    /** Life of the workshop's siege workbench. */
    public double campWorkshopHealth = 300;
    /** Seconds after entering the palisade before the guards attack. */
    public int campGraceSeconds = 10;
    /** Siege machines in nights after a camp. */
    public boolean machinesEnabled = true;
    /** Seconds into the night before the machines are set up. */
    public int machineStartSeconds = 40;
    /** Factors on the machines' life and on what their shots do. */
    public double machineHealthMultiplier = 1.0;
    public double machineDamageMultiplier = 1.0;
    /** Pillagers at every machine: two work it, the guards stand around it (Simon, 27 October 2026). */
    public int machineCrew = 2;
    public int machineGuards = 3;
    /** Seconds until a machine whose crew fell gets a new one. */
    public int machineCrewSeconds = 45;
    /** Siege monsters drop items and experience (false since Simon's call of 26 October 2026: they drop nothing). */
    public boolean siegeMobDrops = false;
    /** Klaffende Wunden (anti-heal, Simon 25 October 2026): these monsters halve all healing of the player they hit. */
    public java.util.List<String> antiHealMobs = new java.util.ArrayList<>(java.util.List.of(
            "minecraft:spider", "minecraft:cave_spider", "minecraft:witch"));
    /** Share of healing the wounds take away, for how many seconds after a hit, from which level on. */
    public double antiHealShare = 0.5;
    public double antiHealSeconds = 4.0;
    public int antiHealFromLevel = 5;
    /** Shield energy (Simon 26.10.): seconds of blocking at a stretch, and seconds charged per second when not blocking. */
    public double shieldEnergySeconds = 6.0;
    public double shieldRechargePerSecond = 1.0;
    /** The most damage any player's skills can take away together (0.70 = at most -70 %). */
    public double maxDamageReduction = 0.70;
    /**
     * Playtest: every player gets the test buttons of the core window (+50 points, reset the
     * class, reset the crate gains, take a crate) without creative mode. Off on normal servers.
     */
    public boolean testToolsForEveryone = false;
    /** The summoner can be chosen by everyone (Simon, 25 October 2026: open for all); false = admins only. */
    public boolean summonerForEveryone = true;
    /**
     * Traps and towers grow with the level like the monsters' health (Simon, 25 October 2026):
     * on level 1 they hit with their skill values, on higher levels as much harder as the
     * monsters are tougher. false = fixed values (only mortar and potion tower grew before).
     */
    public boolean trapDamageFollowsLevel = true;
    /** Factor on all trap and tower damage (server balancing of the engineer). */
    public double trapDamage = 0.75;
    /** Factor on all damage the summoner's companions deal to monsters (Simon, 25 October 2026: +50 %). */
    public double companionDamage = 1.5;
    /**
     * Monsters turn on a summoner's companion only within this many blocks of it (when it
     * hits them, or when they go for its summoner right next to it). 0 = never.
     * Simon 26.10.: 3 (was 6, the companions drew all the anger).
     */
    public double companionTauntRadius = 3.0;
    /** ...and only for this many seconds; then they go back to whom they had before. */
    public double companionTauntSeconds = 3.0;
    /** Version of the companion settings in this file (1: the shared mind of 26 October 2026). */
    public int companionSettingsVersion = 0;
    /** Companions grow with the night like the monsters: this share of the monsters' extra health and damage. */
    public double companionScaling = 0.8;
    /** Share of every wave (taken from its zombies) that carry ladders and build one up the wall. 0 = off. */
    public double ladderCarrierShare = 0.07;
    /** From this level sapper creepers come with every wave and blow craters into the wall. 0 = off. */
    public int sapperFromLevel = 3;
    /**
     * Sappers per wave: 1 plus this many per level (random rounding), at most sapperMaxPerWave
     * (Simon, 27 October 2026: more sappers, the base is rebuilt after every night).
     */
    public double sappersPerLevel = 0.15;
    public int sapperMaxPerWave = 4;
    /** Holes per side of the base (north, east, south, west) and night. */
    public int sapperHolesPerSide = 3;
    /** Radius of a sapper's crater in blocks (a charged creeper one more). */
    public double sapperBlastRadius = 3.0;
    /** Factor on the damage monsters deal to players (1.2 = +20 %, Simon 24 October 2026). */
    public double monsterDamage = 1.2;
    /** Per monster type (entity id), instead of monsterDamage: pillagers stay as they were. */
    public Map<String, Double> monsterDamageByType = new LinkedHashMap<>(Map.of("minecraft:pillager", 1.0));
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
        map.put("minecraft:wither_skeleton", "minecraft:skeleton");
        map.put("variantsandventures:thicket", "minecraft:zombie");
        map.put("variantsandventures:gelid", "minecraft:zombie");
        map.put("variantsandventures:verdant", "minecraft:skeleton");
        map.put("friendsandfoes:iceologer", "minecraft:pillager");
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
            + "subclassHealing: Faktor auf Heilung und Barrieren, die Spieler dieser Unterklasse geben. "
            + "skillTweaks: je Skill-ID amount (Wert je Stufe), cost (Grundkosten), costStep (Aufschlag je Stufe), maxLevel. "
            + "Aendern, speichern, dann /nightsiege reload - gilt sofort, ohne neue Mod-Version.";
    public Map<String, Double> classDamage = new LinkedHashMap<>();
    public Map<String, Double> classDamageTaken = new LinkedHashMap<>();
    public Map<String, Double> subclassDamage = new LinkedHashMap<>();
    public Map<String, Double> subclassDamageTaken = new LinkedHashMap<>();
    /** Factor on the healing and barriers players of this subclass give (Simon 27.10.: the medic 3.8/s). */
    public Map<String, Double> subclassHealing = new LinkedHashMap<>();
    /** Per skill id: new values for its amount per level, base cost, cost step or highest level. */
    public Map<String, SkillTweak> skillTweaks = new LinkedHashMap<>();

    /** One skill changed on the server; empty fields keep the built-in value. */
    public static class SkillTweak {
        public Double amount;
        public Integer cost;
        public Integer costStep;
        public Integer maxLevel;
        /** Named numbers of the skill ({@link SkillValues}): durations, radii, fixed factors. */
        public Map<String, Double> values;
    }

    public static final String[] CLASS_KEYS = {"warrior", "archer", "guardian", "engineer", "summoner"};
    public static final String[] SUBCLASS_KEYS = {"blademaster", "berserker", "axe", "paladin", "swift", "heavy", "bolter",
            "trapper", "shieldfighter", "bulwark", "medic", "warden", "gunner", "electrician", "demolition", "alchemist",
            "tamer", "golemsmith", "necromancer", "soulcatcher"};

    /** Factor on the damage a player of this class and subclass deals to monsters. */
    public double damageFactor(String clazz, String subclass) {
        return factor(classDamage, clazz) * factor(subclassDamage, subclass);
    }

    /** Factor on the healing and barriers a player of this subclass gives. */
    public double healingFactor(String subclass) {
        return factor(subclassHealing, subclass);
    }

    /** Factor on the damage a player of this class and subclass takes from monsters. */
    public double damageTakenFactor(String clazz, String subclass) {
        return factor(classDamageTaken, clazz) * factor(subclassDamageTaken, subclass);
    }

    /** Factor on the damage a monster of this type (e.g. "minecraft:zombie") deals to players. */
    public double monsterDamageFactor(String entityId) {
        Double v = null;
        if (monsterDamageByType != null && entityId != null) {
            v = monsterDamageByType.get(entityId);
            if (v == null && entityId.startsWith("minecraft:")) {
                v = monsterDamageByType.get(entityId.substring("minecraft:".length()));
            }
        }
        return v != null ? v : monsterDamage;
    }

    private static double factor(Map<String, Double> map, String key) {
        if (map == null || key == null || key.isBlank()) {
            return 1.0;
        }
        Double v = map.get(key.toLowerCase(java.util.Locale.ROOT));
        return v == null ? 1.0 : v;
    }

    private static void scale(Map<String, Double> map, String key, double by) {
        double now = map.getOrDefault(key, 1.0);
        map.put(key, Math.max(0.1, Math.min(5.0, Math.round(now * by * 1000.0) / 1000.0)));
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
        // Flyers in every night (Simon, 26 October 2026): vexes without an evoker, phantoms; both go for the walls.
        list.add(new MobEntry("minecraft:vex", 3, 2, 0.15));
        list.add(new MobEntry("minecraft:phantom", 3, 2, 0.15));
        list.add(new MobEntry("minecraft:husk", 3, 8, 0.0));
        list.add(new MobEntry(vv + "murk", 3, 6, 0.1));
        list.add(new MobEntry(vv + "gelid", 3, 6, 0.1));
        list.add(new MobEntry("minecraft:stray", 4, 6, 0.1));
        list.add(new MobEntry("minecraft:bogged", 4, 6, 0.1));
        list.add(new MobEntry(tap + "skirmisher", 4, 1, 0.05));   // axe illager: strong, so only a few
        // Level 5-7: the illagers arrive, first riders.
        list.add(new MobEntry("minecraft:pillager", 5, 8, 0.4));
        list.add(new MobEntry(tap + "archer", 5, 6, 0.4));
        list.add(new MobEntry("minecraft:parched", 5, 5, 0.2));
        list.add(new MobEntry("minecraft:camel_husk+minecraft:husk", 5, 3, 0.2));
        list.add(new MobEntry("minecraft:cave_spider", 6, 4, 0.0));
        list.add(new MobEntry("minecraft:spider+minecraft:skeleton", 6, 3, 0.2));
        list.add(new MobEntry("minecraft:vindicator", 6, 1, 0.05));   // axe illager: strong, so only a few
        list.add(new MobEntry(tap + "legioner", 6, 5, 0.5));
        list.add(new MobEntry("minecraft:witch", 7, 4, 0.2));
        list.add(new MobEntry("minecraft:breeze", 7, 3, 0.3));
        list.add(new MobEntry("minecraft:drowned", 7, 5, 0.0));
        // Level 8-10: casters and wither skeletons.
        list.add(new MobEntry(faf + "iceologer", 8, 1, 0.1));   // rare (Simon, 27 October 2026)
        list.add(new MobEntry(faf + "illusioner", 8, 3, 0.4));
        list.add(new MobEntry("minecraft:wither_skeleton", 8, 4, 0.5));
        list.add(new MobEntry("minecraft:evoker", 10, 2, 0.4));
        // Level 11+: ravagers with riders and the wildfire.
        list.add(new MobEntry("minecraft:ravager+minecraft:pillager", 11, 1, 0.4));
        list.add(new MobEntry(faf + "wildfire", 13, 1, 0.3));
        list.add(new MobEntry("minecraft:ravager+minecraft:vindicator", 14, 1, 0.1));
        return list;
    }

    /** Clamps nonsense values so a hand-edited config can never break the mod. */
    public void sanitize() {
        minDaysBetweenSieges = Math.max(1, minDaysBetweenSieges);
        daysBetweenSieges = clamp(daysBetweenSieges, 1, 100);
        inviteDayTime = clamp(inviteDayTime, 0, 11000);
        requiredAccepts = clamp(requiredAccepts, 1, 100);
        extraSiegeFromLevel = clamp(extraSiegeFromLevel, 1, 1000);
        extraSiegePerDay = clamp(extraSiegePerDay, 0, 1);
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
        campChance = clampDouble(campChance, 0.0, 1.0);
        campMinDistance = clamp(campMinDistance, 40, 400);
        campMaxDistance = clamp(campMaxDistance, campMinDistance, 500);
        campGuards = clamp(campGuards, 0, 40);
        campGuardsPerPlayer = clamp(campGuardsPerPlayer, 0, 10);
        campWorkshopHealth = clampDouble(campWorkshopHealth, 10.0, 5000.0);
        campGraceSeconds = clamp(campGraceSeconds, 0, 60);
        machineStartSeconds = clamp(machineStartSeconds, 0, 600);
        machineHealthMultiplier = clampDouble(machineHealthMultiplier, 0.1, 20.0);
        machineDamageMultiplier = clampDouble(machineDamageMultiplier, 0.0, 20.0);
        machineCrew = clamp(machineCrew, 1, 6);
        machineGuards = clamp(machineGuards, 0, 10);
        machineCrewSeconds = clamp(machineCrewSeconds, 5, 600);
        bloodMoonMobMultiplier = Math.max(1.0, bloodMoonMobMultiplier);
        bloodMoonHealthMultiplier = Math.max(1.0, bloodMoonHealthMultiplier);
        bloodMoonDamageMultiplier = Math.max(1.0, bloodMoonDamageMultiplier);
        eliteHealthMultiplier = Math.max(1.0, eliteHealthMultiplier);
        eliteDamageMultiplier = Math.max(1.0, eliteDamageMultiplier);
        bossEvery = clamp(bossEvery <= 0 ? 6 : bossEvery, 1, 100);
        bossFinaleSeconds = clamp(bossFinaleSeconds <= 0 ? 180 : bossFinaleSeconds, 30, 1200);
        bossFinaleWave = clampDouble(bossFinaleWave, 0.0, 5.0);
        bossHealthMultiplier = clampDouble(bossHealthMultiplier <= 0 ? 1.0 : bossHealthMultiplier, 0.1, 20.0);
        bossHealthPerExtraPlayer = clampDouble(bossHealthPerExtraPlayer, 0.0, 3.0);
        bossDamageMultiplier = clampDouble(bossDamageMultiplier <= 0 ? 1.0 : bossDamageMultiplier, 0.1, 10.0);

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
        subclassHealing = filled(subclassHealing, SUBCLASS_KEYS);
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
        if (settingsVersion < 8) {
            // 25 October 2026: the summoner is finished and open for everyone (Simon).
            summonerForEveryone = true;
            settingsVersion = 8;
        }
        if (settingsVersion < 9) {
            // 25 October 2026, playtest (Simon): the axe illagers (vindicator, skirmisher) are far
            // too strong, so only very few of them come; the heavy archer was too weak.
            if (subclassDamage.getOrDefault("heavy", 1.0) == 1.0) {
                subclassDamage.put("heavy", 1.25);
            }
            if (effectMobReplacements != null) {
                effectMobReplacements.replace("minecraft:wither_skeleton", "minecraft:vindicator", "minecraft:skeleton");
                effectMobReplacements.replace("friendsandfoes:iceologer", "minecraft:vindicator", "minecraft:pillager");
            }
            if (mobs != null) {
                for (MobEntry entry : mobs) {
                    if (entry == null || entry.id == null) {
                        continue;
                    }
                    if (entry.id.equals("minecraft:vindicator") || entry.id.equals("takesapillage:skirmisher")) {
                        entry.weight = Math.min(entry.weight, 1);
                        entry.weightPerLevel = Math.min(entry.weightPerLevel, 0.05);
                    } else if (entry.id.equals("minecraft:ravager+minecraft:vindicator")) {
                        entry.weightPerLevel = Math.min(entry.weightPerLevel, 0.1);
                    }
                }
            }
            settingsVersion = 9;
        }
        if (settingsVersion < 10) {
            // Simon 26.10.: the shield fighter takes as much as anyone, the bulwark 35 % less (it was
            // the other way round on the test bench); the alchemist deals 15 % more.
            double shield = subclassDamageTaken.getOrDefault("shieldfighter", 1.0);
            if (Math.abs(shield - 0.8) < 1e-9 || Math.abs(shield - 1.0) < 1e-9) {
                subclassDamageTaken.put("shieldfighter", 1.0);
            }
            if (subclassDamageTaken.getOrDefault("bulwark", 1.0) == 1.0) {
                subclassDamageTaken.put("bulwark", 0.65);
            }
            if (subclassDamage.getOrDefault("alchemist", 1.0) == 1.0) {
                subclassDamage.put("alchemist", 1.15);
            }
            settingsVersion = 10;
        }
        if (settingsVersion < 11) {
            // Simon 27.10.: the ice illager (iceologer) comes far more rarely.
            if (mobs != null) {
                for (MobEntry entry : mobs) {
                    if (entry != null && "friendsandfoes:iceologer".equals(entry.id)) {
                        entry.weight = Math.min(entry.weight, 1);
                        entry.weightPerLevel = Math.min(entry.weightPerLevel, 0.1);
                    }
                }
            }
            settingsVersion = 11;
        }
        if (settingsVersion < 12) {
            // Simon 27.10.: more sappers from level 3 on (they came from level 5, one per wave).
            if (sapperFromLevel == 5) {
                sapperFromLevel = 3;
            }
            settingsVersion = 12;
        }
        if (settingsVersion < 13) {
            // Simon 27.10., DPS bench at level 10: the beast tamer +20 %, the bolt archer -40 %,
            // the shield fighter -30 %, the bulwark -15 %; the medic heals as much as the warden
            // (3.8 per second instead of 3.1). The paladin's -10 % was taken back (20:28).
            scale(subclassDamage, "tamer", 1.2);
            scale(subclassDamage, "bolter", 0.6);
            scale(subclassDamage, "shieldfighter", 0.7);
            scale(subclassDamage, "bulwark", 0.85);
            scale(subclassHealing, "medic", 1.2);
            settingsVersion = 13;
        }
        if (Double.isNaN(meleeLifesteal)) {
            meleeLifesteal = 0.08;
        }
        meleeLifesteal = Math.min(1.0, Math.max(0.0, meleeLifesteal));
        if (antiHealMobs == null) {
            antiHealMobs = new java.util.ArrayList<>(java.util.List.of("minecraft:spider", "minecraft:cave_spider", "minecraft:witch"));
        }
        antiHealShare = Double.isNaN(antiHealShare) ? 0.5 : Math.min(1.0, Math.max(0.0, antiHealShare));
        antiHealSeconds = Double.isNaN(antiHealSeconds) || antiHealSeconds <= 0 ? 4.0 : Math.min(30.0, antiHealSeconds);
        antiHealFromLevel = Math.max(1, antiHealFromLevel);
        if (Double.isNaN(zombieSpiderVsMelee) || zombieSpiderVsMelee <= 0) {
            zombieSpiderVsMelee = 2.0;
        }
        zombieSpiderVsMelee = Math.min(5.0, Math.max(0.1, zombieSpiderVsMelee));
        armorBreakShare = Double.isNaN(armorBreakShare) ? 0.3 : Math.min(1.0, Math.max(0.0, armorBreakShare));
        armorBreakSeconds = Double.isNaN(armorBreakSeconds) ? 3.0 : Math.min(30.0, Math.max(0.0, armorBreakSeconds));
        if (Double.isNaN(berserkerLifesteal)) {
            berserkerLifesteal = 0.12;
        }
        berserkerLifesteal = Math.min(1.0, Math.max(0.0, berserkerLifesteal));
        if (Double.isNaN(comboKillLifesteal)) {
            comboKillLifesteal = 0.15;
        }
        comboKillLifesteal = Math.min(1.0, Math.max(0.0, comboKillLifesteal));
        if (Double.isNaN(shieldEnergySeconds) || shieldEnergySeconds <= 0) {
            shieldEnergySeconds = 6.0;
        }
        shieldEnergySeconds = Math.min(60.0, Math.max(1.0, shieldEnergySeconds));
        if (Double.isNaN(shieldRechargePerSecond) || shieldRechargePerSecond <= 0) {
            shieldRechargePerSecond = 1.0;
        }
        shieldRechargePerSecond = Math.min(20.0, Math.max(0.1, shieldRechargePerSecond));
        if (Double.isNaN(maxDamageReduction) || maxDamageReduction <= 0) {
            maxDamageReduction = 0.70;
        }
        maxDamageReduction = Math.min(0.95, Math.max(0.1, maxDamageReduction));
        if (Double.isNaN(ladderCarrierShare)) {
            ladderCarrierShare = 0.07;
        }
        ladderCarrierShare = Math.min(1.0, Math.max(0.0, ladderCarrierShare));
        sapperFromLevel = Math.max(0, sapperFromLevel);
        sappersPerLevel = Double.isNaN(sappersPerLevel) ? 0.15 : Math.max(0.0, Math.min(2.0, sappersPerLevel));
        sapperMaxPerWave = clamp(sapperMaxPerWave <= 0 ? 4 : sapperMaxPerWave, 1, 20);
        sapperHolesPerSide = clamp(sapperHolesPerSide <= 0 ? 3 : sapperHolesPerSide, 1, 20);
        sapperBlastRadius = Double.isNaN(sapperBlastRadius) || sapperBlastRadius <= 0 ? 3.0 : Math.min(8.0, sapperBlastRadius);
        if (companionSettingsVersion < 1) {
            // Simon 26.10.: monsters turn on companions only within 3 blocks and for 3 s.
            if (companionTauntRadius == 6.0) {
                companionTauntRadius = 3.0;
            }
            companionSettingsVersion = 1;
        }
        companionTauntRadius = Double.isNaN(companionTauntRadius) ? 3.0 : Math.min(16.0, Math.max(0.0, companionTauntRadius));
        companionTauntSeconds = Double.isNaN(companionTauntSeconds) || companionTauntSeconds <= 0 ? 3.0
                : Math.min(30.0, companionTauntSeconds);
        if (Double.isNaN(companionScaling)) {
            companionScaling = 0.8;
        }
        companionScaling = Math.min(2.0, Math.max(0.0, companionScaling));
        if (Double.isNaN(monsterDamage)) {
            monsterDamage = 1.2;
        }
        monsterDamage = Math.min(5.0, Math.max(0.1, monsterDamage));
        Map<String, Double> byType = new LinkedHashMap<>();
        if (monsterDamageByType != null) {
            for (Map.Entry<String, Double> e : monsterDamageByType.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isNaN()) {
                    byType.put(e.getKey().trim().toLowerCase(java.util.Locale.ROOT), Math.min(5.0, Math.max(0.1, e.getValue())));
                }
            }
        }
        monsterDamageByType = byType;
        crateOddsNormal = odds(crateOddsNormal, new int[]{60, 25, 10, 4, 1});
        crateOddsEvent = odds(crateOddsEvent, new int[]{45, 30, 16, 7, 2});
        crateOddsFragment = odds(crateOddsFragment, new int[]{30, 30, 22, 13, 5});
        if (Double.isNaN(crateClassShare)) {
            crateClassShare = 0.7;
        }
        crateClassShare = Math.min(1.0, Math.max(0.0, crateClassShare));
        if (Double.isNaN(bonusCrateChance)) {
            bonusCrateChance = 0.35;
        }
        bonusCrateChance = Math.min(1.0, Math.max(0.0, bonusCrateChance));
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
