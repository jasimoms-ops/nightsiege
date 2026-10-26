package com.mki.nightsiege.client;

import com.mki.nightsiege.core.CoreStats;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the numbers into a skill text (balance update, 26 October 2026).
 *
 * <p>Placeholders in the language files take their numbers from the node the server
 * sent, so a text always shows what the game really uses:</p>
 * <ul>
 *   <li>{@code {a}} amount per level, {@code {m}} amount at the top level</li>
 *   <li>{@code {name}} a named value of the skill ({@code SkillValues}); {@code {name*}} joins
 *       {@code name1 / name2 / name3} (one value per rank, e.g. of an ultimate)</li>
 *   <li>a format after {@code |}: {@code %} times 100 with a percent sign, {@code h} in hearts
 *       (half the health points), {@code s} ticks in seconds, {@code x} as a factor "×1,5",
 *       {@code +} with a sign</li>
 * </ul>
 * Example: {@code "+{a|h} Herz pro Stufe (höchstens +{m|h})"}.
 */
public final class SkillText {

    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)(\\*)?(?:\\|([%hsx+]+))?}");

    private SkillText() {
    }

    public static String fill(String raw, CoreStats.NodeLine node) {
        if (raw == null || raw.indexOf('{') < 0 || node == null) {
            return raw;
        }
        Matcher m = TOKEN.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            boolean ranks = m.group(2) != null;
            String fmt = m.group(3) == null ? "" : m.group(3);
            String text;
            if (ranks) {
                StringBuilder sb = new StringBuilder();
                for (int r = 1; r <= 9; r++) {
                    Double v = value(node, name + r);
                    if (v == null) {
                        break;
                    }
                    if (sb.length() > 0) {
                        sb.append(" / ");
                    }
                    sb.append(format(v, fmt));
                }
                text = sb.length() == 0 ? m.group() : sb.toString();
            } else {
                Double v = value(node, name);
                text = v == null ? m.group() : format(v, fmt);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(text));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static Double value(CoreStats.NodeLine node, String name) {
        if ("a".equals(name)) {
            return node.amount;
        }
        if ("m".equals(name)) {
            return node.amount * Math.max(1, node.maxLevel);
        }
        Map<String, Double> values = node.values;
        return values == null ? null : values.get(name);
    }

    static String format(double v, String fmt) {
        boolean pct = fmt.contains("%");
        boolean factor = fmt.contains("x");
        if (pct) {
            v *= 100.0;
        }
        if (fmt.contains("h")) {
            v /= 2.0;
        }
        if (fmt.contains("s")) {
            v /= 20.0;
        }
        String num = number(v);
        if (fmt.contains("+") && v > 0) {
            num = "+" + num;
        }
        if (factor) {
            return "×" + num;
        }
        return pct ? num + (german() ? " %" : "%") : num;
    }

    private static String number(double v) {
        double r = Math.round(v * 100.0) / 100.0;
        String s;
        if (Math.abs(r - Math.rint(r)) < 1e-9) {
            s = String.format(Locale.ROOT, "%d", (long) Math.rint(r));
        } else {
            s = String.format(Locale.ROOT, "%.2f", r).replaceAll("0+$", "");
        }
        return german() ? s.replace('.', ',') : s;
    }

    private static boolean german() {
        // The language file says which decimal mark it uses ("," in German).
        return ",".equals(net.minecraft.locale.Language.getInstance().getOrDefault("nightsiege.ui.decimal", "."));
    }
}
