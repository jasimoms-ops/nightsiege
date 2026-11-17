package com.mki.nightsiege.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.GatherPhase;
import com.mki.nightsiege.core.NightFragment;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SkillClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Tor 12 E (Simon 17.11.: "lass das gesamte mal im survival testen"): the survival long run on a dedicated
 * server, started by NIGHTSIEGE_LANGLAUF ("spieler=2 bis=24 min=300"). Bots play the real cycle from the
 * first day: core, sign-up, invitation, accept, horn, teleport, kit, ready, night, morning, crates, skill
 * points, the next invitation; nights 1-20 (finale) and the chapter change up to night 24. The clock only
 * jumps over the empty hours (as sleeping would). Between the nights the special cases of E.3, logouts
 * and server restarts (the CI starts the server again while the file "langlauf-neustart" exists).
 * Every night gives lines "[langlauf] OK/FAIL ..." and the end a line "[langlauf] SUMME ...".
 */
public final class Langlauf {

    private static final String P = "[langlauf] ";
    private static final int LOGS = 13;
    private static final int FLINTS = 5;
    /** The special cases by invitation (cycle) number. */
    private static final Map<Integer, String> SPECIAL = Map.ofEntries(
            Map.entry(2, "absage"), Map.entry(3, "zuwenig"), Map.entry(4, "logout_bereit"),
            Map.entry(5, "logout_nacht"), Map.entry(6, "neustart_bereit"), Map.entry(7, "neustart_nacht"),
            Map.entry(8, "fragment"), Map.entry(9, "fluch"), Map.entry(10, "heerlager"), Map.entry(11, "dungeon"),
            Map.entry(12, "frist"));
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** What survives a restart. */
    static final class State {
        int group = 1;
        int target = 24;
        int minutes = 300;
        boolean built;
        int cx;
        int cy;
        int cz;
        List<String[]> subs = new ArrayList<>();
        int cycle;
        int ok;
        int fail;
        int won;
        int lost;
        int aborted;
        int lossStreak;
        int lossLevel = -1;
        int lossChapter;
        int noNight;
        List<String> done = new ArrayList<>();
        String pending;
        long restartDay = -1L;
        long startDay = -1L;
        int startsOnDay;
        long cancelDay = -1L;
        long startedMs;
        int reached;
        String reachedLabel = "-";
        Map<String, Integer> seen = new TreeMap<>();
        List<String> fails = new ArrayList<>();
        boolean finished;
        List<String> order = new ArrayList<>();
        String curScenario = "";
        String curEvent = "";
        List<String> accepted = new ArrayList<>();
        String decliner;
    }

    private static boolean on;
    private static State st;
    private static Path stateFile;
    private static Path restartFile;
    private static Path logFile;
    private static Path karte;
    private static ServerLevel world;
    private static BlockPos core;
    private static long loginAt = -1L;
    private static long lastNanos;
    private static final Map<UUID, TeamBrain> brains = new LinkedHashMap<>();
    // the cycle
    private static String scenario = "";
    private static long voteSeenDay = -1L;
    private static long voteTick;
    private static boolean voted;
    private static boolean jumped;
    private static boolean extraCalled;
    private static String eventTonight = "";
    private static final Set<UUID> acceptedByUs = new HashSet<>();
    private static UUID decliner;
    private static final Map<UUID, Long> memberSince = new HashMap<>();
    private static long gatherSeen = -1L;
    private static boolean scenarioDone;
    private static UUID outBot;
    private static String outName;
    private static long backAt = -1L;
    private static long postRestartAt = -1L;
    private static long lastIdle;
    // the night
    private static boolean wasActive;
    private static boolean wasGathering;
    private static boolean resumedInGather;
    private static String lastGather = "-";
    private static int noStartRow;
    private static String nightKind = "NORMAL";
    private static long nightStart;
    private static long nightEnd;
    private static long endCheckAt = -1L;
    private static int lvlStart;
    private static int nightNo;
    private static int chapterStart;
    private static int wonBefore;
    private static int lostBefore;
    private static final Map<UUID, Integer> earnedBefore = new HashMap<>();
    private static final Map<UUID, Integer> cratesBefore = new HashMap<>();
    private static final Set<UUID> inNight = new HashSet<>();
    private static int lastStanding;
    private static String hornHeard = "-";
    private static int startSounds;
    private static boolean waves;
    private static boolean bossNight;
    private static boolean special;
    private static boolean wardenSeen;
    private static int cursesSeen;
    private static double winSum;
    private static int winN;
    private static int slow;
    private static double maxMs;
    private static int help;
    private static final List<String> nightFails = new ArrayList<>();

    private Langlauf() {
    }

    public static boolean running() {
        return on && st != null && !st.finished;
    }

    // ------------------------------------------------------------------ start

