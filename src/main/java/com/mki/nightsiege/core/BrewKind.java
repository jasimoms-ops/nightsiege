package com.mki.nightsiege.core;

/**
 * The alchemist's potions for the zones he marks with the remote (Simon, 26 October 2026).
 * Four help the players in the cloud, three hurt the monsters. Each one opens with a node
 * of the alchemist's half of the chemistry tree, so the menu grows with the tree.
 *
 * <p>No Minecraft types here: the server applies the effects ({@code AlchemyZones}), the
 * menu draws a potion in {@link #color()}.</p>
 */
public enum BrewKind {
    /** Players: one heart back every second. */
    HEAL("heal", true, 0xF82423),
    /** Monsters: poison, the undead wither instead. */
    POISON("poison", false, 0x87A363),
    /** Monsters: crawl through the cloud. */
    SLOW("slow", false, 0x8BAFE0),
    /** Players: regeneration while inside. */
    REGEN("regen", true, 0xCD5CAB),
    /** Monsters: weakness, their blows land softer. */
    WEAK("weak", false, 0x5A6C81),
    /** Players: strength while inside. */
    STRENGTH("strength", true, 0xFFC700),
    /** Players: speed while inside. */
    SPEED("speed", true, 0x33EBFF);

    private final String id;
    private final boolean forPlayers;
    private final int color;

    BrewKind(String id, boolean forPlayers, int color) {
        this.id = id;
        this.forPlayers = forPlayers;
        this.color = color;
    }

    public String id() {
        return id;
    }

    /** True for the potions that help players; the others only touch monsters. */
    public boolean forPlayers() {
        return forPlayers;
    }

    /** Colour of the potion and its cloud (RGB). */
    public int color() {
        return color;
    }

    public static BrewKind byId(String id) {
        for (BrewKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * The node (and level) that opens this potion, as "id" or "id:level"; several
     * alternatives are split by "|". Shown in the menu next to a locked potion.
     */
    public String requirement() {
        String brewer = TrapKind.BREWER.id();
        return switch (this) {
            case HEAL, POISON -> brewer;
            case SLOW -> brewer + ":2";
            case REGEN -> brewer + ":3";
            case WEAK -> "toxic_brew|e_acid";
            case STRENGTH -> "toxic_cloud";
            case SPEED -> "e_plague|e_acid_fog";
        };
    }

    /** True when the player's learned nodes open this potion. */
    public boolean unlocked(PlayerSkills skills) {
        if (skills == null) {
            return false;
        }
        for (String option : requirement().split("\\|")) {
            String[] parts = option.split(":");
            int need = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            if (skills.levelOf(parts[0]) >= need) {
                return true;
            }
        }
        return false;
    }

    /** The first potion the player has, or null when none is open yet. */
    public static BrewKind firstUnlocked(PlayerSkills skills) {
        for (BrewKind kind : values()) {
            if (kind.unlocked(skills)) {
                return kind;
            }
        }
        return null;
    }
}
