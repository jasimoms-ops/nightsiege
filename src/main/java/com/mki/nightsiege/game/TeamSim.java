package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SiegeMath;
import com.mki.nightsiege.core.SkillClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The balance tool's team simulation (Simon, 29 October 2026; concept in
 * claude/night-siege-update-fahrplan.md, update 5): groups of 1–10 bots defend real test nights
 * at the castle, many times per setting, and the tool counts how often a group of that size holds
 * a night of its level. Target: about 70 % wins.
 *
 * <p>Every run is one test night: the bots get the skill points one has by that level (or a full
 * tree with "voll"), the kit of the level, posts at the gates, and play until the night ends.
 * The statistics logger writes each night as usual ({@code testnacht_…json}, marked with the run),
 * and the tool adds one line per run and per bot to {@code nightsiege-teamsim.csv}.</p>
 *
 * <p>Started by {@code /nightsiege sim team …} (on a test world) or, in CI, by the environment
 * variable {@code NIGHTSIEGE_TEAMSIM} on a dedicated server: then the castle is built first and the
 * server stops when the matrix is done.</p>
 */
public final class TeamSim {

    /** Group templates (Simon: "zufällige Unterklassen + feste Vorlagen"). */
    static final String[] TEMPLATES = {"tank_heiler", "nur_schaden", "tanks_technik", "beschwoerer"};
    static final int[] DEFAULT_LEVELS = {1, 3, 5, 8, 10, 12, 15, 20};
    static final int[] DEFAULT_GROUPS = {1, 2, 4, 7, 10};
    /**
     * The bots as calibrated against the real nights of the logger (29 October 2026, run
     * balance-run-eich2 on the copy of Simon's castle): alone 0.8 / bold fitted best, groups 0.6 / bold,
     * so 0.7 and fully bold by default. "koennen=1 wagemut=0" is the perfect bot that holds its post.
     */
    static final double CALIBRATED_SKILL = 0.55;   // Tor 8.1 Eichung (vorher 0.7), mit mensch=1 als Standard
    static final double CALIBRATED_BOLDNESS = 1.0;
    /** The target of the balance (Simon 01:17: about 70 % wins on the group's own level). */
    public static final double TARGET = 0.70;

    private enum Phase { GROUND, SETUP, PREP, FIGHT, PAUSE, DONE }

    /** One setting of the matrix: config variant, level, group size, how the group is made, which run. */
    record Run(String variant, int level, int group, String mix, int index, List<String[]> subs) {
    }

    /** One finished run. */
    record Result(Run run, String outcome, boolean held, boolean won, int kills, int goal, int seconds, int deaths,
                  Map<String, double[]> bySub) {
    }

    // ------------------------------------------------------------------ settings

    private static final List<Integer> levels = new ArrayList<>();
    private static final List<Integer> groups = new ArrayList<>();
    private static int randomRuns = 6;
    private static int templateRuns = 1;
    private static boolean fullTree;
    /** "krieg": every run is a war leader's round with a bot leader instead of a test night (29 October 2026). */
    private static boolean warMode;
    /** "boss": every night a boss night, the eight bosses in turn (07.11.: Bosse 2 with the guard measured). */
    private static boolean bossMode;
    /** "bosse=alle" or "bosse=grave_king,warlord": boss nights, the boss of a run = list[run index % size] (p1111). */
    private static List<com.mki.nightsiege.core.BossKind> bossList = new ArrayList<>();
    /** "bastion": a round of the Letzte Bastion from wave 1 with the whole tree (Update 7), at most 40 minutes. */
    private static boolean bastionMode;
    private static int shard;
    private static int shards = 1;
    private static Path copyFile;
    /**
     * Tor 6.0 replay mode: "nachspiel=<file.json> [nachspiel_nur=id1,id2]" plays real nights as scenarios
     * (cast, level, event, boss, points) instead of the level x group matrix; N is the number of seeds each.
     * Key: the run's mix name "nacht_<id>".
     */
    private static final Map<String, Scenario> scenarios = new LinkedHashMap<>();
    private static String scenarioFile = "";
    private static String scenarioOnly = "";

    /** One real night to replay (points < 0: the points one has by that level). */
    record Scenario(String id, int level, List<String[]> subs, SiegeEvent event, com.mki.nightsiege.core.BossKind boss,
                    int points, double skill) {
    }
    /** Variant name → config overrides ("monsterDamage" → "0.8"); the first is the base. */
    private static final Map<String, Map<String, String>> variants = new LinkedHashMap<>();
    /** Overrides for every variant (like the class simulation's "key=value"). */
    private static final Map<String, String> common = new LinkedHashMap<>();

    // ------------------------------------------------------------------ state

    private static Phase phase = Phase.DONE;
    private static boolean started;
    private static boolean serverMode;
    private static long phaseTick;
    private static long fightStart;
    private static int index;
    private static int maxTicks;
    private static final List<Run> queue = new ArrayList<>();
    private static final List<Result> results = new ArrayList<>();
    private static final Map<UUID, TeamBrain> brains = new LinkedHashMap<>();
    /** Tor 6.1/6.5: per run the learned choice nodes of each bot, the tick-time samples, the live monsters. */
    private static final List<String> pendingNodes = new ArrayList<>();
    private static final List<Long> tickSamples = new ArrayList<>();
    private static final List<String> tickRows = new ArrayList<>();
    private static final List<String> extraCsv = new ArrayList<>();
    private static int liveMax;
    private static BlockPos core;
    private static ServerLevel world;
    private static String groundKind = "-";
    private static final Map<String, Object> backup = new LinkedHashMap<>();
    private static long startedAt;
    /** The run's mortar shots, shell bursts and monsters hit, from the trap service's shot log (30.10.). */
    private static int mortarShots;
    private static int shellBursts;
    private static int shellVictims;
    private static final java.util.function.Consumer<String> SHOT_COUNTER = TeamSim::countShot;

    private TeamSim() {
    }

    public static boolean running() {
        return started && phase != Phase.DONE;
    }

    public static boolean done() {
        return started && phase == Phase.DONE;
    }

    // ------------------------------------------------------------------ start

    /** CI: started by NIGHTSIEGE_TEAMSIM on a dedicated server (the castle is built first). */
    public static void startFromEnv(MinecraftServer server) {
        String args = System.getenv("NIGHTSIEGE_TEAMSIM");
        if (args == null) {
            return;
        }
        String map = System.getenv("NIGHTSIEGE_TEAMSIM_KARTE");
        parse(args + (map != null && !map.isBlank() ? " karte=" + map : ""));
        serverMode = true;
        world = server.overworld();
        core = null;
        begin(Phase.GROUND, server);
        NightSiegeMod.LOGGER.info("[Night Siege sim] Team-Sim aus der CI: {}", args);
    }

    /** In game (a test world): at the existing core. Returns null or a problem. */
    public static String start(MinecraftServer server, String args) {
        if (running()) {
            return "läuft schon";
        }
        SiegeManager m = SiegeManager.get();
        if (m == null || m.save().cores == null || m.save().cores.isEmpty()) {
            return "kein Core";
        }
        if (m.isActive() || m.isPreparing()) {
            return "eine Nacht läuft";
        }
        parse(args);
        CoreRecord c = m.save().cores.get(0);
        world = server.overworld();
        core = new BlockPos(c.x, c.y, c.z);
        serverMode = false;
        groundKind = "welt";
        begin(Phase.SETUP, server);
        return null;
    }

    public static void stop(MinecraftServer server) {
        if (running()) {
            finish(server, "gestoppt");
        }
    }

