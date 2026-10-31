package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the Ruhmeshalle window gets from the server (screen payload "hall"). */
public class HallView {
    /** UUID of the player looking. */
    public String viewer = "";
    /** False when the statistics logger is off (then the Ruhmeshalle stands still). */
    public boolean logger = true;
    /** Every defender, the lists of monsters cut short. */
    public Map<String, HallOfFame.Hero> heroes = new LinkedHashMap<>();
    public HallOfFame.LastNight lastNight;
    public Map<String, HallOfFame.Best> curioBests = new LinkedHashMap<>();
    public Map<String, HallOfFame.Beast> bestiary = new LinkedHashMap<>();
    /** Badge → bronze, silver, gold (with the config's thresholds). */
    public Map<String, double[]> tiers = new LinkedHashMap<>();
    /** Who is online right now. */
    public List<String> online = new ArrayList<>();
}
