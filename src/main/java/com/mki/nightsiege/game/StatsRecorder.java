package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.CrateRoll;
import com.mki.nightsiege.core.PlayerSkills;
import com.mki.nightsiege.core.SkillTree;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The statistics logger for the December playtest (concept 28 October 2026,
 * claude/night-siege-statistik-logger-konzept.md): real balancing data that can go over to the
 * stand-alone game later. Only files on the server, nothing the players see:
 * {@code <world>/nightsiege/stats/}.
 *
 * <ul>
 *   <li>one JSON file per night ({@code night_<date>_<time>.json}), saved again at every wave so a
 *       crash keeps what was there, and finished when the night ends;</li>
 *   <li>one file per dungeon attempt and per war camp attempt;</li>
 *   <li>every opened crate as one line of {@code kisten.jsonl}.</li>
 * </ul>
 *
 * Everything is gathered in memory on the server thread; the files are written by a thread of
 * their own, so the waves do not wait for the disk. Every file carries the mod version and the
 * build, so data from before and after a balancing change can be told apart.
 */
public final class StatsRecorder {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter TEXT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nightsiege-stats");
        t.setDaemon(true);
        return t;
    });
    private static String version;
    private static String build;
    /** Set by the team simulation before a run: written into the night's file as "sim". */
    public static volatile String simLabel = "";
    /** The last night that was finished (the team simulation reads its result from it). */
    public static volatile NightFile lastFinished;
    /** Where {@link #lastFinished} was written (the feedback writes it again). */
    private static volatile Path lastPath;

    // ------------------------------------------------------------------ the files

    /** One night. Field names are the keys in the file (German, like the other protocols). */
    public static final class NightFile {
        public String art = "nacht";
        /** A run of the balance tool's team simulation ("variant|level|group|mix|run"), empty for real nights. */
        public String sim = "";
        public String version = "";
        public String build = "";
        public String start = "";
        public String ende = "";
        public boolean abgeschlossen;
        public boolean test;
        public int nacht;
        public int stufe;
        public String ereignis = "";
        public boolean besondereNacht;
        public String fragment = "";
        /** Curse stones laid on the altar for this night (update 2). */
        public List<String> fluchsteine = new ArrayList<>();
        public String boss = "";
        public String ergebnis = "läuft";
        public int dauerSekunden;
        public int killZiel;
        public int kills;
        public int gespawnt;
        public int tode;
        public Basis basis;
        public Map<String, Spieler> spieler = new LinkedHashMap<>();
        public Heerlager heerlager = new Heerlager();
        public List<Maschine> maschinen = new ArrayList<>();
        public List<Welle> wellen = new ArrayList<>();
        public Map<String, Gegner> gegner = new LinkedHashMap<>();
        public Map<String, Turm> tuerme = new LinkedHashMap<>();
        /** 1-click feedback after the night (balance tool, 29 October 2026): player → leicht / passt / schwer. */
        public Map<String, String> bewertung = new LinkedHashMap<>();
        /** Optional with it: player → the subclass they found strongest tonight. */
        public Map<String, String> staerksteKlasse = new LinkedHashMap<>();
    }

    public static final class Basis {
        public boolean geschlossen;
        public int breite;
        public int tiefe;
        public int tore;
    }

    /** A player as they came into the night (or the attempt). */
    public static final class Spieler {
        public String name = "";
        public String uuid = "";
        public String klasse = "";
        public String weg = "";
        public String unterklasse = "";
        public String profil = "";
        /** Skill points spent in the played profile ("class level"). */
        public int punkteImProfil;
        public boolean ultStufe3;
        /** The kit of the night: the class of the played profile. */
        public String kit = "";
    }

    public static final class Heerlager {
        public boolean erschienen;
        public boolean zerstoert;
        public boolean lagerSiegte;
        public int maschinen;
    }

    public static final class Maschine {
        public String art = "";
        public int startSekunde;
        public int endeSekunde = -1;
        public boolean zerstoert;
        public String sabotiertVon = "";
        public int schuesse;
        public int bloecke;
        public double schadenAnSpielern;
    }

    public static final class Welle {
        public int nummer;
        public int startSekunde;
        public int dauerSekunden;
        public int gespawnt;
        public Map<String, Integer> typen = new LinkedHashMap<>();
        public int uebrigAmEnde = -1;
        /** Blocks the night broke during this wave (walls, gates, everything the mending brings back). */
        public int bloeckeZerstoert;
        public Map<String, SpielerWelle> spieler = new LinkedHashMap<>();
        /**
         * Server load in this wave (30.10., "does the server manage 120 monsters?"): average and
         * longest tick in ms (20 ticks a second need under 50), ticks over 50 ms, the most monsters
         * of the night alive at once, all mobs of the world (counted once a second).
         */
        public double msptSchnitt;
        public double msptMax;
        public int ticksUeber50;
        public int lebendMax;
        public int mobsMax;
    }

    public static final class SpielerWelle {
        public double schaden;
        /** Damage dealt by where it came from ("nah:iron_sword", "fern:bow", "turm:arrow_turret", "begleiter" …). */
        public Map<String, Double> schadenNachQuelle = new LinkedHashMap<>();
        public double erhalten;
        /** Damage taken by enemy type. */
        public Map<String, Double> erhaltenNachGegner = new LinkedHashMap<>();
        public double heilungGegeben;
        public double heilungErhalten;
        public int kills;
        public int assists;
        public int tode;
        /**
         * What a defender held off (Simon 30.10.: "warum tankt Bollwerk weniger als Schildkämpfer"): the
         * monsters' damage before armour, shield and reductions, blocked hits too; the hits; the blocked ones.
         */
        public double abgefangen;
        public int treffer;
        public int geblockt;
        /** Life back from life steal (hit, kill and combo heals, the berserker's bloodlust), as it landed. */
        public double lebensraub;
        public int drohneSekunden;
        public double drohneSchaden;
        public Map<String, Integer> faehigkeiten = new LinkedHashMap<>();
        public int turmSekunden;
    }

    public static final class Gegner {
        public int gespawnt;
        public int getoetet;
        public double ueberlebtSekundenSumme;
        public double ueberlebtSekundenSchnitt;
        public double schadenAnSpielern;
        public Map<String, Integer> getoetetVon = new LinkedHashMap<>();
    }

    public static final class Turm {
        public String art = "";
        public String besitzer = "";
        public double schaden;
        public double schadenBemannt;
        public int kills;
        public int killsBemannt;
        public int sekundenBemannt;
    }

    /** One dungeon or war camp attempt. */
    public static final class AttemptFile {
        public String art = "";
        public String version = "";
        public String build = "";
        public String start = "";
        public String ende = "";
        public int dauerSekunden;
        public String typ = "";
        public int stufe;
        public int versuch;
        public int raeume;
        public int raeumeGeraeumt;
        public int wellen;
        public int wellenErreicht;
        public boolean championErreicht;
        public List<Spieler> spieler = new ArrayList<>();
        public int tode;
        public String ergebnis = "";
        public List<String> belohnung = new ArrayList<>();
        public double schadenAusgeteilt;
        public double schadenErhalten;
    }

    /** One crate opened (a line of kisten.jsonl). */
    public static final class CrateLine {
        public String zeit = "";
        public String version = "";
        public String build = "";
        public String spieler = "";
        public String klasse = "";
        public String kistenArt = "";
        public String gerollteArt = "";
        public int stufe;
        public String gewinn = "";
        public String seltenheit = "";
        public boolean doppelt;
        public int gewinnStufe;
        public int punkte;
        public boolean glueck;
        public boolean infernit;
    }

    // ------------------------------------------------------------------ state

    private final SiegeManager m;
    private MinecraftServer server;
    private NightFile night;
    private Path nightPath;
    private long nightStartTick;
    private Welle wave;
    private long waveStartTick;
    private int blocksAtWave;
    /** Mobs of the night: when they came (game time) and their type. */
    private final Map<UUID, Long> mobSince = new HashMap<>();
    private final Map<UUID, String> mobType = new HashMap<>();
    /** Who hit a mob of the night (for the assists). */
    private final Map<UUID, Set<UUID>> hitBy = new HashMap<>();
    /** A mob's life after the last hit: the killing blow (which the damage event leaves out) took the rest. */
    private final Map<UUID, Float> lifeLeft = new HashMap<>();
    /** Machines by their hit box. */
    private final Map<UUID, Maschine> machineOf = new HashMap<>();
    /** Siege days whose war camp stood, fell, won (filled during the day before). */
    private final Map<Long, Heerlager> camps = new HashMap<>();
    private AttemptFile dungeon;
    private long dungeonStartTick;
    private AttemptFile camp;
    private long campStartTick;

    StatsRecorder(SiegeManager manager) {
        this.m = manager;
    }

    void attach(MinecraftServer server) {
        this.server = server;
    }

    private boolean on() {
        return m.config() == null || m.config().statsLogger;
    }

    private static String now() {
        return LocalDateTime.now().format(TEXT_TIME);
    }

    private static synchronized void loadVersion() {
        if (version != null) {
            return;
        }
        version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(NightSiegeMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        build = "";
        try (InputStream in = StatsRecorder.class.getResourceAsStream("/assets/nightsiege/nightsiege-build.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                build = p.getProperty("commit", "");
            }
        } catch (IOException e) {
            build = "";
        }
    }

    private static String version() {
        loadVersion();
        return version;
    }

    private static String build() {
        loadVersion();
        return build;
    }

    private Path folder() {
        if (server == null) {
            return null;
        }
        return server.getWorldPath(LevelResource.ROOT).resolve("nightsiege").resolve("stats").normalize();
    }

    private void writeLater(Path path, Object data) {
        if (path == null) {
            return;
        }
        String json = Json.toJson(data);   // built here, on the server thread
        WRITER.execute(() -> {
            try {
                Files.createDirectories(path.getParent());
                Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                try {
                    Files.writeString(path, json, StandardCharsets.UTF_8);
                } catch (IOException again) {
                    NightSiegeMod.LOGGER.warn("[Night Siege] Statistik nicht geschrieben ({}): {}", path.getFileName(), again.toString());
                }
            }
        });
    }

    private void appendLater(Path path, Object line) {
        if (path == null) {
            return;
        }
        String json = Json.toJson(line).replace("\n", "").replaceAll("\\s{2,}", " ");
        WRITER.execute(() -> {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, json + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                NightSiegeMod.LOGGER.warn("[Night Siege] Kisten-Statistik nicht geschrieben: {}", e.toString());
            }
        });
    }

    // ------------------------------------------------------------------ who and what

    /** A player with class, way, subclass and how far their profile is. */
    static Spieler describe(SiegeManager m, ServerPlayer player) {
        Spieler s = new Spieler();
        s.name = player.getScoreboardName();
        s.uuid = player.getUUID().toString();
        PlayerSkills skills = m.save().skillsOf(s.uuid);
        SkillTree tree = m.skillTree();
        try {
            s.klasse = skills.className().toLowerCase(Locale.ROOT);
            s.profil = skills.chosenClass == null ? "" : skills.chosenClass;
            s.weg = skills.pathTaken(tree);
            s.unterklasse = tree.subclassOf(skills);
            s.punkteImProfil = s.profil.isEmpty() ? 0 : tree.pointsSpentProfile(skills, s.profil);
            s.ultStufe3 = skills.clazz() != null && tree.ultimateMaxed(skills, skills.clazz());
            s.kit = s.klasse;
        } catch (RuntimeException e) {
            // A half-made profile must not stop the protocol.
        }
        if (s.weg == null) {
            s.weg = "";
        }
        if (s.unterklasse == null) {
            s.unterklasse = "";
        }
        return s;
    }

    /** The type of a monster: its role (new attackers), its entity type, boss and elite marked. */
    static String typeOf(Entity e) {
        if (e == null) {
            return "?";
        }
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        for (Foes.Role role : Foes.Role.values()) {
            if (e.entityTags().contains(role.tag())) {
                type = role.entryId();
                break;
            }
        }
        if (e.entityTags().contains(SiegeMachines.TAG_MACHINE)) {
            type = "maschine";
        }
        if (BossService.isBoss(e)) {
            type = "boss:" + type;
        }
        if (e.entityTags().contains(MobFactory.TAG_ELITE)) {
            type += "+elite";
        }
        return type;
    }

    private static String path(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "hand" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static String damageType(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
    }

    /**
     * Where a player's damage came from: their weapon in melee, a bow or crossbow, a tower, the
     * companions, a spell, a drone, or another kind of damage (explosions, fire …).
     */
    static String sourceOf(ServerPlayer player, DamageSource source, boolean companion) {
        if (companion) {
            return Companions.spellHit() ? "zauber" : "begleiter";
        }
        if (Drones.isDrone(player)) {
            return "drohne";
        }
        if (ArrowRain.hitting()) {
            return "faehigkeit:pfeilregen";   // the archers' arrow rain (Simon 30.10.)
        }
        Entity direct = source.getDirectEntity();
        if (direct != null && direct != player) {
            if (direct.entityTags().contains("nightsiege_turret")) {
                return direct.entityTags().contains(TrapService.TAG_MANNED) ? "turm:arrow_turret_bemannt" : "turm:arrow_turret";
            }
            if (direct instanceof AbstractArrow) {
                return "fern:" + path(player.getMainHandItem());
            }
            if (direct instanceof Projectile) {
                return "geschoss:" + BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getPath();
            }
            return "art:" + damageType(source);
        }
        String trap = TrapService.hittingKey(player);
        if (trap != null) {
            return "turm:" + trap;
        }
        String type = damageType(source);
        if ("player_attack".equals(type)) {
            return "nah:" + path(player.getMainHandItem());
        }
        return "art:" + type;
    }

    // ------------------------------------------------------------------ the night

    void beginNight(long gameTime, int number, int level, boolean test, String event, boolean special, String fragment,
                    String boss, BaseScan base, List<ServerPlayer> players, long siegeDay) {
        if (!on() || server == null) {
            night = null;
            return;
        }
        night = new NightFile();
        night.sim = simLabel == null ? "" : simLabel;
        night.version = version();
        night.build = build();
        night.start = now();
        night.test = test;
        night.nacht = number;
        night.stufe = level;
        night.ereignis = event == null ? "" : event;
        night.besondereNacht = special;
        night.fragment = fragment == null ? "" : fragment;
        night.boss = boss == null ? "" : boss;
        for (com.mki.nightsiege.core.CurseKind k : Curses.active()) {
            night.fluchsteine.add(k.id());   // the curse stones of the night (update 2)
        }
        if (base != null) {
            Basis b = new Basis();
            b.geschlossen = base.closed;
            b.breite = base.width();
            b.tiefe = base.depth();
            b.tore = base.gates.size();
            night.basis = b;
        }
        Heerlager seen = camps.remove(siegeDay);
        if (seen != null) {
            night.heerlager = seen;
        }
        camps.keySet().removeIf(day -> day < siegeDay);
        for (ServerPlayer p : players) {
            addPlayer(p);
        }
        nightStartTick = gameTime;
        wave = null;
        mobSince.clear();
        mobType.clear();
        hitBy.clear();
        lifeLeft.clear();
        machineOf.clear();
        Path dir = folder();
        nightPath = dir == null ? null : dir.resolve((test ? "testnacht_" : "night_") + LocalDateTime.now().format(FILE_TIME) + ".json");
        writeLater(nightPath, night);
    }

    void addPlayer(ServerPlayer player) {
        if (night != null && !night.spieler.containsKey(player.getScoreboardName())) {
            night.spieler.put(player.getScoreboardName(), describe(m, player));
        }
    }

    private int second(long gameTime) {
        return (int) Math.max(0, (gameTime - nightStartTick) / 20);
    }

    /** A new wave begins: the last one is closed, the file saved (a crash keeps it). */
    void waveStart(long gameTime, int number, int liveNow) {
        if (night == null) {
            return;
        }
        closeWave(gameTime, liveNow);
        wave = new Welle();
        wave.nummer = number;
        wave.startSekunde = second(gameTime);
        waveStartTick = gameTime;
        blocksAtWave = NightDamage.lostCount();
        night.wellen.add(wave);
        writeLater(nightPath, night);
    }

    private void closeWave(long gameTime, int liveNow) {
        if (wave == null) {
            return;
        }
        wave.dauerSekunden = (int) Math.max(0, (gameTime - waveStartTick) / 20);
        wave.uebrigAmEnde = liveNow;
        wave.bloeckeZerstoert = Math.max(0, NightDamage.lostCount() - blocksAtWave);
        wave.msptSchnitt = tickCount == 0 ? 0 : Math.round(tickSumMs / tickCount * 10.0) / 10.0;
        wave.msptMax = Math.round(tickMaxMs * 10.0) / 10.0;
        wave.ticksUeber50 = ticksOver50;
        wave.lebendMax = Math.max(liveMax, liveNow);
        wave.mobsMax = mobsMax;
        tickSumMs = 0;
        tickCount = 0;
        tickMaxMs = 0;
        ticksOver50 = 0;
        liveMax = 0;
        mobsMax = 0;
    }

    // the server's load in the running wave (30.10.)
    private double tickSumMs;
    private int tickCount;
    private double tickMaxMs;
    private int ticksOver50;
    private int liveMax;
    private int mobsMax;

    /** One server tick of a night: how long it took, the night's monsters alive, all mobs (-1: not counted). */
    void tickLoad(double ms, int live, int mobsAll) {
        if (night == null || wave == null) {
            return;
        }
        tickSumMs += ms;
        tickCount++;
        tickMaxMs = Math.max(tickMaxMs, ms);
        if (ms > 50.0) {
            ticksOver50++;
        }
        liveMax = Math.max(liveMax, live);
        if (mobsAll >= 0) {
            mobsMax = Math.max(mobsMax, mobsAll);
        }
    }

    private SpielerWelle pw(ServerPlayer player) {
        if (wave == null) {
            waveStart(player.level().getGameTime(), 0, 0);
        }
        addPlayer(player);
        return wave.spieler.computeIfAbsent(player.getScoreboardName(), k -> new SpielerWelle());
    }

    /** Once a second in the night: new monsters, drones, gunners in their towers. */
    void tickSecond(long gameTime, Set<UUID> liveMobs, Set<UUID> participants, net.minecraft.server.level.ServerLevel world,
                    TrapService traps) {
        if (night == null || world == null) {
            return;
        }
        for (UUID id : liveMobs) {
            if (mobSince.containsKey(id)) {
                continue;
            }
            Entity e = world.getEntity(id);
            if (e == null) {
                continue;
            }
            mobSince.put(id, gameTime);
            String type = typeOf(e);
            mobType.put(id, type);
            night.gegner.computeIfAbsent(type, k -> new Gegner()).gespawnt++;
            night.gespawnt++;
            if (wave != null) {
                wave.gespawnt++;
                wave.typen.merge(type, 1, Integer::sum);
            }
        }
        for (UUID id : participants) {
            ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
            if (p == null) {
                continue;
            }
            if (Drones.isDrone(p)) {
                pw(p).drohneSekunden++;
            }
            if (traps != null && traps.isSeated(p)) {
                pw(p).turmSekunden++;
                String kind = traps.seatKind(p);
                if (kind != null) {
                    tower(kind, p.getScoreboardName()).sekundenBemannt++;
                }
            }
        }
    }

    private Turm tower(String kind, String owner) {
        String base = kind.endsWith("_bemannt") ? kind.substring(0, kind.length() - "_bemannt".length()) : kind;
        Turm t = night.tuerme.computeIfAbsent(base + "|" + owner, k -> new Turm());
        t.art = base;
        t.besitzer = owner;
        return t;
    }

    /** Damage in the night: a defender's to a monster, a monster's (or the night's) to a defender. */
    void damage(LivingEntity victim, DamageSource source, float taken, boolean victimIsMob, boolean victimIsDefender) {
        if (night == null || taken <= 0) {
            return;
        }
        Entity hitterRaw = source.getEntity();
        boolean companion = Companions.isCompanion(hitterRaw);
        Entity hitter = companion ? Companions.ownerPlayer(hitterRaw) : hitterRaw;
        if (victimIsMob && hitter instanceof ServerPlayer attacker) {
            String from = sourceOf(attacker, source, companion);
            SpielerWelle w = pw(attacker);
            w.schaden += taken;
            w.schadenNachQuelle.merge(from, (double) taken, Double::sum);
            if ("drohne".equals(from)) {
                w.drohneSchaden += taken;
            }
            if (from.startsWith("turm:")) {
                Turm t = tower(from.substring(5), attacker.getScoreboardName());
                t.schaden += taken;
                if (from.endsWith("_bemannt")) {
                    t.schadenBemannt += taken;
                }
            }
            hitBy.computeIfAbsent(victim.getUUID(), k -> new HashSet<>()).add(attacker.getUUID());
        }
        if (victimIsMob) {
            lifeLeft.put(victim.getUUID(), victim.getHealth());
        }
        if (victimIsDefender && victim instanceof ServerPlayer defender) {
            String type;
            Maschine machine = SiegeMachines.landing == null ? null : machineOf.get(SiegeMachines.landing);
            if (hitterRaw != null && !(hitterRaw instanceof ServerPlayer)) {
                type = mobType.getOrDefault(hitterRaw.getUUID(), typeOf(hitterRaw));
            } else if (machine != null) {
                type = "maschine:" + machine.art;
                machine.schadenAnSpielern += taken;
            } else {
                type = "umgebung:" + damageType(source);
            }
            SpielerWelle w = pw(defender);
            w.erhalten += taken;
            w.erhaltenNachGegner.merge(type, (double) taken, Double::sum);
            if (hitterRaw != null && !(hitterRaw instanceof ServerPlayer)) {
                night.gegner.computeIfAbsent(type, k -> new Gegner()).schadenAnSpielern += taken;
            }
        }
    }

    /** A monster of the night died. */
    void mobDied(Mob mob, DamageSource source) {
        if (night == null) {
            return;
        }
        UUID id = mob.getUUID();
        String type = mobType.getOrDefault(id, typeOf(mob));
        Gegner g = night.gegner.computeIfAbsent(type, k -> new Gegner());
        g.getoetet++;
        Long since = mobSince.remove(id);
        if (since != null) {
            g.ueberlebtSekundenSumme += Math.max(0, (mob.level().getGameTime() - since) / 20.0);
        }
        Entity raw = source == null ? null : source.getEntity();
        boolean companion = Companions.isCompanion(raw);
        Entity killer = companion ? Companions.ownerPlayer(raw) : raw;
        Float left = lifeLeft.remove(id);
        double lastBlow = left != null ? left : mob.getMaxHealth();
        String by;
        if (killer instanceof ServerPlayer player) {
            String from = sourceOf(player, source, companion);
            SpielerWelle blow = pw(player);
            blow.schaden += lastBlow;
            blow.schadenNachQuelle.merge(from, lastBlow, Double::sum);
            if ("drohne".equals(from)) {
                blow.drohneSchaden += lastBlow;
            }
            Spieler s = night.spieler.get(player.getScoreboardName());
            String who = s == null ? "?" : s.unterklasse.isEmpty() ? s.klasse : s.unterklasse;
            if (from.startsWith("turm:")) {
                by = from;
                Turm t = tower(from.substring(5), player.getScoreboardName());
                t.schaden += lastBlow;
                if (from.endsWith("_bemannt")) {
                    t.schadenBemannt += lastBlow;
                }
                t.kills++;
                if (from.endsWith("_bemannt")) {
                    t.killsBemannt++;
                }
            } else if (companion) {
                by = "begleiter:" + who;
            } else if ("drohne".equals(from)) {
                by = "drohne";
            } else {
                by = "klasse:" + who;
            }
            pw(player).kills++;
            Set<UUID> helpers = hitBy.remove(id);
            if (helpers != null) {
                for (UUID helper : helpers) {
                    ServerPlayer h = server == null ? null : server.getPlayerList().getPlayer(helper);
                    if (h != null && !helper.equals(player.getUUID())) {
                        pw(h).assists++;
                    }
                }
            }
        } else {
            by = raw == null ? "umgebung:" + (source == null ? "?" : damageType(source)) : "sonstiges:" + typeOf(raw);
            hitBy.remove(id);
        }
        g.getoetetVon.merge(by, 1, Integer::sum);
    }

    /** A defender fell. */
    void playerFell(ServerPlayer player) {
        if (night != null) {
            pw(player).tode++;
            night.tode++;
        }
    }

    /** From the heal mixin: any heal, before it lands. */
    public static void onHeal(LivingEntity entity, float amount) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        try {
            SiegeManager manager = SiegeManager.get();
            if (manager != null) {
                manager.stats().healed(entity, amount);
            }
        } catch (RuntimeException e) {
            // The statistics never get in the way of a heal.
        }
    }

    void healGiven(ServerPlayer player, double amount) {
        if (night != null && amount > 0) {
            pw(player).heilungGegeben += amount;
        }
    }

    /** Any heal of a defender in the night (lifesteal, healers, potions, regeneration), as it lands. */
    void healed(LivingEntity entity, float amount) {
        if (night == null || amount <= 0 || !(entity instanceof ServerPlayer player) || !m.isDefending(player)) {
            return;
        }
        float room = Math.max(0f, player.getMaxHealth() - player.getHealth());
        float landed = Math.min(amount, room);
        if (landed > 0) {
            SpielerWelle w = pw(player);
            w.heilungErhalten += landed;
            if (stealing) {
                w.lebensraub += landed;
            }
        }
    }

    /** Set by the combat effects while a life-steal heal lands (Simon 30.10.: "hast du Werte der Lifesteal-Heilung"). */
    static boolean stealing;

    /** A monster's hit on a defender before any reduction, blocked or not (what a tank holds off). */
    void incoming(ServerPlayer player, DamageSource source, float base, boolean blocked) {
        if (night == null || base <= 0) {
            return;
        }
        Entity hitter = source.getEntity();
        if (hitter == null || hitter instanceof ServerPlayer) {
            return;
        }
        SpielerWelle w = pw(player);
        w.abgefangen += base;
        w.treffer++;
        if (blocked) {
            w.geblockt++;
        }
    }

    /** An ability key or menu used in the night. */
    void ability(ServerPlayer player, String what) {
        if (night != null && m.isDefending(player)) {
            pw(player).faehigkeiten.merge(what, 1, Integer::sum);
        }
    }

    // ------------------------------------------------------------------ machines

    void machineSpawned(UUID hitbox, String kind, long gameTime) {
        if (night == null || hitbox == null) {
            return;
        }
        Maschine mm = new Maschine();
        mm.art = kind;
        mm.startSekunde = second(gameTime);
        machineOf.put(hitbox, mm);
        night.maschinen.add(mm);
        night.heerlager.maschinen = night.maschinen.size();
    }

    void machineShot(UUID hitbox) {
        Maschine mm = night == null ? null : machineOf.get(hitbox);
        if (mm != null) {
            mm.schuesse++;
        }
    }

    void machineBlocks(UUID hitbox, int blocks) {
        Maschine mm = night == null ? null : machineOf.get(hitbox);
        if (mm != null && blocks > 0) {
            mm.bloecke += blocks;
        }
    }

    void machineGone(UUID hitbox, ServerPlayer by, long gameTime, boolean destroyed) {
        Maschine mm = night == null ? null : machineOf.get(hitbox);
        if (mm != null && mm.endeSekunde < 0) {
            mm.endeSekunde = second(gameTime);
            mm.zerstoert = destroyed;
            mm.sabotiertVon = by == null ? "" : by.getScoreboardName();
        }
    }

    // ------------------------------------------------------------------ the end of the night

    void endNight(long gameTime, String result, int killGoal, int kills, Set<UUID> liveMobs) {
        if (night == null) {
            return;
        }
        closeWave(gameTime, liveMobs == null ? 0 : liveMobs.size());
        night.ende = now();
        night.abgeschlossen = true;
        night.ergebnis = result;
        night.dauerSekunden = second(gameTime);
        night.killZiel = killGoal;
        night.kills = kills;
        for (Gegner g : night.gegner.values()) {
            g.ueberlebtSekundenSchnitt = g.getoetet == 0 ? 0 : Math.round(g.ueberlebtSekundenSumme / g.getoetet * 10.0) / 10.0;
        }
        for (Maschine mm : night.maschinen) {
            if (mm.endeSekunde < 0) {
                mm.endeSekunde = night.dauerSekunden;
            }
        }
        writeLater(nightPath, night);
        NightSiegeMod.LOGGER.info("[Night Siege] Statistik der Nacht geschrieben: {}", nightPath == null ? "-" : nightPath.getFileName());
        lastFinished = night;
        lastPath = nightPath;
        night = null;
        wave = null;
        mobSince.clear();
        mobType.clear();
        hitBy.clear();
        lifeLeft.clear();
        machineOf.clear();
    }

    /**
     * The 1-click feedback from the night statistics window (balance tool, 29 October 2026):
     * "leicht", "passt" or "schwer", or "best:<subclass>". Only for the last real night and only
     * from its defenders; a second click replaces the first. The night's file is written again.
     */
    void feedback(ServerPlayer player, String argument) {
        NightFile n = lastFinished;
        Path path = lastPath;
        if (n == null || path == null || n.test || argument == null) {
            return;
        }
        String name = player.getScoreboardName();
        if (!n.spieler.containsKey(name)) {
            return;
        }
        if (argument.startsWith("best:")) {
            String sub = argument.substring(5);
            if (sub.isBlank() || sub.length() > 40) {
                n.staerksteKlasse.remove(name);
            } else {
                n.staerksteKlasse.put(name, sub);
            }
        } else if (argument.equals("leicht") || argument.equals("passt") || argument.equals("schwer")) {
            n.bewertung.put(name, argument);
        } else {
            return;
        }
        writeLater(path, n);
    }

    // ------------------------------------------------------------------ the war camp

    private Heerlager campOf(long siegeDay) {
        return camps.computeIfAbsent(siegeDay, d -> new Heerlager());
    }

    void campBuilt(long siegeDay) {
        campOf(siegeDay).erschienen = true;
    }

    void campDestroyed(long siegeDay) {
        campOf(siegeDay).zerstoert = true;
        campAttemptEnd("zerstoert");
    }

    void campWon(long siegeDay) {
        campOf(siegeDay).lagerSiegte = true;
        campAttemptEnd("lager_siegt");
    }

    /** A fighter steps into the camp: the attempt begins with the first one. */
    void campJoin(ServerPlayer player, int level, long gameTime) {
        if (!on()) {
            return;
        }
        if (camp == null) {
            camp = new AttemptFile();
            camp.art = "heerlager";
            camp.version = version();
            camp.build = build();
            camp.start = now();
            camp.typ = "heerlager";
            camp.stufe = level;
            campStartTick = gameTime;
        }
        String name = player.getScoreboardName();
        if (camp.spieler.stream().noneMatch(s -> s.name.equals(name))) {
            camp.spieler.add(describe(m, player));
        }
    }

    void campFell() {
        if (camp != null) {
            camp.tode++;
        }
    }

    void campAttemptEnd(String result) {
        if (camp == null || server == null) {
            camp = null;
            return;
        }
        camp.ende = now();
        camp.ergebnis = result;
        camp.dauerSekunden = (int) Math.max(0, (server.overworld().getGameTime() - campStartTick) / 20);
        Path dir = folder();
        writeLater(dir == null ? null : dir.resolve("heerlager_" + LocalDateTime.now().format(FILE_TIME) + ".json"), camp);
        camp = null;
    }

    // ------------------------------------------------------------------ dungeons

    void dungeonJoin(ServerPlayer player, String kind, int level, int rooms, int attempt, long gameTime) {
        if (!on()) {
            return;
        }
        if (dungeon == null) {
            dungeon = new AttemptFile();
            dungeon.art = "dungeon";
            dungeon.version = version();
            dungeon.build = build();
            dungeon.start = now();
            dungeon.typ = kind;
            dungeon.stufe = level;
            dungeon.raeume = rooms;
            dungeon.wellen = rooms;
            dungeon.versuch = attempt;
            dungeonStartTick = gameTime;
        }
        String name = player.getScoreboardName();
        if (dungeon.spieler.stream().noneMatch(s -> s.name.equals(name))) {
            dungeon.spieler.add(describe(m, player));
        }
    }

    void dungeonProgress(int roomsCleared, int wavesReached, boolean champion) {
        if (dungeon != null) {
            dungeon.raeumeGeraeumt = Math.max(dungeon.raeumeGeraeumt, roomsCleared);
            dungeon.wellenErreicht = Math.max(dungeon.wellenErreicht, wavesReached);
            dungeon.championErreicht |= champion;
        }
    }

    void dungeonFell() {
        if (dungeon != null) {
            dungeon.tode++;
        }
    }

    /** Damage inside a dungeon fight (to its monsters, from them). */
    void dungeonDamage(boolean dealt, float amount) {
        if (dungeon != null && amount > 0) {
            if (dealt) {
                dungeon.schadenAusgeteilt += amount;
            } else {
                dungeon.schadenErhalten += amount;
            }
        }
    }

    void dungeonEnd(String result, List<String> reward) {
        if (dungeon == null || server == null) {
            dungeon = null;
            return;
        }
        dungeon.ende = now();
        dungeon.ergebnis = result;
        dungeon.dauerSekunden = (int) Math.max(0, (server.overworld().getGameTime() - dungeonStartTick) / 20);
        if (reward != null) {
            dungeon.belohnung.addAll(reward);
        }
        dungeon.schadenAusgeteilt = Math.round(dungeon.schadenAusgeteilt * 10.0) / 10.0;
        dungeon.schadenErhalten = Math.round(dungeon.schadenErhalten * 10.0) / 10.0;
        Path dir = folder();
        writeLater(dir == null ? null : dir.resolve("dungeon_" + LocalDateTime.now().format(FILE_TIME) + ".json"), dungeon);
        dungeon = null;
    }

    // ------------------------------------------------------------------ crates

    void crate(ServerPlayer player, String clazz, String crateKind, int level, CrateRoll roll) {
        if (!on() || roll == null) {
            return;
        }
        CrateLine line = new CrateLine();
        line.zeit = now();
        line.version = version();
        line.build = build();
        line.spieler = player.getScoreboardName();
        line.klasse = clazz == null ? "" : clazz.toLowerCase(Locale.ROOT);
        line.kistenArt = crateKind == null ? "" : crateKind;
        line.gerollteArt = roll.kind == null ? "" : roll.kind;
        line.stufe = level;
        line.gewinn = roll.gain == null ? "" : roll.gain;
        line.seltenheit = roll.rarity == null ? "" : roll.rarity;
        line.doppelt = roll.duplicate;
        line.gewinnStufe = roll.stage;
        line.punkte = roll.points;
        line.glueck = roll.lucky;
        line.infernit = roll.infernite;
        Path dir = folder();
        appendLater(dir == null ? null : dir.resolve("kisten.jsonl"), line);
    }

    // ------------------------------------------------------------------ tests

    /** Tests: the night being recorded, or null. */
    public NightFile testNight() {
        return night;
    }

    /** Tests: waits until every file is on the disk (at most 5 s). */
    public static void testFlush() {
        try {
            WRITER.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            // nothing to wait for
        }
    }

    /** Tests: the folder of the files. */
    public Path testFolder() {
        return folder();
    }
}
