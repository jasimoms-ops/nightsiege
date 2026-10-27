package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The engineer's tower menu (Simon, 27 October 2026): every own trap and tower with its state;
 * a tower that is empty or broken is reloaded or repaired with a code of arrow keys (like the
 * stratagems in Helldivers), then the work takes a few seconds. Server to client, as JSON.
 */
public class TowerMenuData {
    /** True: open the menu; false: only refresh it when it is open. */
    public boolean open;
    /** Blocks the remote reaches: towers further away show greyed out. */
    public int range;
    public int fixSeconds;
    public int reloadArrows = 4;
    public int repairArrows = 6;
    /** The player has the copper golem (crate gain): towers can be given to it. */
    public boolean golem;
    public int golemMax = 2;
    public List<Row> rows = new ArrayList<>();

    public static class Row {
        public String kind = "";
        public int x;
        public int y;
        public int z;
        public int dist;
        public boolean inRange;
        public boolean broken;
        /** Shots left and the full magazine (0: the tower needs none). */
        public int ammo;
        public int mag;
        /** Floor traps: springs left before they are worn (0/0 for towers). */
        public int uses;
        public int fullUses;
        /** Ticks until a repair or reload under way is done (0: none). */
        public int working;
        /** Looked after by the copper golem. */
        public boolean golem;
        /** "repair", "reload" or "". */
        public String need = "";
    }
}
