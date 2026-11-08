package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.NightBars;
import com.mki.nightsiege.core.NightBarIds;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;

/**
 * Scene "bossleisten" (Tor 2.3, Simon 08.11.: "wenn mehrere boss bars da sind wird der bildschirm viel zu voll"):
 * the night bar and four boss bars at once, as in the Vierer-Nacht. First with the old look
 * ({@link NightBars#compact} off, picture "bossleisten_4_vorher"), then compact ("bossleisten_4"): one big bar for
 * the boss hit last, three small rows. Last a single boss: it must look as before ("bossleisten_1").
 */
final class BossleistenScenes {

    private BossleistenScenes() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
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
        });
        context.waitTicks(10);
        context.runOnClient(client -> NightBars.compact = false);
        context.waitTicks(2);
        context.takeScreenshot("bossleisten_4_vorher");
        context.runOnClient(client -> NightBars.compact = true);
        // the Glutkoloss is hit: it gets the big bar
        EventLookTest.onServer(context, (player, manager) -> bars[3].setProgress(0.5f));
        context.waitTicks(10);
        context.takeScreenshot("bossleisten_4");
        int[] shown = {0};
        context.runOnClient(client -> shown[0] = ((com.mki.nightsiege.mixin.client.BossHealthOverlayInvoker)
                client.gui.hud.getBossOverlay()).nightsiege$events().size());
        System.out.println("[Night Siege test] bossleisten: bars=" + shown[0] + " (want 5)");
        EventLookTest.onServer(context, (player, manager) -> {
            for (int i = 2; i < bars.length; i++) {
                bars[i].removeAllPlayers();
            }
        });
        context.waitTicks(5);
        context.takeScreenshot("bossleisten_1");
        EventLookTest.onServer(context, (player, manager) -> {
            bars[0].removeAllPlayers();
            bars[1].removeAllPlayers();
        });
        context.waitTicks(2);
    }
}
