package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.NightBars;
import com.mki.nightsiege.core.BossBarLayout;
import com.mki.nightsiege.core.NightBarIds;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scene "bossleisten" (Tor 2.3, then Tor 3.0c, Simon 08.11.: "nehmen immer noch zu viel ein", chosen "Klein in die
 * Ecke"): the night bar and four boss bars at once, as in the Vierer-Nacht, with Strength and Slowness active (one
 * effect icon row each). At 1920x1080, GUI scale 2 and 3: first Tor 2.3's look ({@link NightBars.Style#MIDDLE},
 * pictures "bossleisten_4_vorher_gui2/3"), then the corner list ("bossleisten_4_gui2/3", the Glutkoloss hit last in
 * the bright frame). Then a single boss, before and after ("bossleisten_1_vorher_gui2/3", "bossleisten_1_gui2/3").
 * Prints "[bossleisten] OK/FAIL ..." lines; FAIL with no picture.
 */
final class BossleistenScenes {

    private static final List<String> FAILS = new ArrayList<>();
    private static int checks;

    private BossleistenScenes() {
    }

    private static void check(boolean ok, String what) {
        checks++;
        System.out.println("[bossleisten] " + (ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        String[] names = {"Knochenlich", "Sturmhexe", "Glutkoloss", "Kriegsfürst"};
        BossEvent.BossBarColor[] colors = {BossEvent.BossBarColor.RED, BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarColor.RED};
        float[] lives = {0.9f, 0.7f, 0.55f, 0.35f};
        ServerBossEvent[] bars = new ServerBossEvent[5];
        EventLookTest.onServer(context, (player, manager) -> {
            bars[0] = new ServerBossEvent(NightBarIds.make(NightBarIds.Kind.NIGHT),
                    Component.literal("Vierer-Nacht – Stufe 12"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            bars[0].setProgress(0.5f);
            for (int i = 0; i < 4; i++) {
                bars[i + 1] = new ServerBossEvent(NightBarIds.make(NightBarIds.Kind.BOSS), Component.literal(names[i]),
                        colors[i], BossEvent.BossBarOverlay.NOTCHED_10);
                bars[i + 1].setProgress(lives[i]);
            }
            for (ServerBossEvent b : bars) {
                b.addPlayer(player);
            }
            // one good and one bad effect: both icon rows in the top right
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 600, 0));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 600, 0));
        });
        int[] window = context.computeOnClient(client -> new int[] {client.getWindow().getWidth(), client.getWindow().getHeight()});
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(10);
        // the Glutkoloss is hit: it gets the bright frame
        EventLookTest.onServer(context, (player, manager) -> bars[3].setProgress(0.5f));
        context.waitTicks(10);
        int pics = 0;
        for (int boss : new int[] {4, 1}) {
            if (boss == 1) {
                EventLookTest.onServer(context, (player, manager) -> {
                    for (int i = 2; i < bars.length; i++) {
                        bars[i].removeAllPlayers();
                    }
                });
                context.waitTicks(5);
            }
            for (int scale : new int[] {2, 3}) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                });
                context.runOnClient(client -> NightBars.style = NightBars.Style.MIDDLE);
                context.waitTicks(3);
                pics += shot(context, "bossleisten_" + boss + "_vorher_gui" + scale);
                int middle = context.computeOnClient(client -> NightBars.middleBosses);
                check(middle >= 1, boss + " bosses gui " + scale + " before: " + middle + " big boss bar(s) in the middle (want at least 1)");
                context.runOnClient(client -> NightBars.style = NightBars.Style.CORNER);
                context.waitTicks(3);
                pics += shot(context, "bossleisten_" + boss + "_gui" + scale);
                checkCorner(context, boss, scale);
            }
        }
        context.runOnClient(client -> {
            NightBars.style = NightBars.Style.CORNER;
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        context.getInput().resizeWindow(window[0], window[1]);
        EventLookTest.onServer(context, (player, manager) -> {
            bars[0].removeAllPlayers();
            bars[1].removeAllPlayers();
            player.removeAllEffects();
        });
        context.waitTicks(2);
        check(pics > 0, "pictures: " + pics + " of 8");
        System.out.println("[bossleisten] summary: " + checks + " checks, " + FAILS.size() + " failed"
                + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }

    private static int shot(ClientGameTestContext context, String name) {
        Path p = context.takeScreenshot(name);
        return p != null && Files.exists(p) ? 1 : 0;
    }

    /** The corner list as drawn in the last frame: top right, right third, no effect icon covered, the night bar free. */
    private static void checkCorner(ClientGameTestContext context, int boss, int scale) {
        String at = boss + " bosses gui " + scale;
        int[] screen = context.computeOnClient(client -> new int[] {(int) client.getWindow().getGuiScale(), client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(), NightBars.middleBosses, NightBars.lastFocusRow,
                ((com.mki.nightsiege.mixin.client.BossHealthOverlayInvoker) client.gui.hud.getBossOverlay()).nightsiege$events().size()});
        BossBarLayout.Corner c = context.computeOnClient(client -> NightBars.lastCorner);
        List<BossBarLayout.Rect> icons = context.computeOnClient(client -> List.copyOf(NightBars.lastEffects));
        int w = screen[1];
        check(screen[0] == scale, at + ": GUI scale " + screen[0] + " (" + w + "x" + screen[2] + ")");
        check(screen[3] == 0, at + ": " + screen[3] + " boss bars left in the middle (want 0)");
        check(screen[5] == boss + 1, at + ": " + screen[5] + " bars on the client (want " + (boss + 1) + ", the night bar stays)");
        if (c == null) {
            check(false, at + ": no corner list drawn");
            return;
        }
        BossBarLayout.Rect p = c.panel();
        check(c.rows().size() == boss, at + ": " + c.rows().size() + " rows (want " + boss + ")");
        check(p.x() >= w * 2 / 3 && p.x() + p.w() <= w, at + ": list x " + p.x() + ".." + (p.x() + p.w())
                + " in the right third (from " + w * 2 / 3 + "), middle third free");
        check(!p.overlaps(new BossBarLayout.Rect(w / 2 - 91, 3, 182, 16)), at + ": night bar in the middle not covered");
        boolean free = true;
        for (BossBarLayout.Rect icon : icons) {
            free &= !p.overlaps(icon);
        }
        check(icons.size() == 2 && free, at + ": " + icons.size() + " effect icons (want 2), none overlapped (list from y "
                + p.y() + ")");
        check(boss < 2 ? screen[4] == -1 : screen[4] == 2, at + ": highlighted row " + screen[4]
                + (boss < 2 ? " (want none)" : " (want 2, Glutkoloss)"));
    }
}
