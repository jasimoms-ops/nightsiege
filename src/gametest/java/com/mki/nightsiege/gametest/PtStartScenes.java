package com.mki.nightsiege.gametest;

import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tor 9.1 j, package pt-start (Simon 09.11. 09:26: "Playtest aber für alle möglich, also ich kann auch
 * alleine einen starten"): a player without OP types the commands. During a (test) night the start is
 * refused; with nothing running the start alone works, a second start is refused, the player stops it
 * and the next start waits for the cooldown. Log lines start with "[pt-start]".
 */
final class PtStartScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private PtStartScenes() {
    }

    private static void log(String line) {
        System.out.println("[pt-start] " + line);
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
    }

    /** The command as this player without any OP rights. */
    private static void asNonOp(ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS);
        player.level().getServer().getCommands().performPrefixedCommand(source, command);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode survival @p");
        context.waitTicks(10);
        server(context, "setup", (player, m) -> {
            if (m.playtest().running()) {
                m.playtest().finish(player.level().getServer(), "nightsiege.playtest.stopped");
            }
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            m.save().register(player.getUUID().toString());
            m.playtest().resetCooldownForTests();
        });
        context.waitTicks(10);

        // 1. A (test) night is prepared or runs: the start without OP is refused.
        game.getServer().runCommand("nightsiege test 5");
        context.waitTicks(10);
        server(context, "during night", (player, m) -> {
            boolean night = m.isActive() || m.isPreparing();
            asNonOp(player, "nightsiege playtest 5 allein");
            log("night=" + night + " playtest=" + m.playtest().running());
            check(night && !m.playtest().running(), "start without OP during a night refused (1 of 5)");
        });
        context.waitTicks(5);
        game.getServer().runCommand("nightsiege stop");
        context.waitTicks(40);
        server(context, "after night", (player, m) -> {
            if (m.isActive() || m.isPreparing()) {
                m.cancelPreparation();
            }
            log("after stop: active=" + m.isActive() + " preparing=" + m.isPreparing());
        });
        context.waitTicks(40);

        // 2. Nothing runs: the start alone without OP works.
        server(context, "start alone", (player, m) -> {
            asNonOp(player, "nightsiege playtest 5 allein");
            check(m.playtest().running() && m.playtest().takesPart(player.getUUID())
                    && m.playtest().startedBy(player.getUUID()) && m.playtest().playerCount() == 1,
                    "start alone without OP works (2 of 5): " + m.playtest().status());
        });
        context.waitTicks(10);

        // 3. A second start while it runs is refused (still the same playtest).
        server(context, "second start", (player, m) -> {
            long before = m.playtest().lastStartTick();
            asNonOp(player, "nightsiege playtest 6 allein");
            check(m.playtest().running() && m.playtest().lastStartTick() == before, "second start refused (3 of 5)");
        });
        context.waitTicks(10);

        // 4. The starter stops it without OP.
        server(context, "stop", (player, m) -> {
            asNonOp(player, "nightsiege playtest stop");
            check(!m.playtest().running(), "stop without OP by the starter (4 of 5)");
        });
        context.waitTicks(20);

        // 5. At once again: the cooldown holds.
        server(context, "cooldown", (player, m) -> {
            asNonOp(player, "nightsiege playtest 5 allein");
            check(!m.playtest().running(), "new start within the cooldown refused (5 of 5)");
            if (m.playtest().running()) {
                m.playtest().finish(player.level().getServer(), "nightsiege.playtest.stopped");
            }
            m.playtest().resetCooldownForTests();
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1500");
        context.waitTicks(10);
        if (checks == 0) {
            FAILS.add("no checks");
        }
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
        log(FAILS.isEmpty() && checks == 5 ? "OK" : "FAIL");
    }
}
