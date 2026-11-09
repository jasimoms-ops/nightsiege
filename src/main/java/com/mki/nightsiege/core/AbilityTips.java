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
            return null;   // Tor 4b: no remote; places 2 and 3 are the subclass's own abilities (barTips)
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
        out.addAll(barTips(tree, own));   // Tor 3.4: every ability on a key of its own
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

    // ------------------------------------------------------------------ the abilities on Q, F, Y, sneak + Y

    /** Tor 4b: the skills (the remote's 13 old nodes) that change each engineer ability (qa/tor-4b/skillbaeume.md). */
    public static final Map<String, String[]> ENGINEER_MODS = Map.of(
            "pierce", new String[]{"e_battery", "e_fastcharge", "e_backup", "e_longrange", "e_overvolt", "e_seeker", "e_fieldtech", "e_fortress"},
            "suppress", new String[]{"e_battery", "e_fastcharge", "e_backup", "e_circuit", "e_network", "e_fieldtech", "e_fortress"},
            "chain", new String[]{"e_battery", "e_fastcharge", "e_backup", "e_longrange", "e_overvolt", "e_seeker", "e_beacon", "e_fieldtech", "e_fortress"},
            "static", new String[]{"e_battery", "e_fastcharge", "e_backup", "e_longrange", "e_circuit", "e_network", "e_fieldtech", "e_fortress"},
            "grenade", new String[]{"e_battery", "e_longrange", "e_detonator", "e_targetblast"},
            "target", new String[]{"e_battery", "e_longrange", "e_detonator", "e_targetblast"},
            "boil", new String[]{"e_battery", "e_detonator"});

    /** Ticks between two dashes of a warrior (Simon: every 10 s); WarriorDash fires on it. */
    public static final int DASH_PAUSE = 200;

    /**
     * Tor 3.4: a tooltip for every ability of the subclass that has a key in {@link AbilityKeys} (the
     * F field has its own, {@link #fTip}); {@link AbilityTip#slot} is the ability id of the bar's box.
     */
    public static List<AbilityTip> barTips(SkillTree tree, PlayerSkills own) {
        List<AbilityTip> out = new ArrayList<>();
        if (own == null || !own.hasClass()) {
            return out;
        }
        List<AbilityKeys.Entry> entries = AbilityKeys.table().get(tree.subclassOf(own));
        if (entries == null) {
            return out;
        }
        for (AbilityKeys.Entry e : entries) {
            if (AbilityKeys.F_ACTION.equals(e.id())) {
                continue;
            }
            AbilityTip tip = barTip(tree, own, e.id());
            if (tip != null) {
                tip.key = e.key().name();
                out.add(tip);
            }
        }
        return out;
    }

    /** One ability of the bar: name, text with the numbers it reads, its pause, the skills that change it. */
    static AbilityTip barTip(SkillTree tree, PlayerSkills own, String id) {
        AbilityTip tip = new AbilityTip();
        tip.slot = id;
        switch (id) {
            case AbilityKeys.SHIELD -> {
                fromNode(tree, own, tip, "g_shieldthrow", "nightsiege.ability.shield_throw", null);
                tip.cooldownTicks = ticks("g_shieldthrow", "cd", 160);
            }
            case "hand" -> {
                fromNode(tree, own, tip, "g_healing_hand", "nightsiege.heal.hand", null);
                tip.cooldownTicks = ticks("g_healing_hand", "cd", 120);
            }
            case "selfheal" -> {
                fromNode(tree, own, tip, "g_healing_hand", "nightsiege.heal.self", KEY + "desc.selfheal");
                tip.cooldownTicks = ticks("g_healing_hand", "cd", 120);
            }
            case TankHeal.ID -> {
                plain(tip, "nightsiege.ability.tankheal", "tankheal", TankHeal.ID);
                tip.cooldownTicks = TankHeal.cooldownTicks();
            }
            case "ward" -> {
                String node = own.levelOf("g_ward") > 0 ? "g_ward" : "s_cross_ward";
                fromNode(tree, own, tip, node, "nightsiege.heal.ward", null);
                tip.cooldownTicks = ticks(node, "interval", 160);
            }
            case "dome" -> {
                fromNode(tree, own, tip, "g_dome", "nightsiege.heal.dome", null);
                tip.cooldownTicks = ticks("g_dome", "cd", 500);
            }
            case "acid" -> {
                fromNode(tree, own, tip, "e_acid_flask", "nightsiege.ability.acid", null);
                tip.cooldownTicks = ticks("e_acid_flask", "pause", 240);
            }
            case "escape" -> {
                plain(tip, "nightsiege.ability.escape", "escape", "e_escape");
                tip.cooldownTicks = ticks("e_escape", "pause", 300);
            }
            // Tor 4b (Variante B): the engineers' own abilities; the remote's old skills improve them
            case "pierce", "suppress", "chain", "static", "grenade", "target", "boil" -> {
                plain(tip, "nightsiege.ability." + id, id, "t_" + id);
                tip.cooldownTicks = ticks("t_" + id, "pause", 200);
                modsOf(tree, own, tip, ENGINEER_MODS.getOrDefault(id, new String[0]));
            }
            case "dash" -> {
                plain(tip, "nightsiege.ability.dash", "dash", null);
                tip.cooldownTicks = DASH_PAUSE;
            }
            case "quiver" -> plain(tip, "nightsiege.wheel.quiver", "quiver", null);
            case "brew" -> {
                plain(tip, "nightsiege.wheel.brews", "brew", "potion_tower");
                tip.cooldownTicks = ticks("potion_tower", "zonepause", 400);
            }
            case "call" -> {
                plain(tip, "nightsiege.ability.call", "call", null);
                tip.main.values.put("reach", SkillValues.get("s_callrange", "base", 16.0) + tree.totalBonus(SkillEffect.CALL_RANGE, own));
                modsOf(tree, own, tip, "s_callrange");
            }
            case "raise", "wall", "curse" -> plain(tip, "nightsiege.spell." + id, id, "d_necro");
            case "swarm", "fear" -> {
                plain(tip, "nightsiege.spell." + id, id, "d_soulcatcher");
                if ("swarm".equals(id)) {
                    double cd = SkillValues.get("d_soulcatcher", "swarmcd", 120);
                    if (SkillTree.has(own, "d_haste")) {
                        cd -= tree.totalBonus(SkillEffect.GHOST_HASTE, own) * 20.0;   // as Companions.spellCooldown
                    }
                    tip.cooldownTicks = (int) Math.max(20, Math.round(cd));
                }
            }
            default -> {
                return null;
            }
        }
        return tip;
    }

    /** The ability is a skill node: its text and numbers at the player's rank, the skills that need it. */
    private static void fromNode(SkillTree tree, PlayerSkills own, AbilityTip tip, String nodeId, String name, String desc) {
        SkillNode node = tree.get(nodeId);
        if (node == null) {
            plain(tip, name, nodeId, nodeId);
            return;
        }
        tip.main = part(node, Math.max(1, own.levelOf(nodeId)));
        tip.main.name = name;
        if (desc != null) {
            tip.main.desc = desc;
        }
        for (SkillNode n : tree.all()) {
            if (n != node && own.levelOf(n.id()) > 0 && needs(n.requires(), nodeId)) {
                tip.mods.add(part(n, own.levelOf(n.id())));
            }
        }
    }

    /** An ability without a node of its own: its text and the numbers of {@code values} (null: none). */
    private static void plain(AbilityTip tip, String name, String desc, String values) {
        tip.main.name = name;
        tip.main.desc = KEY + "desc." + desc;
        if (values != null) {
            tip.main.values.putAll(SkillValues.of(values));
        }
    }

    private static int ticks(String node, String key, double fallback) {
        return (int) Math.round(SkillValues.get(node, key, fallback));
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