    private static void begin(Phase first, MinecraftServer server) {
        buildQueue();
        results.clear();
        extraCsv.clear();
        brains.clear();
        index = 0;
        phase = first;
        phaseTick = server.getTickCount();
        started = true;
        startedAt = System.currentTimeMillis();
        if (TrapService.shotLog == null) {
            TrapService.shotLog = SHOT_COUNTER;   // counts the mortars' shots for the MOERSER line
        }
        NightSiegeMod.LOGGER.info("[Night Siege sim] Team-Sim: {} Läufe (Teil {}/{}), Stufen {}, Gruppen {}, Varianten {}",
                queue.size(), shard + 1, shards, levels, groups, variants.keySet());
        System.out.println("[Night Siege sim] TEAMSTART;" + queue.size() + ";" + levels + ";" + groups + ";" + variants.keySet());
        // Monsters of the list this server does not know (a mod of the modpack missing): they never come.
        SiegeManager m = SiegeManager.get();
        if (m != null) {
            List<String> missing = new ArrayList<>();
            for (com.mki.nightsiege.core.MobEntry e : m.config().mobs) {
                for (String part : e.id.split("\\+")) {
                    net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(part);
                    if (!part.startsWith("nightsiege:") && (id == null
                            || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id))) {
                        missing.add(part);
                    }
                }
            }
            System.out.println("[Night Siege sim] TEAMMOBS;" + (missing.isEmpty() ? "alle da" : "fehlen " + missing));
        }
    }

    /**
     * "nur=<unterklasse>" = every bot that subclass (Tor 4c.4).
     * "L1,3,5 G2,4 N6 T1 voll shard=0/4 karte=… V=neu:monsterDamage=0.8,mobsPerLevel=1.6 trapDamage=0.7".
     */
    static void parse(String args) {
        levels.clear();
        groups.clear();
        variants.clear();
        common.clear();
        randomRuns = 6;
        templateRuns = 1;
        fullTree = false;
        warMode = false;
        bossMode = false;
        bossList = new ArrayList<>();
        bastionMode = false;
        shard = 0;
        shards = 1;
        copyFile = null;
        scenarios.clear();
        scenarioFile = "";
        scenarioOnly = "";
        for (String token : args == null ? new String[0] : args.trim().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (token.matches("[Ll][0-9,]+")) {
                addInts(levels, token.substring(1));
            } else if (token.matches("[Gg][0-9,]+")) {
                addInts(groups, token.substring(1));
            } else if (token.matches("[Nn]\\d+")) {
                randomRuns = Integer.parseInt(token.substring(1));
            } else if (token.matches("[Tt]\\d+")) {
                templateRuns = Integer.parseInt(token.substring(1));
            } else if (token.equals("voll")) {
                fullTree = true;
            } else if (token.equals("krieg")) {
                warMode = true;
            } else if (token.equals("boss")) {
                bossMode = true;
            } else if (token.startsWith("bosse=")) {
                bossMode = true;
                for (String id : token.substring(6).split(",")) {
                    if (id.equals("alle")) {
                        bossList.addAll(List.of(com.mki.nightsiege.core.BossKind.values()));
                    } else {
                        for (com.mki.nightsiege.core.BossKind k : com.mki.nightsiege.core.BossKind.values()) {
                            if (k.id().equals(id)) {
                                bossList.add(k);
                            }
                        }
                    }
                }
            } else if (token.equals("bastion")) {
                bastionMode = true;
                fullTree = true;   // the fun mode: every subclass full
            } else if (token.startsWith("shard=")) {
                String[] p = token.substring(6).split("/");
                shard = Integer.parseInt(p[0]);
                shards = Math.max(1, Integer.parseInt(p[1]));
            } else if (token.startsWith("nachspiel=")) {
                scenarioFile = token.substring(10);
            } else if (token.startsWith("nachspiel_nur=")) {
                scenarioOnly = token.substring(14);
            } else if (token.startsWith("karte=")) {
                copyFile = Path.of(token.substring(6));
            } else if (token.startsWith("V=")) {
                String body = token.substring(2);
                int colon = body.indexOf(':');
                String name = colon < 0 ? body : body.substring(0, colon);
                Map<String, String> over = new LinkedHashMap<>();
                if (colon >= 0) {
                    for (String kv : body.substring(colon + 1).split(",")) {
                        String[] p = kv.split("=", 2);
                        if (p.length == 2) {
                            over.put(p[0], p[1]);
                        }
                    }
                }
                variants.put(name, over);
            } else if (token.contains("=")) {
                String[] p = token.split("=", 2);
                common.put(p[0], p[1]);
            }
        }
        if (!scenarioFile.isEmpty()) {
            loadScenarios();
        }
        if (levels.isEmpty()) {
            for (int l : DEFAULT_LEVELS) {
                levels.add(l);
            }
        }
        if (groups.isEmpty()) {
            for (int g : DEFAULT_GROUPS) {
                groups.add(g);
            }
        }
        if (variants.isEmpty() || !variants.containsKey("aktuell")) {
            Map<String, Map<String, String>> withBase = new LinkedHashMap<>();
            withBase.put("aktuell", new LinkedHashMap<>());
            withBase.putAll(variants);
            variants.clear();
            variants.putAll(withBase);
        }
    }

    /**
     * The scenario file: {"nacht":[{"id":"n1","stufe":5,"gruppe":["medic","swift"],"ereignis":"NETHER_RIFT",
     * "boss":"ember_colossus","punkte":-1}]}; "gruppe" = subclass ids (MeleeSim.SUBCLASSES), boss/ereignis optional.
     */
    private static void loadScenarios() {
        Path file = Path.of(scenarioFile);
        if (!Files.exists(file) && System.getenv("GITHUB_WORKSPACE") != null) {
            file = Path.of(System.getenv("GITHUB_WORKSPACE")).resolve(scenarioFile);
        }
        if (!Files.exists(file)) {
            file = Path.of("..").resolve(scenarioFile);
        }
        java.util.Set<String> only = new java.util.HashSet<>();
        for (String s : scenarioOnly.split(",")) {
            if (!s.isBlank()) {
                only.add(s.trim());
            }
        }
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (com.google.gson.JsonElement el : root.getAsJsonArray("nacht")) {
                com.google.gson.JsonObject o = el.getAsJsonObject();
                String id = o.get("id").getAsString();
                if (!only.isEmpty() && !only.contains(id)) {
                    continue;
                }
                List<String[]> subs = new ArrayList<>();
                for (com.google.gson.JsonElement g : o.getAsJsonArray("gruppe")) {
                    String name = g.getAsString();
                    String[] found = null;
                    for (String[] s : MeleeSim.SUBCLASSES) {
                        if (s[2].equals(name)) {
                            found = s;
                        }
                    }
                    if (found == null) {
                        throw new IllegalArgumentException("unbekannte Unterklasse " + name + " in " + id);
                    }
                    subs.add(found);
                }
                SiegeEvent event = SiegeEvent.NONE;
                if (o.has("ereignis") && !o.get("ereignis").isJsonNull() && !o.get("ereignis").getAsString().isBlank()) {
                    event = SiegeEvent.valueOf(o.get("ereignis").getAsString().toUpperCase(Locale.ROOT));
                }
                com.mki.nightsiege.core.BossKind boss = null;
                if (o.has("boss") && !o.get("boss").isJsonNull() && !o.get("boss").getAsString().isBlank()) {
                    for (com.mki.nightsiege.core.BossKind b : com.mki.nightsiege.core.BossKind.values()) {
                        if (b.id().equals(o.get("boss").getAsString())) {
                            boss = b;
                        }
                    }
                    if (boss == null) {
                        throw new IllegalArgumentException("unbekannter Boss in " + id);
                    }
                }
                int points = o.has("punkte") && !o.get("punkte").isJsonNull() ? o.get("punkte").getAsInt() : -1;
                // Tor 6.1: optional "koennen" = the bots' skill of this night (0.5/0.7/0.9 in the raster), else the knob.
                double skill = o.has("koennen") && !o.get("koennen").isJsonNull() ? o.get("koennen").getAsDouble() : -1;
                scenarios.put("nacht_" + id, new Scenario(id, o.get("stufe").getAsInt(), subs, event, boss, points, skill));
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Nachspiel-Datei nicht lesbar (" + file + "): " + e, e);
        }
        System.out.println("[Night Siege sim] NACHSPIEL;" + scenarios.size() + " Szenarien aus " + file);
    }

    private static void addInts(List<Integer> into, String csv) {
        for (String p : csv.split(",")) {
            if (!p.isBlank()) {
                into.add(Math.max(1, Math.min(1000, Integer.parseInt(p.trim()))));
            }
        }
    }

    /** The whole matrix, then the share of this CI job (every shards-th run). */
    private static void buildQueue() {
        queue.clear();
        List<Run> all = new ArrayList<>();
        for (Scenario sc : scenarios.values()) {
            for (String v : variants.keySet()) {
                for (int i = 0; i < randomRuns; i++) {
                    all.add(new Run(v, sc.level(), sc.subs().size(), "nacht_" + sc.id(), i, sc.subs()));
                }
            }
        }
        for (int level : scenarios.isEmpty() ? levels : List.<Integer>of()) {
            for (int group : groups) {
                int n = 0;
                List<Run> cell = new ArrayList<>();
                for (int i = 0; i < randomRuns; i++) {
                    cell.add(new Run("", level, group, "zufall", n++, compose("zufall", group, seed(level, group, "zufall", i))));
                }
                if (group >= 2 && common.get("nur") == null) {
                    for (String t : TEMPLATES) {
                        for (int i = 0; i < templateRuns; i++) {
                            cell.add(new Run("", level, group, t, n++, compose(t, group, seed(level, group, t, i))));
                        }
                    }
                }
                // Every variant plays the same groups, so old and new can be compared run by run.
                for (String v : variants.keySet()) {
                    for (Run r : cell) {
                        all.add(new Run(v, r.level(), r.group(), r.mix(), r.index(), r.subs()));
                    }
                }
            }
        }
        for (int i = 0; i < all.size(); i++) {
            if (i % shards == shard) {
                Run r = all.get(i);
                queue.add(new Run(r.variant(), r.level(), r.group(), r.mix(), r.index(), withForced(r.subs())));
            }
        }
    }

    /**
     * Sim knob "mit=demolition[,gunner…]" (30.10.): every group gets these subclasses, in place of its
     * last members, so one subclass can be measured in many nights. Without it the groups stay as they are.
     */
    static List<String[]> withForced(List<String[]> subs) {
        String want = common.get("mit");
        if (want == null || want.isBlank()) {
            return subs;
        }
        List<String[]> out = new ArrayList<>(subs);
        int slot = out.size() - 1;
        for (String name : want.split(",")) {
            String[] info = null;
            for (String[] s : MeleeSim.SUBCLASSES) {
                if (s[2].equals(name.trim())) {
                    info = s;
                }
            }
            boolean has = false;
            for (String[] s : out) {
                has |= info != null && s[2].equals(info[2]);
            }
            if (info != null && !has && slot >= 0) {
                out.set(slot--, info);
            }
        }
        return out;
    }

    /** Tor 8.2 Zusatz: "prefer=a/b" (variant or common): these nodes are learned first, their group mates shut out. */
    private static java.util.Set<String> preferred(String variant) {
        String v = variants.getOrDefault(variant, Map.of()).getOrDefault("prefer", common.get("prefer"));
        if (v == null || v.isBlank()) {
            return null;
        }
        return new java.util.HashSet<>(java.util.Arrays.asList(v.split("/")));
    }

    private static long seed(int level, int group, String mix, int i) {
        return ((long) level * 1_000_003L) ^ ((long) group * 7919L) ^ ((long) mix.hashCode() << 20) ^ (i * 104729L);
    }

    /** The subclasses of one group. */
    static List<String[]> compose(String mix, int group, long seed) {
        Random random = new Random(seed);
        List<String[]> out = new ArrayList<>();
        List<String[]> all = new ArrayList<>(List.of(MeleeSim.SUBCLASSES));
        // Sim knob "nur=golemsmith" (Tor 4c.4): every bot of every group is this one subclass.
        String only = common.get("nur");
        if (only != null && !only.isBlank()) {
            for (String[] s : MeleeSim.SUBCLASSES) {
                if (s[2].equals(only.trim())) {
                    for (int i = 0; i < group; i++) {
                        out.add(s);
                    }
                    return out;
                }
            }
        }
        switch (mix) {
            case "tank_heiler" -> {
                out.add(pick(random, TeamBrain.Role.TANK));
                out.add(pick(random, TeamBrain.Role.HEALER));
                while (out.size() < group) {
                    out.add(pickDamage(random));
                }
            }
            case "nur_schaden" -> {
                while (out.size() < group) {
                    out.add(pickDamage(random));
                }
            }
            case "tanks_technik" -> {
                int tanks = (group + 1) / 2;
                while (out.size() < tanks) {
                    out.add(pick(random, TeamBrain.Role.TANK));
                }
                while (out.size() < group) {
                    out.add(pick(random, TeamBrain.Role.ENGINEER));
                }
            }
            case "beschwoerer" -> {
                while (out.size() < group) {
                    out.add(pick(random, TeamBrain.Role.SUMMONER));
                }
            }
            default -> {
                // Random, like real players pick: no subclass twice while there are others left.
                java.util.Collections.shuffle(all, random);
                for (int i = 0; i < group; i++) {
                    out.add(all.get(i % all.size()));
                }
            }
        }
        return out.subList(0, Math.min(group, out.size()));
    }

    private static String[] pick(Random random, TeamBrain.Role role) {
        List<String[]> of = new ArrayList<>();
        for (String[] s : MeleeSim.SUBCLASSES) {
            if (TeamBrain.roleOf(s[2]) == role) {
                of.add(s);
            }
        }
        return of.get(random.nextInt(of.size()));
    }

    private static String[] pickDamage(Random random) {
        TeamBrain.Role[] dmg = {TeamBrain.Role.MELEE, TeamBrain.Role.RANGED, TeamBrain.Role.ENGINEER, TeamBrain.Role.SUMMONER};
        return pick(random, dmg[random.nextInt(dmg.length)]);
    }

    // ------------------------------------------------------------------ the loop

    /** Every server tick. */
    public static void tick(MinecraftServer server) {
        if (!running()) {
            return;
        }
        SiegeManager m = SiegeManager.get();
        if (m == null || world == null) {
            finish(server, "kein Spiel");
            return;
        }
        long now = server.getTickCount();
        // A tower shell that burst in this tick's entity tick leaves its trap as "the blow that lands now"
        // until the traps' own tick; the bots' sword hits below would be counted as that tower (30.10.).
        TrapService.clearHitting();
        try {
            switch (phase) {
                case GROUND -> {
                    if (now - phaseTick < 100) {
                        return;   // the spawn area loads first
                    }
                    SimCastle.Ground ground = SimCastle.build(world, new BlockPos(0, 70, 0), copyFile);
                    core = ground.core();
                    groundKind = ground.kind();
                    world.setBlock(core, ModContent.SIEGE_CORE.defaultBlockState(), 3);
                    m.onCorePlaced(world, core, null);
                    run(server, "tick sprint 1000000000");
                    System.out.println("[Night Siege sim] TEAMGROUND;" + groundKind + ";" + core.toShortString() + ";" + ground.blocks());
                    phase = Phase.SETUP;
                    phaseTick = now;
                }
                case SETUP -> setup(server, m, now);
                case PREP -> {
                    keepStanding(server);   // the night starts them at full health anyway
                    if (now - phaseTick == 20) {
                        for (Map.Entry<UUID, TeamBrain> e : brains.entrySet()) {
                            ServerPlayer bot = server.getPlayerList().getPlayer(e.getKey());
                            if (bot != null) {
                                e.getValue().prepare(m, bot, world);
                            }
                        }
                    }
                    if (m.isActive() && (!warMode || now - phaseTick > 20)) {
                        phase = Phase.FIGHT;
                        fightStart = now;
                        for (Map.Entry<UUID, TeamBrain> e : brains.entrySet()) {
                            ServerPlayer bot = server.getPlayerList().getPlayer(e.getKey());
                            if (bot != null) {
                                bot.setHealth(bot.getMaxHealth());   // the kit came with the night: a fair start
                                e.getValue().reset(world.getGameTime());
                            }
                        }
                    } else if (now - phaseTick > 20L * (m.config().testPrepSeconds + 30)) {
                        record(server, m, "start_fehlgeschlagen");
                    }
                }
                case FIGHT -> {
                    if (!m.isActive()) {
                        record(server, m, null);
                        return;
                    }
                    if (now - fightStart > maxTicks) {
                        m.abort(server);
                        record(server, m, "zeit");
                        return;
                    }
                    if ((now - fightStart) % 200 == 199) {
                        sampleTicks(server, now - fightStart);
                    }
                    List<ServerPlayer> standing = new ArrayList<>();
                    for (UUID id : brains.keySet()) {
                        ServerPlayer bot = server.getPlayerList().getPlayer(id);
                        if (bot != null && m.isDefending(bot) && !bot.isSpectator()) {
                            standing.add(bot);
                        }
                    }
                    for (ServerPlayer bot : standing) {
                        TeamBrain brain = brains.get(bot.getUUID());
                        try {
                            brain.tick(m, bot, world, world.getGameTime(), standing);
                        } catch (RuntimeException e) {
                            NightSiegeMod.LOGGER.warn("[Night Siege sim] Bot-Fehler {}: {}", brain.subclass, e.toString());
                        }
                    }
                }
                case PAUSE -> {
                    keepStanding(server);
                    if (now - phaseTick >= 40) {
                        phase = Phase.SETUP;
                    }
                }
                default -> {
                }
            }
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.error("[Night Siege sim] Team-Sim-Fehler", e);
            finish(server, "Fehler: " + e);
        }
    }

    /** A new run: the bots of the group, their skills, posts, the test night's preparation. */
    private static void setup(MinecraftServer server, SiegeManager m, long now) {
        restoreConfig(m);
        if (index >= queue.size()) {
            finish(server, null);
            return;
        }
        Run run = queue.get(index);
        applyConfig(m, common);
        applyConfig(m, variants.getOrDefault(run.variant(), Map.of()));
        clearField(m);
        pendingNodes.clear();
        tickSamples.clear();
        tickRows.clear();
        liveMax = 0;
        // The bots: as many as the group, "Bot01" to "BotNN" every time, the NN-th playing the NN-th subclass.
        // Picked by name, not by place in the list (31.10.): a bot that died after the night was missing from
        // SimBots.all(), so the others moved up a place, the last name came twice (one subclass lost) and the
        // dead body stayed in the player list as a defender with the last run's skills. SimBots.spawn gives
        // a standing bot back as it is and brings a dead one back fresh.
        List<String> names = new ArrayList<>();
        java.util.Set<UUID> wanted = new java.util.HashSet<>();
        for (int i = 0; i < run.group(); i++) {
            names.add(String.format(Locale.ROOT, "Bot%02d", i + 1));
            wanted.add(SimBots.idOf(names.get(i)));
        }
        for (ServerPlayer extra : SimBots.known()) {
            if (!wanted.contains(extra.getUUID())) {
                SimBots.remove(server, extra);
            }
        }
        brains.clear();
        for (int i = 0; i < run.group(); i++) {
            String name = names.get(i);
            ServerPlayer bot = SimBots.spawn(server, world, name, core.getX() + 0.5, core.getY() + 1, core.getZ() + 2.5);
            if (bot == null) {
                finish(server, "Bot ließ sich nicht erzeugen");
                return;
            }
            String[] sub = run.subs().get(i);
            String id = bot.getUUID().toString();
            if (!m.save().isRegistered(id)) {
                m.save().register(id);
            }
            Scenario sc = scenarios.get(run.mix());
            // Tor 6.5 / 8.3b "wahl=zufall": at every choice node the bot picks by chance (fixed seed per bot and run);
            // 20 % of the bots keep the standard path as the control. Without the knob: the standard path as always.
            long botSeed = seed(run.level(), run.group(), run.mix(), run.index()) * 31 + i;
            Random pickOrder = null;
            if ("zufall".equals(common.get("wahl")) || "zufall".equals(variants.getOrDefault(run.variant(), Map.of()).get("wahl"))) {
                Random ctl = new Random(Long.rotateLeft(botSeed * 0x9E3779B97F4A7C15L, 23) ^ 0xB0A7L);
                if (ctl.nextDouble() >= 0.2) {
                    pickOrder = ctl;
                }
            }
            PlayerSkills skills = fullTree
                    ? MeleeSim.build(m.skillTree(), SkillClass.valueOf(sub[0]), sub[1], pickOrder, preferred(run.variant()))
                    : MeleeSim.buildBudget(m.skillTree(), SkillClass.valueOf(sub[0]), sub[1],
                    sc != null && sc.points() >= 0 ? sc.points() : TestKit.pointsBefore(run.level(), m.config()), pickOrder,
                    preferred(run.variant()));
            m.save().skills.put(id, skills);
            StringBuilder nodes = new StringBuilder();
            for (com.mki.nightsiege.core.SkillNode n : m.skillTree().ofClass(SkillClass.valueOf(sub[0]))) {
                if (n.inGroup() && skills.levelOf(n.id()) > 0) {
                    nodes.append(nodes.length() == 0 ? "" : "+").append(n.id());
                }
            }
            pendingNodes.add(sub[2] + ";" + (pickOrder == null ? "standard" : "zufall") + ";" + nodes);
            if (bot.isSpectator() || !bot.isAlive()) {
                bot.setGameMode(GameType.SURVIVAL);
            }
            bot.removeAllEffects();
            bot.clearFire();
            bot.setHealth(bot.getMaxHealth());
            bot.getFoodData().setFoodLevel(20);
            bot.getFoodData().setSaturation(5f);
            bot.getInventory().clearContent();
            // Tor 6.0 "mensch=1": every bot its own skill (Gaussian around "koennen"), the human behaviours on.
            BotHuman human = isHuman(run.variant()) ? BotHuman.of((k, d) -> humanKnob(run.variant(), k, d)) : BotHuman.OFF;
            double botSkill = sc != null && sc.skill() >= 0 ? sc.skill() : knob(run.variant(), sub[0], "koennen", CALIBRATED_SKILL);
            if (human.on) {
                botSkill = Math.max(0.3, Math.min(1.0, botSkill + gaussOf(botSeed) * human.streuung));
            }
            TeamBrain brain = new TeamBrain(bot.getUUID(), sub, botSkill,
                    knob(run.variant(), sub[0], "wagemut", CALIBRATED_BOLDNESS), botSeed);
            brain.humanize(human);
            brain.able = knob(run.variant(), sub[0], "faehig", 1.0) > 0;   // Tor 8.3: "faehig=0" = the bots of Tor 8.1
            brain.wallShift = human.on ? BotHuman.wallShift(human.mauerVersatz, gaussOf(botSeed * 7 + 3)) : 0;
            brain.core = Vec3.atBottomCenterOf(core);
            System.out.println("[Night Siege sim] BOTKOENNEN;" + run.variant() + ";" + run.mix() + ";" + run.index() + ";"
                    + sub[2] + ";" + String.format(Locale.ROOT, "%.2f", brain.skill));
            brains.put(bot.getUUID(), brain);
        }
        posts(m);
        for (Map.Entry<UUID, TeamBrain> e : brains.entrySet()) {
            ServerPlayer bot = server.getPlayerList().getPlayer(e.getKey());
            BlockPos p = e.getValue().post;
            if (bot != null && p != null) {
                // Near the core for the start (defenders are those near it), the posts when the night begins.
                bot.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            }
        }
        StatsRecorder.simLabel = String.format(Locale.ROOT, "%s|L%d|G%d|%s|%d", run.variant(), run.level(), run.group(),
                run.mix(), run.index());
        StatsRecorder.lastFinished = null;
        mortarShots = 0;
        shellBursts = 0;
        shellVictims = 0;
        applyConfig(m, Map.of("testPrepSeconds", "12"));   // time for the engineers' traps
        boolean ok;
        if (warMode) {
            // A war leader's round: a bot leads the monsters, the group defends (its own build-up first).
            List<ServerPlayer> group = new ArrayList<>();
            for (UUID id : brains.keySet()) {
                ServerPlayer bot = server.getPlayerList().getPlayer(id);
                if (bot != null) {
                    group.add(bot);
                }
            }
            ok = m.warLeader().startBotRound(server, world, core, run.level(), group);
            maxTicks = (m.config().warPrepSeconds + m.config().warWaves * m.config().warWaveSeconds + 120) * 20;
        } else if (bastionMode) {
            // The Letzte Bastion (Update 7): the group from wave 1 until all have fallen (at most 40 minutes).
            List<ServerPlayer> group = new ArrayList<>();
            for (UUID id : brains.keySet()) {
                ServerPlayer bot = server.getPlayerList().getPlayer(id);
                if (bot != null) {
                    group.add(bot);
                }
            }
            ok = m.bastion().startBotRound(server, world, core, 1, group);
            maxTicks = 40 * 60 * 20;
        } else if (scenarios.containsKey(run.mix())) {
            // Replay of a real night (Tor 6.0): its event and boss.
            Scenario sc = scenarios.get(run.mix());
            ok = m.prepareTest(server, world, core, run.level(), sc.event(), sc.boss());
            maxTicks = (SiegeMath.durationSeconds(run.level(), m.config()) + (sc.boss() != null ? 420 : 120)) * 20;
        } else if (bossMode) {
            // A boss night, the eight in turn by the run (Bosse 2 with the guard, 07.11.).
            com.mki.nightsiege.core.BossKind[] all = com.mki.nightsiege.core.BossKind.values();
            com.mki.nightsiege.core.BossKind boss = all[Math.floorMod(run.index() + run.level() * 3 + run.group() * 5
                    + run.mix().hashCode(), all.length)];
            if (!bossList.isEmpty()) {
                boss = bossList.get(run.index() % bossList.size());
            }
            ok = m.prepareTest(server, world, core, run.level(), SiegeEvent.NONE, boss);
            System.out.println("[Night Siege sim] BOSS;" + run.variant() + ";" + run.level() + ";" + run.group() + ";" + run.mix()
                    + ";" + run.index() + ";" + boss.id());
            maxTicks = (SiegeMath.durationSeconds(run.level(), m.config()) + 120) * 20;
        } else {
            ok = m.prepareTest(server, world, core, run.level(), SiegeEvent.NONE);
            maxTicks = (SiegeMath.durationSeconds(run.level(), m.config()) + 120) * 20;
        }
        if (!ok) {
            record(server, m, "start_fehlgeschlagen");
            return;
        }
        phase = Phase.PREP;
        phaseTick = now;
    }

    /**
     * The subclass a bot plays in this run. Since 29 October 2026 every bot holds its subclass node from
     * the start (level 1 free); should the logger still have an empty subclass, the run gave "BotNN" the
     * NN-th subclass of the group.
     */
    static String plannedSub(Run run, String name, StatsRecorder.Spieler who) {
        // Its brain knows (the bots are reused in any order, so "Bot03" can be the group's second).
        if (who != null && who.uuid != null) {
            try {
                TeamBrain brain = brains.get(UUID.fromString(who.uuid));
                if (brain != null) {
                    return brain.subclass;
                }
            } catch (IllegalArgumentException ignored) {
                // not a UUID: fall back to the name
            }
        }
        if (name != null && name.matches("Bot\\d{2}")) {
            int i = Integer.parseInt(name.substring(3)) - 1;
            if (i >= 0 && i < run.subs().size()) {
                return run.subs().get(i)[2];
            }
        }
        return name;
    }

    /**
     * Tor 6.1: every 200 ticks of the fight the last 100 tick times (work time, the sim runs sprinting) and
     * the live monsters, one TICK row each (Tickzeit gegen lebende Monster); the run's P95 comes from all samples.
     */
    private static void sampleTicks(MinecraftServer server, long fightTick) {
        int live = 0;
        for (net.minecraft.world.entity.Entity e : world.getAllEntities()) {
            if (e instanceof Enemy && e.isAlive()) {
                live++;
            }
        }
        liveMax = Math.max(liveMax, live);
        if (System.getenv("NIGHTSIEGE_LAGE") != null && core != null) {
            lage(server, fightTick);   // Tor 8: where the monsters stand and how close each bot is (local diagnosis only)
        }
        long[] times = server.getTickTimesNanos().clone();
        long[] window = new long[times.length];
        int n = 0;
        for (long t : times) {
            if (t > 0) {
                window[n++] = t;
                tickSamples.add(t);
            }
        }
        if (n > 0) {
            java.util.Arrays.sort(window, 0, n);
            tickRows.add(fightTick + ";" + live + ";" + String.format(Locale.ROOT, "%.2f",
                    window[Math.min(n - 1, (int) Math.ceil(0.95 * n) - 1)] / 1e6) + ";"
                    + String.format(Locale.ROOT, "%.2f", window[n - 1] / 1e6));
        }
    }

    /** LAGE;tick;monsters by distance to the core (<8, <16, <32, more);per bot: subclass:nearest monster:distance to post. */
    private static void lage(MinecraftServer server, long fightTick) {
        int[] rings = new int[4];
        List<net.minecraft.world.entity.Entity> mobs = new ArrayList<>();
        for (net.minecraft.world.entity.Entity e : world.getAllEntities()) {
            if (e instanceof Enemy && e.isAlive() && !Companions.isCompanion(e)) {
                double d = Math.sqrt(e.distanceToSqr(Vec3.atCenterOf(core)));
                rings[d < 8 ? 0 : d < 16 ? 1 : d < 32 ? 2 : 3]++;
                mobs.add(e);
            }
        }
        StringBuilder sb = new StringBuilder("[Night Siege sim] LAGE;" + fightTick + ";" + rings[0] + "/" + rings[1] + "/" + rings[2] + "/" + rings[3]);
        Map<String, Integer> farTypes = new TreeMap<>();
        for (net.minecraft.world.entity.Entity e : mobs) {
            if (e.distanceToSqr(Vec3.atCenterOf(core)) >= 32 * 32) {
                String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()
                        + (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null ? "+" : "")
                        + "@" + (int) Math.sqrt(e.distanceToSqr(Vec3.atCenterOf(core))) / 10 * 10;
                farTypes.merge(type, 1, Integer::sum);
            }
        }
        sb.append(";").append(farTypes);
        for (Map.Entry<UUID, TeamBrain> b : brains.entrySet()) {
            ServerPlayer bot = server.getPlayerList().getPlayer(b.getKey());
            if (bot == null || b.getValue().post == null) {
                continue;
            }
            double near = 99;
            for (net.minecraft.world.entity.Entity e : mobs) {
                near = Math.min(near, bot.distanceTo(e));
            }
            sb.append(String.format(Locale.ROOT, ";%s:%.0f:%.0f%s", b.getValue().subclass, near,
                    Math.sqrt(bot.position().distanceToSqr(Vec3.atBottomCenterOf(b.getValue().post))), bot.isSpectator() ? "x" : ""));
        }
        System.out.println(sb);
    }

    private static double tickP95() {
        if (tickSamples.isEmpty()) {
            return -1;
        }
        long[] a = new long[tickSamples.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = tickSamples.get(i);
        }
        java.util.Arrays.sort(a);
        return a[Math.min(a.length - 1, (int) Math.ceil(0.95 * a.length) - 1)] / 1e6;
    }

    /** The night is over (or never came): one result, then the pause. */
    private static void record(MinecraftServer server, SiegeManager m, String problem) {
        Run run = queue.get(index);
        StatsRecorder.NightFile night = StatsRecorder.lastFinished;
        String outcome = problem != null ? problem : night == null ? "unbekannt" : night.ergebnis;
        boolean held = "test_gehalten".equals(outcome);
        int kills = night == null ? 0 : night.kills;
        int goal = night == null ? 0 : night.killZiel;
        boolean won = held && (goal <= 0 || kills >= goal);
        Map<String, double[]> bySub = new TreeMap<>();
        if (night != null) {
            for (StatsRecorder.Welle w : night.wellen) {
                for (Map.Entry<String, StatsRecorder.SpielerWelle> e : w.spieler.entrySet()) {
                    StatsRecorder.Spieler who = night.spieler.get(e.getKey());
                    String sub = who == null || who.unterklasse.isBlank() ? plannedSub(run, e.getKey(), who) : who.unterklasse;
                    double[] v = bySub.computeIfAbsent(e.getKey() + ":" + sub, k -> new double[5]);
                    StatsRecorder.SpielerWelle pw = e.getValue();
                    v[0] += pw.schaden;
                    v[1] += pw.erhalten;
                    v[2] += pw.heilungGegeben;
                    v[3] += pw.kills;
                    v[4] += pw.tode;
                }
            }
        }
        Result r = new Result(run, outcome, held, won, kills, goal, night == null ? 0 : night.dauerSekunden,
                night == null ? 0 : night.tode, bySub);
        results.add(r);
        // Tor 6.1: RASTER row (event, boss, skill, tick time, live monsters, decided early = lost in the first third
        // of the planned time) and the choice nodes (KNOTEN) and tick windows (TICK) of the run.
        Scenario scn = scenarios.get(run.mix());
        double skillSum = 0;
        for (TeamBrain b : brains.values()) {
            skillSum += b.skill;
        }
        double skillMean = brains.isEmpty() ? -1 : skillSum / brains.size();
        int planned = SiegeMath.durationSeconds(run.level(), m.config());
        boolean early = !won && r.seconds() * 3 < planned;
        String key = run.variant() + ";" + run.level() + ";" + run.group() + ";" + run.mix() + ";" + run.index();
        extraCsv.add(String.format(Locale.ROOT, "RASTER;%s;%s;%s;%.2f;%d;%.2f;%d;%d;%d;%d;%s", key,
                scn == null ? "NONE" : scn.event().name(), scn == null || scn.boss() == null ? "-" : scn.boss().id(),
                skillMean, won ? 1 : 0, tickP95(), liveMax, early ? 1 : 0, r.deaths(), r.seconds(), outcome));
        System.out.println("[Night Siege sim] " + extraCsv.get(extraCsv.size() - 1));   // also in the log: survives a killed job
        for (String nodeRow : pendingNodes) {
            extraCsv.add("KNOTEN;" + key + ";" + nodeRow);
        }
        for (String tickRow : tickRows) {
            extraCsv.add("TICK;" + key + ";" + tickRow);
        }
        StringBuilder subs = new StringBuilder();
        for (String[] s : run.subs()) {
            subs.append(subs.length() == 0 ? "" : "+").append(s[2]);
        }
        String line = String.format(Locale.ROOT, "[Night Siege sim] LAUF;%s;%d;%d;%s;%d;%s;%s;%s;%d;%d;%d;%d;%s",
                run.variant(), run.level(), run.group(), run.mix(), run.index(), outcome, held ? 1 : 0, won ? 1 : 0,
                kills, goal, r.seconds(), r.deaths(), subs);
        System.out.println(line);
        NightSiegeMod.LOGGER.info(line);
        for (Map.Entry<String, double[]> e : bySub.entrySet()) {
            double[] v = e.getValue();
            String sub = e.getKey().substring(e.getKey().indexOf(':') + 1);
            System.out.println(String.format(Locale.ROOT, "[Night Siege sim] BOT;%s;%d;%d;%s;%d;%s;%.0f;%.0f;%.0f;%.0f;%.0f",
                    run.variant(), run.level(), run.group(), run.mix(), run.index(), sub, v[0], v[1], v[2], v[3], v[4]));
        }
        // The demolition men (30.10.): mortars set / spots set / salvos called each, and the mortars' shots.
        StringBuilder demo = new StringBuilder();
        for (TeamBrain b : brains.values()) {
            if ("demolition".equals(b.subclass)) {
                demo.append(demo.length() == 0 ? "" : "+").append(b.mortarCount()).append('/').append(b.aims).append('/').append(b.salvos);
            }
        }
        if (demo.length() > 0 || mortarShots > 0 || shellBursts > 0) {
            System.out.println(String.format(Locale.ROOT, "[Night Siege sim] MOERSER;%s;%d;%d;%s;%d;%s;%d;%d;%d",
                    run.variant(), run.level(), run.group(), run.mix(), run.index(), demo, mortarShots, shellBursts, shellVictims));
        }
        if (index % 10 == 9) {
            long secs = (System.currentTimeMillis() - startedAt) / 1000;
            System.out.println(String.format(Locale.ROOT, "[Night Siege sim] Stand: %d von %d Läufen, %d s", index + 1, queue.size(), secs));
        }
        StatsRecorder.simLabel = "";
        if (m.isActive() || m.isPreparing()) {
            m.abort(server);
            m.cancelPreparation();
        }
        clearField(m);
        keepStanding(server);
        index++;
        phase = Phase.PAUSE;
        phaseTick = server.getTickCount();
    }

    /**
     * Between the nights the bots stay whole (31.10.): outside the night no drone catches a fatal blow
     * (a wounded survivor in a mended wall, a last arrow), and a bot that really dies has no client to
     * press "respawn". Only the bots; the night itself starts them at full health anyway.
     */
    private static void keepStanding(MinecraftServer server) {
        for (UUID id : brains.keySet()) {
            ServerPlayer bot = server.getPlayerList().getPlayer(id);
            if (bot != null && SimBots.isBot(bot) && bot.isAlive()) {
                bot.clearFire();
                if (bot.getHealth() < bot.getMaxHealth()) {
                    bot.setHealth(bot.getMaxHealth());
                }
            }
        }
    }

    /** One line of the trap service's shot log: a mortar shot, or a shell burst with the monsters it hit. */
    private static void countShot(String line) {
        if (line.startsWith("mortar fires")) {
            mortarShots++;
        } else if (line.startsWith("shell burst")) {
            shellBursts++;
            int at = line.lastIndexOf("victims=");
            if (at >= 0) {
                try {
                    shellVictims += Integer.parseInt(line.substring(at + 8).trim());
                } catch (NumberFormatException ignored) {
                    // a line of another shape: only counted
                }
            }
        }
    }

    private static void finish(MinecraftServer server, String problem) {
        SiegeManager m = SiegeManager.get();
        if (m != null) {
            if (m.isActive()) {
                m.abort(server);
            }
            if (m.isPreparing()) {
                m.cancelPreparation();
            }
            restoreConfig(m);
            clearField(m);
            for (ServerPlayer bot : SimBots.known()) {
                m.save().skills.remove(bot.getUUID().toString());
                m.save().unregister(bot.getUUID().toString());
            }
            m.persist();
        }
        SimBots.removeAll(server);
        if (TrapService.shotLog == SHOT_COUNTER) {
            TrapService.shotLog = null;
        }
        phase = Phase.DONE;
        StatsRecorder.simLabel = "";
        List<String> lines = report();
        if (problem != null) {
            lines.add(0, "[Night Siege sim] Team-Sim abgebrochen: " + problem);
        }
        for (String line : lines) {
            System.out.println(line);
        }
        writeFiles(lines);
        if (serverMode) {
            run(server, "tick sprint stop");
            server.halt(false);
        }
    }

    // ------------------------------------------------------------------ the posts

    /**
     * Where each bot stands: in a closed base at its gates (tanks in front, melee beside them,
     * healers right at the gate, archers on the wall, engineers and summoners beside), else in a
     * ring around the core.
     */
    private static void posts(SiegeManager m) {
        BaseScan scan = BaseDefense.scanFor(world, core, m.config());
        List<BaseScan.Gate> gates = scan != null && scan.closed ? new ArrayList<>(scan.gates) : new ArrayList<>();
        List<TeamBrain> order = new ArrayList<>(brains.values());
        order.sort(Comparator.comparingInt(b -> b.role.ordinal()));
        int fighters = 0;
        for (TeamBrain b : order) {
            if (b.role == TeamBrain.Role.TANK || b.role == TeamBrain.Role.MELEE || b.role == TeamBrain.Role.SUMMONER) {
                fighters++;
            }
        }
        int active = gates.isEmpty() ? 0 : Math.max(1, Math.min(gates.size(), Math.max(1, fighters)));
        int[] counter = new int[TeamBrain.Role.values().length];
        int ring = 0;
        for (TeamBrain b : order) {
            if (active == 0) {
                double angle = ring++ * 2.3999632;
                double r = switch (b.role) {
                    case TANK, MELEE -> 5.0;
                    case SUMMONER, ENGINEER -> 4.0;
                    default -> 3.0;
                };
                BlockPos at = core.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
                b.post = standable(world, at, 3);
                b.gate = -1;
                continue;
            }
            int n = counter[b.role.ordinal()]++;
            int gi = n % active;
            BaseScan.Gate g = gates.get(gi);
            b.gate = gi;
            Vec3 out = new Vec3(g.front().getX() - g.pos().getX(), 0, g.front().getZ() - g.pos().getZ());
            if (out.lengthSqr() < 0.01) {
                out = new Vec3(g.pos().getX() - core.getX(), 0, g.pos().getZ() - core.getZ());
            }
            out = out.normalize();
            Vec3 lat = new Vec3(-out.z, 0, out.x);
            int side = (n / active) % 2 == 0 ? 1 : -1;
            Vec3 f = Vec3.atBottomCenterOf(g.front());
            Vec3 want = switch (b.role) {
                case TANK -> f.add(out);
                case MELEE -> f.add(out).add(lat.scale(2 * side));
                case SUMMONER -> f.add(lat.scale(3 * side));
                case HEALER -> f;
                case ENGINEER -> f.add(lat.scale(2 * side));
                case RANGED -> null;
            };
            BlockPos spot = null;
            if (b.role == TeamBrain.Role.RANGED) {
                spot = b.onWall ? wallTop(g, lat, side, b.wallShift) : null;   // onWall is always true unless "mensch=1" (Tor 6.0)
                if (spot == null) {
                    want = f.add(lat.scale(4 * side));
                }
            }
            if (spot == null && want != null) {
                spot = standable(world, BlockPos.containing(want), 3);
            }
            b.post = spot != null ? spot : standable(world, g.front(), 4);
        }
        StringBuilder sb = new StringBuilder("[Night Siege sim] Posten (" + gates.size() + " Tore):");
        for (TeamBrain b : order) {
            sb.append(' ').append(b.describe());
        }
        NightSiegeMod.LOGGER.info(sb.toString());
    }

    /** A spot on top of the wall beside this gate, at least three blocks up. */
    private static BlockPos wallTop(BaseScan.Gate g, Vec3 lat, int side, int shift) {
        for (int k = 2 + shift; k <= 8 + shift; k++) {
            for (int s : new int[]{side, -side}) {
                int x = (int) Math.round(g.pos().getX() + lat.x * k * s);
                int z = (int) Math.round(g.pos().getZ() + lat.z * k * s);
                for (int dy = 14; dy >= 3; dy--) {
                    BlockPos p = new BlockPos(x, g.pos().getY() + dy, z);
                    if (TeamBrain.passable(world, p) && TeamBrain.passable(world, p.above())
                            && !world.getBlockState(p.below()).getCollisionShape(world, p.below()).isEmpty()) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    /** The nearest spot at or around {@code at} where a player can stand (feet and head free, ground below). */
    static BlockPos standable(ServerLevel level, BlockPos at, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
                        BlockPos p = at.offset(dx, dy, dz);
                        if (TeamBrain.passable(level, p) && TeamBrain.passable(level, p.above())
                                && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
                                && level.getBlockState(p.below()).getFluidState().isEmpty()
                                && !TeamBrain.dangerous(level, p)) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ housekeeping

    /** No monsters and no traps left from the last run, the base mended by the night itself. */
    private static void clearField(SiegeManager m) {
        if (world == null || core == null) {
            return;
        }
        AABB box = new AABB(core).inflate(128.0);
        for (Mob mob : world.getEntitiesOfClass(Mob.class, box, e -> e instanceof Enemy && !Companions.isCompanion(e))) {
            mob.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(core.offset(-48, -6, -48), core.offset(48, 14, 48))) {
            if (ModContent.TRAP_BLOCKS.containsValue(world.getBlockState(pos).getBlock())) {
                m.trapService().onBroken(world, pos.immutable());
                world.removeBlock(pos, false);
            }
        }
    }

    /**
     * The bots' skill (calibration knob): "koennen.<klasse>" before "koennen", the variant's before
     * the common one; 1 (the perfect bot) when nothing is set.
     */
    static double skillOf(String variant, String clazz) {
        return knob(variant, clazz, "koennen", CALIBRATED_SKILL);
    }

    /**
     * A bot knob ("koennen", "wagemut"): "<name>.<klasse>" before "<name>", the variant's before the
     * common one, else the given default; always 0–1.
     */
    static double knob(String variant, String clazz, String name, double fallback) {
        String own = name + "." + clazz.toLowerCase(Locale.ROOT);
        for (Map<String, String> over : List.of(variants.getOrDefault(variant, Map.of()), common)) {
            for (String key : List.of(own, name)) {
                String v = over.get(key);
                if (v != null) {
                    try {
                        return Math.max(0.0, Math.min(1.0, Double.parseDouble(v)));
                    } catch (NumberFormatException ignored) {
                        // fall through
                    }
                }
            }
        }
        return fallback;
    }

    /** A Gaussian from a seed; the seed is scrambled first (Random seeds one apart give nearly the same first values). */
    static double gaussOf(long seed) {
        Random r = new Random(Long.rotateLeft(seed * 0x9E3779B97F4A7C15L, 17) ^ 0x5EEDL);
        r.nextGaussian();
        return r.nextGaussian();
    }

    /** "mensch=1" (in a variant or for all): the human bots of Tor 6.0, see {@link BotHuman}. */
    static boolean isHuman(String variant) {
        for (Map<String, String> over : List.of(variants.getOrDefault(variant, Map.of()), common)) {
            String v = over.get("mensch");
            if (v != null) {
                try {
                    return Double.parseDouble(v) > 0;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return true;   // Tor 8.1: Mensch-Bots sind Standard, "mensch=0" schaltet sie ab
    }

    /** One human knob "mensch.<name>" (variant before common), not clamped; else the default. */
    static double humanKnob(String variant, String name, double fallback) {
        for (Map<String, String> over : List.of(variants.getOrDefault(variant, Map.of()), common)) {
            String v = over.get("mensch." + name);
            if (v != null) {
                try {
                    return Double.parseDouble(v);
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        return fallback;
    }

    private static void applyConfig(SiegeManager m, Map<String, String> over) {
        SiegeConfig cfg = m.config();
        for (Map.Entry<String, String> e : over.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key.startsWith("koennen") || key.startsWith("wagemut") || key.equals("mit") || key.equals("nur")
                    || key.startsWith("mensch") || key.startsWith("nachspiel") || key.equals("wahl") || key.equals("prefer") || key.startsWith("faehig")) {
                continue;   // the bots' knobs, not config values
            }
            try {
                if (key.startsWith("amt.")) {
                    // Tor 8.2 Zusatz: the amount of a skill node, e.g. amt.w_blessing=1.5 (the node's own number)
                    backup.putIfAbsent("skillTweaks", copyTweaks(cfg.skillTweaks));
                    cfg.skillTweaks.computeIfAbsent(key.substring(4), k -> new SiegeConfig.SkillTweak()).amount = Double.parseDouble(value);
                    m.skillTree().applyTweaks(cfg.skillTweaks);
                } else if (key.startsWith("sv.")) {
                    // A skill value like a server skill tweak: sv.d_soulcatcher.swarmcd=80 (30.10.)
                    String[] path = key.split("\\.", 3);
                    backup.putIfAbsent("skillTweaks", copyTweaks(cfg.skillTweaks));
                    SiegeConfig.SkillTweak t = cfg.skillTweaks.computeIfAbsent(path[1], k -> new SiegeConfig.SkillTweak());
                    if (t.values == null) {
                        t.values = new java.util.LinkedHashMap<>();
                    }
                    if (path[2].equals("amount")) {
                        // The node's value per level: sv.w_blessing.amount=1.0 (Tor 8)
                        t.amount = Double.parseDouble(value);
                        m.skillTree().applyTweaks(cfg.skillTweaks);
                    } else {
                        t.values.put(path[2], Double.parseDouble(value));
                    }
                    com.mki.nightsiege.core.SkillValues.apply(cfg.skillTweaks);
                } else if (key.startsWith("mob.")) {
                    // A field of a spawn-table entry: mob.minecraft:evoker.minLevel=11 (30.10.)
                    String rest = key.substring(4);
                    int dot = rest.lastIndexOf('.');
                    String id = rest.substring(0, dot);
                    String field = rest.substring(dot + 1);
                    boolean found = false;
                    for (com.mki.nightsiege.core.MobEntry entry : cfg.mobs) {
                        if (entry != null && id.equals(entry.id)) {
                            java.lang.reflect.Field f = entry.getClass().getField(field);
                            backup.putIfAbsent(key, f.get(entry));
                            if (f.getType() == int.class) {
                                f.setInt(entry, (int) Math.round(Double.parseDouble(value)));
                            } else {
                                f.setDouble(entry, Double.parseDouble(value));
                            }
                            found = true;
                        }
                    }
                    if (!found) {
                        NightSiegeMod.LOGGER.warn("[Night Siege sim] Monster {} nicht in der Liste", id);
                    }
                } else if (key.contains(".")) {
                    String[] path = key.split("\\.", 2);
                    java.lang.reflect.Field f = cfg.getClass().getField(path[0]);
                    @SuppressWarnings("unchecked")
                    Map<String, Double> map = (Map<String, Double>) f.get(cfg);
                    backup.putIfAbsent(key, map.containsKey(path[1]) ? map.get(path[1]) : "--");
                    map.put(path[1], Double.parseDouble(value));
                } else if (List.of("warrior", "archer", "guardian", "engineer", "summoner").contains(key)) {
                    backup.putIfAbsent("classDamage." + key, cfg.classDamage.containsKey(key) ? cfg.classDamage.get(key) : "--");
                    cfg.classDamage.put(key, Double.parseDouble(value));
                } else {
                    java.lang.reflect.Field f = cfg.getClass().getField(key);
                    backup.putIfAbsent(key, f.get(cfg));
                    if (f.getType() == double.class) {
                        f.setDouble(cfg, Double.parseDouble(value));
                    } else if (f.getType() == int.class) {
                        f.setInt(cfg, (int) Double.parseDouble(value));
                    } else if (f.getType() == boolean.class) {
                        f.setBoolean(cfg, Boolean.parseBoolean(value));
                    } else if (List.class.isAssignableFrom(f.getType())) {
                        // A list as numbers with "/" between them: liveCapByPlayers=8/12/20/30; or names (05.11.):
                        // antiHealMobs=minecraft:spider/minecraft:witch
                        boolean ints = f.getGenericType().getTypeName().contains("Integer");
                        boolean names = f.getGenericType().getTypeName().contains("String");
                        List<Object> list = new ArrayList<>();
                        for (String part : value.split("/")) {
                            if (!part.isBlank()) {
                                list.add(names ? (Object) part.trim()
                                        : ints ? (Object) (int) Math.round(Double.parseDouble(part)) : (Object) Double.parseDouble(part));
                            }
                        }
                        f.set(cfg, list);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                NightSiegeMod.LOGGER.warn("[Night Siege sim] Wert {} = {} nicht gesetzt: {}", key, value, ex.toString());
            }
        }
    }

    private static void restoreConfig(SiegeManager m) {
        SiegeConfig cfg = m.config();
        for (Map.Entry<String, Object> e : backup.entrySet()) {
            String key = e.getKey();
            try {
                if (key.startsWith("mob.")) {
                    String rest = key.substring(4);
                    int dot = rest.lastIndexOf('.');
                    String id = rest.substring(0, dot);
                    for (com.mki.nightsiege.core.MobEntry entry : cfg.mobs) {
                        if (entry != null && id.equals(entry.id)) {
                            entry.getClass().getField(rest.substring(dot + 1)).set(entry, e.getValue());
                        }
                    }
                } else if (key.contains(".")) {
                    String[] path = key.split("\\.", 2);
                    java.lang.reflect.Field f = cfg.getClass().getField(path[0]);
                    @SuppressWarnings("unchecked")
                    Map<String, Double> map = (Map<String, Double>) f.get(cfg);
                    if ("--".equals(e.getValue())) {
                        map.remove(path[1]);
                    } else {
                        map.put(path[1], (Double) e.getValue());
                    }
                } else {
                    cfg.getClass().getField(key).set(cfg, e.getValue());
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                NightSiegeMod.LOGGER.warn("[Night Siege sim] Wert {} nicht zurückgesetzt: {}", key, ex.toString());
            }
        }
        backup.clear();
        com.mki.nightsiege.core.SkillValues.apply(cfg.skillTweaks);   // the server's skill values again
        m.skillTree().applyTweaks(cfg.skillTweaks);
    }

    /** A copy of the skill tweaks, so a variant's sv.… values can be taken back. */
    private static Map<String, SiegeConfig.SkillTweak> copyTweaks(Map<String, SiegeConfig.SkillTweak> tweaks) {
        Map<String, SiegeConfig.SkillTweak> copy = new java.util.LinkedHashMap<>();
        if (tweaks != null) {
            for (Map.Entry<String, SiegeConfig.SkillTweak> e : tweaks.entrySet()) {
                SiegeConfig.SkillTweak t = e.getValue();
                if (t == null) {
                    continue;
                }
                SiegeConfig.SkillTweak c = new SiegeConfig.SkillTweak();
                c.amount = t.amount;
                c.cost = t.cost;
                c.costStep = t.costStep;
                c.maxLevel = t.maxLevel;
                c.values = t.values == null ? null : new java.util.LinkedHashMap<>(t.values);
                copy.put(e.getKey(), c);
            }
        }
        return copy;
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    // ------------------------------------------------------------------ the report

    /** Win share per variant, level and group size, then per subclass. */
    static List<String> report() {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "[Night Siege sim] Team-Sim: %d Läufe, Boden %s, %s, Ziel %.0f %% Siege",
                results.size(), groundKind, fullTree ? "voll geskillt" : "Punkte wie bis zur Stufe", TARGET * 100));
        // variant → level → group → [runs, wins, held]
        Map<String, Map<Integer, Map<Integer, int[]>>> cells = new LinkedHashMap<>();
        for (Result r : results) {
            int[] c = cells.computeIfAbsent(r.run().variant(), k -> new TreeMap<>())
                    .computeIfAbsent(r.run().level(), k -> new TreeMap<>())
                    .computeIfAbsent(r.run().group(), k -> new int[3]);
            c[0]++;
            c[1] += r.won() ? 1 : 0;
            c[2] += r.held() ? 1 : 0;
        }
        for (Map.Entry<String, Map<Integer, Map<Integer, int[]>>> v : cells.entrySet()) {
            StringBuilder head = new StringBuilder("[Night Siege sim] " + v.getKey() + " Stufe |");
            for (int g : groups) {
                head.append(String.format(Locale.ROOT, " %2d Sp. |", g));
            }
            out.add(head.toString());
            for (Map.Entry<Integer, Map<Integer, int[]>> l : v.getValue().entrySet()) {
                StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "[Night Siege sim] %s %5d |", v.getKey(), l.getKey()));
                for (int g : groups) {
                    int[] c = l.getValue().get(g);
                    row.append(c == null || c[0] == 0 ? "    -   |"
                            : String.format(Locale.ROOT, " %3.0f%%%s |", 100.0 * c[1] / c[0], mark(c[1] / (double) c[0])));
                }
                out.add(row.toString());
            }
            for (Map.Entry<Integer, Map<Integer, int[]>> l : v.getValue().entrySet()) {
                for (Map.Entry<Integer, int[]> g : l.getValue().entrySet()) {
                    int[] c = g.getValue();
                    out.add(String.format(Locale.ROOT, "[Night Siege sim] ZELLE;%s;%d;%d;%d;%d;%d", v.getKey(), l.getKey(), g.getKey(),
                            c[0], c[1], c[2]));
                }
            }
        }
        // Per subclass: in how many runs it played, how often its group won, damage and deaths per run.
        Map<String, double[]> subs = new TreeMap<>();
        for (Result r : results) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String[] s : r.run().subs()) {
                if (seen.add(s[2])) {
                    double[] v = subs.computeIfAbsent(r.run().variant() + ";" + s[2], k -> new double[6]);
                    v[0]++;
                    v[1] += r.won() ? 1 : 0;
                }
            }
            for (Map.Entry<String, double[]> e : r.bySub().entrySet()) {
                String sub = e.getKey().substring(e.getKey().indexOf(':') + 1);
                double[] v = subs.computeIfAbsent(r.run().variant() + ";" + sub, k -> new double[6]);
                v[2] += e.getValue()[0];
                v[3] += e.getValue()[1];
                v[4] += e.getValue()[2];
                v[5] += e.getValue()[4];
            }
        }
        out.add("[Night Siege sim] UNTERKLASSEN;variante;unterklasse;laeufe;siegquote;schaden_je_lauf;erhalten_je_lauf;heilung_je_lauf;tode_je_lauf");
        for (Map.Entry<String, double[]> e : subs.entrySet()) {
            double[] v = e.getValue();
            double n = Math.max(1, v[0]);
            out.add(String.format(Locale.ROOT, "[Night Siege sim] UK;%s;%.0f;%.2f;%.0f;%.0f;%.0f;%.2f", e.getKey(), v[0], v[1] / n,
                    v[2] / n, v[3] / n, v[4] / n, v[5] / n));
        }
        return out;
    }

    private static String mark(double share) {
        return share < 0.55 ? "!" : share > 0.85 ? "+" : " ";
    }

    private static void writeFiles(List<String> summary) {
        try {
            List<String> csv = new ArrayList<>();
            csv.add("art;variante;stufe;gruppe;mischung;lauf;ergebnis;gehalten;sieg;kills;ziel;sekunden;tode;unterklassen");
            for (Result r : results) {
                StringBuilder subs = new StringBuilder();
                for (String[] s : r.run().subs()) {
                    subs.append(subs.length() == 0 ? "" : "+").append(s[2]);
                }
                csv.add(String.format(Locale.ROOT, "LAUF;%s;%d;%d;%s;%d;%s;%d;%d;%d;%d;%d;%d;%s", r.run().variant(), r.run().level(),
                        r.run().group(), r.run().mix(), r.run().index(), r.outcome(), r.held() ? 1 : 0, r.won() ? 1 : 0,
                        r.kills(), r.goal(), r.seconds(), r.deaths(), subs));
            }
            csv.add("art;variante;stufe;gruppe;mischung;lauf;unterklasse;schaden;erhalten;heilung;kills;tode");
            for (Result r : results) {
                for (Map.Entry<String, double[]> e : r.bySub().entrySet()) {
                    double[] v = e.getValue();
                    String sub = e.getKey().substring(e.getKey().indexOf(':') + 1);
                    csv.add(String.format(Locale.ROOT, "BOT;%s;%d;%d;%s;%d;%s;%.0f;%.0f;%.0f;%.0f;%.0f", r.run().variant(),
                            r.run().level(), r.run().group(), r.run().mix(), r.run().index(), sub, v[0], v[1], v[2], v[3], v[4]));
                }
            }
            csv.add("art;variante;stufe;gruppe;mischung;lauf;ereignis;boss;koennen;sieg;mspt_p95;lebende_max;frueh;tode;sekunden;ergebnis");
            csv.add("art;variante;stufe;gruppe;mischung;lauf;unterklasse;wahl;knoten");
            csv.add("art;variante;stufe;gruppe;mischung;lauf;kampftick;lebende;mspt_p95_fenster;mspt_max_fenster");
            csv.addAll(extraCsv);
            Files.write(Json.configFile("nightsiege-teamsim.csv"), csv, StandardCharsets.UTF_8);
            List<String> txt = new ArrayList<>();
            for (String s : summary) {
                txt.add(s.replace("[Night Siege sim] ", ""));
            }
            Files.write(Json.configFile("nightsiege-teamsim.txt"), txt, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege sim] Team-Sim-Ergebnis nicht gespeichert: {}", e.toString());
        }
    }
}
