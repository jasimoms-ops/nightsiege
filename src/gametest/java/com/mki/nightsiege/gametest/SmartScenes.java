package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.SmartNight;
import com.mki.nightsiege.game.Elites;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.StatsRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Schlaue Nacht (Simon 05.11. 20:26): the night steps up when the defenders are too good (elites from
 * level 5, the special monsters earlier and more) and down when it gets tight (fewer at once, a pause,
 * far down no special monsters and no elites). Log lines start with "[schlau]".
 */
final class SmartScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private SmartScenes() {
    }

    private static void log(String line) {
        System.out.println("[schlau] " + line);
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

    /** Every monster of the night near the player goes (without a kill). */
    private static int clear(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        List<Mob> monsters = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(200),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy);
        monsters.forEach(Mob::discard);
        return monsters.size();
    }

    /** Every monster of the night near the player dies (a kill for the smart night). */
    private static int killAll(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        List<Mob> monsters = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(200),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive());
        for (Mob mob : monsters) {
            mob.invulnerableTime = 0;
            mob.hurtServer(level, level.damageSources().generic(), 100000f);
        }
        return monsters.size();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 300;
        int cz = pos.getZ() + 300;
        int cy = EventLookTest.yard(context, game, cx, cz, 30, 12);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy, cz));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(20);
        final int[] saved = new int[7];
        final boolean[] savedB = new boolean[3];
        final double[] savedD = new double[2];
        server(context, "night", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            var c = m.config();
            saved[0] = c.testPrepSeconds;
            saved[1] = c.batchIntervalSeconds;
            saved[2] = c.smartNightWindowSeconds;
            saved[3] = c.smartNightEverySeconds;
            saved[4] = c.smartNightUpSeconds;
            saved[5] = c.smartNightDownSeconds;
            saved[6] = c.eliteMobFromLevel;
            savedB[0] = c.refillSpawns;
            savedB[1] = c.smartNight;
            savedB[2] = c.eliteMobs;
            savedD[0] = c.smartNightUpKillsPerPlayer;
            savedD[1] = c.eliteMobChance;
            c.smartNight = true;   // off for the other scenes (EventLookTest)
            c.eliteMobs = false;   // as on the server since 05.11. 19:51
            c.testPrepSeconds = 1;
            c.batchIntervalSeconds = 600;   // waves only by hand
            c.refillSpawns = false;
            boolean ok = m.prepareTest(level.getServer(), level, new BlockPos(cx + 8, cy, cz + 8), 6,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            log("night prepared=" + ok);
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        server(context, "clear", (player, m) -> log("the night's first monsters gone: " + clear(player)));
        context.waitTicks(10);

        // ---------------------------------------------------------------- step 0: as before
        final int[] before = new int[4];
        server(context, "zero", (player, m) -> {
            SmartNight smart = m.smartNight();
            check(m.isActive() && smart.on(), "the smart night runs in a test night (level " + m.currentLevel() + ")");
            check(smart.step() == 0, "it starts at 0 (" + smart.step() + ")");
            check(m.fieldRoom() == m.maxLive(), "at 0 the field is as big as ever (" + m.fieldRoom() + " of " + m.maxLive() + ")");
            before[0] = m.foes().stunnersTonight;
            before[1] = m.foes().executionersTonight;
            before[2] = Elites.made();
            for (int i = 0; i < 2; i++) {
                m.testWave(player.level().getServer());
                clear(player);
            }
            check(m.foes().stunnersTonight == before[0] && m.foes().executionersTonight == before[1],
                    "at 0 no stunners and no executioners on level 6 (stunners from 8, executioners from 11)");
            check(Elites.made() == before[2], "at 0 no elites (the fixed ones are off)");
        });
        context.waitTicks(5);

        // ---------------------------------------------------------------- up: the special monsters earlier
        server(context, "up3", (player, m) -> {
            m.setSmartStep(3);
            check(m.smartNight().step() == 3, "step +3 set");
            check(!m.smartNight().paused(player.level().getGameTime()), "up gives no pause");
            before[0] = m.foes().stunnersTonight;
            before[1] = m.foes().executionersTonight;
            before[3] = m.foes().smartExtrasTonight;
            m.testWave(player.level().getServer());
            int stunners = m.foes().stunnersTonight - before[0];
            int executioners = m.foes().executionersTonight - before[1];
            int extras = m.foes().smartExtrasTonight - before[3];
            log("step 3, level 6: stunners " + stunners + ", executioners " + executioners + ", extras " + extras);
            check(stunners >= 1, "at +3 the stunners come on level 6 (6 levels earlier, for every class from +3)");
            check(extras == 1, "at +3 one extra special monster with the wave (" + extras + ")");
            check(stunners + executioners >= 2, "so at least two special monsters (" + (stunners + executioners) + ")");
            clear(player);
        });
        context.waitTicks(5);

        // ---------------------------------------------------------------- up: elites from level 5
        server(context, "up5", (player, m) -> {
            m.setSmartStep(5);
            before[2] = Elites.made();
            int spawned = 0;
            for (int i = 0; i < 4; i++) {
                m.testWave(player.level().getServer());
                spawned += m.nightMobCount();
                clear(player);
            }
            int elites = Elites.made() - before[2];
            log("step 5: " + elites + " elites of about " + spawned + " monsters (25 % wanted)");
            check(elites > 0, "at +5 elites come on level 6 (" + elites + ")");
            check(elites < spawned, "but not every monster is one");
        });
        context.waitTicks(5);

        // ---------------------------------------------------------------- down: fewer, a pause, no elites
        server(context, "down", (player, m) -> {
            var c = m.config();
            m.setSmartStep(-1);
            c.eliteMobs = true;   // fixed elites for every monster: far down they must not come
            c.eliteMobFromLevel = 1;
            c.eliteMobChance = 1.0;
            log("at -1: field " + m.fieldRoom() + " of " + m.maxLive());
            m.setSmartStep(-3);
            long now = player.level().getGameTime();
            check(m.smartNight().step() == -3, "step -3 set");
            check(m.smartNight().paused(now), "a step down gives a breather");
            int room = m.fieldRoom();
            int expect = Math.max(1, (int) Math.round(m.maxLive() * 0.85));
            check(room == expect, "at -3 the field holds 15 % fewer (" + room + " of " + m.maxLive() + ")");
            m.testWave(player.level().getServer());
            check(m.lastWaveSize() == 0, "a wave in the breather brings nobody (" + m.lastWaveSize() + ")");
        });
        context.waitTicks(180);   // the breather of 8 s is over
        server(context, "down after", (player, m) -> {
            check(!m.smartNight().paused(player.level().getGameTime()), "the breather ends");
            before[2] = Elites.made();
            before[0] = m.foes().stunnersTonight;
            m.testWave(player.level().getServer());
            int n = m.lastWaveSize();
            check(n > 0 && n <= m.fieldRoom(), "then the wave comes, at most the smaller field (" + n + " of " + m.fieldRoom() + ")");
            check(Elites.made() == before[2], "far down no elites, not even the fixed ones (" + (Elites.made() - before[2]) + ")");
            clear(player);
            m.setSmartStep(-1);
            m.testWave(player.level().getServer());
            log("at -1 with fixed elites for all: " + (Elites.made() - before[2]) + " elites");
            clear(player);
            var c = m.config();
            c.eliteMobs = false;
            c.eliteMobFromLevel = saved[6];
            c.eliteMobChance = savedD[1];
        });
        context.waitTicks(180);

        // ---------------------------------------------------------------- the night steps by itself
        server(context, "flow", (player, m) -> {
            var c = m.config();
            c.smartNightWindowSeconds = 3;
            c.smartNightEverySeconds = 1;
            c.smartNightUpSeconds = 2;
            c.smartNightDownSeconds = 1;
            c.smartNightUpKillsPerPlayer = 1;
            m.setSmartStep(0);
            player.setHealth(player.getMaxHealth());
        });
        final int[] stepAt = new int[2];
        // Full life and a kill every second for 12 s: it steps up.
        for (int s = 0; s < 12; s++) {
            server(context, "easy " + s, (player, m) -> {
                player.setHealth(player.getMaxHealth());
                m.testWave(player.level().getServer());
                killAll(player);
            });
            context.waitTicks(20);
        }
        server(context, "easy check", (player, m) -> {
            stepAt[0] = m.smartNight().step();
            SmartNight.Look l = m.smartNight().last();
            log("after 12 easy seconds: step " + stepAt[0] + (l == null ? "" : " (" + l.why() + ", stress "
                    + String.format(java.util.Locale.ROOT, "%.2f", l.stress()) + ")"));
            check(stepAt[0] >= 2, "too good: the night stepped up by itself (" + stepAt[0] + ")");
            clear(player);
        });
        // A fifth of the life for 6 s: it steps down again.
        for (int s = 0; s < 6; s++) {
            server(context, "hard " + s, (player, m) -> player.setHealth(player.getMaxHealth() * 0.2f));
            context.waitTicks(20);
        }
        server(context, "hard check", (player, m) -> {
            stepAt[1] = m.smartNight().step();
            SmartNight.Look l = m.smartNight().last();
            log("after 6 hard seconds: step " + stepAt[1] + (l == null ? "" : " (" + l.why() + ")"));
            check(stepAt[1] < stepAt[0], "too tight: the night stepped down by itself (" + stepAt[0] + " -> " + stepAt[1] + ")");
            check(m.smartNight().pauses() > 0, "with a breather");
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(10);

        // ---------------------------------------------------------------- the statistics
        server(context, "stats", (player, m) -> {
            m.finish(player.level().getServer(), true);
            StatsRecorder.NightFile n = StatsRecorder.lastFinished;
            check(n != null && n.schlaueNacht != null && n.schlaueNacht.an, "the night's file has the smart night");
            if (n != null && n.schlaueNacht != null) {
                StatsRecorder.SchlaueNacht s = n.schlaueNacht;
                log("file: highest " + s.hoechste + ", lowest " + s.tiefste + ", last " + s.ende + ", steps " + s.schritte.size()
                        + ", elites " + s.eliten + ", extras " + s.extraSondermonster + ", pauses " + s.pausen);
                check(s.hoechste == 5 && s.tiefste <= -3, "highest +5, lowest -3 or below");
                check(s.schritte.size() >= 7, "every step is in it (" + s.schritte.size() + ")");
                check(s.extraSondermonster >= 1 && s.eliten > 0, "and what it sent");
            }
            check(!m.smartNight().on(), "after the night it is off");
        });
        context.waitTicks(10);

        server(context, "end", (player, m) -> {
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            var c = m.config();
            c.testPrepSeconds = saved[0];
            c.batchIntervalSeconds = saved[1];
            c.smartNightWindowSeconds = saved[2];
            c.smartNightEverySeconds = saved[3];
            c.smartNightUpSeconds = saved[4];
            c.smartNightDownSeconds = saved[5];
            c.eliteMobFromLevel = saved[6];
            c.refillSpawns = savedB[0];
            c.smartNight = false;
            c.eliteMobs = false;
            c.smartNightUpKillsPerPlayer = savedD[0];
            c.eliteMobChance = savedD[1];
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(20);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
