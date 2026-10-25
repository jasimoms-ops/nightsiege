package com.mki.nightsiege.core;

import java.util.Locale;

/**
 * The bosses of the boss nights (Simon, 25 October 2026): every 6th siege ends with one of
 * them, the night is only won when it falls. Each is a vanilla monster made big, with its
 * own look and abilities (see BossService).
 */
public enum BossKind {
    /** A giant zombie with a crown: stomps, raises zombies, leaps over walls, rages below half. */
    GRAVE_KING("grave_king", "minecraft:zombie", 2.4, 360.0, 1.6),
    /** A giant spider: climbs any wall, webs players in place, lays egg sacs. */
    SPIDER_QUEEN("spider_queen", "minecraft:spider", 2.5, 300.0, 1.5),
    /** A floating skeleton: arrow rain, a soul shield held up by three crystals, blinks away. */
    BONE_LICH("bone_lich", "minecraft:skeleton", 2.0, 240.0, 1.3),
    /** A ravager with the warlord on its back: charges, the war banner, squads; then on foot. */
    WARLORD("warlord", "minecraft:ravager", 1.5, 200.0, 1.3);

    /** The warlord himself, who rides the ravager. */
    public static final String WARLORD_RIDER = "minecraft:vindicator";
    /** Health of the warlord on foot (the second half of his bar), before level and players. */
    public static final double WARLORD_RIDER_HEALTH = 180.0;

    private final String id;
    private final String entityId;
    private final double scale;
    private final double baseHealth;
    private final double damage;

    BossKind(String id, String entityId, double scale, double baseHealth, double damage) {
        this.id = id;
        this.entityId = entityId;
        this.scale = scale;
        this.baseHealth = baseHealth;
        this.damage = damage;
    }

    /** Short name for commands, the save and the textures (grave_king …). */
    public String id() {
        return id;
    }

    /** The vanilla monster the boss is made of. */
    public String entityId() {
        return entityId;
    }

    /** How much bigger than the vanilla monster. */
    public double scale() {
        return scale;
    }

    /** Health before the night's level and the number of defenders. */
    public double baseHealth() {
        return baseHealth;
    }

    /** Factor on the damage of its blows, on top of the night's level. */
    public double damage() {
        return damage;
    }

    /** The boss with this short name (or enum name), or null. */
    public static BossKind byId(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (BossKind kind : values()) {
            if (kind.id.equals(key) || kind.name().toLowerCase(Locale.ROOT).equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
