package com.mki.nightsiege.core;

/** How hard a dungeon is at its core: waves, base level and wave size. */
public enum DungeonTier {
    EASY(1, 3, 5, 0xFF55FF55),
    MEDIUM(2, 6, 7, 0xFFFFFF55),
    HARD(3, 10, 9, 0xFFFF8040),
    NETHER(3, 14, 10, 0xFFFF4040);

    private final int waves;
    private final int baseLevel;
    private final int mobsPerWave;
    private final int color;

    DungeonTier(int waves, int baseLevel, int mobsPerWave, int color) {
        this.waves = waves;
        this.baseLevel = baseLevel;
        this.mobsPerWave = mobsPerWave;
        this.color = color;
    }

    public int waves() {
        return waves;
    }

    public int baseLevel() {
        return baseLevel;
    }

    public int mobsPerWave() {
        return mobsPerWave;
    }

    public int color() {
        return color;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static DungeonTier byName(String name) {
        for (DungeonTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return null;
    }
}