    public static void startFromEnv(MinecraftServer server) {
        String env = System.getenv("NIGHTSIEGE_LANGLAUF");
        if (env == null) {
            return;
        }
        stateFile = Path.of("langlauf-state.json").toAbsolutePath();
        restartFile = Path.of("langlauf-neustart").toAbsolutePath();
        logFile = Path.of("langlauf.txt").toAbsolutePath();
        String map = System.getenv("NIGHTSIEGE_LANGLAUF_KARTE");
        karte = map == null || map.isBlank() ? null : Path.of(map);
        try {
            Files.deleteIfExists(restartFile);
            st = Files.exists(stateFile) ? GSON.fromJson(Files.readString(stateFile, StandardCharsets.UTF_8), State.class) : null;
        } catch (Exception e) {
            NightSiegeMod.LOGGER.error(P + "Zustand nicht lesbar", e);
            st = null;
        }
        world = server.overworld();
        on = true;
        if (st == null) {
            st = new State();
            for (String token : env.trim().split("\\s+")) {
                String[] kv = token.split("=", 2);
                if (kv.length == 2) {
                    switch (kv[0]) {
                        case "spieler" -> st.group = Math.max(1, Math.min(10, Integer.parseInt(kv[1].trim())));
                        case "bis" -> st.target = Math.max(1, Integer.parseInt(kv[1].trim()));
                        case "min" -> st.minutes = Math.max(5, Integer.parseInt(kv[1].trim()));
                        case "folge" -> st.order = new ArrayList<>(List.of(kv[1].trim().split(",")));   // own order from cycle 2
                        default -> {
                        }
                    }
                }
            }
            st.startedMs = System.currentTimeMillis();
            line("START spieler=" + st.group + " bis=Nacht " + st.target + " karte=" + (karte == null ? "standard" : karte.getFileName()));
        } else {
            core = new BlockPos(st.cx, st.cy, st.cz);
            scenario = st.curScenario == null ? "" : st.curScenario;
            eventTonight = st.curEvent == null ? "" : st.curEvent;
            scenarioDone = true;
            for (String id : st.accepted) {
                acceptedByUs.add(UUID.fromString(id));
            }
            decliner = st.decliner == null ? null : UUID.fromString(st.decliner);
            loginAt = server.getTickCount() + 200;   // players come back a few seconds after the server, not before its first tick
            resumedInGather = "bereit".equals(st.pending);
            line("WEITER nach Neustart (" + st.pending + "), Zyklus " + st.cycle);
            run(server, "tick sprint 1000000000");
        }
    }

    // ------------------------------------------------------------------ the loop

    public static void tick(MinecraftServer server) {
        if (!running()) {
            return;
        }
        SiegeManager m = SiegeManager.get();
        if (m == null) {
            return;
        }
        long now = server.getTickCount();
        long ns = System.nanoTime();
        double ms = lastNanos == 0L ? 0.0 : (ns - lastNanos) / 1_000_000.0;
        lastNanos = ns;
        try {
            if (!st.built) {
                ground(server, m, now);
                return;
            }
            if (loginAt >= 0 && now >= loginAt) {
                loginAt = -1L;
                for (int i = 0; i < st.group; i++) {
                    SimBots.login(server, world, name(i), core.getX() + 0.5, core.getY() + 1, core.getZ() + 2.5);
                }
                if (st.pending != null) {
                    postRestartAt = now + 40;
                }
            }
            step(server, m, now, ms);
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.error(P + "Fehler im Langlauf", e);
            fail("Langlauf-Fehler: " + e);
            finish(server, "Fehler");
        }
    }

