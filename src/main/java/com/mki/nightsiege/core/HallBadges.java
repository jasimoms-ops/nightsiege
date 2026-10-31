package com.mki.nightsiege.core;

import java.util.List;
import java.util.Map;

/**
 * The badges of the Ruhmeshalle (Simon 30.10., 23:47: "die Abzeichen-Idee ist sehr cool"): bronze,
 * silver and gold for what a defender has done; every gold one gives a title. The thresholds can be
 * changed in the config ({@code hallBadgeTiers}: badge → "bronze/silver/gold").
 */
public final class HallBadges {

    /** A badge: its id (lang key nightsiege.hall.badge.&lt;id&gt;), its icon and its three thresholds. */
    public record Badge(String id, String icon, double bronze, double silver, double gold) {
    }

    public static final List<Badge> ALL = List.of(
            new Badge("schlaechter", "minecraft:iron_sword", 250, 1000, 5000),
            new Badge("bossjaeger", "minecraft:wither_skeleton_skull", 1, 10, 40),
            new Badge("bollwerk", "minecraft:shield", 5000, 25000, 100000),
            new Badge("feldscher", "minecraft:golden_apple", 1000, 5000, 20000),
            new Badge("unverwuestlich", "minecraft:totem_of_undying", 1, 10, 25),
            new Badge("treu", "minecraft:clock", 10, 25, 50),
            new Badge("siegesserie", "minecraft:white_banner", 3, 7, 15),
            new Badge("meisterschuetze", "minecraft:spectral_arrow", 30, 45, 60),
            new Badge("blutrausch", "minecraft:redstone", 5, 8, 12),
            new Badge("letzter_mann", "minecraft:skeleton_skull", 1, 3, 10),
            new Badge("retter", "minecraft:glistering_melon_slice", 5, 25, 100),
            new Badge("drohnenpilot", "minecraft:phantom_membrane", 500, 2500, 10000),
            new Badge("baumeister", "minecraft:dispenser", 100, 500, 2000),
            new Badge("dungeonmeister", "minecraft:trial_key", 1, 5, 20),
            new Badge("lagerbrecher", "minecraft:campfire", 1, 5, 15),
            new Badge("nemesis_jaeger", "minecraft:target", 1, 5, 15),
            new Badge("fluchbrecher", "minecraft:crying_obsidian", 1, 2, 3),
            new Badge("sammler", "minecraft:chest", 25, 100, 250),
            new Badge("vielseitig", "minecraft:armor_stand", 3, 6, 10));

    private HallBadges() {
    }

    public static Badge byId(String id) {
        for (Badge b : ALL) {
            if (b.id().equals(id)) {
                return b;
            }
        }
        return null;
    }

    /** The three thresholds of a badge, from the config when it has them ("250/1000/5000"). */
    public static double[] tiers(Badge b, Map<String, String> overrides) {
        double[] t = {b.bronze(), b.silver(), b.gold()};
        String o = overrides == null ? null : overrides.get(b.id());
        if (o != null) {
            String[] parts = o.split("/");
            for (int i = 0; i < Math.min(3, parts.length); i++) {
                try {
                    t[i] = Math.max(0.0, Double.parseDouble(parts[i].trim().replace(',', '.')));
                } catch (NumberFormatException ignored) {
                    // a typo in the config keeps the standard
                }
            }
        }
        return t;
    }

    /** What a hero has towards a badge. */
    public static double value(String id, HallOfFame.Hero h) {
        return switch (id) {
            case "schlaechter" -> h.kills;
            case "bossjaeger" -> h.bosses;
            case "bollwerk" -> h.intercepted;
            case "feldscher" -> h.healed;
            case "unverwuestlich" -> h.nightsNoDeath;
            case "treu" -> h.nights;
            case "siegesserie" -> h.bestStreak;
            case "meisterschuetze" -> h.curios.getOrDefault("weitesterTreffer", 0.0);
            case "blutrausch" -> h.curios.getOrDefault("blutrausch", 0.0);
            case "letzter_mann" -> h.lastStandingHeld;
            case "retter" -> h.curios.getOrDefault("retter", 0.0);
            case "drohnenpilot" -> h.droneDamage;
            case "baumeister" -> h.towerKills;
            case "dungeonmeister" -> h.dungeonsWon;
            case "lagerbrecher" -> h.campsDestroyed;
            case "nemesis_jaeger" -> h.curios.getOrDefault("nemesis", 0.0);
            case "fluchbrecher" -> h.maxCurses;
            case "sammler" -> h.crates;
            case "vielseitig" -> h.subs.size();
            default -> 0.0;
        };
    }

    /** 0 none, 1 bronze, 2 silver, 3 gold. */
    public static int tier(double value, double[] tiers) {
        int t = 0;
        for (int i = 0; i < 3; i++) {
            if (tiers[i] > 0 && value + 1e-9 >= tiers[i]) {
                t = i + 1;
            }
        }
        return t;
    }

    /**
     * Brings a hero's badges up to date. Returns the badges that went up, as "id:tier" (the caller
     * tells the player, and everybody for gold).
     */
    public static List<String> update(HallOfFame.Hero h, Map<String, String> overrides) {
        List<String> up = new java.util.ArrayList<>();
        for (Badge b : ALL) {
            int now = tier(value(b.id(), h), tiers(b, overrides));
            int before = h.badges.getOrDefault(b.id(), 0);
            if (now > before) {
                h.badges.put(b.id(), now);
                up.add(b.id() + ":" + now);
            }
        }
        return up;
    }
}
