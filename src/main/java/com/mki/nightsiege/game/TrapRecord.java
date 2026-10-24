package com.mki.nightsiege.game;

/** A placed trap the mod keeps track of, saved with the rest of the siege state. */
public class TrapRecord {
    public String kind = "";
    public String dimension = "";
    public int x;
    public int y;
    public int z;
    /** UUID of the engineer who placed it; their skill tier sets its strength. */
    public String owner = "";
    /** Activations left before it is used up. */
    public int uses;
    /** Arrows loaded, turret only. */
    public int ammo;
    /** Game time from which it may fire again. Not worth saving across restarts. */
    public transient long readyAt;
    /** The night in which this trap already sprang its ambush (hidden traps gain). */
    public transient int ambushNight = -1;

    public TrapRecord() {
    }

    public TrapRecord(String kind, String dimension, int x, int y, int z, String owner) {
        this.kind = kind;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.owner = owner;
    }

    public boolean matches(String dim, int bx, int by, int bz) {
        return x == bx && y == by && z == bz && dimension != null && dimension.equals(dim);
    }
}
