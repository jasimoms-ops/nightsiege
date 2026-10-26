package com.mki.nightsiege.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Named numbers of a skill that are not its amount per level: durations, radii,
 * cooldowns, fixed multipliers (balance update, 26 October 2026).
 *
 * <p>Handlers read them here instead of writing the number into the code, the
 * skill texts show them through placeholders, and the server can change each one
 * in {@code skillTweaks.<id>.values.<key>}. So a text can never again show a
 * number the game does not use.</p>
 */
public final class SkillValues {

    private static final Map<String, Map<String, Double>> DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Double>> live = new LinkedHashMap<>();

    static {
        // One file per class, so each class's numbers stay together (and edits don't collide).
        SkillValuesArcher.register();
        SkillValuesWarrior.register();
        SkillValuesGuardian.register();
        SkillValuesEngineer.register();
        SkillValuesSummoner.register();
    }

    private SkillValues() {
    }

    /** Declares a built-in value; called from the per-class files (SkillValuesArcher …). */
    public static double def(String node, String key, double value) {
        DEFAULTS.computeIfAbsent(node, k -> new LinkedHashMap<>()).putIfAbsent(key, value);
        return value;
    }

    /** The value in use (server tweak or built-in), or the fallback when it was never declared. */
    public static double get(String node, String key, double fallback) {
        Map<String, Double> over = live.get(node);
        if (over != null && over.containsKey(key)) {
            return over.get(key);
        }
        Map<String, Double> base = DEFAULTS.get(node);
        if (base != null && base.containsKey(key)) {
            return base.get(key);
        }
        def(node, key, fallback);
        return fallback;
    }

    /** All values of one node as used now, for the skill window. */
    public static Map<String, Double> of(String node) {
        Map<String, Double> out = new LinkedHashMap<>();
        Map<String, Double> base = DEFAULTS.get(node);
        if (base != null) {
            out.putAll(base);
        }
        Map<String, Double> over = live.get(node);
        if (over != null) {
            out.putAll(over);
        }
        return out;
    }

    /** Lays the server's values over the built-in ones; returns how many were changed. */
    public static int apply(Map<String, SiegeConfig.SkillTweak> tweaks) {
        live.clear();
        int changed = 0;
        if (tweaks == null) {
            return 0;
        }
        for (Map.Entry<String, SiegeConfig.SkillTweak> entry : tweaks.entrySet()) {
            SiegeConfig.SkillTweak t = entry.getValue();
            if (t == null || t.values == null || t.values.isEmpty()) {
                continue;
            }
            Map<String, Double> map = new LinkedHashMap<>();
            for (Map.Entry<String, Double> v : t.values.entrySet()) {
                if (v.getKey() != null && v.getValue() != null && Double.isFinite(v.getValue())) {
                    map.put(v.getKey(), v.getValue());
                    changed++;
                }
            }
            live.put(entry.getKey(), map);
        }
        return changed;
    }
}
