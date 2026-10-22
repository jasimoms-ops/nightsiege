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
    /** The night fragment beating it pays out (see {@link NightFragment}). */
    public String fragment = NightFragment.BLOOD.name();
    /** Share of the structures of this kind that hide a core (1 = every one). */
    public double chance = 1.0;
    /**
     * A core place set by hand in the creative tool: x, y, z inside the piece built
     * from {@link #coreTemplate}, before the piece is turned. Null: the place is
     * found by itself.
     */
    public List<Integer> core;
    /** The template of the piece the hand-set core stands in. */
    public String coreTemplate;

    public DungeonType() {
    }

    public DungeonType(String key, String structure, DungeonTier tier, DungeonTheme theme) {
        this(key, structure, tier, theme, NightFragment.BLOOD);
    }

    public DungeonType(String key, String structure, DungeonTier tier, DungeonTheme theme, NightFragment fragment) {
        this.key = key;
        this.structure = structure;
        this.tier = tier.name();
        this.theme = theme.name();
        this.fragment = fragment.name();
    }

    public NightFragment fragment() {
        NightFragment f = NightFragment.byName(fragment);
        return f == null ? NightFragment.BLOOD : f;
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

    /**
     * The dungeons of "Dungeons and Taverns" (namespace nova_structures): every one
     * of them hides a core. Taverns, villages, wells, camps and watchtowers stay
     * ordinary places.
     */
    public static List<DungeonType> defaults() {
        List<DungeonType> list = new ArrayList<>();
        DungeonTier e = DungeonTier.EASY;
        DungeonTier m = DungeonTier.MEDIUM;
        DungeonTier h = DungeonTier.HARD;
        DungeonTier n = DungeonTier.NETHER;
        DungeonTheme undead = DungeonTheme.RUINS;
        DungeonTheme illager = DungeonTheme.OUTPOST;
        DungeonTheme sea = DungeonTheme.OCEAN;
        DungeonTheme nether = DungeonTheme.NETHER;
        DungeonTheme frost = DungeonTheme.FROST;
        DungeonTheme swamp = DungeonTheme.SWAMP;
        DungeonTheme end = DungeonTheme.END;
        NightFragment blood = NightFragment.BLOOD;
        NightFragment mist = NightFragment.MIST;
        NightFragment ice = NightFragment.FROST;
        NightFragment storm = NightFragment.STORM;
        NightFragment shadow = NightFragment.SHADOW;
        NightFragment ember = NightFragment.EMBER;
        String d = "nova_structures:";
        // easy
        list.add(new DungeonType("undead_crypt", d + "undead_crypt", e, undead, blood));
        list.add(new DungeonType("creeping_crypt", d + "creeping_crypt", e, undead, mist));
        list.add(new DungeonType("combat_shrine_1", d + "shrine_combat_tier_1", e, undead, blood));
        list.add(new DungeonType("combat_shrine_2", d + "shrine_combat_tier_2", e, undead, blood));
        // medium
        list.add(new DungeonType("jungle_ruins", d + "jungle_ruins", m, undead, blood));
        list.add(new DungeonType("desert_ruins", d + "desert_ruins", m, undead, blood));
        list.add(new DungeonType("combat_shrine_3", d + "shrine_combat_tier_3", m, undead, blood));
        list.add(new DungeonType("combat_shrine_4", d + "shrine_combat_tier_4", m, undead, blood));
        list.add(new DungeonType("toxic_lair", d + "toxic_lair", m, swamp, mist));
        list.add(new DungeonType("witch_villa", d + "witch_villa", m, swamp, mist));
        list.add(new DungeonType("catacomb", d + "catacomb", m, frost, ice));
        list.add(new DungeonType("stray_outlook", d + "stray_outlook", m, frost, ice));
        list.add(new DungeonType("illager_barracks", d + "illager_barracks", m, illager, storm));
        list.add(new DungeonType("bunker", d + "bunker", m, undead, storm));
        list.add(new DungeonType("shrine_tower", d + "shrine_tower", m, undead, storm));
        list.add(new DungeonType("trial_dungeon", d + "trial_dungeon", m, undead, shadow));
        list.add(new DungeonType("illager_hideout", d + "illager_hideout", m, illager, shadow));
        list.add(new DungeonType("nether_port", d + "nether_port", m, nether, ember));
        list.add(new DungeonType("piglin_outstation", d + "piglin_outstation", m, nether, ember));
        // hard
        list.add(new DungeonType("combat_shrine_5", d + "shrine_combat_tier_5", h, undead, blood));
        list.add(new DungeonType("combat_shrine_6", d + "shrine_combat_tier_6", h, undead, blood));
        list.add(new DungeonType("pale_residence", d + "pale_residence", h, undead, mist));
        list.add(new DungeonType("stray_fort", d + "stray_fort", h, frost, ice));
        list.add(new DungeonType("lone_citadel", d + "lone_citadel", h, frost, ice));
        list.add(new DungeonType("trident_monument", d + "trident_trial_monument", h, sea, storm));
        list.add(new DungeonType("illager_manor", d + "illager_manor", h, illager, shadow));
        // the strongest: Nether and End
        list.add(new DungeonType("nether_keep", d + "nether_keep", n, nether, ember));
        list.add(new DungeonType("sealing_halls", d + "sealing_halls", n, nether, ember));
        list.add(new DungeonType("piglin_donjon", d + "piglin_donjon", n, nether, ember));
        list.add(new DungeonType("end_castle", d + "end_castle", n, end, shadow));
        return list;
    }
}
