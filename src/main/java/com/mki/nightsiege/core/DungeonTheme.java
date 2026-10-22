package com.mki.nightsiege.core;

import java.util.List;

/**
 * What lives in a dungeon. Every theme has its own mobs, its own guardian boss
 * and the kind of fragment it pays out. Mobs from mods that are missing are
 * skipped at spawn time.
 */
public enum DungeonTheme {
    RUINS(PerkKind.VITALITY, List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:husk", "minecraft:stray",
            "minecraft:spider", "variantsandventures:murk", "variantsandventures:thicket", "minecraft:zombie_villager"),
            List.of("minecraft:wither_skeleton")),
    OUTPOST(PerkKind.BULWARK, List.of("minecraft:pillager", "minecraft:vindicator", "takesapillage:archer",
            "takesapillage:legioner", "takesapillage:skirmisher", "minecraft:witch"),
            List.of("minecraft:evoker")),
    OCEAN(PerkKind.TIDES, List.of("minecraft:drowned", "minecraft:drowned", "minecraft:guardian",
            "minecraft:bogged", "variantsandventures:murk"),
            List.of("minecraft:drowned")),
    NETHER(PerkKind.FLAME, List.of("minecraft:blaze", "minecraft:wither_skeleton", "minecraft:piglin_brute",
            "minecraft:magma_cube", "minecraft:blaze"),
            List.of("friendsandfoes:wildfire", "minecraft:blaze")),
    FROST(PerkKind.VITALITY, List.of("minecraft:stray", "minecraft:stray", "minecraft:skeleton",
            "variantsandventures:gelid", "minecraft:zombie"),
            List.of("friendsandfoes:iceologer", "minecraft:stray")),
    SWAMP(PerkKind.TIDES, List.of("minecraft:witch", "minecraft:bogged", "minecraft:spider", "minecraft:cave_spider",
            "minecraft:slime", "variantsandventures:murk"),
            List.of("minecraft:witch")),
    END(PerkKind.VIGILANCE, List.of("minecraft:enderman", "minecraft:endermite", "minecraft:enderman",
            "minecraft:phantom"),
            List.of("minecraft:enderman"));

    private final PerkKind fragment;
    private final List<String> mobs;
    private final List<String> bosses;

    DungeonTheme(PerkKind fragment, List<String> mobs, List<String> bosses) {
        this.fragment = fragment;
        this.mobs = mobs;
        this.bosses = bosses;
    }

    /** The fragment kind beating a dungeon of this theme pays out. */
    public PerkKind fragment() {
        return fragment;
    }

    public List<String> mobs() {
        return mobs;
    }

    /** Boss candidates in order of preference; the first one that exists is used. */
    public List<String> bosses() {
        return bosses;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static DungeonTheme byName(String name) {
        for (DungeonTheme theme : values()) {
            if (theme.name().equalsIgnoreCase(name)) {
                return theme;
            }
        }
        return null;
    }
}
