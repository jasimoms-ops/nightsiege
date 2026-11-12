package com.mki.nightsiege.gametest;

import com.mki.nightsiege.client.KitHud;
import com.mki.nightsiege.client.PetHud;
import com.mki.nightsiege.core.ActionBarLayout;
import com.mki.nightsiege.core.PetRows;
import com.mki.nightsiege.game.Companions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scene "begleiter" (Paket 11.11. Z2, Simon 12.11.: "der Wolfbändiger soll jeden Wolf einzeln in der Leiste sehen
 * mit LP-Anzeige"): a tamer with the alpha and the kit, real wolves called on the server - 2, 4, 8 and 10 (the
 * "+2"), and a mixed pack with golem, snow golem and skeleton guard. In each one wolf is nearly dead and one fell
 * (grey, with the pause until the next call). 1080p, GUI 2 and 3, chat lines on screen. Checks that the rows came
 * over the network with the server's health and stay off the chat, the voice chat and the bar.
 * Screenshots begleiter_*. Log lines "[begleiter]".
 */
final class BegleiterScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private BegleiterScenes() {
    }

    private static void log(String line) {
        System.out.println("[begleiter] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            FAILS.add(what);
        }
        log((ok ? "OK " : "FAIL ") + what);
    }

    /** A pack: name, wolves, and golem + snow golem + skeleton guard. */
    private record Pack(String name, int wolves, boolean mixed) {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("gamemode creative @p");   // companions stay outside a night only in creative
        game.getServer().runCommand("time set 13000");
        context.waitTicks(10);
        final String[] backup = new String[1];
        final boolean[] openBefore = new boolean[1];
        EventLookTest.onServer(context, (player, manager) -> {
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            var own = manager.save().skillsOf(player.getUUID().toString());
            backup[0] = com.mki.nightsiege.game.Json.toJson(own);
            openBefore[0] = manager.config().summonerForEveryone;
            manager.config().summonerForEveryone = true;
            own.reset();
            own.chosenClass = com.mki.nightsiege.core.SkillClass.SUMMONER.name();
            own.unlockClass(com.mki.nightsiege.core.SkillClass.SUMMONER);
            own.award(600);
            for (String id : new String[]{"s_tamer", "s_soulbond", "s_command", "s_packcall", "s_tamer", "s_alpha"}) {
                manager.onBuySkill(player, id);
            }
            manager.kits().equip(player, 12);
            manager.sendUltHud(player);
            log("class " + own.chosenClass + " alpha=" + own.levelOf("s_alpha") + " tamer=" + own.levelOf("s_tamer"));
        });
        context.waitTicks(20);
        for (int i = 0; i < 3; i++) {
            game.getServer().runCommand("say Zeile " + (i + 1) + " im Chat");
        }
        int[] window = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(), client.getWindow().getHeight()});
        int scaleBefore = context.computeOnClient(client -> client.options.guiScale().get());
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(10);
        context.getInput().setCursorPos(1919, 540);   // the cursor in an empty corner (lehren: Design 12.11.)
        check(context.computeOnClient(client -> KitHud.active()), "kit HUD on");
        List<Pack> packs = List.of(new Pack("2", 2, false), new Pack("4", 4, false), new Pack("8", 8, false),
                new Pack("10", 10, false), new Pack("gemischt", 3, true));
        int pics = 0;
        for (Pack pack : packs) {
            float[] low = new float[1];
            EventLookTest.onServer(context, (player, manager) -> {
                ServerLevel level = (ServerLevel) player.level();
                Companions c = Companions.get();
                c.dismissAll(level.getServer());
                c.resetPause(player.getUUID());
                List<Mob> wolves = new ArrayList<>();
                for (int i = 0; i < pack.wolves(); i++) {
                    Mob m = c.testCall(player, "wolf");
                    if (m != null) {
                        wolves.add(m);
                    }
                }
                if (pack.mixed()) {
                    c.testCall(player, "golem");
                    c.testCall(player, "snow");
                    c.testCall(player, "skeleton");
                }
                // the alpha full, the others at falling shares, one nearly dead, the last one falls
                for (int i = 1; i < wolves.size(); i++) {
                    Mob w = wolves.get(i);
                    w.setHealth(w.getMaxHealth() * Math.max(0.3f, 1f - 0.15f * i));
                }
                Mob weak = wolves.get(wolves.size() > 2 ? 1 : 0);
                weak.setHealth(1f);
                low[0] = weak.getHealth();
                wolves.get(wolves.size() - 1).kill(level);
                c.callPauseForTests(player.getUUID(), level.getGameTime(), 300);
            });
            context.waitTicks(25);   // the fallen one is noticed, the rows go out every half second
            int expected = pack.wolves() + (pack.mixed() ? 3 : 0);
            List<PetRows.Pet> got = context.computeOnClient(client -> PetHud.pets());
            long dead = got.stream().filter(p -> p.dead).count();
            long alpha = got.stream().filter(p -> p.alpha && !p.dead).count();
            boolean lowSeen = got.stream().anyMatch(p -> !p.dead && Math.abs(p.hp - low[0]) < 0.01f);
            int waitSeen = got.stream().filter(p -> p.dead).mapToInt(p -> p.waitTicks).max().orElse(0);
            check(got.size() == expected && dead == 1 && alpha == 1 && lowSeen && waitSeen > 200,
                    pack.name() + ": client got " + got.size() + " of " + expected + " rows, fallen " + dead + " (pause "
                            + waitSeen + " t), alpha " + alpha + ", nearly dead " + low[0] + " seen " + lowSeen);
            check(got.isEmpty() || "wolf".equals(got.get(0).kind), pack.name() + ": wolves first (" + (got.isEmpty() ? "-" : got.get(0).kind) + ")");
            for (int scale : new int[]{2, 3}) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                });
                context.waitTicks(4);
                pics += shot(context, "begleiter_" + pack.name() + "_gui" + scale);
                checkPlace(context, pack.name() + " gui " + scale, expected);
            }
        }
        EventLookTest.onServer(context, (player, manager) -> {
            Companions.get().dismissAll(((ServerLevel) player.level()).getServer());
            manager.config().summonerForEveryone = openBefore[0];
            var own = manager.save().skillsOf(player.getUUID().toString());
            var old = com.mki.nightsiege.game.Json.fromJson(backup[0], com.mki.nightsiege.core.PlayerSkills.class);
            for (java.lang.reflect.Field f : com.mki.nightsiege.core.PlayerSkills.class.getFields()) {
                try {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.set(own, f.get(old));
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            if (manager.kits().hasStash(player)) {
                manager.kits().restore(player);
            }
            manager.sendUltHud(player);
        });
        context.waitTicks(25);
        check(context.computeOnClient(client -> !PetHud.shown()), "rows gone after the pack was sent away");
        context.runOnClient(client -> {
            client.options.guiScale().set(scaleBefore);
            client.resizeGui();
        });
        context.getInput().resizeWindow(window[0], window[1]);
        game.getServer().runCommand("time set 1000");
        context.waitTicks(5);
        check(pics == 10, "pictures: " + pics + " of 10");
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }

    /** At most 8 rows (then "+N"), left of the bar, off the (lifted) chat, the voice chat and the bar. */
    private static void checkPlace(ClientGameTestContext context, String at, int expected) {
        List<ActionBarLayout.Rect> rows = context.computeOnClient(client -> PetHud.lastRects);
        List<String> texts = context.computeOnClient(client -> PetHud.lastTexts);
        String more = context.computeOnClient(client -> PetHud.lastMore);
        ActionBarLayout.Layout l = context.computeOnClient(client -> KitHud.lastLayout);
        int[] screen = context.computeOnClient(client -> new int[]{client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                ActionBarLayout.chatRight(client.options.chatWidth().get(), client.options.chatScale().get()),
                KitHud.chatLift()});
        int shown = Math.min(PetRows.MAX_ROWS, expected);
        String wantMore = expected > shown ? "+" + (expected - shown) : "";
        check(texts.size() == shown && more.equals(wantMore), at + ": " + texts.size() + " rows " + texts + " more '" + more + "'");
        if (l == null) {
            check(false, at + ": no bar drawn");
            return;
        }
        List<ActionBarLayout.Rect> others = new ArrayList<>(l.parts());
        others.add(ActionBarLayout.chat(screen[1] - screen[3], screen[2], 10));
        others.addAll(ActionBarLayout.voiceChat(screen[1]));
        others.add(ActionBarLayout.party(screen[1], 2));
        others.add(ActionBarLayout.jade(screen[0]));
        List<String> hits = new ArrayList<>();
        for (ActionBarLayout.Rect r : rows) {
            for (ActionBarLayout.Rect o : others) {
                if (r.overlaps(o)) {
                    hits.add(r + "/" + o);
                }
            }
            if (r.right() > l.left()) {
                hits.add(r + " right of the bar's left " + l.left());
            }
        }
        check(hits.isEmpty(), at + ": rows " + rows.size() + " clear of chat (up " + screen[3] + "), voice chat, bar"
                + (hits.isEmpty() ? "" : " " + hits));
    }

    private static int shot(ClientGameTestContext context, String name) {
        Path p = context.takeScreenshot(name);
        return p != null && Files.exists(p) ? 1 : 0;
    }
}
