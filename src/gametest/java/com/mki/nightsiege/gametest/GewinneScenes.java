package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.GainClient;
import com.mki.nightsiege.client.KitInventory;
import com.mki.nightsiege.client.NightKeys;
import com.mki.nightsiege.client.UltHud;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.UltState;
import com.mki.nightsiege.game.GainKeys;
import com.mki.nightsiege.game.KitService;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;

import java.util.List;

/**
 * Tor 3.2b, scene "gewinne": the gains on the keys 1-3 (no wheel any more). A guardian with three
 * gains (two places) and the night kit on: the gains' rings show at the right edge; the night keys
 * Q E F C X R 1 2 3 I have their defaults; in the night a press reaches only Night Siege (not vanilla,
 * not another mod's mapping), by day E and 1-3 are vanilla's again; a click on gain place 2 in the
 * inventory brings the third gain there; no kit thing lies on hotbar places 1-3.
 * Every line starts with "[gewinne]"; "OK/FAIL"; the summary "x von y" (0 checks = FAIL).
 * Pictures gewinne_01_hud and gewinne_02_inventar.
 */
final class GewinneScenes {

    private GewinneScenes() {
    }

    private static void log(String line) {
        System.out.println("[gewinne] " + line);
    }

    /** Three gains a guardian can own: the hook for everyone, the signal horn and the trowel. */
    private static final String[] GAINS = {"kletterhaken", "signalhorn", "mauerkelle"};
    private static final int[] oldStage = new int[GAINS.length];
    private static String oldClass;
    private static int ok;
    private static int all;

