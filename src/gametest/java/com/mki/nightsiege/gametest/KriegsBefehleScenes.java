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

    private interface Cond {
        boolean test(ServerPlayer player, com.mki.nightsiege.game.SiegeManager m);
    }

    /**
     * Gives the order on the server (start, may be null when a key gave it), then polls the condition every tick
     * until it holds, at most max ticks after the order. Returns the ticks it took (-1: not within max).
     */
    private static long until(ClientGameTestContext context, int max, EventLookTest.OnServer start, Cond cond) {
        final long[] t0 = {-1};
        final long[] hit = {-1};
        final long[] last = {0};
        server(context, "order", (player, m) -> {
            t0[0] = player.level().getGameTime();
            if (start != null) {
                start.run(player, m);
            }
        });
        for (int t = 0; t < max * 3 + 20 && hit[0] < 0 && last[0] <= max; t++) {
            context.waitTicks(1);
            server(context, "until", (player, m) -> {
                if (t0[0] < 0 || hit[0] >= 0) {
                    return;
                }
                last[0] = player.level().getGameTime() - t0[0];
                if (last[0] <= max && cond.test(player, m)) {
                    hit[0] = last[0];
                }
            });
        }
        context.waitTicks(2);   // the last poll ran
        return hit[0];
    }

    /** Why a unit is not on its way: its target, its path, its mount, who hurt it. */
    private static String why(Mob mob) {
        Mob mover = mob.getVehicle() instanceof Mob mount ? mount : mob;
        net.minecraft.world.level.pathfinder.Path path = mover.getNavigation().getPath();
        return String.format(Locale.ROOT, "[%s target=%s nav=%s reach=%s mount=%s hurtBy=%s]", mob.getName().getString(),
                mob.getTarget() == null ? "-" : mob.getTarget().getName().getString(), mover.getNavigation().getTargetPos(),
                path == null ? "-" : path.canReach(), mover == mob ? "-" : mover.getName().getString(),
                mob.getLastHurtByMob() == null ? "-" : mob.getLastHurtByMob().getName().getString());
    }

    /** "n/m" units, with "Szene: Einheit fehlt" when the squad has none (the scene's fault, not the game's). */
    private static String units(int ok, int all) {
        return ok + "/" + all + (all == 0 ? " (Szene: Einheit fehlt)" : "");
    }

    /** The spots on the screen, for the log ("x,y x,y ..."). */
    private static String onScreen(WarLeaderScreen w, List<double[]> spots) {
        StringBuilder out = new StringBuilder();
        for (double[] b : spots) {
            int[] s = w.testProject(b[0], b[1], b[2]);
            out.append(s == null ? "-" : s[0] + "," + s[1]).append(' ');
        }
        return out.toString().trim();
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
                bones.add(new double[]{mob.getX(), mob.getY(), mob.getZ(), mob.getId()});
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
            // Fresh, in this very tick: the skeletons where the client draws them (the server's spots from the
            // "spread" step are 15+ ticks old, and they walk round their flag).
            net.minecraft.client.multiplayer.ClientLevel cl = net.minecraft.client.Minecraft.getInstance().level;
            double[] mid = spread[1];
            int fresh = 0;
            if (!bones.isEmpty()) {
                mid = new double[3];
                for (double[] b : bones) {
                    net.minecraft.world.entity.Entity e = cl == null ? null : cl.getEntity((int) b[3]);
                    if (e != null) {
                        b[0] = e.getX();
                        b[1] = e.getY();
                        b[2] = e.getZ();
                        fresh++;
                    }
                    mid[0] += b[0] / bones.size();
                    mid[1] += b[1] / bones.size();
                    mid[2] += b[2] / bones.size();
                }
            }
            log("groups: " + fresh + "/" + bones.size() + " skeletons seen by the client");
            int[] at = mid == null ? null : w.testProject(mid[0], mid[1], mid[2]);
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
            spots.add(mid);
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
            log(String.format(Locale.ROOT, "before the flag click: skeletons on the screen %s, click %s, cross %s",
                    onScreen(w, bones), onFlag == null ? "-" : onFlag[0] + "," + onFlag[1], cross));
            if (onFlag != null) {
                click(w, onFlag);
            }
            check(onFlag != null && nearGap < 10 && w.testPicked().equals(String.valueOf(id[1])),
                    String.format(Locale.ROOT, "click on flag squad picks squad: picked=%s (want %d), flag %.0f px away (< 10), click at %d,%d, cross %s",
                            w.testPicked(), id[1], nearGap, onFlag == null ? 0 : onFlag[0], onFlag == null ? 0 : onFlag[1], cross));
            log(String.format(Locale.ROOT, "before the click: skeletons on the screen %s, click %d,%d, cross %s",
                    onScreen(w, bones), at[0], at[1], com.mki.nightsiege.client.WarCursor.testState()));
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

        // 3. Move: the zombies 10 blocks further out; every unit has the new way within 10 ticks, then they are there.
        final double[] goal = new double[2];
        final java.util.Set<java.util.UUID> moved = new java.util.HashSet<>();
        final String[] note = {""};
        long took = until(context, 10, (player, m) -> {
            double[] c = middle(m.warLeader().testMobs(id[0]));
            if (c == null) {
                log("Szene: Einheit fehlt: zombies #" + id[0]);
                return;
            }
            double dx = c[0] - (core.getX() + 0.5);
            double dz = c[2] - (core.getZ() + 0.5);
            double len = Math.max(1.0, Math.hypot(dx, dz));
            goal[0] = c[0] + dx / len * 10;
            goal[1] = c[2] + dz / len * 10;
            m.warLeader().testCommand(player, String.format(Locale.ROOT, "cmd:%d:move:%.1f:%.1f:0", id[0], goal[0], goal[1]));
        }, (player, m) -> {
            List<Mob> mobs = m.warLeader().testMobs(id[0]);
            StringBuilder not = new StringBuilder();
            int on = 0;
            for (Mob mob : mobs) {
                if (headsFor(mob, goal[0], goal[1], 3.5)) {
                    moved.add(mob.getUUID());
                }
                if (moved.contains(mob.getUUID())) {
                    on++;
                } else {
                    not.append(' ').append(why(mob));
                }
            }
            note[0] = units(on, mobs.size()) + " on the new way, order=" + order(m, id[0]) + not;
            return !mobs.isEmpty() && on == mobs.size() && order(m, id[0]).startsWith("move/");
        });
        check(took >= 0, "move: every unit on the new way after " + took + " ticks (<= 10), " + note[0]);
        long there = until(context, 300, null, (player, m) -> order(m, id[0]).startsWith("idle/"));
        check(there >= 0, "move: there after " + there + " ticks (<= 300)");

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
        final String[] wayNote = {""};
        long wayTook = until(context, 20, null, (player, m) -> {
            wayNote[0] = order(m, id[0]);
            return wayNote[0].startsWith("move/aggressive/2/cmd");
        });
        check(wayTook >= 0, "way points: two moves in a row after " + wayTook + " ticks (<= 20), order=" + wayNote[0]);
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

        // 5. Attack a defender: every skeleton has him as its target within 10 ticks; then retreat without fighting.
        final java.util.Set<java.util.UUID> aimed = new java.util.HashSet<>();
        long att = until(context, 10, (player, m) -> m.warLeader().testCommand(player, "cmd:" + id[1] + ":attack:NaN:NaN:0:" + foe[0]),
                (player, m) -> {
                    List<Mob> mobs = m.warLeader().testMobs(id[1]);
                    StringBuilder not = new StringBuilder();
                    int on = 0;
                    for (Mob mob : mobs) {
                        if (mob.getTarget() instanceof ServerPlayer p && p.getScoreboardName().equals(foe[0])) {
                            aimed.add(mob.getUUID());
                        }
                        if (aimed.contains(mob.getUUID())) {
                            on++;
                        } else {
                            not.append(' ').append(why(mob));
                        }
                    }
                    note[0] = units(on, mobs.size()) + " target him, order=" + order(m, id[1]) + not;
                    return !mobs.isEmpty() && on == mobs.size() && order(m, id[1]).startsWith("attack/");
                });
        check(att >= 0, "attack " + foe[0] + ": every skeleton targets him after " + att + " ticks (<= 10), " + note[0]);
        long calmed = until(context, 10, (player, m) -> m.warLeader().testCommand(player, "cmd:" + id[1] + ":retreat:NaN:NaN:0"),
                (player, m) -> {
                    List<Mob> mobs = m.warLeader().testMobs(id[1]);
                    ServerPlayer bot = null;
                    for (ServerPlayer b : SimBots.all()) {
                        if (b.getScoreboardName().equals(foe[0])) {
                            bot = b;
                        }
                    }
                    int calm = 0;
                    int back = 0;
                    StringBuilder not = new StringBuilder();
                    for (Mob mob : mobs) {
                        boolean c = mob.getTarget() == null && (bot == null || !m.warLeader().mayTarget(mob, bot));
                        boolean h = headsFor(mob, home[0] + 0.5, home[1] + 0.5, 6.0);
                        calm += c ? 1 : 0;
                        back += h ? 1 : 0;
                        if (!c || !h) {
                            not.append(' ').append(why(mob));
                        }
                    }
                    note[0] = units(calm, mobs.size()) + " fight nobody, " + back + " head for their point, order=" + order(m, id[1]) + not;
                    return !mobs.isEmpty() && calm == mobs.size() && back == mobs.size()
                            && (order(m, id[1]).startsWith("retreat/") || order(m, id[1]).startsWith("idle/"));
                });
        check(calmed >= 0, "retreat: after " + calmed + " ticks (<= 10) " + note[0]);
        // 6. Attack the gate (a building: the raid's way in): every zombie on its way within 10 ticks.
        final java.util.Set<java.util.UUID> going = new java.util.HashSet<>();
        long gate = until(context, 10, (player, m) -> m.warLeader().testCommand(player,
                String.format(Locale.ROOT, "cmd:%d:attack:%.1f:%.1f:0", id[0], core.getX() + 0.5, core.getZ() - 16.5)), (player, m) -> {
            List<Mob> mobs = m.warLeader().testMobs(id[0]);
            StringBuilder not = new StringBuilder();
            int on = 0;
            for (Mob mob : mobs) {
                if (m.raid().enlisted(mob.getUUID()) || !mob.getNavigation().isDone() || mob.getTarget() != null) {
                    going.add(mob.getUUID());
                }
                if (going.contains(mob.getUUID())) {
                    on++;
                } else {
                    not.append(' ').append(why(mob));
                }
            }
            note[0] = units(on, mobs.size()) + " on their way (raid " + m.raid().states() + "), order=" + order(m, id[0]) + not;
            return !mobs.isEmpty() && on == mobs.size() && order(m, id[0]).startsWith("attack/");
        });
        check(gate >= 0, "attack the gate: after " + gate + " ticks (<= 10) " + note[0]);
        // 7. Hold with H (the client's way), then V (stance), then G (the old ways again).
        view(context, "hold", w -> {
            key(w, GLFW.GLFW_KEY_1, 0);
            key(w, GLFW.GLFW_KEY_H, 0);
        });
        long held = until(context, 10, null, (player, m) -> {
            List<Mob> mobs = m.warLeader().testMobs(id[0]);
            double[] c = middle(mobs);
            int right = 0;
            double d = -1;
            for (ServerPlayer b : SimBots.all()) {
                if (b.getScoreboardName().equals(foe[0]) && c != null) {
                    d = Math.hypot(b.getX() - c[0], b.getZ() - c[2]);
                    for (Mob mob : mobs) {
                        right += m.warLeader().mayTarget(mob, b) == (d <= 6.0) ? 1 : 0;
                    }
                }
            }
            note[0] = String.format(Locale.ROOT, "order=%s, fights only within 6 blocks %s (defender %.1f blocks away)",
                    order(m, id[0]), units(right, mobs.size()), d);
            return !mobs.isEmpty() && order(m, id[0]).startsWith("hold/") && right == mobs.size();
        });
        check(held >= 0, "hold (H): after " + held + " ticks (<= 10), " + note[0]);
        server(context, "hold", (player, m) -> {
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
        // tunneler that came up already is done at once (WarLeader.specialDone). The sandbox's squads come only with
        // the wave, so it gets an order while it is still coming (to its own point): from the start it waits for E.
        server(context, "sandbox keep tunneler", (player, m) -> {
            String done = "none";
            for (WarLeaderData.Squad q : m.warLeader().testData(player.level().getServer()).squads) {
                if ("tunnelgraeber".equals(q.item) && q.point >= 0 && q.point < m.warLeader().testPoints().size()) {
                    BlockPos pt = m.warLeader().testPoints().get(q.point);
                    m.warLeader().testCommand(player, String.format(Locale.ROOT, "cmd:%d:move:%.1f:%.1f:0", q.id, pt.getX() + 0.5, pt.getZ() + 0.5));
                    done = "#" + q.id + " " + q.order + " -> " + order(m, q.id);
                }
            }
            log("sandbox keep tunneler: " + done);
        });
    }

    /**
     * Polls the special orders every tick (at most 40) until each one polled took hold. ti: each order's game time
     * (-1: take t0, the common order). Fills first (ticks), seen (state), counted (units with a job to do).
     */
    private static void pollSpecials(ClientGameTestContext context, String[] items, int[] id, long[] ti, long[] t0,
                                     long[] first, String[] seen, int[] counted, boolean[] poll, int[] boss, BlockPos core) {
        for (int t = 0; t < 40 && java.util.stream.IntStream.range(0, first.length).anyMatch(i -> poll[i] && first[i] < 0); t++) {
            server(context, "specials poll", (player, m) -> {
                long now = player.level().getGameTime();
                for (int i = 0; i < items.length; i++) {
                    if (!poll[i]) {
                        continue;
                    }
                    long dt = now - (ti[i] >= 0 ? ti[i] : t0[0]);
                    int[] job = specialJob(m, i, id[i]);
                    counted[i] = job[1];
                    seen[i] = units(job[0], job[1]) + " (" + job[2] + " mobs, " + job[3] + " babies), order=" + order(m, id[i]);
                    if (first[i] < 0 && dt <= 10 && job[1] > 0 && job[0] == job[1] && order(m, id[i]).startsWith("special/")) {
                        first[i] = dt;
                    }
                }
                if (!poll[items.length]) {
                    return;
                }
                long dt = now - t0[0];
                List<Mob> b = m.warLeader().testMobs(boss[0]);
                Mob mover = b.isEmpty() ? null : b.get(0).getVehicle() instanceof Mob mount ? mount : b.get(0);
                net.minecraft.world.level.pathfinder.Path path = mover == null ? null : mover.getNavigation().getPath();
                seen[items.length] = "order=" + order(m, boss[0]) + " nav=" + (mover == null ? "-" : mover.getNavigation().getTargetPos())
                        + " reach=" + (path == null ? "-" : path.canReach()) + " target=" + (b.isEmpty() || b.get(0).getTarget() == null ? "-"
                        : b.get(0).getTarget().getName().getString());
                if (first[items.length] < 0 && dt <= 10 && !b.isEmpty() && order(m, boss[0]).startsWith("move/")
                        && headsFor(b.get(0), core.getX() + 0.5, core.getZ() - 40.5, 5.0)) {
                    first[items.length] = dt;
                    log("boss on its way after " + dt + " ticks: " + seen[items.length]);
                }
            });
            context.waitTicks(1);
        }
        context.waitTicks(2);   // the last poll ran
    }

    /** The unit's job after E: {at work, counted, mobs, babies} (a baby zombie carries no ladder). */
    private static int[] specialJob(com.mki.nightsiege.game.SiegeManager m, int i, int squad) {
        int ok = 0;
        int counted = 0;
        int babies = 0;
        List<Mob> mobs = m.warLeader().testMobs(squad);
        for (Mob mob : mobs) {
            if (i == 0 && mob.isBaby()) {
                babies++;
                continue;
            }
            counted++;
            boolean job = i < 2 ? Besiegers.busy(mob)
                    : Foes.orderedSpot(mob.getUUID()) != null || (i == 2 && !mob.getNavigation().isDone()) || (i == 2 && mob.isInvisible());
            ok += job ? 1 : 0;
        }
        return new int[]{ok, counted, mobs.size(), babies};
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
        final long[] t0 = new long[1];
        server(context, "specials", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            t0[0] = player.level().getGameTime();
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
        // Each order: the ticks from its own order until it took hold (plan: within 0.5 s = 10 ticks).
        final long[] first = new long[items.length + 1];   // the boss last; -1: not (yet)
        final String[] seen = new String[items.length + 1];
        final int[] counted = new int[items.length];
        final long[] ti = new long[items.length + 1];   // the game time of each order
        java.util.Arrays.fill(first, -1);
        java.util.Arrays.fill(ti, -1);
        final boolean[] poll = new boolean[items.length + 1];
        java.util.Arrays.fill(poll, true);
        pollSpecials(context, items, id, ti, t0, first, seen, counted, poll, boss, core);
        // A squad with no unit at all (died, or only a baby that carries no ladder): the scene's fault - bought again.
        final int[] pointOf = {0, 0, 1, 1, 2};
        final int[] old = id.clone();
        final boolean[] again = new boolean[items.length + 1];
        server(context, "specials missing", (player, m) -> {
            for (int i = 0; i < items.length; i++) {
                if (first[i] < 0 && counted[i] == 0) {
                    again[i] = true;
                    log("Szene: Einheit fehlt: " + items[i] + " #" + id[i] + " (" + seen[i] + "), bought again: "
                            + m.warLeader().testBuy(player.level().getServer(), items[i], pointOf[i]));
                }
            }
        });
        context.waitTicks(2);
        for (int t = 0; t < 600 && java.util.stream.IntStream.range(0, items.length).anyMatch(i -> again[i] && ti[i] < 0); t++) {
            server(context, "specials again", (player, m) -> {
                for (int i = 0; i < items.length; i++) {
                    int n = again[i] && ti[i] < 0 ? m.warLeader().testSquadOf(items[i]) : -1;
                    if (n >= 0 && n != old[i] && !m.warLeader().testMobs(n).isEmpty()) {
                        id[i] = n;
                        ti[i] = player.level().getGameTime();
                        m.warLeader().testCommand(player, String.format(Locale.ROOT, "cmd:%d:special:%.1f:%.1f:0", n, spots[i][0], spots[i][1]));
                        log("Szene: " + items[i] + " came again as #" + n + ", E given");
                    }
                }
            });
            context.waitTicks(1);
        }
        if (java.util.stream.IntStream.range(0, items.length).anyMatch(i -> again[i])) {
            pollSpecials(context, items, id, ti, t0, first, seen, counted, again, boss, core);
        }
        server(context, "specials 0.5 s", (player, m) -> {
            MinecraftServer server = player.level().getServer();
            String[] what = {"ladder carriers at work", "sapper at work", "tunneler has its spot", "grappler has its spot", "standard has its spot"};
            for (int i = 0; i < items.length; i++) {
                check(id[i] >= 0 && first[i] >= 0 && first[i] <= 10,
                        "E " + items[i] + ": " + what[i] + " after " + first[i] + " ticks (<= 10)" + (again[i] ? " (bought again)" : "")
                                + ", now " + seen[i]);
            }
            for (WarLeaderData.Squad q : m.warLeader().testData(server).squads) {
                if (q.id == machine[0]) {
                    check(q.aimed && Math.abs(q.aimX - (core.getX() - 5.5)) < 1 && Math.abs(q.aimZ - (core.getZ() + 3.5)) < 1,
                            String.format(Locale.ROOT, "machine aims by right click: aimed=%s at %.1f,%.1f", q.aimed, q.aimX, q.aimZ));
                }
            }
            check(first[items.length] >= 0 && first[items.length] <= 10,
                    "boss: move within 0.5 s, after " + first[items.length] + " ticks (<= 10), now " + seen[items.length]);
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
