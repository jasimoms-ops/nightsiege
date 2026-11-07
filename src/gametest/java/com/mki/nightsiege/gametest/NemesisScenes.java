package com.mki.nightsiege.gametest;

import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.WardenNight;
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
 * Fehler-Stichprobe 06.11. nachts: since the nemesis really comes (06.11. 00:34) the killer of a defender
 * flees. The night's warden and a boss's guard must stay (the warden night could not be won any more); a
 * plain monster of the night still becomes a nemesis (scene "fehler"). Log lines start with "[nemesis]".
 */
final class NemesisScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private NemesisScenes() {
    }

    private static void log(String line) {
        System.out.println("[nemesis] " + line);
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

    private static int clearOthers(ServerPlayer player, Mob keep) {
        ServerLevel level = (ServerLevel) player.level();
        List<Mob> monsters = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(200),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && e != keep
                        && !e.entityTags().contains(WardenNight.TAG) && !com.mki.nightsiege.game.BossService.isBossOrPart(e));
        monsters.forEach(Mob::discard);
        return monsters.size();
    }

    /** The lethal blow from {@code killer}: the defender becomes a drone; the killer stays, no nemesis comes. */
    private static void blow(ServerPlayer player, SiegeManager m, Mob killer, String who) {
        ServerLevel level = (ServerLevel) player.level();
        int before = m.save().nemeses.size();
        killer.setNoAi(true);
        player.setHealth(3.0f);
        player.invulnerableTime = 0;
        player.hurtServer(level, level.damageSources().mobAttack(killer), 200f);
        boolean stays = !killer.isRemoved() && killer.isAlive();
        log(who + ": player alive=" + player.isAlive() + " defending=" + m.isDefending(player) + " killer stays=" + stays
                + " nemeses " + before + " -> " + m.save().nemeses.size());
        check(player.isAlive() && !m.isDefending(player), who + ": the defender is out (a drone)");
        check(stays, who + ": stays on the field");
        check(m.save().nemeses.size() == before, who + ": becomes no nemesis");
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
        final int[] saved = new int[2];
        final boolean[] savedB = new boolean[1];
        final BlockPos core = new BlockPos(cx + 8, cy, cz + 8);

        // ---------------------------------------------------------------- 1: the night's warden kills a defender
        server(context, "warden night", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            savedB[0] = m.config().refillSpawns;
            m.config().testPrepSeconds = 1;
            m.config().batchIntervalSeconds = 600;
            m.config().refillSpawns = false;
            m.planTestWarden(com.mki.nightsiege.core.ExpeditionKind.STONE_CRYPT);
            log("warden night prepared=" + m.prepareTest(level.getServer(), level, core, 8, com.mki.nightsiege.core.SiegeEvent.NONE, null));
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        final Mob[] warden = new Mob[1];
        server(context, "warden comes", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            boolean now = m.wardenNow(level.getServer());
            warden[0] = m.warden().warden(level);
            log("warden now=" + now + " active=" + m.isActive() + " warden=" + (warden[0] == null ? "-" : warden[0].getType().toShortString()));
            check(warden[0] != null, "the warden of the warden night is there");
            log("others gone: " + clearOthers(player, warden[0]));
        });
        context.waitTicks(10);
        server(context, "warden blow", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            if (warden[0] == null) {
                return;
            }
            warden[0].teleportTo(player.getX() + 2, player.getY(), player.getZ());
            blow(player, m, warden[0], "the warden");
            check(m.warden().warden(level) != null && !m.warden().fallen(), "the warden night still has its warden to beat");
        });
        context.waitTicks(20);
        server(context, "warden end", (player, m) -> {
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            m.save().nemeses.removeIf(r -> r.test);
        });
        context.waitTicks(40);
        EventLookTest.alive(context);

        // ---------------------------------------------------------------- 2: a boss's guard kills a defender
        server(context, "boss night", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            log("boss night prepared=" + m.prepareTest(level.getServer(), level, core, 8, com.mki.nightsiege.core.SiegeEvent.NONE,
                    com.mki.nightsiege.core.BossKind.GRAVE_KING));
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        final Mob[] guard = new Mob[1];
        server(context, "boss comes", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            boolean now = m.bossNow(level.getServer());
            for (WardenNight g : m.bossGuardsForTest()) {
                Mob w = g.warden(level);
                if (w != null && guard[0] == null) {
                    guard[0] = w;
                }
            }
            log("boss now=" + now + " guards=" + m.bossGuardsForTest().size() + " guard=" + (guard[0] == null ? "-" : guard[0].getType().toShortString()));
            check(guard[0] != null, "the boss has his guard");
            log("others gone: " + clearOthers(player, guard[0]));
        });
        context.waitTicks(10);
        server(context, "guard blow", (player, m) -> {
            if (guard[0] == null) {
                return;
            }
            guard[0].teleportTo(player.getX() + 2, player.getY(), player.getZ());
            blow(player, m, guard[0], "the boss's guard");
        });
        context.waitTicks(20);
        server(context, "boss end", (player, m) -> {
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            m.save().nemeses.removeIf(r -> r.test);
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            m.config().refillSpawns = savedB[0];
        });
        context.waitTicks(40);
        EventLookTest.alive(context);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
