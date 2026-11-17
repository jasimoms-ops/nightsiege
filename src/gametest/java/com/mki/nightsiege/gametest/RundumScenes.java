package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.NightArc;
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
 * Tor 12 F (Simon 17.11. 15:30): every night the monsters come from one 90° side, another one each night; no kill
 * goal (Simon 15:28). Pictures: a map from above with a gold block where each monster first showed up
 * (rundum_karte), the bar "time · wave x/y · kills N" in English and German at GUI 2 and 3 (rundum_leiste_*).
 * Cases: monsters came, all within tonight's side, the bar shows wave and kills without a goal, the next night
 * has another side. A FAIL ends the run with an AssertionError. Log lines start with "[rundum]".
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
        final int[] saved = new int[2];
        final int[] arc = {NightArc.NONE};
        server(context, "start", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            saved[0] = m.config().spawnArcRotate ? 1 : 0;
            saved[1] = m.config().testPrepSeconds;
            m.config().spawnArcRotate = true;
            m.config().testPrepSeconds = 1;
            log("night prepared=" + m.prepareTest(level.getServer(), level, core, 10, com.mki.nightsiege.core.SiegeEvent.NONE, null));
        });
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        server(context, "arc", (player, m) -> {
            arc[0] = m.nightArcForTest();
            check(arc[0] >= 0 && arc[0] < 8, "tonight has a side (" + arc[0] + ")");
        });
        double width = 90.0;
        double from = NightArc.from(Math.max(0, arc[0]), width);
        log("tonight's side: " + NightArc.key(from) + " to " + NightArc.key(NightArc.to(Math.max(0, arc[0]), width)));

        // Where each monster first shows up, four times a second for 40 s. Flyers (phantoms, vexes) spawn in the side
        // too but are far off within a moment (CI 17.11.: 2 of 19 seen in the south-west): logged, not counted.
        final Map<UUID, double[]> first = new HashMap<>();
        final Map<UUID, String> kinds = new HashMap<>();
        for (int s = 0; s < 160; s++) {
            server(context, "look", (player, m) -> {
                ServerLevel level = (ServerLevel) player.level();
                for (Mob mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(core).inflate(120),
                        e -> e.entityTags().contains(MobFactory.TAG))) {
                    if (first.putIfAbsent(mob.getUUID(), new double[] {mob.getX(), mob.getZ()}) == null) {
                        kinds.put(mob.getUUID(), net.minecraft.world.entity.EntityType.getKey(mob.getType()).getPath()
                                + (mob.isPassenger() ? "(rider)" : ""));
                    }
                }
            });
            context.waitTicks(5);
        }
        int flyers = 0;
        int in = 0;
        int out = 0;
        int[] compass = new int[8];
        double middle = Math.max(0, arc[0]) * 45.0;
        for (Map.Entry<UUID, double[]> e : first.entrySet()) {
            double[] p = e.getValue();
            String kind = kinds.getOrDefault(e.getKey(), "?");
            double deg = (Math.toDegrees(Math.atan2(p[1] - (core.getZ() + 0.5), p[0] - (core.getX() + 0.5))) + 90.0 + 360.0) % 360.0;
            compass[(int) Math.floorMod(Math.round(deg / 45.0), 8L)]++;
            double off = Math.abs(((deg - middle) % 360.0 + 540.0) % 360.0 - 180.0);
            if (kind.startsWith("phantom") || kind.startsWith("vex")) {
                flyers++;
            } else if (off <= width / 2 + 12.0) {   // a little room: the first sight is up to 5 ticks after the spawn
                in++;
            } else {
                out++;
                log(String.format(Locale.ROOT, "outside: %s at %.0f deg (%.0f off the middle) %.0f/%.0f", kind, deg, off,
                        p[0] - core.getX(), p[1] - core.getZ()));
            }
        }
        log(String.format(Locale.ROOT, "monsters %d, flyers %d, in the side %d, outside %d, by compass N %d NE %d E %d SE %d S %d SW %d W %d NW %d",
                first.size(), flyers, in, out, compass[0], compass[1], compass[2], compass[3], compass[4], compass[5], compass[6], compass[7]));
        check(first.size() >= 8, "monsters came (" + first.size() + ")");
        check(out == 0, "every monster came from tonight's side (" + out + " outside)");

        // The map: a gold block under where each monster showed up, a redstone block under the core; from above.
        server(context, "map", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            for (double[] p : first.values()) {
                level.setBlockAndUpdate(BlockPos.containing(p[0], cy - 1, p[1]), net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState());
            }
            level.setBlockAndUpdate(core.below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 90", cx, cy + 70, cz));
        game.getServer().runCommand("effect give @p minecraft:night_vision 120 0 true");
        context.waitTicks(40);
        shot(context, "rundum_karte");

        // The bar: time, wave x/y with its side, kills as a number without a goal; English and German, GUI 2 and 3.
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", cx, cy + 2, cz - 6));
        context.waitTicks(25);
        server(context, "bar", (player, m) -> {
            String bar = m.bossBarText();
            log("bar: " + bar + " (night active=" + m.isActive() + ")");
            check(java.util.regex.Pattern.compile("Wave \\d+/\\d+").matcher(bar).find(), "the bar shows wave x/y");
            check(java.util.regex.Pattern.compile("Kills \\d+(?!/)").matcher(bar).find() && !bar.matches(".*Kills \\d+/.*"),
                    "the bar shows the kills without a goal");
            check(!bar.toLowerCase(Locale.ROOT).contains("core"), "no core life in the bar");
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

        // The next night comes from another side.
        server(context, "next", (player, m) -> {
            ServerLevel level = (ServerLevel) player.level();
            m.abort(level.getServer());
            log("next night prepared=" + m.prepareTest(level.getServer(), level, core, 10, com.mki.nightsiege.core.SiegeEvent.NONE, null));
        });
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        server(context, "next arc", (player, m) -> {
            int next = m.nightArcForTest();
            check(next >= 0 && next != arc[0], "the next night has another side (" + arc[0] + " -> " + next + ")");
        });

        server(context, "end", (player, m) -> {
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            m.config().spawnArcRotate = saved[0] > 0;
            m.config().testPrepSeconds = saved[1];
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
