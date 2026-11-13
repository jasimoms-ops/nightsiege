package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.BossPlan;
import com.mki.nightsiege.core.Chapters;
import com.mki.nightsiege.core.HallOfFame;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Scene "kapitel" (Tor 11, Simon 13.11.): chapter 1 at night 20 (the finale), the finale won → the character is a
 * veteran, chapter 2 starts at "Nacht 1" on world level 1, the class pick locks the veteran's class, the chapter
 * bar, the finale's chest and banner, the star at the name and the veteran in the hall. Pictures "kapitel_*".
 * Log lines start with "[kapitel]".
 */
final class KapitelScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static int shots;

    private KapitelScenes() {
    }

    private static void log(String line) {
        System.out.println("[kapitel] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    private static void server(ClientGameTestContext context, String step, EventLookTest.OnServer action) {
        EventLookTest.onServer(context, (player, manager) -> {
            try {
                action.run(player, manager);
            } catch (Throwable t) {
                FAILS.add(step + ": " + t);
                log("ERROR in " + step + ": " + t);
                t.printStackTrace(System.out);
            }
        });
    }

    private static void tab(ClientGameTestContext context, String tab) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                field.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object value = Enum.valueOf((Class) field.getType(), tab);
                field.set(null, value);
            } catch (ReflectiveOperationException e) {
                log("could not set the tab: " + e);
            }
        });
        EventLookTest.onServer(context, (player, manager) -> manager.onCoreAction(player, "open"));
        context.waitTicks(30);
    }

    private static void shot(ClientGameTestContext context, String name) {
        // The mouse in an empty corner first (lehren Design 12.11.), else a tooltip covers a button (fix 13.11.).
        int windowH = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        context.getInput().setCursorPos(2, Math.max(2, windowH - 2));
        context.waitTicks(3);
        context.takeScreenshot(name);
        shots++;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        shots = 0;
        EventLookTest.ensureCore(context, game);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("time set 6000");
        int[] window = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(), client.getWindow().getHeight()});
        context.getInput().resizeWindow(1280, 720);   // GUI 3 in the CI window 854x480: room for the core buttons
        context.waitTicks(10);
        final boolean[] cfgBefore = new boolean[3];
        // What the world had before, to give it back at the end.
        final Object[] before = new Object[8];
        final int[] beforeN = new int[3];
        server(context, "setup", (player, m) -> {
            String uuid = player.getUUID().toString();
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            var save = m.save();
            beforeN[0] = save.siegesWon;
            beforeN[1] = save.bossNights;
            beforeN[2] = save.season;
            before[0] = save.chapters;
            before[1] = save.skills.get(uuid);
            before[2] = new ArrayList<>(save.groupBosses);
            before[3] = save.bossBag == null ? null : new ArrayList<>(save.bossBag);
            before[4] = save.nextBoss;
            HallOfFame.Hero h = save.hall == null ? null : save.hall.heroes.get(uuid);
            before[5] = h == null || h.veterans == null ? null : new ArrayList<>(h.veterans);
            // Chapter 1, the finale comes: 19 nights held and won, a warrior of level 20.
            save.chapters = new Chapters.Progress();
            save.siegesWon = 19;
            save.bossNights = 19;
            PlayerSkills own = new PlayerSkills();
            String key = own.addProfile(SkillClass.WARRIOR, "");
            own.playOpened(key);
            own.earned = 500;
            save.skills.put(uuid, own);
            check(m.chapter() == 1, "chapter 1 (" + m.chapter() + ")");
            check(m.chapterNight() == 20, "the coming night is night 20 (" + m.chapterNight() + ")");
            check(Chapters.nightKind(m.chapterNight(), m.config()) == BossPlan.NightKind.GROUP, "night 20 is the finale");
            check(Chapters.finaleBosses(1, save.groupBosses).equals(Chapters.GROUP_ONE), "the finale brings the four of chapter 1");
            check(m.currentLevel() == 20, "world level 20 (" + m.currentLevel() + ")");
            check(!save.chapters().firstFinaleWon, "no finale won yet (Welt-Merker off)");
            // Teil 2: war leader, bastion and the veterans wait for the first finale (the operator counts as a player here)
            cfgBefore[0] = m.config().warLeaderEnabled;
            cfgBefore[1] = m.config().bastionEnabled;
            cfgBefore[2] = m.config().testToolsForEveryone;
            m.config().warLeaderEnabled = true;
            m.config().bastionEnabled = true;
            m.config().testToolsForEveryone = false;
            SiegeManager.finaleLockAsPlayer = true;
            check("nightsiege.chapter.lock_finale".equals(m.finaleLock(player)), "before the first finale: buttons grey");
            SiegeManager.finaleLockAsPlayer = false;
            check(m.finaleLock(player) == null, "an operator has them open");
            SiegeManager.finaleLockAsPlayer = true;
        });
        context.waitTicks(10);
        tab(context, "STATS");
        shot(context, "kapitel_vor_finale");
        shot(context, "kapitel_knoepfe_gesperrt");
        EventLookTest.closeScreen(context);

        // The finale is won: the night counted (rhythm 20, won 20), then the chapter closes.
        server(context, "finale", (player, m) -> {
            String uuid = player.getUUID().toString();
            var save = m.save();
            save.bossNights = 20;
            save.siegesWon = 20;
            save.chapters().closing = List.of(uuid);   // Pruefer 13.11.: as saved by the won finale, finished as after a restart
            save.chapters().closingLevel = 20;
            m.finishChapterChange(player.level().getServer());
            Chapters.Progress p = save.chapters();
            check(p.closing == null, "the pending chapter change is done (closing cleared)");
            check(p.chapter == 2, "chapter 2 started (" + p.chapter + ")");
            check(m.chapterNight() == 1, "it starts at night 1 (" + m.chapterNight() + ")");
            check(m.currentLevel() == 1, "world level 1 again (" + m.currentLevel() + ")");
            check(p.firstFinaleWon, "Welt-Merker: first finale won");
            check(Chapters.nightKind(4, m.config()) == BossPlan.NightKind.BOSS
                    && Chapters.bossGroup(2).equals(Chapters.GROUP_TWO), "chapter 2: boss night 4 from the second group");
            List<Chapters.Veteran> vets = p.veteransOf(uuid);
            check(vets.size() == 1 && "WARRIOR".equals(vets.get(0).clazz) && vets.get(0).finale && vets.get(0).level == 20,
                    "the warrior is a veteran (level 20, finale)");
            check(vets.size() == 1 && vets.get(0).skills != null && vets.get(0).skills.earned == 500, "the veteran keeps its character");
            PlayerSkills fresh = save.skillsOf(uuid);
            check(!fresh.hasClass() && fresh.earned == 0, "a new character at level 1, no class yet");
            check(m.veteranLocked(player, SkillClass.WARRIOR) && !m.veteranLocked(player, SkillClass.ARCHER),
                    "the class pick locks the warrior, not the archer");
            m.onChooseClass(player, "WARRIOR");
            check(!save.skillsOf(uuid).hasClass(), "the warrior cannot be chosen again");
            int ingots = 0;
            boolean banner = false;
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (stack.is(Items.CHEST) && stack.has(DataComponents.CONTAINER) && stack.has(DataComponents.CUSTOM_NAME)) {
                    ingots += stack.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().mapToInt(ItemStack::getCount).sum();
                }
                if (com.mki.nightsiege.game.ClassBanners.isClassBanner(stack) && stack.has(DataComponents.CUSTOM_NAME)) {
                    banner = true;
                }
            }
            check(ingots == m.config().chapterFinaleInfernite, "the finale chest holds " + ingots + " infernite");
            check(banner, "the crest of the warrior as a banner");
            var team = player.level().getServer().getScoreboard().getPlayersTeam(player.getScoreboardName());
            check(team != null && team.getPlayerPrefix().getString().contains("★"), "the chapter star at the name");
            HallOfFame.Hero h = save.hall.heroes.get(uuid);
            check(h != null && h.veterans != null && !h.veterans.isEmpty() && h.veterans.get(h.veterans.size() - 1).chapter == 1,
                    "the hall of fame has the veteran");
            check(m.finaleLock(player) == null, "after the first finale: buttons open");
        });
        context.waitTicks(10);
        tab(context, "CLASSES");
        shot(context, "kapitel_klassenwahl");
        tab(context, "STATS");
        shot(context, "kapitel_core_leiste");
        shot(context, "kapitel_knoepfe_offen");
        EventLookTest.closeScreen(context);

        // The chapter bar of a night: a test night shows it here like a real one.
        final int[] saved = new int[2];
        server(context, "night", (player, m) -> {
            SiegeManager.chapterBarInTests = true;
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            m.config().testPrepSeconds = 1;
            m.config().batchIntervalSeconds = 600;
            BlockPos at = player.blockPosition();
            boolean ok = m.prepareTest(player.level().getServer(), (ServerLevel) player.level(), at.offset(4, 0, 4), 1,
                    com.mki.nightsiege.core.SiegeEvent.NONE);
            log("night prepared=" + ok);
        });
        context.waitTicks(80);
        EventLookTest.closeScreen(context);
        context.waitTicks(10);
        server(context, "bar", (player, m) -> {
            String name = "";
            try {
                java.lang.reflect.Field f = SiegeManager.class.getDeclaredField("bossBar");
                f.setAccessible(true);
                Object bar = f.get(m);
                if (bar instanceof net.minecraft.server.level.ServerBossEvent e) {
                    name = e.getName().getString();
                }
            } catch (ReflectiveOperationException e) {
                log("bar not read: " + e);
            }
            log("bar: " + name);
            check(m.isActive() && (name.contains("Kapitel 2 · Nacht 1") || name.contains("Chapter 2 · Night 1")),
                    "the bar shows chapter 2, night 1");
        });
        context.waitTicks(5);
        shot(context, "kapitel_leiste");
        server(context, "night end", (player, m) -> {
            if (m.isActive()) {
                m.abort(player.level().getServer());
            }
            SiegeManager.chapterBarInTests = false;
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
        });
        context.waitTicks(20);

        // The new character: the archer is free.
        server(context, "archer", (player, m) -> {
            m.onChooseClass(player, "ARCHER");
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            check(own.hasClass() && own.clazz() == SkillClass.ARCHER, "the new character is an archer");
        });
        EventLookTest.closeScreen(context);
        context.waitTicks(10);

        // Teil 2: the warrior veteran plays night 20 as a normal night, with a curse stone laid in.
        final int[] round = new int[4];
        server(context, "round", (player, m) -> {
            var save = m.save();
            if (save.curses == null) {
                save.curses = new ArrayList<>();
            }
            round[0] = save.siegesWon;
            round[1] = m.chapterNight();
            save.curses.add(com.mki.nightsiege.core.CurseKind.TOUGH.id());
            saved[0] = m.config().testPrepSeconds;
            saved[1] = m.config().batchIntervalSeconds;
            m.config().testPrepSeconds = 0;   // no build-up in the scene
            m.config().batchIntervalSeconds = 600;
            check(m.roundBlock(player.level().getServer(), player, 0) == null, "the veterans' loop is open");
            com.mki.nightsiege.game.CoreRecord rec = m.mainCore();
            if (rec != null) {
                player.teleportTo(rec.x + 2.5, rec.y, rec.z + 2.5);   // at the core (48 blocks)
            }
            boolean ok = m.startSiegeRound(player, 0);
            PlayerSkills played = save.skillsOf(player.getUUID().toString());
            check(ok && m.isActive() && m.inSiegeRound(), "Belagerung starten runs");
            check(played.clazz() == SkillClass.WARRIOR && save.realSkillsOf(player.getUUID().toString()).clazz() == SkillClass.ARCHER,
                    "the veteran plays, the archer waits");
            check(m.currentLevel() == 20, "night 20 of the veteran (level " + m.currentLevel() + ")");
            check(m.roundBlock(player.level().getServer(), player, -1) != null, "no second round meanwhile");
            check(m.isNightPlayer(player), "who pressed ready plays");
        });
        context.waitTicks(40);
        shot(context, "kapitel_belagerung");
        server(context, "round end", (player, m) -> {
            var save = m.save();
            PlayerSkills own = save.realSkillsOf(player.getUUID().toString());
            int earnedBefore = own.earned;
            m.roundWavesForTests(player.getUUID(), 20);
            m.finish(player.level().getServer(), true);
            double want = 20 * m.config().pointGainMultiplier / Math.max(1, m.config().wavesPerPoint) * 0.6;   // 50 % + 10 % (one curse)
            log("pool " + own.xpPool + " (want ~" + want + "), earned " + earnedBefore + " -> " + own.earned + ", cap " + m.poolCap());
            check(!m.isActive() && !m.inSiegeRound(), "the round is over");
            check(own.xpPool + own.earned - earnedBefore >= Math.floor(want + 1e-9), "50 % + 10 % for the curse into the pool (waves and the won night)");
            check(save.curses == null || save.curses.isEmpty(), "the curse stone is used up, as in a real night");
            check(own.earned <= Math.max(earnedBefore, m.poolCap()), "it flows only up to the world level");
            check(own.xpPool > 0, "the rest waits in the pool");
            check(save.skillsOf(player.getUUID().toString()).clazz() == SkillClass.ARCHER, "the veteran went back");
            check(save.chapters().veteransOf(player.getUUID().toString()).get(0).skills.earned == 500, "the frozen veteran is untouched");
            check(save.siegesWon == round[0] && m.chapterNight() == round[1], "no effect on the world (level, night)");
            var file = com.mki.nightsiege.game.StatsRecorder.lastFinished;
            check(file != null && "belagerung".equals(file.art), "statistics: art belagerung");
            check(save.chapters().round == null, "no round left in the save");
            round[2] = own.xpPool;
            round[3] = own.earned;
            // The world level rises: the pool flows on up to the new level.
            save.siegesWon += 3;
            m.flowPools();
            check(own.earned > round[3] && own.earned <= m.poolCap() && own.xpPool < round[2], "a higher level lets it flow on ("
                    + round[3] + " -> " + own.earned + ", pool " + own.xpPool + ")");
            save.siegesWon -= 3;
            m.config().testPrepSeconds = saved[0];
            m.config().batchIntervalSeconds = saved[1];
            save.curses.remove(com.mki.nightsiege.core.CurseKind.TOUGH.id());
        });
        // The picture must show a pool (fix 13.11.): after the flow above it may be spent, then a test pool.
        server(context, "pool for the picture", (player, m) -> {
            PlayerSkills own = m.save().realSkillsOf(player.getUUID().toString());
            if (own.xpPool <= 0) {
                own.xpPool = 120;
            }
            log("pool for the picture " + own.xpPool);
            check(own.xpPool > 0, "a pool to show");
        });
        context.waitTicks(10);
        tab(context, "STATS");
        shot(context, "kapitel_vorrat");
        context.runOnClient(client -> {
            if (client.gui.screen() instanceof com.mki.nightsiege.client.CoreScreen core) {
                try {
                    java.lang.reflect.Field f = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("stats");
                    f.setAccessible(true);
                    Object s = f.get(core);
                    if (s instanceof com.mki.nightsiege.core.CoreStats stats) {
                        client.setScreenAndShow(new com.mki.nightsiege.client.SiegeRoundScreen(stats, core));
                    }
                } catch (ReflectiveOperationException e) {
                    log("round window not opened: " + e);
                }
            }
        });
        context.waitTicks(10);
        shot(context, "kapitel_belagerung_wahl");
        EventLookTest.closeScreen(context);
        context.waitTicks(10);
        // The hall: the chronicle of this player with the veteran card.
        context.runOnClient(client -> {
            try {
                Class<?> pages = Class.forName("com.mki.nightsiege.client.HallPages");
                java.lang.reflect.Field page = pages.getDeclaredField("page");
                page.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object chronicle = Enum.valueOf((Class) page.getType(), "CHRONICLE");
                page.set(null, chronicle);
                java.lang.reflect.Field hero = pages.getDeclaredField("shownHero");
                hero.setAccessible(true);
                hero.set(null, client.player.getUUID().toString());
            } catch (ReflectiveOperationException e) {
                log("hall page not set: " + e);
            }
        });
        tab(context, "HALL");
        context.waitTicks(20);
        shot(context, "kapitel_ruhmeshalle");
        EventLookTest.closeScreen(context);

        server(context, "end", (player, m) -> {
            String uuid = player.getUUID().toString();
            var save = m.save();
            save.siegesWon = beforeN[0];
            save.bossNights = beforeN[1];
            save.season = beforeN[2];
            save.chapters = (Chapters.Progress) before[0];
            if (before[1] != null) {
                save.skills.put(uuid, (PlayerSkills) before[1]);
            } else {
                save.skills.remove(uuid);
            }
            save.groupBosses.clear();
            @SuppressWarnings("unchecked")
            List<String> came = (List<String>) before[2];
            save.groupBosses.addAll(came);
            @SuppressWarnings("unchecked")
            List<String> bag = (List<String>) before[3];
            save.bossBag = bag;
            save.nextBoss = (String) before[4];
            HallOfFame.Hero h = save.hall.heroes.get(uuid);
            if (h != null) {
                @SuppressWarnings("unchecked")
                List<HallOfFame.VeteranLine> lines = (List<HallOfFame.VeteranLine>) before[5];
                h.veterans = lines == null ? new ArrayList<>() : lines;
            }
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (stack.has(DataComponents.CUSTOM_NAME) && (stack.is(Items.CHEST) || com.mki.nightsiege.game.ClassBanners.isClassBanner(stack))) {
                    stack.setCount(0);
                }
            }
            if (save.pendingBanners != null) {
                save.pendingBanners.remove(uuid);
            }
            com.mki.nightsiege.game.ClassTags.update(player.level().getServer(), player, save.skillsOf(uuid), "");
            SiegeManager.finaleLockAsPlayer = false;
            m.config().warLeaderEnabled = cfgBefore[0];
            m.config().bastionEnabled = cfgBefore[1];
            m.config().testToolsForEveryone = cfgBefore[2];
            m.persist();
        });
        context.getInput().resizeWindow(window[0], window[1]);
        context.waitTicks(10);
        if (shots == 0) {
            FAILS.add("no pictures");
        }
        log("summary: " + checks + " checks, " + shots + " pictures, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
        System.out.println("[Night Siege test] kapitel: " + (checks - FAILS.size()) + " von " + checks);
        if (checks == 0 || !FAILS.isEmpty()) {
            throw new AssertionError("kapitel: " + (checks == 0 ? "no checks" : String.join("; ", FAILS)));
        }
    }
}
