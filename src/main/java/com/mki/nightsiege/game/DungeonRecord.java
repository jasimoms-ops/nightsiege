package com.mki.nightsiege.game;

/** A dungeon core the mod has placed in a structure, saved with the world. */
public class DungeonRecord {
    public static final String DORMANT = "dormant";
    public static final String AWAKE = "awake";
    public static final String BEATEN = "beaten";

    /** Key of the dungeon kind, see {@link com.mki.nightsiege.core.DungeonType}. */
    public String type = "";
    /** The structure's own id, e.g. {@code mos:ruined_castle}. */
    public String structure = "";
    public String dimension = "";
    /** Chunk the structure starts in: identifies the structure for good. */
    public int chunkX;
    public int chunkZ;
    /** Where the core block stands. */
    public int x;
    public int y;
    public int z;
    public String state = DORMANT;
    /** Game time from which a new attempt may start after a failed one. */
    public long retryAt;

    public DungeonRecord() {
    }

    public boolean sameStructure(String dim, int cx, int cz) {
        return chunkX == cx && chunkZ == cz && dimension != null && dimension.equals(dim);
    }

    public boolean at(String dim, int bx, int by, int bz) {
        return x == bx && y == by && z == bz && dimension != null && dimension.equals(dim);
    }

    public boolean beaten() {
        return BEATEN.equals(state);
    }
}
