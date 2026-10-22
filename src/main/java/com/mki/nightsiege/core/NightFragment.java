package com.mki.nightsiege.core;

/**
 * Fragments from beaten dungeons. Set into the main core, a fragment makes the
 * next siege a special night of its event: harder, with double experience and
 * a chest of loot for the defenders when they hold.
 */
public enum NightFragment {
    BLOOD(SiegeEvent.BLOOD_MOON, 0xFFE0303A),
    MIST(SiegeEvent.FOG, 0xFFB8C4C8),
    FROST(SiegeEvent.FROST, 0xFF8CD8FF),
    STORM(SiegeEvent.THUNDER, 0xFF6FB8FF),
    SHADOW(SiegeEvent.ECLIPSE, 0xFFE8B84A),
    EMBER(SiegeEvent.NETHER_RIFT, 0xFFFF8030);

    private final SiegeEvent event;
    private final int color;

    NightFragment(SiegeEvent event, int color) {
        this.event = event;
        this.color = color;
    }

    /** The event the night after the fragment is set takes. */
    public SiegeEvent event() {
        return event;
    }

    public int color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static NightFragment byName(String name) {
        if (name == null) {
            return null;
        }
        for (NightFragment fragment : values()) {
            if (fragment.name().equalsIgnoreCase(name)) {
                return fragment;
            }
        }
        return null;
    }
}
