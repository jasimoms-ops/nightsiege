package com.mki.nightsiege.core;

/** The numbers behind dungeons, kept apart from Minecraft so they can be tested. */
public final class DungeonMath {

    /** Share of matching structures that hide a core: two in five. */
    public static final int DUNGEON_IN = 2;
    public static final int DUNGEON_OF = 5;
    /** A player this close wakes a dormant core. */
    public static final double WAKE_RADIUS = 24.0;
    /** Players this close when the fight starts take part. */
    public static final double FIGHT_RADIUS = 32.0;
    /** Defenders further than this count as fled. */
    public static final double LEAVE_RADIUS = 48.0;
    /** Wait after a failed attempt: five minutes. */
    public static final long RETRY_TICKS = 6000L;
    /** A fight that takes longer than ten minutes counts as lost. */
    public static final long MAX_FIGHT_TICKS = 12000L;
    /** Experience points spent per extra level of difficulty. */
    public static final int POINTS_PER_LEVEL = 25;
    /** Most extra levels the players' progress can add. */
    public static final int MAX_PROGRESS_LEVELS = 6;

    private DungeonMath() {
    }

    /**
     * Whether this structure hides a core. Depends only on the world seed, the
     * structure and where it starts, so it is the same every time it is asked.
     */
    public static boolean isDungeon(long seed, String structureId, int chunkX, int chunkZ) {
        return isDungeon(seed, structureId, chunkX, chunkZ, DUNGEON_IN / (double) DUNGEON_OF);
    }

    /** As above, with the share of structures that hide a core (1 = every one). */
    public static boolean isDungeon(long seed, String structureId, int chunkX, int chunkZ, double chance) {
        if (chance >= 1.0) {
            return true;
        }
        long h = seed ^ 0x5DEECE66DL;
        h = h * 31 + (structureId == null ? 0 : structureId.hashCode());
        h = h * 31 + chunkX;
        h = h * 31 + chunkZ;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return Math.floorMod(h, 1000) < Math.round(Math.max(0.0, chance) * 1000);
    }

    /** Level the dungeon's mobs are scaled to: the tier's base plus the players' progress. */
    public static int level(DungeonTier tier, int averageSpentPoints) {
        int extra = Math.min(MAX_PROGRESS_LEVELS, Math.max(0, averageSpentPoints) / POINTS_PER_LEVEL);
        return Math.min(20, tier.baseLevel() + extra);
    }

    /**
     * Guards in the rooms of a dungeon: a bit more than one wave, more in big
     * structures (one per eight pieces, at most twenty more) and with more players.
     */
    public static int guards(DungeonTier tier, int players, int pieces) {
        double scale = 1.0 + 0.4 * Math.max(0, players - 1);
        int base = tier.mobsPerWave() + 2 + Math.min(20, Math.max(0, pieces) / 8);
        return Math.max(2, (int) Math.round(base * scale));
    }

    /** Mobs in one wave: the tier's size, half again for every extra player. */
    public static int mobsPerWave(DungeonTier tier, int players) {
        double scale = 1.0 + 0.5 * Math.max(0, players - 1);
        return Math.max(1, (int) Math.round(tier.mobsPerWave() * scale));
    }
}
