package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SkillState;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the mod remembers between restarts.
 *
 * <p>Note: this lives in the config folder, so it is shared by all worlds of one
 * installation.</p>
 */
public class SiegeSave {
    public List<CoreRecord> cores = new ArrayList<>();
    /** True once the free starter core has been handed out in this world. */
    public boolean starterCoreGiven;

    public int siegesWon;
    public int siegesLost;

    /** Countdown state, so a restart does not reset the clock. */
    public long readyTicks;
    public long requiredTicks;


    /** One shared pot of points and learned levels for the whole server. */
    public SkillState skills = new SkillState();

    public CoreRecord findCore(String dimension, int x, int y, int z) {
        if (cores == null) {
            return null;
        }
        for (CoreRecord core : cores) {
            if (core != null && core.matches(dimension, x, y, z)) {
                return core;
            }
        }
        return null;
    }

    public void removeCore(String dimension, int x, int y, int z) {
        if (cores != null) {
            cores.removeIf(core -> core == null || core.matches(dimension, x, y, z));
        }
    }

    public SkillState skills() {
        if (skills == null) {
            skills = new SkillState();
        }
        return skills;
    }

    /** Wipes every player's skill progress. Used when a lost siege resets the core. */
    public void resetAllSkills() {
        skills().reset();
    }
}
