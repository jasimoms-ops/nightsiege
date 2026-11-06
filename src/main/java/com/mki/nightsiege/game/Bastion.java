package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.BastionMath;
import com.mki.nightsiege.core.BossKind;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The Letzte Bastion (Update 7, Simon 29.10. and 06.11.; concept in claude/night-siege-update-7-bastion.md):
 * a fun mode from world level 20 on (in a playtest any time). Endless waves round the core until every
 * defender has fallen, the only reward is fame: the record board at the core and the records tab.
 *
 * <ul>
 *   <li>Started from the core window: an invitation to every signed-up player, a ready round of those
 *   within {@code bastionLobbyRadius} of the core. Everyone gets a full lent profile of any subclass
 *   (the fun mode, {@link com.mki.nightsiege.core.FunProfile}), spends its points, presses "Bereit".</li>
 *   <li>It begins when all are ready and there are {@link BastionMath#needed} of them; after
 *   {@code bastionForceSeconds} the starter may go without those not ready.</li>
 *   <li>The clock stands at midnight during the round, the time of day comes back after it.</li>
 *   <li>The waves themselves run in the night of the round ({@link SiegeManager#startBastion}): wave n at
 *   level 20 + n of the full curve, its kills then a pause, a boss every 5th wave, machines from wave 10,
 *   fixed elites from wave 5, nemeses from wave 15. No curses, no smart night.</li>
 * </ul>
 */
public final class Bastion {

    enum Phase { NONE, LOBBY, ROUND }

    /** The round's waves: fighting, the last ones of a wave falling, the pause before the next. */
    public enum Step { FIGHT, CLEAR, PAUSE }

    /** The record board's stands (one per line) carry this tag. */
    static final String BOARD_TAG = "nightsiege_bastion_board";
    /** The lobby gives up after this long (ticks). */
    private static final long LOBBY_MAX = 20L * 300;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final SiegeManager m;
    private final Random random = new Random();
    private Phase phase = Phase.NONE;
    private StartRound round;
    private ServerLevel world;
    private BlockPos core;
    private UUID starter;
    private String starterName = "";
    /** Counts for the record board (world level 20 or more, no test). */
    private boolean counted;
    /** A run in a playtest: marked, on its own list. */
    private boolean playtestRun;
    /** The operator's test (/nightsiege bastion): it never counts. */
    private boolean test;
    private int startWave = 1;
    private long lobbySince;
    final List<UUID> roster = new ArrayList<>();
    final Map<UUID, String> names = new LinkedHashMap<>();
    final Map<UUID, String> subs = new LinkedHashMap<>();

    // ------------------------------------------------------------------ the round's waves (SiegeManager drives them)
    int wave;
    Step step = Step.FIGHT;
    int killsFrom;
    int quota;
    long stepSince;
    long pauseUntil;
    boolean bossWave;
    boolean bossSpawned;
    boolean bossDown;
    BossKind boss;
    boolean nemesisCalled;
    long roundStart;
    long waveStart;
    double dealtFrom;
    double takenFrom;
    int fallenFrom;
    int held;
    private final List<BossKind> bag = new ArrayList<>();
    private RunFile file;

    /** One wave of a run, for the statistics logger. */
    public static final class WaveLine {
        public int welle;
        public int stufe;
        public int ziel;
        public int kills;
        public int tode;
        public int sekunden;
        public double schaden;
        public double eingesteckt;
        public String boss = "";
        public int maschinen;
        public boolean gehalten;
    }

    /** The statistics logger's file of one run. */
    public static final class RunFile {
        public String art = "letzte_bastion";
        public String start = "";
        public String ende = "";
        public boolean gezaehlt;
        public boolean playtest;
        public boolean test;
        public int startWelle = 1;
        public int welle;
        public int gehalten;
        public int kills;
        public int sekunden;
        public String ergebnis = "";
        public List<String> team = new ArrayList<>();
        public List<String> unterklassen = new ArrayList<>();
        public List<String> gefallen = new ArrayList<>();
        public List<WaveLine> wellen = new ArrayList<>();
    }

    Bastion(SiegeManager manager) {
        this.m = manager;
    }

    // ------------------------------------------------------------------ state

    /** A ready round or a round runs. */
    public boolean busy() {
        return phase != Phase.NONE;
    }

    public boolean lobbyOpen() {
        return phase == Phase.LOBBY;
    }

    public boolean running() {
        return phase == Phase.ROUND;
    }

    public String starterName() {
        return phase == Phase.NONE ? "" : starterName;
    }

    /** For the class window: a member of the ready round. */
    public boolean inRound(ServerPlayer player) {
        return phase == Phase.LOBBY && round != null && player != null && round.has(player.getUUID());
    }

    public boolean roundStarter(ServerPlayer player) {
        return inRound(player) && round.isStarter(player.getUUID());
    }

    public int roundForceIn(long now) {
        return round == null ? 0 : round.forceIn(now);
    }

    public List<UUID> roundMembers() {
        return round == null ? List.of() : round.members();
    }

    public boolean roundReady(UUID id) {
        return round != null && round.isReady(id);
    }

    /** The wave of the running round (0 outside one). */
    public int wave() {
        return phase == Phase.ROUND ? wave : 0;
    }

    public Step step() {
        return step;
    }

    public int quota() {
        return quota;
    }

    public boolean counted() {
        return counted;
    }

    /** The level a wave fights at. */
    int levelFor(int w) {
        return BastionMath.levelForWave(w, m.config());
    }

    /** The next boss from the bag: every boss once, then the bag is filled anew. */
    BossKind drawBoss() {
        if (bag.isEmpty()) {
            Collections.addAll(bag, BossKind.values());
            Collections.shuffle(bag, random);
        }
        return bag.remove(0);
    }

    // ------------------------------------------------------------------ the lobby

    /** Why a round cannot begin now (a translation key), or null. */
    String blockReason(MinecraftServer server, boolean forTest) {
        SiegeConfig cfg = m.config();
        if (!cfg.bastionEnabled) {
            return "nightsiege.bastion.off";
        }
        if (m.mainCore() == null) {
            return "nightsiege.bastion.no_core";
        }
        if (phase != Phase.NONE) {
            return "nightsiege.bastion.running";
        }
        if (m.isActive() || m.isPreparing() || m.nightComing(server)) {
            return "nightsiege.bastion.night";
        }
        if (m.warLeader().busy() || m.camp().fighting() || !m.expeditions().fighterIds().isEmpty()
                || !m.camp().roundMembers().isEmpty() || !m.expeditions().roundMembers().isEmpty() || TeamSim.running()) {
            return "nightsiege.bastion.fight";
        }
        if (!forTest && !m.inPlaytest() && m.currentLevel() < cfg.bastionFromLevel) {
            return "nightsiege.bastion.level";
        }
        return null;
    }

    /** The core window's button. */
    public void requestStart(ServerPlayer player) {
        open(player, false, 1);
    }

    /** The operator's test (/nightsiege bastion [wave]): never counts. */
    public void testStart(ServerPlayer player, int fromWave) {
        open(player, true, Math.max(1, fromWave));
    }

    private void open(ServerPlayer player, boolean asTest, int fromWave) {
        if (player == null || player.level().getServer() == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        String block = blockReason(server, asTest);
        if (block != null) {
            tell(player, block, ChatFormatting.GRAY, m.config().bastionFromLevel, starterName);
            return;
        }
        if (!m.isRegistered(player)) {
            tell(player, "nightsiege.bastion.register", ChatFormatting.GRAY);
            return;
        }
        CoreRecord rec = m.mainCore();
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level == null) {
            tell(player, "nightsiege.bastion.no_core", ChatFormatting.GRAY);
            return;
        }
        BlockPos at = new BlockPos(rec.x, rec.y, rec.z);
        double r = m.config().bastionLobbyRadius;
        if (player.level() != level || player.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) > r * r) {
            tell(player, "nightsiege.bastion.too_far", ChatFormatting.GRAY, m.config().bastionLobbyRadius);
            return;
        }
        world = level;
        core = at;
        starter = player.getUUID();
        starterName = player.getScoreboardName();
        test = asTest;
        playtestRun = !asTest && m.inPlaytest();
        counted = !asTest && !playtestRun && m.currentLevel() >= m.config().bastionFromLevel;
        startWave = fromWave;
        long now = level.getGameTime();
        round = new StartRound(starter, core, now, m.config().bastionLobbyRadius, m.config().bastionForceSeconds, true);
        phase = Phase.LOBBY;
        lobbySince = now;
        roster.clear();
        names.clear();
        subs.clear();
        // Everyone signed up hears of it; who stands at the core is in.
        Component invite = Component.translatable("nightsiege.bastion.invite", player.getDisplayName(),
                m.config().bastionLobbyRadius).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (m.isRegistered(p)) {
                p.sendSystemMessage(invite);
            }
        }
        join(player);
        addNearby(server);
        NightSiegeMod.LOGGER.info("[Night Siege] Letzte Bastion: Bereit-Runde von {} ({}{}, ab Welle {}) mit {} Spielern",
                starterName, counted ? "zählt" : "zählt nicht", playtestRun ? ", Playtest" : test ? ", Test" : "", startWave, round.size());
    }

    /** A player into the ready round: the lent profile and the class window. */
    private void join(ServerPlayer p) {
        round.add(p.getUUID());
        m.lendFun(p);
        m.campClassPick(p, 0);
        tell(p, "nightsiege.bastion.lobby_help", ChatFormatting.YELLOW, needed(p.level().getServer()));
    }

    /** Signed-up players near the core join the ready round. */
    private void addNearby(MinecraftServer server) {
        if (round == null || world == null) {
            return;
        }
        for (ServerPlayer other : world.players()) {
            UUID id = other.getUUID();
            if (round.has(id) || !m.isRegistered(other) || other.isSpectator() || Drones.isDrone(other)
                    || !round.near(other, world)) {
                continue;
            }
            join(other);
            Component text = Component.translatable("nightsiege.bastion.joined", other.getDisplayName(), round.size(),
                    needed(server)).withStyle(ChatFormatting.YELLOW);
            for (UUID mid : round.members()) {
                ServerPlayer mp = server.getPlayerList().getPlayer(mid);
                if (mp != null && mp != other) {
                    mp.sendSystemMessage(text);
                }
            }
            m.refreshClassPicksNow(server);
        }
    }

    /** Ready players needed: bastionAccepts, or every signed-up player online when fewer are on. */
    int needed(MinecraftServer server) {
        int online = 0;
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (m.isRegistered(p) && !SimBots.isBot(p)) {
                    online++;
                }
            }
        }
        return BastionMath.needed(online, m.config());
    }

    private void tickLobby(MinecraftServer server, long now) {
        if (now % 5 != 0 || round == null) {
            return;
        }
        if (server.getPlayerList().getPlayer(starter) == null) {
            cancel(server, "nightsiege.bastion.lobby_gone");
            return;
        }
        if (m.isActive() || m.isPreparing() || m.nightComing(server) || m.camp().fighting()
                || !m.expeditions().fighterIds().isEmpty() || m.warLeader().busy()) {
            cancel(server, "nightsiege.bastion.night");
            return;
        }
        if (now - lobbySince > LOBBY_MAX) {
            cancel(server, "nightsiege.bastion.lobby_timeout");
            return;
        }
        addNearby(server);
        List<UUID> gone = round.dropGone(world);
        for (UUID id : gone) {
            m.endFun(server, id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                tell(p, "nightsiege.bastion.lobby_left", ChatFormatting.GRAY);
                m.closeClassPick(p);
            }
        }
        if (gone.contains(starter)) {
            cancel(server, "nightsiege.bastion.lobby_gone");
            return;
        }
        if (!gone.isEmpty()) {
            m.refreshClassPicksNow(server);
        }
        if (round.size() >= needed(server) && round.allReady()) {
            begin(server);
        }
    }

    /** "Bereit" in the fun mode's tree: ready in the round. */
    public void onRoundReady(ServerPlayer player) {
        if (phase != Phase.LOBBY || round == null || player == null || !round.markReady(player.getUUID())) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Component text = Component.translatable("nightsiege.bastion.lobby_ready", player.getDisplayName(),
                round.readyCount(), round.size(), needed(server)).withStyle(ChatFormatting.YELLOW);
        for (UUID id : round.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(text));
            }
        }
        m.refreshClassPicksNow(server);
    }

    /** The starter's button: those not ready are out (after the force time), then the round begins. */
    public void force(ServerPlayer player) {
        if (phase != Phase.LOBBY || round == null || player == null || !round.isStarter(player.getUUID())) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        long now = world.getGameTime();
        if (round.readyCount() == 0) {
            tell(player, "nightsiege.bastion.go_nobody", ChatFormatting.GRAY);
            return;
        }
        if (!round.allReady()) {
            if (round.forceIn(now) > 0) {
                tell(player, "nightsiege.bastion.go_wait", ChatFormatting.GRAY, round.forceIn(now));
                return;
            }
            for (UUID id : round.force(now)) {
                m.endFun(server, id);
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    tell(p, "nightsiege.bastion.lobby_dropped", ChatFormatting.GRAY);
                    m.closeClassPick(p);
                }
            }
        }
        if (!round.has(starter)) {
            cancel(server, "nightsiege.bastion.lobby_gone");
            return;
        }
        if (round.size() < needed(server)) {
            tell(player, "nightsiege.bastion.go_few", ChatFormatting.GRAY, round.size(), needed(server));
            m.refreshClassPicksNow(server);
            return;
        }
        if (round.allReady()) {
            begin(server);
        }
    }

    /** The starter (or a command) calls the ready round off. */
    public void cancel(MinecraftServer server, String key) {
        if (phase != Phase.LOBBY) {
            return;
        }
        if (round != null) {
            for (UUID id : round.members()) {
                m.endFun(server, id);
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    tell(p, key, ChatFormatting.GRAY);
                    m.closeClassPick(p);
                }
            }
        }
        round = null;
        phase = Phase.NONE;
        starter = null;
        roster.clear();
    }

    // ------------------------------------------------------------------ the round

    /** All ready: the clock to midnight, the night of the round. */
    private void begin(MinecraftServer server) {
        roster.clear();
        names.clear();
        subs.clear();
        for (UUID id : round == null ? List.<UUID>of() : round.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                roster.add(id);
                names.put(id, p.getScoreboardName());
                PlayerSkills lent = m.save().skillsOf(id.toString());
                subs.put(id, m.skillTree().subclassOfProfile(lent, lent.chosenClass));
                m.closeClassPick(p);
            }
        }
        round = null;
        if (roster.isEmpty()) {
            phase = Phase.NONE;
            return;
        }
        m.save().bastionTimeOfDay = m.clockTimeOf(server);
        m.persist();
        m.warNight(server);
        phase = Phase.ROUND;
        wave = 0;
        held = 0;
        step = Step.FIGHT;
        bossSpawned = false;
        bossDown = false;
        boss = null;
        nemesisCalled = false;
        bag.clear();
        file = new RunFile();
        file.start = LocalDateTime.now().format(STAMP);
        file.gezaehlt = counted;
        file.playtest = playtestRun;
        file.test = test;
        file.startWelle = startWave;
        if (!m.startBastion(server, world, core, startWave)) {
            phase = Phase.NONE;
            restoreTime(server);
            for (UUID id : roster) {
                m.endFun(server, id);
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    tell(p, "nightsiege.bastion.start_failed", ChatFormatting.RED);
                }
            }
            roster.clear();
            file = null;
            return;
        }
        roundStart = world.getGameTime();
        Component text = Component.translatable("nightsiege.bastion.begin", roster.size(), startWave)
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (m.isRegistered(p)) {
                p.sendSystemMessage(text);
            }
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Letzte Bastion beginnt: {} Verteidiger {}, ab Welle {} (Stufe {}), {}",
                roster.size(), names.values(), startWave, levelFor(startWave), counted ? "zählt" : playtestRun ? "Playtest" : "zählt nicht");
    }

    /** A wave begins (SiegeManager): what the logger keeps of the wave before. */
    void waveBegins(int w, int level, long now, double dealt, double taken, int fallen) {
        wave = w;
        waveStart = now;
        dealtFrom = dealt;
        takenFrom = taken;
        fallenFrom = fallen;
        if (file != null) {
            WaveLine line = new WaveLine();
            line.welle = w;
            line.stufe = level;
            line.ziel = quota;
            line.boss = bossWave ? "kommt" : "";
            line.maschinen = BastionMath.machines(w, m.config());
            file.wellen.add(line);
        }
    }

    /** The wave's kills are in (or the run ends in it): its line for the logger. */
    void waveEnds(boolean heldIt, int kills, long now, double dealt, double taken, int fallen) {
        if (heldIt) {
            held++;
        }
        if (file == null || file.wellen.isEmpty()) {
            return;
        }
        WaveLine line = file.wellen.get(file.wellen.size() - 1);
        if (line.welle != wave) {
            return;
        }
        line.kills = kills;
        line.gehalten = heldIt;
        line.sekunden = (int) Math.max(0, (now - waveStart) / 20);
        line.schaden = Math.round((dealt - dealtFrom) * 10) / 10.0;
        line.eingesteckt = Math.round((taken - takenFrom) * 10) / 10.0;
        line.tode = Math.max(0, fallen - fallenFrom);
        if (bossWave) {
            line.boss = boss == null ? "" : boss.id() + (bossDown ? "_besiegt" : "");
        }
    }

    /** The bar of the round. */
    Component barTitle(int kills, long now) {
        if (step == Step.PAUSE) {
            return Component.translatable("nightsiege.bastion.bar_pause", wave + 1, Math.max(0, (pauseUntil - now + 19) / 20))
                    .withStyle(ChatFormatting.GOLD);
        }
        if (step == Step.CLEAR) {
            return Component.translatable("nightsiege.bastion.bar_clear", wave);
        }
        MutableComponent title = Component.translatable("nightsiege.bastion.bar_wave", wave, Math.min(kills, quota), quota);
        if (bossWave && boss != null && !bossDown) {
            title = title.append(Component.literal("  ·  ")).append(Component.translatable(bossSpawned ? "nightsiege.bar.boss_here"
                    : "nightsiege.bar.boss_coming", Component.translatable("nightsiege.boss." + boss.id())).withStyle(ChatFormatting.RED));
        }
        return title;
    }

    /**
     * The run is over (SiegeManager's finish or abort): all fell ({@code fell} true) or it was broken off
     * (false/null). The record, the board, the logger, the time of day, the own profiles back.
     */
    void onEnd(MinecraftServer server, boolean fell, int kills) {
        if (phase != Phase.ROUND) {
            return;
        }
        long now = world == null ? 0L : world.getGameTime();
        int seconds = (int) Math.max(0, (now - roundStart) / 20);
        int reached = Math.max(startWave, wave);
        List<String> team = new ArrayList<>();
        List<String> subKeys = new ArrayList<>();
        for (UUID id : roster) {
            String sub = subs.getOrDefault(id, "");
            team.add(names.getOrDefault(id, "?"));
            subKeys.add(sub);
        }
        int place = 0;
        if (fell && (counted || playtestRun)) {
            BastionMath.Run run = new BastionMath.Run();
            run.welle = reached;
            run.gehalten = held;
            run.kills = kills;
            run.sekunden = seconds;
            run.namen.addAll(team);
            run.unterklassen.addAll(subKeys);
            for (int i = 0; i < team.size(); i++) {
                run.team.add(team.get(i) + (subKeys.get(i).isEmpty() ? "" : " (" + subKeys.get(i) + ")"));
            }
            run.datum = LocalDateTime.now().format(DATE);
            run.playtest = playtestRun;
            if (m.save().bastionRuns == null) {
                m.save().bastionRuns = new ArrayList<>();
            }
            place = BastionMath.add(m.save().bastionRuns, run);
            if (counted) {
                m.save().bastionRounds++;
            }
        }
        // Chat: the result for everyone signed up.
        Component text = !fell ? Component.translatable("nightsiege.bastion.end_aborted", reached).withStyle(ChatFormatting.GRAY)
                : Component.translatable("nightsiege.bastion.end", reached, held, kills, WarLeader.clock(seconds))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (m.isRegistered(p) || roster.contains(p.getUUID())) {
                p.sendSystemMessage(text);
                if (place > 0) {
                    tell(p, playtestRun ? "nightsiege.bastion.end_place_playtest" : "nightsiege.bastion.end_place",
                            ChatFormatting.GOLD, place);
                } else if (fell && !counted && !playtestRun) {
                    tell(p, "nightsiege.bastion.end_not_counted", ChatFormatting.GRAY, m.config().bastionFromLevel);
                }
            }
        }
        if (file != null) {
            file.ende = LocalDateTime.now().format(STAMP);
            file.welle = reached;
            file.gehalten = held;
            file.kills = kills;
            file.sekunden = seconds;
            file.ergebnis = fell ? "gefallen" : "abgebrochen";
            file.team.addAll(team);
            file.unterklassen.addAll(subKeys);
            for (UUID id : roster) {
                if (m.isFallen(id)) {
                    file.gefallen.add(names.getOrDefault(id, "?"));
                }
            }
            RunFile f = file;
            m.stats().bastionRun(f);
            System.out.println(String.format(Locale.ROOT, "[Night Siege] BASTION;%s;%d;%d;%d;%d;%d;%s",
                    f.ergebnis, roster.size(), reached, held, kills, seconds, String.join(",", subKeys)));
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Letzte Bastion vorbei: {} in Welle {} ({} gehalten), {} Kills, {} s, Platz {}",
                fell ? "gefallen" : "abgebrochen", reached, held, kills, seconds, place);
        m.persist();
        restoreTime(server);
        for (UUID id : roster) {
            m.endFun(server, id);
        }
        if (place > 0 && !playtestRun) {
            board(server);
        }
        phase = Phase.NONE;
        starter = null;
        roster.clear();
        file = null;
        wave = 0;
    }

    private void restoreTime(MinecraftServer server) {
        int t = m.save().bastionTimeOfDay;
        if (t < 0) {
            return;
        }
        m.setClockTime(server, t);
        m.save().bastionTimeOfDay = -1;
        m.persist();
    }

    void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (phase == Phase.NONE) {
            if (now % 20 == 0 && m.save().bastionTimeOfDay >= 0) {
                restoreTime(server);   // a round that ended without us (a crash): the time of day comes back
            }
            return;
        }
        if (phase == Phase.LOBBY) {
            tickLobby(server, world == null ? now : world.getGameTime());
            return;
        }
        if (!m.isBastionRound()) {
            onEnd(server, false, 0);   // the night went without us (never: finish and abort call onEnd first)
        }
    }

    /** The server stops: a ready round goes, the time of day comes back. */
    void onServerStopping(MinecraftServer server) {
        if (phase == Phase.LOBBY) {
            cancel(server, "nightsiege.bastion.lobby_gone");
        }
        restoreTime(server);
    }

    // ------------------------------------------------------------------ records

    /** The records page (★ Rekorde): the best runs, the playtest's below. */
    void recordLines(List<String> out) {
        List<BastionMath.Run> runs = m.save().bastionRuns;
        List<BastionMath.Run> real = BastionMath.best(runs, false, BastionMath.SHOWN);
        List<BastionMath.Run> pt = BastionMath.best(runs, true, 3);
        if (real.isEmpty() && pt.isEmpty()) {
            return;
        }
        out.add(line("nightsiege.records.bastion_title", m.save().bastionRounds));
        int i = 1;
        for (BastionMath.Run r : real) {
            out.add(line("nightsiege.records.bastion_run", i++, r.welle, String.join(", ", r.namen), r.kills, r.datum));
        }
        for (BastionMath.Run r : pt) {
            out.add(line("nightsiege.records.bastion_playtest", r.welle, String.join(", ", r.namen), r.datum));
        }
    }

    /** The record board at the core: one stand per line, the title and the 5 best runs (made anew each time). */
    public int board(MinecraftServer server) {
        CoreRecord rec = m.mainCore();
        ServerLevel level = rec == null || server == null ? null : SiegeManager.levelByName(server, rec.dimension);
        if (level == null) {
            return 0;
        }
        BlockPos at = new BlockPos(rec.x, rec.y, rec.z);
        AABB box = new AABB(at).inflate(4.0, 8.0, 4.0);
        for (Entity e : level.getEntitiesOfClass(ArmorStand.class, box, s -> s.entityTags().contains(BOARD_TAG))) {
            e.discard();
        }
        List<BastionMath.Run> real = BastionMath.best(m.save().bastionRuns, false, BastionMath.SHOWN);
        if (real.isEmpty()) {
            return 0;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("nightsiege.bastion.board_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        int i = 1;
        for (BastionMath.Run r : real) {
            lines.add(Component.translatable("nightsiege.bastion.board_line", i++, r.welle, String.join(", ", r.namen), r.datum)
                    .withStyle(i == 2 ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
        }
        double top = at.getY() + 2.2 + lines.size() * 0.28;
        for (int n = 0; n < lines.size(); n++) {
            ArmorStand stand = new ArmorStand(level, at.getX() + 0.5, top - n * 0.28, at.getZ() + 0.5);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCustomName(lines.get(n));
            stand.setCustomNameVisible(true);
            marker(stand);
            level.addFreshEntity(stand);
            stand.addTag(BOARD_TAG);
        }
        return lines.size();
    }

    /** A marker stand has no box to hit (the method is private in vanilla). */
    private static void marker(ArmorStand stand) {
        try {
            java.lang.reflect.Method mk = ArmorStand.class.getDeclaredMethod("setMarker", boolean.class);
            mk.setAccessible(true);
            mk.invoke(stand, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Without it the stand keeps its small box; it is invulnerable either way.
        }
    }

    // ------------------------------------------------------------------ helpers

    public String status() {
        return switch (phase) {
            case NONE -> "Letzte Bastion: keine";
            case LOBBY -> "Letzte Bastion: Bereit-Runde von " + starterName + ", " + (round == null ? 0 : round.readyCount()) + "/"
                    + (round == null ? 0 : round.size()) + " bereit";
            case ROUND -> "Letzte Bastion: Welle " + wave + " (" + step + "), " + roster.size() + " Verteidiger, Stufe " + levelFor(Math.max(1, wave));
        };
    }

    private static void tell(ServerPlayer p, String key, ChatFormatting color, Object... args) {
        if (p != null) {
            p.sendSystemMessage(Component.translatable(key, args).withStyle(color));
        }
    }

    /** "key\u0001arg\u0001arg": a line the client translates. */
    private static String line(String key, Object... args) {
        StringBuilder out = new StringBuilder(key);
        for (Object a : args) {
            out.append('\u0001').append(a);
        }
        return out.toString();
    }
}
