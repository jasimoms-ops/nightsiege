package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.EnumMap;
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
            palette()
                    .put(Part.WALL, "stone_bricks 50, cracked_stone_bricks 15, mossy_stone_bricks 15, tuff_bricks 15, chiseled_stone_bricks 5")
                    .put(Part.BASE, "mossy_stone_bricks 40, cracked_stone_bricks 30, mossy_cobblestone 30")
                    .put(Part.WALL_TOP, "tuff_bricks 60, stone_bricks 40")
                    .put(Part.TRIM, "polished_tuff")
                    .put(Part.FRAME, "chiseled_stone_bricks")
                    .put(Part.PILLAR, "polished_tuff 70, chiseled_tuff 30")
                    .put(Part.FLOOR, "polished_andesite 55, andesite 20, tuff 10, cobblestone 10, gravel 5")
                    .put(Part.RUNNER, "red_carpet")
                    .put(Part.CEILING, "tuff_bricks 70, tuff 30")
                    .put(Part.BEAM, "chiseled_stone_bricks")
                    .put(Part.GALLERY, "polished_tuff")
                    .put(Part.COVER, "stone_bricks 70, mossy_stone_bricks 30")
                    .put(Part.LIGHT_HANG, "soul_lantern")
                    .put(Part.LIGHT_WALL, "soul_campfire")
                    .put(Part.LIGHT_SMALL, "white_candle")
                    .put(Part.ACCENT, "bone_block")
                    .put(Part.PROPS, "decorated_pot, skeleton_skull, bone_block, cobweb")
                    .put(Part.STAIRS, "polished_tuff_stairs")
                    .put(Part.SLAB, "polished_tuff_slab")
                    .put(Part.BANNER, "gray_banner")
                    .put(Part.RAIL, "polished_tuff_wall")
                    .put(Part.THRONE, "red_candle"),
            List.of()),
    DESERT_CRYPT("desert_crypt", Layout.CRYPT, NightFragment.BLOOD,
            List.of("minecraft:husk", "minecraft:husk", "minecraft:skeleton", "minecraft:skeleton", "nightsiege:revenant"),
            "minecraft:husk",
            palette()
                    .put(Part.WALL, "sandstone 40, cut_sandstone 30, smooth_sandstone 20, chiseled_sandstone 10")
                    .put(Part.BASE, "cut_red_sandstone")
                    .put(Part.WALL_TOP, "smooth_sandstone 60, cut_sandstone 40")
                    .put(Part.TRIM, "blue_terracotta 50, orange_terracotta 50")
                    .put(Part.FRAME, "chiseled_sandstone")
                    .put(Part.PILLAR, "cut_sandstone 60, chiseled_sandstone 25, blue_terracotta 15")
                    .put(Part.FLOOR, "smooth_sandstone 50, cut_sandstone 30, sandstone 10, sand 10")
                    .put(Part.RUNNER, "orange_terracotta 50, yellow_terracotta 50")
                    .put(Part.CEILING, "smooth_sandstone 70, cut_sandstone 30")
                    .put(Part.BEAM, "cut_red_sandstone")
                    .put(Part.GALLERY, "cut_sandstone")
                    .put(Part.COVER, "cut_sandstone 70, sandstone 30")
                    .put(Part.LIGHT_HANG, "lantern")
                    .put(Part.LIGHT_WALL, "campfire")
                    .put(Part.LIGHT_SMALL, "orange_candle")
                    .put(Part.ACCENT, "blue_terracotta 60, gold_block 40")
                    .put(Part.PROPS, "decorated_pot, gold_block, chiseled_sandstone")
                    .put(Part.STAIRS, "sandstone_stairs")
                    .put(Part.SLAB, "cut_sandstone_slab")
                    .put(Part.BANNER, "orange_banner")
                    .put(Part.RAIL, "sandstone_wall")
                    .put(Part.THRONE, "red_candle"),
            List.of("desert", "badlands", "savanna")),
    ILLAGER_KEEP("illager_keep", Layout.KEEP, NightFragment.STORM,
            List.of("minecraft:vindicator", "minecraft:vindicator", "minecraft:pillager", "minecraft:pillager",
                    "nightsiege:standard_bearer", "nightsiege:iron_knight"),
            "minecraft:vindicator",
            palette()
                    .put(Part.WALL, "cobblestone 35, stone_bricks 30, mossy_cobblestone 15, andesite 10, cracked_stone_bricks 10")
                    .put(Part.BASE, "cobblestone 50, mossy_cobblestone 50")
                    .put(Part.WALL_TOP, "birch_planks")
                    .put(Part.TRIM, "stripped_dark_oak_log")
                    .put(Part.FRAME, "dark_oak_log")
                    .put(Part.PILLAR, "dark_oak_log")
                    .put(Part.FLOOR, "dark_oak_planks 55, spruce_planks 25, deepslate_tiles 20")
                    .put(Part.RUNNER, "gray_wool")
                    .put(Part.CEILING, "dark_oak_planks")
                    .put(Part.BEAM, "dark_oak_log")
                    .put(Part.GALLERY, "dark_oak_planks")
                    .put(Part.COVER, "barrel 50, hay_block 50")
                    .put(Part.LIGHT_HANG, "lantern")
                    .put(Part.LIGHT_WALL, "campfire")
                    .put(Part.LIGHT_SMALL, "lantern")
                    .put(Part.ACCENT, "weathered_copper")
                    .put(Part.PROPS, "grindstone, anvil, smithing_table, barrel")
                    .put(Part.STAIRS, "dark_oak_stairs")
                    .put(Part.SLAB, "dark_oak_slab")
                    .put(Part.BANNER, "gray_banner")
                    .put(Part.RAIL, "dark_oak_fence")
                    .put(Part.THRONE, "lantern"),
            List.of()),
    FROST_KEEP("frost_keep", Layout.KEEP, NightFragment.FROST,
            List.of("minecraft:stray", "minecraft:stray", "minecraft:pillager", "minecraft:vindicator",
                    "friendsandfoes:iceologer"),
            "minecraft:stray",
            palette()
                    .put(Part.WALL, "polished_diorite 30, calcite 25, stone_bricks 20, packed_ice 15, snow_block 10")
                    .put(Part.BASE, "stone_bricks 50, cobblestone 30, packed_ice 20")
                    .put(Part.WALL_TOP, "calcite 70, snow_block 30")
                    .put(Part.TRIM, "stripped_spruce_log")
                    .put(Part.FRAME, "spruce_log")
                    .put(Part.PILLAR, "spruce_log 60, packed_ice 40")
                    .put(Part.FLOOR, "spruce_planks 60, polished_diorite 25, snow_block 15")
                    .put(Part.RUNNER, "light_blue_carpet")
                    .put(Part.CEILING, "spruce_planks 70, packed_ice 30")
                    .put(Part.BEAM, "spruce_log")
                    .put(Part.GALLERY, "spruce_planks")
                    .put(Part.COVER, "snow_block 50, packed_ice 50")
                    .put(Part.LIGHT_HANG, "lantern 60, soul_lantern 40")
                    .put(Part.LIGHT_WALL, "sea_lantern")
                    .put(Part.LIGHT_SMALL, "lantern")
                    .put(Part.ACCENT, "blue_ice")
                    .put(Part.PROPS, "blue_ice, white_wool, anvil, barrel")
                    .put(Part.STAIRS, "spruce_stairs")
                    .put(Part.SLAB, "spruce_slab")
                    .put(Part.BANNER, "light_blue_banner")
                    .put(Part.RAIL, "spruce_fence")
                    .put(Part.THRONE, "lantern"),
            List.of("snow", "frozen", "ice", "grove", "peaks", "slopes")),
    POISON_SWAMP("poison_swamp", Layout.CELLAR, NightFragment.MIST,
            List.of("minecraft:witch", "minecraft:bogged", "minecraft:bogged", "minecraft:spider", "minecraft:spider",
                    "minecraft:cave_spider"),
            "minecraft:witch",
            palette()
                    .put(Part.WALL, "mud_bricks 45, packed_mud 20, mossy_cobblestone 15, moss_block 10, mangrove_roots 10")
                    .put(Part.BASE, "mossy_cobblestone 40, moss_block 30, muddy_mangrove_roots 30")
                    .put(Part.WALL_TOP, "mud_bricks 60, packed_mud 40")
                    .put(Part.TRIM, "mangrove_planks")
                    .put(Part.FRAME, "mangrove_log")
                    .put(Part.PILLAR, "mangrove_log 60, mud_bricks 40")
                    .put(Part.FLOOR, "packed_mud 35, mud_bricks 25, moss_block 25, muddy_mangrove_roots 15")
                    .put(Part.RUNNER, "moss_carpet")
                    .put(Part.CEILING, "mud_bricks 40, packed_mud 30, moss_block 30")
                    .put(Part.BEAM, "mangrove_log")
                    .put(Part.GALLERY, "mangrove_planks")
                    .put(Part.COVER, "barrel 40, brown_mushroom_block 30, mud_bricks 30")
                    .put(Part.LIGHT_HANG, "lantern")
                    .put(Part.LIGHT_WALL, "ochre_froglight 40, verdant_froglight 30, pearlescent_froglight 30")
                    .put(Part.LIGHT_SMALL, "glow_lichen")
                    .put(Part.ACCENT, "red_mushroom_block")
                    .put(Part.PROPS, "cauldron, brewing_stand, red_mushroom_block, barrel")
                    .put(Part.STAIRS, "mud_brick_stairs")
                    .put(Part.SLAB, "mud_brick_slab")
                    .put(Part.BANNER, "green_banner")
                    .put(Part.RAIL, "mangrove_fence")
                    .put(Part.THRONE, "verdant_froglight"),
            List.of("swamp", "mangrove", "jungle")),
    DEEP_CELLAR("deep_cellar", Layout.CELLAR, NightFragment.SHADOW,
            List.of("minecraft:spider", "minecraft:cave_spider", "minecraft:cave_spider", "minecraft:skeleton",
                    "minecraft:skeleton"),
            "minecraft:skeleton",
            palette()
                    .put(Part.WALL, "deepslate_bricks 40, cracked_deepslate_bricks 20, deepslate_tiles 20, cobbled_deepslate 15, chiseled_deepslate 5")
                    .put(Part.BASE, "cobbled_deepslate 50, cracked_deepslate_bricks 30, sculk 20")
                    .put(Part.WALL_TOP, "deepslate_tiles 70, sculk 30")
                    .put(Part.TRIM, "polished_deepslate")
                    .put(Part.FRAME, "chiseled_deepslate")
                    .put(Part.PILLAR, "polished_deepslate 60, chiseled_deepslate 20, deepslate_tiles 20")
                    .put(Part.FLOOR, "deepslate_tiles 45, cracked_deepslate_tiles 20, polished_deepslate 20, tuff 15")
                    .put(Part.RUNNER, "polished_deepslate")
                    .put(Part.CEILING, "deepslate_tiles 60, sculk 20, cobbled_deepslate 20")
                    .put(Part.BEAM, "polished_deepslate")
                    .put(Part.GALLERY, "deepslate_tiles")
                    .put(Part.COVER, "cobbled_deepslate 60, barrel 40")
                    .put(Part.LIGHT_HANG, "soul_lantern")
                    .put(Part.LIGHT_WALL, "amethyst_cluster")
                    .put(Part.LIGHT_SMALL, "purple_candle")
                    .put(Part.ACCENT, "sculk 60, amethyst_block 40")
                    .put(Part.PROPS, "iron_chain, grindstone, anvil, barrel")
                    .put(Part.STAIRS, "deepslate_brick_stairs")
                    .put(Part.SLAB, "deepslate_brick_slab")
                    .put(Part.BANNER, "black_banner")
                    .put(Part.RAIL, "deepslate_tile_wall")
                    .put(Part.THRONE, "purple_candle"),
            List.of()),
    NETHER_FORGE("nether_forge", Layout.FORGE, NightFragment.EMBER,
            List.of("minecraft:blaze", "minecraft:blaze", "minecraft:wither_skeleton", "minecraft:wither_skeleton",
                    "nightsiege:iron_knight"),
            "nightsiege:iron_knight",
            palette()
                    .put(Part.WALL, "polished_blackstone_bricks 45, cracked_polished_blackstone_bricks 20, blackstone 15, nether_bricks 15, gilded_blackstone 5")
                    .put(Part.BASE, "blackstone 50, nether_bricks 30, red_nether_bricks 20")
                    .put(Part.WALL_TOP, "polished_blackstone_bricks 70, nether_bricks 30")
                    .put(Part.TRIM, "red_nether_bricks")
                    .put(Part.FRAME, "crimson_stem")
                    .put(Part.PILLAR, "polished_blackstone 50, chiseled_polished_blackstone 25, gilded_blackstone 25")
                    .put(Part.FLOOR, "polished_blackstone 50, blackstone 25, polished_blackstone_bricks 15, magma_block 10")
                    .put(Part.RUNNER, "polished_blackstone_bricks")
                    .put(Part.CEILING, "nether_bricks 60, crimson_planks 40")
                    .put(Part.BEAM, "crimson_stem")
                    .put(Part.GALLERY, "crimson_planks")
                    .put(Part.COVER, "coal_block 60, polished_blackstone 40")
                    .put(Part.LIGHT_HANG, "lantern")
                    .put(Part.LIGHT_WALL, "shroomlight")
                    .put(Part.LIGHT_SMALL, "lava_cauldron")
                    .put(Part.ACCENT, "gilded_blackstone 50, red_nether_bricks 50")
                    .put(Part.PROPS, "anvil, smithing_table, coal_block, blast_furnace")
                    .put(Part.STAIRS, "polished_blackstone_brick_stairs")
                    .put(Part.SLAB, "polished_blackstone_brick_slab")
                    .put(Part.BANNER, "red_banner")
                    .put(Part.RAIL, "red_nether_brick_wall")
                    .put(Part.THRONE, "lava_cauldron"),
            List.of()),
    BASALT_FORGE("basalt_forge", Layout.FORGE, NightFragment.EMBER,
            List.of("minecraft:wither_skeleton", "minecraft:wither_skeleton", "minecraft:blaze", "minecraft:skeleton"),
            "minecraft:wither_skeleton",
            palette()
                    .put(Part.WALL, "polished_basalt 35, basalt 20, smooth_basalt 20, blackstone 15, crying_obsidian 5, calcite 5")
                    .put(Part.BASE, "smooth_basalt 50, blackstone 50")
                    .put(Part.WALL_TOP, "basalt 60, polished_basalt 40")
                    .put(Part.TRIM, "polished_blackstone")
                    .put(Part.FRAME, "polished_basalt")
                    .put(Part.PILLAR, "polished_basalt 70, crying_obsidian 15, obsidian 15")
                    .put(Part.FLOOR, "smooth_basalt 40, polished_blackstone 30, tuff 15, calcite 15")
                    .put(Part.RUNNER, "calcite")
                    .put(Part.CEILING, "smooth_basalt 60, basalt 40")
                    .put(Part.BEAM, "polished_blackstone")
                    .put(Part.GALLERY, "polished_blackstone")
                    .put(Part.COVER, "obsidian 50, smooth_basalt 50")
                    .put(Part.LIGHT_HANG, "soul_lantern")
                    .put(Part.LIGHT_WALL, "soul_campfire")
                    .put(Part.LIGHT_SMALL, "crying_obsidian")
                    .put(Part.ACCENT, "crying_obsidian 60, calcite 40")
                    .put(Part.PROPS, "anvil, lodestone, smithing_table, iron_chain")
                    .put(Part.STAIRS, "polished_blackstone_stairs")
                    .put(Part.SLAB, "polished_blackstone_slab")
                    .put(Part.BANNER, "light_gray_banner")
                    .put(Part.RAIL, "polished_blackstone_wall")
                    .put(Part.THRONE, "soul_lantern"),
            List.of("stony", "windswept", "jagged", "meadow"));

    /** The four buildings; two dungeons share each, in their own blocks. */
    public enum Layout { CRYPT, KEEP, CELLAR, FORGE }

    /**
     * The parts of a building whose blocks the palette mixes (content update, 29 October 2026: from
     * 12 single blocks to about 30 per dungeon). The first block of a mix is its ground, the others
     * come in patches by their weight.
     */
    public enum Part {
        WALL, BASE, WALL_TOP, TRIM, FRAME, PILLAR, FLOOR, RUNNER, CEILING, BEAM, GALLERY, COVER,
        LIGHT_HANG, LIGHT_WALL, LIGHT_SMALL, ACCENT, PROPS, STAIRS, SLAB, BANNER,
        /** Railings of galleries and trenches (a wall or a fence: monsters do not jump it). */
        RAIL,
        /** The small light at the altar and the throne (the third colour). */
        THRONE
    }

    /** One block of a mix with its weight. */
    public record Weighted(String block, int weight) {
    }

    /** Builds a palette from text like "stone_bricks 50, tuff_bricks 15" (no weight: 1). */
    public static final class Palette {
        private final Map<Part, List<Weighted>> parts = new EnumMap<>(Part.class);

        public Palette put(Part part, String text) {
            List<Weighted> mix = new ArrayList<>();
            for (String piece : text.split(",")) {
                String[] bits = piece.trim().split("\\s+");
                if (bits[0].isEmpty()) {
                    continue;
                }
                mix.add(new Weighted(bits[0], bits.length > 1 ? Integer.parseInt(bits[1]) : 1));
            }
            parts.put(part, List.copyOf(mix));
            return this;
        }
    }

    private static Palette palette() {
        return new Palette();
    }

    private final String id;
    private final Layout layout;
    private final NightFragment fragment;
    private final List<String> mobs;
    private final String champion;
    private final Map<Part, List<Weighted>> palette;
    private final List<String> biomes;

    ExpeditionKind(String id, Layout layout, NightFragment fragment, List<String> mobs, String champion,
                   Palette palette, List<String> biomes) {
        this.id = id;
        this.layout = layout;
        this.fragment = fragment;
        this.mobs = mobs;
        this.champion = champion;
        this.palette = palette.parts;
        this.biomes = biomes;
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

    /** The ground block id (without "minecraft:") of a part. */
    public String block(Part part) {
        List<Weighted> mix = mix(part);
        return mix.isEmpty() ? "stone" : mix.get(0).block();
    }

    /** The whole mix of a part (never empty for a part the palette names; FRAME falls back to the pillar). */
    public List<Weighted> mix(Part part) {
        List<Weighted> mix = palette.get(part);
        if (mix == null && part == Part.FRAME) {
            mix = palette.get(Part.PILLAR);
        }
        return mix == null ? List.of() : mix;
    }

    /** Every block id the palettes use (the test checks that all exist). */
    public java.util.Set<String> allBlocks() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (List<Weighted> mix : palette.values()) {
            for (Weighted w : mix) {
                out.add(w.block());
            }
        }
        return out;
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
        return guards(level, fighters, 0.5, 14);
    }

    /** As above with the share per further fighter and the cap given (the playtest scales per player). */
    public static int guards(int level, int fighters, double perPlayer, int cap) {
        double base = 5 + Math.max(0, level) / 5.0;   // the halls are four times as big (29 October 2026)
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
