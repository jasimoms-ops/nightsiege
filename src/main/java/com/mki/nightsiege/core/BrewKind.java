package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The alchemist's potions for the zones he marks with the remote (Simon, 26 October 2026).
 * Four help the players in the cloud, three hurt the monsters.
 *
 * <p>Since 28 October 2026 (Simon: "he should be able to learn only 3") every potion is a
 * node of its own on the outer edge of the alchemist's fan; of the seven, one profile may
 * learn {@link SkillTree#brewLimit()} (three). A potion node opens behind the node its
 * potion used to need ({@link #requirement()}).</p>
 *
 * <p>No Minecraft types here: the server applies the effects ({@code AlchemyZones}), the
 * menu draws a potion in {@link #color()}.</p>
 */
public enum BrewKind {
    /** Players: one heart back every second. */
    HEAL("heal", true, 0xF82423, "minecraft:glistering_melon_slice"),
    /** Monsters: poison, the undead wither instead. */
    POISON("poison", false, 0x87A363, "minecraft:spider_eye"),
    /** Monsters: crawl through the cloud. */
    SLOW("slow", false, 0x8BAFE0, "minecraft:soul_sand"),
    /** Players: regeneration while inside. */
    REGEN("regen", true, 0xCD5CAB, "minecraft:ghast_tear"),
    /** Monsters: weakness, their blows land softer. */
    WEAK("weak", false, 0x5A6C81, "minecraft:fermented_spider_eye"),
    /** Players: strength while inside. */
    STRENGTH("strength", true, 0xFFC700, "minecraft:blaze_powder"),
    /** Players: speed while inside. */
    SPEED("speed", true, 0x33EBFF, "minecraft:sugar");

    /** Prefix of the potion nodes ("brew_heal" …). */
    public static final String NODE_PREFIX = "brew_";

    private final String id;
    private final boolean forPlayers;
    private final int color;
    private final String icon;

    BrewKind(String id, boolean forPlayers, int color, String icon) {
        this.id = id;
        this.forPlayers = forPlayers;
        this.color = color;
        this.icon = icon;
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

    /** The item drawn on the potion's node: the ingredient it is brewed from. */
    public String icon() {
        return icon;
    }

    /** The skill node that teaches this potion ("brew_heal" …). */
    public String nodeId() {
        return NODE_PREFIX + id;
    }

    public static BrewKind byId(String id) {
        for (BrewKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }

    /** The potion a node teaches, or null. */
    public static BrewKind byNode(String nodeId) {
        if (nodeId == null || !nodeId.startsWith(NODE_PREFIX)) {
            return null;
        }
        return byId(nodeId.substring(NODE_PREFIX.length()));
    }

    /**
     * What the potion's node needs, in the format of the skill tree ("id", "id:level", "a|b"
     * for either, "a,b" for both). The deep potions also need the potion thrower, so only the
     * alchemist reaches them.
     */
    public String requirement() {
        String brewer = TrapKind.BREWER.id();
        return switch (this) {
            case HEAL, POISON -> brewer;
            case SLOW -> brewer + ":2";
            case REGEN -> brewer + ":3";
            case WEAK -> "toxic_brew|e_acid";
            case STRENGTH -> brewer + ",toxic_cloud";
            case SPEED -> brewer + ",e_plague|e_acid_fog";
        };
    }

    /** True when the player has learned this potion's node. */
    public boolean unlocked(PlayerSkills skills) {
        return skills != null && skills.levelOf(nodeId()) > 0;
    }

    /**
     * The potions the player has, in the order of the menu, at most {@link SkillTree#brewLimit()}
     * of them (a creative test profile may hold all seven nodes).
     */
    public static List<BrewKind> learned(PlayerSkills skills) {
        List<BrewKind> out = new ArrayList<>();
        int limit = SkillTree.brewLimit();
        for (BrewKind kind : values()) {
            if (kind.unlocked(skills) && out.size() < limit) {
                out.add(kind);
            }
        }
        return out;
    }

    /** True when the player has this potion among the ones that count. */
    public boolean usable(PlayerSkills skills) {
        return learned(skills).contains(this);
    }

    /** The first potion the player has, or null when none is learned yet. */
    public static BrewKind firstUnlocked(PlayerSkills skills) {
        List<BrewKind> all = learned(skills);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Whether the old rules (before 28 October 2026) gave the player this potion: the node
     * its potion needed. Used once to hand an existing alchemist his three potions.
     */
    public boolean openedTheOldWay(PlayerSkills skills) {
        if (skills == null) {
            return false;
        }
        for (String all : requirement().split(",")) {
            boolean any = false;
            for (String option : all.split("\\|")) {
                String[] parts = option.split(":");
                int need = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                if (skills.levelOf(parts[0]) >= need) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }
}
