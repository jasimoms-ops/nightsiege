package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.game.CoreRecord;
import com.mki.nightsiege.game.FreeClassPick;
import com.mki.nightsiege.game.Json;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Tor 10 / K3: the free class pick on level 20 in the war leader's ready round (the bot leads, the player
 * defends). Every subclass free, the points of level 20, "Empfohlener Aufbau", 20 crates with "Alle öffnen",
 * then the cancel: the own profile and inventory are back. Then a round: it fights at warLevel 25 with the kit
 * of level 20, and after its end everything is back again. Log lines start with "[klassenwahl]".
 */
final class KlassenwahlScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static int shots;

    private KlassenwahlScenes() {
    }

    private static void log(String line) {
        System.out.println("[klassenwahl] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.takeScreenshot(name);
        shots++;
    }

    /** Runs a step on the server thread and waits until it ran (onServer only queues it). */
    private static void server(ClientGameTestContext context, String step, EventLookTest.OnServer action) {
        final java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean();
        EventLookTest.onServer(context, (player, manager) -> {
            try {
                action.run(player, manager);
            } catch (Throwable t) {
                FAILS.add(step + ": " + t);
                log("ERROR in " + step + ": " + t);
                t.printStackTrace(System.out);
            } finally {
                ran.set(true);
            }
        });
        for (int i = 0; i < 200 && !ran.get(); i++) {
            context.waitTick();
        }
        if (!ran.get()) {
            FAILS.add(step + ": did not run on the server");
            log("ERROR in " + step + ": did not run on the server within 10 s");
        }
    }

    private static String id(ServerPlayer p) {
        return p.getUUID().toString();
    }

    /** The inventory as text (item and count per slot) to compare before and after. */
    private static String inventory(ServerPlayer p) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty()) {
                b.append(i).append('=').append(s.getItem()).append('x').append(s.getCount()).append(';');
            }
        }
        return b.toString();
    }

    private static String keyOf(PlayerSkills lent, String subclass) {
        for (String key : lent.profiles()) {
            if (subclass.equals(lent.plannedSubclass(key))) {
                return key;
            }
        }
        return null;
    }

    private static boolean ultLearned(SiegeManager m, PlayerSkills p) {
        for (SkillNode n : m.skillTree().ofClass(p.clazz())) {
            if (n.effect().target() == SkillEffect.Target.ULTIMATE && p.levelOf(n.id()) > 0) {
                return true;
            }
        }
        return false;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        shots = 0;
        EventLookTest.ensureCore(context, game);
        final BlockPos[] core = new BlockPos[1];
        final String[] before = new String[2];
        final int[] keep = new int[2];
        server(context, "setup", (player, m) -> {
            CoreRecord rec = m.mainCore();
            core[0] = rec == null ? null : new BlockPos(rec.x, rec.y, rec.z);
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            keep[0] = m.config().warForceSeconds;
            keep[1] = m.config().warPrepSeconds;
            m.config().warForceSeconds = 0;
            check(m.config().warLevel == 25, "warLevel 25 (" + m.config().warLevel + ")");
            check(m.config().funCrates == 20 && m.config().funKitLevel == 20, "20 crates, kit level 20");
        });
        if (core[0] == null) {
            log("summary: " + checks + " checks, setup failed (no core): " + FAILS);
            return;
        }
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 90 10", core[0].getX() + 4, core[0].getY(), core[0].getZ()));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("time set 6000");
        game.getServer().runCommand("give @p minecraft:bread 3");   // something of the own inventory to find again
        context.waitTicks(20);
        server(context, "before", (player, m) -> {
            before[0] = Json.toJson(m.save().realSkillsOf(id(player)));
            before[1] = inventory(player);
            check(!m.playsLent(player), "no loan before");
        });

        // 1. The ready round (the bot leads): the free class pick opens.
        server(context, "lobby", (player, m) -> {
            String why = m.warLeader().startBot(player.level().getServer());
            check(why == null, "the bot's ready round opens (" + why + ")");
        });
        context.waitTicks(30);
        server(context, "pick open", (player, m) -> {
            check(m.warLeader().lobbyOpen() && m.warLeader().inRound(player), "the player is in the ready round");
            check(m.playsLent(player), "a lent profile");
            PlayerSkills lent = m.save().skillsOf(id(player));
            int expected = 0;
            for (SkillClass c : SkillClass.values()) {
                if (!c.isShared() && m.mayChoose(player, c)) {
                    expected += SkillTree.subclassInfosOf(c).size();
                }
            }
            check(lent.profiles().size() == expected && expected >= 16, "every subclass free (" + lent.profiles().size() + " of " + expected + ")");
            check(lent.points() == FreeClassPick.budget(m.config()), "the points of level 20 (" + lent.points() + ")");
            check(m.freeClassPick().due(player) == 20, "20 crates wait (" + m.freeClassPick().due(player) + ")");
        });
        context.waitTicks(20);
        shot(context, "klassenwahl_01_classes");

        // 2. A subclass, the recommended build.
        server(context, "pick medic", (player, m) -> {
            PlayerSkills lent = m.save().skillsOf(id(player));
            String key = keyOf(lent, "medic");
            check(key != null, "the medic is one of them");
            m.onPlayClass(player, key);
            check(key != null && key.equals(lent.chosenClass), "the medic is played");
            m.onFunAction(player, "recommended");
            int spent = m.skillTree().pointsSpentProfile(lent, lent.chosenClass);
            int budget = FreeClassPick.budget(m.config());
            check(spent > 0 && spent <= budget && lent.points() < 10, "\"Empfohlener Aufbau\" spends the level 20 points ("
                    + spent + " of " + budget + ", left " + lent.points() + ")");
            check(ultLearned(m, lent), "the ultimate is learned");
            check("medic".equals(m.skillTree().subclassOfProfile(lent, lent.chosenClass)), "the subclass stays");
            check(Json.toJson(m.save().realSkillsOf(id(player))).equals(before[0]), "the own profile untouched");
        });
        context.waitTicks(30);
        shot(context, "klassenwahl_02_tree");

        // 3. The crates: all at once, on the lent profile.
        server(context, "crates", (player, m) -> {
            m.onFunAction(player, "crates");
            PlayerSkills lent = m.save().skillsOf(id(player));
            check(m.freeClassPick().due(player) == 0, "all 20 opened (" + m.freeClassPick().due(player) + " left)");
            java.util.Map<String, Integer> gains = lent.gains == null ? null : lent.gains.get(lent.clazz().name());
            check(gains != null && !gains.isEmpty(), "gains on the lent profile (" + gains + ")");
            check(Json.toJson(m.save().realSkillsOf(id(player))).equals(before[0]), "the own gains untouched");
            check(inventory(player).equals(before[1]), "the own inventory untouched");
        });
        context.waitTicks(30);
        shot(context, "klassenwahl_03_crates");
        EventLookTest.closeScreen(context);

        // 4. The cancel: everything back.
        server(context, "cancel", (player, m) -> {
            check(m.warLeader().stop(player.level().getServer()), "the ready round is called off");
            check(!m.playsLent(player), "the loan is gone");
            check(Json.toJson(m.save().realSkillsOf(id(player))).equals(before[0]), "the own profile is back as it was");
            check(inventory(player).equals(before[1]), "the own inventory is back");
            check(m.freeClassPick().due(player) == 0, "no crates without a loan");
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);

        // 5. A round: level 25, the kit of level 20; after it everything back.
        server(context, "lobby 2", (player, m) -> {
            String why = m.warLeader().startBot(player.level().getServer());
            check(why == null, "a second ready round (" + why + ")");
        });
        context.waitTicks(30);
        server(context, "ready", (player, m) -> {
            PlayerSkills lent = m.save().skillsOf(id(player));
            String key = keyOf(lent, "berserker");
            m.onPlayClass(player, key);
            m.onFunAction(player, "recommended");
            m.onFunAction(player, "crates");
            m.onFunAction(player, "ready");
        });
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        server(context, "round", (player, m) -> {
            check(m.warLeader().running() && m.isActive(), "the round runs");
            check(m.foesHost().level() == 25, "the monsters of level 25 (" + m.foesHost().level() + ", world " + m.currentLevel() + ")");
            check(m.levelForKit() == 20, "the kit of level 20 (" + m.levelForKit() + ")");
            check(m.warLeader().testRoundGold() > 0, "war gold for level 25 (" + m.warLeader().testRoundGold() + ")");
            check(m.playsLent(player) && "berserker".equals(m.skillTree().subclassOfProfile(m.save().skillsOf(id(player)),
                    m.save().skillsOf(id(player)).chosenClass)), "the berserker plays");
        });
        context.waitTicks(40);
        shot(context, "klassenwahl_04_round");
        server(context, "stop", (player, m) -> m.warLeader().stop(player.level().getServer()));
        final boolean[] done = new boolean[1];
        for (int i = 0; i < 30 && !done[0]; i++) {
            context.waitTicks(20);
            server(context, "wait", (player, m) -> done[0] = !m.isActive() && !m.warLeader().busy());
        }
        context.waitTicks(40);
        server(context, "after", (player, m) -> {
            check(done[0], "the round is over");
            check(!m.playsLent(player), "the loan is gone after the round");
            check(Json.toJson(m.save().realSkillsOf(id(player))).equals(before[0]), "the own profile is back after the round");
            check(inventory(player).equals(before[1]), "the own inventory is back after the round (" + inventory(player) + ")");
            m.config().warForceSeconds = keep[0];
            m.config().warPrepSeconds = keep[1];
        });
        EventLookTest.closeScreen(context);
        EventLookTest.alive(context);
        game.getServer().runCommand("clear @p minecraft:bread 3");
        game.getServer().runCommand("time set 6000");
        if (shots == 0) {
            FAILS.add("no screenshots");
        }
        if (checks == 0) {
            FAILS.add("no checks");
        }
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
