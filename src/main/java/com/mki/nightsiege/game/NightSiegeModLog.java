package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;

/** Tiny wrapper so decorative code can log without pulling in the logger. */
final class NightSiegeModLog {

    private NightSiegeModLog() {
    }

    static void warn(String message) {
        NightSiegeMod.LOGGER.warn("[Night Siege] {}", message);
    }
}
