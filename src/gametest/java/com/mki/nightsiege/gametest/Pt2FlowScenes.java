package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.ExpeditionKind;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SkillClass;
import com.mki.nightsiege.core.SkillNode;
import com.mki.nightsiege.core.SkillTree;
import com.mki.nightsiege.game.CoreNetworking;
import com.mki.nightsiege.game.CrateService;
import com.mki.nightsiege.game.Playtest;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.SiegeSave;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Playtest 2", package pt2-flow (Simon 04.11.): a solo playtest night holds as many monsters at once and
 * as strong a field as a real solo night; the plan has three different dungeon kinds and an event of its
 * own for every night without a boss (what the last playtests had comes last); nobody is hurt between the
 * rounds and every part starts with full life and food; "Kiste öffnen" opens a crate in a test night with
 * the kit on; with nobody online the running part ends and comes again after "Bereit". Log lines start
 * with "[pt2-flow]".
 */
final class Pt2FlowScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;

    private Pt2FlowScenes() {
    }

    private static void log(String line) {
        System.out.println("[pt2-flow] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

    /** Runs an action on the server, a throw becomes a failed check instead of a lost line. */
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

    private static String phase(ClientGameTestContext context) {
        return context.computeOnClient(client -> SiegeManager.get().playtest().phase());
    }

    /** Waits (at most {@code seconds}) until the playtest is in one of these phases; the phase it ended in. */
    private static String waitFor(ClientGameTestContext context, int seconds, String... phases) {
        Set<String> want = Set.of(phases);
        String now = phase(context);
        for (int i = 0; i < seconds && !want.contains(now); i++) {
            context.waitTicks(20);
            now = phase(context);
        }
        return now;
    }

    private static int crateItems(ServerPlayer player) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(CrateService.CRATE)) {
                n += player.getInventory().getItem(i).getCount();
            }
        }
        return n;
    }

    /** Whether the part of this step stands: the camp or the dungeon. */
    private static boolean partStands(SiegeManager m) {
        return "camp".equals(m.playtest().interlude()) ? m.save().camp != null : m.save().expedition != null;
    }

    /** A hit of 4 by nothing in particular: whether it went through (life or absorption taken, or the hit counted). */
    private static boolean hurts(ServerPlayer player) {
        float before = player.getHealth() + player.getAbsorptionAmount();
        player.invulnerableTime = 0;
        boolean hit = player.hurtServer((ServerLevel) player.level(), player.level().damageSources().generic(), 4f);
        float after = player.getHealth() + player.getAbsorptionAmount();
        log("  hit: returned " + hit + ", life+absorption " + before + " -> " + after + ", mode " + player.gameMode()
                + ", invulnerable " + player.getAbilities().invulnerable);
        return hit || after < before;
    }

    /** The step the playtest is at (0-3). */
    private static int step(SiegeManager m) {
        return m.save().playtest == null ? -1 : m.save().playtest.step;
    }

    // ------------------------------------------------------------------ the scene

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        EventLookTest.ensureCore(context, game);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("time set 1000");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect clear @p");
        context.waitTicks(10);
        server(context, "mode", (player, m) -> log("game mode " + player.gameMode() + ", invulnerable " + player.getAbilities().invulnerable));
        context.waitTicks(5);
        // The server's night scaling of 04.11. (the test world's config may differ): the cap by group size, the field.
        final Object[] saved = new Object[8];
        server(context, "setup", (player, m) -> {
            SiegeConfig cfg = m.config();
            saved[0] = new ArrayList<>(cfg.liveCapByPlayers);
            saved[1] = new ArrayList<>(cfg.fieldRefCaps);
            saved[2] = cfg.maxLiveMobs;
            saved[3] = cfg.playtestLivePerPlayer;
            saved[4] = cfg.refillCap;
            saved[5] = cfg.testPrepSeconds;
            saved[6] = cfg.refillSpawns;
            cfg.liveCapByPlayers = new ArrayList<>(List.of(6, 10, 13, 17, 20, 24, 27, 31, 34, 38));
            cfg.fieldRefCaps = new ArrayList<>(List.of(9, 15, 20, 33, 46, 58, 70, 82, 98, 114));
            cfg.maxLiveMobs = 40;
            cfg.playtestLivePerPlayer = 0.5;
            cfg.refillCap = 40;
            cfg.refillSpawns = true;
            cfg.testPrepSeconds = 0;   // the test night starts at once
            if (m.playtest().running()) {
                m.playtest().finish(player.level().getServer(), "nightsiege.playtest.stopped");
            }
            if (m.kits().hasStash(player)) {
                m.kits().restore(player);
            }
            m.save().register(player.getUUID().toString());
            // What the last playtests had: five dungeon kinds, two events, two bosses (no theme wave since 5 November).
            SiegeSave.PlaytestRecent recent = new SiegeSave.PlaytestRecent();
            recent.dungeons.addAll(List.of("stone_crypt", "illager_keep", "deep_cellar", "nether_forge", "desert_crypt"));
            recent.events.addAll(List.of("BLOOD_MOON", "FOG"));
            recent.bosses.addAll(List.of("grave_king", "warlord"));
            m.save().playtestRecent = recent;
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            own.reset();
            String problem = m.playtest().start(player.level().getServer(), 8, player);
            log("start: problem=" + problem + " " + m.playtest().status());
        });
        context.waitTicks(30);

        // ---------------------------------------------------------------- 4: variety
        server(context, "plan", (player, m) -> {
            List<String> dungeons = m.playtest().dungeonPlan();
            List<String> events = m.playtest().eventPlan();
            SiegeSave.PlaytestState s = m.save().playtest;
            Set<String> kinds = new HashSet<>();
            int dungeonSteps = 0;
            for (int i = 0; i < s.interludes.size(); i++) {
                if ("dungeon".equals(s.interludes.get(i))) {
                    dungeonSteps++;
                    if (ExpeditionKind.byId(dungeons.get(i)) != null) {
                        kinds.add(dungeons.get(i));
                    }
                }
            }
            log("plan: interludes=" + s.interludes + " dungeons=" + dungeons + " events=" + events + " boss night="
                    + (s.bossNight + 1) + " boss=" + s.boss);
            check(dungeonSteps == 3 && kinds.size() == 3, "the plan has 3 different dungeon kinds");
            check(kinds.equals(Set.of("frost_keep", "poison_swamp", "basalt_forge")),
                    "the dungeon kinds of the last playtests come last (the three unused ones)");
            Set<String> seen = new HashSet<>();
            boolean allSet = true;
            for (int i = 0; i < Playtest.NIGHTS; i++) {
                String e = events.get(i);
                if (i == s.bossNight) {
                    allSet &= e.isEmpty();
                    continue;
                }
                allSet &= !e.isEmpty() && !"NONE".equals(e) && !"ECLIPSE".equals(e) && seen.add(e);
            }
            check(allSet && events.size() == Playtest.NIGHTS, "every non-boss night has its own event, none twice, never NONE or ECLIPSE");
            List<String> fresh = new ArrayList<>(seen);
            fresh.retainAll(List.of("THUNDER", "NETHER_RIFT", "FROST"));
            check(!seen.contains("THEME_WAVE"), "no theme wave in the plan (removed 5 November)");
            check(fresh.size() >= Math.min(3, seen.size()), "the events of the last playtest come last");
            check(s.bossNight < 0 || !List.of("grave_king", "warlord").contains(s.boss), "the boss is none of the last two playtests");
            List<String> history = m.save().playtestRecent.dungeons;
            check(history.size() == 8 && history.containsAll(kinds), "the history keeps this playtest's dungeon kinds");
            m.playtest().send(player, true);
        });
        context.waitTicks(20);
        context.takeScreenshot("pt2_flow_plan");

        // The path: warrior, the whole tree; then ready.
        server(context, "path", (player, m) -> {
            SkillTree tree = m.skillTree();
            String sub = SkillTree.subclassInfosOf(SkillClass.WARRIOR).get(0).node();
            m.onChooseClass(player, "WARRIOR:" + sub);
            PlayerSkills own = m.save().skillsOf(player.getUUID().toString());
            for (int round = 0; round < 600; round++) {
                boolean any = false;
                for (SkillNode n : tree.ofClass(SkillClass.WARRIOR)) {
                    if (tree.canUnlock(n.id(), own)) {
                        tree.unlock(n.id(), own);
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    break;
                }
            }
            m.playtest().onAction(player, "ready");
            log("path " + own.history.size() + " levels, ready");
        });
        context.waitTicks(45);
        EventLookTest.closeScreen(context);

        // ---------------------------------------------------------------- 5: no harm in the pause
        server(context, "pause harm", (player, m) -> {
            boolean hurt = hurts(player);
            log("pause before the part: phase=" + m.playtest().phase() + " health=" + player.getHealth() + " hurt=" + hurt);
            check("pause_interlude".equals(m.playtest().phase()) && !hurt, "no damage to the participant in the pause before the part");
            player.getFoodData().setFoodLevel(6);
        });
        context.waitTicks(30);
        server(context, "pause food", (player, m) -> {
            log("food in the pause after 1.5 s: " + player.getFoodData().getFoodLevel());
            check(player.getFoodData().getFoodLevel() == 20, "no hunger in the pause (food kept full)");
            m.playtest().skip(player.level().getServer());
        });
        context.waitTicks(40);
        final int[] stepAt = new int[2];
        final String at = waitFor(context, 150, "interlude");
        server(context, "interlude", (player, m) -> {
            String kind = m.playtest().interlude();
            String built = m.save().expedition == null ? "-" : m.save().expedition.kind;
            String planned = m.playtest().dungeonPlan().get(Math.max(0, step(m)));
            stepAt[0] = step(m);
            log("part 1: phase=" + m.playtest().phase() + " " + kind + " built=" + built + " planned=" + planned
                    + " health=" + player.getHealth() + "/" + player.getMaxHealth() + " food=" + player.getFoodData().getFoodLevel()
                    + " fighter=" + m.isDefending(player));
            check("interlude".equals(m.playtest().phase()), "the camp or dungeon of step 1 began (" + at + ")");
            check(!"dungeon".equals(kind) || planned.equals(built), "the dungeon built is the kind of the plan");
            check(player.getHealth() >= player.getMaxHealth() && player.getFoodData().getFoodLevel() == 20,
                    "full life and food at the start of the part");
            boolean hurt = !m.isDefending(player) && hurts(player);
            check(m.isDefending(player) || !hurt, "no damage on the way, before the fight starts");
        });
        context.waitTicks(10);

        // ---------------------------------------------------------------- 3: pause in the camp or dungeon
        context.runOnClient(client -> Playtest.testAway = true);
        context.waitTicks(50);
        server(context, "paused 1", (player, m) -> {
            log("away in the part: " + m.playtest().status() + " camp=" + (m.save().camp != null) + " dungeon="
                    + (m.save().expedition != null) + " kit=" + m.kits().hasStash(player));
            check("paused".equals(m.playtest().phase()) && "interlude".equals(m.playtest().pausedPart()),
                    "nobody online: the playtest pauses and keeps the camp/dungeon to play again");
            check(!partStands(m) && !m.kits().hasStash(player), "the running camp/dungeon ended and went, no kit left on");
            Playtest.testAway = false;
            m.playtest().testJoin(player);
        });
        context.waitTicks(30);
        String screen = context.computeOnClient(client -> client.gui.screen() == null ? "none" : client.gui.screen().getClass().getSimpleName());
        context.takeScreenshot("pt2_flow_paused");
        server(context, "rejoin", (player, m) -> {
            log("back: screen=" + screen + " " + m.playtest().status());
            check("PlaytestScreen".equals(screen), "on login the playtest menu opens");
            check("paused".equals(m.playtest().phase()), "it waits for Bereit (still paused after the login)");
            m.playtest().onAction(player, "ready");
        });
        context.waitTicks(50);
        server(context, "resumed 1", (player, m) -> {
            log("after Bereit: " + m.playtest().status());
            SiegeSave.PlaytestState s = m.save().playtest;
            check("pause_interlude".equals(s.phase) && s.step == stepAt[0], "after Bereit the same step's camp/dungeon comes again (short pause first)");
            m.playtest().skip(player.level().getServer());
        });
        context.waitTicks(40);
        final String atAgain = waitFor(context, 150, "interlude");
        server(context, "interlude again", (player, m) -> {
            String built = m.save().expedition == null ? "-" : m.save().expedition.kind;
            log("part 1 again: phase=" + m.playtest().phase() + " " + m.playtest().interlude() + " built=" + built);
            check("interlude".equals(m.playtest().phase()) && partStands(m), "the same part is played again (" + atAgain + ")");
            check(!"dungeon".equals(m.playtest().interlude()) || m.playtest().dungeonPlan().get(step(m)).equals(built),
                    "again the planned dungeon kind");
            m.playtest().skip(player.level().getServer());   // the part ends
        });
        context.waitTicks(30);
        EventLookTest.closeScreen(context);

        // ---------------------------------------------------------------- 5 + 1: the night starts, full and scaled like a real one
        server(context, "night start", (player, m) -> {
            log("before the night: phase=" + m.playtest().phase());
            player.setHealth(6f);
            player.getFoodData().setFoodLevel(4);
            player.getFoodData().setSaturation(0f);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
            player.setRemainingFireTicks(200);
            m.playtest().skip(player.level().getServer());   // the night
            boolean poisoned = player.hasEffect(MobEffects.POISON) || player.hasEffect(MobEffects.WEAKNESS);
            log("night: phase=" + m.playtest().phase() + " active=" + m.isActive() + " health=" + player.getHealth() + "/"
                    + player.getMaxHealth() + " food=" + player.getFoodData().getFoodLevel() + " harmful=" + poisoned
                    + " fire=" + player.getRemainingFireTicks());
            check(m.isActive(), "the test night runs");
            check(player.getHealth() >= player.getMaxHealth() && player.getFoodData().getFoodLevel() == 20 && !poisoned
                    && player.getRemainingFireTicks() <= 0, "full life and food, no fire, no harmful effect at the start of the night");
            SiegeConfig cfg = m.config();
            int real = SiegeMath.liveCap(1, cfg);
            real = cfg.refillSpawns ? Math.min(real, cfg.refillCap) : real;
            double field = SiegeMath.fieldStrength(m.presentCount(), m.maxLive(), cfg);
            double realField = SiegeMath.fieldStrength(1, real, cfg);
            log("scaling: live cap " + m.maxLive() + " (real solo " + real + "), field " + field + " (real solo " + realField
                    + "), defenders " + m.presentCount() + ", monster life x" + m.overflowHealthFactor()
                    + ", event " + m.currentEventKey() + " planned " + m.playtest().eventPlan().get(step(m)));
            check(m.maxLive() == 6 && m.maxLive() == real, "a solo playtest night holds the real solo cap (6), not 40");
            check(Math.abs(field - realField) < 1e-9 && field > 1.0, "its field strength is a real solo night's (9/6)");
            String plannedEvent = m.playtest().eventPlan().get(step(m));
            check(plannedEvent.equals(m.currentEventKey()), "the night has the event of the plan");
            boolean hurt = hurts(player);
            log("hit as a defender: hurt=" + hurt + " health=" + player.getHealth());
            check(hurt, "as a defender of the running night the participant does take damage");
            player.setHealth(player.getMaxHealth());
        });
        game.getServer().runCommand("effect give @p minecraft:resistance 300 255 true");
        context.waitTicks(30);

        // ---------------------------------------------------------------- 6: a crate in the night, kit on
        final int[] due = new int[2];
        server(context, "crate before", (player, m) -> {
            due[0] = m.playtest().cratesDue(player.getUUID());
            log("crate: kit=" + m.kits().hasStash(player) + " due=" + due[0] + " items=" + crateItems(player));
            check(m.kits().hasStash(player) && due[0] > 0, "in the night with the kit on, crates are owed");
        });
        context.waitTicks(5);
        context.runOnClient(client -> ClientPlayNetworking.send(new CoreNetworking.CoreActionPayload("playtest:crates")));
        context.waitTicks(30);
        String crateScreen = context.computeOnClient(client -> client.gui.screen() == null ? "none" : client.gui.screen().getClass().getSimpleName());
        context.takeScreenshot("pt2_flow_crate_night");
        server(context, "crate after", (player, m) -> {
            due[1] = m.playtest().cratesDue(player.getUUID());
            log("crate opened: screen=" + crateScreen + " due " + due[0] + "->" + due[1] + " items=" + crateItems(player)
                    + " active=" + m.isActive() + " kit=" + m.kits().hasStash(player));
            check("CrateScreen".equals(crateScreen), "\"Kiste öffnen\" in the night opens the crate window");
            check(due[1] == due[0] - 1 && crateItems(player) == 0, "the count goes down by one, no crate item");
        });
        context.waitTicks(10);
        EventLookTest.closeScreen(context);

        // ---------------------------------------------------------------- 3: pause in the night
        context.runOnClient(client -> Playtest.testAway = true);
        context.waitTicks(50);
        server(context, "paused 2", (player, m) -> {
            log("away in the night: " + m.playtest().status() + " active=" + m.isActive() + " preparing=" + m.isPreparing()
                    + " kit=" + m.kits().hasStash(player));
            check("paused".equals(m.playtest().phase()) && "night".equals(m.playtest().pausedPart()),
                    "nobody online in the night: paused, the night to play again");
            check(!m.isActive() && !m.isPreparing() && !m.kits().hasStash(player), "the test night was broken off, the kit is off");
            stepAt[1] = step(m);
            Playtest.testAway = false;
            m.playtest().testJoin(player);
            m.playtest().onAction(player, "ready");
        });
        context.waitTicks(50);
        server(context, "resumed 2", (player, m) -> {
            SiegeSave.PlaytestState s = m.save().playtest;
            log("after Bereit: " + m.playtest().status());
            check("pause_night".equals(s.phase) && s.step == stepAt[1], "after Bereit the same night comes again (short pause first)");
            m.playtest().skip(player.level().getServer());
            log("the night again: active=" + m.isActive() + " " + m.playtest().status());
            check(m.isActive() && "night".equals(m.playtest().phase()), "the night is played again");
            m.playtest().skip(player.level().getServer());   // it ends
        });
        context.waitTicks(20);
        game.getServer().runCommand("effect clear @p");   // the resistance of the night would hide a hit
        context.waitTicks(10);
        server(context, "after night", (player, m) -> {
            boolean hurt = hurts(player);
            log("after the night: phase=" + m.playtest().phase() + " hurt=" + hurt);
            check("after_night".equals(m.playtest().phase()) && !hurt, "no damage after the night");
        });
        context.waitTicks(10);

        // The end: everything back.
        server(context, "end", (player, m) -> {
            Playtest.testAway = false;
            m.playtest().finish(player.level().getServer(), "nightsiege.playtest.stopped");
            SiegeConfig cfg = m.config();
            @SuppressWarnings("unchecked")
            List<Integer> caps = (List<Integer>) saved[0];
            @SuppressWarnings("unchecked")
            List<Integer> refs = (List<Integer>) saved[1];
            cfg.liveCapByPlayers = caps;
            cfg.fieldRefCaps = refs;
            cfg.maxLiveMobs = (Integer) saved[2];
            cfg.playtestLivePerPlayer = (Double) saved[3];
            cfg.refillCap = (Integer) saved[4];
            cfg.testPrepSeconds = (Integer) saved[5];
            cfg.refillSpawns = (Boolean) saved[6];
            log("end: playtest=" + (m.save().playtest == null ? "clean" : m.playtest().status()) + " kit=" + m.kits().hasStash(player));
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("time set 1500");
        context.waitTicks(10);
        log("summary: " + checks + " checks, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
    }
}
