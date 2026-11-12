package com.mki.nightsiege.gametest;

import com.mki.nightsiege.game.SiegeSave;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scene "steckbriefe" (Paket 11.11., Simon 12.11. 01:04: "die Schrift im Nemesis-Fenster ist kaum lesbar"): the core
 * window on the page "Fluch &amp; Rache" with three wanted posters (one out tonight, one high level with five traits
 * and four victims, one slain). At 1920x1080, GUI scale 2 and 3, English and German:
 * pictures "steckbriefe_en_us_gui2" ... "steckbriefe_de_de_gui3". Prints "[steckbriefe] OK/FAIL ..." lines.
 */
final class SteckbriefeScenes {

    private static final List<String> FAILS = new ArrayList<>();
    private static int checks;

    private SteckbriefeScenes() {
    }

    private static void check(boolean ok, String what) {
        checks++;
        System.out.println("[steckbriefe] " + (ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        List<String> ids = new ArrayList<>();
        EventLookTest.onServer(context, (player, manager) -> {
            SiegeSave save = manager.save();
            com.mki.nightsiege.game.Nemesis.clear(save, true);
            SiegeSave.NemesisRecord here = manager.nemesis().make(manager.nemesisHost(), "minecraft:zombie", player, false, 2);
            SiegeSave.NemesisRecord big = manager.nemesis().make(manager.nemesisHost(), "minecraft:skeleton", player, true, 5);
            big.victims.add("Bergfried_Bernd");
            big.victims.add("Kraehenauge");
            big.victims.add("Simsalabim");
            SiegeSave.NemesisRecord dead = manager.nemesis().make(manager.nemesisHost(), "minecraft:spider", player, false, 3);
            dead.status = "erschlagen";
            dead.slainBy = player.getScoreboardName();
            for (SiegeSave.NemesisRecord r : List.of(here, big, dead)) {
                ids.add(r.id);
                System.out.println("[steckbriefe] poster " + r.name + " " + r.type + " level " + r.level + " " + r.traits
                        + " victims " + r.victims + " status " + r.status);
            }
        });
        context.waitTicks(10);
        int[] window = context.computeOnClient(client -> new int[] {client.getWindow().getWidth(), client.getWindow().getHeight()});
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        String langBefore = context.computeOnClient(client -> client.getLanguageManager().getSelected());
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(10);
        int pics = 0;
        for (String lang : new String[] {"en_us", "de_de"}) {
            language(context, lang);
            for (int scale : new int[] {2, 3}) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                });
                context.waitTicks(3);
                showCurseTab(context);
                context.getInput().setCursorPos(4, 4);   // no tooltip over the posters
                int posters = markHere(context);
                check(posters >= 3, lang + " gui " + scale + ": " + posters + " posters in the window (want at least 3)");
                Path p = context.takeScreenshot("steckbriefe_" + lang + "_gui" + scale);
                pics += p != null && Files.exists(p) ? 1 : 0;
                EventLookTest.closeScreen(context);
            }
        }
        check(pics == 4, pics + " of 4 pictures written");
        language(context, langBefore);
        context.runOnClient(client -> {
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        context.getInput().resizeWindow(window[0], window[1]);
        EventLookTest.onServer(context, (player, manager) -> manager.save().nemeses.removeIf(r -> r == null || ids.contains(r.id)));
        context.waitTicks(5);
        System.out.println("[steckbriefe] " + (FAILS.isEmpty() ? "PASS" : "FAIL") + " " + (checks - FAILS.size()) + " of " + checks
                + (FAILS.isEmpty() ? "" : " " + FAILS));
    }

    /** Switches the client's language without a full resource reload (only the texts). */
    private static void language(ClientGameTestContext context, String code) {
        context.runOnClient(client -> {
            client.getLanguageManager().setSelected(code);
            client.options.languageCode = code;
            client.getLanguageManager().onResourceManagerReload(client.getResourceManager());
        });
        context.waitTicks(5);
    }

    private static void showCurseTab(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                field.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object value = Enum.valueOf((Class) field.getType(), "CURSE");
                field.set(null, value);
            } catch (ReflectiveOperationException e) {
                System.out.println("[steckbriefe] could not set the tab: " + e);
            }
        });
        EventLookTest.onServer(context, (player, manager) -> manager.onCoreAction(player, "open"));
        context.waitTicks(30);
    }

    /** The first poster counts as out tonight (no night runs here); returns the posters the window knows. */
    private static int markHere(ClientGameTestContext context) {
        int n = context.computeOnClient(client -> {
            if (!(client.gui.screen() instanceof com.mki.nightsiege.client.CoreScreen screen)) {
                return -1;
            }
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("stats");
                field.setAccessible(true);
                com.mki.nightsiege.core.CoreStats stats = (com.mki.nightsiege.core.CoreStats) field.get(screen);
                if (stats == null || stats.nemeses == null) {
                    return 0;
                }
                if (!stats.nemeses.isEmpty()) {
                    stats.nemeses.get(0).here = true;
                }
                return stats.nemeses.size();
            } catch (ReflectiveOperationException e) {
                System.out.println("[steckbriefe] could not read the stats: " + e);
                return -2;
            }
        });
        context.waitTicks(3);
        return n;
    }
}
