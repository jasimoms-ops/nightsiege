package com.mki.nightsiege.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chef 13.11. 11:30 (warWinStandingDivisor): in a running war leader's round (not the sandbox) the bar reads
 * " · Kriegsführer siegt bei ≤ X Stehenden (stehen: Y)" with X = floor(G / D). Part of the scene "warleader":
 * D = 3 for the check, then back. The leader does not get the round's bar (only the defenders do), so for the
 * pictures the very same title is put on a bar of its own for the leader, the bird's-eye view shut meanwhile.
 */
final class LeisteScenes {

    private static final Pattern STANDING = Pattern.compile("≤ (\\d+)\\D+?(\\d+)\\)");

    private LeisteScenes() {
    }

    private static void log(String line) {
        System.out.println("[Night Siege test] war standing bar " + line);
    }

    static void run(ClientGameTestContext context) {
        final int[] before = new int[1];
        EventLookTest.onServer(context, (player, manager) -> {
            before[0] = manager.config().warWinStandingDivisor;
            manager.config().warWinStandingDivisor = 3;
        });
        context.waitTicks(25);   // the bar's title is renewed every second
        final Object[] bar = new Object[1];
        EventLookTest.onServer(context, (player, manager) -> {
            net.minecraft.server.MinecraftServer server = player.level().getServer();
            com.mki.nightsiege.core.WarLeaderData data = manager.warLeader().testData(server);
            int g = data.defenders.size();
            int standing = 0;
            for (com.mki.nightsiege.core.WarLeaderData.Mate mate : data.defenders) {
                standing += mate.fallen ? 0 : 1;
            }
            String text = manager.bossBarText();
            Matcher m = STANDING.matcher(text);
            boolean found = m.find();
            int x = found ? Integer.parseInt(m.group(1)) : -1;
            int y = found ? Integer.parseInt(m.group(2)) : -1;
            boolean ok = manager.isWarRound() && found && x == g / 3 && y == standing;
            log(String.format(Locale.ROOT, "round=%s G=%d X=%d (want %d) Y=%d (want %d) text=[%s] %s",
                    manager.isWarRound(), g, x, g / 3, y, standing, text, ok ? "OK" : "FAIL"));
            try {
                java.lang.reflect.Method title = com.mki.nightsiege.game.SiegeManager.class.getDeclaredMethod("bossBarTitle");
                title.setAccessible(true);
                net.minecraft.server.bossevents.CustomBossEvent copy = server.getCustomBossEvents().create(
                        net.minecraft.util.RandomSource.create(), net.minecraft.resources.Identifier.parse("nightsiege:test_leiste"),
                        (net.minecraft.network.chat.Component) title.invoke(manager));
                copy.addPlayer(player);
                bar[0] = copy;
            } catch (ReflectiveOperationException | RuntimeException e) {
                log("no picture bar: " + e + " FAIL");
            }
        });
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        int[] window = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(), client.getWindow().getHeight()});
        reopen(context, 60000L);
        context.runOnClient(client -> client.gui.setScreen(null));
        for (int scale : new int[]{2, 3}) {
            if (scale == 3) {
                context.getInput().resizeWindow(1280, 720);   // 854 x 480 offers no scale 3
            }
            context.waitTicks(3);
            context.runOnClient(client -> {
                client.options.guiScale().set(scale);
                client.resizeGui();
            });
            int h = context.computeOnClient(client -> client.getWindow().getScreenHeight());
            context.getInput().setCursorPos(2, Math.max(2, h - 2));   // the mouse in an empty corner
            context.waitTicks(10);
            int real = context.computeOnClient(client -> client.getWindow().getGuiScale());
            log("picture gui=" + real + (real == scale ? " OK" : " FAIL"));
            context.takeScreenshot("war_leiste_g" + scale);
        }
        context.getInput().resizeWindow(window[0], window[1]);
        context.runOnClient(client -> {
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        EventLookTest.onServer(context, (player, manager) -> {
            if (bar[0] instanceof net.minecraft.server.bossevents.CustomBossEvent copy) {
                copy.removeAllPlayers();
                player.level().getServer().getCustomBossEvents().remove(copy);
            }
            manager.config().warWinStandingDivisor = before[0];
        });
        reopen(context, 0L);   // the view comes back with the next update of the round
        context.waitTicks(30);
        context.runOnClient(client -> log("view back=" + (client.gui.screen() instanceof com.mki.nightsiege.client.WarLeaderScreen)));
    }

    /** The view's own "back in a moment": no reopening until then (as its menu's "Kurz zum Spiel"). */
    private static void reopen(ClientGameTestContext context, long inMillis) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field f = com.mki.nightsiege.client.WarLeaderScreen.class.getDeclaredField("reopenAfter");
                f.setAccessible(true);
                f.setLong(null, inMillis <= 0 ? 0L : System.currentTimeMillis() + inMillis);
            } catch (ReflectiveOperationException e) {
                log("reopen: " + e);
            }
        });
    }
}
