package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the inventory tooltips of Q, F and X (Simon 08.11.). Every number comes from where the ability
 * reads it ({@link SkillValues}, the skill nodes, the ult's cooldown rule below), so a balance change
 * shows in the tooltip without a second place to edit. Pure logic, the tests run it for all 20 subclasses.
 */
public final class AbilityTips {

    /** Translation key prefix of everything new here. */
    public static final String KEY = "nightsiege.ability.tooltip.";

    private AbilityTips() {
    }

    // ------------------------------------------------------------------ the ultimate's cooldown

    /** Base cooldown of an ultimate at this rank (ticks): 25 s, 20 s, 15 s. */
    public static int baseUltCooldown(int rank) {
        return 600 - 100 * Math.max(1, rank);
    }

    /** The ultimate's cooldown at this rank as it fires (warriors and summoners have their own per rank). */
    public static int ultCooldown(SkillNode node, int rank) {
        return (int) Math.round(node.clazz() == SkillClass.WARRIOR || node.clazz() == SkillClass.SUMMONER
                ? SkillValues.get(node.id(), "cd" + rank, baseUltCooldown(rank)) : baseUltCooldown(rank));
    }

    // ------------------------------------------------------------------ which F the class has

    /** The guardian's heals (wheel order): hand, ward, dome, each when one of its nodes is learned. */
    public static List<String> heals(SkillTree tree, PlayerSkills skills) {
        List<String> out = new ArrayList<>();
        if (skills == null || skills.clazz() != SkillClass.GUARDIAN) {
            return out;
        }
        if (tree.totalBonus(SkillEffect.HEALING_HAND, skills) > 0) {
            out.add("hand");
        }
        if (tree.totalBonus(SkillEffect.WARD, skills) > 0) {
            out.add("ward");
        }
        if (tree.totalBonus(SkillEffect.DOME, skills) > 0) {
            out.add("dome");
        }
        return out;
    }

    /**
     * The name key of F for this player, in the order the server picks it ({@code sendUltHud}); null when
     * F does nothing for him (the soul catcher, a guardian without a heal).
     */
    public static String fName(SkillTree tree, PlayerSkills own) {
        String sub = tree.subclassOf(own);
        if (own.clazz() == SkillClass.ENGINEER) {
            return "nightsiege.abilitybar.f_remote";
        }
        if (own.clazz() == SkillClass.SUMMONER && !SkillTree.SUB_SOULCATCHER.equals(sub)) {
            return "nightsiege.abilitybar.f_staff";
        }
        if (!heals(tree, own).isEmpty()) {
            return "nightsiege.abilitybar.f_heal";
        }
        if (own.clazz() == SkillClass.ARCHER) {
            return "nightsiege.abilitybar.f_rain";
        }
        if (own.clazz() == SkillClass.WARRIOR && WarriorSpiritMath.isWarriorSub(sub)) {
            return "nightsiege.warrior_f." + WarriorSpiritMath.abilityId(sub);
        }
        return null;
    }

    // ------------------------------------------------------------------ building

    /**
     * The tooltips of the fields the player sees.
     *
     * @param fName  the F field's name key the server sends (null: no F field)
     * @param second the crate gain of the second ultimate (null: no X field)
     * @param secondCooldown its pause in ticks
     */
    public static List<AbilityTip> build(SkillTree tree, PlayerSkills own, boolean ult, String fName,
                                         String second, int secondCooldown) {
        List<AbilityTip> out = new ArrayList<>();
        if (own == null || !own.hasClass()) {
            return out;
        }
        SkillNode node = ult ? tree.ultimateOf(own) : null;
        if (node != null) {
            out.add(ultTip(tree, own, node));
        }
        if (fName != null && !fName.isEmpty()) {
            AbilityTip f = fTip(tree, own, fName);
            if (f != null) {
                out.add(f);
            }
        }
        if (second != null && !second.isEmpty()) {
            AbilityTip x = new AbilityTip();
            x.slot = "x";
            x.main.name = "nightsiege.gain." + second;
            x.main.desc = "nightsiege.gain." + second + ".desc";
            x.cooldownTicks = secondCooldown;
            out.add(x);
        }
        return out;
    }

    static AbilityTip ultTip(SkillTree tree, PlayerSkills own, SkillNode node) {
        int rank = Math.max(1, own.levelOf(node.id()));
        AbilityTip tip = new AbilityTip();
        tip.slot = "q";
        tip.main = part(node, rank);
        tip.cooldownTicks = ultCooldown(node, rank);
        // Its masteries: learned nodes that need the ultimate (w_bladestorm needs whirlwind ...).
        for (SkillNode n : tree.all()) {
            if (n != node && own.levelOf(n.id()) > 0 && needs(n.requires(), node.id())) {
                tip.mods.add(part(n, own.levelOf(n.id())));
            }
        }
        return tip;
    }

    static AbilityTip fTip(SkillTree tree, PlayerSkills own, String fName) {
        AbilityTip tip = new AbilityTip();
        tip.slot = "f";
        tip.main.name = fName;
        String sub = tree.subclassOf(own);
        Map<String, Double> v = tip.main.values;
        switch (fName) {
            case "nightsiege.abilitybar.f_remote" -> {
                tip.main.desc = KEY + "desc.remote";
                for (String id : new String[]{"e_longrange", "e_seeker", "e_circuit", "e_detonator"}) {
                    prefixed(v, id);
                }
                modsOf(tree, own, tip, "e_longrange", "e_seeker", "e_circuit", "e_overvolt", "e_fortress", "e_fieldtech",
                        "e_detonator");
            }
            case "nightsiege.abilitybar.f_staff" -> {
                tip.main.desc = KEY + "desc.staff";
                prefixed(v, "s_callrange");
                // The order's reach: the staff's (with the call range skill) and the order's extra.
                v.put("reach", SkillValues.get("s_callrange", "base", 16.0) + tree.totalBonus(SkillEffect.CALL_RANGE, own)
                        + SkillValues.get("s_callrange", "order", 16.0));
                modsOf(tree, own, tip, "s_callrange");
            }
            case "nightsiege.abilitybar.f_heal" -> {
                tip.main.desc = KEY + "desc.heal";
                for (SkillNode n : tree.all()) {
                    SkillEffect e = n.effect();
                    if ((e == SkillEffect.HEALING_HAND || e == SkillEffect.WARD || e == SkillEffect.DOME)
                            && n.clazz() == SkillClass.GUARDIAN && own.levelOf(n.id()) > 0) {
                        tip.mods.add(part(n, own.levelOf(n.id())));
                    }
                }
            }
            case "nightsiege.abilitybar.f_rain" -> {
                String k = sub == null || sub.isEmpty() ? SkillTree.SUB_SWIFT : sub;
                tip.main.desc = KEY + "desc.rain_" + k;
                v.put("dur", SkillValues.get("arrow_rain", "dur", 60));
                v.put("range", SkillValues.get("arrow_rain", "range", 32.0));
                v.put("radius", SkillValues.get("arrow_rain", "radius_" + k, 4.0));
                v.put("every", SkillValues.get("arrow_rain", "every_" + k, 10));
                v.put("dmg", SkillValues.get("arrow_rain", "dmg_" + k, 0.5) * SkillValues.get("arrow_rain", "mult", 1.0));
                v.put("slow", SkillValues.get("arrow_rain", "slow_" + k, 0));
                v.put("push", SkillValues.get("arrow_rain", "push_" + k, 0.0));
                tip.cooldownTicks = (int) (Math.max(1, SkillValues.get("arrow_rain", "dur", 60))
                        + Math.max(0, SkillValues.get("arrow_rain", "pause", 160)));
            }
            default -> {
                if (!fName.startsWith("nightsiege.warrior_f.")) {
                    return null;
                }
                // The warrior's F: the numbers sit on the subclass node, the cost on the meter (w_spirit).
                tip.main.desc = KEY + "desc." + fName.substring("nightsiege.warrior_f.".length());
                v.putAll(SkillValues.of(WarriorSpiritMath.nodeOf(sub)));
                for (Map.Entry<String, Double> e : SkillValues.of(WarriorSpiritMath.NODE).entrySet()) {
                    v.putIfAbsent("sp_" + e.getKey(), e.getValue());
                }
                v.put("sp_cost", WarriorSpiritMath.abilityCost());
                tip.cooldownTicks = WarriorSpiritMath.abilityCooldown(sub);
            }
        }
        return tip;
    }

    // ------------------------------------------------------------------ helpers

    private static AbilityTip.Part part(SkillNode node, int level) {
        AbilityTip.Part p = new AbilityTip.Part();
        p.name = "nightsiege.skill." + node.id();
        p.desc = "nightsiege.skill." + node.id() + ".desc";
        p.amount = node.amountPerLevel();
        p.maxLevel = node.maxLevel();
        p.level = level;
        p.values = SkillValues.of(node.id());
        return p;
    }

    private static void prefixed(Map<String, Double> into, String node) {
        for (Map.Entry<String, Double> e : SkillValues.of(node).entrySet()) {
            into.put(node + "_" + e.getKey(), e.getValue());
        }
    }

    private static void modsOf(SkillTree tree, PlayerSkills own, AbilityTip tip, String... ids) {
        for (String id : ids) {
            SkillNode n = tree.get(id);
            if (n != null && own.levelOf(id) > 0) {
                tip.mods.add(part(n, own.levelOf(id)));
            }
        }
    }

    /** True when a requirement expression ("a,b", "a|b", "g_ward:2") names this node. */
    static boolean needs(String requires, String id) {
        if (requires == null || requires.isEmpty()) {
            return false;
        }
        for (String term : requires.split("[,|&+ ()]")) {
            int colon = term.indexOf(':');
            String name = (colon >= 0 ? term.substring(0, colon) : term).trim().toLowerCase(Locale.ROOT);
            if (name.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
