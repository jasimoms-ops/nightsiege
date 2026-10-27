package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Everything a reward crate can hold. A gain belongs to one class (or to all,
 * as a general gain) and always only works in that class's night kit.
 *
 * <p>Gains with stages rise one stage per draw; "+1" enchantments need two draws
 * for their one step (the first draw shows as 1/2). A draw of a gain that is
 * already at its top is a duplicate and pays skill points instead.</p>
 *
 * <p>Gains that are not built yet ({@link Gain#ready()} false) show in the
 * collection but are never drawn.</p>
 */
public final class CrateGains {

    /** One gain: its id, class (null for every class), rarity, stages, the way it needs ("" for any). */
    public record Gain(String id, SkillClass clazz, CrateRarity rarity, int stages, boolean twoPulls,
                       String way, boolean ready, boolean consumable) {

        /** The number of draws to reach the top. */
        public int topStage() {
            return twoPulls ? stages * 2 : stages;
        }

        /** The working level for a stage: "+1" gains count only every second draw. */
        public int level(int stage) {
            int s = Math.max(0, Math.min(topStage(), stage));
            return twoPulls ? s / 2 : s;
        }

        public boolean general() {
            return clazz == null;
        }

        public String translationKey() {
            return "nightsiege.gain." + id;
        }
    }

    private static final Map<String, Gain> GAINS = new LinkedHashMap<>();

    static {
        // ---- general: every class ----
        add("proviant", null, CrateRarity.COMMON, 4, false, "", true);
        add("schutz", null, CrateRarity.COMMON, 1, true, "", true);
        add("fackeln", null, CrateRarity.COMMON, 1, false, "", true);
        add("notration", null, CrateRarity.COMMON, 3, false, "", true);
        add("baukiste", null, CrateRarity.UNCOMMON, 1, false, "", true);
        add("heiltrank", null, CrateRarity.UNCOMMON, 2, false, "", true);
        add("wurfperlen", null, CrateRarity.UNCOMMON, 1, false, "", true);
        add("wurfspeere", null, CrateRarity.UNCOMMON, 1, false, "", true);
        add("sprungstiefel", null, CrateRarity.RARE, 1, false, "", true);
        add("amulett", null, CrateRarity.RARE, 1, false, "", true);
        addConsumable("glueck", null, CrateRarity.RARE);
        add("totem", null, CrateRarity.EPIC, 1, false, "", true);
        add("kletterhaken", null, CrateRarity.EPIC, 1, false, "", true);
        add("federleicht", null, CrateRarity.EPIC, 1, false, "", true);
        add("zweite_chance", null, CrateRarity.EPIC, 1, false, "", true);
        add("kriegsbanner", null, CrateRarity.LEGENDARY, 1, false, "", true);

        // ---- warrior ----
        SkillClass w = SkillClass.WARRIOR;
        add("schaerfe", w, CrateRarity.COMMON, 1, true, "", true);
        add("wurfbeile", w, CrateRarity.COMMON, 3, false, "", true);
        add("gestaehlt", w, CrateRarity.COMMON, 1, false, "", true);
        // "pluenderung" did nothing since siege monsters drop nothing; the cleaving axe instead (27.10.).
        add("spaltaxt", w, CrateRarity.UNCOMMON, 1, false, SkillTree.WAY_MIGHT, true);
        add("schwung", w, CrateRarity.UNCOMMON, 1, true, SkillTree.WAY_BLADE, true);   // the sword only (27.10.)
        add("kriegsgurt", w, CrateRarity.UNCOMMON, 1, false, "", true);
        add("feuerklinge", w, CrateRarity.RARE, 1, false, "", true);
        add("blitzklinge", w, CrateRarity.RARE, 1, false, "", true);
        add("kampfhorn", w, CrateRarity.RARE, 1, false, "", true);
        // "ersatzwaffe_w" (axe and sword) left the crates too (Simon, 27 October 2026).
        add("zweihaender", w, CrateRarity.EPIC, 1, false, "", true);
        add("hinrichter", w, CrateRarity.EPIC, 1, false, "", true);
        add("kampfrausch", w, CrateRarity.EPIC, 1, false, "", true);
        add("schlachtentrance", w, CrateRarity.LEGENDARY, 1, false, "", true);

        // ---- archer ----
        SkillClass a = SkillClass.ARCHER;
        add("koecherfuellung", a, CrateRarity.COMMON, 4, false, "", true);
        add("staerke", a, CrateRarity.COMMON, 1, true, SkillTree.WAY_BOW, true);
        add("schnellladen", a, CrateRarity.COMMON, 1, true, SkillTree.WAY_CROSSBOW, true);
        add("lichtpfeile", a, CrateRarity.UNCOMMON, 1, false, "", true);
        add("spektralpfeile", a, CrateRarity.UNCOMMON, 1, false, "", true);
        add("zielfernrohr", a, CrateRarity.UNCOMMON, 1, false, "", true);
        add("kletterpfeile", a, CrateRarity.RARE, 1, false, "", true);
        add("sturmpfeile", a, CrateRarity.RARE, 1, false, "", true);
        // "ersatzwaffe_a" (second ranged weapon) left the crates (Simon 26.10.).
        add("tarnumhang", a, CrateRarity.RARE, 1, false, "", true);
        add("teernetz", a, CrateRarity.RARE, 1, false, SkillTree.WAY_CROSSBOW, true);   // 27.10.: nets stop ladders and sappers
        add("koecher", a, CrateRarity.EPIC, 1, false, "", true);
        add("adlerfeder", a, CrateRarity.EPIC, 1, false, "", true);
        add("lenkpfeile", a, CrateRarity.EPIC, 1, false, "", true);
        add("lockpfeil", a, CrateRarity.LEGENDARY, 1, false, "", true);

        // ---- guardian ----
        SkillClass g = SkillClass.GUARDIAN;
        add("fester_griff", g, CrateRarity.RARE, 1, false, "", true);   // rare since 27 October 2026 (Simon: too common)
        add("projektilschutz", g, CrateRarity.COMMON, 1, true, "", true);
        add("verbandskasten", g, CrateRarity.COMMON, 1, false, "", true);
        add("turmschild", g, CrateRarity.UNCOMMON, 1, false, "", true);
        add("barrikaden", g, CrateRarity.UNCOMMON, 1, false, "", true);
        add("signalhorn", g, CrateRarity.UNCOMMON, 1, false, "", true);
        add("mauerkelle", g, CrateRarity.RARE, 1, false, "", true);
        add("leuchtschild", g, CrateRarity.RARE, 1, false, "", true);
        add("bombenfest", g, CrateRarity.RARE, 1, false, "", true);
        add("schildlaterne", g, CrateRarity.RARE, 1, false, "", true);
        add("deckung", g, CrateRarity.EPIC, 1, false, "", true);
        add("leibwaechter", g, CrateRarity.EPIC, 1, false, "", true);
        add("leichtschild", g, CrateRarity.EPIC, 1, false, "", true);
        add("wundsalbe", g, CrateRarity.RARE, 1, false, SkillTree.WAY_BLESSING, true);   // 27.10.: the healers' own
        add("spiegelschild", g, CrateRarity.LEGENDARY, 1, false, "", true);

        // ---- engineer ----
        SkillClass e = SkillClass.ENGINEER;
        add("blendgranaten", e, CrateRarity.COMMON, 3, false, "", true);
        add("pfeilkiste", e, CrateRarity.COMMON, 1, false, SkillTree.WAY_MECHANICS, true);
        add("werkzeugtasche", e, CrateRarity.COMMON, 1, false, "", true);
        add("verstaerkt", e, CrateRarity.UNCOMMON, 3, false, "", true);
        add("ersatzbatterie", e, CrateRarity.UNCOMMON, 1, false, "", true);
        add("notfallmine", e, CrateRarity.UNCOMMON, 1, false, SkillTree.WAY_CHEMISTRY, true);
        add("stahlturm", e, CrateRarity.RARE, 1, false, SkillTree.WAY_MECHANICS, true);
        add("zweite_ladung", e, CrateRarity.RARE, 1, false, "", true);
        add("oelfass", e, CrateRarity.RARE, 1, false, SkillTree.WAY_CHEMISTRY, true);
        add("recycling", e, CrateRarity.RARE, 1, false, "", true);
        add("ueberkapazitaet", e, CrateRarity.EPIC, 1, false, "", true);
        add("tarnfallen", e, CrateRarity.EPIC, 1, false, "", true);
        add("kupfergolem", e, CrateRarity.EPIC, 1, false, "", true);   // both ways since 27 October 2026: it tends two towers
        add("geschuetzschild", e, CrateRarity.RARE, 1, false, SkillTree.WAY_MECHANICS, true);   // 27 October 2026
        add("munitionskiste", e, CrateRarity.UNCOMMON, 1, false, "", true);   // 27.10.: longer magazines
        add("sappeuralarm", e, CrateRarity.UNCOMMON, 1, false, "", true);    // 27.10.: towers go for sappers first
        add("doppelbrau", e, CrateRarity.RARE, 1, false, SkillTree.WAY_CHEMISTRY, true);   // 27.10.: the alchemist's own
        add("magnetfeld", e, CrateRarity.LEGENDARY, 1, false, "", true);

        // ---- summoner ----
        SkillClass s = SkillClass.SUMMONER;
        add("futterbeutel", s, CrateRarity.COMMON, 3, false, SkillTree.WAY_WILD, true);
        add("knochenstaub", s, CrateRarity.COMMON, 2, false, SkillTree.WAY_DEAD, true);
        add("rueckrufpfeife", s, CrateRarity.COMMON, 1, false, "", true);
        add("wolfspanzer", s, CrateRarity.UNCOMMON, 1, false, SkillTree.WAY_WILD, true);
        add("golemplatten", s, CrateRarity.UNCOMMON, 1, false, SkillTree.WAY_WILD, true);
        add("grabkerzen", s, CrateRarity.UNCOMMON, 1, false, SkillTree.WAY_DEAD, true);
        add("geisterross", s, CrateRarity.RARE, 1, false, "", true);
        add("futternapf", s, CrateRarity.RARE, 1, false, SkillTree.WAY_WILD, true);
        add("totenglocke", s, CrateRarity.RARE, 1, false, SkillTree.WAY_DEAD, true);
        add("grabruestung", s, CrateRarity.RARE, 1, false, SkillTree.WAY_DEAD, true);
        add("doppelstab", s, CrateRarity.EPIC, 1, false, "", true);
        add("spaehfalke", s, CrateRarity.EPIC, 1, false, SkillTree.WAY_WILD, true);
        add("seelengefaess", s, CrateRarity.EPIC, 1, false, SkillTree.WAY_DEAD, true);
        add("seelentausch", s, CrateRarity.LEGENDARY, 1, false, "", true);
        add("seelenzins", s, CrateRarity.EPIC, 1, false, SkillTree.WAY_DEAD, true);   // 27.10.: cheaper spells
    }

    private static final Map<String, String> ICONS = Map.ofEntries(
            Map.entry("teernetz", "minecraft:cobweb"), Map.entry("wundsalbe", "minecraft:honeycomb"),
            Map.entry("munitionskiste", "minecraft:trapped_chest"), Map.entry("sappeuralarm", "minecraft:sculk_sensor"),
            Map.entry("doppelbrau", "minecraft:dragon_breath"), Map.entry("seelenzins", "minecraft:echo_shard"),
            Map.entry("proviant", "minecraft:cooked_beef"), Map.entry("schutz", "minecraft:diamond_chestplate"),
            Map.entry("fackeln", "minecraft:torch"), Map.entry("notration", "minecraft:golden_apple"),
            Map.entry("baukiste", "minecraft:cobblestone"), Map.entry("heiltrank", "minecraft:glistering_melon_slice"),
            Map.entry("wurfperlen", "minecraft:ender_pearl"), Map.entry("wurfspeere", "minecraft:trident"),
            Map.entry("sprungstiefel", "minecraft:leather_boots"), Map.entry("amulett", "minecraft:experience_bottle"),
            Map.entry("glueck", "minecraft:rabbit_foot"), Map.entry("totem", "minecraft:totem_of_undying"),
            Map.entry("kletterhaken", "minecraft:tripwire_hook"), Map.entry("federleicht", "minecraft:feather"),
            Map.entry("zweite_chance", "minecraft:recovery_compass"), Map.entry("kriegsbanner", "minecraft:red_banner"),
            Map.entry("schaerfe", "minecraft:iron_sword"), Map.entry("wurfbeile", "minecraft:iron_axe"),
            Map.entry("gestaehlt", "minecraft:iron_ingot"), Map.entry("spaltaxt", "minecraft:diamond_axe"),
            Map.entry("schwung", "minecraft:golden_sword"), Map.entry("kriegsgurt", "minecraft:iron_chestplate"),
            Map.entry("feuerklinge", "minecraft:blaze_powder"), Map.entry("blitzklinge", "minecraft:lightning_rod"),
            Map.entry("kampfhorn", "minecraft:goat_horn"),
            Map.entry("zweihaender", "minecraft:netherite_sword"), Map.entry("hinrichter", "minecraft:wither_skeleton_skull"),
            Map.entry("kampfrausch", "minecraft:sugar"), Map.entry("schlachtentrance", "minecraft:clock"),
            Map.entry("koecherfuellung", "minecraft:arrow"),
            Map.entry("staerke", "minecraft:bow"), Map.entry("schnellladen", "minecraft:crossbow"),
            Map.entry("lichtpfeile", "minecraft:glowstone_dust"), Map.entry("spektralpfeile", "minecraft:spectral_arrow"),
            Map.entry("zielfernrohr", "minecraft:spyglass"), Map.entry("kletterpfeile", "minecraft:ladder"),
            Map.entry("sturmpfeile", "minecraft:wind_charge"),
            Map.entry("tarnumhang", "minecraft:phantom_membrane"), Map.entry("koecher", "minecraft:bundle"),
            Map.entry("adlerfeder", "minecraft:elytra"), Map.entry("lenkpfeile", "minecraft:ender_eye"),
            Map.entry("lockpfeil", "minecraft:target"),
            Map.entry("fester_griff", "minecraft:shield"), Map.entry("projektilschutz", "minecraft:turtle_helmet"),
            Map.entry("verbandskasten", "minecraft:honey_bottle"), Map.entry("turmschild", "minecraft:iron_door"),
            Map.entry("barrikaden", "minecraft:oak_fence"), Map.entry("signalhorn", "minecraft:bell"),
            Map.entry("mauerkelle", "minecraft:bricks"), Map.entry("leuchtschild", "minecraft:glow_ink_sac"),
            Map.entry("bombenfest", "minecraft:tnt"), Map.entry("schildlaterne", "minecraft:lantern"),
            Map.entry("deckung", "minecraft:iron_bars"), Map.entry("leibwaechter", "minecraft:iron_chain"),
            Map.entry("leichtschild", "minecraft:rabbit_hide"), Map.entry("spiegelschild", "minecraft:amethyst_shard"),
            Map.entry("blendgranaten", "minecraft:snowball"),
            Map.entry("pfeilkiste", "minecraft:barrel"), Map.entry("werkzeugtasche", "minecraft:smithing_table"),
            Map.entry("verstaerkt", "minecraft:iron_block"), Map.entry("ersatzbatterie", "minecraft:redstone"),
            Map.entry("notfallmine", "minecraft:tnt_minecart"), Map.entry("stahlturm", "minecraft:dispenser"),
            Map.entry("zweite_ladung", "minecraft:repeater"), Map.entry("oelfass", "minecraft:lava_bucket"),
            Map.entry("recycling", "minecraft:hopper"), Map.entry("ueberkapazitaet", "minecraft:ender_chest"),
            Map.entry("tarnfallen", "minecraft:tinted_glass"), Map.entry("kupfergolem", "minecraft:copper_block"), Map.entry("geschuetzschild", "minecraft:shield"),
            Map.entry("magnetfeld", "minecraft:lodestone"),
            Map.entry("futterbeutel", "minecraft:beef"), Map.entry("knochenstaub", "minecraft:bone_meal"),
            Map.entry("rueckrufpfeife", "minecraft:lead"), Map.entry("wolfspanzer", "minecraft:wolf_armor"),
            Map.entry("golemplatten", "minecraft:heavy_weighted_pressure_plate"), Map.entry("grabkerzen", "minecraft:purple_candle"),
            Map.entry("geisterross", "minecraft:saddle"), Map.entry("futternapf", "minecraft:bowl"),
            Map.entry("totenglocke", "minecraft:bell"), Map.entry("grabruestung", "minecraft:iron_helmet"),
            Map.entry("doppelstab", "nightsiege:summoning_staff"), Map.entry("spaehfalke", "minecraft:parrot_spawn_egg"),
            Map.entry("seelengefaess", "minecraft:soul_lantern"), Map.entry("seelentausch", "minecraft:chorus_fruit"));

    private CrateGains() {
    }

    /** The item a gain shows as in the crate window and the collection. */
    public static String icon(String id) {
        return ICONS.getOrDefault(id, "minecraft:chest");
    }

    private static void add(String id, SkillClass clazz, CrateRarity rarity, int stages, boolean twoPulls,
                            String way, boolean ready) {
        GAINS.put(id, new Gain(id, clazz, rarity, stages, twoPulls, way, ready, false));
    }

    /** A gain used up by the next crate (the luck crate): it can always be drawn again. */
    private static void addConsumable(String id, SkillClass clazz, CrateRarity rarity) {
        GAINS.put(id, new Gain(id, clazz, rarity, 1, false, "", true, true));
    }

    public static Gain get(String id) {
        return id == null ? null : GAINS.get(id);
    }

    public static List<Gain> all() {
        return Collections.unmodifiableList(new ArrayList<>(GAINS.values()));
    }

    /** Everything one class's collection shows: its own gains, then the general ones. */
    public static List<Gain> forCollection(SkillClass clazz) {
        List<Gain> own = new ArrayList<>();
        List<Gain> general = new ArrayList<>();
        for (Gain gain : GAINS.values()) {
            if (gain.clazz() == clazz) {
                own.add(gain);
            } else if (gain.general()) {
                general.add(gain);
            }
        }
        own.addAll(general);
        return own;
    }

    /** True when a gain fits the class and way and can be drawn at all. */
    public static boolean drawable(Gain gain, SkillClass clazz, String way) {
        if (!gain.ready() || (gain.clazz() != null && gain.clazz() != clazz)) {
            return false;
        }
        return gain.way().isEmpty() || way == null || way.isEmpty() || gain.way().equals(way);
    }

    /**
     * Rolls a rarity from the given weights (common to legendary). A luck crate
     * uses the weights of the next better night kind (the caller passes those).
     */
    public static CrateRarity rollRarity(int[] weights, Random random) {
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return CrateRarity.COMMON;
        }
        int r = random.nextInt(total);
        CrateRarity[] all = CrateRarity.values();
        for (int i = 0; i < all.length && i < weights.length; i++) {
            r -= Math.max(0, weights[i]);
            if (r < 0) {
                return all[i];
            }
        }
        return CrateRarity.COMMON;
    }

    /**
     * Picks the gain of a draw: a rarity is rolled, then one gain of that rarity,
     * class gains at {@code classShare} against general ones. A rarity with no
     * drawable gain steps down to the next one that has some.
     */
    public static Gain roll(SkillClass clazz, String way, int[] weights, double classShare, Random random) {
        CrateRarity rarity = rollRarity(weights, random);
        for (int step = 0; step < CrateRarity.values().length; step++) {
            List<Gain> own = new ArrayList<>();
            List<Gain> general = new ArrayList<>();
            for (Gain gain : GAINS.values()) {
                if (gain.rarity() == rarity && drawable(gain, clazz, way)) {
                    (gain.general() ? general : own).add(gain);
                }
            }
            if (!own.isEmpty() || !general.isEmpty()) {
                boolean pickOwn = general.isEmpty() || (!own.isEmpty() && random.nextDouble() < classShare);
                List<Gain> from = pickOwn ? own : general;
                return from.get(random.nextInt(from.size()));
            }
            if (rarity == CrateRarity.COMMON) {
                break;
            }
            rarity = rarity.lower();
        }
        return null;
    }

    /** Filler for the spinning band: any drawable gain of a rolled rarity. */
    public static Gain filler(SkillClass clazz, String way, int[] weights, Random random) {
        Gain gain = roll(clazz, way, weights, 0.7, random);
        return gain != null ? gain : GAINS.values().iterator().next();
    }
}
