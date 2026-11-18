package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.game.Json;
import com.mki.nightsiege.game.MeleeSim;
import com.mki.nightsiege.game.TestKit;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scene "angleich" (Tor 12 I, Simon 18.11.): a blademaster on level 3 in a world of level 15 is lifted to level 12
 * with lent points (as a real night lends them, SiegeManager.testLevelSync); the bar and the core window read
 * "Angeglichen auf Stufe 12 (geliehen)" in EN and DE; at the end the lent levels go back and the own tree is as
 * before. Pictures "angleich_*". Log lines "[angleich]".
 */
final class AngleichScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static int shots;

    private AngleichScenes() {
    }

    private static void log(String line) {
        System.out.println("[angleich] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    private static void server(ClientGameTestContext context, String step, EventLookTest.OnServer action) {
        EventLookTest.onServer(context, (player, manager) -> {
            try {
                action.run(player, manager);
            } catch (Throwable t) {
                FAILS.add(step + ": " + t);
                log("ERROR in " + step + ": " + t);
                t.printStackTrace(System.out);
            }
        });
        context.waitTicks(5);
    }

    private static void picture(ClientGameTestContext context, String name) {
        context.waitTicks(10);
        context.takeScreenshot(name);
        shots++;
        log("picture " + name);
    }

    static void run(ClientGameTestContext context) {
        FAILS.clear();
        checks = 0;
        shots = 0;
        final PlayerSkills[] saved = new PlayerSkills[1];
        final Map<String, Integer> ownLevels = new HashMap<>();
        server(context, "set-up", (player, m) -> {
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            String id = player.getUUID().toString();
            saved[0] = Json.fromJson(Json.toJson(m.save().realSkillsOf(id)), PlayerSkills.class);
            PlayerSkills low = MeleeSim.buildBudget(m.skillTree(), SkillClass.WARRIOR, "w_combo", TestKit.pointsBefore(3, m.config()));
            m.save().skills.put(id, low);
            ownLevels.putAll(low.levels);
            player.setGameMode(GameType.SURVIVAL);
            m.kits().equip(player, 12);
            int lent = m.testLevelSync(player, 15);
            PlayerSkills own = m.save().realSkillsOf(id);
            check(lent > 0 && own.synced(), "level 3 in a world of 15: points lent (" + lent + ")");
            check(own.syncLevel == 12, "lifted to world level − 3 = 12 (is " + own.syncLevel + ")");
            check(own.levels.equals(ownLevels), "the own tree untouched while lent");
            int more = 0;
            for (String node : own.borrowed.keySet()) {
                more += own.levelOf(node) - ownLevels.getOrDefault(node, 0);
            }
            check(more > 0, "lent levels count in the fight (" + more + " levels on " + own.borrowed.size() + " nodes)");
            m.sendUltHud(player);
        });
        context.waitTicks(20);
        int barLevel = context.computeOnClient(client -> {
            com.mki.nightsiege.core.UltState s = com.mki.nightsiege.client.UltHud.state();
            return s == null ? -1 : s.syncLevel;
        });
        check(barLevel == 12, "the bar knows the level (" + barLevel + ")");
        for (String lang : new String[]{"en_us", "de_de"}) {
            LayoutCheck.language(context, lang);
            String text = context.computeOnClient(client -> net.minecraft.network.chat.Component
                    .translatable("nightsiege.ui.level_sync", 12).getString());
            check(text.contains("12") && !text.startsWith("nightsiege."), lang + ": the note reads [" + text + "]");
            picture(context, "angleich_leiste_" + lang);
            server(context, "window " + lang, (player, m) -> m.openCoreWindow(player));
            context.waitTicks(20);
            int windowLevel = context.computeOnClient(client -> {
                if (!(client.gui.screen() instanceof com.mki.nightsiege.client.CoreScreen s)) {
                    return -2;
                }
                try {
                    java.lang.reflect.Field f = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("stats");
                    f.setAccessible(true);
                    com.mki.nightsiege.core.CoreStats st = (com.mki.nightsiege.core.CoreStats) f.get(s);
                    return st == null ? -1 : st.syncLevel;
                } catch (ReflectiveOperationException e) {
                    return -3;
                }
            });
            check(windowLevel == 12, lang + ": the core window knows the level (" + windowLevel + ")");
            picture(context, "angleich_core_" + lang);
            EventLookTest.closeScreen(context);
        }
        server(context, "end", (player, m) -> {
            String id = player.getUUID().toString();
            m.testLevelSyncEnd(player);
            PlayerSkills own = m.save().realSkillsOf(id);
            check(!own.synced() && own.syncLevel == 0, "the lent levels are back");
            check(own.levels.equals(ownLevels), "the own tree as before");
            m.sendUltHud(player);
        });
        context.waitTicks(20);
        int after = context.computeOnClient(client -> {
            com.mki.nightsiege.core.UltState s = com.mki.nightsiege.client.UltHud.state();
            return s == null ? 0 : s.syncLevel;
        });
        check(after == 0, "the bar's note is gone (" + after + ")");
        picture(context, "angleich_leiste_zurueck");
        server(context, "clean-up", (player, m) -> {
            m.kits().restore(player);
            if (saved[0] != null) {
                m.save().skills.put(player.getUUID().toString(), saved[0]);
            }
        });
        LayoutCheck.language(context, "en_us");
        log((FAILS.isEmpty() && checks > 0 && shots > 0 ? "OK" : "FAIL") + " checks=" + checks + " failed=" + FAILS.size()
                + " pictures=" + shots);
        if (!FAILS.isEmpty() || checks == 0 || shots == 0) {
            throw new AssertionError("angleich: " + (checks == 0 ? "no checks" : String.join("; ", FAILS)));
        }
    }
}
