package com.mki.nightsiege.core;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The eight dungeons of the dungeon update (Simon, 27 October 2026: "Es reichen 8 Dungeons und
 * 2 davon können jeweils ähnlich sein, nur eine leicht andere Block-Palette"). Four buildings,
 * each in two palettes. Which one comes is decided by the ground where the scouts saw the band.
 */
public enum ExpeditionKind {
    STONE_CRYPT("stone_crypt", Layout.CRYPT, NightFragment.BLOOD,
            List.of("minecraft:zombie", "minecraft:zombie", "minecraft:skeleton", "minecraft:skeleton", "nightsiege:revenant"),
            "minecraft:wither_skeleton",
            palette("stone_bricks", "cracked_stone_bricks", "polished_andesite", "stone_bricks", "chiseled_stone_bricks",
                    "stone_bricks", "lantern", "cobweb", "stone_brick_stairs", "stone_brick_slab", "gray_banner", "mossy_stone_bricks"),
            List.of()),
    DESERT_CRYPT("desert_crypt", Layout.CRYPT, NightFragment.BLOOD,
            List.of("minecraft:husk", "minecraft:husk", "minecraft:skeleton", "minecraft:skeleton", "nightsiege:revenant"),
            "minecraft:husk",
            palette("sandstone", "cut_sandstone", "smooth_sandstone", "cut_sandstone", "chiseled_sandstone",
                    "cut_sandstone", "lantern", "orange_terracotta", "sandstone_stairs", "sandstone_slab", "orange_banner",
                    "brown_terracotta"),
            List.of("desert", "badlands", "savanna")),
    ILLAGER_KEEP("illager_keep", Layout.KEEP, NightFragment.STORM,
            List.of("minecraft:vindicator", "minecraft:vindicator", "minecraft:pillager", "minecraft:pillager",
                    "nightsiege:standard_bearer", "nightsiege:iron_knight"),
            "minecraft:vindicator",
            palette("cobblestone", "dark_oak_planks", "dark_oak_planks", "spruce_planks", "dark_oak_log",
                    "dark_oak_planks", "lantern", "gray_wool", "dark_oak_stairs", "dark_oak_slab", "gray_banner", "barrel"),
            List.of()),
    FROST_KEEP("frost_keep", Layout.KEEP, NightFragment.FROST,
            List.of("minecraft:stray", "minecraft:stray", "minecraft:pillager", "minecraft:vindicator",
                    "friendsandfoes:iceologer"),
            "minecraft:stray",
            palette("packed_ice", "spruce_planks", "spruce_planks", "snow_block", "spruce_log",
                    "spruce_planks", "lantern", "blue_ice", "spruce_stairs", "spruce_slab", "white_banner", "white_wool"),
            List.of("snow", "frozen", "ice", "grove", "peaks", "slopes")),
    POISON_SWAMP("poison_swamp", Layout.CELLAR, NightFragment.MIST,
            List.of("minecraft:witch", "minecraft:bogged", "minecraft:bogged", "minecraft:spider", "minecraft:spider",
                    "minecraft:cave_spider"),
            "minecraft:witch",
            palette("mud_bricks", "packed_mud", "moss_block", "mud_bricks", "mangrove_log",
                    "mud_bricks", "ochre_froglight", "moss_block", "mud_brick_stairs", "mud_brick_slab", "green_banner",
                    "mangrove_roots"),
            List.of("swamp", "mangrove", "jungle")),
    DEEP_CELLAR("deep_cellar", Layout.CELLAR, NightFragment.SHADOW,
            List.of("minecraft:spider", "minecraft:cave_spider", "minecraft:cave_spider", "minecraft:skeleton",
                    "minecraft:skeleton"),
            "minecraft:skeleton",
            palette("deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles", "polished_deepslate", "chiseled_deepslate",
                    "deepslate_tiles", "soul_lantern", "sculk", "deepslate_brick_stairs", "deepslate_brick_slab", "black_banner",
                    "cobweb"),
            List.of()),
    NETHER_FORGE("nether_forge", Layout.FORGE, NightFragment.EMBER,
            List.of("minecraft:blaze", "minecraft:blaze", "minecraft:wither_skeleton", "minecraft:wither_skeleton",
                    "nightsiege:iron_knight"),
            "nightsiege:iron_knight",
            palette("polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "polished_blackstone", "blackstone",
                    "chiseled_polished_blackstone", "polished_blackstone_bricks", "lantern", "gilded_blackstone",
                    "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab", "red_banner", "anvil"),
            List.of()),
    BASALT_FORGE("basalt_forge", Layout.FORGE, NightFragment.EMBER,
            List.of("minecraft:wither_skeleton", "minecraft:wither_skeleton", "minecraft:blaze", "minecraft:skeleton"),
            "minecraft:wither_skeleton",
            palette("polished_basalt", "basalt", "smooth_basalt", "blackstone", "basalt",
                    "smooth_basalt", "soul_lantern", "crying_obsidian", "blackstone_stairs", "blackstone_slab",
                    "light_gray_banner", "iron_chain"),
            List.of("stony", "windswept", "jagged", "meadow"));

    /** The four buildings; two dungeons share each, in their own blocks. */
    public enum Layout { CRYPT, KEEP, CELLAR, FORGE }

    /** The parts of a building whose block the palette chooses. */
    public enum Part { WALL, WALL_ALT, FLOOR, FLOOR_ALT, PILLAR, CEILING, LIGHT, ACCENT, STAIRS, SLAB, BANNER, DECOR }

    private final String id;
    private final Layout layout;
    private final NightFragment fragment;
    private final List<String> mobs;
    private final String champion;
    private final Map<Part, String> palette;
    private final List<String> biomes;

    ExpeditionKind(String id, Layout layout, NightFragment fragment, List<String> mobs, String champion,
                   Map<Part, String> palette, List<String> biomes) {
        this.id = id;
        this.layout = layout;
        this.fragment = fragment;
        this.mobs = mobs;
        this.champion = champion;
        this.palette = palette;
        this.biomes = biomes;
    }

    private static Map<Part, String> palette(String wall, String wallAlt, String floor, String floorAlt, String pillar,
                                             String ceiling, String light, String accent, String stairs, String slab,
                                             String banner, String decor) {
        return Map.ofEntries(Map.entry(Part.WALL, wall), Map.entry(Part.WALL_ALT, wallAlt), Map.entry(Part.FLOOR, floor),
                Map.entry(Part.FLOOR_ALT, floorAlt), Map.entry(Part.PILLAR, pillar), Map.entry(Part.CEILING, ceiling),
                Map.entry(Part.LIGHT, light), Map.entry(Part.ACCENT, accent), Map.entry(Part.STAIRS, stairs),
                Map.entry(Part.SLAB, slab), Map.entry(Part.BANNER, banner), Map.entry(Part.DECOR, decor));
    }

    public String id() {
        return id;
    }

    public Layout layout() {
        return layout;
    }

    public NightFragment fragment() {
        return fragment;
    }

    /** The monsters of its rooms and waves (entries may repeat for weight). */
    public List<String> mobs() {
        return mobs;
    }

    /** The monster the champion is made from. */
    public String champion() {
        return champion;
    }

    /** The block id (without "minecraft:") of a part. */
    public String block(Part part) {
        return palette.get(part);
    }

    public String translationKey() {
        return "nightsiege.expedition." + id;
    }

    public String championKey() {
        return "nightsiege.expedition." + id + ".champion";
    }

    public static ExpeditionKind byId(String id) {
        for (ExpeditionKind k : values()) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return null;
    }

    /**
     * The dungeon for the ground at a spot, by the path of its biome id ("snowy_taiga",
     * "desert" …). Where no kind claims the biome, the common ones come most often: stone
     * crypt and illager keep; the deep cellar and the nether forge now and then.
     */
    public static ExpeditionKind forBiome(String biomePath, int roll) {
        String b = biomePath == null ? "" : biomePath.toLowerCase(Locale.ROOT);
        for (ExpeditionKind k : values()) {
            for (String word : k.biomes) {
                if (b.contains(word)) {
                    return k;
                }
            }
        }
        int r = Math.floorMod(roll, 10);
        return r < 4 ? STONE_CRYPT : r < 8 ? ILLAGER_KEEP : r < 9 ? DEEP_CELLAR : NETHER_FORGE;
    }

    /** Guard rooms by world level: 2 up to level 9, 3 up to 19, then 4. */
    public static int rooms(int level) {
        return level < 10 ? 2 : level < 20 ? 3 : 4;
    }

    /** The champion's life factor by world level: 4, 5, 6. */
    public static double championLife(int level) {
        return level < 10 ? 4.0 : level < 20 ? 5.0 : 6.0;
    }

    /** Monsters in a guard room. */
    public static int guards(int level, int fighters) {
        return guards(level, fighters, 0.5, 10);
    }

    /** As above with the share per further fighter and the cap given (the playtest scales per player). */
    public static int guards(int level, int fighters, double perPlayer, int cap) {
        double base = 3 + Math.max(0, level) / 6.0;
        return (int) Math.min(cap, Math.round(base * (1.0 + perPlayer * Math.max(0, fighters - 1))));
    }

    /** Monsters in an arena wave. */
    public static int wave(int level, int fighters) {
        return wave(level, fighters, 0.5, 16);
    }

    /** As above with the share per further fighter and the cap given. */
    public static int wave(int level, int fighters, double perPlayer, int cap) {
        double base = 4 + Math.max(0, level) / 4.0;
        return (int) Math.min(cap, Math.round(base * (1.0 + perPlayer * Math.max(0, fighters - 1))));
    }
}
