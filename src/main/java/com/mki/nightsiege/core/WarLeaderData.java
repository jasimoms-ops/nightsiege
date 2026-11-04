package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The war leader's view (Kriegsführer, Simon 29.10.2026), server to client as JSON, twice a second.
 */
public class WarLeaderData {
    /** "lobby" (ready round), "prep" (build-up), "wave", "end". */
    public String phase = "";
    public String leader = "";
    public int wave;
    public int waves;
    /** Seconds left of the phase (the build-up, the wave). */
    public int seconds;
    public int gold;
    /** The gold the next wave brings (without what comes over). */
    public int nextGold;
    public int level;
    /** The core, the reach of the view, where it starts. */
    public int centerX;
    public int centerY;
    public int centerZ;
    public int radius;
    public double startX;
    public double startZ;
    public List<Item> shop = new ArrayList<>();
    public List<Point> points = new ArrayList<>();
    public List<Target> targets = new ArrayList<>();
    public List<Squad> squads = new ArrayList<>();
    public List<Mate> defenders = new ArrayList<>();
    /** The card of this wave ("" none). */
    public String card = "";
    public int machines;
    public int machinesMax;
    /** The ready round: who is in it and ready, when the leader may go without the rest. */
    public List<Mate> lobby = new ArrayList<>();
    public int forceIn;
    public boolean canGo;
    /** "won" (the leader), "lost", "aborted"; the lines of the end panel. */
    public String result = "";
    public List<String> lines = new ArrayList<>();

    /** One thing to buy: a squad ("squad"), a machine ("machine"), a boss ("boss") or a card ("card"). */
    public static class Item {
        public String id = "";
        public String group = "";
        public int price;
        public int count;
        /** "ok", "poor" (too little gold), "level" (from a level), "wave" (from a wave), "used", "full". */
        public String state = "";
        public int from;
        /** Acts on its own (tunneler, grappler, standard): no orders. */
        public boolean ownWill;
    }

    public static class Point {
        public int index;
        public int x;
        public int y;
        public int z;
    }

    /** A gate or open gateway of the base (to aim a squad at). */
    public static class Target {
        public String kind = "";
        public int x;
        public int y;
        public int z;
    }

    public static class Squad {
        public int id;
        public String item = "";
        public int alive;
        public int total;
        /** "coming" (flare up), "gather", "target", "storm". */
        public String order = "";
        public int point;
        public double x;
        public double z;
        /** Seconds until it storms by itself (gather), or comes (coming). */
        public int seconds;
        public boolean ownWill;
    }

    public static class Mate {
        public String name = "";
        public String clazz = "";
        public double x;
        public double y;
        public double z;
        public float health;
        public float max;
        public boolean fallen;
        public boolean ready;
    }
}
