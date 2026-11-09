package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.CrateGains;
import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.CrateRoll;
import com.mki.nightsiege.core.CrateSummary;
import com.mki.nightsiege.client.CrateScreen;
import com.mki.nightsiege.client.CrateSummaryScreen;
import com.mki.nightsiege.game.CrateService;
import net.minecraft.world.item.ItemStack;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.game.GainGear;
import com.mki.nightsiege.game.GainKeys;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tor 5.4, scene "loot": for every class the crate is rolled on the server (the same roll as a crate in
 * the hand) until a gain with a key comes; it is on the keys 1-3 of that class; no retired gain is ever
 * drawn; the archer's fire arrows fill the quiver. Every line starts with "[loot]"; "OK/FAIL"; the summary
 * "x von y" (0 checks = FAIL). Tor 9.1 h: "alle öffnen" and its overview (openAll).
 */
final class LootScenes {

    private LootScenes() {
    }

    private static final Set<String> RETIRED = Set.of("fackeln", "spektralpfeile", "fester_griff", "leuchtschild",
            "zweite_ladung", "recycling", "koecher", "blendgranaten", "wurfspeere", "wurfbeile");
    private static int ok;
    private static int all;

    private static void log(String line) {
        System.out.println("[loot] " + line);
    }

    private static void check(boolean good, String what) {
        all++;
        ok += good ? 1 : 0;
        log((good ? "OK   " : "FAIL ") + what);
    }

