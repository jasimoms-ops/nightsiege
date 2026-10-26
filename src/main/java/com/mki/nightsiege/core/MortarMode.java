package com.mki.nightsiege.core;

/**
 * The fire the demolition expert calls from the bird's-eye view (Simon, 26 October 2026).
 * Point fire is always there and has no pause; the other four are skills of their own and
 * wait 10 s after each use.
 */
public enum MortarMode {
    /** All own mortars in reach fire at the spot from now on (like X with the remote). */
    POINT("point", null),
    /** A line of shells between two clicked spots. */
    BARRAGE("barrage", "e_barrage"),
    /** Shells scattered over a wide circle. */
    AREA("area", "e_area_fire"),
    /** Smoke shells: monsters in the cloud lose their target. */
    SMOKE("smoke", "e_smoke_salvo"),
    /** Fire shells: the ground burns. */
    FIRE("fire", "e_fire_salvo");

    private final String id;
    private final String node;

    MortarMode(String id, String node) {
        this.id = id;
        this.node = node;
    }

    public String id() {
        return id;
    }

    /** The skill that opens this fire, or null for point fire. */
    public String node() {
        return node;
    }

    /** Ticks of pause after a use (Simon: 10 s for everything but point fire). */
    public int pauseTicks() {
        return node == null ? 0 : (int) Math.round(SkillValues.get(node, "pause", 200));
    }

    public boolean unlocked(PlayerSkills skills) {
        return node == null || (skills != null && skills.levelOf(node) > 0);
    }

    public static MortarMode byId(String id) {
        for (MortarMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return null;
    }
}
