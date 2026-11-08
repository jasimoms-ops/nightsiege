package com.mki.nightsiege.client;

import com.mki.nightsiege.core.AbilityTip;
import com.mki.nightsiege.core.AbilityTips;
import com.mki.nightsiege.core.CoreStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The lines of an ability tooltip in the inventory (Simon 08.11.): name, key, what it does with the
 * numbers of the player's rank, cooldown and what learned skills change. Without the game's language
 * (a lookup is passed in), so the tests can check every subclass in German and English.
 */
public final class AbilityTipText {

    /** Cooldown of a crate gain that has not been used yet: unknown, so no line. */
    public static final int NO_COOLDOWN_LINE = -2;

    private AbilityTipText() {
    }

    /**
     * @param keyName the key as the player has it bound ("Q")
     * @param lang    translation lookup, null for a missing key
     */
    public static List<String> lines(AbilityTip tip, String keyName, Function<String, String> lang) {
        boolean german = ",".equals(lang.apply("nightsiege.ui.decimal"));
        List<String> out = new ArrayList<>();
        AbilityTip.Part main = tip.main;
        String tag = lang.apply(AbilityTips.KEY + "slot." + tip.slot);
        out.add("§6" + text(lang, main.name) + (tag == null ? "" : "  §8" + tag));
        String head = "§8" + tr(lang, "key", "§e" + keyName + "§8");
        if (main.level > 0 && main.maxLevel > 1) {
            head += "  ·  " + tr(lang, "level", main.level, main.maxLevel);
        }
        out.add(head);
        String desc = lang.apply(main.desc);
        if (desc != null) {
            for (String part : fill(desc, main, german).split("\n")) {
                out.add("§7" + part);
            }
        }
        if (tip.cooldownTicks > 0) {
            out.add("§8" + tr(lang, "cooldown", "§f" + SkillText.format(tip.cooldownTicks, "s", german) + " s"));
        } else if (tip.cooldownTicks == 0 || tip.cooldownTicks == -1) {
            out.add("§8" + tr(lang, "cooldown_none"));
        }
        if (tip.mods != null && !tip.mods.isEmpty()) {
            out.add("§6" + tr(lang, "mods"));
            for (AbilityTip.Part mod : tip.mods) {
                String line = "§e• " + text(lang, mod.name);
                if (mod.maxLevel > 1) {
                    line += " §8(" + mod.level + "/" + mod.maxLevel + ")";
                }
                String modDesc = lang.apply(mod.desc);
                if (modDesc != null) {
                    line += "§7: " + fill(modDesc, mod, german).replace("\n", " ");
                }
                out.add(line);
            }
        }
        if (desc != null && desc.contains("*") && main.level > 0) {
            out.add("§8" + tr(lang, "rank_hint"));
        }
        return out;
    }

    private static String fill(String raw, AbilityTip.Part part, boolean german) {
        CoreStats.NodeLine line = new CoreStats.NodeLine();
        line.amount = part.amount;
        line.maxLevel = part.maxLevel;
        line.level = part.level;
        line.values = part.values;
        return SkillText.fill(raw, line, part.level, german);
    }

    private static String text(Function<String, String> lang, String key) {
        String t = lang.apply(key);
        return t == null ? key : t;
    }

    private static String tr(Function<String, String> lang, String key, Object... args) {
        String raw = lang.apply(AbilityTips.KEY + key);
        if (raw == null) {
            return AbilityTips.KEY + key;
        }
        return args.length == 0 ? raw : String.format(Locale.ROOT, raw, args);
    }
}
