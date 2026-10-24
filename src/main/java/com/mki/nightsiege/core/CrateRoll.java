package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/** One opened crate as the window shows it: the spinning band and what it stopped on. Travels as JSON. */
public class CrateRoll {

    /** One card of the band. */
    public static class Slot {
        public String gain = "";
        public String rarity = "";
        public String icon = "";

        public Slot() {
        }

        public Slot(String gain, String rarity, String icon) {
            this.gain = gain;
            this.rarity = rarity;
            this.icon = icon;
        }
    }

    public String clazz = "";
    public String kind = "";
    public int level;
    public int[] odds = new int[5];
    public List<Slot> band = new ArrayList<>();
    public int winIndex;

    /** The gain won, its rarity and icon. */
    public String gain = "";
    public String rarity = "";
    public String icon = "";
    /** Stage reached now, and the top (for "1/2" or "stage 2 of 4"). */
    public int stage;
    public int topStage;
    public boolean twoPulls;
    /** Already at the top: skill points instead. */
    public boolean duplicate;
    public int points;
    /** The luck crate was used up for this one. */
    public boolean lucky;
    /** The extra roll from level 20 on hit: an infernite ingot. */
    public boolean infernite;
    /** Gains this class's kit holds now, and all there are. */
    public int collected;
    public int total;
}
