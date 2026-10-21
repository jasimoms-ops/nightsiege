package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One kind of dungeon: which structures it lives in, how hard it is and what
 * lives there. A structure pattern ending in {@code *} matches every id that
 * starts with the rest, so all pillager outpost variants are one dungeon kind.
 */
public class DungeonType {
    public String key = "";
    public String structure = "";
    public String tier = DungeonTier.EASY.name();
    public String theme = DungeonTheme.RUINS.name();

    public DungeonType() {
    }

    public DungeonType(String key, String structure, DungeonTier tier, DungeonTheme theme) {
        this.key = key;
        this.structure = structure;
        this.tier = tier.name();
        this.theme = theme.name();
    }

    public DungeonTier tier() {
        DungeonTier t = DungeonTier.byName(tier);
        return t == null ? DungeonTier.EASY : t;
    }

    public DungeonTheme theme() {
        DungeonTheme t = DungeonTheme.byName(theme);
        return t == null ? DungeonTheme.RUINS : t;
    }

    /** The mod a structure comes from, for the list in the test tool. */
    public String namespace() {
        int colon = structure.indexOf(':');
        return colon < 0 ? "minecraft" : structure.substring(0, colon);
    }

    public boolean matches(String structureId) {
        if (structureId == null || structure == null || structure.isBlank()) {
            return false;
        }
        if (structure.endsWith("*")) {
            return structureId.startsWith(structure.substring(0, structure.length() - 1));
        }
        return structure.equals(structureId);
    }

    public static DungeonType find(List<DungeonType> types, String structureId) {
        for (DungeonType type : types) {
            if (type.matches(structureId)) {
                return type;
            }
        }
        return null;
    }

    public static DungeonType byKey(List<DungeonType> types, String key) {
        for (DungeonType type : types) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }

    /** The dungeons of Simon's modpack, from the concept. */
    public static List<DungeonType> defaults() {
        List<DungeonType> list = new ArrayList<>();
        DungeonTier e = DungeonTier.EASY;
        DungeonTier m = DungeonTier.MEDIUM;
        DungeonTier h = DungeonTier.HARD;
        DungeonTier n = DungeonTier.NETHER;
        DungeonTheme ruins = DungeonTheme.RUINS;
        DungeonTheme outpost = DungeonTheme.OUTPOST;
        DungeonTheme ocean = DungeonTheme.OCEAN;
        DungeonTheme nether = DungeonTheme.NETHER;
        list.add(new DungeonType("ruin", "structures:ruin2", e, ruins));
        list.add(new DungeonType("ruin_ii", "structures:ruin3", e, ruins));
        list.add(new DungeonType("ruin_plus", "structuresplus:ruin", e, ruins));
        list.add(new DungeonType("statue_ruins", "mvs:statue_ruins", e, ruins));
        list.add(new DungeonType("mine_camp", "mvs:mine_with_campsite", e, ruins));
        list.add(new DungeonType("outpost", "towns_and_towers:pillager_outpost_*", m, outpost));
        list.add(new DungeonType("castle_ruins", "mvs:castle_ruins", m, ruins));
        list.add(new DungeonType("ruined_beacon", "mvs:ruined_beacon", m, ruins));
        list.add(new DungeonType("diorite_tower", "mvs:diorite_tower", m, outpost));
        list.add(new DungeonType("trapped_council", "structuresplus:council_of_the_trapped_ruins", m, ruins));
        list.add(new DungeonType("ruined_council", "structures:ruined_council", m, ruins));
        list.add(new DungeonType("dungeon", "structures:dungeon", m, ruins));
        list.add(new DungeonType("underwater_shrine", "mos:underwater_shrine", m, ocean));
        list.add(new DungeonType("ocean_temple", "mos:small_ocean_temple", m, ocean));
        list.add(new DungeonType("warped_tower", "mvs:large_warped_tower", h, nether));
        list.add(new DungeonType("pillager_castle", "structures:pillager_castle", h, outpost));
        list.add(new DungeonType("war_castle", "structures:castle_war", h, outpost));
        list.add(new DungeonType("sunken_castle", "mos:ruined_castle", h, ocean));
        list.add(new DungeonType("atlantis", "mos:ruins_atlantis", h, ocean));
        list.add(new DungeonType("nether_arena", "structures:nether_arena", n, nether));
        return list;
    }
}
