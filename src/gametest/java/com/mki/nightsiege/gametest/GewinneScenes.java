package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.GainClient;
import com.mki.nightsiege.client.GainWheelScreen;
import com.mki.nightsiege.client.NightKeys;
import com.mki.nightsiege.client.UltHud;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.GainKeys;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;

/**
 * Tor 3.2, scene "gewinne": the gain wheel on one key (R). Two real gains in the kit (one cooling
 * down) show at the right edge with their rings; the wheel opens while R is held; in the night
 * the key reaches only the wheel, not vanilla and not another mod's mapping on R.
 * Every line starts with "[gewinne]"; pictures gewinne_01_hud and gewinne_02_rad.
 */
final class GewinneScenes {

    private GewinneScenes() {
    }

    private static void log(String line) {
        System.out.println("[gewinne] " + line);
    }

    /** The two gains on the warrior's keys: the hook for everyone and the warrior's horn. */
    private static final String[] GAINS = {"kletterhaken", "kampfhorn"};
    private static final int[] oldStage = new int[GAINS.length];
    private static String oldClass;

    /** Every check as "[gewinne] OK/FAIL ..."; nothing seen counts as FAIL. */
    private static void check(boolean ok, String what) {
        log((ok ? "OK   " : "FAIL ") + what);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.ensureCore(context, game);
        game.getServer().runCommand("time set 18000");
        context.waitTicks(10);
        // Tor 3.6: a real warrior with two gains on the keys and the night kit on (only then the
        // server sends the gains), one gain used: its pause runs and shows as a ring.
        EventLookTest.onServer(context, (player, m) -> {
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            oldClass = own.chosenClass;
            own.chosenClass = SkillClass.WARRIOR.name();
            for (int i = 0; i < GAINS.length; i++) {
                oldStage[i] = own.gainStage(SkillClass.WARRIOR, GAINS[i]);
                own.setGainStage(SkillClass.WARRIOR, GAINS[i], 1);
            }
            m.kits().equip(player, 7);
            GainKeys.pauseForTests(player, "kletterhaken", 400);
            m.sendUltHud(player);
        });
        context.waitTicks(30);
        int cooling = context.computeOnClient(client -> UltHud.state() == null ? 0
                : (int) UltHud.state().gains.stream().filter(g -> g.cooldownTicks > 0).count());
        context.takeScreenshot("gewinne_01_hud");
        check(cooling > 0, "HUD: gain cooling down (" + cooling + ")");

        // The wheel as the player opens it: R held past the hold time.
        KeyMapping wheelKey = context.computeOnClient(client -> GainClient.wheelKey());
        context.getInput().holdKey(wheelKey);
        context.waitTicks(12);
        boolean open = context.computeOnClient(client -> client.gui.screen() instanceof GainWheelScreen);
        int entries = context.computeOnClient(client -> UltHud.state() == null ? 0 : UltHud.state().gains.size());
        context.takeScreenshot("gewinne_02_rad");
        check(open, "wheel open on R held");
        check(entries >= 2, "wheel entries (" + entries + ")");
        context.runOnClient(client -> client.gui.setScreen(null));   // closed without using a gain
        context.waitTicks(1);
        context.getInput().releaseKey(wheelKey);
        context.waitTicks(2);
        log("key=" + context.computeOnClient(client -> GainClient.wheelKey().getTranslatedKeyMessage().getString()));

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
            check(wheelClick && !otherClick && otherByDay, "R at night only for the wheel, by day for the others");
        });
        EventLookTest.onServer(context, (player, m) -> {
            GainKeys.pauseForTests(player, "kletterhaken", 0);
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            for (int i = 0; i < GAINS.length; i++) {
                own.setGainStage(SkillClass.WARRIOR, GAINS[i], oldStage[i]);
            }
            own.chosenClass = oldClass;
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);   // own inventory back for the next scenes
            }
        });
        context.runOnClient(client -> UltHud.update(new UltState()));
        game.getServer().runCommand("time set 1000");
        context.waitTicks(5);
        log("done");
    }
}
