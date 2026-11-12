package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.KitHud;
import com.mki.nightsiege.client.PartyHealthHud;
import com.mki.nightsiege.client.UltHud;
import com.mki.nightsiege.core.ActionBarLayout;
import com.mki.nightsiege.core.HudSync;
import com.mki.nightsiege.core.UltState;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
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
 * Scene "herzen" (Z3, Simon 12.11.: "Leben, Hunger und Rüstung näher an die UI ... nur Herzen im anderen Design";
 * until then Tor 9b): at night in survival with the kit worn, four states at 1080p with GUI 2, 3 and 4 - 10 hearts
 * (armour 20, food full, regeneration wave), 20 hearts (half heart, absorption, poison, armour 0, food empty),
 * 30+ hearts (absorption, armour 20, food full) and low (3 health, wither, shaking). Chat lines, a bar message,
 * companions on the bar and the healers' party list on screen; one picture without the kit (vanilla).
 * Screenshots herzen_*. Log lines "[herzen]".
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

    /** A state: max health, health, absorption level (0 none, n: 4n), food, full diamond armour, an effect. */
    private record State(String name, double max, float health, int absorption, int food, boolean armour,
                         Holder<MobEffect> effect) {
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
        context.getInput().setCursorPos(4, 4);   // no tooltip over the picture
        context.waitTicks(10);
        boolean worn = context.computeOnClient(client -> KitHud.active());
        check(worn, "kit HUD on in survival at night");
        List<State> states = List.of(
                new State("zehn", 20, 20f, 0, 20, true, MobEffects.REGENERATION),
                new State("zwanzig", 40, 31f, 2, 0, false, MobEffects.POISON),
                new State("dreissig", 64, 64f, 4, 20, true, null),
                new State("wenig", 20, 3f, 0, 6, false, MobEffects.WITHER));
        int pics = 0;
        for (State st : states) {
            for (int scale : new int[]{2, 3, 4}) {
                EventLookTest.onServer(context, (player, manager) -> {
                    player.removeAllEffects();
                    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(st.max());
                    if (st.absorption() > 0) {
                        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 600, st.absorption() - 1));
                    }
                    if (st.effect() != null) {
                        player.addEffect(new MobEffectInstance(st.effect(), 20 * 600, 0));
                    }
                    player.setHealth(st.health());
                    player.getFoodData().setFoodLevel(st.food());
                    player.getFoodData().setSaturation(0f);
                    EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                    ItemStack[] diamond = {new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};
                    for (int i = 0; i < 4; i++) {
                        player.setItemSlot(slots[i], st.armour() ? diamond[i] : ItemStack.EMPTY);
                    }
                });
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                    KitHud.overlayMessage(Component.literal("Anna is low!"));
                });
                context.waitTicks(5);
                context.runOnClient(client -> companions());
                float[] seen = context.computeOnClient(client -> new float[]{client.player.getHealth(),
                        client.player.getMaxHealth(), client.player.getAbsorptionAmount(),
                        client.player.getFoodData().getFoodLevel(), client.player.getArmorValue()});
                log(st.name() + " gui " + scale + ": health " + seen[0] + "/" + seen[1] + " +" + seen[2]
                        + ", food " + seen[3] + ", armour " + seen[4]);
                int hearts = context.computeOnClient(client -> KitHud.ownHeartSprites);
                pics += shot(context, "herzen_" + st.name() + "_gui" + scale);
                checkRows(context, st.name() + " gui " + scale, seen,
                        context.computeOnClient(client -> KitHud.ownHeartSprites) - hearts);
            }
        }
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
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
        });
        context.waitTicks(10);
        // without the kit everything stays vanilla: vanilla's sprites in vanilla's place
        int hearts = context.computeOnClient(client -> KitHud.ownHeartSprites);
        pics += shot(context, "herzen_ohnekit_gui2");
        boolean off = context.computeOnClient(client -> !KitHud.active() && KitHud.vanillaLift() == 0
                && KitHud.heartSprite("NORMAL", false, false) == null);
        check(off && context.computeOnClient(client -> KitHud.ownHeartSprites) == hearts,
                "without the kit: vanilla hearts in vanilla's place");
        context.runOnClient(client -> {
            PartyHealthHud.clear();
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        context.getInput().resizeWindow(window[0], window[1]);
        game.getServer().runCommand("time set 1000");
        context.waitTicks(5);
        check(pics == 13, "pictures: " + pics + " of 13");
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }

    /** Two kinds of companions in the bar's unit row (Z2 builds the rows; here the row as it is). */
    private static void companions() {
        UltState s = UltHud.state();
        if (s == null || !s.units.isEmpty()) {
            return;
        }
        String[] icons = {"minecraft:bone", "minecraft:iron_block"};
        float[] health = {0.15f, 0.8f};
        for (int i = 0; i < 2; i++) {
            UltState.Unit u = new UltState.Unit();
            u.icon = icons[i];
            u.count = 2 + i;
            u.health = health[i];
            s.units.add(u);
        }
    }

    /** The own hearts drawn, the rows right on the bar and centred, the status row above, nothing over them. */
    private static void checkRows(ClientGameTestContext context, String at, float[] seen, int sprites) {
        ActionBarLayout.Layout l = context.computeOnClient(client -> KitHud.lastLayout);
        List<ActionBarLayout.Rect> bars = context.computeOnClient(client -> KitHud.lastBars);
        int[] screen = context.computeOnClient(client -> new int[]{client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                ActionBarLayout.chatRight(client.options.chatWidth().get(), client.options.chatScale().get()),
                KitHud.chatLift(), KitHud.vanillaLift()});
        check(sprites > 0 && l != null && bars.size() == 2 && screen[4] == 0,
                at + ": own heart sprites " + sprites + ", lift " + screen[4] + ", rows " + bars);
        if (l == null || bars.size() != 2) {
            return;
        }
        ActionBarLayout.Rect frame = l.barOnScreen();
        boolean on = true;
        for (ActionBarLayout.Rect b : bars) {
            on &= b.bottom() <= frame.y() && b.bottom() >= frame.y() - 2;
        }
        int mid = (bars.get(0).x() + bars.get(1).right()) / 2;
        boolean centred = Math.abs(mid - (frame.x() + frame.w() / 2)) <= 1;
        boolean statusAbove = l.screen(l.status()).bottom() <= Math.min(bars.get(0).y(), bars.get(1).y());
        check(on && centred && statusAbove, at + ": rows on the bar " + on + ", centred " + centred
                + ", status row above " + statusAbove + " (frame " + frame + ")");
        int rows = (int) Math.ceil((seen[1] + seen[2]) / 20f);
        List<ActionBarLayout.Rect> others = new ArrayList<>(l.parts());
        others.add(ActionBarLayout.party(screen[1], 2));
        others.add(ActionBarLayout.chat(screen[1] - screen[3], screen[2], 10));
        others.addAll(ActionBarLayout.voiceChat(screen[1]));
        List<String> hits = new ArrayList<>();
        for (ActionBarLayout.Rect b : bars) {
            for (ActionBarLayout.Rect o : others) {
                if (b.overlaps(o)) {
                    hits.add(b + "/" + o);
                }
            }
        }
        check(hits.isEmpty(), at + ": " + rows + " heart row(s), armour " + (int) seen[4] + ", food " + (int) seen[3]
                + ", nothing over the rows (chat up " + screen[3] + ")" + (hits.isEmpty() ? "" : " " + hits));
    }

    private static int shot(ClientGameTestContext context, String name) {
        Path p = context.takeScreenshot(name);
        return p != null && Files.exists(p) ? 1 : 0;
    }
}
