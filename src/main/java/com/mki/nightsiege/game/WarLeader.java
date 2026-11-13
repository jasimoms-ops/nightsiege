package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.BossKind;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import com.mki.nightsiege.core.SiegeMachineKind;
import com.mki.nightsiege.core.WarBotPlan;
import com.mki.nightsiege.core.WarLeaderData;
import com.mki.nightsiege.core.WarOrders;
import com.mki.nightsiege.core.WarShop;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The war leader's round (Kriegsführer, Simon 29 October 2026; concept in
 * claude/night-siege-kriegsfuehrer-konzept.md): one player leads the monsters from above, the
 * others defend the base as in a night of the world's level.
 *
 * <ul>
 *   <li>Started from the core window ("Kriegsführer-Runde"); whoever presses first leads. A ready
 *   round like the seal stone's: everyone signed up within {@code warLobbyRadius} of the core picks
 *   a class; when all are ready the round begins, after {@code warForceSeconds} the leader may go
 *   without those not ready.</li>
 *   <li>The round is a test night of its own kind ({@link SiegeManager#startWar}): no waves of its
 *   own, no curses, boss, warden or machines; it leaves no trace, no reward.</li>
 *   <li>The clock stands at midnight during the round; the time of day comes back after it.</li>
 *   <li>The leader is a spectator with night vision; his screen is the bird's-eye view. His body
 *   follows the camera high above, so the ground under it is sent to him.</li>
 *   <li>{@code warPrepSeconds} of build-up, then {@code warWaves} waves of {@code warWaveSeconds}.
 *   Gold at the start and at every wave, half of what is left comes over ({@link WarShop}).</li>
 *   <li>A squad bought at a gathering point comes {@code warSquadDelaySeconds} after a red flare
 *   (squads bought in the build-up come with the first wave). It gathers there and only fights who
 *   comes out to it; orders: target (a gate or a spot of the wall: the raid's plan takes it there),
 *   gather (back to the point), storm (every gathering squad at once). Without an order a squad
 *   storms after {@code warAutoStormSeconds}.</li>
 *   <li>The defenders win when the last wave runs out with one of them standing, the leader when
 *   all have fallen. Giving up hands the round to the defenders.</li>
 * </ul>
 */
public final class WarLeader {

    enum Phase { NONE, LOBBY, ROUND }

    /** A squad within this many blocks of its gathering point counts as there. */
    private static final double GATHER_NEAR = 5.0;
    /** A gathering squad fights only who comes this close to its point. */
    private static final double GATHER_LEASH = 14.0;
    /** The lobby gives up after this long (ticks). */
    private static final long LOBBY_MAX = 20L * 300;
    /** A leader away for this long gives the round up (ticks). */
    private static final long AWAY_MAX = 20L * 30;
    /** Where the leader's body hangs above the core (blocks). */
    private static final int BODY_HEIGHT = 140;
    /** The clock's time during the round: midnight. */
    private static final int WAR_TIME = 18000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /** A squad, a machine or the boss the leader bought. */
    static final class Squad {
        final int id;
        final String item;
        final int point;
        final boolean ownWill;
        final List<String> mobs;
        final Set<UUID> members = new LinkedHashSet<>();
        final long boughtAt;
        int total;
        /** "coming", "gather", "target", "storm", "free". */
        String order = "coming";
        long spawnAt;
        long gatherSince;
        double tx;
        double tz;
        boolean spawned;
        long nextNav;
        /** Kriegsführer 3 (K1): the leader's orders in a row; with any, no will of its own (no auto-storm). */
        final WarOrders.Queue queue = new WarOrders.Queue();
        WarOrders.Stance stance = WarOrders.Stance.AGGRESSIVE;
        boolean commanded;
        /** Where it stands between and after orders. */
        double sx;
        double sz;
        /** When the boss's ability was called last (-1: never). */
        long specialUsed = -1L;
        /** The bot leader sent it into an assault (Tor 10). */
        boolean botSent;

        Squad(int id, String item, int point, boolean ownWill, List<String> mobs, long boughtAt) {
            this.id = id;
            this.item = item;
            this.point = point;
            this.ownWill = ownWill;
            this.mobs = mobs;
            this.boughtAt = boughtAt;
        }

        boolean isMachine() {
            return item.startsWith("machine.");
        }

        boolean isBoss() {
            return item.startsWith("boss.");
        }
    }

    /** A red flare over a gathering point: up, a burst, then a glow until the squad is there. */
    private record Flare(BlockPos at, long start, long until) {
    }

    /** One line of the round's file for the statistics logger. */
    public static final class Entry {
        public int sekunde;
        public int welle;
        public String was = "";
        public String ding = "";
        public int punkt = -1;
        public int gold;
    }

    /** The round's file (stats folder, kriegsfuehrer_*.json). */
    public static final class RoundFile {
        public String art = "kriegsfuehrer";
        public String start = "";
        public String ende = "";
        public String kriegsfuehrer = "";
        public boolean bot;
        public boolean sandbox;
        public int stufe;
        public List<String> verteidiger = new ArrayList<>();
        public List<String> gefallen = new ArrayList<>();
        public int rundenGold;
        public List<Integer> goldJeWelle = new ArrayList<>();
        public int ausgegeben;
        public int kaeufe;
        public int befehle;
        public int welle;
        public int sekunden;
        public String ergebnis = "";
        public List<Entry> verlauf = new ArrayList<>();
    }

    private final SiegeManager m;
    private final Random random = new Random();
    private Phase phase = Phase.NONE;

    private UUID leader;
    private String leaderName = "";
    private boolean bot;
    /**
     * The sandbox (Simon 05.11.: "sandbox mäßig alleine probieren", "keiner verteidigt, ich kann Sachen
     * steuern und commanden"): no defenders, endless gold, everything open from the start, waves without
     * end until the leader stops it, no records.
     */
    private boolean sandbox;
    /** Sandbox: a short build-up and waves without end. */
    static final int SANDBOX_PREP = 15;
    static final int SANDBOX_WAVES = 99;
    static final int SANDBOX_MACHINES = 6;
    private StartRound round;
    private long lobbySince;

    private ServerLevel world;
    private BlockPos core;
    private BaseScan base;
    private int level;
    private int radius;
    private int waves;
    private int wave;
    private int roundGold;
    private int gold;
    private final List<BlockPos> points = new ArrayList<>();
    private final List<Squad> squads = new ArrayList<>();
    private final Map<UUID, Squad> memberOf = new HashMap<>();
    private int nextSquad = 1;
    private final Set<UUID> roster = new LinkedHashSet<>();
    private final Map<UUID, String> rosterNames = new HashMap<>();
    private final Set<String> usedCards = new HashSet<>();
    private WarShop.Card card;
    private long cardEnds;
    private final List<BossKind> bossOffers = new ArrayList<>();
    private boolean bossBought;
    private final List<Flare> flares = new ArrayList<>();
    private double camX;
    private double camZ;
    private double bodyX;
    private double bodyZ;
    private long awaySince = -1L;
    private long roundStart;
    private long botNext;
    private int botWave = -1;
    private RoundFile file;

    WarLeader(SiegeManager m) {
        this.m = m;
    }

    // ------------------------------------------------------------------ queries

    /** A ready round or a round runs (the camp, the dungeons and the playtest wait). */
    public boolean busy() {
        return phase != Phase.NONE;
    }

    public boolean lobbyOpen() {
        return phase == Phase.LOBBY;
    }

    public boolean running() {
        return phase == Phase.ROUND;
    }

    /** The round is the leader's sandbox (nobody defends). */
    public boolean sandbox() {
        return sandbox && phase != Phase.NONE;
    }

    private int prepSeconds(SiegeConfig cfg) {
        return sandbox ? SANDBOX_PREP : cfg.warPrepSeconds;
    }

    private int waveCount(SiegeConfig cfg) {
        return sandbox ? SANDBOX_WAVES : cfg.warWaves;
    }

    /** The leader of a ready round or round ("" when none runs). */
    public String leaderName() {
        return phase == Phase.NONE ? "" : leaderName;
    }

    public boolean isLeader(ServerPlayer player) {
        return player != null && leader != null && phase != Phase.NONE && leader.equals(player.getUUID());
    }

    /** The defenders of the round: its roster, online and in the round's world. */
    List<ServerPlayer> rosterOnline(MinecraftServer server, ServerLevel targetWorld) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : roster) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.level() == targetWorld) {
                out.add(p);
            }
        }
        return out;
    }

    /** For the class window: a member of the ready round. */
    public boolean inRound(ServerPlayer player) {
        return phase == Phase.LOBBY && round != null && player != null && round.has(player.getUUID());
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

    /** The whole round in ticks: the build-up and the waves. */
    int totalTicks(SiegeConfig cfg) {
        return (prepSeconds(cfg) + waveCount(cfg) * cfg.warWaveSeconds) * 20;
    }

    /** A gathering squad's monsters fight only who comes out to their point. */
    public boolean mayTarget(Mob mob, LivingEntity target) {
        if (phase != Phase.ROUND || memberOf.isEmpty() || mob == null || target == null) {
            return true;
        }
        Squad s = memberOf.get(mob.getUUID());
        if (s != null && s.commanded) {
            return engage(s, mob, target);
        }
        if (s == null || !"gather".equals(s.order) || s.point < 0 || s.point >= points.size()) {
            return true;
        }
        BlockPos p = points.get(s.point);
        double dx = target.getX() - (p.getX() + 0.5);
        double dz = target.getZ() - (p.getZ() + 0.5);
        return dx * dx + dz * dz <= GATHER_LEASH * GATHER_LEASH;
    }

    /** Kriegsführer 3 (K1): the monster's squad has the leader's orders. */
    public boolean commanded(Mob mob) {
        Squad s = mob == null || phase != Phase.ROUND ? null : memberOf.get(mob.getUUID());
        return s != null && s.commanded;
    }

    /** The boss bar of the round for the defenders. */
    Component barTitle() {
        SiegeConfig cfg = m.config();
        int elapsed = m.warElapsedTicks();
        int prep = prepSeconds(cfg) * 20;
        Component title;
        if (elapsed < prep) {
            title = Component.translatable("nightsiege.war.bar_prep", leaderName, clock((prep - elapsed) / 20));
        } else if (sandbox) {
            title = Component.translatable("nightsiege.war.bar_sandbox", leaderName, currentWave(cfg, elapsed));
        } else {
            int w = currentWave(cfg, elapsed);
            int left = Math.max(0, waveTicksLeft(cfg, elapsed)) / 20;
            title = Component.translatable("nightsiege.war.bar_wave", leaderName, w, cfg.warWaves, clock(left));
        }
        if (card != null) {
            return Component.empty().append(title).append(Component.translatable("nightsiege.war.bar_card",
                    Component.translatable("nightsiege.war.card." + card.id())).withStyle(ChatFormatting.RED));
        }
        return title;
    }

    private int currentWave(SiegeConfig cfg, int elapsed) {
        int prep = prepSeconds(cfg) * 20;
        if (elapsed < prep) {
            return 0;
        }
        return Math.min(waveCount(cfg), 1 + (elapsed - prep) / Math.max(1, cfg.warWaveSeconds * 20));
    }

    private int waveTicksLeft(SiegeConfig cfg, int elapsed) {
        int prep = prepSeconds(cfg) * 20;
        if (elapsed < prep) {
            return prep - elapsed;
        }
        int len = Math.max(1, cfg.warWaveSeconds * 20);
        int w = currentWave(cfg, elapsed);
        return prep + w * len - elapsed;
    }

    static String clock(int seconds) {
        seconds = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    // ------------------------------------------------------------------ the lobby

    /** The core window's button: a ready round begins with this player as the leader. */
    public void requestStart(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        SiegeConfig cfg = m.config();
        if (!cfg.warLeaderEnabled) {
            tell(player, "nightsiege.war.off", ChatFormatting.GRAY);
            return;
        }
        if (phase != Phase.NONE) {
            if (isLeader(player)) {
                sendData(player, true);
            } else {
                tell(player, "nightsiege.war.taken", ChatFormatting.GRAY, leaderName);
            }
            return;
        }
        String block = blockReason(server);
        if (block != null) {
            tell(player, block, ChatFormatting.GRAY);
            return;
        }
        CoreRecord rec = m.mainCore();
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level == null) {
            tell(player, "nightsiege.war.no_core", ChatFormatting.GRAY);
            return;
        }
        world = level;
        core = new BlockPos(rec.x, rec.y, rec.z);
        leader = player.getUUID();
        leaderName = player.getScoreboardName();
        bot = false;
        openLobby(server, level.getGameTime());
        NightSiegeMod.LOGGER.info("[Night Siege] Kriegsführer-Runde: Bereit-Runde von {} mit {} Spielern",
                leaderName, round.size());
    }

    /**
     * The sandbox (Simon 05.11.): the player alone as the leader, nobody defends, no ready round. Starts
     * at once with the world's level (or the one given, 1-20).
     */
    public void requestSandbox(ServerPlayer player, int wantedLevel) {
        if (player == null || player.level().getServer() == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (!m.config().warLeaderEnabled) {
            tell(player, "nightsiege.war.off", ChatFormatting.GRAY);
            return;
        }
        if (phase != Phase.NONE) {
            if (isLeader(player)) {
                sendData(player, true);
            } else {
                tell(player, "nightsiege.war.taken", ChatFormatting.GRAY, leaderName);
            }
            return;
        }
        String block = blockReason(server);
        if (block != null) {
            tell(player, block, ChatFormatting.GRAY);
            return;
        }
        CoreRecord rec = m.mainCore();
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level == null) {
            tell(player, "nightsiege.war.no_core", ChatFormatting.GRAY);
            return;
        }
        world = level;
        core = new BlockPos(rec.x, rec.y, rec.z);
        leader = player.getUUID();
        leaderName = player.getScoreboardName();
        bot = false;
        sandbox = true;
        forcedLevel = wantedLevel > 0 ? Math.min(Math.max(25, m.config().warLevel), wantedLevel) : 0;
        round = null;
        roster.clear();
        rosterNames.clear();
        NightSiegeMod.LOGGER.info("[Night Siege] Kriegsführer-Sandbox von {}", leaderName);
        begin(server);
        if (phase != Phase.ROUND) {
            sandbox = false;
        }
        forcedLevel = 0;
    }

    /** Why a round cannot begin now (a translation key), or null. */
    String blockReason(MinecraftServer server) {
        if (m.mainCore() == null) {
            return "nightsiege.war.no_core";
        }
        if (m.isActive() || m.isPreparing() || m.nightComing(server)) {
            return "nightsiege.war.night";
        }
        if (m.camp().fighting() || !m.expeditions().fighterIds().isEmpty()
                || !m.camp().roundMembers().isEmpty() || !m.expeditions().roundMembers().isEmpty()) {
            return "nightsiege.war.fight";
        }
        if (TeamSim.running() && !bot) {
            return "nightsiege.war.fight";
        }
        if (m.bastion().busy()) {
            return "nightsiege.war.fight";   // the Letzte Bastion's ready round or round (Update 7)
        }
        return null;
    }

    private void openLobby(MinecraftServer server, long now) {
        round = new StartRound(leader, core, now, m.config().warLobbyRadius, m.config().warForceSeconds, false);
        phase = Phase.LOBBY;
        lobbySince = now;
        roster.clear();
        rosterNames.clear();
        addNearby(server, true);
        Component text = Component.translatable("nightsiege.war.lobby_open", leaderName, m.config().warLobbyRadius)
                .withStyle(ChatFormatting.GOLD);
        for (UUID id : round.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(text);
            }
        }
        ServerPlayer lead = leaderPlayer(server);
        if (lead != null) {
            tell(lead, round.size() == 0 ? "nightsiege.war.lobby_empty" : "nightsiege.war.lobby_lead", ChatFormatting.GOLD,
                    m.config().warLobbyRadius);
            sendData(lead, true);
        }
    }

    /** Signed-up players with a class near the core join the ready round (and get the class window). */
    private void addNearby(MinecraftServer server, boolean first) {
        if (round == null || world == null) {
            return;
        }
        for (ServerPlayer other : world.players()) {
            UUID id = other.getUUID();
            boolean lent = m.config().funModeLoans;   // the fun mode (Update 7): every defender a full lent profile
            if (id.equals(leader) || round.has(id) || !m.isRegistered(other) || other.isSpectator() || Drones.isDrone(other)
                    || (!bot && other.getAbilities().instabuild) || (!lent && !m.save().skillsOf(id.toString()).hasClass())
                    || !round.near(other, world)) {
                continue;
            }
            round.add(id);
            if (lent) {
                m.lendFun(other);
            }
            if (!first) {
                other.sendSystemMessage(Component.translatable("nightsiege.war.lobby_open", leaderName, m.config().warLobbyRadius)
                        .withStyle(ChatFormatting.GOLD));
            }
            m.campClassPick(other, 0);
        }
    }

    private void tickLobby(MinecraftServer server, long now) {
        if (now % 5 != 0) {
            return;
        }
        ServerPlayer lead = leaderPlayer(server);
        if (!bot && lead == null) {
            cancelLobby(server, "nightsiege.war.lobby_gone");
            return;
        }
        if (m.isActive() || m.isPreparing() || m.nightComing(server) || m.camp().fighting()
                || !m.expeditions().fighterIds().isEmpty()) {
            cancelLobby(server, "nightsiege.war.night");
            return;
        }
        if (now - lobbySince > LOBBY_MAX) {
            cancelLobby(server, "nightsiege.war.lobby_timeout");
            return;
        }
        addNearby(server, false);
        List<UUID> gone = round.dropGone(world);
        for (UUID id : gone) {
            m.endFun(server, id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                tell(p, "nightsiege.war.lobby_left", ChatFormatting.GRAY);
                m.closeClassPick(p);
            }
        }
        if (!gone.isEmpty()) {
            m.refreshClassPicksNow(server);
        }
        if (round.size() > 0 && round.allReady()) {
            begin(server);
            return;
        }
        if (bot && round.size() > 0 && round.readyCount() > 0 && round.forceIn(now) == 0) {
            go(server);
            return;
        }
        if (lead != null && now % 20 == 0) {
            sendData(lead, false);
        }
    }

    /** A class chosen in the window: ready in the round. */
    public void onRoundReady(ServerPlayer player) {
        if (phase != Phase.LOBBY || round == null || player == null || !round.markReady(player.getUUID())) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Component text = Component.translatable("nightsiege.war.lobby_ready", player.getDisplayName(),
                round.readyCount(), round.size()).withStyle(ChatFormatting.YELLOW);
        for (UUID id : round.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(text));
            }
        }
        ServerPlayer lead = leaderPlayer(server);
        if (lead != null) {
            sendData(lead, false);
        }
    }

    /** The leader's "Los": those not ready are out (after the force time), then the round begins. */
    private void go(MinecraftServer server) {
        if (phase != Phase.LOBBY || round == null) {
            return;
        }
        long now = world.getGameTime();
        ServerPlayer lead = leaderPlayer(server);
        if (round.size() == 0 || round.readyCount() == 0) {
            if (lead != null) {
                tell(lead, "nightsiege.war.go_nobody", ChatFormatting.GRAY);
            }
            return;
        }
        if (!round.allReady()) {
            if (round.forceIn(now) > 0) {
                if (lead != null) {
                    tell(lead, "nightsiege.war.go_wait", ChatFormatting.GRAY, round.forceIn(now));
                }
                return;
            }
            for (UUID id : round.force(now)) {
                m.endFun(server, id);
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    tell(p, "nightsiege.war.lobby_dropped", ChatFormatting.GRAY);
                    m.closeClassPick(p);
                }
            }
        }
        if (round.size() > 0 && round.allReady()) {
            begin(server);
        }
    }

    private void cancelLobby(MinecraftServer server, String key) {
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
        ServerPlayer lead = leaderPlayer(server);
        if (lead != null) {
            tell(lead, key, ChatFormatting.GRAY);
            close(lead);
        }
        round = null;
        phase = Phase.NONE;
        leader = null;
        roster.clear();
    }

    // ------------------------------------------------------------------ the round begins

    /** All ready: the clock to midnight, the leader up into the air, the night of the round. */
    private void begin(MinecraftServer server) {
        SiegeConfig cfg = m.config();
        roster.clear();
        rosterNames.clear();
        for (UUID id : round == null ? List.<UUID>of() : round.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                roster.add(id);
                rosterNames.put(id, p.getScoreboardName());
                m.closeClassPick(p);
            }
        }
        round = null;
        ServerPlayer lead = leaderPlayer(server);
        if ((roster.isEmpty() && !sandbox) || (!bot && lead == null)) {
            cancelLobby(server, "nightsiege.war.go_nobody");
            return;
        }
        // Simon 06.11.: "immer Stufe 20" - the defenders play full lent profiles (the fun mode, Update 7); the round fights at warLevel.
        level = FreeClassPick.roundLevel(cfg, forcedLevel, m.currentLevel());   // K3: warLevel 25, not the world's level
        // The leader's place and the time of day, kept for a crash.
        SiegeSave.WarRestore keep = new SiegeSave.WarRestore();
        if (lead != null) {
            keep.id = lead.getUUID().toString();
            keep.name = lead.getScoreboardName();
            keep.dimension = SiegeManager.dimensionName(lead.level());
            keep.x = lead.getX();
            keep.y = lead.getY();
            keep.z = lead.getZ();
            keep.yaw = lead.getYRot();
            keep.pitch = lead.getXRot();
            keep.gameMode = lead.gameMode().getName();
        }
        keep.timeOfDay = m.clockTimeOf(server);
        m.save().warLeader = keep;
        m.persist();
        m.warNight(server);
        // Fresh state of the round.
        squads.clear();
        memberOf.clear();
        flares.clear();
        usedCards.clear();
        card = null;
        bossBought = false;
        nextSquad = 1;
        wave = 0;
        waves = waveCount(cfg);
        phase = Phase.ROUND;
        if (!m.startWar(server, world, core, level)) {
            phase = Phase.NONE;
            for (UUID id : roster) {
                m.endFun(server, id);
            }
            restoreTime(server);
            restoreLeader(server);
            if (lead != null) {
                tell(lead, "nightsiege.war.start_failed", ChatFormatting.RED);
                close(lead);
            }
            leader = null;
            roster.clear();
            return;
        }
        roundStart = world.getGameTime();
        base = m.nightBase();
        placePoints();
        double reach = base != null ? base.reach() : 24;
        radius = (int) Math.round(Math.max(cfg.warRangeMin, reach + cfg.warRangeExtra));
        roundGold = WarShop.roundGold(level, roster.size(), cfg, cfg.perPlayer(m.inPlaytest(), cfg.playerScaling), m.maxLive());
        gold = WarShop.waveGold(roundGold, 1, waves);
        bossOffers.clear();
        if (sandbox) {
            bossOffers.addAll(List.of(BossKind.values()));   // the sandbox: every boss
        } else {
            bossOffers.addAll(WarShop.bossOffers(random));
        }
        camX = core.getX() + 0.5;
        camZ = core.getZ() + 0.5;
        file = new RoundFile();
        file.start = LocalDateTime.now().format(DATE);
        file.kriegsfuehrer = bot ? "Bot" : leaderName;
        file.bot = bot;
        file.sandbox = sandbox;
        file.stufe = level;
        file.verteidiger.addAll(rosterNames.values());
        file.rundenGold = roundGold;
        file.goldJeWelle.add(gold);
        if (lead != null) {
            lead.closeContainer();
            lead.setGameMode(GameType.SPECTATOR);
            lead.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 60, 0, false, false));
            moveBody(lead, true);
            awaySince = -1L;
        }
        botNext = world.getGameTime() + 40;
        botWave = -1;
        Component text = sandbox ? Component.translatable("nightsiege.war.begin_sandbox", leaderName, SANDBOX_PREP)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                : Component.translatable("nightsiege.war.begin", bot ? "Bot" : leaderName, cfg.warPrepSeconds,
                waves, cfg.warWaveSeconds / 60).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer p : everyone(server)) {
            p.sendSystemMessage(text);
        }
        if (lead != null) {
            tell(lead, sandbox ? "nightsiege.war.lead_help_sandbox" : "nightsiege.war.lead_help", ChatFormatting.YELLOW);
            sendData(lead, true);
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Kriegsführer-Runde beginnt: {} gegen {} Verteidiger, Stufe {}, Gold {} (Welle 1: {}), {} Sammelplätze, Sicht {}",
                bot ? "Bot" : leaderName, roster.size(), level, roundGold, gold, points.size(), radius);
    }

    /** About eight gathering points round the base, before the wall, never inside it. */
    private void placePoints() {
        SiegeConfig cfg = m.config();
        points.clear();
        int n = cfg.warGatherPoints;
        double cx = core.getX() + 0.5;
        double cz = core.getZ() + 0.5;
        double offset = random.nextDouble() * Math.PI * 2.0 / n;
        for (int i = 0; i < n; i++) {
            double a = offset + i * Math.PI * 2.0 / n;
            BlockPos found = null;
            for (int t = 0; t < 12 && found == null; t++) {
                double aa = a + (t == 0 ? 0 : (random.nextDouble() - 0.5) * (Math.PI * 2.0 / n) * 0.8);
                double dx = Math.cos(aa);
                double dz = Math.sin(aa);
                double edge = base != null ? base.edgeTowards(cx + dx * 30, cz + dz * 30) : 16;
                double far = edge + (t >= 8 ? cfg.warGatherMin
                        : cfg.warGatherMin + random.nextDouble() * (cfg.warGatherMax - cfg.warGatherMin));
                BlockPos g = ground(cx + dx * far, cz + dz * far);
                if (g != null && (base == null || !base.near(g.getX(), g.getZ(), 6.0))) {
                    found = g;
                }
            }
            if (found != null) {
                points.add(found);
            }
        }
        if (points.isEmpty()) {
            // No open ground at all (water all round): the old ring.
            BlockPos spot = MobFactory.findSpawnSpot(world, core, cfg, random, 0.0);
            if (spot != null) {
                points.add(spot);
            }
        }
    }

    /** Dry ground at a column (the chunk is loaded for it). */
    private BlockPos ground(double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        try {
            world.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(bx >> 4, bz >> 4), 2);
            world.getChunk(bx >> 4, bz >> 4);
        } catch (RuntimeException e) {
            return null;
        }
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        BlockPos at = new BlockPos(bx, y, bz);
        if (!world.getFluidState(at.below()).isEmpty() || !world.getFluidState(at).isEmpty()) {
            return null;
        }
        return free(at) ? at : null;
    }

    private boolean free(BlockPos spot) {
        BlockState feet = world.getBlockState(spot);
        BlockState head = world.getBlockState(spot.above());
        return feet.getCollisionShape(world, spot).isEmpty() && head.getCollisionShape(world, spot.above()).isEmpty()
                && feet.getFluidState().isEmpty() && !world.getBlockState(spot.below()).getCollisionShape(world, spot.below()).isEmpty();
    }

    // ------------------------------------------------------------------ every tick

    void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (phase == Phase.NONE) {
            if (now % 20 == 0 && m.save().warLeader != null) {
                restoreTime(server);   // a round that ended without us (a crash): the time of day comes back
                restoreLeader(server);
            }
            return;
        }
        if (phase == Phase.LOBBY) {
            tickLobby(server, world == null ? now : world.getGameTime());
            return;
        }
        if (!m.isWarRound() || world == null) {
            onEnd(server, null);   // the night went without us (never: finish and abort call onEnd first)
            return;
        }
        long t = world.getGameTime();
        SiegeConfig cfg = m.config();
        int elapsed = m.warElapsedTicks();
        int w = currentWave(cfg, elapsed);
        if (w > wave) {
            newWave(server, w);
        }
        ServerPlayer lead = leaderPlayer(server);
        if (!bot) {
            if (lead == null) {
                if (awaySince < 0) {
                    awaySince = t;
                } else if (t - awaySince > AWAY_MAX) {
                    giveUp(server, "nightsiege.war.leader_away");
                    return;
                }
            } else {
                awaySince = -1L;
                if (!lead.isSpectator()) {
                    lead.setGameMode(GameType.SPECTATOR);   // back after a log-in
                }
            }
        }
        tickFlares(t);
        tickSquads(server, t);
        if (card != null && t >= cardEnds) {
            card = null;
            m.warEvent(server, SiegeEvent.NONE);
            for (ServerPlayer p : everyone(server)) {
                tell(p, "nightsiege.war.card_over", ChatFormatting.GRAY);
            }
        }
        if (t % 20 == 0) {
            keepLoaded();
        }
        if (bot) {
            tickBot(server, t);
        }
        if (lead != null && t % 10 == 0) {
            sendData(lead, false);
        }
    }

    /** A new wave: its gold, half of the rest over. */
    private void newWave(MinecraftServer server, int w) {
        // The first wave plays with the build-up's gold; every later one brings its own and half the rest.
        int carried = w == 1 ? gold : WarShop.carry(gold);
        int fresh = w == 1 ? 0 : WarShop.waveGold(roundGold, w, waves);
        gold = carried + fresh;
        wave = w;
        if (file != null && w > 1) {
            file.goldJeWelle.add(fresh);
        }
        log("welle", "welle_" + w, -1, gold);
        Component text = w == 1 ? Component.translatable("nightsiege.war.wave_first", waves).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                : Component.translatable("nightsiege.war.wave", w, waves).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        for (ServerPlayer p : everyone(server)) {
            p.sendSystemMessage(text);
        }
        ServerPlayer lead = leaderPlayer(server);
        if (lead != null && w > 1) {
            tell(lead, "nightsiege.war.gold_wave", ChatFormatting.GOLD, fresh, carried, gold);
        }
        if (world != null && core != null) {
            world.playSound(null, core.getX() + 0.5, core.getY() + 4, core.getZ() + 0.5, SoundEvents.RAID_HORN.value(),
                    SoundSource.HOSTILE, 3.0f, 0.8f);
        }
    }

    /** The chunks of the gathering points and the squads stay loaded while the round runs. */
    private void keepLoaded() {
        try {
            for (BlockPos p : points) {
                world.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(p.getX() >> 4, p.getZ() >> 4), 2);
            }
            Set<Long> held = new HashSet<>();
            for (Squad s : squads) {
                Vec3 c = centre(s);
                if (c != null) {
                    world.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL,
                            new ChunkPos((int) Math.floor(c.x) >> 4, (int) Math.floor(c.z) >> 4), 2);
                }
                // Kriegsführer 3: every member where it is, and where its order takes it.
                for (UUID id : s.members) {
                    Entity e = world.getEntity(id);
                    if (e != null) {
                        hold(held, e.getX(), e.getZ());
                    }
                }
                WarOrders.Order o = s.commanded ? s.queue.current() : null;
                if (o != null) {
                    hold(held, o.x(), o.z());
                }
            }
        } catch (RuntimeException ignored) {
            // loading is a help, not a must
        }
    }

    private void hold(Set<Long> held, double x, double z) {
        int cx = (int) Math.floor(x) >> 4;
        int cz = (int) Math.floor(z) >> 4;
        if (held.add(((long) cx << 32) ^ (cz & 0xffffffffL))) {
            world.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(cx, cz), 1);
        }
    }

    // ------------------------------------------------------------------ squads

    private void tickSquads(MinecraftServer server, long now) {
        SiegeConfig cfg = m.config();
        Iterator<Squad> it = squads.iterator();
        List<Squad> toSpawn = new ArrayList<>();
        while (it.hasNext()) {
            Squad s = it.next();
            if (!s.spawned) {
                if (now >= s.spawnAt) {
                    toSpawn.add(s);
                }
                continue;
            }
            if (now % 20 == 0) {
                prune(s);
                if (s.members.isEmpty()) {
                    it.remove();
                    continue;
                }
            }
            if (s.commanded && now % WarOrders.STEER_TICKS == 0) {
                steer(s, now);   // Kriegsführer 3: the leader's orders take hold within half a second
            }
            if (now % 10 == 0 && "gather".equals(s.order) && !s.isMachine() && !s.isBoss()) {
                steerGather(s, now);
                if (now - s.gatherSince >= cfg.warAutoStormSeconds * 20L) {
                    storm(s, now);
                }
            }
        }
        for (Squad s : toSpawn) {
            spawn(server, s, now);
        }
    }

    private void prune(Squad s) {
        Iterator<UUID> it = s.members.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = world.getEntity(id);
            if (!(e instanceof LivingEntity l) || !l.isAlive()) {
                it.remove();
                memberOf.remove(id);
            }
        }
    }

    /** Gathering: back to the point when pushed off, no chasing defenders far from it. */
    private void steerGather(Squad s, long now) {
        if (s.point < 0 || s.point >= points.size()) {
            return;
        }
        BlockPos p = points.get(s.point);
        for (UUID id : s.members) {
            if (!(world.getEntity(id) instanceof Mob mob) || mob.isPassenger()) {
                continue;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && !mayTarget(mob, target)) {
                mob.setTarget(null);
                target = null;
            }
            if (target != null) {
                continue;
            }
            double dx = mob.getX() - (p.getX() + 0.5);
            double dz = mob.getZ() - (p.getZ() + 0.5);
            if (dx * dx + dz * dz > GATHER_NEAR * GATHER_NEAR && (now >= s.nextNav || mob.getNavigation().isDone())) {
                mob.getNavigation().moveTo(p.getX() + 0.5 + random.nextInt(5) - 2, p.getY(), p.getZ() + 0.5 + random.nextInt(5) - 2, 1.0);
            }
        }
        if (now >= s.nextNav) {
            s.nextNav = now + 40;
        }
    }

    /** The squad comes: monsters at its point, a machine, or the boss. */
    private void spawn(MinecraftServer server, Squad s, long now) {
        s.spawned = true;
        BlockPos p = s.point >= 0 && s.point < points.size() ? points.get(s.point) : core;
        s.sx = p.getX() + 0.5;
        s.sz = p.getZ() + 0.5;
        if (s.isMachine()) {
            SiegeMachineKind kind = SiegeMachineKind.byId(s.item.substring("machine.".length()));
            Vec3 at = machineSpot(p);
            List<UUID> before = m.machines().testHitboxes();
            boolean ok = kind != null && at != null && m.machines().testSpawn(m.bossHost(), m.config(), kind, at);
            if (ok) {
                for (UUID id : m.machines().testHitboxes()) {
                    if (!before.contains(id)) {
                        s.members.add(id);
                        memberOf.put(id, s);
                    }
                }
                s.total = Math.max(1, s.members.size());
                s.order = "free";
                if (s.members.isEmpty()) {
                    s.members.add(UUID.randomUUID());   // never seen: it counts as standing until the round ends
                }
            } else {
                refund(server, s);
            }
            return;
        }
        if (s.isBoss()) {
            BossKind kind = BossKind.byId(s.item.substring("boss.".length()));
            boolean ok = false;
            if (kind != null) {
                m.bosses().plan(kind);
                m.setWarSpot(p);
                try {
                    ok = m.bosses().spawn(m.bossHost());
                } finally {
                    m.setWarSpot(null);
                }
            }
            Mob boss = ok ? m.bosses().boss(world) : null;
            if (boss != null) {
                s.members.add(boss.getUUID());
                memberOf.put(boss.getUUID(), s);
                s.total = 1;
                s.order = "free";
                if (s.commanded) {
                    start(s, now);
                }
                Component text = Component.translatable("nightsiege.war.boss_comes", Component.translatable("nightsiege.boss." + kind.id()))
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                for (ServerPlayer pl : everyone(server)) {
                    pl.sendSystemMessage(text);
                }
            } else {
                m.bosses().reset(server);
                bossBought = false;
                refund(server, s);
            }
            return;
        }
        int made = 0;
        for (String id : s.mobs) {
            if (m.liveCount() >= m.maxLive()) {
                break;
            }
            String use = MobFactory.isUnavailable(id) ? fallback(id) : id;
            BlockPos spot = spotNear(p);
            Mob mob = spot == null ? null : m.warSpawn(use, spot);
            if (mob == null) {
                continue;
            }
            made++;
            s.members.add(mob.getUUID());
            memberOf.put(mob.getUUID(), s);
            for (Entity rider : mob.getPassengers()) {
                s.members.add(rider.getUUID());
                memberOf.put(rider.getUUID(), s);
            }
        }
        s.total = s.members.size();
        if (made == 0) {
            refund(server, s);
            return;
        }
        String wanted = s.order;
        if (s.commanded) {
            start(s, now);   // Kriegsführer 3: ordered while it was coming
        } else if (s.ownWill) {
            s.order = "free";
        } else if ("target".equals(wanted)) {
            target(s, s.tx, s.tz, now);
        } else if ("storm".equals(wanted)) {
            storm(s, now);
        } else {
            s.order = "gather";
            s.gatherSince = now;
        }
    }

    /** A monster no mod of the server has: the nearest vanilla one. */
    private static String fallback(String id) {
        return id.contains("skirmisher") || id.contains("illager") ? "minecraft:vindicator" : "minecraft:zombie";
    }

    /** Ground near a gathering point for one monster. */
    private BlockPos spotNear(BlockPos p) {
        for (int i = 0; i < 10; i++) {
            int dx = i == 0 ? 0 : random.nextInt(7) - 3;
            int dz = i == 0 ? 0 : random.nextInt(7) - 3;
            int x = p.getX() + dx;
            int z = p.getZ() + dz;
            if (!world.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (Math.abs(y - p.getY()) <= 4 && free(at) && world.getFluidState(at.below()).isEmpty()) {
                return at;
            }
        }
        return free(p) ? p : null;
    }

    /** A machine stands a few blocks behind its point, facing the base. */
    private Vec3 machineSpot(BlockPos p) {
        double dx = p.getX() - core.getX();
        double dz = p.getZ() - core.getZ();
        double d = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        for (int back : new int[] {5, 3, 7, 0}) {
            int x = (int) Math.floor(p.getX() + dx / d * back);
            int z = (int) Math.floor(p.getZ() + dz / d * back);
            if (!world.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (world.getFluidState(at.below()).isEmpty() && world.getFluidState(at).isEmpty()) {
                return new Vec3(x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    private void refund(MinecraftServer server, Squad s) {
        int price = priceOf(s.item);
        gold += price;
        squads.remove(s);
        for (UUID id : s.members) {
            memberOf.remove(id);
        }
        ServerPlayer lead = leaderPlayer(server);
        if (lead != null) {
            tell(lead, "nightsiege.war.no_room", ChatFormatting.GRAY, price);
        }
        flares.removeIf(f -> f.at().equals(s.point >= 0 && s.point < points.size() ? points.get(s.point) : core) && f.until() >= world.getGameTime());
    }

    // ------------------------------------------------------------------ orders

    /** Target: the raid's plan takes the squad to the wall at that spot (and in, once a way is open). */
    private void target(Squad s, double x, double z, long now) {
        s.order = "target";
        s.tx = x;
        s.tz = z;
        double angle = base != null ? base.angleOf(x, z) : Math.atan2(z - core.getZ() - 0.5, x - core.getX() - 0.5);
        attack(s, angle, x, z);
    }

    /** Storm: towards the base from the squad's own side. */
    private void storm(Squad s, long now) {
        s.order = "storm";
        BlockPos p = s.point >= 0 && s.point < points.size() ? points.get(s.point) : core;
        s.tx = p.getX() + 0.5;
        s.tz = p.getZ() + 0.5;
        double angle = base != null ? base.angleOf(s.tx, s.tz) : Math.atan2(s.tz - core.getZ() - 0.5, s.tx - core.getX() - 0.5);
        attack(s, angle, core.getX() + 0.5, core.getZ() + 0.5);
    }

    private void attack(Squad s, double angle, double x, double z) {
        RaidPlan raid = m.raid();
        long now = world.getGameTime();
        if ("leitertraeger".equals(s.item) && base != null) {
            // The ladder carriers take their ladders to the wall on that side (then they climb with the rest).
            List<Mob> carriers = new ArrayList<>();
            for (UUID id : s.members) {
                if (world.getEntity(id) instanceof Mob mob) {
                    carriers.add(mob);
                }
            }
            m.besiegers().carriers(core, carriers, angle, now);
        }
        for (UUID id : s.members) {
            if (!(world.getEntity(id) instanceof Mob mob) || mob.isPassenger()) {
                continue;
            }
            if (WarShop.SAPPER.equals(s.item) && base != null && !Besiegers.busy(mob)
                    && m.besiegers().sapper(mob, core, base, m.config(), random, angle, now)) {
                continue;   // the sapper goes for the wall on its own
            }
            raid.release(mob);
            if (raid.active() && RaidPlan.eligible(mob)) {
                raid.enlist(mob, angle);
            }
            if (!raid.enlisted(mob.getUUID())) {
                // Outside the plan (no plan, a flyer): straight at the spot, then the nearest defender.
                mob.getNavigation().moveTo(x, mob.getY(), z, 1.1);
                ServerPlayer near = nearestDefender(mob);
                if (near != null) {
                    mob.setTarget(near);
                }
            }
        }
    }

    /** Gather: the raid lets go, back to the point. */
    private void gather(Squad s, long now) {
        free(s);
        s.order = "gather";
        s.gatherSince = now;
        s.nextNav = 0;
        for (UUID id : s.members) {
            if (world.getEntity(id) instanceof Mob mob) {
                m.raid().release(mob);
                mob.setTarget(null);
            }
        }
    }

    private ServerPlayer nearestDefender(Mob mob) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : rosterOnline(world.getServer(), world)) {
            if (m.isFallen(p.getUUID()) || p.isSpectator()) {
                continue;
            }
            double d = p.distanceToSqr(mob);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ Kriegsführer 3 (K1): the leader's orders

    /** The squads of "1,4,7" (unknown ids left out). */
    private List<Squad> picked(String ids) {
        List<Squad> out = new ArrayList<>();
        for (String part : ids.split(",")) {
            int id = parse(part, -1);
            for (Squad q : squads) {
                if (q.id == id && !out.contains(q)) {
                    out.add(q);
                }
            }
        }
        return out;
    }

    /**
     * An order for the picked squads: "gather" (back to the old ways: gathering, auto-storm) or a kind of
     * {@link WarOrders.Kind}; queued (Shift) it waits behind the orders before it.
     */
    private void command(MinecraftServer server, List<Squad> picked, String what, double x, double z, boolean queued, String target, long now) {
        if (picked.isEmpty() || world == null || core == null) {
            return;
        }
        if ("gather".equals(what)) {
            for (Squad s : picked) {
                free(s);
                if (s.isMachine()) {
                    aimMachine(server, s, "free", x, z);
                } else if (s.ownWill || s.isBoss()) {
                    s.order = "free";
                } else if (s.spawned) {
                    gather(s, now);
                } else {
                    s.order = "coming";
                }
            }
            log("befehl", "sammeln#" + picked.size(), -1, picked.size());
            return;
        }
        WarOrders.Kind kind = WarOrders.Kind.byId(what);
        if (kind == null) {
            return;
        }
        boolean spot = !Double.isNaN(x) && !Double.isNaN(z) && inView(x, z);
        ServerPlayer foe = null;
        if (kind == WarOrders.Kind.ATTACK && target != null && !target.isEmpty()) {
            foe = defenderNamed(server, target);
            if (foe == null) {
                return;
            }
            x = foe.getX();
            z = foe.getZ();
            spot = true;
        }
        if (kind.needsSpot() && !spot) {
            return;
        }
        int n = 0;
        for (Squad s : picked) {
            if (s.isMachine()) {
                // A machine aims (as since 05.11.); hold and retreat let it shoot on its own again.
                if (kind == WarOrders.Kind.HOLD || kind == WarOrders.Kind.RETREAT) {
                    aimMachine(server, s, "free", x, z);
                } else if (spot) {
                    aimMachine(server, s, "target", x, z);
                }
                continue;
            }
            if (kind == WarOrders.Kind.SPECIAL && WarOrders.specialOf(s.item) == WarOrders.Special.NONE) {
                continue;   // no job of its own
            }
            WarOrders.Order o;
            switch (kind) {
                case RETREAT -> {
                    BlockPos pt = s.point >= 0 && s.point < points.size() ? points.get(s.point) : core;
                    o = WarOrders.Order.at(kind, pt.getX() + 0.5, pt.getZ() + 0.5);
                }
                case HOLD -> {
                    Vec3 c = centre(s);
                    o = WarOrders.Order.at(kind, c != null ? c.x : s.sx, c != null ? c.z : s.sz);
                }
                case STORM -> o = WarOrders.Order.at(kind, core.getX() + 0.5, core.getZ() + 0.5);
                default -> o = new WarOrders.Order(kind, x, z, foe == null ? "" : foe.getUUID().toString());
            }
            s.commanded = true;
            s.queue.give(o, queued);
            if (s.spawned && (!queued || s.queue.size() == 1)) {
                start(s, now);
            }
            n++;
        }
        if (n > 0) {
            if (file != null) {
                file.befehle++;
            }
            log("befehl", String.format(Locale.ROOT, "%s%s#%d@%.0f,%.0f", queued ? "+" : "", kind.id(), n, x, z), -1, n);
        }
    }

    /** V: the picked squads' stance goes on (the first one's next); it stands with an order from now on. */
    private void stance(List<Squad> picked) {
        if (picked.isEmpty()) {
            return;
        }
        WarOrders.Stance next = picked.get(0).stance.next();
        for (Squad s : picked) {
            if (s.isMachine()) {
                continue;
            }
            s.stance = next;
            if (!s.commanded) {
                s.commanded = true;
                Vec3 c = centre(s);
                if (c != null) {
                    s.sx = c.x;
                    s.sz = c.z;
                }
                if (s.spawned) {
                    start(s, world.getGameTime());
                }
            }
        }
        log("befehl", "haltung_" + next.id(), -1, picked.size());
    }

    /** E twice: the sappers go off where they are, the boss calls its ability. */
    private void specialNow(MinecraftServer server, List<Squad> picked, long now) {
        for (Squad s : picked) {
            WarOrders.Special sp = WarOrders.specialOf(s.item);
            if (sp == WarOrders.Special.BLAST && s.spawned) {
                for (Mob mob : mobsOf(s)) {
                    m.besiegers().igniteNow(world, mob, core, now);
                }
                log("befehl", "zuenden#" + s.id, s.point, 0);
            } else if (sp == WarOrders.Special.BOSS && s.spawned) {
                bossAbility(server, s, now);
            }
        }
    }

    private boolean bossAbility(MinecraftServer server, Squad s, long now) {
        ServerPlayer lead = leaderPlayer(server);
        long wait = WarOrders.cooldownLeft(s.specialUsed, now);
        if (wait > 0) {
            if (lead != null) {
                tell(lead, "nightsiege.war.special_wait", ChatFormatting.GRAY, (wait + 19) / 20);
            }
            return false;
        }
        if (!m.bosses().commandAbility(now)) {
            if (lead != null) {
                tell(lead, "nightsiege.war.special_none", ChatFormatting.GRAY);
            }
            return false;
        }
        s.specialUsed = now;
        log("befehl", "boss_faehigkeit#" + s.id, s.point, 0);
        return true;
    }

    /** Back to the old ways (gathering, the auto-storm, a job of its own). */
    private void free(Squad s) {
        if (!s.commanded) {
            return;
        }
        s.commanded = false;
        s.queue.clear();
        if (world == null) {
            return;
        }
        for (UUID id : s.members) {
            Foes.orderSpot(id, null, 0L);
            Foes.hold(world.getEntity(id), false);
        }
    }

    private List<Mob> mobsOf(Squad s) {
        List<Mob> out = new ArrayList<>();
        if (world == null) {
            return out;
        }
        for (UUID id : s.members) {
            if (world.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                out.add(mob);
            }
        }
        return out;
    }

    /** A new order takes hold now: old jobs and fights the order does not allow end, the way is new. */
    private void start(Squad s, long now) {
        WarOrders.Order o = s.queue.current();
        s.nextNav = 0;
        for (Mob mob : mobsOf(s)) {
            m.raid().release(mob);
            if (Besiegers.busy(mob)) {
                Besiegers.disrupt(mob);
            }
            Foes.orderSpot(mob.getUUID(), null, 0L);
            Foes.hold(mob, true);
            LivingEntity t = mob.getTarget();
            if (t != null && !mayTarget(mob, t)) {
                mob.setTarget(null);
            }
            if (!mob.isPassenger()) {
                mob.getNavigation().stop();
            }
        }
        if (o == null) {
            s.order = "idle";
            return;
        }
        s.order = o.kind().id();
        switch (o.kind()) {
            case STORM -> storm(s, now);
            case ATTACK -> {
                if (o.hasTarget()) {
                    steer(s, now);
                } else {
                    target(s, o.x(), o.z(), now);   // a building: the raid's way in, the ladders and sappers at work
                }
            }
            case SPECIAL -> {
                special(s, o, now);
                if (WarOrders.specialOf(s.item) == WarOrders.Special.BOSS && s.queue.current() == o) {
                    advance(s, now);   // the ability is called: the next order at once
                    return;
                }
            }
            default -> steer(s, now);
        }
        if (s.queue.current() == o) {
            s.order = o.kind().id();   // storm() and target() wrote their old names
        }
    }

    /** The unit's own job at the leader's spot. */
    private void special(Squad s, WarOrders.Order o, long now) {
        Vec3 spot = new Vec3(o.x(), groundY(o.x(), o.z()), o.z());
        List<Mob> mobs = mobsOf(s);
        switch (WarOrders.specialOf(s.item)) {
            case LADDER -> m.besiegers().carriersTo(core, mobs, spot, now);
            case BLAST -> {
                boolean any = false;
                for (Mob mob : mobs) {
                    any |= m.besiegers().sapperAt(mob, core, m.config(), spot, now);
                }
                ServerPlayer lead = leaderPlayer(world.getServer());
                if (!any && lead != null) {
                    tell(lead, "nightsiege.war.special_full", ChatFormatting.GRAY);
                }
            }
            case TUNNEL, GRAPPLE, STANDARD -> {
                boolean dug = WarOrders.specialOf(s.item) == WarOrders.Special.TUNNEL && !mobs.isEmpty();
                for (Mob mob : mobs) {
                    Foes.orderSpot(mob.getUUID(), spot, now);
                    dug &= Foes.emerged(mob.getUUID());
                }
                ServerPlayer lead = dug ? leaderPlayer(world.getServer()) : null;
                if (lead != null) {
                    tell(lead, "nightsiege.war.special_dug", ChatFormatting.GRAY);   // a tunneler digs once (Foes.tickTunneler)
                }
            }
            case BOSS -> bossAbility(world.getServer(), s, now);
            default -> {
            }
        }
    }

    /** Is the special order done (then the next in the row)? Ladders and sappers at work, a tunneler under the ground: not yet. */
    private boolean specialDone(Squad s, WarOrders.Order o) {
        switch (WarOrders.specialOf(s.item)) {
            case LADDER, BLAST -> {
                for (Mob mob : mobsOf(s)) {
                    if (Besiegers.busy(mob)) {
                        return false;
                    }
                }
                return true;
            }
            case TUNNEL -> {
                for (Mob mob : mobsOf(s)) {
                    if (Foes.roleOf(mob) == Foes.Role.TUNNELER && !Foes.emerged(mob.getUUID())) {
                        return false;
                    }
                }
                return true;
            }
            case GRAPPLE, STANDARD -> {
                Vec3 c = centre(s);
                return c == null || WarOrders.arrived(c.x, c.z, o.x(), o.z());
            }
            default -> {
                return true;
            }
        }
    }

    /** The order is done: the next one in the row, or the squad stands where it is. */
    private void advance(Squad s, long now) {
        s.queue.next();
        start(s, now);
    }

    /** Every half second: the squad's order carried on (the way, the target, whom it may fight). */
    private void steer(Squad s, long now) {
        WarOrders.Order o = s.queue.current();
        Vec3 c = centre(s);
        boolean nav = now >= s.nextNav;
        if (nav) {
            s.nextNav = now + 40;
        }
        if (o == null) {
            for (Mob mob : mobsOf(s)) {
                LivingEntity t = drop(mob);
                if (t == null && far(mob, s.sx, s.sz, WarOrders.ARRIVE + 1.0) && (nav || mob.getNavigation().isDone())) {
                    walk(mob, s.sx, s.sz, 1.0);
                }
            }
            return;
        }
        switch (o.kind()) {
            case MOVE, ATTACK_MOVE, RETREAT -> {
                if (c != null && WarOrders.arrived(c.x, c.z, o.x(), o.z())) {
                    s.sx = o.x();
                    s.sz = o.z();
                    advance(s, now);
                    return;
                }
                double speed = o.kind() == WarOrders.Kind.RETREAT ? WarOrders.RETREAT_SPEED : 1.0;
                for (Mob mob : mobsOf(s)) {
                    LivingEntity t = drop(mob);
                    if (t == null && o.kind() == WarOrders.Kind.ATTACK_MOVE) {
                        ServerPlayer near = nearestDefender(mob);
                        if (near != null && near.distanceTo(mob) <= WarOrders.LEASH) {
                            mob.setTarget(near);
                            t = mob.getTarget();
                        }
                    }
                    if (t == null && (nav || mover(mob).getNavigation().isDone())) {
                        walk(mob, o.x(), o.z(), speed);
                    }
                }
            }
            case ATTACK -> {
                if (!o.hasTarget()) {
                    // A building: the raid's plan takes them; a way point behind it waits until they are there.
                    if (s.queue.size() > 1 && c != null && WarOrders.arrived(c.x, c.z, o.x(), o.z())) {
                        advance(s, now);
                    }
                    return;
                }
                ServerPlayer foe = defenderById(o.target());
                if (foe == null) {
                    advance(s, now);   // fallen, gone: the next order
                    return;
                }
                for (Mob mob : mobsOf(s)) {
                    if (mob.getTarget() != foe) {
                        mob.setTarget(foe);
                    }
                    if (nav || mover(mob).getNavigation().isDone()) {
                        mover(mob).getNavigation().moveTo(foe, 1.1);
                    }
                }
            }
            case HOLD -> {
                for (Mob mob : mobsOf(s)) {
                    LivingEntity t = drop(mob);
                    if (t == null && far(mob, o.x(), o.z(), 2.5) && (nav || mob.getNavigation().isDone())) {
                        walk(mob, o.x(), o.z(), 1.0);
                    }
                }
            }
            case SPECIAL -> {
                if (specialDone(s, o)) {
                    s.sx = o.x();
                    s.sz = o.z();
                    advance(s, now);
                }
            }
            default -> {
                // storm: the raid's plan
            }
        }
    }

    /** The monster's target, dropped when its order does not allow it any more. */
    private LivingEntity drop(Mob mob) {
        LivingEntity t = mob.getTarget();
        if (t != null && (!t.isAlive() || !mayTarget(mob, t))) {
            mob.setTarget(null);
            return null;
        }
        return t;
    }

    private static boolean far(Mob mob, double x, double z, double d) {
        double dx = mob.getX() - x;
        double dz = mob.getZ() - z;
        return dx * dx + dz * dz > d * d;
    }

    /** A rider is steered through its mount (the warlord on his ravager, the camel troop). */
    private static Mob mover(Mob mob) {
        return mob.getVehicle() instanceof Mob mount ? mount : mob;
    }

    private void walk(Mob mob, double x, double z, double speed) {
        double ox = random.nextInt(3) - 1;
        double oz = random.nextInt(3) - 1;
        mover(mob).getNavigation().moveTo(x + ox, groundY(x + ox, z + oz), z + oz, speed);
    }

    private double groundY(double x, double z) {
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    /** Whom a commanded squad's monster may fight ({@link WarOrders#mayEngage}). */
    private boolean engage(Squad s, Mob mob, LivingEntity target) {
        WarOrders.Order o = s.queue.current();
        WarOrders.Kind kind = o == null ? null : o.kind();
        boolean named = o != null && (!o.hasTarget() || o.target().equals(target.getUUID().toString()));
        boolean attacker = mob.getLastHurtByMob() == target;
        double ax = kind == WarOrders.Kind.HOLD ? o.x() : s.sx;
        double az = kind == WarOrders.Kind.HOLD ? o.z() : s.sz;
        double dx = target.getX() - ax;
        double dz = target.getZ() - az;
        return WarOrders.mayEngage(kind, s.stance, named, attacker, Math.sqrt(dx * dx + dz * dz));
    }

    private ServerPlayer defenderNamed(MinecraftServer server, String name) {
        for (ServerPlayer p : rosterOnline(server, world)) {
            if (p.getScoreboardName().equals(name) && !m.isFallen(p.getUUID()) && !p.isSpectator() && p.isAlive()) {
                return p;
            }
        }
        return null;
    }

    private ServerPlayer defenderById(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
        ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
        return p == null || p.level() != world || !p.isAlive() || p.isSpectator() || m.isFallen(uuid) ? null : p;
    }

    // ------------------------------------------------------------------ the leader's actions

    /** "war:…" from the core window or the leader's screen. */
    public void onAction(ServerPlayer player, String argument) {
        if (player == null || argument == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        String[] p = argument.split(":");
        String verb = p.length == 0 ? "" : p[0];
        if (verb.equals("start")) {
            requestStart(player);
            return;
        }
        if (verb.equals("sandbox")) {
            requestSandbox(player, p.length >= 2 ? parse(p[1], 0) : 0);
            return;
        }
        if (!isLeader(player)) {
            return;
        }
        long now = world == null ? 0L : world.getGameTime();
        switch (verb) {
            case "go" -> go(server);
            case "cancel" -> {
                if (phase == Phase.LOBBY) {
                    cancelLobby(server, "nightsiege.war.lobby_cancelled");
                }
            }
            case "giveup" -> {
                if (phase == Phase.ROUND) {
                    giveUp(server, "nightsiege.war.gave_up");
                }
            }
            case "cam" -> {
                if (phase == Phase.ROUND && p.length >= 3) {
                    double x = parseD(p[1]);
                    double z = parseD(p[2]);
                    if (Double.isFinite(x) && Double.isFinite(z)) {   // a broken report (also NaN, Infinity) changes nothing
                        camX = x;
                        camZ = z;
                        moveBody(player, false);
                    }
                }
            }
            case "buy" -> {
                if (phase == Phase.ROUND && p.length >= 2) {
                    int point = p.length >= 3 ? parse(p[2], 0) : 0;
                    String result = buy(server, p[1], point, now);
                    if (result != null) {
                        tell(player, result, ChatFormatting.GRAY);
                    }
                    sendData(player, false);
                }
            }
            case "order" -> {
                if (phase == Phase.ROUND && p.length >= 3) {
                    order(server, parse(p[1], -1), p[2], p.length >= 5 ? parseD(p[3]) : Double.NaN,
                            p.length >= 5 ? parseD(p[4]) : Double.NaN, now);
                    sendData(player, false);
                }
            }
            case "storm" -> {
                if (phase == Phase.ROUND) {
                    stormAll(now);
                    sendData(player, false);
                }
            }
            case "cmd" -> {
                // Kriegsführer 3: "cmd:<ids>:<kind>:<x>:<z>:<queued>[:<defender>]"
                if (phase == Phase.ROUND && p.length >= 6) {
                    command(server, picked(p[1]), p[2], parseD(p[3]), parseD(p[4]), "1".equals(p[5]), p.length >= 7 ? p[6] : "", now);
                    sendData(player, false);
                }
            }
            case "now" -> {
                // Kriegsführer 3, E twice: "now:<ids>" (the sapper goes off, the boss's ability)
                if (phase == Phase.ROUND && p.length >= 2) {
                    specialNow(server, picked(p[1]), now);
                    sendData(player, false);
                }
            }
            case "stance" -> {
                // Kriegsführer 3, V: "stance:<ids>" (aggressive, defensive, passive)
                if (phase == Phase.ROUND && p.length >= 2) {
                    stance(picked(p[1]));
                    sendData(player, false);
                }
            }
            case "refresh" -> sendData(player, phase == Phase.LOBBY);
            default -> {
            }
        }
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** A number from a client command; NaN (= none) when broken or not finite (Pruefer 13.11.: no NaN/Infinity). */
    static double parseD(String s) {
        try {
            double v = Double.parseDouble(s.trim());
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Every squad that gathers storms now. */
    private void stormAll(long now) {
        int n = 0;
        for (Squad s : squads) {
            if (s.ownWill || s.isMachine() || s.isBoss()) {
                continue;
            }
            if (!s.spawned) {
                s.order = "storm";
                n++;
            } else if ("gather".equals(s.order)) {
                storm(s, now);
                n++;
            }
        }
        if (n > 0) {
            log("befehl", "sturm", -1, n);
            if (file != null) {
                file.befehle++;
            }
            world.playSound(null, core.getX() + 0.5, core.getY() + 4, core.getZ() + 0.5, SoundEvents.RAID_HORN.value(),
                    SoundSource.HOSTILE, 2.5f, 1.2f);
        }
    }

    private void order(MinecraftServer server, int id, String what, double x, double z, long now) {
        Squad s = null;
        for (Squad q : squads) {
            if (q.id == id) {
                s = q;
            }
        }
        if (s == null || s.ownWill || s.isBoss()) {
            return;
        }
        free(s);   // the old orders (the bot, the side panel's buttons) end the leader's new ones
        if (s.isMachine()) {
            aimMachine(server, s, what, x, z);
            return;
        }
        if (file != null) {
            file.befehle++;
        }
        switch (what) {
            case "gather" -> {
                if (s.spawned) {
                    gather(s, now);
                } else {
                    s.order = "coming";
                }
                log("befehl", "sammeln#" + id, s.point, 0);
            }
            case "target" -> {
                if (Double.isNaN(x) || Double.isNaN(z) || !inView(x, z)) {
                    return;
                }
                if (s.spawned) {
                    target(s, x, z, now);
                } else {
                    s.order = "target";
                    s.tx = x;
                    s.tz = z;
                }
                log("befehl", String.format(Locale.ROOT, "ziel#%d@%.0f,%.0f", id, x, z), s.point, 0);
            }
            case "storm" -> {
                if (s.spawned) {
                    storm(s, now);
                } else {
                    s.order = "storm";
                }
                log("befehl", "sturm#" + id, s.point, 0);
            }
            default -> {
            }
        }
    }

    /**
     * A machine's order (Simon 05.11.: like the demolitionist, both ways): "target" aims it at the spot,
     * it shoots there as its reload allows; "gather" (or "free") gives it back its own will.
     */
    private void aimMachine(MinecraftServer server, Squad s, String what, double x, double z) {
        ServerPlayer lead = leaderPlayer(server);
        if ("target".equals(what)) {
            if (Double.isNaN(x) || Double.isNaN(z) || !inView(x, z)) {
                return;
            }
            boolean any = false;
            for (UUID id : s.members) {
                any |= m.machines().aim(id, new Vec3(x, core.getY(), z));
            }
            if (!any) {
                if (lead != null) {
                    tell(lead, s.spawned ? "nightsiege.war.machine_far" : "nightsiege.war.machine_coming", ChatFormatting.GRAY);
                }
                return;
            }
            s.order = "target";
            s.tx = x;
            s.tz = z;
            log("befehl", String.format(Locale.ROOT, "maschine#%d@%.0f,%.0f", s.id, x, z), s.point, 0);
        } else {
            for (UUID id : s.members) {
                m.machines().aim(id, null);
            }
            s.order = "free";
            log("befehl", "maschine_frei#" + s.id, s.point, 0);
        }
        if (file != null) {
            file.befehle++;
        }
    }

    private boolean inView(double x, double z) {
        double dx = x - (core.getX() + 0.5);
        double dz = z - (core.getZ() + 0.5);
        return dx * dx + dz * dz <= (radius + 8.0) * (radius + 8.0);
    }

    /** A purchase; null when it went through, else why not (a translation key). */
    String buy(MinecraftServer server, String item, int point, long now) {
        SiegeConfig cfg = m.config();
        int w = wave;
        if (point < 0 || point >= points.size()) {
            point = 0;
        }
        if (points.isEmpty()) {
            return "nightsiege.war.no_points";
        }
        if (item.startsWith("card.")) {
            WarShop.Card c = WarShop.Card.byId(item.substring(5));
            if (c == null) {
                return null;
            }
            if (w < 1 && !sandbox) {
                return "nightsiege.war.card_prep";
            }
            if (usedCards.contains(c.id()) && !sandbox) {
                return "nightsiege.war.card_used";
            }
            if (card != null) {
                return "nightsiege.war.card_busy";
            }
            if (gold < c.price() && !sandbox) {
                return "nightsiege.war.poor";
            }
            if (!sandbox) {
                gold -= c.price();
            }
            usedCards.add(c.id());
            card = c;
            cardEnds = now + cfg.warWaveSeconds * 20L;
            m.warEvent(server, c.event());
            spent(c.price());
            log("karte", c.id(), -1, c.price());
            Component text = Component.translatable("nightsiege.war.card_played", leaderName,
                    Component.translatable("nightsiege.war.card." + c.id())).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            for (ServerPlayer p : everyone(server)) {
                p.sendSystemMessage(text);
            }
            return null;
        }
        int price = priceOf(item);
        if (price <= 0) {
            return null;
        }
        List<String> mobs = List.of();
        boolean ownWill = false;
        if (item.startsWith("machine.")) {
            SiegeMachineKind kind = SiegeMachineKind.byId(item.substring(8));
            if (kind == null) {
                return null;
            }
            if (w < cfg.warMachineFromWave && !sandbox) {
                return "nightsiege.war.later";
            }
            if (machinesStanding() >= (sandbox ? SANDBOX_MACHINES : cfg.warMaxMachines)) {
                return "nightsiege.war.machines_full";
            }
        } else if (item.startsWith("boss.")) {
            BossKind kind = BossKind.byId(item.substring(5));
            if (kind == null || !bossOffers.contains(kind)) {
                return null;
            }
            if (w < cfg.warBossFromWave && !sandbox) {
                return "nightsiege.war.later";
            }
            if (sandbox ? squads.stream().anyMatch(Squad::isBoss) : bossBought) {   // the sandbox: one boss at a time
                return "nightsiege.war.boss_used";
            }
        } else {
            WarShop.Squad sq = WarShop.squad(item);
            if (sq == null) {
                return null;
            }
            if (fromLevel(sq) > level && !sandbox) {
                return "nightsiege.war.level";
            }
            if (m.liveCount() + pendingMonsters() + sq.size() > m.maxLive()) {
                return "nightsiege.war.full";
            }
            mobs = sq.mobs();
            ownWill = sq.ownWill();
        }
        if (gold < price && !sandbox) {
            return "nightsiege.war.poor";
        }
        if (!sandbox) {
            gold -= price;
        }
        spent(price);
        if (item.startsWith("boss.")) {
            bossBought = true;
        }
        Squad s = new Squad(nextSquad++, item, point, ownWill, mobs, now);
        long delay = cfg.warSquadDelaySeconds * 20L;
        long from = wave < 1 ? roundStart + prepSeconds(cfg) * 20L : now;
        s.spawnAt = from + delay;
        s.total = item.startsWith("machine.") || item.startsWith("boss.") ? 1 : mobs.size();
        squads.add(s);
        BlockPos at = points.get(point);
        flares.add(new Flare(at, Math.max(now, from), s.spawnAt));
        log("kauf", item, point, price);
        if (file != null) {
            file.kaeufe++;
        }
        return null;
    }

    private void spent(int price) {
        if (file != null) {
            file.ausgegeben += price;
        }
    }

    /** From which level a squad comes (the sapper as in the nights: from sapperFromLevel). */
    private int fromLevel(WarShop.Squad sq) {
        return WarShop.SAPPER.equals(sq.id()) ? Math.max(1, m.config().sapperFromLevel) : sq.fromLevel();
    }

    private static int priceOf(String item) {
        if (item.startsWith("machine.")) {
            return WarShop.MACHINE_PRICE;
        }
        if (item.startsWith("boss.")) {
            return WarShop.BOSS_PRICE;
        }
        if (item.startsWith("card.")) {
            WarShop.Card c = WarShop.Card.byId(item.substring(5));
            return c == null ? 0 : c.price();
        }
        WarShop.Squad sq = WarShop.squad(item);
        return sq == null ? 0 : sq.price();
    }

    private int pendingMonsters() {
        int n = 0;
        for (Squad s : squads) {
            if (!s.spawned && !s.isMachine() && !s.isBoss()) {
                n += s.mobs.size();
            }
        }
        return n;
    }

    private int machinesStanding() {
        int n = 0;
        for (Squad s : squads) {
            if (s.isMachine()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ flares

    private void tickFlares(long now) {
        if (flares.isEmpty()) {
            return;
        }
        List<ServerPlayer> viewers = world.players();
        DustParticleOptions red = new DustParticleOptions(0xFF2A1A, 4.0f);
        DustParticleOptions glow = new DustParticleOptions(0xFF5A3A, 3.0f);
        Iterator<Flare> it = flares.iterator();
        while (it.hasNext()) {
            Flare f = it.next();
            if (now > f.until()) {
                it.remove();
                continue;
            }
            if (now < f.start()) {
                continue;
            }
            long t = now - f.start();
            double x = f.at().getX() + 0.5;
            double z = f.at().getZ() + 0.5;
            double top = f.at().getY() + 26;
            if (t == 0) {
                world.playSound(null, f.at(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 4.0f, 0.8f);
            }
            for (ServerPlayer p : viewers) {
                if (t < 24) {
                    if (t % 2 != 0) {
                        continue;
                    }
                    double y = f.at().getY() + 2 + t * 1.0;
                    world.sendParticles(p, red, true, true, x, y, z, 4, 0.08, 0.3, 0.08, 0.0);
                    world.sendParticles(p, ParticleTypes.FIREWORK, true, true, x, y - 0.6, z, 3, 0.05, 0.2, 0.05, 0.02);
                } else if (t < 28) {
                    world.sendParticles(p, red, true, true, x, top, z, 90, 3.0, 2.0, 3.0, 0.0);
                    world.sendParticles(p, ParticleTypes.FIREWORK, true, true, x, top, z, 60, 1.8, 1.5, 1.8, 0.2);
                } else if (t % 10 == 0) {
                    world.sendParticles(p, glow, true, true, x, top - (t - 28) * 0.05, z, 10, 1.0, 0.4, 1.0, 0.0);
                }
                // Until the squad is there: a red ring on the ground where it will stand.
                if (t % 6 == 0) {
                    for (int i = 0; i < 16; i++) {
                        double a = Math.PI * 2 * i / 16;
                        world.sendParticles(p, red, true, true, x + Math.cos(a) * 3.0, f.at().getY() + 0.3, z + Math.sin(a) * 3.0,
                                1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
            if (t == 24) {
                world.playSound(null, BlockPos.containing(x, top, z), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 4.0f, 0.7f);
            }
        }
    }

    // ------------------------------------------------------------------ the end

    /** The leader gives up (or is away too long): the defenders win. */
    private void giveUp(MinecraftServer server, String key) {
        if (sandbox) {
            m.finish(server, true);   // the sandbox: ended, nothing to tell
            return;
        }
        for (ServerPlayer p : everyone(server)) {
            tell(p, key, ChatFormatting.GOLD, leaderName);
        }
        m.finish(server, true);
    }

    /** The boss of the round fell: the round goes on. */
    void onBossDown(MinecraftServer server) {
        // The squad goes with its member's death (prune); nothing else changes.
    }

    /**
     * The round is over ({@link SiegeManager#finish}, before its state is cleared): records, the
     * leader back on the ground, the time of day back. {@code won} is the defenders' win; null when
     * it was broken off.
     */
    void onEnd(MinecraftServer server, Boolean won) {
        if (phase != Phase.ROUND) {
            return;
        }
        Foes.clearOrders();
        long now = world == null ? 0L : world.getGameTime();
        int seconds = (int) Math.max(0, (now - roundStart) / 20);
        int wavesSeconds = Math.max(0, seconds - prepSeconds(m.config()));
        List<String> fallen = new ArrayList<>();
        List<String> standing = new ArrayList<>();
        for (UUID id : roster) {
            (m.isFallen(id) ? fallen : standing).add(rosterNames.getOrDefault(id, "?"));
        }
        String result = sandbox ? "sandbox" : won == null ? "aborted" : won ? "lost" : "won";
        List<String> lines = new ArrayList<>();
        SiegeSave.WarRecords rec = m.save().war == null ? (m.save().war = new SiegeSave.WarRecords()) : m.save().war;
        boolean count = won != null && !bot && !sandbox && !TeamSim.running();
        if (count) {
            rec.rounds++;
            if (won) {
                rec.defenderWins++;
            } else {
                rec.leaderWins++;
            }
            if (leader != null) {
                SiegeSave.WarPlayer lp = rec.of(leader.toString(), leaderName);
                if (won) {
                    lp.leaderLosses++;
                } else {
                    lp.leaderWins++;
                }
            }
            for (UUID id : roster) {
                SiegeSave.WarPlayer dp = rec.of(id.toString(), rosterNames.get(id));
                if (won) {
                    dp.defenderWins++;
                } else {
                    dp.defenderLosses++;
                }
            }
            if (!won && (rec.fastestSeconds < 0 || wavesSeconds < rec.fastestSeconds)) {
                rec.fastestSeconds = wavesSeconds;
                rec.fastestWave = Math.max(1, wave);
                rec.fastestBy = leaderName;
                rec.fastestDefenders = roster.size();
                rec.fastestDate = LocalDateTime.now().format(DATE);
                lines.add(line("nightsiege.war.end.record", clock(wavesSeconds), Math.max(1, wave)));
            }
        }
        // The end panel's lines (translation keys with their values).
        if (sandbox) {
            lines.add(0, line("nightsiege.war.end.sandbox", Math.max(0, wave), clock(seconds)));
        } else if (won == null) {
            lines.add(0, line("nightsiege.war.end.aborted"));
        } else if (won) {
            lines.add(0, line("nightsiege.war.end.defenders", standing.size(), roster.size()));
        } else {
            lines.add(0, line("nightsiege.war.end.leader", Math.max(1, wave), clock(wavesSeconds)));
        }
        if (file != null) {
            lines.add(line("nightsiege.war.end.spent", file.ausgegeben, file.kaeufe, file.befehle));
        }
        if (!fallen.isEmpty()) {
            lines.add(line("nightsiege.war.end.fallen", String.join(", ", fallen)));
        }
        if (rec.fastestSeconds >= 0) {
            lines.add(line("nightsiege.war.end.fastest", clock(rec.fastestSeconds), rec.fastestWave, rec.fastestBy, rec.fastestDefenders));
        }
        lines.add(line("nightsiege.war.end.tally", rec.leaderWins, rec.defenderWins));
        if (leader != null && !bot) {
            SiegeSave.WarPlayer lp = rec.players.get(leader.toString());
            if (lp != null) {
                lines.add(line("nightsiege.war.end.mine_leader", leaderName, lp.leaderWins, lp.leaderLosses));
            }
        }
        // Chat: the result for everyone in it.
        Component text = sandbox ? Component.translatable("nightsiege.war.chat_sandbox", leaderName).withStyle(ChatFormatting.GRAY)
                : won == null ? Component.translatable("nightsiege.war.chat_aborted").withStyle(ChatFormatting.GRAY)
                : won ? Component.translatable("nightsiege.war.chat_defenders", standing.size(), roster.size(), leaderName)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                : Component.translatable("nightsiege.war.chat_leader", leaderName, Math.max(1, wave), clock(wavesSeconds))
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        for (ServerPlayer p : everyone(server)) {
            p.sendSystemMessage(text);
            if (count && !won && rec.fastestBy.equals(leaderName) && rec.fastestSeconds == wavesSeconds) {
                tell(p, "nightsiege.war.chat_record", ChatFormatting.GOLD, leaderName, clock(wavesSeconds));
            }
        }
        // The statistics logger: one file per round.
        if (file != null) {
            file.ende = LocalDateTime.now().format(DATE);
            file.gefallen.addAll(fallen);
            file.welle = wave;
            file.sekunden = seconds;
            file.ergebnis = sandbox ? "sandbox" : won == null ? "abgebrochen" : won ? "verteidiger" : "kriegsfuehrer";
            RoundFile f = file;
            m.stats().warRound(f);
            NightSiegeMod.LOGGER.info("[Night Siege] Kriegsführer-Runde vorbei: {} nach {} s (Welle {}), Gold {} von {}, {} Käufe, {} Befehle, gefallen {}",
                    f.ergebnis, seconds, wave, f.ausgegeben, roundGold, f.kaeufe, f.befehle, fallen);
            System.out.println(String.format(Locale.ROOT, "[Night Siege] WAR;%s;%d;%d;%d;%d;%d;%d;%d;%d",
                    f.ergebnis, level, roster.size(), wave, seconds, roundGold, f.ausgegeben, f.kaeufe, fallen.size()));
        }
        m.persist();
        // The leader back where he stood; the time of day back.
        ServerPlayer lead = leaderPlayer(server);
        restoreTime(server);
        restoreLeader(server);
        if (lead != null) {
            WarLeaderData data = new WarLeaderData();
            data.phase = "end";
            data.leader = leaderName;
            data.result = result;
            data.lines.addAll(lines);
            data.wave = wave;
            data.waves = waves;
            send(lead, "warleader", data);
        }
        for (UUID id : roster) {
            m.endFun(server, id);   // the own profiles back (the fun mode, Update 7)
        }
        phase = Phase.NONE;
        leader = null;
        bot = false;
        sandbox = false;
        roster.clear();
        squads.clear();
        memberOf.clear();
        flares.clear();
        card = null;
        file = null;
    }

    /** The records page (★ Rekorde): the war rounds' tally, the fastest win, the viewer's own. */
    void recordLines(List<String> out, ServerPlayer viewer) {
        SiegeSave.WarRecords rec = m.save().war;
        if (rec == null || rec.rounds <= 0) {
            return;
        }
        out.add(line("nightsiege.records.war_title", rec.rounds, rec.leaderWins, rec.defenderWins));
        if (rec.fastestSeconds >= 0) {
            out.add(line("nightsiege.records.war_fastest", clock(rec.fastestSeconds), rec.fastestWave, rec.fastestBy, rec.fastestDefenders));
        }
        SiegeSave.WarPlayer mine = viewer == null ? null : rec.players.get(viewer.getUUID().toString());
        if (mine != null) {
            out.add(line("nightsiege.records.war_mine", mine.leaderWins, mine.leaderLosses, mine.defenderWins, mine.defenderLosses));
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

    private void restoreTime(MinecraftServer server) {
        SiegeSave.WarRestore r = m.save().warLeader;
        if (r == null || r.timeOfDay < 0) {
            return;
        }
        m.setClockTime(server, r.timeOfDay);
        r.timeOfDay = -1;
        if (r.id == null || r.id.isEmpty()) {
            m.save().warLeader = null;   // the bot's round: nobody to put back
        }
        m.persist();
    }

    /** The leader back on the ground (now, or when he logs in again). */
    private void restoreLeader(MinecraftServer server) {
        SiegeSave.WarRestore r = m.save().warLeader;
        if (r == null || r.id == null || r.id.isEmpty()) {
            return;
        }
        ServerPlayer p;
        try {
            p = server.getPlayerList().getPlayer(UUID.fromString(r.id));
        } catch (IllegalArgumentException e) {
            m.save().warLeader = null;
            return;
        }
        if (p == null) {
            return;
        }
        apply(p, r);
        m.save().warLeader = null;
        m.persist();
    }

    private static void apply(ServerPlayer p, SiegeSave.WarRestore r) {
        p.removeEffect(MobEffects.NIGHT_VISION);
        p.setGameMode(GameType.byName(r.gameMode == null || r.gameMode.isEmpty() ? "survival" : r.gameMode, GameType.SURVIVAL));
        ServerLevel level = p.level().getServer() == null ? null : SiegeManager.levelByName(p.level().getServer(), r.dimension);
        if (level != null) {
            p.teleportTo(level, r.x, r.y, r.z, java.util.Set.of(), r.yaw, r.pitch, true);
        }
    }

    /** Logging in: a leader whose round is over (or never came back from a crash) is put back. */
    public void onJoin(ServerPlayer player) {
        SiegeSave.WarRestore r = m.save().warLeader;
        if (r == null || player == null || !player.getUUID().toString().equals(r.id)) {
            return;
        }
        if (phase == Phase.ROUND && isLeader(player)) {
            awaySince = -1L;
            player.setGameMode(GameType.SPECTATOR);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60 * 60, 0, false, false));
            moveBody(player, true);
            sendData(player, true);
            return;
        }
        apply(player, r);
        if (r.timeOfDay < 0) {
            m.save().warLeader = null;
        }
        m.persist();
    }

    /** The server stops in the middle of a round: the leader is put back at once. */
    void onServerStopping(MinecraftServer server) {
        if (phase == Phase.LOBBY) {
            round = null;
            phase = Phase.NONE;
        }
        restoreTime(server);
        restoreLeader(server);
    }

    // ------------------------------------------------------------------ the leader's body

    /** The body hangs high above the camera, so the ground under the camera is sent to him. */
    private void moveBody(ServerPlayer lead, boolean force) {
        if (world == null || core == null) {
            return;
        }
        double dx = camX - (core.getX() + 0.5);
        double dz = camZ - (core.getZ() + 0.5);
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > radius + 8) {
            camX = core.getX() + 0.5 + dx * (radius + 8) / d;
            camZ = core.getZ() + 0.5 + dz * (radius + 8) / d;
        }
        double mx = camX - bodyX;
        double mz = camZ - bodyZ;
        if (!force && lead.level() == world && mx * mx + mz * mz < 12 * 12) {
            return;
        }
        bodyX = camX;
        bodyZ = camZ;
        lead.teleportTo(world, camX, core.getY() + BODY_HEIGHT, camZ, java.util.Set.of(), 0f, 90f, true);
    }

    // ------------------------------------------------------------------ the screen

    private void sendData(ServerPlayer lead, boolean open) {
        if (lead == null) {
            return;
        }
        send(lead, open ? "warleader" : "warleader_update", build(lead.level().getServer()));
    }

    private void close(ServerPlayer lead) {
        if (lead != null && ServerPlayNetworking.canSend(lead, CoreNetworking.ScreenPayload.TYPE)) {
            ServerPlayNetworking.send(lead, new CoreNetworking.ScreenPayload("warleader_close", "{}"));
        }
    }

    private static void send(ServerPlayer p, String kind, WarLeaderData data) {
        if (p != null && ServerPlayNetworking.canSend(p, CoreNetworking.ScreenPayload.TYPE)) {
            ServerPlayNetworking.send(p, new CoreNetworking.ScreenPayload(kind, Json.toJson(data)));
        }
    }

    WarLeaderData build(MinecraftServer server) {
        SiegeConfig cfg = m.config();
        WarLeaderData d = new WarLeaderData();
        d.leader = leaderName;
        d.level = phase == Phase.ROUND ? level : Math.max(1, m.currentLevel());
        if (core != null) {
            d.centerX = core.getX();
            d.centerY = core.getY();
            d.centerZ = core.getZ();
        }
        if (phase == Phase.LOBBY) {
            d.phase = "lobby";
            long now = world == null ? 0L : world.getGameTime();
            d.forceIn = round == null ? 0 : round.forceIn(now);
            d.canGo = round != null && round.readyCount() > 0 && (round.allReady() || d.forceIn == 0);
            if (round != null) {
                for (UUID id : round.members()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    WarLeaderData.Mate mate = new WarLeaderData.Mate();
                    mate.name = p == null ? "?" : p.getScoreboardName();
                    PlayerSkills own = m.save().skillsOf(id.toString());
                    mate.clazz = own.className();
                    mate.ready = round.isReady(id);
                    d.lobby.add(mate);
                }
            }
            d.radius = cfg.warLobbyRadius;
            return d;
        }
        int elapsed = m.warElapsedTicks();
        d.phase = wave < 1 ? "prep" : "wave";
        d.wave = wave;
        d.waves = waves;
        d.seconds = Math.max(0, waveTicksLeft(cfg, elapsed)) / 20;
        d.gold = gold;
        d.nextGold = wave < waves ? WarShop.waveGold(roundGold, Math.max(2, wave + 1), waves) : 0;
        d.radius = radius;
        d.startX = camX;
        d.startZ = camZ;
        d.card = card == null ? "" : card.id();
        d.machines = machinesStanding();
        d.machinesMax = sandbox ? SANDBOX_MACHINES : cfg.warMaxMachines;
        d.sandbox = sandbox;
        for (int i = 0; i < points.size(); i++) {
            WarLeaderData.Point pt = new WarLeaderData.Point();
            pt.index = i;
            pt.x = points.get(i).getX();
            pt.y = points.get(i).getY();
            pt.z = points.get(i).getZ();
            d.points.add(pt);
        }
        if (base != null) {
            for (BaseScan.Gate g : base.gates) {
                WarLeaderData.Target t = new WarLeaderData.Target();
                t.kind = g.arch() ? "arch" : "gate";
                t.x = g.pos().getX();
                t.y = g.pos().getY();
                t.z = g.pos().getZ();
                net.minecraft.world.level.block.state.BlockState door = world == null ? null : world.getBlockState(g.pos());
                t.state = door == null ? "" : WarLeaderData.gateState(g.arch(), BaseScan.isDoor(door),
                        door.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)
                                && door.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN));
                d.targets.add(t);
            }
        }
        long now = world.getGameTime();
        if (base != null && core != null) {
            // Kriegsführer 3: the base's edge, so a click on it attacks.
            for (int i = 0; i < 32; i++) {
                double a = (i + 0.5) * 2 * Math.PI / 32;
                d.edge.add(base.edgeTowards(core.getX() + 0.5 + Math.cos(a), core.getZ() + 0.5 + Math.sin(a)));
            }
        }
        for (Squad s : squads) {
            WarLeaderData.Squad q = new WarLeaderData.Squad();
            q.id = s.id;
            q.item = s.item;
            q.total = Math.max(s.total, s.isMachine() || s.isBoss() ? 1 : s.mobs.size());
            q.alive = s.spawned ? s.members.size() : q.total;
            q.order = s.spawned ? s.order : "coming";
            q.point = s.point;
            q.ownWill = s.ownWill || s.isBoss();
            q.machine = s.isMachine();
            if (s.isMachine() && s.spawned) {
                for (UUID id : s.members) {
                    Vec3 aim = m.machines().aimOf(id);
                    if (aim != null) {
                        q.aimed = true;
                        q.aimX = aim.x;
                        q.aimZ = aim.z;
                    }
                }
            }
            Vec3 c = centre(s);
            if (c != null) {
                q.x = c.x;
                q.z = c.z;
            } else if (s.point >= 0 && s.point < points.size()) {
                q.x = points.get(s.point).getX() + 0.5;
                q.z = points.get(s.point).getZ() + 0.5;
            }
            q.commanded = s.commanded;
            q.stance = s.stance.id();
            for (WarOrders.Order o : s.queue.all()) {
                WarLeaderData.Way way = new WarLeaderData.Way();
                way.kind = o.kind().id();
                way.x = o.x();
                way.z = o.z();
                q.ways.add(way);
            }
            q.special = WarOrders.specialOf(s.item).id();
            q.specialWait = (int) ((WarOrders.cooldownLeft(s.specialUsed, now) + 19) / 20);
            if (s.spawned) {
                for (UUID id : s.members) {
                    if (world.getEntity(id) instanceof LivingEntity l && l.isAlive()) {
                        WarLeaderData.Unit u = new WarLeaderData.Unit();
                        u.x = l.getX();
                        u.y = l.getY();
                        u.z = l.getZ();
                        q.units.add(u);
                        q.health += l.getHealth();
                        q.maxHealth += l.getMaxHealth();
                    }
                }
            }
            q.seconds = !s.spawned ? (int) Math.max(0, (s.spawnAt - now + 19) / 20)
                    : "gather".equals(s.order) ? (int) Math.max(0, (cfg.warAutoStormSeconds * 20L - (now - s.gatherSince) + 19) / 20) : 0;
            d.squads.add(q);
        }
        for (UUID id : roster) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            WarLeaderData.Mate mate = new WarLeaderData.Mate();
            mate.name = rosterNames.getOrDefault(id, "?");
            mate.clazz = m.save().skillsOf(id.toString()).className();
            mate.fallen = m.isFallen(id);
            if (p != null) {
                mate.x = p.getX();
                mate.y = p.getY();
                mate.z = p.getZ();
                mate.health = p.getHealth();
                mate.max = p.getMaxHealth();
            }
            d.defenders.add(mate);
        }
        // The shop: squads, machines, the boss offers, the cards.
        int live = m.liveCount() + pendingMonsters();
        for (WarShop.Squad sq : WarShop.SQUADS) {
            WarLeaderData.Item it = item(sq.id(), "squad", sq.price());
            it.count = sq.size();
            it.from = fromLevel(sq);
            it.ownWill = sq.ownWill();
            it.state = fromLevel(sq) > level && !sandbox ? "level" : live + sq.size() > m.maxLive() ? "full"
                    : gold < sq.price() && !sandbox ? "poor" : "ok";
            d.shop.add(it);
        }
        for (SiegeMachineKind kind : SiegeMachineKind.values()) {
            WarLeaderData.Item it = item("machine." + kind.id(), "machine", WarShop.MACHINE_PRICE);
            it.from = cfg.warMachineFromWave;
            it.state = wave < cfg.warMachineFromWave && !sandbox ? "wave" : machinesStanding() >= d.machinesMax ? "full"
                    : gold < WarShop.MACHINE_PRICE && !sandbox ? "poor" : "ok";
            d.shop.add(it);
        }
        for (BossKind kind : bossOffers) {
            WarLeaderData.Item it = item("boss." + kind.id(), "boss", WarShop.BOSS_PRICE);
            it.from = cfg.warBossFromWave;
            it.state = sandbox ? (squads.stream().anyMatch(Squad::isBoss) ? "busy" : "ok")
                    : bossBought ? "used" : wave < cfg.warBossFromWave ? "wave" : gold < WarShop.BOSS_PRICE ? "poor" : "ok";
            d.shop.add(it);
        }
        for (WarShop.Card c : WarShop.Card.values()) {
            WarLeaderData.Item it = item("card." + c.id(), "card", c.price());
            it.from = 1;
            it.state = sandbox ? (card != null ? "busy" : "ok")
                    : usedCards.contains(c.id()) ? "used" : wave < 1 ? "wave" : card != null ? "busy" : gold < c.price() ? "poor" : "ok";
            d.shop.add(it);
        }
        return d;
    }

    private static WarLeaderData.Item item(String id, String group, int price) {
        WarLeaderData.Item it = new WarLeaderData.Item();
        it.id = id;
        it.group = group;
        it.price = price;
        return it;
    }

    /** The middle of a squad's living members, or null. */
    private Vec3 centre(Squad s) {
        if (!s.spawned || s.members.isEmpty() || world == null) {
            return null;
        }
        double x = 0;
        double z = 0;
        int n = 0;
        for (UUID id : s.members) {
            Entity e = world.getEntity(id);
            if (e != null) {
                x += e.getX();
                z += e.getZ();
                n++;
            }
        }
        return n == 0 ? null : new Vec3(x / n, 0, z / n);
    }

    // ------------------------------------------------------------------ the bot

    /** /nightsiege kriegsfuehrer bot: a bot leads; the operator and those near the core defend. */
    public String startBot(MinecraftServer server) {
        if (phase != Phase.NONE) {
            return "nightsiege.war.taken";
        }
        bot = true;
        String block = blockReason(server);
        if (block != null) {
            bot = false;
            return block;
        }
        CoreRecord rec = m.mainCore();
        world = SiegeManager.levelByName(server, rec.dimension);
        if (world == null) {
            bot = false;
            return "nightsiege.war.no_core";
        }
        core = new BlockPos(rec.x, rec.y, rec.z);
        leader = null;
        leaderName = "Bot";
        openLobby(server, world.getGameTime());
        return null;
    }

    /** The team simulation: the bots defend, a bot leads, no ready round. */
    boolean startBotRound(MinecraftServer server, ServerLevel level, BlockPos at, int wantedLevel, List<ServerPlayer> defenders) {
        if (phase != Phase.NONE || defenders.isEmpty()) {
            return false;
        }
        bot = true;
        botWave = -1;
        botLadders = -1;
        botAssaultAt = 0;
        botSpendAt = 0;
        world = level;
        core = at;
        leader = null;
        leaderName = "Bot";
        round = new StartRound(UUID.randomUUID(), at, level.getGameTime(), 1000, 0, false);
        for (ServerPlayer p : defenders) {
            round.add(p.getUUID());
            round.markReady(p.getUUID());
        }
        phase = Phase.LOBBY;
        forcedLevel = wantedLevel;
        begin(server);
        forcedLevel = 0;
        return phase == Phase.ROUND;
    }

    /** The team simulation's level (the world's otherwise). */
    private int forcedLevel;

    /** Stops a round or a ready round (the command). */
    public boolean stop(MinecraftServer server) {
        if (phase == Phase.LOBBY) {
            cancelLobby(server, "nightsiege.war.lobby_cancelled");
            return true;
        }
        if (phase == Phase.ROUND) {
            m.abort(server);
            return true;
        }
        return false;
    }

    public String status() {
        if (phase == Phase.NONE) {
            return "Kriegsführer: keine Runde";
        }
        if (phase == Phase.LOBBY) {
            return "Kriegsführer: Bereit-Runde von " + leaderName + ", " + (round == null ? 0 : round.readyCount()) + "/"
                    + (round == null ? 0 : round.size()) + " bereit";
        }
        return String.format(Locale.ROOT, "Kriegsführer%s: %s, Welle %d/%d, Gold %s (Runde %d), %d Trupps, %d Sammelplätze, Sicht %d, Karte %s",
                sandbox ? "-Sandbox" : "", leaderName, wave, waves, sandbox ? "∞" : String.valueOf(gold), roundGold, squads.size(),
                points.size(), radius, card == null ? "-" : card.id());
    }

    /**
     * The bot buys by a list: at the start and at every wave it spends what it has (machines from
     * their wave, the boss once, a card now and then), on two sides of the base; its squads storm
     * after 20 seconds, the first one aims at a gate.
     */
    /**
     * The bot leader (Tor 10, plan-kriegsfuehrer-3 Prüfen 4) plays with the orders as a good human does, with only
     * what the window shows (the defenders' places and life, the base's edge and gates): the squads gather until
     * the assault is big enough, then go by a way point before the wall to its weakest spot (fewest defenders),
     * ladders and sappers right there; a defender alone and weak is focused; hurt squads pull back; machines
     * shoot at groups and those up high; the boss (after wave 3) goes for the focus and calls its ability close to them.
     */
    private void tickBot(MinecraftServer server, long now) {
        if (now < botNext) {
            return;
        }
        botNext = now + 40;
        List<WarBotPlan.Defender> defs = botDefenders(server);
        if (botWave != wave) {
            botWave = wave;
            botAssaultAt = now + 20L * 20;
            spendBot(server, now, defs);
        } else if (wave >= 1 && gold >= 4 && now >= botSpendAt) {
            spendBot(server, now, defs);
        }
        if (wave < 1 || core == null) {
            return;
        }
        int focus = WarBotPlan.focus(defs);
        WarBotPlan.Defender foe = focus < 0 ? null : defs.get(focus);
        int ready = 0;
        List<Squad> waiting = new ArrayList<>();
        for (Squad s : new ArrayList<>(squads)) {
            if (!s.spawned || s.members.isEmpty()) {
                continue;
            }
            if (s.isMachine()) {
                int t = WarBotPlan.machineTarget(defs, core.getY());
                if (t >= 0) {
                    WarBotPlan.Defender d = defs.get(t);
                    if (!"target".equals(s.order) || Math.hypot(s.tx - d.x(), s.tz - d.z()) > 4.0) {
                        command(server, List.of(s), "attack", d.x(), d.z(), false, "", now);
                    }
                }
                continue;
            }
            Vec3 c = centre(s);
            if (s.isBoss()) {
                if (foe != null && c != null && Math.hypot(c.x - foe.x(), c.z - foe.z()) <= 8.0) {
                    specialNow(server, List.of(s), now);   // its ability where the defenders are
                }
                WarOrders.Order o = s.queue.current();
                String want = foe == null ? null : defenderUuid(server, foe.name());
                if (want != null && (o == null || o.kind() != WarOrders.Kind.ATTACK || !want.equals(o.target()))) {
                    command(server, List.of(s), "attack", Double.NaN, Double.NaN, false, foe.name(), now);
                } else if (want == null && o == null) {
                    command(server, List.of(s), "storm", Double.NaN, Double.NaN, false, "", now);
                }
                continue;
            }
            float[] life = life(s);
            if (s.botSent && WarBotPlan.retreat(life[0], life[1]) && !"retreat".equals(s.order)) {
                command(server, List.of(s), "retreat", Double.NaN, Double.NaN, false, "", now);
                s.botSent = false;   // spared for the next assault
                continue;
            }
            if (!s.botSent) {
                if ("gather".equals(s.order) || "idle".equals(s.order)) {
                    waiting.add(s);
                    ready += s.members.size();
                }
                continue;
            }
            if (WarOrders.specialOf(s.item) != WarOrders.Special.NONE && !"idle".equals(s.order)) {
                continue;   // at its job (done: it joins the fight)
            }
            // Sent: the focus when it is close and reachable, else on with the storm.
            WarOrders.Order o = s.queue.current();
            if (foe != null && c != null && Math.hypot(c.x - foe.x(), c.z - foe.z()) <= 20.0
                    && (base == null || !base.closed || !base.contains((int) Math.floor(foe.x()), (int) Math.floor(foe.z()))
                    || base.contains((int) Math.floor(c.x), (int) Math.floor(c.z)))) {
                String want = defenderUuid(server, foe.name());
                if (want != null && (o == null || o.kind() != WarOrders.Kind.ATTACK || !want.equals(o.target()))) {
                    command(server, List.of(s), "attack", Double.NaN, Double.NaN, false, foe.name(), now);
                }
            } else if (o == null) {
                command(server, List.of(s), "storm", Double.NaN, Double.NaN, false, "", now);
            }
        }
        if (!waiting.isEmpty() && (ready >= WarBotPlan.assaultSize(defs.size()) || now >= botAssaultAt)) {
            assault(server, waiting, defs, now);
            botAssaultAt = now + 30L * 20;
        }
    }

    /** The assault: by a way point before the wall to its weakest spot; ladders, sappers and the others at their jobs. */
    private void assault(MinecraftServer server, List<Squad> waiting, List<WarBotPlan.Defender> defs, long now) {
        List<double[]> spots = wallSpots();
        int i = WarBotPlan.quietest(spots, defs);
        if (i < 0) {
            for (Squad s : waiting) {
                command(server, List.of(s), "storm", Double.NaN, Double.NaN, false, "", now);
                s.botSent = true;
            }
            return;
        }
        double[] spot = spots.get(i);
        double cx = core.getX() + 0.5;
        double cz = core.getZ() + 0.5;
        double a = Math.atan2(spot[1] - cz, spot[0] - cx);
        double edge = base == null ? Math.hypot(spot[0] - cx, spot[1] - cz) : base.edgeTowards(spot[0], spot[1]);
        double wx = cx + Math.cos(a) * (edge + 1.5);
        double wz = cz + Math.sin(a) * (edge + 1.5);
        double sx = cx + Math.cos(a) * (edge + 10.0);
        double sz = cz + Math.sin(a) * (edge + 10.0);
        for (Squad s : waiting) {
            switch (WarOrders.specialOf(s.item)) {
                // Chef 13.11. 03:20: ladders and sappers together at ONE spot.
                case LADDER, BLAST -> command(server, List.of(s), "special", wx, wz, false, "", now);
                case TUNNEL, GRAPPLE, STANDARD -> command(server, List.of(s), "special", sx, sz, false, "", now);
                default -> {
                    command(server, List.of(s), "move", sx, sz, false, "", now);
                    command(server, List.of(s), "attack", spot[0], spot[1], true, "", now);
                }
            }
            s.botSent = true;
        }
        log("bot", String.format(Locale.ROOT, "sturm@%.0f,%.0f%s", spot[0], spot[1], spot[2] > 0 ? "_tor" : ""), -1, waiting.size());
    }

    /** The wall's spots to choose from: the gates (in front of them) and eight places round the wall. */
    private List<double[]> wallSpots() {
        List<double[]> out = new ArrayList<>();
        double cx = core.getX() + 0.5;
        double cz = core.getZ() + 0.5;
        if (base != null) {
            for (BaseScan.Gate g : base.gates) {
                BlockPos f = g.front() != null ? g.front() : g.pos();
                out.add(new double[]{f.getX() + 0.5, f.getZ() + 0.5, 1});
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            double e = base == null ? 10.0 : base.edgeTowards(cx + Math.cos(a), cz + Math.sin(a));
            double x = cx + Math.cos(a) * (e + 1.0);
            double z = cz + Math.sin(a) * (e + 1.0);
            if (inView(x, z)) {
                out.add(new double[]{x, z, 0});
            }
        }
        return out;
    }

    /** The defenders standing, as the window shows them. */
    private List<WarBotPlan.Defender> botDefenders(MinecraftServer server) {
        List<WarBotPlan.Defender> out = new ArrayList<>();
        if (world == null) {
            return out;
        }
        for (ServerPlayer p : rosterOnline(server, world)) {
            if (!m.isFallen(p.getUUID()) && !p.isSpectator() && p.isAlive()) {
                out.add(new WarBotPlan.Defender(p.getScoreboardName(), p.getX(), p.getY(), p.getZ(), p.getHealth(), p.getMaxHealth()));
            }
        }
        return out;
    }

    private String defenderUuid(MinecraftServer server, String name) {
        ServerPlayer p = defenderNamed(server, name);
        return p == null ? null : p.getUUID().toString();
    }

    /** A squad's life together (as its bar in the list): {health, max}. */
    private float[] life(Squad s) {
        float h = 0;
        float max = 0;
        for (Mob mob : mobsOf(s)) {
            h += mob.getHealth();
            max += mob.getMaxHealth();
        }
        // The fallen count too: the bar starts at the squad's full size.
        if (s.total > 0 && !s.members.isEmpty() && s.members.size() < s.total) {
            max = max / s.members.size() * s.total;
        }
        return new float[]{h, max};
    }

    /** The bot's shopping, at the point nearest to the wall's weakest spot; fitting to the group's size. */
    private void spendBot(MinecraftServer server, long now, List<WarBotPlan.Defender> defs) {
        SiegeConfig cfg = m.config();
        botSpendAt = now + 200;
        if (points.isEmpty()) {
            return;
        }
        List<double[]> spots = core == null ? List.of() : wallSpots();
        int target = WarBotPlan.quietest(spots, defs);
        int main = random.nextInt(points.size());
        if (target >= 0) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < points.size(); i++) {
                double d = Math.hypot(points.get(i).getX() + 0.5 - spots.get(target)[0], points.get(i).getZ() + 0.5 - spots.get(target)[1]);
                if (d < best) {
                    best = d;
                    main = i;
                }
            }
        }
        int flank = (main + points.size() / 2) % points.size();
        // Chef 13.11. 03:20: the boss only after wave 3 (the last wave at the latest), then steered.
        if (wave >= Math.max(cfg.warBossFromWave, Math.min(4, waves)) && !bossBought && gold >= WarShop.BOSS_PRICE && !bossOffers.isEmpty()) {
            buy(server, "boss." + bossOffers.get(random.nextInt(bossOffers.size())).id(), main, now);
        }
        if (wave >= cfg.warMachineFromWave && gold >= WarShop.MACHINE_PRICE + 8) {
            SiegeMachineKind[] kinds = SiegeMachineKind.values();
            buy(server, "machine." + kinds[random.nextInt(kinds.length)].id(), main, now);
        }
        if (wave >= 2 && card == null && random.nextDouble() < 0.3) {
            WarShop.Card[] cards = WarShop.Card.values();
            WarShop.Card c = cards[random.nextInt(cards.length)];
            if (!usedCards.contains(c.id()) && gold >= c.price() + 10) {
                buy(server, "card." + c.id(), main, now);
            }
        }
        if (botWave == wave && botLadders != wave) {
            // Every wave its ways over the wall first: ladder carriers by the group's size, two sappers.
            botLadders = wave;
            for (int i = 0; i < WarBotPlan.ladders(defs.size()); i++) {
                buy(server, "leitertraeger", main, now);
            }
            if (level >= Math.max(1, cfg.sapperFromLevel)) {
                buy(server, WarShop.SAPPER, main, now);
                buy(server, WarShop.SAPPER, main, now);
            }
        }
        List<String> weights = WarBotPlan.shopping(defs.size());
        int tries = 0;
        while (gold >= 3 && tries++ < 60) {
            String id = weights.get(random.nextInt(weights.size()));
            WarShop.Squad sq = WarShop.squad(id);
            if (sq == null || fromLevel(sq) > level || sq.price() > gold) {
                continue;
            }
            String why = buy(server, id, random.nextDouble() < 0.75 ? main : flank, now);
            if ("nightsiege.war.full".equals(why)) {
                break;
            }
        }
    }

    private long botAssaultAt;
    private long botSpendAt;
    private int botLadders = -1;

    // ------------------------------------------------------------------ helpers

    private ServerPlayer leaderPlayer(MinecraftServer server) {
        return leader == null || server == null ? null : server.getPlayerList().getPlayer(leader);
    }

    /** The defenders of the round and the leader. */
    private List<ServerPlayer> everyone(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : roster) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        ServerPlayer lead = leaderPlayer(server);
        if (lead != null && !out.contains(lead)) {
            out.add(lead);
        }
        return out;
    }

    private static void tell(ServerPlayer p, String key, ChatFormatting color, Object... args) {
        if (p != null) {
            p.sendSystemMessage(Component.translatable(key, args).withStyle(color));
        }
    }

    private void log(String was, String ding, int point, int goldValue) {
        if (file == null || world == null) {
            return;
        }
        Entry e = new Entry();
        e.sekunde = (int) Math.max(0, (world.getGameTime() - roundStart) / 20);
        e.welle = wave;
        e.was = was;
        e.ding = ding;
        e.punkt = point;
        e.gold = goldValue;
        if (file.verlauf.size() < 2000) {
            file.verlauf.add(e);
        }
    }

    /** Tests: the round's points, squads and gold. */
    public List<BlockPos> testPoints() {
        return new ArrayList<>(points);
    }

    public int testGold() {
        return gold;
    }

    /** Tests: gold for the leader (to try the machines, the boss and the cards). */
    public void testAddGold(int amount) {
        gold += amount;
    }

    public int testRoundGold() {
        return roundGold;
    }

    public int testRadius() {
        return radius;
    }

    public int testWave() {
        return wave;
    }

    public String testSquads() {
        StringBuilder out = new StringBuilder();
        for (Squad s : squads) {
            out.append(s.id).append('=').append(s.item).append('/').append(s.spawned ? s.order : "coming")
                    .append('/').append(s.members.size()).append(' ');
        }
        return out.toString().trim();
    }

    /** Tests: what the leader's screen gets now. */
    public WarLeaderData testData(MinecraftServer server) {
        return build(server);
    }

    /** Tests: a purchase as the leader's screen sends it. */
    public String testBuy(MinecraftServer server, String item, int point) {
        return buy(server, item, point, world == null ? 0L : world.getGameTime());
    }

    /** Tests: an order as the leader's screen sends it. */
    public void testOrder(MinecraftServer server, int squad, String what, double x, double z) {
        order(server, squad, what, x, z, world == null ? 0L : world.getGameTime());
    }

    public void testStormAll() {
        stormAll(world == null ? 0L : world.getGameTime());
    }

    /** Tests (Kriegsführer 3): an order as the leader's screen sends it ("cmd:..." without "war:"). */
    public void testCommand(ServerPlayer leader, String action) {
        onAction(leader, action);
    }

    /** Tests: a squad's order now, its stance, its row, whether it has the leader's order. */
    public String testOrderOf(int id) {
        for (Squad s : squads) {
            if (s.id == id) {
                return s.order + "/" + s.stance.id() + "/" + s.queue.size() + (s.commanded ? "/cmd" : "/free");
            }
        }
        return "-";
    }

    /** Tests: the living members of a squad. */
    public List<Mob> testMobs(int id) {
        for (Squad s : squads) {
            if (s.id == id) {
                return mobsOf(s);
            }
        }
        return List.of();
    }

    /** Tests: the id of the newest squad of this kind that came (or -1). */
    public int testSquadOf(String item) {
        int best = -1;
        for (Squad s : squads) {
            if (s.spawned && s.item.equals(item)) {
                best = s.id;
            }
        }
        return best;
    }

    /** Tests: the squads' first id that came (or -1). */
    public int testFirstSquad() {
        for (Squad s : squads) {
            if (s.spawned && !s.isMachine() && !s.isBoss()) {
                return s.id;
            }
        }
        return -1;
    }
}
