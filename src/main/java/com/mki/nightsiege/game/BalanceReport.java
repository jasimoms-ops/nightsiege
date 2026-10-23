package com.mki.nightsiege.game;

import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads the night protocol back and holds it against the balancing targets of
 * the config: how often nights are won, how often the kill goal is met, how
 * many defenders fall, and how far the subclasses' damage lies apart.
 */
public final class BalanceReport {

    private BalanceReport() {
    }

    public static List<Component> build(SiegeConfig cfg, boolean tests) {
        List<Component> out = new ArrayList<>();
        List<String[]> nights = read(Json.configFile(NightLog.NIGHT_FILE));
        List<String[]> players = read(Json.configFile(NightLog.PLAYER_FILE));
        int count = 0;
        int won = 0;
        int held = 0;
        int goal = 0;
        int deaths = 0;
        double closest = 0;
        int closestCount = 0;
        Map<Integer, int[]> byLevel = new TreeMap<>();
        for (String[] n : nights) {
            if (n.length < 20 || (!tests && "ja".equals(n[3])) || "abgebrochen".equals(n[15])) {
                continue;
            }
            count++;
            boolean win = "gewonnen".equals(n[15]) || ("test_gehalten".equals(n[15]) && "ja".equals(n[13]));
            boolean survived = win || "ziel_verfehlt".equals(n[15]) || "test_gehalten".equals(n[15]);
            if (win) {
                won++;
            }
            if (survived) {
                held++;
                if ("ja".equals(n[13])) {
                    goal++;
                }
            }
            deaths += parseInt(n[14]);
            if (!n[17].isBlank()) {
                closest += parse(n[17]);
                closestCount++;
            }
            int[] lv = byLevel.computeIfAbsent(parseInt(n[2]), k -> new int[2]);
            lv[0]++;
            if (win) {
                lv[1]++;
            }
        }
        out.add(Component.translatable(tests ? "nightsiege.balance.header_tests" : "nightsiege.balance.header", count).withStyle(ChatFormatting.GOLD));
        if (count == 0) {
            out.add(Component.translatable("nightsiege.balance.empty").withStyle(ChatFormatting.GRAY));
            return out;
        }
        double winRate = won / (double) count;
        double goalRate = held == 0 ? 0 : goal / (double) held;
        double deathRate = deaths / (double) count;
        out.add(line("nightsiege.balance.wins", pct(winRate), pct(cfg.targetWinRate),
                Math.abs(winRate - cfg.targetWinRate) <= 0.15));
        out.add(line("nightsiege.balance.goal", pct(goalRate), pct(cfg.targetGoalRate),
                Math.abs(goalRate - cfg.targetGoalRate) <= 0.2));
        out.add(line("nightsiege.balance.deaths", num(deathRate), num(cfg.targetDeathsPerNight),
                deathRate <= cfg.targetDeathsPerNight * 1.5));
        if (closestCount > 0) {
            out.add(Component.translatable("nightsiege.balance.closest", num(closest / closestCount)).withStyle(ChatFormatting.GRAY));
        }
        StringBuilder levels = new StringBuilder();
        for (Map.Entry<Integer, int[]> e : byLevel.entrySet()) {
            levels.append(" ").append(e.getKey()).append(": ").append(e.getValue()[1]).append("/").append(e.getValue()[0]);
        }
        out.add(Component.translatable("nightsiege.balance.levels", levels.toString().trim()).withStyle(ChatFormatting.GRAY));

        // Damage per defender and night, by subclass (or class).
        Map<String, double[]> damage = new LinkedHashMap<>();
        for (String[] p : players) {
            if (p.length < 13 || (!tests && "ja".equals(p[3])) || "abgebrochen".equals(p[12])) {
                continue;
            }
            String key = p[6].isBlank() ? p[5] : p[6];
            if (key.isBlank()) {
                continue;
            }
            double[] d = damage.computeIfAbsent(key, k -> new double[2]);
            d[0] += parse(p[7]);
            d[1]++;
        }
        if (!damage.isEmpty()) {
            double mean = 0;
            for (double[] d : damage.values()) {
                mean += d[0] / d[1];
            }
            mean /= damage.size();
            for (Map.Entry<String, double[]> e : damage.entrySet()) {
                double avg = e.getValue()[0] / e.getValue()[1];
                double off = mean <= 0 ? 0 : avg / mean - 1.0;
                boolean ok = Math.abs(off) <= cfg.targetClassSpread;
                String name = Component.translatable("nightsiege.subclass." + e.getKey()).getString();
                if (name.startsWith("nightsiege.")) {
                    name = e.getKey();
                }
                out.add(Component.translatable("nightsiege.balance.class", name, num(avg), (off >= 0 ? "+" : "") + pct(off),
                        (int) e.getValue()[1]).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        if (count < 5) {
            out.add(Component.translatable("nightsiege.balance.few").withStyle(ChatFormatting.GRAY));
        } else if (winRate < cfg.targetWinRate - 0.15) {
            out.add(Component.translatable("nightsiege.balance.too_hard").withStyle(ChatFormatting.YELLOW));
        } else if (winRate > cfg.targetWinRate + 0.15) {
            out.add(Component.translatable("nightsiege.balance.too_easy").withStyle(ChatFormatting.YELLOW));
        } else {
            out.add(Component.translatable("nightsiege.balance.fine").withStyle(ChatFormatting.GREEN));
        }
        return out;
    }

    private static Component line(String key, String value, String target, boolean ok) {
        return Component.translatable(key, value, target).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static List<String[]> read(Path path) {
        List<String[]> rows = new ArrayList<>();
        try {
            if (!Files.exists(path)) {
                return rows;
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                if (!lines.get(i).isBlank()) {
                    rows.add(lines.get(i).split(";", -1));
                }
            }
        } catch (IOException e) {
            // An unreadable protocol is simply empty.
        }
        return rows;
    }

    private static double parse(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int parseInt(String text) {
        return (int) parse(text);
    }

    private static String pct(double v) {
        return Math.round(v * 100) + " %";
    }

    private static String num(double v) {
        return String.format(Locale.GERMANY, "%.1f", v);
    }
}
