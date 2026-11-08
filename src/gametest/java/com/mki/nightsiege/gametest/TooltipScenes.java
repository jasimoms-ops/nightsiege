package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.KitInventory;
import com.mki.nightsiege.client.UltHud;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;

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
        int shots = 0;
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
                for (SkillNode n : manager.skillTree().all()) {
                    if (n.clazz() == info.clazz() && n.effect().target() == SkillEffect.Target.ULTIMATE
                            && info.way().equals(n.path())) {
                        own.setLevel(n.id(), 2);
                        learned.add(n.id());
                        break;
                    }
                }
                manager.giveKits(player.level().getServer(), (ServerLevel) player.level(), player.blockPosition(), 7, true);
                manager.sendUltHud(player);
            });
            context.waitTicks(30);
            context.runOnClient(client -> client.gui.setScreen(
                    new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
            context.waitTicks(10);
            int tips = context.computeOnClient(client -> UltHud.state() == null || UltHud.state().tips == null
                    ? 0 : UltHud.state().tips.size());
            log(info.key() + " tips=" + tips);
            for (int i = 0; i < 3; i++) {
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
            }
            EventLookTest.closeScreen(context);
            context.waitTicks(5);
        }
        log("RESULT screenshots=" + shots);
    }
}