    /** Every check as "[gewinne] OK/FAIL ...". */
    private static void check(boolean good, String what) {
        all++;
        ok += good ? 1 : 0;
        log((good ? "OK   " : "FAIL ") + what);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        ok = 0;
        all = 0;
        EventLookTest.ensureCore(context, game);
        game.getServer().runCommand("time set 18000");
        context.waitTicks(10);
        List<String>[] before = new List[1];
        boolean[] freePlaces = {true};
        EventLookTest.onServer(context, (player, m) -> {
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            oldClass = own.chosenClass;
            own.chosenClass = SkillClass.GUARDIAN.name();
            for (int i = 0; i < GAINS.length; i++) {
                oldStage[i] = own.gainStage(SkillClass.GUARDIAN, GAINS[i]);
                own.setGainStage(SkillClass.GUARDIAN, GAINS[i], 1);
            }
            if (own.gainKeyChoice != null) {
                own.gainKeyChoice.remove(SkillClass.GUARDIAN.name());
            }
            m.kits().equip(player, 7);
            GainKeys.pauseForTests(player, "kletterhaken", 400);
            before[0] = GainKeys.assigned(m, own, SkillClass.GUARDIAN);
            // Tor 3.2b: the kit's own things (weapons first) never on hotbar places 1-3
            for (int s = 0; s < 3; s++) {
                net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(s);
                if (!stack.isEmpty() && KitService.isKit(stack) && KitService.gainOf(stack).isEmpty()) {
                    freePlaces[0] = false;
                    log("kit thing on place " + (s + 1) + ": " + stack);
                }
            }
            m.sendUltHud(player);
        });
        context.waitTicks(30);
        check(freePlaces[0], "no kit weapon on hotbar places 1-3");
        check(before[0] != null && before[0].size() == 2, "two gain places without Third Hand: " + before[0]);
        int cooling = context.computeOnClient(client -> UltHud.state() == null || UltHud.state().gains == null ? 0
                : (int) UltHud.state().gains.stream().filter(g -> g.cooldownTicks > 0).count());
        context.takeScreenshot("gewinne_01_hud");
        check(cooling > 0, "HUD: gain ring cooling down (" + cooling + ")");

        // The defaults of every night key (Chef 01:38).
        context.runOnClient(client -> {
            String keys = GainClient.slotKey(0).saveString() + " " + GainClient.slotKey(1).saveString() + " "
                    + GainClient.slotKey(2).saveString() + " " + GainClient.slotKey(3).saveString() + " "
                    + com.mki.nightsiege.client.NightSiegeClient.ultimateKey().saveString() + " "
                    + GainClient.secondUltKey().saveString() + " " + GainClient.gainKey(0).saveString() + " "
                    + GainClient.gainKey(1).saveString() + " " + GainClient.gainKey(2).saveString() + " "
                    + GainClient.inventoryKey().saveString();
            check(keys.equals("key.keyboard.q key.keyboard.e key.keyboard.f key.keyboard.c key.keyboard.x "
                    + "key.keyboard.r key.keyboard.1 key.keyboard.2 key.keyboard.3 key.keyboard.i"), "defaults " + keys);
        });

        // Night exclusive: vanilla and another mod's mapping on the key get nothing at night, by day they do.
        context.runOnClient(client -> {
            exclusive(client.options.keyHotbarSlots[0], GainClient.gainKey(0), "1");
            exclusive(client.options.keyHotbarSlots[2], GainClient.gainKey(2), "3");
            exclusive(client.options.keyInventory, GainClient.orderKey(), "e");
            exclusive(null, GainClient.slotKey(3), "c");
            exclusive(null, GainClient.secondUltKey(), "r");
            exclusive(null, GainClient.inventoryKey(), "i");
        });

        // The choice in the inventory: a click on gain place 2 brings the third gain there.
        game.getServer().runCommand("gamemode survival @p");   // creative opens the creative inventory (Lehre Tor 3)
        context.waitTicks(5);
        context.runOnClient(client -> client.gui.setScreen(
                new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
        context.waitTicks(10);
        double[] at = context.computeOnClient(client -> {
            double scale = client.getWindow().getGuiScale();
            for (int i = 0; i < 16; i++) {
                int[] c = KitInventory.cellCenter(i);
                if (c != null && KitInventory.gainAt(c[0], c[1]) == 1) {
                    return new double[]{c[0] * scale, c[1] * scale};
                }
            }
            return null;
        });
        check(at != null, "gain place 2 shown in the inventory");
        if (at != null) {
            context.getInput().setCursorPos(at[0], at[1]);
            context.waitTicks(3);
            context.takeScreenshot("gewinne_02_inventar");
            context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(10);
        }
        List<String>[] after = new List[1];
        EventLookTest.onServer(context, (player, m) -> after[0] = GainKeys.assigned(m,
                m.save().skillsOf(player.getUUID().toString()), SkillClass.GUARDIAN));
        check(before[0] != null && after[0] != null && after[0].size() == 2 && after[0].get(0).equals(before[0].get(0))
                && !after[0].get(1).equals(before[0].get(1)) && !before[0].contains(after[0].get(1)),
                "click on place 2: " + before[0] + " -> " + after[0]);
        EventLookTest.closeScreen(context);
        context.waitTicks(3);

        EventLookTest.onServer(context, (player, m) -> {
            GainKeys.pauseForTests(player, "kletterhaken", 0);
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            for (int i = 0; i < GAINS.length; i++) {
                own.setGainStage(SkillClass.GUARDIAN, GAINS[i], oldStage[i]);
            }
            if (own.gainKeyChoice != null) {
                own.gainKeyChoice.remove(SkillClass.GUARDIAN.name());
            }
            own.chosenClass = oldClass;
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);   // own inventory back for the next scenes
            }
        });
        context.runOnClient(client -> UltHud.update(new UltState()));
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("gamemode creative @p");
        context.waitTicks(5);
        log((all > 0 && ok == all ? "OK   " : "FAIL ") + ok + " von " + all);
        log("done");
    }

    /** One night key: at night only it gets the press; by day vanilla's and the other mod's mapping too. */
    private static void exclusive(KeyMapping vanilla, KeyMapping mine, String name) {
        KeyMapping other = new KeyMapping("key.gewinne_test.other_" + name,
                InputConstants.getKey("key.keyboard." + name).getValue(), mine.getCategory());
        KeyMapping.resetMapping();
        InputConstants.Key key = InputConstants.getKey("key.keyboard." + name);
        drain(mine, other, vanilla);
        NightKeys.setOwned(true);
        KeyMapping.click(key);
        boolean mineNight = mine.consumeClick();
        boolean otherNight = other.consumeClick();
        boolean vanillaNight = vanilla != null && vanilla.consumeClick();
        NightKeys.setOwned(false);
        KeyMapping.click(key);
        boolean otherDay = other.consumeClick();
        boolean vanillaDay = vanilla == null || vanilla.consumeClick();
        drain(mine, other, vanilla);
        other.setKey(InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        check(mineNight && !otherNight && !vanillaNight && otherDay && vanillaDay, "key " + name + ": night mine="
                + mineNight + " other=" + otherNight + " vanilla=" + vanillaNight + " | day other=" + otherDay
                + " vanilla=" + vanillaDay);
    }

    private static void drain(KeyMapping... maps) {
        for (KeyMapping m : maps) {
            while (m != null && m.consumeClick()) {
                // drained
            }
        }
    }
}
