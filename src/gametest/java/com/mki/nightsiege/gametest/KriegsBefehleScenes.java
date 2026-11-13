package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.WarLeaderScreen;
import com.mki.nightsiege.core.WarLeaderData;
import com.mki.nightsiege.game.Besiegers;
import com.mki.nightsiege.game.Foes;
import com.mki.nightsiege.game.SimBots;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Kriegsführer 3, K1 (Simon 13.11.2026: "mehr Gewalt über die Armee"), inside the scene "warleader": the frame,
 * groups 1/2, move, attack a defender and the gate, hold, retreat, way points, the special order of every unit
 * type, the boss steered. Log lines start with "[warleader-k1]".
 */
final class KriegsBefehleScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private KriegsBefehleScenes() {
    }

    private static void log(String line) {
        System.out.println("[warleader-k1] " + line);
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

    private interface OnView {
        void run(WarLeaderScreen w);
    }

    private static void view(ClientGameTestContext context, String step, OnView action) {
        context.runOnClient(client -> {
            if (!(client.gui.screen() instanceof WarLeaderScreen w)) {
                check(false, step + ": no war view (" + client.gui.screen() + ")");
                return;
            }
            try {
                action.run(w);
            } catch (Throwable t) {
                FAILS.add(step + ": " + t);
                log("ERROR in " + step + ": " + t);
                t.printStackTrace(System.out);
            }
        });
    }

    private static final MouseButtonInfo LEFT = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);

    private static void key(WarLeaderScreen w, int key, int mods) {
        w.keyPressed(new KeyEvent(key, 0, mods));
    }

    /** A click without moving (press and let go at once). */
    private static void click(WarLeaderScreen w, int[] at) {
        w.mouseClicked(new MouseButtonEvent(at[0], at[1], LEFT), false);
        w.mouseReleased(new MouseButtonEvent(at[0], at[1], LEFT));
    }

    private static double[] middle(List<Mob> mobs) {
        if (mobs.isEmpty()) {
            return null;
        }
        double x = 0;
        double y = 0;
        double z = 0;
        for (Mob mob : mobs) {
            x += mob.getX();
            y += mob.getY();
            z += mob.getZ();
        }
        return new double[]{x / mobs.size(), y / mobs.size(), z / mobs.size()};
    }

    private static boolean headsFor(Mob mob, double x, double z, double near) {
        Mob mover = mob.getVehicle() instanceof Mob mount ? mount : mob;
        BlockPos t = mover.getNavigation().getTargetPos();
        double dx = mob.getX() - x;
        double dz = mob.getZ() - z;
        if (dx * dx + dz * dz <= near * near) {
            return true;
        }
        if (t == null) {
            return false;
        }
        double tx = t.getX() + 0.5 - x;
        double tz = t.getZ() + 0.5 - z;
        return tx * tx + tz * tz <= near * near;
    }

    private static String order(com.mki.nightsiege.game.SiegeManager m, int id) {
        return m.warLeader().testOrderOf(id);
    }

    /** In the round with defenders (after the nemesis check, before the leader wins). */
    static void main(ClientGameTestContext context, BlockPos core, int[] points) {
        FAILS.clear();
        checks = 0;
        final int[] id = {-1, -1};   // zombies (bought now), skeletons (now or from the build-up)
        final int[] home = new int[2];   // the skeletons' gathering point
        final List<double[]> flags = new ArrayList<>();   // every gathering point (a click near its flag takes the flag)
        final List<double[]> bones = new ArrayList<>();   // where each skeleton stands
        final String[] foe = {""};
        server(context, "room", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            // Room for two fresh squads at their own points: the storming squads of before go (else "full", and the
            // checks would take squads in the middle of the fight, mixed with others).
            int gone = 0;
            for (WarLeaderData.Squad q : m.warLeader().testData(server).squads) {
                if (!q.machine && !q.item.startsWith("boss.")) {
                    for (Mob mob : m.warLeader().testMobs(q.id)) {
                        mob.discard();
                        gone++;
                    }
                }
            }
            log("room: " + gone + " monsters of the squads before taken away");
        });
        context.waitTicks(25);   // the round and the night count them gone
        server(context, "buy", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            m.warLeader().testAddGold(60);
            log("buy: zombies=" + m.warLeader().testBuy(server, "zombies", 0) + " skelette=" + m.warLeader().testBuy(server, "skelette", 1)
                    + " gold=" + m.warLeader().testGold());
            for (ServerPlayer b : SimBots.all()) {
                if (b.isAlive() && m.isDefending(b)) {
                    foe[0] = b.getScoreboardName();
                    b.setHealth(b.getMaxHealth());
                }
            }
        });
        context.waitTicks(110);   // the flare, 3 s, then they are there (the old squads are pruned meanwhile)
        server(context, "came", (player, m) -> {
            id[0] = m.warLeader().testSquadOf("zombies");
            id[1] = m.warLeader().testSquadOf("skelette");
            for (BlockPos p : m.warLeader().testPoints()) {
                flags.add(new double[]{p.getX() + 0.5, p.getY(), p.getZ() + 0.5});
            }
            for (WarLeaderData.Squad q : m.warLeader().testData(player.level().getServer()).squads) {
                if (q.id == id[1] && q.point >= 0 && q.point < m.warLeader().testPoints().size()) {
                    home[0] = m.warLeader().testPoints().get(q.point).getX();
                    home[1] = m.warLeader().testPoints().get(q.point).getZ();
                }
            }
            check(id[0] >= 0 && id[1] >= 0 && !m.warLeader().testMobs(id[0]).isEmpty() && !m.warLeader().testMobs(id[1]).isEmpty(),
                    "squads came: zombies #" + id[0] + " skelette #" + id[1] + " defender=" + foe[0] + " " + m.warLeader().testSquads());
        });
        context.waitTicks(15);

        // 1. The frame around the zombies.
        final double[][] spread = new double[2][];
        server(context, "spread", (player, m) -> {
            double x0 = 1e9;
            double z0 = 1e9;
            double x1 = -1e9;
            double z1 = -1e9;
            double y = 0;
            for (Mob mob : m.warLeader().testMobs(id[0])) {
                x0 = Math.min(x0, mob.getX());
                z0 = Math.min(z0, mob.getZ());
                x1 = Math.max(x1, mob.getX());
                z1 = Math.max(z1, mob.getZ());
                y = mob.getY();
            }
            spread[0] = new double[]{x0, y, z0, x1, z1};
            spread[1] = middle(m.warLeader().testMobs(id[1]));
            for (Mob mob : m.warLeader().testMobs(id[1])) {
                bones.add(new double[]{mob.getX(), mob.getY(), mob.getZ()});
            }
        });
        context.waitTicks(3);
        view(context, "frame camera", w -> w.testMove((spread[0][0] + spread[0][3]) / 2, (spread[0][2] + spread[0][4]) / 2, 24));
        context.waitTicks(15);
        final int[][] corner = new int[2][];
        view(context, "frame", w -> {
            corner[0] = w.testProject(spread[0][0] - 2, spread[0][1], spread[0][2] - 2);
            corner[1] = w.testProject(spread[0][3] + 2, spread[0][1], spread[0][4] + 2);
            if (corner[0] == null || corner[1] == null) {
                check(false, "frame: zombies not on the screen");
                return;
            }
            w.mouseClicked(new MouseButtonEvent(corner[0][0], corner[0][1], LEFT), false);
            w.mouseDragged(new MouseButtonEvent(corner[1][0], corner[1][1], LEFT), corner[1][0] - corner[0][0], corner[1][1] - corner[0][1]);
        });
        context.waitTicks(2);
        context.takeScreenshot("k1_01_frame");
        view(context, "frame let go", w -> {
            if (corner[1] == null) {
                return;
            }
            w.mouseReleased(new MouseButtonEvent(corner[1][0], corner[1][1], LEFT));
            check(w.testPicked().equals(String.valueOf(id[0])), "frame picks the zombies: picked=" + w.testPicked() + " (want " + id[0] + ")");
            key(w, GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);   // group 1
        });
        // 2. A click on the skeletons, group 2; 1 and 2 bring them back, 1 twice moves the camera, Ctrl+A all.
        view(context, "skeleton camera", w -> {
            if (spread[1] != null) {
                w.testMove(spread[1][0], spread[1][2], 24);
            }
        });
        context.waitTicks(15);
        view(context, "groups", w -> {
            int[] at = spread[1] == null ? null : w.testProject(spread[1][0], spread[1][1], spread[1][2]);
            // The skeletons gather round their flag (WarLeader.steerGather, within 2 blocks), so their middle lies on
            // the flag, and a click within 10 pixels of a flag picks the flag (WarLeaderScreen.nearestFlag). Click
            // the skeleton furthest from every flag, as a player clicks the squad and not its flag.
            double flagGap = -1;
            for (double[] b : bones) {
                int[] s = w.testProject(b[0], b[1], b[2]);
                if (s == null) {
                    continue;
                }
                double gap = 1e9;
                for (double[] f : flags) {
                    int[] fs = w.testProject(f[0], f[1], f[2]);
                    if (fs != null) {
                        gap = Math.min(gap, Math.hypot(fs[0] - s[0], fs[1] - 6 - s[1]));
                    }
                }
                if (gap > flagGap) {
                    flagGap = gap;
                    at = s;
                }
            }
            if (at == null) {
                check(false, "groups: skeletons not on the screen");
                return;
            }
            // The squad on its flag: a click there picks the squad, not the flag (the closest spot to a flag).
            int[] onFlag = null;
            double nearGap = 1e9;
            List<double[]> spots = new ArrayList<>(bones);
            spots.add(spread[1]);
            for (double[] b : spots) {
                int[] s = w.testProject(b[0], b[1], b[2]);
                for (double[] f : flags) {
                    int[] fs = s == null ? null : w.testProject(f[0], f[1], f[2]);
                    if (fs != null && Math.hypot(fs[0] - s[0], fs[1] - 6 - s[1]) < nearGap) {
                        nearGap = Math.hypot(fs[0] - s[0], fs[1] - 6 - s[1]);
                        onFlag = s;
                    }
                }
            }
            String cross = com.mki.nightsiege.client.WarCursor.testState();   // the cross before the click (mouse, snap)
            if (onFlag != null) {
                click(w, onFlag);
            }
            check(onFlag != null && nearGap < 10 && w.testPicked().equals(String.valueOf(id[1])),
                    String.format(Locale.ROOT, "click on flag squad picks squad: picked=%s (want %d), flag %.0f px away (< 10), click at %d,%d, cross %s",
                            w.testPicked(), id[1], nearGap, onFlag == null ? 0 : onFlag[0], onFlag == null ? 0 : onFlag[1], cross));
            click(w, at);
            boolean clicked = w.testPicked().equals(String.valueOf(id[1]));
            key(w, GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
            key(w, GLFW.GLFW_KEY_1, 0);
            boolean one = w.testPicked().equals(String.valueOf(id[0]));
            key(w, GLFW.GLFW_KEY_2, 0);
            boolean two = w.testPicked().equals(String.valueOf(id[1]));
            double[] before = WarLeaderScreen.cameraAt(1f);
            key(w, GLFW.GLFW_KEY_1, 0);
            key(w, GLFW.GLFW_KEY_1, 0);
            double[] after = WarLeaderScreen.cameraAt(1f);
            boolean jumped = before != null && after != null
                    && Math.hypot(after[0] - (spread[0][0] + spread[0][3]) / 2, after[2] - (spread[0][2] + spread[0][4]) / 2) < 8.0
                    && Math.hypot(after[0] - before[0], after[2] - before[2]) > 4.0;
            check(clicked && one && two, "click picks the skeletons, Ctrl+1/2 keep, 1/2 bring back: click=" + clicked + " 1=" + one + " 2=" + two
                    + String.format(Locale.ROOT, " (flag %.0f px away)", flagGap));
            check(jumped, String.format(Locale.ROOT, "1 twice: the camera at group 1 (%.0f,%.0f -> %.0f,%.0f)",
                    before == null ? 0 : before[0], before == null ? 0 : before[2], after == null ? 0 : after[0], after == null ? 0 : after[2]));
            key(w, GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
            check(w.testPicked().split(",").length >= 2, "Ctrl+A picks all: " + w.testPicked());
            key(w, GLFW.GLFW_KEY_1, 0);
        });
        context.waitTicks(5);
        context.takeScreenshot("k1_02_groups");

        // 3. Move: the zombies 10 blocks further out, within half a second on their way, then there.
        final double[] goal = new double[2];
        server(context, "move", (player, m) -> {
            double[] c = middle(m.warLeader().testMobs(id[0]));
            double dx = c[0] - (core.getX() + 0.5);
            double dz = c[2] - (core.getZ() + 0.5);
            double len = Math.max(1.0, Math.hypot(dx, dz));
            goal[0] = c[0] + dx / len * 10;
            goal[1] = c[2] + dz / len * 10;
            m.warLeader().testCommand(player, String.format(Locale.ROOT, "cmd:%d:move:%.1f:%.1f:0", id[0], goal[0], goal[1]));
        });
        context.waitTicks(10);
        server(context, "move 0.5 s", (player, m) -> {
            List<Mob> mobs = m.warLeader().testMobs(id[0]);
            int on = 0;
            for (Mob mob : mobs) {
                on += headsFor(mob, goal[0], goal[1], 3.5) ? 1 : 0;
            }
            check(on == mobs.size() && !mobs.isEmpty() && order(m, id[0]).startsWith("move/"),
                    "move: after 0.5 s " + on + "/" + mobs.size() + " on their way, order=" + order(m, id[0]));
        });
        context.waitTicks(150);
        server(context, "move there", (player, m) -> check(order(m, id[0]).startsWith("idle/"),
                "move: there after 8 s, order=" + order(m, id[0])));

        // 4. Way points with the keys: 1, M, Shift+right click twice (a line with points), the markers.
        final double[][] ways = new double[2][];
        view(context, "ways", w -> {
            w.testMove(goal[0], goal[1], 30);
        });
        context.waitTicks(10);
        view(context, "ways click", w -> {
            key(w, GLFW.GLFW_KEY_1, 0);
            key(w, GLFW.GLFW_KEY_M, 0);
            ways[0] = new double[]{goal[0] + 8, goal[1] + 4};
            ways[1] = new double[]{goal[0] + 8, goal[1] - 8};
            MouseButtonInfo shiftRight = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_MOD_SHIFT);
            for (double[] wp : ways) {
                int[] at = w.testProject(wp[0], core.getY(), wp[1]);
                if (at == null) {
                    check(false, "ways: spot not on the screen");
                    return;
                }
                log("intent at way point: " + w.testIntent(at[0], at[1]));
                w.mouseClicked(new MouseButtonEvent(at[0], at[1], shiftRight), false);
            }
            key(w, GLFW.GLFW_KEY_ESCAPE, 0);
        });
        context.waitTicks(3);
        context.takeScreenshot("k1_04_markers");
        context.waitTicks(10);
        server(context, "ways", (player, m) -> check(order(m, id[0]).startsWith("move/aggressive/2/cmd"),
                "way points: two moves in a row, order=" + order(m, id[0])));
        context.waitTicks(5);
        context.takeScreenshot("k1_03_ways");
        final int[] scale = new int[1];
        context.runOnClient(client -> {
            scale[0] = client.options.guiScale().get();
            client.options.guiScale().set(2);
            client.resizeGui();
        });
        context.waitTicks(5);
        context.takeScreenshot("k1_06_scale2");
        int[] window = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(), client.getWindow().getHeight()});
        context.getInput().resizeWindow(1280, 720);   // the CI window (854x480) allows no scale 3
        context.waitTicks(10);
        context.runOnClient(client -> {
            client.options.guiScale().set(3);
            client.resizeGui();
        });
        context.waitTicks(5);
        context.takeScreenshot("k1_07_scale3");
        context.runOnClient(client -> {
            client.options.guiScale().set(scale[0]);
            client.resizeGui();
        });
        context.getInput().resizeWindow(window[0], window[1]);
        context.waitTicks(10);

        // 5. Attack a defender: the skeletons go for him within half a second; then retreat without fighting.
        server(context, "attack defender", (player, m) -> m.warLeader().testCommand(player,
                "cmd:" + id[1] + ":attack:NaN:NaN:0:" + foe[0]));
        context.waitTicks(10);
        server(context, "attack defender 0.5 s", (player, m) -> {
            List<Mob> mobs = m.warLeader().testMobs(id[1]);
            int on = 0;
            for (Mob mob : mobs) {
                on += mob.getTarget() instanceof ServerPlayer p && p.getScoreboardName().equals(foe[0]) ? 1 : 0;
            }
            check(!mobs.isEmpty() && on == mobs.size() && order(m, id[1]).startsWith("attack/"),
                    "attack " + foe[0] + ": " + on + "/" + mobs.size() + " target him, order=" + order(m, id[1]));
            m.warLeader().testCommand(player, "cmd:" + id[1] + ":retreat:NaN:NaN:0");
        });
        context.waitTicks(10);
        server(context, "retreat", (player, m) -> {
            List<Mob> mobs = m.warLeader().testMobs(id[1]);
            ServerPlayer bot = null;
            for (ServerPlayer b : SimBots.all()) {
                if (b.getScoreboardName().equals(foe[0])) {
                    bot = b;
                }
            }
            int calm = 0;
            int back = 0;
            for (Mob mob : mobs) {
                calm += mob.getTarget() == null && (bot == null || !m.warLeader().mayTarget(mob, bot)) ? 1 : 0;
                back += headsFor(mob, home[0] + 0.5, home[1] + 0.5, 6.0) ? 1 : 0;
            }
            check(!mobs.isEmpty() && calm == mobs.size() && back == mobs.size() && (order(m, id[1]).startsWith("retreat/") || order(m, id[1]).startsWith("idle/")),
                    "retreat: " + calm + "/" + mobs.size() + " fight nobody, " + back + " head for their point, order=" + order(m, id[1]));
            // 6. Attack the gate (a building: the raid's way in).
            m.warLeader().testCommand(player, String.format(Locale.ROOT, "cmd:%d:attack:%.1f:%.1f:0", id[0], core.getX() + 0.5, core.getZ() - 16.5));
        });
        context.waitTicks(10);
        server(context, "attack gate", (player, m) -> {
            List<Mob> mobs = m.warLeader().testMobs(id[0]);
            int going = 0;
            for (Mob mob : mobs) {
                going += m.raid().enlisted(mob.getUUID()) || !mob.getNavigation().isDone() || mob.getTarget() != null ? 1 : 0;
            }
            check(!mobs.isEmpty() && going == mobs.size() && order(m, id[0]).startsWith("attack/"),
                    "attack the gate: " + going + "/" + mobs.size() + " on their way (raid " + m.raid().states() + "), order=" + order(m, id[0]));
        });
        context.waitTicks(5);   // the check above runs on the server first, then the key
        // 7. Hold with H (the client's way), then V (stance), then G (the old ways again).
        view(context, "hold", w -> {
            key(w, GLFW.GLFW_KEY_1, 0);
            key(w, GLFW.GLFW_KEY_H, 0);
        });
        context.waitTicks(10);
        server(context, "hold", (player, m) -> {
            List<Mob> mobs = m.warLeader().testMobs(id[0]);
            double[] c = middle(mobs);
            int right = 0;
            for (ServerPlayer b : SimBots.all()) {
                if (b.getScoreboardName().equals(foe[0]) && c != null) {
                    double d = Math.hypot(b.getX() - c[0], b.getZ() - c[2]);
                    for (Mob mob : mobs) {
                        right += m.warLeader().mayTarget(mob, b) == (d <= 6.0) ? 1 : 0;
                    }
                    log(String.format(Locale.ROOT, "hold: defender %.1f blocks from the squad", d));
                }
            }
            check(order(m, id[0]).startsWith("hold/") && right == mobs.size(),
                    "hold (H): order=" + order(m, id[0]) + ", fights only within 6 blocks " + right + "/" + mobs.size());
            m.warLeader().testCommand(player, "stance:" + id[0]);
            check(order(m, id[0]).contains("/defensive/"), "V: stance on, order=" + order(m, id[0]));
            m.warLeader().testCommand(player, "cmd:" + id[0] + ":gather:NaN:NaN:0");
            check(order(m, id[0]).startsWith("gather/") && order(m, id[0]).endsWith("/free"),
                    "G: back to gathering (auto-storm), order=" + order(m, id[0]));
            m.warLeader().testCommand(player, "cmd:" + id[1] + ":gather:NaN:NaN:0");
        });
        context.waitTicks(5);
    }

    /** The sandbox's build-up: one squad of every unit type with a job of its own. */
    static void sandboxBuy(ClientGameTestContext context) {
        server(context, "sandbox buy", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            log("sandbox buy: haken=" + m.warLeader().testBuy(server, "enterhaken", 1)
                    + " standarte=" + m.warLeader().testBuy(server, "standarte", 2)
                    + " tunnel=" + m.warLeader().testBuy(server, "tunnelgraeber", 1)
                    + " spreng=" + m.warLeader().testBuy(server, "sprengmeister", 0)
                    + " leiter=" + m.warLeader().testBuy(server, "leitertraeger", 0));
        });
        // A tunneler of its own will digs once the wave starts and comes up within ~10 s (Foes.tickTunneler); E on a
        // tunneler that came up already is done at once (WarLeader.specialDone) - so it waits on H for the E check.
        context.waitTicks(110);   // the flare, 3 s, then they are there (the wave starts later)
        server(context, "sandbox hold tunneler", (player, m) -> {
            int t = m.warLeader().testSquadOf("tunnelgraeber");
            m.warLeader().testCommand(player, "cmd:" + t + ":hold:NaN:NaN:0");
            log("sandbox hold tunneler: #" + t + " " + m.warLeader().testMobs(t).size() + " mobs, order=" + order(m, t));
        });
    }

    /** In the sandbox: the special order of every unit type, the machine, the boss. */
    static void sandbox(ClientGameTestContext context, BlockPos core) {
        final String[] items = {"leitertraeger", "sprengmeister", "tunnelgraeber", "enterhaken", "standarte"};
        final double[][] spots = {{core.getX() + 3.5, core.getZ() - 18.5}, {core.getX() - 3.5, core.getZ() - 18.5},
                {core.getX() + 16.5, core.getZ() - 30.5}, {core.getX() - 16.5, core.getZ() - 30.5}, {core.getX() + 0.5, core.getZ() - 30.5}};
        final int[] id = new int[items.length];
        final int[] boss = {-1};
        final int[] machine = {-1};
        final int[] ladders = new int[1];
        final double[] bossFrom = new double[1];
        server(context, "specials", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            ladders[0] = com.mki.nightsiege.game.TempBlocks.all((ServerLevel) player.level(), net.minecraft.world.level.block.Blocks.LADDER).size();
            for (int i = 0; i < items.length; i++) {
                id[i] = m.warLeader().testSquadOf(items[i]);
                m.warLeader().testCommand(player, String.format(Locale.ROOT, "cmd:%d:special:%.1f:%.1f:0", id[i], spots[i][0], spots[i][1]));
            }
            for (WarLeaderData.Squad q : m.warLeader().testData(server).squads) {
                if (q.item.startsWith("boss.") && !"coming".equals(q.order)) {
                    boss[0] = q.id;
                }
                if (q.machine && !"coming".equals(q.order)) {
                    machine[0] = q.id;
                }
            }
            log("special squads: " + java.util.Arrays.toString(id) + " boss=" + boss[0] + " machine=" + machine[0] + " " + m.warLeader().testSquads());
            List<Mob> b = m.warLeader().testMobs(boss[0]);
            if (!b.isEmpty()) {
                bossFrom[0] = Math.hypot(b.get(0).getX() - (core.getX() + 0.5), b.get(0).getZ() - (core.getZ() - 40.5));
            }
            m.warLeader().testCommand(player, String.format(Locale.ROOT, "cmd:%d:move:%.1f:%.1f:0", boss[0], core.getX() + 0.5, core.getZ() - 40.5));
            m.warLeader().testCommand(player, String.format(Locale.ROOT, "cmd:%d:attack:%.1f:%.1f:0", machine[0], core.getX() - 5.5, core.getZ() + 3.5));
        });
        context.waitTicks(10);
        server(context, "specials 0.5 s", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            String[] what = {"ladder carriers at work", "sapper at work", "tunneler has its spot", "grappler has its spot", "standard has its spot"};
            for (int i = 0; i < items.length; i++) {
                List<Mob> mobs = m.warLeader().testMobs(id[i]);
                int ok = 0;
                int counted = 0;
                for (Mob mob : mobs) {
                    if (i == 0 && mob.isBaby()) {
                        continue;   // a baby zombie carries no ladder
                    }
                    counted++;
                    boolean job = i < 2 ? Besiegers.busy(mob)
                            : Foes.orderedSpot(mob.getUUID()) != null || (i == 2 && !mob.getNavigation().isDone()) || (i == 2 && mob.isInvisible());
                    ok += job ? 1 : 0;
                }
                check(id[i] >= 0 && counted > 0 && ok == counted && order(m, id[i]).startsWith("special/"),
                        "E " + items[i] + ": " + what[i] + " " + ok + "/" + counted + ", order=" + order(m, id[i]));
            }
            for (WarLeaderData.Squad q : m.warLeader().testData(server).squads) {
                if (q.id == machine[0]) {
                    check(q.aimed && Math.abs(q.aimX - (core.getX() - 5.5)) < 1 && Math.abs(q.aimZ - (core.getZ() + 3.5)) < 1,
                            String.format(Locale.ROOT, "machine aims by right click: aimed=%s at %.1f,%.1f", q.aimed, q.aimX, q.aimZ));
                }
            }
            List<Mob> b = m.warLeader().testMobs(boss[0]);
            check(!b.isEmpty() && order(m, boss[0]).startsWith("move/") && headsFor(b.get(0), core.getX() + 0.5, core.getZ() - 40.5, 5.0),
                    "boss: move within 0.5 s, order=" + order(m, boss[0]) + " nav=" + (b.isEmpty() ? "-" : b.get(0).getNavigation().getTargetPos()));
            m.warLeader().testCommand(player, "now:" + boss[0]);
            m.warLeader().testCommand(player, "now:" + id[1]);   // the sapper goes off now
        });
        context.waitTicks(10);
        server(context, "now", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            int wait = -1;
            for (WarLeaderData.Squad q : m.warLeader().testData(server).squads) {
                if (q.id == boss[0]) {
                    wait = q.specialWait;
                }
            }
            check(wait > 0, "boss: E calls its ability, then it waits " + wait + " s");
            check(m.warLeader().testMobs(id[1]).isEmpty(), "sapper: E twice, it went off (" + m.warLeader().testMobs(id[1]).size() + " left)");
        });
        view(context, "specials picture", w -> {
            w.testMove(core.getX() + 0.5, core.getZ() - 24.5, 40);
            key(w, GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
        });
        context.waitTicks(60);
        context.takeScreenshot("k1_05_specials");
        context.waitTicks(140);
        server(context, "later", (player, m) -> {
            List<Mob> b = m.warLeader().testMobs(boss[0]);
            double now = b.isEmpty() ? -1 : Math.hypot(b.get(0).getX() - (core.getX() + 0.5), b.get(0).getZ() - (core.getZ() - 40.5));
            log(String.format(Locale.ROOT, "boss: %.1f -> %.1f blocks from its spot, order=%s", bossFrom[0], now, order(m, boss[0])));
            int after = com.mki.nightsiege.game.TempBlocks.all((ServerLevel) player.level(), net.minecraft.world.level.block.Blocks.LADDER).size();
            log("ladders " + ladders[0] + " -> " + after + ", carriers " + order(m, id[0]) + ", standard " + order(m, id[4]));
        });
        context.waitTicks(5);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
        System.out.println("[warleader-k1] " + (FAILS.isEmpty() && checks > 0 ? "OK " : "FAIL ") + (checks - FAILS.size()) + " von " + checks);
    }
}
