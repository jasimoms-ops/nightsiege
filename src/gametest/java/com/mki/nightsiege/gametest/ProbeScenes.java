package com.mki.nightsiege.gametest;

import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.SiegeSave;
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
 * Fehler-Stichprobe 06.11. nachts: since the nemesis really comes (06.11. 00:34), the killer of a defender
 * flees. Does that also happen to a night's warden or a boss's guard? Log lines start with "[probe]".
 * Only looks (no expectation is a "check"); the findings go into the report.
 */
final class ProbeScenes {

    private static final List<String> NOTES = Collections.synchronizedList(new ArrayList<>());

    private ProbeScenes() {
    }

    private static void log(String line) {
        System.out.println("[probe] " + line);
    }

    private static void server(ClientGameTestContext context, String step, EventLookTest.OnServer action) {
        EventLookTest.onServer(context, (player, manager) -> {
            try {
                action.run(player, manager);
            } catch (Throwable t) {
                NOTES.add(step + ": " + t);
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

    /** The lethal blow from {@code killer}; returns whether the killer is still on the field afterwards. */
    private static boolean blow(ServerPlayer player, SiegeManager m, Mob killer, String who) {
        ServerLevel level = (ServerLevel) player.level();
        int before = m.save().nemeses.size();
        killer.setNoAi(true);
        player.setHealth(3.0f);
        player.invulnerableTime = 0;
        player.hurtServer(level, level.damageSources().mobAttack(killer), 200f);
        boolean stays = !killer.isRemoved() && killer.isAlive();
        SiegeSave.NemesisRecord made = null;
        for (SiegeSave.NemesisRecord r : m.save().nemeses) {
            if (r.victimIds.contains(player.getUUID().toString()) && r.test) {
                made = r;
            }
        }
        log(who + ": player alive=" + player.isAlive() + " defending=" + m.isDefending(player) + " killer stays=" + stays
                + " removed=" + killer.isRemoved() + " nemeses " + before + " -> " + m.save().nemeses.size()
                + (made == null ? "" : " made=" + made.name + " " + made.type));
        return stays;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        NOTES.clear();
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
            log("others gone: " + clearOthers(player, warden[0]));
        });
        context.waitTicks(10);
        server(context, "warden blow", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            if (warden[0] == null) {
                log("no warden: nothing to look at");
                return;
            }
            warden[0].teleportTo(player.getX() + 2, player.getY(), player.getZ());
            boolean stays = blow(player, m, warden[0], "warden");
            Mob still = m.warden().warden(level);
            log("after: warden standing=" + (still != null) + " spawned=" + m.warden().spawned() + " fallen=" + m.warden().fallen()
                    + " active=" + m.isActive());
            log(stays ? "RESULT warden stays on the field (fine)" : "RESULT BUG the warden fled as a nemesis: the warden night cannot be won by beating him");
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
            log("others gone: " + clearOthers(player, guard[0]));
        });
        context.waitTicks(10);
        server(context, "guard blow", (player, m) -> {
            if (guard[0] == null) {
                log("no guard: nothing to look at");
                return;
            }
            guard[0].teleportTo(player.getX() + 2, player.getY(), player.getZ());
            boolean stays = blow(player, m, guard[0], "guard");
            log(stays ? "RESULT guard stays (fine)" : "RESULT the boss's guard fled as a nemesis");
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
        log("done" + (NOTES.isEmpty() ? "" : ", errors: " + NOTES));
    }
}
