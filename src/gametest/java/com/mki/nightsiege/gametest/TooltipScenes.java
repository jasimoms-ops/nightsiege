package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.KitInventory;
import com.mki.nightsiege.client.UltHud;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scene "tooltips" (Simon 08.11.): for each of the 20 subclasses the kit is put on, the way's first
 * ultimate learned, the inventory opened and the mouse rested on Q, F and X in turn; a screenshot of
 * each tooltip ("tooltip_<subclass>_<field>"). Log lines start with "[tooltips]".
 */
final class TooltipScenes {

    private TooltipScenes() {
    }

    private static void log(String line) {
        System.out.println("[tooltips] " + line);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.ensureCore(context, game);
        // survival: in creative the vanilla InventoryScreen.init swaps to the creative inventory (no kit plate, no cells)
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("time set 6000");
        context.waitTicks(10);
        final List<String> learned = new ArrayList<>();
        final java.util.Set<String> subNodes = new java.util.HashSet<>();
        SkillTree.SUBCLASSES.forEach(i -> subNodes.add(i.node()));
        int shots = 0;
        int full = 0;
        for (SkillTree.SubclassInfo info : SkillTree.SUBCLASSES) {
            EventLookTest.onServer(context, (player, manager) -> {
                if (!manager.isRegistered(player)) {
                    manager.register(player);
                }
                PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
                own.chosenClass = info.clazz().name();
                for (String id : learned) {
                    own.setLevel(id, 0);
                }
                learned.clear();
                own.setLevel(info.node(), 1);
                learned.add(info.node());
                // Tor 3.4: every ability of the class (not the other subclasses), and the way's first ultimate
                boolean ult = false;
                for (SkillNode n : manager.skillTree().all()) {
                    if (n.clazz() != info.clazz() || subNodes.contains(n.id())) {
                        continue;
                    }
                    if (n.effect().target() == SkillEffect.Target.ULTIMATE) {
                        if (ult || !info.way().equals(n.path())) {
                            continue;
                        }
                        ult = true;
                    }
                    own.setLevel(n.id(), n.maxLevel());
                    learned.add(n.id());
                }
                // the night kit worn (stash): in survival only then the server sends the abilities and tips
                // (sendUltHud "fighting"), and the inventory shows the kit plate; giveKits only hands out items
                manager.kits().equip(player, 7);
                manager.sendUltHud(player);
            });
            context.waitTicks(30);
            context.runOnClient(client -> client.gui.setScreen(
                    new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
            context.waitTicks(10);
            int tips = context.computeOnClient(client -> UltHud.state() == null || UltHud.state().tips == null
                    ? 0 : UltHud.state().tips.size());
            log(info.key() + " tips=" + tips);
            int fields = 0;
            for (int i = 0; i < 16; i++) {   // Tor 3.4: every field, X and the bar's Q, F, Y, sneak + Y
                final int cell = i;
                double[] at = context.computeOnClient(client -> {
                    int[] c = KitInventory.cellCenter(cell);
                    double scale = client.getWindow().getGuiScale();
                    return c == null ? null : new double[]{c[0] * scale, c[1] * scale};
                });
                if (at == null) {
                    break;
                }
                context.getInput().setCursorPos(at[0], at[1]);
                context.waitTicks(3);
                context.takeScreenshot("tooltip_" + info.key() + "_" + cell);
                shots++;
                fields++;
            }
            log((fields > 0 ? "OK   " : "FAIL ") + info.key() + " fields=" + fields);
            full += fields > 0 ? 1 : 0;
            EventLookTest.closeScreen(context);
            context.waitTicks(5);
        }
        EventLookTest.onServer(context, (player, manager) -> {
            if (manager.kits().hasStash(player)) {
                manager.kits().restore(player);   // own inventory back for the next scenes
            }
        });
        log("RESULT screenshots=" + shots);
        System.out.println("[Night Siege test] tooltips: " + full + " von " + SkillTree.SUBCLASSES.size());
    }
}
