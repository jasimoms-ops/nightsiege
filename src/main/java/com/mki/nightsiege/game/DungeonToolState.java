package com.mki.nightsiege.game;

import java.util.ArrayList;
import java.util.List;

/** What the creative dungeon tool window shows: every dungeon kind and how far it is played. */
public final class DungeonToolState {

    public List<Entry> entries = new ArrayList<>();

    public static final class Entry {
        public String key;
        public String tier;
        public String theme;
        public String mod;
        /** Night fragment its dungeons pay out. */
        public String fragment;
        /** A core place was set by hand for the kind. */
        public boolean coreSet;
        /** The structure's mod is in the game. */
        public boolean installed;
        public int found;
        public int beaten;
    }
}
