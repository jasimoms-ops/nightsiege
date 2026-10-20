package com.mki.nightsiege.game;

/** One bed the mod knows about, plus how often it has been slept in. */
public class BedRecord {
    public String dimension = "";
    public int x;
    public int y;
    public int z;
    /** How many separate nights a player has slept here. */
    public int sleeps;

    public BedRecord() {
    }

    public BedRecord(String dimension, int x, int y, int z) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean matches(String dim, int bx, int by, int bz) {
        return x == bx && y == by && z == bz && dimension != null && dimension.equals(dim);
    }

    public boolean isRegistered(int required) {
        return sleeps >= required;
    }
}
