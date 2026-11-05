package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.BossKind;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SiegeMachineKind;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Siege machines (Simon 05.11. 12:50): from level 10 a night without a camp brings machines of its own
 * (50 %, +5 % a level, one fewer than the level's count), a camp left standing sends twice the level's,
 * and the night's own ones stay away from boss nights. Log lines start with "[maschinen]".
 */
final class MaschinenScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private MaschinenScenes() {
    }

    private static void log(String line) {
        System.out.println("[maschinen] " + line);
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

    /** One test night: what it plans (machines), then off again. */
    private static void night(ClientGameTestContext context, TestSingleplayerContext game, int cx, int cy, int cz,
                              String name, int level, boolean camp, Boolean own, BossKind boss, int want) {
        final int[] got = {-1};
        final boolean[] on = new boolean[1];
        server(context, name + " prep", (player, m) -> {
            ServerLevel world = (ServerLevel) player.level();
            m.config().testPrepSeconds = 1;
            m.setTestMachines(camp);
            m.setTestRandomMachines(own);
            boolean ok = m.prepareTest(world.getServer(), world, new BlockPos(cx + 8, cy, cz + 8), level, SiegeEvent.NONE, boss);
            log(name + ": night prepared=" + ok);
        });
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        server(context, name + " read", (player, m) -> {
            got[0] = m.machines().tonightCount();
            on[0] = m.machinesTonight();
            log(name + ": machines=" + got[0] + " tonight=" + on[0] + " planned=" + m.machines().plannedKinds() + " active=" + m.isActive());
            check(m.isActive(), name + ": the test night runs");
            check(got[0] == want && on[0] == (want > 0), name + ": " + want + " machines (got " + got[0] + ")");
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
        });
        context.waitTicks(40);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 260;
        int cz = pos.getZ() - 260;
        int cy = EventLookTest.yard(context, game, cx, cz, 30, 12);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy, cz));
        game.getServer().runCommand("gamemode survival @p");
        context.waitTicks(20);
        final int[] saved = new int[2];
        server(context, "config", (player, m) -> {
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().machinesRandomFromLevel;
            m.config().machinesRandomFromLevel = 10;   // the other scenes keep the night's own machines away
            check(m.config().machinesRandomChanceAt(9) == 0.0, "no own machines below level 10");
            check(Math.abs(m.config().machinesRandomChanceAt(10) - 0.5) < 1e-9, "level 10: 50 %");
            check(Math.abs(m.config().machinesRandomChanceAt(15) - 0.75) < 1e-9, "level 15: 75 %");
            check(m.config().machinesRandomChanceAt(20) == 1.0 && m.config().machinesRandomChanceAt(30) == 1.0,
                    "from level 20: every night");
            check(SiegeMachineKind.campCount(10, m.config().machinesCampFactor, m.config().machinesMax) == 4
                    && SiegeMachineKind.campCount(20, m.config().machinesCampFactor, m.config().machinesMax) == 8,
                    "a camp left standing: twice the level's (4 on 10, 8 on 20)");
        });
        night(context, game, cx, cy, cz, "own 10", 10, false, Boolean.TRUE, null, 1);
        night(context, game, cx, cy, cz, "own 20", 20, false, Boolean.TRUE, null, 3);
        night(context, game, cx, cy, cz, "camp 10", 10, true, null, null, 4);
        night(context, game, cx, cy, cz, "camp 20", 20, true, null, null, 8);
        night(context, game, cx, cy, cz, "boss 20", 20, false, Boolean.TRUE, BossKind.RAM_TITAN, 0);
        night(context, game, cx, cy, cz, "none 20", 20, false, Boolean.FALSE, null, 0);
        server(context, "end", (player, m) -> {
            m.config().testPrepSeconds = saved[0];
            m.config().machinesRandomFromLevel = saved[1];
            m.setTestRandomMachines(null);
        });
        context.waitTicks(10);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
