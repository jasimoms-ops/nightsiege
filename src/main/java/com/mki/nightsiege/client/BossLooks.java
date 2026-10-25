package com.mki.nightsiege.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Which monsters wear a boss look (Simon's boss nights, 25 October 2026): the server tells
 * the network ids of the boss monsters and their look; the renderer swaps their textures for
 * the boss folder's (see {@link EventTextures#renderingBoss}).
 */
public final class BossLooks {

    private static final Map<Integer, String> LOOKS = new HashMap<>();

    private BossLooks() {
    }

    /** What the server sends. */
    public static final class Data {
        public Map<String, String> looks;
    }

    /** The server's list: {"looks": {"123": "grave_king"}} (an empty list when the fight is over). */
    public static void set(String json) {
        LOOKS.clear();
        try {
            Data data = com.mki.nightsiege.game.Json.fromJson(json, Data.class);
            if (data != null && data.looks != null) {
                for (Map.Entry<String, String> e : data.looks.entrySet()) {
                    LOOKS.put(Integer.parseInt(e.getKey()), e.getValue());
                }
            }
        } catch (RuntimeException e) {
            LOOKS.clear();
        }
    }

    public static void clear() {
        LOOKS.clear();
    }

    /** The boss look of the monster with this network id, or null. */
    public static String of(int entityId) {
        return LOOKS.isEmpty() ? null : LOOKS.get(entityId);
    }
}