    /** First start: the castle, the core, the bots signed up with their class and their own things. */
    private static void ground(MinecraftServer server, SiegeManager m, long now) {
        if (now < 100) {
            return;
        }
        world = server.overworld();
        SimCastle.Ground g = SimCastle.build(world, new BlockPos(0, 70, 0), karte);
        core = g.core();
        world.setBlock(core, ModContent.SIEGE_CORE.defaultBlockState(), 3);
        m.onCorePlaced(world, core, null);
        st.built = true;
        st.cx = core.getX();
        st.cy = core.getY();
        st.cz = core.getZ();
        st.subs = new ArrayList<>(TeamSim.compose("tank_heiler", st.group, 1234L + st.group));
        StringBuilder who = new StringBuilder();
        for (int i = 0; i < st.group; i++) {
            BlockPos spot = TeamSim.standable(world, core.offset(2 + i, 0, 2), 3);
            ServerPlayer bot = SimBots.login(server, world, name(i), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            if (bot == null) {
                fail("Bot " + name(i) + " ließ sich nicht anmelden");
                finish(server, "Fehler");
                return;
            }
            bot.setGameMode(GameType.SURVIVAL);
            m.register(bot);
            if (!m.isRegistered(bot)) {
                fail("Eintragen am Core ging nicht für " + name(i));
            }
            spend(m, bot, st.subs.get(i), false);
            bot.getInventory().add(new ItemStack(Items.OAK_LOG, LOGS));
            bot.getInventory().add(new ItemStack(Items.FLINT, FLINTS));
            who.append(' ').append(name(i)).append('=').append(st.subs.get(i)[2]);
        }
        run(server, "tick sprint 1000000000");
        line("BURG " + g.kind() + " core=" + core.toShortString() + " bots:" + who);
        save();
    }

    private static void step(MinecraftServer server, SiegeManager m, long now, double ms) {
        if (outBot != null && backAt >= 0 && now >= backAt) {
            backAt = -1L;
            ServerPlayer back = SimBots.login(server, world, outName, core.getX() + 0.5, core.getY() + 1, core.getZ() + 2.5);
            line("Wieder da: " + outName + (back == null ? " (FEHLER)" : " an " + back.blockPosition().toShortString()
                    + (m.isActive() ? " verteidigt=" + m.isDefending(back) + " zuschauer=" + back.isSpectator() : "")));
            outBot = null;
        }
        if (postRestartAt >= 0 && now >= postRestartAt) {
            postRestartAt = -1L;
            afterRestart(server, m);
        }
        if (now % 200 == 0 && System.currentTimeMillis() - st.startedMs > st.minutes * 60_000L) {
            finish(server, "Zeitgrenze " + st.minutes + " min");
            return;
        }
        boolean active = m.isActive();
        boolean gathering = m.gathering();
        if (wasGathering && !gathering && !active) {
            noStart(server, m);
            if (!running()) {
                return;
            }
        } else if (active) {
            noStartRow = 0;
        }
        wasGathering = gathering;
        if (now % 20 == 0) {
            respawnDead(server, m, active);
        }
        if (!active && now % 20 == 0) {
            keepSafe(server);
        }
        if (active && !wasActive) {
            onNightStart(server, m, now);
        }
        if (active) {
            duringNight(server, m, now, ms);
        }
        if (!active && wasActive) {
            nightEnd = now;
            endCheckAt = now + 100;
        }
        wasActive = active;
        if (endCheckAt >= 0) {
            if (now >= endCheckAt) {
                endCheckAt = -1L;
                endChecks(server, m);
                if (!running()) {
                    return;
                }
                afterMorning(server, m);
            }
            return;
        }
        if (active || loginAt >= 0 || postRestartAt >= 0) {
            return;
        }
        if (m.gathering()) {
            gatherStep(server, m, now);
            return;
        }
        if (m.voteOpen(server)) {
            voteStep(server, m, now);
            return;
        }
        if (m.nightComing(server) || m.isPreparing()) {
            return;
        }
        if (now % 20 == 0 && now - lastIdle >= 60) {
            idleStep(server, m, now);
        }
    }

    // ------------------------------------------------------------------ the day

    /** Nothing planned now: the clock jumps to the morning of the next siege day (or calls the extra siege). */
    private static void idleStep(MinecraftServer server, SiegeManager m, long now) {
        SiegeConfig cfg = m.config();
        long day = m.clockDayFor(server);
        int time = m.clockTimeFor(server);
        long next = m.save().nextSiegeDay;
        if (next < 0) {
            return;
        }
        if (!st.done.contains("extra") && m.save().chapters().chapter == 1 && m.currentLevel() >= cfg.extraSiegeFromLevel
                && next > day) {
            if (time >= 1000 && time < cfg.warningDayTime - 2000) {
                st.done.add("extra");
                extraCalled = true;
                line("EXTRA: " + name(0) + " ruft eine Extra-Belagerung aus (Tag " + day + ", regulär Tag " + next + ")");
                m.onCallExtraSiege(bot(server, 0));
                lastIdle = now;
                return;
            }
            if (next > day + 1) {
                jump(server, (day + 1) * 24000L + 1500);
                lastIdle = now;
                return;
            }
        }
        if (next > day || time < cfg.inviteDayTime) {
            if (special(st.cycle + 1).equals("fragment")) {
                m.save().nightFragment = NightFragment.BLOOD.name();   // as if laid into the core
                m.persist();
            }
            jump(server, Math.max(next, day) * 24000L + Math.max(0, cfg.inviteDayTime - 10));
            travel(server);
            lastIdle = now;
        }
    }

    /** The invitation: a new cycle; the bots answer; then the clock goes on to shortly before the horn. */
    private static void voteStep(MinecraftServer server, SiegeManager m, long now) {
        long day = m.clockDayFor(server);
        if (voteSeenDay != day) {
            voteSeenDay = day;
            newCycle(server, m, day);
            voteTick = now;
            voted = false;
            jumped = false;
            return;
        }
        if (!voted && now - voteTick >= 20) {
            voted = true;
            vote(server, m);
            return;
        }
        if (voted && !jumped && now - voteTick >= 60) {
            jumped = true;
            boolean away = scenario.equals("heerlager") || scenario.equals("dungeon");
            if (away) {
                awayScenario(server, m);
            }
            if (scenario.equals("fluch")) {
                ServerPlayer b = bot(server, 0);
                boolean laid = b != null && Curses.toggle(m, b, com.mki.nightsiege.core.CurseKind.values()[0].id());
                line("Fluch gelegt: " + laid);
            }
            boolean eclipse = "ECLIPSE".equals(m.save().pendingEvent);
            int close = eclipse ? m.config().eclipseWarningDayTime : m.config().warningDayTime;
            jump(server, day * 24000L + close - (away ? 100 : 10));
        }
    }

    private static void newCycle(MinecraftServer server, SiegeManager m, long day) {
        st.cycle++;
        if (st.cancelDay >= 0) {
            boolean started = st.startDay == st.cancelDay;
            check(!started, "zu wenige Zusagen: keine Nacht an Tag " + st.cancelDay + ", nächste Einladung Tag " + day);
            st.cancelDay = -1L;
        }
        if (st.restartDay >= 0) {
            check(st.startDay == st.restartDay, "Neustart in der Bereit-Phase: Nacht danach gestartet");
            st.restartDay = -1L;
        }
        scenario = extraCalled ? "extra" : special(st.cycle);
        extraCalled = false;
        if (scenario.equals("absage") && st.group < 2) {
            scenario = "";
        }
        if (st.done.contains(scenario) && !scenario.equals("extra")) {
            scenario = "";
        }
        if (!scenario.isEmpty()) {
            st.done.add(scenario);
        }
        scenarioDone = false;
        hornHeard = "-";
        for (int i = 0; i < st.group; i++) {
            SimBots.takeSounds(bot(server, i));
        }
        if (scenario.equals("dungeon")) {
            BlockPos at = surface(core.getX(), core.getZ() + 170);
            m.expeditions().testBuild(world, at.below(12), 0, com.mki.nightsiege.core.ExpeditionKind.STONE_CRYPT, 3);
        }
        eventTonight = m.save().pendingEvent == null ? "" : m.save().pendingEvent;
        st.curScenario = scenario;
        st.curEvent = eventTonight;
        memberSince.clear();
        gatherSeen = -1L;
        acceptedByUs.clear();
        decliner = null;
        earnedBefore.clear();
        cratesBefore.clear();
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b != null) {
                earnedBefore.put(b.getUUID(), m.save().skillsOf(b.getUUID().toString()).earned);
                cratesBefore.put(b.getUUID(), crates(m, b));
            }
        }
        line("ABSCHNITT k=" + st.cycle + " tag=" + day + " stufe=" + m.currentLevel() + " kapitel=" + m.save().chapters().chapter
                + " szenario=" + (scenario.isEmpty() ? "-" : scenario) + " ereignis=" + (eventTonight.isEmpty() ? "-" : eventTonight)
                + " fragment=" + m.save().nightFragment);
        save();
    }

    private static void vote(MinecraftServer server, SiegeManager m) {
        int yes = 0;
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b == null) {
                continue;
            }
            if (scenario.equals("zuwenig") && (st.group <= 3 || i >= 2)) {
                continue;   // no answer
            }
            if (scenario.equals("absage") && i == 1) {
                m.vote(b, false);
                decliner = b.getUUID();
                continue;
            }
            m.vote(b, true);
            acceptedByUs.add(b.getUUID());
            yes++;
        }
        if (yes < m.requiredAccepts()) {
            st.cancelDay = m.clockDayFor(server);   // too few: the night must not come
        }
        st.accepted.clear();
        for (UUID id : acceptedByUs) {
            st.accepted.add(id.toString());
        }
        st.decliner = decliner == null ? null : decliner.toString();
        save();
        line("Zusagen " + yes + "/" + st.group + " (nötig " + m.requiredAccepts() + ", gezählt " + m.acceptedCount() + ")");
    }

    /** Heerlager or dungeon: Bot01 fights there when the horn sounds. */
    private static void awayScenario(MinecraftServer server, SiegeManager m) {
        ServerPlayer b = bot(server, 0);
        if (b == null) {
            return;
        }
        try {
            if (scenario.equals("heerlager")) {
                BlockPos at = surface(core.getX() + 110, core.getZ());
                m.camp().testCamp(world, at, 0, m.save().nextSiegeDay);
                b.teleportTo(world, at.getX() + 0.5, at.getY() + 1, at.getZ() + 6.5, java.util.Set.of(), 0f, 0f, true);
                m.camp().testJoin(b);
                line("Heerlager bei " + at.toShortString() + ", " + name(0) + " kämpft dort");
            } else {
                BlockPos at = surface(core.getX(), core.getZ() + 170);
                b.teleportTo(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
                m.expeditions().testJoin(b);
                line("Dungeon bei " + at.toShortString() + ", " + name(0) + " drin=" + m.expeditions().testInside(b) + " " + m.expeditions().testState());
            }
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.error(P + "Szenario " + scenario, e);
            fail("Szenario " + scenario + " ließ sich nicht aufbauen: " + e);
        }
    }

    /**
     * A bot that died outside the siege (vanilla: a creeper at dusk, far from the core) presses "respawn" as a
     * player's client would: it comes back at the world spawn with its own things (they dropped where it died).
     * Defenders in the night are the siege's business and are left alone.
     */
    private static void respawnDead(MinecraftServer server, SiegeManager m, boolean active) {
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b == null || !b.isDeadOrDying() || (active && inNight.contains(b.getUUID()))) {
                continue;
            }
            String cause = b.getCombatTracker().getDeathMessage().getString();
            BlockPos spawn = TeamSim.standable(world, core.offset(2 + i, 0, 2), 3);   // the world spawn is at the castle
            ServerPlayer back = SimBots.spawn(server, world, name(i), spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            if (back != null) {
                fixMarkers(back, 0, 0);
            }
            line("Tod außerhalb der Belagerung k=" + st.cycle + ": " + cause + " -> wiederbelebt=" + (back != null)
                    + (m.isActive() ? " (Nacht läuft, nicht dabei)" : ""));
        }
    }

    /** The gathering ended and no night started: what the bots looked like; three in a row end the run. */
    private static void noStart(MinecraftServer server, SiegeManager m) {
        StringBuilder who = new StringBuilder();
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b == null) {
                who.append(' ').append(name(i)).append("=offline");
                continue;
            }
            String id = b.getUUID().toString();
            who.append(' ').append(name(i)).append("[tot=").append(b.isDeadOrDying()).append(" leben=").append(Math.round(b.getHealth()))
                    .append(" zuschauer=").append(b.isSpectator()).append(" eingetragen=").append(m.isRegistered(b))
                    .append(" zugesagt=").append(m.save().accepted != null && m.save().accepted.contains(id))
                    .append(" abstand=").append(Math.round(Math.sqrt(b.blockPosition().distSqr(core)))).append(']');
        }
        boolean expected = st.cancelDay >= 0;   // too few accepts: no night is right
        if (!expected) {
            noStartRow++;
        }
        check(expected, "Nacht nach dem Horn k=" + st.cycle + " (sammeln zuletzt " + lastGather + ")" + who);
        if (noStartRow >= 3) {
            finish(server, "3× keine Nacht nach dem Horn");
        }
    }

    /** From the horn to the start: the bots press ready (one not, in "frist"), logout and restart cases. */
    private static void gatherStep(MinecraftServer server, SiegeManager m, long now) {
        GatherPhase g = m.gatherForTests();
        if (gatherSeen < 0) {
            gatherSeen = now;
            StringBuilder heard = new StringBuilder();
            int yes = 0;
            int online = 0;
            for (int i = 0; i < st.group; i++) {
                ServerPlayer b = bot(server, i);
                if (b == null) {
                    continue;
                }
                online++;
                boolean h = SimBots.takeSounds(b).contains("minecraft:event.raid.horn");
                yes += h ? 1 : 0;
                heard.append(' ').append(name(i)).append('=').append(h ? "ja" : "nein");
            }
            hornHeard = yes + "/" + online;
            line("HORN k=" + st.cycle + " uhr=" + m.clockTimeFor(server) + " gehört:" + heard);
            if (resumedInGather) {
                resumedInGather = false;   // the horn sounded before the restart; the gathering just goes on
            } else {
                check(yes == online, "Horn gehört k=" + st.cycle + " " + hornHeard);
            }
        }
        for (int i = 0; i < st.group; i++) {
            SimBots.takeSounds(bot(server, i));   // the gathering's pings are not the night's start
        }
        lastGather = g.phase() + " mitglieder=" + g.members().size() + " bereit=" + g.readyCount();
        if (g.phase() != GatherPhase.Phase.READY) {
            return;
        }
        int members = 0;
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b == null || !g.isMember(b.getUUID())) {
                continue;
            }
            members++;
            long since = memberSince.computeIfAbsent(b.getUUID(), k -> now);
            if (now - since == 20) {
                brain(m, b, i).prepare(m, b, world);
            }
            if (now - since >= 40 && !g.isReady(b.getUUID()) && !(scenario.equals("frist") && i == 0)
                    && !((scenario.equals("logout_bereit") || scenario.equals("neustart_bereit")) && !scenarioDone)) {
                m.toggleGatherReady(b);
            }
        }
        if (scenarioDone) {
            return;
        }
        if (scenario.equals("logout_bereit")) {
            ServerPlayer b = bot(server, 0);
            Long since = b == null ? null : memberSince.get(b.getUUID());
            if (since != null && now - since >= 40) {
                scenarioDone = true;
                logout(server, b, now + 100);
            }
        } else if (scenario.equals("neustart_bereit") && members >= acceptedByUs.size() && members > 0
                && now - gatherSeen >= 160) {
            scenarioDone = true;
            st.restartDay = m.clockDayFor(server);
            restart(server, "bereit");
        }
    }

    // ------------------------------------------------------------------ the night

    private static void onNightStart(MinecraftServer server, SiegeManager m, long now) {
        nightStart = now;
        long day = m.clockDayFor(server);
        int time = m.clockTimeFor(server);
        if (st.startDay == day) {
            st.startsOnDay++;
        } else {
            st.startDay = day;
            st.startsOnDay = 1;
        }
        nightFails.clear();
        if (st.startsOnDay > 1) {
            nightFails.add("Nacht startet zum " + st.startsOnDay + ". Mal an Tag " + day);
        }
        int want = GatherPhase.startTime(m.config(), "ECLIPSE".equals(eventTonight));
        if (Math.abs(time - want) > 40) {
            nightFails.add("Startzeit " + time + " statt " + want);
        }
        lvlStart = m.currentLevel();
        chapterStart = m.save().chapters().chapter;
        nightNo = m.chapterNight();
        wonBefore = m.save().siegesWon;
        lostBefore = m.save().siegesLost;
        bossNight = m.bosses().planned();
        nightKind = m.tonightKindForTests().name();
        special = m.save().specialNight;
        lastStanding = 0;
        startSounds = 0;
        waves = false;
        wardenSeen = false;
        cursesSeen = 0;
        winSum = 0;
        winN = 0;
        slow = 0;
        maxMs = 0;
        inNight.clear();
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b != null && m.isDefending(b)) {
                inNight.add(b.getUUID());
                TeamBrain brain = brain(m, b, i);
                brain.prepare(m, b, world);
                brain.reset(world.getGameTime());
            }
        }
        boost(server, m);
        for (int i = 0; i < st.group; i++) {
            startSounds += SimBots.takeSounds(bot(server, i)).size();   // sent in the start tick
        }
        line("NACHT START k=" + st.cycle + " " + label(chapterStart, lvlStart) + " tag=" + day + " uhr=" + time
                + " sammeln=" + (gatherSeen < 0 ? "-" : (now - gatherSeen) / 20 + "s") + " verteidiger=" + inNight.size() + " boss=" + bossNight + " sonder=" + special + " ereignis="
                + (eventTonight.isEmpty() ? "-" : eventTonight) + " flüche=" + m.nightCurses() + (help > 0 ? " HILFE=" + help : ""));
    }

    private static void duringNight(MinecraftServer server, SiegeManager m, long now, double ms) {
        long t = now - nightStart;
        if (!waves && m.nightMobCount() > 0) {
            waves = true;
        }
        wardenSeen |= m.warden().spawned();
        cursesSeen = Math.max(cursesSeen, m.nightCurses());
        if (t > 0) {
            winSum += ms;
            maxMs = Math.max(maxMs, ms);
            if (++winN >= 200) {
                if (winSum / winN > 100.0) {
                    slow++;
                }
                winSum = 0;
                winN = 0;
            }
        }
        if (t == 100) {
            for (int i = 0; i < st.group; i++) {
                ServerPlayer b = bot(server, i);
                if (b == null) {
                    continue;
                }
                boolean expected = acceptedByUs.contains(b.getUUID());
                boolean defends = m.isDefending(b);
                boolean near = b.level() == world && b.blockPosition().distSqr(core) <= 16 * 16;
                int kit = kitItems(b);
                if (expected && (!defends || !near || kit == 0)) {
                    nightFails.add(name(i) + " nicht bereit am Core (verteidigt=" + defends + " nah=" + near + " kit=" + kit + ")");
                }
                if (!expected && (defends || kit > 0)) {
                    nightFails.add(name(i) + " hat abgesagt, ist aber dabei (verteidigt=" + defends + " kit=" + kit + ")");
                }
            }
        }
        if (t == 2400 && !waves) {
            nightFails.add("nach 120 s noch keine Welle");
        }
        if (help >= 2 && t % 200 == 0) {
            boost(server, m);
        }
        if (help >= 3 && t % 100 == 0) {
            ServerPlayer hitter = null;
            for (int i = 0; i < st.group && hitter == null; i++) {
                ServerPlayer b = bot(server, i);
                hitter = b != null && m.isDefending(b) && !b.isSpectator() ? b : null;
            }
            if (hitter != null) {
                for (Mob mob : world.getEntitiesOfClass(Mob.class, new AABB(core).inflate(40), Mob::isAlive)) {
                    if (SiegeManager.isNightSiegeMob(mob) || BossService.isBoss(mob)) {
                        mob.hurtServer(world, world.damageSources().playerAttack(hitter), 40f);
                    }
                }
            }
        }
        List<ServerPlayer> standing = new ArrayList<>();
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b != null && m.isDefending(b) && !b.isSpectator()) {
                standing.add(b);
            }
        }
        lastStanding = standing.size();
        for (ServerPlayer b : standing) {
            TeamBrain brain = brains.get(b.getUUID());
            if (brain == null) {
                continue;
            }
            try {
                brain.tick(m, b, world, world.getGameTime(), standing);
            } catch (RuntimeException e) {
                NightSiegeMod.LOGGER.warn(P + "Bot-Fehler {}: {}", brain.subclass, e.toString());
            }
        }
        if (!scenarioDone && scenario.equals("logout_nacht") && t == 300) {
            scenarioDone = true;
            ServerPlayer b = bot(server, 0);
            if (b != null) {
                logout(server, b, now + 800);
            }
        }
        if (!scenarioDone && scenario.equals("neustart_nacht") && t == 400) {
            scenarioDone = true;
            restart(server, "nacht");
        }
    }

    /** Five seconds after the night: result, time, core, inventory, kit, rewards, level, load. */
    private static void endChecks(MinecraftServer server, SiegeManager m) {
        SiegeConfig cfg = m.config();
        int secs = (int) ((nightEnd - nightStart) / 20);
        boolean won = m.save().siegesWon > wonBefore;
        boolean lost = m.save().siegesLost > lostBefore;
        String result = won ? "gewonnen" : lost ? "verloren" : "ohne_ergebnis";
        List<String> f = nightFails;
        if (!won && !lost) {
            f.add("Nacht ohne Sieg oder Niederlage");
        }
        if (secs > cfg.maxDurationSeconds + 5) {
            f.add("dauerte " + secs + " s, Grenze " + cfg.maxDurationSeconds + " s");
        }
        boolean cap8 = secs <= 480;   // Chef 17.11.: no night over 8:00, also with bossPhaseMaxSeconds 600
        if (won && lastStanding == 0 && !bossNight) {
            f.add("Sieg, aber kein Verteidiger stand");
        }
        if (lost && lastStanding > 0 && !bossNight && secs < cfg.maxDurationSeconds - 2) {
            f.add("Niederlage, obwohl " + lastStanding + " Verteidiger standen");
        }
        if (!waves) {
            f.add("keine Welle");
        }
        int dEarned = Integer.MAX_VALUE;
        int dCrates = Integer.MAX_VALUE;
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b == null) {
                f.add(name(i) + " nicht online nach der Nacht");
                continue;
            }
            int logs = count(b, Items.OAK_LOG);
            int flint = count(b, Items.FLINT);
            int kit = kitItems(b);
            boolean stash = m.kits().hasStash(b);
            if (logs != LOGS || flint != FLINTS) {
                f.add(name(i) + " eigene Sachen nicht zurück (Holz " + logs + "/" + LOGS + ", Feuerstein " + flint + "/" + FLINTS + ")");
                fixMarkers(b, logs, flint);
            }
            if (kit > 0 || stash) {
                f.add(name(i) + " Kit nicht weg (Kit-Teile " + kit + ", Ablage " + stash + ")");
            }
            if (b.isSpectator()) {
                f.add(name(i) + " noch Zuschauer");
            }
            UUID id = b.getUUID();
            if (inNight.contains(id)) {
                dEarned = Math.min(dEarned, m.save().skillsOf(id.toString()).earned - earnedBefore.getOrDefault(id, 0));
                dCrates = Math.min(dCrates, crates(m, b) - cratesBefore.getOrDefault(id, 0));
            } else if (id.equals(decliner)) {
                int d = m.save().skillsOf(id.toString()).earned - earnedBefore.getOrDefault(id, 0);
                if (d != 0) {
                    f.add(name(i) + " abgesagt, bekam trotzdem " + d + " Punkte");
                }
            }
        }
        if (dEarned == Integer.MAX_VALUE) {
            dEarned = 0;
            dCrates = 0;
        }
        int lvlNow = m.currentLevel();
        int chapterNow = m.save().chapters().chapter;
        boolean chapterChange = chapterNow != chapterStart;
        if (won && ((dEarned < 1 && !chapterChange) || dCrates < 1)) {   // the new chapter starts new characters (points from 0)
            f.add("Belohnung fehlt (Punkte +" + dEarned + ", Kisten +" + dCrates + ")");
        }
        if (won && !chapterChange && lvlNow != lvlStart + 1) {
            f.add("Stufe nach Sieg " + lvlNow + " statt " + (lvlStart + 1));
        }
        if (won && chapterChange && (chapterNow != chapterStart + 1 || lvlNow != 1)) {
            f.add("Kapitelwechsel falsch: Kapitel " + chapterNow + " Stufe " + lvlNow);
        }
        if (!won && (lvlNow != lvlStart || chapterChange)) {
            f.add("Stufe nach Niederlage " + lvlNow + " statt " + lvlStart);
        }
        if (slow > 0) {
            f.add(slow + "× 10 s im Schnitt über 100 ms je Tick (max " + Math.round(maxMs) + " ms)");
        }
        if (won) {
            st.won++;
        } else if (lost) {
            st.lost++;
        } else {
            st.aborted++;
        }
        String kind = nightKind.equals("GROUP") ? "finale" : nightKind.equals("LATE") ? "spaet" : bossNight ? "boss" : wardenSeen ? "waechter" : special ? "fragment" : !eventTonight.isEmpty() && !eventTonight.equals("NONE")
                ? "ereignis_" + eventTonight.toLowerCase(Locale.ROOT) : "normal";
        st.seen.merge(kind.startsWith("ereignis") ? "ereignis" : kind, 1, Integer::sum);
        if (cursesSeen > 0) {
            st.seen.merge("fluch", 1, Integer::sum);
        }
        if (chapterChange) {
            st.seen.merge("kapitelwechsel", 1, Integer::sum);
        }
        String text = "nacht k=" + st.cycle + " " + label(chapterStart, lvlStart) + " spieler=" + inNight.size() + "/" + st.group
                + " art=" + kind + (scenario.isEmpty() ? "" : " szenario=" + scenario) + " ergebnis=" + result + " dauer=" + secs
                + "s stehend=" + lastStanding + " horn=" + hornHeard + " startton=" + startSounds + " punkte=+" + dEarned + " kisten=+" + dCrates + " stufe=" + lvlStart + "->"
                + lvlNow + (chapterChange ? " kapitel=" + chapterNow : "") + " maxtick=" + Math.round(maxMs) + "ms"
                + (help > 0 ? " hilfe=" + help : "");
        if (f.isEmpty()) {
            st.ok++;
            line("OK " + text);
        } else {
            st.fail++;
            line("FAIL " + text + " | " + String.join("; ", f));
            for (String one : f) {
                st.fails.add("k=" + st.cycle + " " + label(chapterStart, lvlStart) + ": " + one);
            }
        }
        line((cap8 ? "OK" : "FAIL") + " 8:00-Grenze k=" + st.cycle + " " + secs + " s art=" + kind + " (maxDurationSeconds=" + cfg.maxDurationSeconds + " bossPhaseMaxSeconds=" + cfg.bossPhaseMaxSeconds + ")");
        if (cap8) {
            st.ok++;
        } else {
            st.fail++;
            st.fails.add("k=" + st.cycle + " " + label(chapterStart, lvlStart) + ": 8:00-Grenze " + secs + " s");
        }
        int abs = (chapterStart - 1) * com.mki.nightsiege.core.Chapters.LENGTH + nightNo;
        if (abs > st.reached && (won || lost)) {
            st.reached = abs;
            st.reachedLabel = label(chapterStart, lvlStart);
        }
        if (won) {
            st.lossStreak = 0;
        } else if (lost) {
            if (lvlStart == st.lossLevel && chapterStart == st.lossChapter) {
                st.lossStreak++;
            } else {
                st.lossStreak = 1;
                st.lossLevel = lvlStart;
                st.lossChapter = chapterStart;
            }
        }
        help = st.lossStreak >= 2 ? st.lossStreak - 1 : 0;
        save();
    }

    /** The morning: the bots spend their points; the run ends at its night or when it is stuck. */
    private static void afterMorning(MinecraftServer server, SiegeManager m) {
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b != null) {
                spend(m, b, st.subs.get(i), help >= 1);
            }
        }
        brains.clear();
        if (st.reached >= st.target) {
            finish(server, "Ziel Nacht " + st.target + " erreicht");
        } else if (st.lossStreak >= 7) {
            fail("festgefahren: " + st.lossStreak + " Niederlagen auf " + label(st.lossChapter, st.lossLevel));
            finish(server, "festgefahren");
        }
    }

    // ------------------------------------------------------------------ restarts and logouts

    private static void logout(MinecraftServer server, ServerPlayer b, long back) {
        outBot = b.getUUID();
        outName = b.getGameProfile().name();
        backAt = back;
        line("Abmelden: " + outName + " (" + (SiegeManager.get().isActive() ? "Nacht" : "Bereit-Phase") + ")");
        SimBots.logout(server, b);
    }

    private static void restart(MinecraftServer server, String kind) {
        st.pending = kind;
        save();
        try {
            Files.writeString(restartFile, kind, StandardCharsets.UTF_8);
        } catch (Exception e) {
            fail("Neustart-Datei: " + e);
        }
        line("NEUSTART (" + kind + ") k=" + st.cycle);
        server.halt(false);
    }

    /** Ten seconds after the bots are back from a restart. */
    private static void afterRestart(MinecraftServer server, SiegeManager m) {
        String kind = st.pending;
        st.pending = null;
        if ("nacht".equals(kind)) {
            if (m.isActive()) {
                line("Neustart in der Nacht: Nacht läuft wieder (k=" + st.cycle + ")");
            } else {
                List<String> f = new ArrayList<>();
                for (int i = 0; i < st.group; i++) {
                    ServerPlayer b = bot(server, i);
                    if (b == null) {
                        f.add(name(i) + " nicht da");
                        continue;
                    }
                    int logs = count(b, Items.OAK_LOG);
                    int flint = count(b, Items.FLINT);
                    if (logs != LOGS || flint != FLINTS || kitItems(b) > 0 || m.kits().hasStash(b) || b.isSpectator()) {
                        f.add(name(i) + " Holz " + logs + " Feuerstein " + flint + " Kit " + kitItems(b) + " Ablage "
                                + m.kits().hasStash(b) + " Zuschauer " + b.isSpectator());
                        fixMarkers(b, logs, flint);
                    }
                }
                if (m.save().clockHeld) {
                    f.add("Uhr steht noch");
                }
                check(f.isEmpty(), "Neustart in der Nacht: Inventar zurück, Kit weg, Uhr läuft" + (f.isEmpty() ? "" : " | " + String.join("; ", f)));
                st.aborted++;
                wasActive = false;
            }
            line("nach Neustart: nächste Belagerung Tag " + m.save().nextSiegeDay + " (heute " + m.clockDayFor(server) + ")");
        } else if ("bereit".equals(kind)) {
            check(m.gathering() || m.isActive(), "Neustart in der Bereit-Phase: Sammeln läuft wieder (sammeln="
                    + m.gathering() + " nacht=" + m.isActive() + ")");
        }
        save();
    }

    private static void finish(MinecraftServer server, String why) {
        if (st == null || st.finished) {
            return;
        }
        st.finished = true;
        line("ARTEN " + st.seen);
        for (String f : st.fails) {
            line("FEHLERLISTE " + f);
        }
        line("SUMME spieler=" + st.group + " bis=" + st.reachedLabel + " naechte=" + (st.won + st.lost + st.aborted) + " gewonnen="
                + st.won + " verloren=" + st.lost + " abgebrochen=" + st.aborted + " ok=" + st.ok + " fail=" + st.fail
                + " zyklen=" + st.cycle + " ende=" + why + " minuten=" + (System.currentTimeMillis() - st.startedMs) / 60000);
        save();
        try {
            Files.deleteIfExists(restartFile);
        } catch (Exception ignored) {
        }
        server.halt(false);
    }

    // ------------------------------------------------------------------ helpers

    private static String special(int cycle) {
        if (st.order != null && !st.order.isEmpty()) {
            return cycle >= 2 && cycle - 2 < st.order.size() ? st.order.get(cycle - 2) : "";
        }
        return SPECIAL.getOrDefault(cycle, "");
    }

    private static String name(int i) {
        return String.format(Locale.ROOT, "Bot%02d", i + 1);
    }

    private static ServerPlayer bot(MinecraftServer server, int i) {
        return server.getPlayerList().getPlayer(SimBots.idOf(name(i)));
    }

    private static String label(int chapter, int level) {
        return "K" + chapter + "N" + nightNo + "S" + level;
    }

    private static TeamBrain brain(SiegeManager m, ServerPlayer b, int i) {
        return brains.computeIfAbsent(b.getUUID(), id -> {
            TeamBrain brain = new TeamBrain(id, st.subs.get(i), TeamSim.CALIBRATED_SKILL, TeamSim.CALIBRATED_BOLDNESS, 77L * (i + 1) + st.cycle);
            double angle = i * 2.3999632;
            brain.post = TeamSim.standable(world, core.offset((int) Math.round(Math.cos(angle) * 4), 0,
                    (int) Math.round(Math.sin(angle) * 4)), 3);
            return brain;
        });
    }

    /** The bot spends its points like a player (the recommended build); with help the full tree. */
    private static void spend(SiegeManager m, ServerPlayer b, String[] sub, boolean full) {
        String id = b.getUUID().toString();
        PlayerSkills old = m.save().skillsOf(id);
        SkillClass clazz = SkillClass.valueOf(sub[0]);
        PlayerSkills now = full ? MeleeSim.build(m.skillTree(), clazz, sub[1]) : MeleeSim.buildBudget(m.skillTree(), clazz, sub[1], old.earned);
        now.earned = old.earned;
        now.xpPool = old.xpPool;
        now.waveCredit = old.waveCredit;
        now.lastNight = old.lastNight;
        now.catchUpPool = old.catchUpPool;
        now.donated = old.donated;
        now.lost = old.lost;
        now.profileVersion = Math.max(old.profileVersion, PlayerSkills.PROFILE_VERSION);
        m.save().skills.put(id, now);
        m.persist();
    }

    private static void boost(MinecraftServer server, SiegeManager m) {
        if (help < 2) {
            return;
        }
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b != null && m.isDefending(b) && !b.isSpectator()) {
                b.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 260, 2, false, false));
                b.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 260, 1, false, false));
            }
        }
    }

    /** Outside the nights the bots do not die of the world (they wait far out, in water or at night). */
    private static void keepSafe(MinecraftServer server) {
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b == null || !b.isAlive()) {
                continue;
            }
            b.clearFire();
            if (b.getHealth() < b.getMaxHealth()) {
                b.setHealth(b.getMaxHealth());
            }
            b.setAirSupply(b.getMaxAirSupply());
            b.getFoodData().setFoodLevel(20);
        }
    }

    /** By day the bots are out and about, far from the core (the horn brings them back). */
    private static void travel(MinecraftServer server) {
        for (int i = 0; i < st.group; i++) {
            ServerPlayer b = bot(server, i);
            if (b == null || !b.isAlive()) {
                continue;
            }
            double angle = Math.toRadians(i * 90 + st.cycle * 37);
            int dist = 120 + 30 * i;
            BlockPos at = surface(core.getX() + (int) (Math.cos(angle) * dist), core.getZ() + (int) (Math.sin(angle) * dist));
            b.teleportTo(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
        }
    }

    private static BlockPos surface(int x, int z) {
        world.getChunk(x >> 4, z >> 4);
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static void jump(MinecraftServer server, long total) {
        var clock = server.overworld().dimensionTypeRegistration().value().defaultClock().orElse(null);
        if (clock == null) {
            return;
        }
        long cur = server.clockManager().getTotalTicks(clock);
        if (total > cur) {
            server.clockManager().setTotalTicks(clock, total);
        }
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int kitItems(ServerPlayer p) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (KitService.isKit(inv.getItem(i))) {
                n++;
            }
        }
        return n;
    }

    private static int crates(SiegeManager m, ServerPlayer p) {
        List<String> pending = m.save().pendingCrates == null ? null : m.save().pendingCrates.get(p.getUUID().toString());
        return (CrateService.CRATE == null ? 0 : count(p, CrateService.CRATE)) + (pending == null ? 0 : pending.size());
    }

    /** After a loss of own things the run goes on with the right count (the FAIL is written). */
    private static void fixMarkers(ServerPlayer b, int logs, int flint) {
        Inventory inv = b.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.OAK_LOG) || s.is(Items.FLINT)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        inv.add(new ItemStack(Items.OAK_LOG, LOGS));
        inv.add(new ItemStack(Items.FLINT, FLINTS));
    }

    private static void check(boolean good, String what) {
        if (good) {
            st.ok++;
            line("OK " + what);
        } else {
            fail(what);
        }
    }

    private static void fail(String what) {
        if (st != null) {
            st.fail++;
            st.fails.add("k=" + st.cycle + ": " + what);
        }
        line("FAIL " + what);
    }

    private static void line(String text) {
        String l = P + text;
        System.out.println(l);
        if (logFile != null) {
            try {
                Files.writeString(logFile, l + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {
            }
        }
    }

    private static void save() {
        if (stateFile == null || st == null) {
            return;
        }
        try {
            Files.writeString(stateFile, GSON.toJson(st), StandardCharsets.UTF_8);
        } catch (Exception e) {
            NightSiegeMod.LOGGER.warn(P + "Zustand nicht gespeichert: {}", e.toString());
        }
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
