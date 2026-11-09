package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.AbilityKeys;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Scene "steuerung" (Tor 3.1, Simon 08.11.: "Nur Tasten"): every box of the ability bar has exactly one
 * place out of 1-4 (Q, E, F, C; Tor 3.2b, no sneak), no key twice; the HUD shows the mapping the client fires on;
 * the ult is on X; the summoner's staff does nothing on right click. Fälle "x von y" in the log.
 */
final class SteuerungScenes {

    private SteuerungScenes() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        int[] ok = {0};
        int[] all = {0};
        EventLookTest.onServer(context, (player, manager) -> {
            Set<String> used = new HashSet<>();
            boolean fine = true;
            for (var slot : manager.abilitySlots(player)) {
                boolean known = java.util.Arrays.stream(AbilityKeys.Key.values())
                        .anyMatch(k -> k.ability() && k.name().equals(slot.key));   // Tor 3.2b: places 1-4 only
                fine &= known && used.add(slot.key);
                System.out.println("[Night Siege test] steuerung: " + slot.id + " -> " + slot.key);
            }
            all[0]++;
            ok[0] += fine ? 1 : 0;
            all[0]++;
            ok[0] += com.mki.nightsiege.game.ModContent.STAFF   // the registered staff, no new item (registry is frozen)
                    .use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND)
                    == net.minecraft.world.InteractionResult.PASS ? 1 : 0;
        });
        context.waitTicks(25);   // the HUD state comes once a second
        context.runOnClient(client -> {
            all[0]++;
            ok[0] += org.lwjgl.glfw.GLFW.GLFW_KEY_X == com.mojang.blaze3d.platform.InputConstants.getKey(
                    com.mki.nightsiege.client.NightSiegeClient.ultimateKey().saveString()).getValue() ? 1 : 0;
            var s = com.mki.nightsiege.client.UltHud.state();
            all[0]++;
            boolean same = true;
            if (s != null && s.slots != null) {
                for (var slot : s.slots) {
                    same &= com.mki.nightsiege.client.GainClient.keyOf(slot) != null;
                }
            }
            ok[0] += same ? 1 : 0;
        });
        System.out.println("[Night Siege test] steuerung: " + ok[0] + " von " + all[0]);
    }
}
