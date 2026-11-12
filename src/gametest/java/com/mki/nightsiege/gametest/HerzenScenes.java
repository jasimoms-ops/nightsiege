package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.KitHud;
import com.mki.nightsiege.client.PartyHealthHud;
import com.mki.nightsiege.client.PetHud;
import com.mki.nightsiege.core.ActionBarLayout;
import com.mki.nightsiege.core.HudSync;
import com.mki.nightsiege.core.PetRows;
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
 * Chef 12.11. 18:10: nothing on the hearts; Z3b 20:15: the hunger symbols in the own design too): at night in
 * survival with the kit worn, four states at 1080p with GUI 2, 3 and 4 - low (3 of 20 health right after 64: vanilla
 * still shows the lost rows, wither, armour 0, food half: 11), 20 hearts (half heart, absorption, poison, armour 0,
 * food empty), 30+ hearts (absorption, armour 20, food full) and the hunger effect (green symbols, food 15).
 * A full chat (10 lines), the bar's warning, five companions' rows (Z2) and the healers' party list on screen; FAIL
 * when chat, warning, hearts and companions' rows overlap. Also the open chat at GUI 3 and one picture without the
 * kit (vanilla). Screenshots herzen_*. Log lines "[herzen]".
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

    /**
     * A state: max health before (64: the rows vanilla still shows after the loss), max health, health, absorption
     * level (0 none, n: 4n), food, full diamond armour, an effect.
     */
    private record State(String name, double before, double max, float health, int absorption, int food,
                         boolean armour, Holder<MobEffect> effect) {
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
        for (int i = 0; i < 10; i++) {
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
        context.getInput().setCursorPos(1919, 540);   // the cursor in an empty corner (lehren: Design 12.11.)
        context.waitTicks(10);
        check(context.computeOnClient(client -> KitHud.active()), "kit HUD on in survival at night");
        List<State> states = List.of(
                new State("wenig", 64, 20, 3f, 0, 11, false, MobEffects.WITHER),
                new State("zwanzig", 40, 40, 31f, 2, 0, false, MobEffects.POISON),
                new State("dreissig", 64, 64, 64f, 4, 20, true, null),
                new State("hunger", 20, 20, 20f, 0, 15, true, MobEffects.HUNGER));
        int pics = 0;
        for (State st : states) {
            for (int scale : new int[]{2, 3, 4}) {
                EventLookTest.onServer(context, (player, manager) -> {
                    player.removeAllEffects();
                    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(st.before());
                    player.setHealth((float) st.before());
                });
                context.waitTicks(3);
                EventLookTest.onServer(context, (player, manager) -> {
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
                    PetHud.set(pets());
                });
                context.waitTicks(4);   // the shot right after the loss: vanilla still shows the old rows
                float[] seen = context.computeOnClient(client -> new float[]{client.player.getHealth(),
                        client.player.getMaxHealth(), client.player.getAbsorptionAmount(),
                        client.player.getFoodData().getFoodLevel(), client.player.getArmorValue()});
                log(st.name() + " gui " + scale + ": health " + seen[0] + "/" + seen[1] + " +" + seen[2]
                        + ", food " + seen[3] + ", armour " + seen[4]);
                int hearts = context.computeOnClient(client -> KitHud.ownHeartSprites);
                int food = context.computeOnClient(client -> KitHud.ownFoodSprites);
                pics += shot(context, "herzen_" + st.name() + "_gui" + scale);
                int foodNow = context.computeOnClient(client -> KitHud.ownFoodSprites);
                check(foodNow > food, st.name() + " gui " + scale + ": own hunger symbols " + (foodNow - food)
                        + " (food " + (int) seen[3] + (st.effect() == MobEffects.HUNGER ? ", hunger effect" : "") + ")");
                checkRows(context, st.name() + " gui " + scale, seen,
                        context.computeOnClient(client -> KitHud.ownHeartSprites) - hearts, false);
            }
        }
        // the open chat at GUI 3: it ends above the hearts as well
        context.runOnClient(client -> {
            client.options.guiScale().set(3);
            client.resizeGui();
            KitHud.overlayMessage(Component.literal("Anna is low!"));
            PetHud.set(pets());
            client.setScreenAndShow(new net.minecraft.client.gui.screens.ChatScreen("", false));
        });
        context.waitTicks(5);
        context.getInput().setCursorPos(1919, 540);
        pics += shot(context, "herzen_chatoffen_gui3");
        checkRows(context, "open chat gui 3", new float[]{0, 64, 16, 20, 20}, 1, true);
        EventLookTest.closeScreen(context);
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
            PetHud.clear();
            client.options.guiScale().set(2);
            client.resizeGui();
        });
        context.waitTicks(10);
        // without the kit everything stays vanilla: vanilla's sprites in vanilla's place
        int hearts = context.computeOnClient(client -> KitHud.ownHeartSprites);
        int food = context.computeOnClient(client -> KitHud.ownFoodSprites);
        pics += shot(context, "herzen_ohnekit_gui2");
        boolean off = context.computeOnClient(client -> !KitHud.active() && KitHud.vanillaLift() == 0
                && KitHud.heartSprite("NORMAL", false, false) == null);
        check(off && context.computeOnClient(client -> KitHud.ownHeartSprites) == hearts
                        && context.computeOnClient(client -> KitHud.ownFoodSprites) == food,
                "without the kit: vanilla hearts and hunger symbols in vanilla's place");
        context.runOnClient(client -> {
            PartyHealthHud.clear();
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        context.getInput().resizeWindow(window[0], window[1]);
        game.getServer().runCommand("time set 1000");
        context.waitTicks(5);
        check(pics == 14, "pictures: " + pics + " of 14");
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }

    /** Five wolves for Z2's rows: the alpha, one nearly dead, one fallen with its pause. */
    private static String pets() {
        PetRows.Pets all = new PetRows.Pets();
        float[] hp = {20f, 14f, 9f, 1.5f, 0f};
        for (int i = 0; i < hp.length; i++) {
            PetRows.Pet p = new PetRows.Pet();
            p.type = "minecraft:wolf";
            p.icon = "minecraft:wolf_spawn_egg";
            p.kind = "wolf";
            p.hp = hp[i];
            p.max = 20f;
            p.alpha = i == 0;
            p.dead = hp[i] <= 0f;
            p.waitTicks = p.dead ? 300 : 0;
            p.born = i;
            all.pets.add(p);
        }
        return new com.google.gson.Gson().toJson(all);
    }

    /**
     * The own hearts drawn, the rows right on the bar and centred, the status row above; chat, warning, heart rows
     * and the companions' rows apart from each other (FAIL on any overlap).
     */
    private static void checkRows(ClientGameTestContext context, String at, float[] seen, int sprites, boolean open) {
        ActionBarLayout.Layout l = context.computeOnClient(client -> KitHud.lastLayout);
        List<ActionBarLayout.Rect> bars = context.computeOnClient(client -> KitHud.lastBars);
        List<ActionBarLayout.Rect> pets = context.computeOnClient(client -> PetHud.lastRects);
        int[] screen = context.computeOnClient(client -> new int[]{client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                ActionBarLayout.chatRight(client.options.chatWidth().get(), client.options.chatScale().get()),
                KitHud.chatLift(), KitHud.vanillaLift(),
                (int) Math.ceil(client.gui.hud.getChat().getLinesPerPage()), client.gui.hud.getChat().isChatFocused() ? 1 : 0});
        check(sprites > 0 && l != null && bars.size() == 2 && screen[4] == 0 && pets.size() >= 5,
                at + ": own heart sprites " + sprites + ", lift " + screen[4] + ", rows " + bars + ", companions " + pets.size());
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
        check(on && centred, at + ": rows on the bar " + on + ", centred " + centred + " (frame " + frame + ")");
        // the chat: closed 10 lines, open as many as fit its page (vanilla: 40 px above the bottom, 9 a line)
        int lines = open ? Math.max(10, screen[5]) : 10;
        ActionBarLayout.Rect chat = ActionBarLayout.chat(screen[1] - screen[3], screen[2], lines);
        ActionBarLayout.Rect warning = l.screen(l.message());
        List<String> names = new ArrayList<>(List.of("chat", "warning", "hearts", "hunger"));
        List<ActionBarLayout.Rect> all = new ArrayList<>(List.of(chat, warning, bars.get(0), bars.get(1)));
        for (int i = 0; i < pets.size(); i++) {
            names.add("companion" + i);
            all.add(pets.get(i));
        }
        all.add(ActionBarLayout.party(screen[1], 2));
        names.add("party");
        List<String> hits = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                boolean bothPets = names.get(i).startsWith("companion") && names.get(j).startsWith("companion");
                boolean partyChat = names.get(j).equals("party") && names.get(i).equals("chat");   // Tor 3.2c's matter
                if (!bothPets && !partyChat && all.get(i).overlaps(all.get(j))) {
                    hits.add(names.get(i) + all.get(i) + "/" + names.get(j) + all.get(j));
                }
            }
        }
        boolean chatAbove = chat.bottom() <= Math.min(bars.get(0).y(), bars.get(1).y())
                || chat.right() <= bars.get(0).x();
        check(hits.isEmpty() && chatAbove, at + ": " + (int) Math.ceil((seen[1] + seen[2]) / 20f) + "+ heart row(s), armour "
                + (int) seen[4] + ", food " + (int) seen[3] + ", chat " + (open ? "open" : "closed") + " up " + screen[3]
                + " ends " + chat.bottom() + ", warning " + warning + ", nothing overlaps"
                + (hits.isEmpty() ? "" : " " + hits));
    }

    private static int shot(ClientGameTestContext context, String name) {
        Path p = context.takeScreenshot(name);
        return p != null && Files.exists(p) ? 1 : 0;
    }
}
