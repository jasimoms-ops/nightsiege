package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.KitHud;
import com.mki.nightsiege.client.PartyHealthHud;
import com.mki.nightsiege.core.HudSync;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scene "herzen" (Tor 9b, Simon 09.11.: "hunger plus herzens anzeige mehr vanilla darstellen"): at night in
 * survival with the kit worn, three states at 1080p with GUI 2 and 3 - full (20 health, 4 absorption, armour),
 * much (40 health, 8 absorption: three heart rows, hungry) and low (3 health, 2 food: the shaking rows). Chat
 * lines, a bar message and the healers' party list on screen. Screenshots herzen_*. Log lines "[herzen]".
 */
final class HerzenScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private HerzenScenes() {
    }

    private static void log(String line) {
        System.out.println("[herzen] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            FAILS.add(what);
        }
        log((ok ? "OK " : "FAIL ") + what);
    }

    /** A state: max health, health, absorption level (0 none, 1: 4, 2: 8), food. */
    private record State(String name, double max, float health, int absorption, int food) {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("time set 13000");
        context.waitTicks(10);
        double[] baseMax = new double[1];
        EventLookTest.onServer(context, (player, manager) -> {
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            baseMax[0] = player.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
            manager.kits().equip(player, 12);
            if (player.getArmorValue() == 0) {
                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            }
            manager.sendUltHud(player);
        });
        context.waitTicks(20);
        for (int i = 0; i < 3; i++) {
            game.getServer().runCommand("say Zeile " + (i + 1) + " im Chat");
        }
        context.runOnClient(client -> {
            HudSync.Party p = new HudSync.Party();
            p.mates.add(new HudSync.Mate("a", "Anna", 5, 20, 0));
            p.mates.add(new HudSync.Mate("b", "Bernhard", 14, 20, 4));
            PartyHealthHud.set(new com.google.gson.Gson().toJson(p));
        });
        int[] window = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(), client.getWindow().getHeight()});
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(10);
        boolean worn = context.computeOnClient(client -> KitHud.active());
        check(worn, "kit HUD on in survival at night");
        List<State> states = List.of(new State("voll", 20, 20f, 1, 20), new State("viel", 40, 40f, 2, 6),
                new State("wenig", 20, 3f, 0, 2));
        int pics = 0;
        for (State st : states) {
            for (int scale : new int[]{2, 3}) {
                EventLookTest.onServer(context, (player, manager) -> {
                    player.removeAllEffects();
                    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(st.max());
                    if (st.absorption() > 0) {
                        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 600, st.absorption() - 1));
                    }
                    player.setHealth(st.health());
                    player.getFoodData().setFoodLevel(st.food());
                    player.getFoodData().setSaturation(0f);
                });
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                    KitHud.overlayMessage(Component.literal("Anna is low!"));
                });
                context.waitTicks(5);
                float[] seen = context.computeOnClient(client -> new float[]{client.player.getHealth(),
                        client.player.getMaxHealth(), client.player.getAbsorptionAmount(),
                        client.player.getFoodData().getFoodLevel(), client.player.getArmorValue()});
                log(st.name() + " gui " + scale + ": health " + seen[0] + "/" + seen[1] + " +" + seen[2]
                        + ", food " + seen[3] + ", armour " + seen[4]);
                pics += shot(context, "herzen_" + st.name() + "_gui" + scale);
            }
        }
        context.runOnClient(client -> {
            PartyHealthHud.clear();
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        context.getInput().resizeWindow(window[0], window[1]);
        EventLookTest.onServer(context, (player, manager) -> {
            player.removeAllEffects();
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseMax[0]);
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            if (manager.kits().hasStash(player)) {
                manager.kits().restore(player);
            }
            manager.sendUltHud(player);
        });
        game.getServer().runCommand("time set 1000");
        context.waitTicks(5);
        check(pics == 6, "pictures: " + pics + " of 6");
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }

    private static int shot(ClientGameTestContext context, String name) {
        Path p = context.takeScreenshot(name);
        return p != null && Files.exists(p) ? 1 : 0;
    }
}