    /**
     * Tor 9.1 h: "alle öffnen" rolls every crate of the inventory once, on the same path as a single
     * crate (same counter, the gains land in the kit), and the overview holds exactly these rolls; the
     * window's button sends "crate_all" and the rest comes as an overview too.
     */
    private static void openAll(ClientGameTestContext context, TestSingleplayerContext game) {
        boolean creative = context.computeOnClient(client -> client.player != null && client.player.isCreative());
        game.getServer().runCommand("gamemode survival @p");
        context.waitTicks(5);
        SkillClass clazz = SkillClass.WARRIOR;
        Object[] old = new Object[3];
        int[] fromAll = new int[1];
        EventLookTest.onServer(context, (player, m) -> {
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            PlayerSkills own = m.save().realSkillsOf(player.getUUID().toString());
            old[0] = own.chosenClass;
            old[1] = own.gains.get(clazz.name()) == null ? null : new HashMap<>(own.gains.get(clazz.name()));
            old[2] = own.points;
            own.chosenClass = clazz.name();
            own.gains.put(clazz.name(), new HashMap<>());
            var inv = player.getInventory();
            ItemStack a = CrateService.crate(clazz, CrateKind.NORMAL, 7);
            a.setCount(5);
            ItemStack b = CrateService.crate(clazz, CrateKind.EVENT, 7);
            b.setCount(2);
            inv.setItem(30, a);
            inv.setItem(31, b);
            int before = CrateService.revealedForTests;
            int points0 = own.points;
            CrateSummary s = m.crates().openAll(player);
            int rolled = CrateService.revealedForTests - before;
            fromAll[0] = s == null ? -1 : s.crates;
            check(s != null && s.crates == 7, "alle öffnen: 7 Kisten ergeben " + fromAll[0] + " Gewinne");
            check(rolled == 7, "alle öffnen: jede Kiste genau einmal gezogen wie einzeln (" + rolled + ")");
            check(inv.getItem(30).isEmpty() && inv.getItem(31).isEmpty(), "alle öffnen: keine Kiste bleibt im Inventar");
            if (s != null) {
                int counted = s.entries.stream().mapToInt(e -> e.count).sum();
                check(counted == 7 && s.fresh + s.duplicates == 7, "Übersicht: " + counted + " Einträge, "
                        + s.fresh + " neu + " + s.duplicates + " doppelt");
                check(s.points == own.points - points0, "Übersicht: Punkte " + s.points + " = Zuwachs " + (own.points - points0));
                boolean inKit = s.entries.stream().filter(e -> !"glueck".equals(e.gain))
                        .allMatch(e -> own.gainStage(clazz, e.gain) == e.stage);
                check(inKit, "Übersicht: jede Stufe steht so im Kit");
            }
            // single crate: one crate, one roll, as before
            inv.setItem(30, CrateService.crate(clazz, CrateKind.NORMAL, 7));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, CrateService.crate(clazz, CrateKind.NORMAL, 7));
        });
        context.waitTicks(10);
        CrateSummary shown = context.computeOnClient(client ->
                client.gui.screen() instanceof CrateSummaryScreen sc ? sc.summary() : null);
        check(shown != null && shown.crates == fromAll[0] && shown.entries.stream().mapToInt(e -> e.count).sum() == fromAll[0],
                "Übersichtsfenster zeigt " + (shown == null ? "-" : shown.crates + " Kisten, " + shown.entries.size() + " Zeilen"));
        context.takeScreenshot("loot_kisten_alle");
        EventLookTest.closeScreen(context);
        int[] single = new int[2];
        EventLookTest.onServer(context, (player, m) -> {
            int before = CrateService.revealedForTests;
            // sneaking too: only this one crate (the sneak way to "alle öffnen" is gone, Prüfer 09.11.)
            player.setShiftKeyDown(true);
            m.crates().open(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            player.setShiftKeyDown(false);
            single[0] = CrateService.revealedForTests - before;
            single[1] = player.getMainHandItem().getCount();
            check(single[0] == 1 && single[1] == 0 && !player.getInventory().getItem(30).isEmpty(),
                    "einzeln (geschlichen): 1 Kiste ergibt " + single[0] + " Gewinn, Hand leer, Rest bleibt");
        });
        context.waitTicks(10);
        String button = context.computeOnClient(client -> client.gui.screen() instanceof CrateScreen ? "crate" : String.valueOf(client.gui.screen()));
        check("crate".equals(button), "einzeln: das Band-Fenster bleibt (" + button + ")");
        // the window's "Alle öffnen (1)": the client only sends "crate_all", the server rolls the rest
        context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.mki.nightsiege.game.CoreNetworking.CoreActionPayload("crate_all")));
        context.waitTicks(15);
        CrateSummary rest = context.computeOnClient(client ->
                client.gui.screen() instanceof CrateSummaryScreen sc ? sc.summary() : null);
        check(rest != null && rest.crates == 1, "Knopf \"Alle öffnen\": der Rest kommt als Übersicht (" + (rest == null ? "-" : rest.crates) + ")");
        EventLookTest.closeScreen(context);
        EventLookTest.onServer(context, (player, m) -> {
            PlayerSkills own = m.save().realSkillsOf(player.getUUID().toString());
            own.chosenClass = (String) old[0];
            if (old[1] == null) {
                own.gains.remove(clazz.name());
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Integer> g = (Map<String, Integer>) old[1];
                own.gains.put(clazz.name(), g);
            }
            own.points = (int) old[2];
        });
        context.waitTicks(5);
        if (creative) {
            game.getServer().runCommand("gamemode creative @p");
            context.waitTicks(5);
        }
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        ok = 0;
        all = 0;
        EventLookTest.ensureCore(context, game);
        game.getServer().runCommand("time set 18000");
        context.waitTicks(10);
        for (SkillClass clazz : new SkillClass[]{SkillClass.WARRIOR, SkillClass.ARCHER, SkillClass.GUARDIAN,
                SkillClass.ENGINEER, SkillClass.SUMMONER}) {
            EventLookTest.onServer(context, (player, m) -> {
                if (!m.isRegistered(player)) {
                    m.register(player);
                }
                PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
                String oldClass = own.chosenClass;
                Map<String, Integer> oldGains = own.gains.get(clazz.name()) == null ? null
                        : new HashMap<>(own.gains.get(clazz.name()));
                int oldPoints = own.points;
                own.chosenClass = clazz.name();
                own.gains.put(clazz.name(), new HashMap<>());
                if (own.gainKeyChoice != null) {
                    own.gainKeyChoice.remove(clazz.name());
                }
                String won = null;
                String retired = null;
                int rolls = 0;
                for (; rolls < 400 && won == null; rolls++) {
                    CrateRoll roll = m.crates().roll(own, clazz, CrateKind.values()[0], 7);
                    if (roll == null || roll.gain == null) {
                        break;
                    }
                    if (RETIRED.contains(roll.gain) && retired == null) {
                        retired = roll.gain;
                    }
                    // A way-bound key gain off the player's way (the bell without the way of the dead,
                    // GainKeys.owned) may come out while the way is still open (CrateGains.drawable), but it
                    // only goes onto the keys on its way – so it does not count as the new key gain here.
                    CrateGains.Gain drawn = CrateGains.get(roll.gain);
                    boolean heldBack = drawn != null && !drawn.way().isEmpty()
                            && !GainKeys.owned(m, own, clazz).contains(roll.gain);
                    if (!roll.duplicate && CrateGains.keyAbility(roll.gain) && !heldBack) {
                        won = roll.gain;
                    }
                }
                check(retired == null, clazz + ": no retired gain in " + rolls + " crates" + (retired == null ? "" : " (" + retired + ")"));
                check(won != null, clazz + ": a key gain came out of the crate after " + rolls + ": " + won);
                List<String> keys = GainKeys.assigned(m, own, clazz);
                String key = com.mki.nightsiege.core.GainCharges.FIRST_AID.equals(won)
                        ? com.mki.nightsiege.core.GainCharges.POTION : won;   // Tor 5.7: the first-aid kit is in the field "Heiltrank"
                check(won != null && keys.contains(key), clazz + ": new gain on the keys 1-3: " + keys);
                if (clazz == SkillClass.ARCHER) {
                    own.setGainStage(clazz, "brandpfeile", 1);
                    own.setGainStage(clazz, "lichtpfeile", 1);
                    m.kits().equip(player, 7);
                    var q = GainGear.quiverOf(player);
                    check(q != null && q.shots.getOrDefault("fire", 0) == 16 && q.shots.getOrDefault("light", 0) == 16,
                            "ARCHER: fire and light arrows in the quiver " + (q == null ? "-" : q.shots));
                    if (m.kits().hasStash(player)) {
                        m.kits().restore(player);
                    }
                }
                // everything back for the next scenes
                if (oldGains == null) {
                    own.gains.remove(clazz.name());
                } else {
                    own.gains.put(clazz.name(), oldGains);
                }
                if (own.gainKeyChoice != null) {
                    own.gainKeyChoice.remove(clazz.name());
                }
                own.points = oldPoints;
                own.chosenClass = oldClass;
            });
            context.waitTicks(5);   // onServer only queues the step on the server thread
        }
        openAll(context, game);
        game.getServer().runCommand("time set 1000");
        context.waitTicks(5);
        log((all > 0 && ok == all ? "OK   " : "FAIL ") + ok + " von " + all);
        log("done");
    }
}
