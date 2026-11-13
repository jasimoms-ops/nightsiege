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
    /** The base's edge per slice around the core (Kriegsführer 3: a click on it attacks). */
    public List<Double> edge = new ArrayList<>();
    /** The card of this wave ("" none). */
    public String card = "";
    public int machines;
    public int machinesMax;
    /** The sandbox (Simon 05.11.): nobody defends, endless gold, everything open, no end. */
    public boolean sandbox;
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
        /** Tor 10: how it stands (gates are blocks, they have no life): "closed", "open", "broken"; empty from an older server. */
        public String state = "";
    }

    /** Tor 10: a gate's state for the leader's cross. An archway is always open, a door or fence gate gone is broken. */
    public static String gateState(boolean arch, boolean door, boolean open) {
        if (arch) {
            return "open";
        }
        if (!door) {
            return "broken";
        }
        return open ? "open" : "closed";
    }

    public static class Squad {
        public int id;
        public String item = "";
        public int alive;
        public int total;
        /** "coming" (flare up), "gather", "target", "storm", "free"; with an order (K1) its kind ("move", "hold", ...) or "idle". */
        public String order = "";
        public int point;
        public double x;
        public double z;
        /** Seconds until it storms by itself (gather), or comes (coming). */
        public int seconds;
        public boolean ownWill;
        /** A machine: it takes a target to shoot at (Simon 05.11.); where it aims now. */
        public boolean machine;
        public boolean aimed;
        public double aimX;
        public double aimZ;
        /** Kriegsführer 3 (K1): ordered by the leader (no will of its own), its stance, its way points. */
        public boolean commanded;
        public String stance = "aggressive";
        public List<Way> ways = new ArrayList<>();
        /** Its special order on E ("none", "ladder", "blast", ...) and the seconds until it is ready again. */
        public String special = "none";
        public int specialWait;
        /** The life of its living members together. */
        public float health;
        public float maxHealth;
        /** Where its members stand (for the rings and the frame). */
        public List<Unit> units = new ArrayList<>();
    }

    /** One order in a squad's row: what and where. */
    public static class Way {
        public String kind = "";
        public double x;
        public double z;
    }

    public static class Unit {
        public double x;
        public double y;
        public double z;
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
