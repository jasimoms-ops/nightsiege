package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/** What the alchemist's potion menu shows (server to client, as JSON). */
public class BrewMenuData {
    /** Id of the potion thrown now, "" when none is open. */
    public String selected = "";
    /** Seconds until the next zone may be thrown. */
    public int cooldown;
    /** How long a cloud lasts, the pause after a throw, and the reach, for the texts. */
    public int zoneSeconds;
    public int pauseSeconds;
    public int reach;
    public List<Entry> brews = new ArrayList<>();

    public static class Entry {
        public String id = "";
        public boolean open;
        /** The node that opens it ("id", "id:level", alternatives split by "|"). */
        public String need = "";
    }
}
