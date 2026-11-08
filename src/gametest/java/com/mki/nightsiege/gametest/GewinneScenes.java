package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.GainClient;
import com.mki.nightsiege.client.GainWheelScreen;
import com.mki.nightsiege.client.NightKeys;
import com.mki.nightsiege.client.UltHud;
import com.mki.nightsiege.core.UltState;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;

/**
 * Tor 3.2, scene "gewinne": the gain wheel on one key (R). Three gains (ready, cooling down, spent)
 * show at the right edge with their rings; the wheel opens with the second chosen; in the night
 * the key reaches only the wheel, not vanilla and not another mod's mapping on R.
 * Every line starts with "[gewinne]"; pictures gewinne_01_hud and gewinne_02_rad.
 */
final class GewinneScenes {

    private GewinneScenes() {
    }

    private static void log(String line) {
        System.out.println("[gewinne] " + line);
    }

    private static UltState.Slot gain(String id, String icon, int cooldown, int total, boolean spent) {
        UltState.Slot s = new UltState.Slot();
        s.id = id;
        s.icon = icon;
        s.name = "nightsiege.gain." + id;
        s.cooldownTicks = cooldown;
        s.totalTicks = total;
        s.current = spent;
        return s;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.ensureCore(context, game);
        game.getServer().runCommand("time set 18000");
        context.waitTicks(10);
        context.runOnClient(client -> {
            UltState s = new UltState();
            s.live = true;
            s.keys = true;
            s.gains.add(gain("kampfhorn", "minecraft:goat_horn", 0, 1200, false));
            s.gains.add(gain("kletterhaken", "minecraft:tripwire_hook", 300, 400, false));
            s.gains.add(gain("tarnumhang", "minecraft:leather_chestplate", 0, 900, true));
            UltHud.update(s);
        });
        context.waitTicks(2);
        context.takeScreenshot("gewinne_01_hud");
        context.runOnClient(client -> client.gui.setScreen(new GainWheelScreen(1)));
        context.waitTicks(2);
        context.takeScreenshot("gewinne_02_rad");
        boolean open = context.computeOnClient(client -> client.gui.screen() instanceof GainWheelScreen);
        context.runOnClient(client -> client.gui.setScreen(null));
        context.waitTicks(2);
        log("wheel open=" + open + " key=" + context.computeOnClient(client ->
                GainClient.wheelKey().getTranslatedKeyMessage().getString()));

        // Night exclusive: another mod's mapping on R and vanilla do not get the press, the wheel does.
        context.runOnClient(client -> {
            KeyMapping wheel = GainClient.wheelKey();
            KeyMapping other = new KeyMapping("key.gewinne_test.other", org.lwjgl.glfw.GLFW.GLFW_KEY_R,
                    wheel.getCategory());
            KeyMapping.resetMapping();
            InputConstants.Key r = InputConstants.getKey("key.keyboard.r");
            while (wheel.consumeClick() || other.consumeClick()) {
                // drained
            }
            NightKeys.setOwned(true);
            KeyMapping.click(r);
            boolean wheelClick = wheel.consumeClick();
            boolean otherClick = other.consumeClick();
            NightKeys.setOwned(false);
            KeyMapping.click(r);
            boolean otherByDay = other.consumeClick();
            while (wheel.consumeClick()) {
                // the day press of the wheel itself: drained
            }
            other.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            log("night R wheel=" + wheelClick + " other=" + otherClick + " | day other=" + otherByDay);
        });
        context.runOnClient(client -> UltHud.update(new UltState()));
        game.getServer().runCommand("time set 1000");
        context.waitTicks(5);
        log("done");
    }
}
