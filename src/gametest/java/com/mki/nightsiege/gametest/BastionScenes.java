package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.BastionMath;
import com.mki.nightsiege.core.FunProfile;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.game.Bastion;
import com.mki.nightsiege.game.CoreRecord;
import com.mki.nightsiege.game.Json;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Update 7 (Simon 06.11.): the Letzte Bastion and the fun mode's lent profiles. The level gate, a full lent
 * profile of every subclass, its tree's points, "Alles lernen", the own profile untouched; the waves (level
 * 20 + n of the full curve, kills, pause), a boss wave that goes on after the boss, machines from wave 10,
 * the end when all fell, the record board; and vanilla's zombie reinforcement going with the night.
 * Log lines start with "[bastion]".
 */
final class BastionScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private BastionScenes() {
    }

    private static void log(String line) {
        System.out.println("[bastion] " + line);
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

    private static String id(ServerPlayer p) {
        return p.getUUID().toString();
    }

    /** The lent profile's key of a subclass. */
    private static String keyOf(PlayerSkills lent, String subclass) {
        for (String key : lent.profiles()) {
            if (subclass.equals(lent.plannedSubclass(key))) {
                return key;
            }
        }
        return null;
    }

    /** Pick, fill and ready, as a player in the fun mode's tree does. */
    private static void pickFillReady(ClientGameTestContext context, String subclass) {
        server(context, "pick " + subclass, (player, m) -> {
            PlayerSkills lent = m.save().skillsOf(id(player));
            String key = keyOf(lent, subclass);
            m.onPlayClass(player, key);
            m.onFunAction(player, "fill");
            m.onFunAction(player, "ready");
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);
    }

    /** Until the night is over (at most so many seconds). */
    private static void waitEnd(ClientGameTestContext context, int seconds) {
        final boolean[] done = new boolean[1];
        for (int i = 0; i < seconds && !done[0]; i++) {
            context.waitTicks(20);
            server(context, "wait", (player, m) -> done[0] = !m.isActive() && !m.bastion().busy());
        }
    }

    private static void killPlayer(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        player.setHealth(1.0f);
        player.hurtServer(level, level.damageSources().generic(), 1000f);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.ensureCore(context, game);
        final BlockPos[] core = new BlockPos[1];
        final String[] before = new String[1];
        final long[] clock = new long[1];
        final int[] runsBefore = new int[1];
        server(context, "setup", (player, m) -> {
            CoreRecord rec = m.mainCore();
            core[0] = new BlockPos(rec.x, rec.y, rec.z);
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            SiegeConfig cfg = m.config();
            cfg.bastionPauseSeconds = 3;   // fast waves for the test
            cfg.bastionClearSeconds = 2;
            before[0] = Json.toJson(m.save().realSkillsOf(id(player)));
            runsBefore[0] = m.save().bastionRuns == null ? 0 : m.save().bastionRuns.size();
            check(cfg.warLevel == 20, "the war leader's round fights at level 20 (warLevel " + cfg.warLevel + ")");
            // The numbers.
            check(BastionMath.levelForWave(1, cfg) == 24 && BastionMath.levelForWave(5, cfg) == 40,
                    "wave 1 at level 24, wave 5 at 40: 20 + n of the full curve (" + BastionMath.levelForWave(1, cfg) + ", "
                            + BastionMath.levelForWave(5, cfg) + ")");
            check(BastionMath.bossWave(5, cfg) && BastionMath.bossWave(10, cfg) && !BastionMath.bossWave(4, cfg),
                    "a boss every 5th wave");
            check(BastionMath.machines(9, cfg) == 0 && BastionMath.machines(10, cfg) == 1 && BastionMath.machines(11, cfg) == 0
                    && BastionMath.machines(15, cfg) == 2 && BastionMath.machines(20, cfg) == 3, "machines: 10 one, 15 two, 20 three");
            check(BastionMath.eliteChance(4, cfg) == 0.0 && BastionMath.eliteChance(5, cfg) > 0
                    && BastionMath.eliteChance(10, cfg) > BastionMath.eliteChance(5, cfg), "elites from wave 5, more every wave");
            check(BastionMath.needed(1, cfg) == 1 && BastionMath.needed(2, cfg) == 2 && BastionMath.needed(6, cfg) == 3,
                    "ready players needed: 3, or all online when fewer");
        });
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 90 10", core[0].getX() + 4, core[0].getY(), core[0].getZ()));
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("time set 6000");
        context.waitTicks(20);

        // 1. The level gate: the world is far below 20.
        server(context, "gate", (player, m) -> {
            if (m.currentLevel() < m.config().bastionFromLevel && !m.inPlaytest()) {
                m.bastion().requestStart(player);
                check(!m.bastion().busy(), "below world level 20 the Letzte Bastion does not open (level " + m.currentLevel() + ")");
            } else {
                log("skip the gate: world level " + m.currentLevel());
            }
        });

        // 2. The operator's test: the ready round and the lent profile.
        server(context, "lobby", (player, m) -> {
            clock[0] = Math.floorMod(player.level().getServer().overworld().getOverworldClockTime(), 24000L);
            m.bastion().testStart(player, 1);
            check(m.bastion().lobbyOpen() && m.bastion().inRound(player), "the ready round is open, the player is in it");
            check(m.playsLent(player), "the player plays a lent profile");
            PlayerSkills lent = m.save().skillsOf(id(player));
            int expected = 0;
            for (SkillClass c : SkillClass.values()) {
                if (!c.isShared() && m.mayChoose(player, c)) {
                    expected += SkillTree.subclassInfosOf(c).size();
                }
            }
            check(lent.profiles().size() == expected && expected >= 16,
                    "every subclass is a profile of it (" + lent.profiles().size() + " of " + expected + ")");
            int rest = FunProfile.remaining(m.skillTree(), lent, lent.chosenClass);
            check(lent.points() == rest && rest > 0, "it has the whole tree's points (" + lent.points() + " = " + rest + ")");
            check(lent.gains == null || lent.gains.isEmpty(), "no crate gains");
            check(Json.toJson(m.save().realSkillsOf(id(player))).equals(before[0]), "the own profile is untouched");
        });
        context.waitTicks(20);
        context.takeScreenshot("bastion_01_classes");

        // 3. A subclass: its tree with the points; "Alles lernen".
        server(context, "pick", (player, m) -> {
            PlayerSkills lent = m.save().skillsOf(id(player));
            String key = keyOf(lent, "berserker");
            check(key != null, "the berserker is one of the lent profiles");
            m.onPlayClass(player, key);
            check(key != null && key.equals(lent.chosenClass), "it is played now");
            int rest = FunProfile.remaining(m.skillTree(), lent, key);
            check(lent.points() == rest && rest > 0, "with its whole tree's points (" + lent.points() + ")");
            check(!m.classResetAvailable(lent), "no class change with a lent profile");
        });
        context.waitTicks(20);
        context.takeScreenshot("bastion_02_tree");
        server(context, "fill", (player, m) -> {
            PlayerSkills lent = m.save().skillsOf(id(player));
            m.onFunAction(player, "fill");
            check(m.skillTree().isCompleteProfile(lent, lent.chosenClass), "\"Alles lernen\" learns the whole tree");
            check(lent.points() == 0, "no points left over (" + lent.points() + ")");
            int donated = m.save().realSkillsOf(id(player)).donated;
            m.onDonate(player, 5);
            check(m.save().realSkillsOf(id(player)).donated == donated, "the lent points cannot go into the core tree");
            check(Json.toJson(m.save().realSkillsOf(id(player))).equals(before[0]), "the own profile is still untouched");
        });
        context.waitTicks(20);
        context.takeScreenshot("bastion_03_tree_full");
        EventLookTest.closeScreen(context);

        // 4. Ready: the round begins (one player online: one is enough).
        server(context, "ready", (player, m) -> m.onFunAction(player, "ready"));
        context.waitTicks(30);
        server(context, "wave 1", (player, m) -> {
            check(m.bastion().running() && m.isBastionRound(), "the round runs");
            check(m.bastion().wave() == 1 && m.foesHost().level() == 24, "wave 1 at level 24 (" + m.bastion().wave() + ", "
                    + m.foesHost().level() + ")");
            check(m.levelForKit() == m.config().funKitLevel, "the kit of level 20 (" + m.levelForKit() + ")");
            check(m.bastion().quota() >= 5, "the wave asks for kills (" + m.bastion().quota() + ")");
            long t = Math.floorMod(player.level().getServer().overworld().getOverworldClockTime(), 24000L);
            check(t >= 17000 && t <= 19000, "the clock stands at midnight (" + t + ")");
            check(m.isDefending(player), "the player defends");
        });
        context.waitTicks(100);
        server(context, "monsters", (player, m) -> check(m.nightMobCount() > 0, "monsters come (" + m.nightMobCount() + ")"));
        context.takeScreenshot("bastion_04_wave1");

        // 5. The wave's kills: the last ones, the pause, wave 2 one level step up.
        server(context, "kills", (player, m) -> m.testBastionWaveDone());
        context.waitTicks(10);
        server(context, "clear", (player, m) -> check(m.bastion().step() == Bastion.Step.CLEAR, "after the kills: the last ones fall ("
                + m.bastion().step() + ")"));
        server(context, "clear over", (player, m) -> m.testBastionPauseOver());
        context.waitTicks(10);
        server(context, "pause", (player, m) -> check(m.bastion().step() == Bastion.Step.PAUSE, "then the pause (" + m.bastion().step() + ")"));
        server(context, "pause over", (player, m) -> m.testBastionPauseOver());
        context.waitTicks(10);
        server(context, "wave 2", (player, m) -> check(m.bastion().wave() == 2 && m.bastion().step() == Bastion.Step.FIGHT
                && m.foesHost().level() == 28, "wave 2 at level 28 (" + m.bastion().wave() + ", " + m.foesHost().level() + ")"));

        // 6. Vanilla's zombie reinforcement goes with the night; a plain zombie stays.
        final Mob[] zombies = new Mob[2];
        server(context, "zombies", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            for (int i = 0; i < 2; i++) {
                Zombie z = EntityType.ZOMBIE.create(level, EntitySpawnReason.REINFORCEMENT);
                if (z == null) {
                    continue;
                }
                z.setPos(core[0].getX() + 12.5 + i * 3, core[0].getY(), core[0].getZ() + 12.5);
                z.setNoAi(true);
                z.setPersistenceRequired();
                level.addFreshEntity(z);
                if (i == 0) {
                    SiegeManager.markReinforcement(z);
                }
                zombies[i] = z;
            }
            check(zombies[0] != null && SiegeManager.isReinforcement((Zombie) zombies[0])
                    && zombies[1] != null && !SiegeManager.isReinforcement((Zombie) zombies[1]), "a reinforcement and a plain zombie");
        });

        // 7. All fell: the end, nothing counted (a test), the own profile back, the time of day back.
        server(context, "fall", (player, m) -> killPlayer(player));
        waitEnd(context, 20);
        server(context, "end", (player, m) -> {
            check(!m.isActive() && !m.bastion().busy(), "the round is over");
            check(!m.playsLent(player), "the lent profile is gone");
            check(Json.toJson(m.save().realSkillsOf(id(player))).equals(before[0]), "the own profile is back as it was");
            int runs = m.save().bastionRuns == null ? 0 : m.save().bastionRuns.size();
            check(runs == runsBefore[0], "a test run does not count (" + runs + ")");
            long t = Math.floorMod(player.level().getServer().overworld().getOverworldClockTime(), 24000L);
            check(Math.abs(t - clock[0]) < 1200, "the time of day is back (" + t + ", before " + clock[0] + ")");
            check(zombies[0] != null && zombies[0].isRemoved(), "the zombie reinforcement went with the night");
            check(zombies[1] != null && !zombies[1].isRemoved(), "the plain zombie stayed");
            if (zombies[1] != null) {
                zombies[1].discard();
            }
        });
        EventLookTest.alive(context);
        context.waitTicks(20);

        // 8. A boss wave: the boss comes, falls, the round goes on.
        server(context, "boss lobby", (player, m) -> m.bastion().testStart(player, 5));
        context.waitTicks(10);
        pickFillReady(context, "blademaster");
        context.waitTicks(60);
        server(context, "boss", (player, m) -> {
            check(m.isBastionRound() && m.bastion().wave() == 5, "wave 5 runs");
            ServerLevel level = (ServerLevel) player.level();
            Mob boss = m.bosses().boss(level);
            check(m.bosses().spawned() && boss != null, "the boss of the wave came (" + (boss == null ? "-" : boss.getType()) + ")");
            if (boss != null) {
                boss.kill(level);
            }
        });
        context.waitTicks(40);
        server(context, "boss down", (player, m) -> {
            check(m.isBastionRound(), "the round goes on after the boss");
            check(m.bastion().step() == Bastion.Step.FIGHT || m.bastion().step() == Bastion.Step.CLEAR, "the wave goes on ("
                    + m.bastion().step() + ")");
            m.abort(player.level().getServer());
        });
        waitEnd(context, 10);
        EventLookTest.alive(context);

        // 9. Wave 10: a siege machine.
        server(context, "machines lobby", (player, m) -> m.bastion().testStart(player, 10));
        context.waitTicks(10);
        pickFillReady(context, "shieldfighter");
        context.waitTicks(10);
        server(context, "machines", (player, m) -> {
            check(m.isBastionRound() && m.bastion().wave() == 10, "wave 10 runs");
            check(m.machines().tonightCount() == 1, "it brings one siege machine (" + m.machines().tonightCount() + ")");
            m.abort(player.level().getServer());
        });
        waitEnd(context, 10);
        EventLookTest.alive(context);

        // 10. A run that counts: the record and the board at the core.
        server(context, "counted lobby", (player, m) -> {
            m.config().bastionFromLevel = 1;
            m.bastion().requestStart(player);
            check(m.bastion().lobbyOpen() && m.bastion().counted(), "from the world's level on the run counts");
        });
        context.waitTicks(10);
        pickFillReady(context, "berserker");
        context.waitTicks(40);
        server(context, "counted fall", (player, m) -> killPlayer(player));
        waitEnd(context, 20);
        server(context, "record", (player, m) -> {
            int runs = m.save().bastionRuns == null ? 0 : m.save().bastionRuns.size();
            check(runs == runsBefore[0] + 1, "the run is on the list (" + runs + ")");
            BastionMath.Run best = runs == 0 ? null : m.save().bastionRuns.get(0);
            check(best != null && best.welle >= 1 && best.namen.contains(player.getScoreboardName())
                    && best.unterklassen.contains("berserker"), "with its wave, name and subclass ("
                    + (best == null ? "-" : best.welle + " " + best.team) + ")");
            ServerLevel level = (ServerLevel) player.level();
            int stands = level.getEntitiesOfClass(ArmorStand.class, new AABB(core[0]).inflate(4, 8, 4),
                    s -> s.entityTags().contains("nightsiege_bastion_board")).size();
            check(stands == 2, "the record board at the core: the title and one run (" + stands + ")");
            check(!m.playsLent(player) && Json.toJson(m.save().realSkillsOf(id(player))).equals(before[0]),
                    "and the own profile is back");
            m.config().bastionFromLevel = 20;
        });
        EventLookTest.alive(context);
        game.getServer().runCommand("time set 13000");
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 90 -20", core[0].getX() + 5, core[0].getY(), core[0].getZ()));
        context.waitTicks(40);
        context.takeScreenshot("bastion_05_board");
        game.getServer().runCommand("time set 6000");
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
