package com.mki.nightsiege.core;

/**
 * The warrior's Kampfgeist and combo chain for the client (Simon 05.11.): sent as JSON on every hit,
 * kill and finisher and while the meter falls, so the bar and the dots under the crosshair keep up
 * (the kit HUD's own state only comes once a second).
 */
public class WarriorHudData {
    /** The meter now, its top, where it glows and what F costs. */
    public float v;
    public float max = 100f;
    public float glow = 50f;
    public float cost = 50f;
    /** Hits of the running chain (0 none), the hits of a whole chain and the ticks until it breaks. */
    public int chain;
    public int hits = 4;
    public int window = 40;
    /** Kombo 2: ticks the finisher waits once only it is left, and ticks left now until the chain breaks. */
    public int ready = 120;
    public int left;
    /** Finishers so far (the client flashes the dots when it grows) and whether the last was empowered. */
    public int fin;
    public boolean finEmp;
    /** The next finisher will be empowered (the meter is full). */
    public boolean emp;
    /** F cannot be paid for now (too little Kampfgeist): its field stays grey. */
    public boolean dim;
}
