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

    /** Sets the core window's tab by its name (the next opening shows it); returns the tab before. */
    private static Object coreTab(ClientGameTestContext context, String name) {
        return context.computeOnClient(client -> {
            try {
                java.lang.reflect.Field tab = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                tab.setAccessible(true);
                Object before = tab.get(null);
                for (Object t : tab.getType().getEnumConstants()) {
                    if (name != null && t.toString().equals(name)) {
                        tab.set(null, t);
                    }
                }
                return before;
            } catch (ReflectiveOperationException e) {
                log("tab not set: " + e);
                return null;
            }
        });
    }

    /** Trap items (the engineer's free traps) in the inventory. */
    private static int traps(net.minecraft.server.level.ServerPlayer player) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && com.mki.nightsiege.game.ModContent.TRAP_ITEMS.containsValue(stack.getItem())) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /**
     * The kit comes before the lend (horn and teleport, dungeon and camp door): an engineer on level 3 in a world of
     * 15 gets the kit, then the lend; the kit must hold the free traps of the lent tree, a second lend (log-in again)
     * no second kit, and after the return and the restore no kit trap is left and the own things are back.
     */
    private static void kitCase(ClientGameTestContext context) {
        server(context, "kit", (player, m) -> {
            String id = player.getUUID().toString();
            PlayerSkills keep = Json.fromJson(Json.toJson(m.save().realSkillsOf(id)), PlayerSkills.class);
            PlayerSkills low = MeleeSim.buildBudget(m.skillTree(), SkillClass.ENGINEER,
                    com.mki.nightsiege.core.TrapKind.TURRET.id(), TestKit.pointsBefore(3, m.config()));
            m.save().skills.put(id, low);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 7));
            m.kits().equip(player, 12);   // the kit before the lend
            int ownTraps = traps(player);
            int lent = m.testLevelSync(player, 15);
            int want = 0;
            for (int c : m.kits().traps().freeTrapCounts(m.save().skillsOf(id)).values()) {
                want += c;
            }
            int have = traps(player);
            log("kit: own traps " + ownTraps + ", lent " + lent + ", want " + want + ", have " + have);
            check(lent > 0 && want > ownTraps, "engineer: the lend brings more free traps (" + ownTraps + " -> " + want + ")");
            check(have == want, "the kit has the lent tree's traps after the lend (" + have + " of " + want + ")");
            com.mki.nightsiege.game.LevelSync.end(m.save().realSkillsOf(id));   // logged out and in again
            m.testLevelSync(player, 15);
            check(traps(player) == want, "a second lend gives no second kit (" + traps(player) + " of " + want + ")");
            m.testLevelSyncEnd(player);
            m.kits().restore(player);
            int logs = player.getInventory().countItem(net.minecraft.world.item.Items.OAK_LOG);
            check(traps(player) == 0 && !m.kits().hasStash(player) && logs == 7,
                    "back: no kit trap left (" + traps(player) + "), own things back (logs " + logs + " of 7)");
            player.getInventory().clearContent();
            m.save().skills.put(id, keep);
        });
    }

    /**
     * Tor 12 (Prüfer): a profile switch while lent (a camp fighter gets the class window at the horn). The loan goes
     * back with the warrior, the engineer is lifted to the same level, both own trees stay as they were, nothing lent
     * is saved, and the kit after it holds the lent engineer tree's traps.
     */
    private static void switchCase(ClientGameTestContext context) {
        server(context, "profile switch", (player, m) -> {
            String id = player.getUUID().toString();
            PlayerSkills keep = Json.fromJson(Json.toJson(m.save().realSkillsOf(id)), PlayerSkills.class);
            int points = TestKit.pointsBefore(3, m.config());
            PlayerSkills two = MeleeSim.buildBudget(m.skillTree(), SkillClass.WARRIOR, "w_combo", points);
            PlayerSkills eng = MeleeSim.buildBudget(m.skillTree(), SkillClass.ENGINEER,
                    com.mki.nightsiege.core.TrapKind.TURRET.id(), points);
            String warrior = two.chosenClass;
            String engineer = eng.chosenClass;
            Map<String, Integer> warriorOwn = new HashMap<>(two.levels);
            Map<String, Integer> engineerOwn = new HashMap<>(eng.levels);
            two.unlockedClasses();
            two.unlocked.add(engineer);
            two.classLevels.put(engineer, new HashMap<>(eng.levels));
            m.save().skills.put(id, two);
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            int lent = m.testLevelSync(player, 15);
            m.onPlayClass(player, engineer);   // no kit on: the switch is open
            PlayerSkills own = m.save().realSkillsOf(id);
            int more = 0;
            if (own.borrowed != null) {
                for (String node : own.borrowed.keySet()) {
                    more += own.borrowedOf(node);
                }
            }
            log("switch: lent " + lent + " on " + warrior + " -> playing " + own.chosenClass + ", synced=" + own.synced()
                    + " profile=" + own.borrowedProfile + " level=" + own.syncLevel + " lent levels=" + more);
            check(lent > 0 && engineer.equals(own.chosenClass), "the switch to the engineer works while lent");
            check(own.synced() && engineer.equals(own.borrowedProfile) && own.syncLevel == 12,
                    "the loan moved to the engineer, level 12 (" + own.borrowedProfile + ", " + own.syncLevel + ")");
            check(more > 0, "lent levels count on the engineer (" + more + ")");
            check(own.levels.equals(engineerOwn) && warriorOwn.equals(own.classLevels.get(warrior)),
                    "both own trees untouched");
            String saved = Json.toJson(own);
            check(!saved.contains("borrowed") && !saved.contains("syncLevel"), "nothing lent in the save");
            int ownWant = 0;
            for (int c : own.withoutBorrowed(() -> m.kits().traps().freeTrapCounts(own)).values()) {
                ownWant += c;
            }
            int want = 0;
            for (int c : m.kits().traps().freeTrapCounts(own).values()) {
                want += c;
            }
            m.kits().equip(player, 12);
            int have = traps(player);
            log("switch kit: own traps " + ownWant + ", want " + want + ", have " + have + ", built lent " + m.kits().builtLent(player));
            check(want > ownWant && have == want && m.kits().builtLent(player),
                    "the kit after the switch has the lent engineer tree's traps (" + have + " of " + want + ")");
            m.testLevelSyncEnd(player);
            m.kits().restore(player);
            check(!own.synced() && traps(player) == 0, "back: loan and kit gone");
            player.getInventory().clearContent();
            m.save().skills.put(id, keep);
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);   // the switch opened the core window
    }

    static void run(ClientGameTestContext context) {
        FAILS.clear();
        checks = 0;
        shots = 0;
        final PlayerSkills[] saved = new PlayerSkills[1];
        final Map<String, Integer> ownLevels = new HashMap<>();
        kitCase(context);
        switchCase(context);
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
            Object tabBefore = coreTab(context, "STATS");   // the Lagebericht: its record card has the foot
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
            coreTab(context, tabBefore == null ? null : tabBefore.toString());
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
