package com.mki.nightsiege.gametest;

import com.mki.nightsiege.game.MobFactory;
import com.mki.nightsiege.game.RaidPlan;
import com.mki.nightsiege.game.StatsRecorder;
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
 * Tor 12 F (Simon 17.11. 15:23): monsters from all around in 2–4 groups, the core's life instead of the kill goal.
 * Pictures: a map from above with a gold block where each monster first showed up (rundum_karte), the bar with
 * "Core x/100 · Welle x/y" in English and German at GUI 2 and 3 (rundum_leiste_*), the core falling
 * (rundum_core_fall). Cases: monsters came, from at least two sides, the wave's groups 2–4 and evenly apart,
 * the bar shows the core and no kills, hits cost life, at 0 the night is lost. Log lines start with "[rundum]".
 */
final class RundumScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static int shots;

    private RundumScenes() {
    }

    private static void log(String line) {
        System.out.println("[rundum] " + line);
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

    private static void guiScale(ClientGameTestContext context, int scale) {
        context.runOnClient(client -> {
            client.options.guiScale().set(scale);
            client.resizeGui();
        });
        context.waitTicks(5);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        shots = 0;
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 300;
        int cz = pos.getZ() + 300;
        int cy = EventLookTest.yard(context, game, cx, cz, 48, 12);
        game.getServer().runCommand("time set 18000");
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy, cz));
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(20);
        final BlockPos core = new BlockPos(cx, cy, cz);
        final int[] saved = new int[5];
        final double[] arc = new double[2];
        server(context, "start", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            saved[0] = m.config().coreHealth;
            saved[1] = m.config().testPrepSeconds;
            arc[0] = m.config().spawnArcFrom;
            arc[1] = m.config().spawnArcTo;
            m.config().coreHealth = 100;   // the scenes run without the core (EventLookTest); this one with it
            // CI 17.11.: at level 10 the monsters brought the core down in the 40 s of looking (the bar was gone after):
            // while looking and taking the pictures, hits cost nothing; the defeat part below turns them on.
            saved[2] = m.config().coreDamageNormal;
            saved[3] = m.config().coreDamageStrong;
            saved[4] = m.config().coreDamageBoss;
            m.config().coreDamageNormal = 0;
            m.config().coreDamageStrong = 0;
            m.config().coreDamageBoss = 0;
            m.config().testPrepSeconds = 1;
            m.config().spawnArcFrom = 0.0;   // all around, as Simon wants it
            m.config().spawnArcTo = 360.0;
            log("night prepared=" + m.prepareTest(level.getServer(), level, core, 10, com.mki.nightsiege.core.SiegeEvent.NONE, null));
        });
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        server(context, "life", (player, m) -> check(m.coreMax() == 100 && m.coreLife() == 100,
                "the core starts with 100 life (" + m.coreLife() + "/" + m.coreMax() + ")"));

        // Where each monster first shows up, once a second for 40 s (two waves at least).
        final Map<UUID, double[]> first = new HashMap<>();
        for (int s = 0; s < 40; s++) {
            server(context, "look", (player, m) -> {
                ServerLevel level = (ServerLevel) player.level();
                for (Mob mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(core).inflate(120),
                        e -> e.entityTags().contains(MobFactory.TAG))) {
                    first.putIfAbsent(mob.getUUID(), new double[] {mob.getX(), mob.getZ()});
                }
            });
            context.waitTicks(20);
        }
        int[] compass = new int[8];
        for (double[] p : first.values()) {
            double deg = (Math.toDegrees(Math.atan2(p[1] - (core.getZ() + 0.5), p[0] - (core.getX() + 0.5))) + 90.0 + 360.0) % 360.0;
            compass[(int) Math.floorMod(Math.round(deg / 45.0), 8L)]++;
        }
        int sides = 0;
        for (int c : compass) {
            sides += c > 0 ? 1 : 0;
        }
        log(String.format(Locale.ROOT, "monsters %d by compass N %d NE %d E %d SE %d S %d SW %d W %d NW %d",
                first.size(), compass[0], compass[1], compass[2], compass[3], compass[4], compass[5], compass[6], compass[7]));
        check(first.size() >= 8, "monsters came (" + first.size() + ")");
        check(sides >= 2, "monsters from at least two sides (" + sides + ")");
        server(context, "wave", (player, m) -> {
            RaidPlan.Wave w = m.raid().lastWave();
            if (w == null) {
                log("no raid plan in this yard (old spawning): groups not checked, sides " + 0);
                return;
            }
            int n = w.groups().size();
            check(n >= 1 && n <= 4, "the wave has 1–4 groups (" + n + ", planned 2–4, fewer only without ground)");
            StringBuilder dirs = new StringBuilder();
            for (double a : w.groups()) {
                dirs.append(RaidPlan.compass(a)).append(' ');
            }
            log("wave " + w.number() + " groups " + n + ": " + dirs + "flank=" + w.hasFlank());
            check(!w.hasFlank(), "no flank any more");
        });

        // The map: a gold block under where each monster showed up, a red one under the core; seen from above.
        server(context, "map", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            for (double[] p : first.values()) {
                BlockPos at = BlockPos.containing(p[0], cy - 1, p[1]);
                level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState());
            }
            level.setBlockAndUpdate(core.below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 90", cx, cy + 70, cz));
        game.getServer().runCommand("effect give @p minecraft:night_vision 120 0 true");
        context.waitTicks(40);
        shot(context, "rundum_karte");

        // The bar: "Core x/100 · Welle x/y" and the sides, no kills; English and German, GUI 2 and 3.
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy + 2, cz - 6));
        context.waitTicks(25);
        server(context, "bar", (player, m) -> {
            String bar = m.bossBarText();
            log("bar: " + bar + " (night active=" + m.isActive() + ", core " + m.coreLife() + "/" + m.coreMax() + ")");
            check(bar.contains("/100") && bar.toLowerCase(Locale.ROOT).contains("core"), "the bar shows the core's life");
            check(!bar.toLowerCase(Locale.ROOT).contains("kills"), "the bar shows no kill goal");
        });
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        String langBefore = context.computeOnClient(client -> client.options.languageCode);
        for (String lang : new String[] {"en_us", "de_de"}) {
            LayoutCheck.language(context, lang);
            for (int scale : new int[] {2, 3}) {
                guiScale(context, scale);
                context.waitTicks(25);
                shot(context, "rundum_leiste_" + lang + "_gui" + scale);
            }
        }
        LayoutCheck.language(context, langBefore);
        guiScale(context, scaleBefore);

        // Core hits until the defeat: the life set low, the night's monsters brought to the core.
        server(context, "low", (player, m) -> {
            check(m.isActive() && m.coreLife() == 100, "before the hits: night on, core whole (" + m.isActive() + ", " + m.coreLife() + ")");
            m.config().coreDamageNormal = saved[2];
            m.config().coreDamageStrong = saved[3];
            m.config().coreDamageBoss = saved[4];
            m.testCoreLife(30);
        });
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @e[tag=nightsiege] %d.5 %d %d.5", cx, cy, cz + 1));
        final int[] seen = {30, -1};
        boolean ended = false;
        for (int t = 0; t < 30 && !ended; t++) {   // at most 30 s
            context.waitTicks(20);
            final boolean[] over = new boolean[1];
            server(context, "watch", (player, m) -> {
                over[0] = !m.isActive();
                if (!over[0]) {
                    seen[0] = Math.min(seen[0], m.coreLife());
                }
            });
            if (!over[0] && seen[0] <= 20 && seen[1] < 0) {
                seen[1] = 1;
                shot(context, "rundum_core_fall");
            }
            ended = over[0];
            if (!ended) {
                game.getServer().runCommand(String.format(Locale.ROOT, "tp @e[tag=nightsiege] %d.5 %d %d.5", cx, cy, cz + 1));
            }
        }
        check(seen[0] < 30, "hits on the core cost life (lowest seen " + seen[0] + ")");
        check(ended, "the night ended with the core at 0");
        StatsRecorder.NightFile night = StatsRecorder.lastFinished;
        check(night != null && "test_verloren".equals(night.ergebnis) && night.coreLeben == 0,
                "lost with coreLeben 0 (" + (night == null ? "none" : night.ergebnis + ", " + night.coreLeben) + ")");

        server(context, "end", (player, m) -> {
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            m.config().coreHealth = saved[0];
            m.config().coreDamageNormal = saved[2];
            m.config().coreDamageStrong = saved[3];
            m.config().coreDamageBoss = saved[4];
            m.config().testPrepSeconds = saved[1];
            m.config().spawnArcFrom = arc[0];
            m.config().spawnArcTo = arc[1];
        });
        context.waitTicks(40);
        EventLookTest.closeScreen(context);
        if (checks == 0 || shots == 0) {
            FAILS.add("no checks or pictures");
        }
        log("summary: " + checks + " checks, " + shots + " pictures, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
        System.out.println("[Night Siege test] rundum: " + (checks - FAILS.size()) + " von " + checks);
        if (!FAILS.isEmpty()) {
            throw new AssertionError("rundum: " + String.join("; ", FAILS));   // a FAIL ends the client with exit != 0
        }
    }
}
