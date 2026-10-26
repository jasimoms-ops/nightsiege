package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/** What the bird's-eye view needs (server to client, as JSON). */
public class SpotterData {
    /** The centre the view must stay near (the core) and how far it may go. */
    public int centerX;
    public int centerY;
    public int centerZ;
    public int radius;
    /** Where the view starts. */
    public double startX;
    public double startZ;
    /** Own mortars standing. */
    public int mortars;
    public List<Mode> modes = new ArrayList<>();

    public static class Mode {
        public String id = "";
        public boolean open;
        /** Seconds until it may be used again. */
        public int wait;
        public int pause;
        /** Circle the shells land in, for the preview. */
        public double spread;
    }
}
