package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.FxSettings;
import com.mki.nightsiege.client.WarCursor;
import com.mki.nightsiege.client.WarCursorRender;
import com.mki.nightsiege.client.WarLeaderScreen;
import com.mki.nightsiege.core.WarLeaderData;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Tor 10 / K2: the war leader's ground cross ("fadenkreuz"). A small field of grass, stone and a wall,
 * the bird's-eye view opened on the client with a made-up round (squads, a mortar, defenders, a gate),
 * the cross put on each surface in each state, by day and at night, at GUI scale 2 and 3. Each picture
 * logs where the cross sits on the screen so the contrast of its rim can be measured from the picture.
 */
final class WarCursorScenes {

    private static final int ZOMBIES = 1;
    private static final int MORTAR = 2;
    private static final int OWN = 4;
    private static final int SLING = 5;

    private static int bx;
    private static int by;
    private static int bz;
    private static int pictures;
    private static int problems;
    private static int[] windowBefore;

    private WarCursorScenes() {
    }

    private static void log(String line) {
        System.out.println("[Night Siege test] kreuz " + line);
    }

    /** The scene "fadenkreuz". */
    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        pictures = 0;
        problems = 0;
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        int sizeBefore = context.computeOnClient(client -> FxSettings.crossSize());
        setUp(context, game);
        game.getServer().runCommand("time set 6000");
        open(context);
        for (int scale : new int[]{2, 3}) {
            guiScale(context, scale);
            String g = "g" + scale;
            surfaces(context, g + "_tag", scale == 2);
        }
        guiScale(context, 2);
        // The size setting: small and large on stone.
        for (int size : new int[]{0, 2}) {
            context.runOnClient(client -> FxSettings.setCrossSize(size));
            shot(context, "kreuz_g2_groesse" + size, bx + 3.5, bz + 0.5, ZOMBIES, null, null);
        }
        context.runOnClient(client -> FxSettings.setCrossSize(1));
        // The Esc menu with the new button.
        context.runOnClient(client -> {
            WarCursor.testAimAt(null);
            if (client.gui.screen() instanceof WarLeaderScreen w) {
                w.testMenu(true);
            }
        });
        context.waitTicks(5);
        context.takeScreenshot("kreuz_g2_menu");
        pictures++;
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof WarLeaderScreen w) {
                w.testMenu(false);
            }
        });
        // At night.
        game.getServer().runCommand("time set 18000");
        context.waitTicks(20);
        for (int scale : new int[]{2, 3}) {
            guiScale(context, scale);
            surfaces(context, "g" + scale + "_nacht", false);
        }
        close(context, game, scaleBefore, sizeBefore);
        log(String.format(Locale.ROOT, "result pictures=%d problems=%d %s", pictures, problems,
                problems == 0 && pictures > 0 ? "PASS" : "FAIL"));
    }

    /** In the shader run: a few states by day and at night through each pack. */
    static void shaderShots(ClientGameTestContext context, TestSingleplayerContext game, String prefix) {
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        int sizeBefore = context.computeOnClient(client -> FxSettings.crossSize());
        setUp(context, game);
        guiScale(context, 2);
        for (String time : new String[]{"6000", "18000"}) {
            game.getServer().runCommand("time set " + time);
            open(context);
            context.waitTicks(60);
            String t = time.equals("6000") ? "tag" : "nacht";
            shot(context, prefix + "_kreuz_" + t + "_gras_leer", bx - 6.5, bz + 0.5, -1, null, null);
            shot(context, prefix + "_kreuz_" + t + "_stein_angriff", bx + 3.6, bz + 4.4, ZOMBIES, null, null);
            shot(context, prefix + "_kreuz_" + t + "_gras_maschine", bx - 6.5, bz + 0.5, MORTAR, null, null);
        }
        close(context, game, scaleBefore, sizeBefore);
    }

    /** The states on grass, stone and the wall. */
    private static void surfaces(ClientGameTestContext context, String g, boolean all) {
        double[][] spots = {{bx - 6.5, bz + 0.5}, {bx + 3.5, bz - 2.5}, {bx + 8.5, bz - 1.5}};
        String[] names = {"gras", "stein", "mauer"};
        for (int i = 0; i < spots.length; i++) {
            double x = spots[i][0];
            double z = spots[i][1];
            String n = "kreuz_" + g + "_" + names[i];
            shot(context, n + "_1_leer", x, z, -1, null, null);
            shot(context, n + "_2_befehl", x, z, ZOMBIES, null, null);   // green arrow, on the wall a red sword
            shot(context, n + "_5_grau", x, z, OWN, null, null);
            shot(context, n + "_6_maschine", x, z, MORTAR, null, null);
            if (all || i == 0) {
                shot(context, n + "_4_sonder", x, z, ZOMBIES, WarCursor.Intent.SPECIAL, c -> WarCursor.setPreview(
                        WarCursor.Preview.BLAST, x, by, z, 2.0));
            }
        }
        // Red on a defender (snapped from 0.9 blocks away), on the gate, the wall's ladder preview, out of reach.
        shot(context, "kreuz_" + g + "_stein_3_angriff", bx + 3.6, bz + 4.4, ZOMBIES, null, null);
        shot(context, "kreuz_" + g + "_tor_3_angriff", bx + 7.6, bz - 6.0, ZOMBIES, null, null);
        shot(context, "kreuz_" + g + "_mauer_4_leiter", bx + 6.5, bz + 3.5, ZOMBIES, WarCursor.Intent.SPECIAL,
                c -> WarCursor.setPreview(WarCursor.Preview.LADDER, bx + 7.4, by + 2, bz + 3.5, 0));
        shot(context, "kreuz_" + g + "_stein_5_weit", bx + 13.5, bz + 0.5, MORTAR, null, null);
        shot(context, "kreuz_" + g + "_gras_1_trupp", bx - 4.2, bz + 5.4, -1, null, null);
        // Tor 10 (Chef 03:20): machine and gate highlighted with their life or state.
        shot(context, "kreuz_" + g + "_maschine_leben", bx - 15.0, bz - 3.0, -1, null, null);
        shot(context, "kreuz_" + g + "_tor_leben", bx + 7.6, bz - 6.0, -1, null, null);
    }

    /** One picture: the cross over a spot with a squad picked, maybe an intent and a preview from outside (K1). */
    private static void shot(ClientGameTestContext context, String name, double x, double z, int squad, WarCursor.Intent intent,
                             Consumer<Minecraft> extra) {
        context.runOnClient(client -> {
            WarCursor.clearIntent();
            WarCursor.clearPreview();
            if (intent != null) {
                WarCursor.setIntent(intent, "");
            }
            if (extra != null) {
                extra.accept(client);
            }
            WarCursor.testAimAt(new double[]{x, z});
            if (client.gui.screen() instanceof WarLeaderScreen w) {
                w.testPick(0, squad, null);
            }
        });
        context.waitTicks(4);
        String state = context.computeOnClient(client -> {
            int scale = client.getWindow().getGuiScale();
            return String.format(Locale.ROOT, "screen=%s gui=%d px=%d py=%d rpx=%d %s",
                    client.gui.screen() instanceof WarLeaderScreen ? "war" : String.valueOf(client.gui.screen()), scale,
                    Math.round(WarCursor.testScreen()[0] * scale), Math.round(WarCursor.testScreen()[1] * scale),
                    WarCursorRender.radius() * scale, WarCursor.testState());
        });
        if (!state.startsWith("screen=war") || !state.contains("shown=true") || (name.contains("_g3_") && !state.contains("gui=3"))) {
            problems++;
        }
        log(name + " " + state);
        context.takeScreenshot(name);
        pictures++;
    }

    /** The field: grass west, stone east, a wall of stone bricks on the stone, nothing above. */
    private static void setUp(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.alive(context);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("gamemode creative @p");
        int[] p = context.computeOnClient(client -> new int[]{client.player.getBlockX(), client.player.getBlockZ()});
        bx = p[0] + 30;
        bz = p[1];
        by = context.computeOnClient(client -> client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz));
        for (int y = by; y <= by + 30; y += 10) {
            game.getServer().runCommand("fill %d %d %d %d %d %d air".formatted(bx - 24, y, bz - 16, bx + 24, Math.min(by + 30, y + 9), bz + 16));
        }
        game.getServer().runCommand("fill %d %d %d %d %d %d grass_block".formatted(bx - 24, by - 2, bz - 16, bx - 1, by - 1, bz + 16));
        game.getServer().runCommand("fill %d %d %d %d %d %d stone".formatted(bx, by - 2, bz - 16, bx + 24, by - 1, bz + 16));
        game.getServer().runCommand("fill %d %d %d %d %d %d stone_bricks".formatted(bx + 8, by, bz - 16, bx + 9, by + 3, bz + 16));
        game.getServer().runCommand("fill %d %d %d %d %d %d air".formatted(bx + 8, by, bz - 7, bx + 9, by + 2, bz - 5));   // the gate
        context.waitTicks(40);
        log(String.format(Locale.ROOT, "field at %d %d %d", bx, by, bz));
    }

    /** The bird's-eye view with a made-up round on the client only. */
    private static void open(ClientGameTestContext context) {
        context.runOnClient(client -> WarLeaderScreen.receive(data(), true));
        context.waitTicks(5);
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof WarLeaderScreen w) {
                w.testMove(bx + 0.5, bz + 0.5, 30);
            }
        });
        context.waitTicks(30);
    }

    /** The test window is 854 x 480, where scale 3 is not offered: for 3 it grows to 1280 x 720 (back in close). */
    private static void guiScale(ClientGameTestContext context, int scale) {
        if (windowBefore == null) {
            windowBefore = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(), client.getWindow().getHeight()});
        }
        if (scale >= 3) {
            context.getInput().resizeWindow(1280, 720);
        } else {
            context.getInput().resizeWindow(windowBefore[0], windowBefore[1]);
        }
        context.waitTicks(3);
        context.runOnClient(client -> {
            client.options.guiScale().set(scale);
            client.resizeGui();
        });
        context.waitTicks(5);
    }

    private static void close(ClientGameTestContext context, TestSingleplayerContext game, int scaleBefore, int sizeBefore) {
        if (windowBefore != null) {
            context.getInput().resizeWindow(windowBefore[0], windowBefore[1]);
            windowBefore = null;
        }
        context.runOnClient(client -> {
            WarCursor.reset();
            FxSettings.setCrossSize(sizeBefore);
            client.gui.setScreen(null);
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        game.getServer().runCommand("time set 1500");
        context.waitTicks(10);
    }

    private static WarLeaderData data() {
        WarLeaderData d = new WarLeaderData();
        d.phase = "wave";
        d.leader = "Test";
        d.wave = 1;
        d.waves = 3;
        d.seconds = 140;
        d.gold = 120;
        d.level = 10;
        d.centerX = bx;
        d.centerY = by;
        d.centerZ = bz;
        d.radius = 40;
        d.startX = bx + 0.5;
        d.startZ = bz + 0.5;
        d.sandbox = true;
        WarLeaderData.Point flag = new WarLeaderData.Point();
        flag.index = 0;
        flag.x = bx - 12;
        flag.y = by;
        flag.z = bz - 9;
        d.points.add(flag);
        WarLeaderData.Target gate = new WarLeaderData.Target();
        gate.kind = "gate";
        gate.x = bx + 8;
        gate.y = by;
        gate.z = bz - 6;
        gate.state = "closed";
        d.targets.add(gate);
        d.squads.add(squad(ZOMBIES, "zombies", bx - 4.5, bz + 5.5, false, false));
        d.squads.add(squad(MORTAR, "machine.mortar", bx - 100.5, bz + 0.5, true, false));
        d.squads.add(squad(OWN, "zombies", bx - 10.5, bz + 6.5, false, true));
        WarLeaderData.Squad sling = squad(SLING, "machine.fire", bx - 15.5, bz - 3.5, true, false);
        sling.health = 84;
        sling.maxHealth = 132;
        d.squads.add(sling);
        d.defenders.add(mate("Ritter", bx + 3.5, by, bz + 5.0, 14, 20));
        d.defenders.add(mate("Bogner", bx + 8.5, by + 4, bz + 9.5, 6, 20));
        return d;
    }

    private static WarLeaderData.Squad squad(int id, String item, double x, double z, boolean machine, boolean own) {
        WarLeaderData.Squad s = new WarLeaderData.Squad();
        s.id = id;
        s.item = item;
        s.alive = machine ? 1 : 6;
        s.total = machine ? 1 : 8;
        s.order = machine ? "free" : "point";
        s.x = x;
        s.z = z;
        s.machine = machine;
        s.ownWill = own;
        return s;
    }

    private static WarLeaderData.Mate mate(String name, double x, double y, double z, float health, float max) {
        WarLeaderData.Mate m = new WarLeaderData.Mate();
        m.name = name;
        m.clazz = "ritter";
        m.x = x;
        m.y = y;
        m.z = z;
        m.health = health;
        m.max = max;
        return m;
    }
}
