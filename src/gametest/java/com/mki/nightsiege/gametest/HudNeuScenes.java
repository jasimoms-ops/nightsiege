package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.KitHud;
import com.mki.nightsiege.client.NightBars;
import com.mki.nightsiege.client.PartyHealthHud;
import com.mki.nightsiege.client.UltHud;
import com.mki.nightsiege.core.ActionBarLayout;
import com.mki.nightsiege.core.BossBarLayout;
import com.mki.nightsiege.core.HudSync;
import com.mki.nightsiege.core.NightBarIds;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillEffect;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.core.UltState;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scene "hudneu" (Tor 3.2c, Simon 09.11., draft approved 03:30): the action bar Q E F (C) · X R · 1 2 3 · 4-9 for
 * one subclass of each class (Klingenmeister, Sanitäter, Totenbeschwörer, Elektriker) at 1080p with GUI 2 and 3,
 * two bosses, two effects and a pause running; the same with the old panel as "vorher". Checks: fields in key order,
 * nothing overlapping (chat, party list, boss list, effects, night bar, voice chat), place 3 locked without
 * "Dritte Hand", advancement toasts kept away at night. Screenshots hudneu_*. Log lines "[hudneu]".
 */
final class HudNeuScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static final String[] SUBS = {SkillTree.SUB_BLADEMASTER, SkillTree.SUB_MEDIC, SkillTree.SUB_NECROMANCER,
            SkillTree.SUB_ELECTRICIAN};

    private HudNeuScenes() {
    }

    private static void log(String line) {
        System.out.println("[hudneu] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    /** For the pictures: the bar live, a pause running on Q, X charging, two gains (one loading). */
    private static void edit(UltState s, boolean three) {
        s.live = true;
        if (s.slots != null && !s.slots.isEmpty()) {
            s.slots.get(0).cooldownTicks = 140;
            s.slots.get(0).totalTicks = 200;
        }
        if (s.has) {
            s.cooldownTicks = 300;
            s.totalTicks = 600;
        }
        if (s.gains == null || s.gains.isEmpty()) {
            s.gains = new ArrayList<>();
            for (String icon : new String[]{"minecraft:golden_apple", "minecraft:potion"}) {
                UltState.Slot g = new UltState.Slot();
                g.id = "test";
                g.icon = icon;
                g.name = "nightsiege.gain.dritte_hand";
                s.gains.add(g);
            }
        }
        if (s.gains.size() > 1) {
            s.gains.get(1).cooldownTicks = 280;
            s.gains.get(1).totalTicks = 400;
        }
        s.gainPlaces = three ? 3 : 2;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("gamemode survival @p");   // the kit HUD as at night: survival, kit worn
        game.getServer().runCommand("time set 13000");
        context.waitTicks(10);
        ServerBossEvent[] bars = new ServerBossEvent[3];
        EventLookTest.onServer(context, (player, manager) -> {
            bars[0] = new ServerBossEvent(NightBarIds.make(NightBarIds.Kind.NIGHT),
                    Component.literal("Stufe 12 – Welle 3/5 – 37/60"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            bars[0].setProgress(0.6f);
            bars[1] = new ServerBossEvent(NightBarIds.make(NightBarIds.Kind.BOSS), Component.literal("Knochenlich"),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            bars[1].setProgress(0.8f);
            bars[2] = new ServerBossEvent(NightBarIds.make(NightBarIds.Kind.BOSS), Component.literal("Sturmhexe"),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
            bars[2].setProgress(0.4f);
            for (ServerBossEvent b : bars) {
                b.addPlayer(player);
            }
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 600, 0));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 600, 0));
        });
        int[] window = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(), client.getWindow().getHeight()});
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(10);
        Set<String> subNodes = new HashSet<>();
        SkillTree.SUBCLASSES.forEach(i -> subNodes.add(i.node()));
        List<String> learned = new ArrayList<>();
        int pics = 0;
        for (String sub : SUBS) {
            SkillTree.SubclassInfo info = SkillTree.SUBCLASSES.stream().filter(i -> i.key().equals(sub)).findFirst().orElse(null);
            if (info == null) {
                check(false, sub + ": no such subclass");
                continue;
            }
            boolean three = sub.equals(SkillTree.SUB_MEDIC);   // one with "Dritte Hand", the others with the lock
            context.runOnClient(client -> UltHud.testEdit = s -> edit(s, three));
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
                manager.kits().equip(player, 12);
                manager.sendUltHud(player);
            });
            context.waitTicks(30);
            int mates = three ? 4 : 0;
            if (three) {
                context.runOnClient(client -> {
                    HudSync.Party p = new HudSync.Party();
                    p.mates.add(new HudSync.Mate("a", "Anna", 5, 20, 0));
                    p.mates.add(new HudSync.Mate("b", "Bernhard", 14, 20, 4));
                    p.mates.add(new HudSync.Mate("c", "Clara", 0, 20, 0));
                    p.mates.add(new HudSync.Mate("d", "Dora", 20, 20, 0));
                    PartyHealthHud.set(new com.google.gson.Gson().toJson(p));
                });
            }
            for (int scale : new int[]{2, 3}) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                    KitHud.legacy = true;
                    KitHud.overlayMessage(Component.literal("Anna is low!"));
                });
                context.waitTicks(3);
                pics += shot(context, "hudneu_" + sub + "_vorher_gui" + scale);
                context.runOnClient(client -> KitHud.legacy = false);
                context.waitTicks(3);
                pics += shot(context, "hudneu_" + sub + "_gui" + scale);
                checkBar(context, sub + " gui " + scale, scale, three, mates);
            }
            context.runOnClient(client -> PartyHealthHud.clear());
        }

        // at night (kit worn) no advancement toast: revoke and grant "Stone Age"
        int before = context.computeOnClient(client -> KitHud.blockedToasts);
        game.getServer().runCommand("advancement revoke @p only minecraft:story/mine_stone");
        context.waitTicks(5);
        game.getServer().runCommand("advancement grant @p only minecraft:story/mine_stone");
        context.waitTicks(20);
        int after = context.computeOnClient(client -> KitHud.blockedToasts);
        boolean shown = context.computeOnClient(client -> client.toastManager().getToast(
                net.minecraft.client.gui.components.toasts.AdvancementToast.class, net.minecraft.client.gui.components.toasts.Toast.NO_TOKEN) != null);
        check(after > before && !shown, "toast at night kept away (" + (after - before) + " blocked, shown=" + shown + ")");

        context.runOnClient(client -> {
            UltHud.testEdit = null;
            KitHud.legacy = false;
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        context.getInput().resizeWindow(window[0], window[1]);
        EventLookTest.onServer(context, (player, manager) -> {
            for (ServerBossEvent b : bars) {
                if (b != null) {
                    b.removeAllPlayers();
                }
            }
            player.removeAllEffects();
            PlayerSkills own = manager.save().skillsOf(player.getUUID().toString());
            for (String id : learned) {
                own.setLevel(id, 0);
            }
            if (manager.kits().hasStash(player)) {
                manager.kits().restore(player);
            }
            manager.sendUltHud(player);
        });
        context.waitTicks(5);
        check(pics > 0, "pictures: " + pics + " of 16");
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }

    private static int shot(ClientGameTestContext context, String name) {
        Path p = context.takeScreenshot(name);
        return p != null && Files.exists(p) ? 1 : 0;
    }

    /** The bar as drawn in the last frame: order, nothing overlapping, a pause running, place 3 as it should be. */
    private static void checkBar(ClientGameTestContext context, String at, int scale, boolean three, int mates) {
        ActionBarLayout.Layout l = context.computeOnClient(client -> KitHud.lastLayout);
        List<String> keys = context.computeOnClient(client -> KitHud.lastKeys);
        int loading = context.computeOnClient(client -> KitHud.lastLoading);
        int[] screen = context.computeOnClient(client -> new int[]{(int) client.getWindow().getGuiScale(),
                client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(),
                ActionBarLayout.chatRight(client.options.chatWidth().get(), client.options.chatScale().get())});
        BossBarLayout.Corner corner = context.computeOnClient(client -> NightBars.lastCorner);
        List<BossBarLayout.Rect> icons = context.computeOnClient(client -> List.copyOf(NightBars.lastEffects));
        check(screen[0] == scale, at + ": GUI scale " + screen[0] + " (" + screen[1] + "x" + screen[2] + ")");
        if (l == null) {
            check(false, at + ": no action bar drawn");
            return;
        }
        int w = screen[1];
        int h = screen[2];
        // order
        int last = -1;
        boolean ordered = true;
        for (String k : keys) {
            if (k.startsWith("T")) {
                continue;
            }
            int i = ActionBarLayout.ORDER.indexOf(k);
            ordered &= i > last;
            last = i;
        }
        check(ordered && keys.indexOf("Q") == 0 && keys.contains("X") && keys.contains("9"), at + ": fields in key order " + keys);
        ActionBarLayout.Field third = l.field("3");
        check(third != null && third.kind() == (three ? ActionBarLayout.Kind.GAIN : ActionBarLayout.Kind.LOCKED),
                at + ": place 3 " + (third == null ? "missing" : third.kind()) + " (want " + (three ? "GAIN" : "LOCKED") + ")");
        check(loading >= 1, at + ": " + loading + " field(s) with a pause running (symbol, ring, seconds)");
        // overlaps
        List<ActionBarLayout.Rect> others = new ArrayList<>();
        others.add(ActionBarLayout.chat(h, screen[3], 10));
        others.add(ActionBarLayout.nightBar(w));
        others.addAll(ActionBarLayout.voiceChat(h));
        if (mates > 0) {
            others.add(ActionBarLayout.party(h, mates));
        }
        if (corner != null) {
            BossBarLayout.Rect p = corner.panel();
            others.add(new ActionBarLayout.Rect(p.x(), p.y(), p.w(), p.h()));
        }
        for (BossBarLayout.Rect e : icons) {
            others.add(new ActionBarLayout.Rect(e.x(), e.y(), e.w(), e.h()));
        }
        List<String> hits = new ArrayList<>();
        for (ActionBarLayout.Rect part : l.parts()) {
            for (ActionBarLayout.Rect o : others) {
                if (part.overlaps(o)) {
                    hits.add(part + "/" + o);
                }
            }
        }
        for (int i = 0; i < others.size(); i++) {
            for (int j = i + 1; j < others.size(); j++) {
                if (others.get(i).overlaps(others.get(j))) {
                    hits.add(others.get(i) + "/" + others.get(j));
                }
            }
        }
        List<ActionBarLayout.Rect> fields = new ArrayList<>();
        for (ActionBarLayout.Field f : l.fields()) {
            ActionBarLayout.Rect r = l.screen(f.at());
            for (ActionBarLayout.Rect o : fields) {
                if (r.overlaps(o)) {
                    hits.add(f.key() + "/" + o);
                }
            }
            fields.add(r);
        }
        check(hits.isEmpty(), at + ": nothing overlaps (" + others.size() + " fixed places, bar at x " + l.left()
                + " y " + l.top() + ", corner " + (corner == null ? "none" : "drawn") + ", effects " + icons.size() + ")"
                + (hits.isEmpty() ? "" : " " + hits));
        check(icons.size() == 2 && corner != null && corner.rows().size() == 2, at + ": 2 effects and 2 bosses on screen");
    }
}
