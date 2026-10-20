package com.mki.nightsiege.core;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether siege mobs may break a given block. Kept free of Minecraft
 * types so the rules can be tested directly.
 */
public final class BlockGuard {

    private BlockGuard() {
    }

    /**
     * @param blockId the block id, for example {@code minecraft:chest}
     * @return true when the block must stay intact
     */
    public static boolean isProtected(String blockId, SiegeConfig cfg) {
        if (blockId == null || blockId.isBlank()) {
            return true;
        }
        String id = blockId.toLowerCase(Locale.ROOT);
        List<String> exact = cfg.protectedBlocks;
        if (exact != null && exact.contains(id)) {
            return true;
        }
        List<String> keywords = cfg.protectedBlockKeywords;
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank() && id.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }
}
