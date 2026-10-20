package com.mki.nightsiege.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the mod remembers between restarts.
 *
 * <p>Note: this lives in the config folder, so it is shared by all worlds of one
 * installation. That matches how the predecessor mod stored its data and keeps
 * the implementation simple.</p>
 */
public class SiegeSave {
    public List<BedRecord> beds = new ArrayList<>();
    public int siegesWon;
    public int siegesLost;
    /** In-game day of the last finished siege, or -1 when there was none yet. */
    public long lastSiegeDay = -1L;

    public BedRecord findBed(String dimension, int x, int y, int z) {
        for (BedRecord bed : beds) {
            if (bed != null && bed.matches(dimension, x, y, z)) {
                return bed;
            }
        }
        return null;
    }

    public void removeBed(String dimension, int x, int y, int z) {
        beds.removeIf(bed -> bed == null || bed.matches(dimension, x, y, z));
    }

    public List<BedRecord> registeredBeds(int required) {
        List<BedRecord> out = new ArrayList<>();
        for (BedRecord bed : beds) {
            if (bed != null && bed.isRegistered(required)) {
                out.add(bed);
            }
        }
        return out;
    }
}
