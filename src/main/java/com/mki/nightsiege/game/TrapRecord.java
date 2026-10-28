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
    /** Old saves only: traps no longer break (Simon, 27 October 2026); cleared on the next tick. */
    public boolean broken;
    /** Shots left in the magazine: arrow turret, mortar and potion thrower (27 October 2026). */
    public int ammo;
    /** One of the two towers the copper golem looks after (27 October 2026). */
    public boolean golem;
    /**
     * Set up in a dungeon or at the war camp (28 October 2026): it counts only against the
     * field caps and goes when its engineer's fight there ends.
     */
    public boolean field;
    /** Shots the tower shield (crate gain) still blocks for the gunner in the seat. */
    public int shield;
    /** Damage the tower took for its gunner (Simon, 27 October 2026); 0 = whole. */
    public float hpLost;
    /** Game time until which nobody can climb into the tower after it was overrun under its gunner. */
    public transient long seatLockedUntil;
    /** Game time from which it may fire again. Not worth saving across restarts. */
    public transient long readyAt;
    /** The mortar's target spot, set with the remote (Simon, 25 October 2026); without one it does nothing. */
    public boolean aimed;
    public int aimX;
    public int aimY;
    public int aimZ;
    /** When the target spot was last set (it can be changed every 10 s). */
    public transient long aimedAt = Long.MIN_VALUE / 2;
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
