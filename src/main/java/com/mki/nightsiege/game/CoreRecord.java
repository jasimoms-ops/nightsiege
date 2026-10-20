package com.mki.nightsiege.game;

/** A placed siege core the mod keeps track of. */
public class CoreRecord {
    public String dimension = "";
    public int x;
    public int y;
    public int z;

    public CoreRecord() {
    }

    public CoreRecord(String dimension, int x, int y, int z) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean matches(String dim, int bx, int by, int bz) {
        return x == bx && y == by && z == bz && dimension != null && dimension.equals(dim);
    }
}
