package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.SpawnArc;
import com.mki.nightsiege.game.MobFactory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simon 08.11.: "die Gegner-Wellen nur noch aus Nordost bis Südost vom Core aus". With spawnArcFrom 45 and
 * spawnArcTo 135 every monster of the night comes in that arc (seen where it first shows up). Log lines
 * start with "[richtung]".
 */
final class RichtungScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private RichtungScenes() {
    }

    private static void log(String line) {
        System.out.println("[richtung] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    private static void server(ClientGameTestContext context, String step, EventLookTest.OnServer action) {
        AtomicBoolean ran = new AtomicBoolean();
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
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() + 300;
        int cz = pos.getZ() - 300;
        int cy = EventLookTest.yard(context, game, cx, cz, 40, 12);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy, cz));
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(20);
        final BlockPos core = new BlockPos(cx, cy, cz);
        final double[] saved = new double[3];
        server(context, "start", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            saved[0] = m.config().spawnArcFrom;
            saved[1] = m.config().spawnArcTo;
            saved[2] = m.config().testPrepSeconds;
            m.config().spawnArcFrom = 45.0;
            m.config().spawnArcTo = 135.0;
            m.config().testPrepSeconds = 1;
            log("night prepared=" + m.prepareTest(level.getServer(), level, core, 10, com.mki.nightsiege.core.SiegeEvent.NONE, null));
        });
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        // Where each monster first shows up, once a second for 30 s.
        final Map<UUID, double[]> first = new HashMap<>();
        for (int s = 0; s < 30; s++) {
            server(context, "look", (player, m) -> {
                ServerLevel level = (ServerLevel) player.level();
                for (Mob mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(core).inflate(120),
                        e -> e.entityTags().contains(MobFactory.TAG))) {
                    first.putIfAbsent(mob.getUUID(), new double[] {mob.getX(), mob.getZ()});
                }
            });
            context.waitTicks(20);
        }
        int in = 0;
        int out = 0;
        int[] compass = new int[8];
        for (double[] p : first.values()) {
            double a = Math.atan2(p[1] - (core.getZ() + 0.5), p[0] - (core.getX() + 0.5));
            double deg = (Math.toDegrees(a) + 90.0 + 360.0) % 360.0;   // compass: 0 north, 90 east
            compass[(int) Math.floorMod(Math.round(deg / 45.0), 8L)]++;
            // A little room at the edges: the first sight is up to a second after the spawn.
            double d = Math.toDegrees(a);
            boolean ok = d >= -45.0 - 12.0 && d <= 45.0 + 12.0;
            if (ok) {
                in++;
            } else {
                out++;
            }
        }
        log(String.format(Locale.ROOT, "monsters %d, in the arc %d, outside %d, by compass N %d NE %d E %d SE %d S %d SW %d W %d NW %d",
                first.size(), in, out, compass[0], compass[1], compass[2], compass[3], compass[4], compass[5], compass[6], compass[7]));
        check(first.size() >= 8, "monsters came");
        check(out == 0, "every monster came from north-east to south-east");
        server(context, "unit", (player, m) -> {
            check(SpawnArc.contains(m.config(), 0.0), "east is in the arc");
            check(!SpawnArc.contains(m.config(), Math.PI), "west is not");
        });
        server(context, "end", (player, m) -> {
            m.abort(player.level().getServer());
            m.config().spawnArcFrom = saved[0];
            m.config().spawnArcTo = saved[1];
            m.config().testPrepSeconds = (int) saved[2];
        });
        context.waitTicks(40);
        EventLookTest.closeScreen(context);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
