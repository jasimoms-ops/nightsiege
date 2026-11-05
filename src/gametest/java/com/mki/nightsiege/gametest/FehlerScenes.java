package com.mki.nightsiege.gametest;

import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.SiegeSave;
import com.mki.nightsiege.game.StatsRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Vex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fehler-Stichprobe 05.11. abends: (1) a defender killed in the night becomes a drone, and the killer still
 * becomes a nemesis and the arch-enemy in the statistics; (3) the rest of his life counts as lost; (2) a
 * magician's vexes go with the night. Log lines start with "[fehler]".
 */
final class FehlerScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private FehlerScenes() {
    }

    private static void log(String line) {
        System.out.println("[fehler] " + line);
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

    private static int clear(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        List<Mob> monsters = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(200),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy);
        monsters.forEach(Mob::discard);
        return monsters.size();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 300;
        int cz = pos.getZ() + 300;
        int cy = EventLookTest.yard(context, game, cx, cz, 30, 12);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy, cz));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(20);
        final int[] saved = new int[2];
        final boolean[] savedB = new boolean[1];
        final BlockPos core = new BlockPos(cx + 8, cy, cz + 8);

        // ---------------------------------------------------------------- 1 and 3: a real lethal blow in the night
        server(context, "night", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            savedB[0] = m.config().refillSpawns;
            m.config().testPrepSeconds = 1;
            m.config().batchIntervalSeconds = 600;
            m.config().refillSpawns = false;
            log("night prepared=" + m.prepareTest(level.getServer(), level, core, 6, com.mki.nightsiege.core.SiegeEvent.NONE));
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "clear", (player, m) -> log("first monsters gone: " + clear(player)));
        context.waitTicks(5);
        final int[] nemesesBefore = new int[1];
        final Mob[] killer = new Mob[1];
        server(context, "kill", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            check(m.isActive() && m.isDefending(player), "the test night runs and the player defends");
            nemesesBefore[0] = m.save().nemeses.size();
            killer[0] = m.foesHost().spawn("minecraft:zombie", player.blockPosition().east(2));
            check(killer[0] != null, "a zombie of the night");
            if (killer[0] == null) {
                return;
            }
            killer[0].setNoAi(true);
            m.foesHost().addMob(killer[0]);
            player.setHealth(3.0f);
            player.invulnerableTime = 0;
            player.hurtServer(level, level.damageSources().mobAttack(killer[0]), 60f);
            log("after the blow: alive=" + player.isAlive() + " hp=" + player.getHealth() + " defending=" + m.isDefending(player)
                    + " nemeses " + nemesesBefore[0] + " -> " + m.save().nemeses.size());
            check(player.isAlive(), "the player does not really die (a drone instead)");
            check(!m.isDefending(player), "he is out for the night");
            SiegeSave.NemesisRecord made = null;
            for (SiegeSave.NemesisRecord r : m.save().nemeses) {
                if (r.victimIds.contains(player.getUUID().toString())) {
                    made = r;
                }
            }
            check(m.save().nemeses.size() == nemesesBefore[0] + 1 && made != null,
                    "the killer becomes a nemesis (" + (made == null ? "-" : made.name + " " + made.type + " test=" + made.test) + ")");
            check(made != null && made.test, "as a test night's nemesis");
            check(killer[0].isRemoved(), "and flees from the field");
        });
        // The night ends by itself (nobody stands), the file is written.
        for (int i = 0; i < 10; i++) {
            context.waitTicks(20);
            final boolean[] done = new boolean[1];
            server(context, "wait " + i, (player, m) -> done[0] = !m.isActive());
            if (done[0]) {
                break;
            }
        }
        context.waitTicks(20);
        server(context, "stats", (player, m) -> {
            if (m.isActive()) {
                m.finish(player.level().getServer(), false);
            }
            StatsRecorder.NightFile n = StatsRecorder.lastFinished;
            Map<String, Integer> by = n == null || n.todDurch == null ? null : n.todDurch.get(player.getScoreboardName());
            log("file: tode=" + (n == null ? "-" : n.tode) + " todDurch=" + (n == null ? "-" : n.todDurch));
            check(by != null && by.keySet().stream().anyMatch(k -> k.contains("zombie")), "the night's file names the killer (" + by + ")");
            double lost = 0;
            if (n != null) {
                for (StatsRecorder.Welle w : n.wellen) {
                    StatsRecorder.SpielerWelle pw = w.spieler.get(player.getScoreboardName());
                    if (pw != null) {
                        lost += pw.verloren;
                    }
                }
            }
            log("lost in the file: " + lost);
            check(lost >= 2.9, "the rest of the life (3) counts as lost (" + lost + ")");
            // The test's nemesis goes again, so the scenes after see the world as before.
            m.save().nemeses.removeIf(r -> r.victimIds.contains(player.getUUID().toString()) && r.test);
        });
        context.waitTicks(40);
        EventLookTest.alive(context);

        // ---------------------------------------------------------------- 2: a magician's vexes go with the night
        server(context, "night 2", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            log("night 2 prepared=" + m.prepareTest(level.getServer(), level, core, 12, com.mki.nightsiege.core.SiegeEvent.NONE));
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "clear 2", (player, m) -> log("first monsters gone: " + clear(player)));
        context.waitTicks(5);
        final Vex[] vexes = new Vex[2];
        server(context, "vex", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            check(m.isActive(), "night 2 runs");
            Mob evoker = m.foesHost().spawn("minecraft:evoker", player.blockPosition().south(6));
            check(evoker != null, "a magician of the night");
            if (evoker == null) {
                return;
            }
            evoker.setNoAi(true);
            m.foesHost().addMob(evoker);
            for (int i = 0; i < 2; i++) {
                Vex v = EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (v == null) {
                    continue;
                }
                if (i == 0) {
                    v.setOwner(evoker);   // called up by the magician
                }
                v.setPos(player.getX() + 3 + i, player.getY() + 4, player.getZ() - 3);
                v.setNoAi(true);
                level.addFreshEntity(v);
                vexes[i] = v;
            }
            check(vexes[0] != null && vexes[0].entityTags().contains(SiegeManager.NIGHT_MOB_TAG),
                    "the magician's vex belongs to the night");
            check(vexes[1] != null && !vexes[1].entityTags().contains(SiegeManager.NIGHT_MOB_TAG),
                    "a vex without a master is not tagged (it goes by the core's reach)");
            m.abort(player.level().getServer());
        });
        context.waitTicks(10);
        server(context, "vex after", (player, m) -> {
            check(!m.isActive(), "night 2 is over");
            check(vexes[0] == null || vexes[0].isRemoved(), "the magician's vex is gone with the night");
            check(vexes[1] == null || vexes[1].isRemoved(), "so is the vex near the core");
            clear(player);
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            m.config().refillSpawns = savedB[0];
        });
        context.waitTicks(10);
        EventLookTest.alive(context);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
