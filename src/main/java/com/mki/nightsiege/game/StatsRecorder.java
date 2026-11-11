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
        /** A night of the playtest mode (05.11.: the Ruhmeshalle takes these too, marked as playtest). */
        public boolean playtest;
        /** Tor 7.3: a night of the playtest (Spieltest), file spieltest_<time>.json; real test nights stay testnacht_. */
        public boolean spieltest;
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
        /** Tor 9.1 (i): seconds of the playtest's build-up before this night ("Bereit", 2 min at most); 0 otherwise. */
        public int aufbauSekunden;
        public int killZiel;
        public int kills;
        public int gespawnt;
        public int tode;
        /** Tor 7.3: second and wave of the night's first death (-1 = nobody fell). */
        public int ersterTodSekunde = -1;
        public int ersterTodWelle = -1;
        public Basis basis;
        public Map<String, Spieler> spieler = new LinkedHashMap<>();
        public Heerlager heerlager = new Heerlager();
        public List<Maschine> maschinen = new ArrayList<>();
        public List<Welle> wellen = new ArrayList<>();
        public Map<String, Gegner> gegner = new LinkedHashMap<>();
        public Map<String, Turm> tuerme = new LinkedHashMap<>();
        /** Tor 6.4: 95th percentile of the server's tick time over the whole night (ms; the live cap's gauge). */
        public double msptP95;
        /** Tor 6.4: the most monsters of the night alive at once over the whole night. */
        public int lebendMax;
        /** 1-click feedback after the night (balance tool, 29 October 2026): player → leicht / passt / schwer. */
        public Map<String, String> bewertung = new LinkedHashMap<>();
        /** Optional with it: player → the subclass they found strongest tonight. */
        public Map<String, String> staerksteKlasse = new LinkedHashMap<>();
        /**
         * The curiosities of the night (Ruhmeshalle, Simon 30.10.): player → key → value. Counts
         * ("creeper", "kletterer", "halbesHerz", "geschosse", "traenke", "retter", "nemesis",
         * "bossLetzterTreffer") and the night's bests ("weitesterTreffer" in blocks, "blutrausch"
         * kills in 10 s, "mehrfach" kills with one blow, "ohneTreffer" seconds, "letzterMann" 1).
         */
        public Map<String, Map<String, Double>> kuriositaeten = new LinkedHashMap<>();
        /** Who killed the defenders: player → monster type → times. */
        public Map<String, Map<String, Integer>> todDurch = new LinkedHashMap<>();
        /** Bosses and wardens of the night that fell. */
        public int bosseBesiegt;
        /** What each defender killed: player → monster type → kills. */
        public Map<String, Map<String, Integer>> opfer = new LinkedHashMap<>();
        /** The smart night (Simon 05.11. 20:26): how it turned itself up and down. */
        public SchlaueNacht schlaueNacht = new SchlaueNacht();
    }

    /** The smart night of one night: whether it ran, its steps and what it sent. */
    public static final class SchlaueNacht {
        public boolean an;
        public int hoechste;
        public int tiefste;
        public int ende;
        public int eliten;
        public int extraSondermonster;
        public int pausen;
        public List<Schritt> schritte = new ArrayList<>();
        /** Tor 6.6: the smart night steered tonight (false = it only measured). */
        public boolean steuert;
        /** Tor 6.6: decided early (lost in the first third, or pressure never above 0.2). */
        public boolean fruehEntschieden;
        /** Share of the night's time that ran (0..1) and the highest pressure (-1 = no look). */
        public double zeitAnteil;
        public double spitzenDruck = -1;
        /** Tor 6.6, the outer loop: offset on the normal monsters' life before and after this night (real nights). */
        public boolean schleifeGezaehlt;
        public boolean schleifeAn;
        public double schleifeVorher;
        public double schleifeNachher;
    }

    /** One step of the smart night: when, to which step, and what it saw (share of life, stress). */
    public static final class Schritt {
        public int sekunde;
        public int stufe;
        public String grund = "";
        public double druck;
        public double leben;
        public double minLeben;
        public int tode;
        public int kills;
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
        /** Second of the night of this defender's first death, -1 = none (Tor 6.0, to calibrate the sim). */
        public int ersterTodSekunde = -1;
        /** The kit of the night: the class of the played profile. */
        public String kit = "";
        /** Tor 7.3 (ziele 9.1 a): the learned skill nodes of the played profile, node id -> level. */
        public Map<String, Integer> knoten = new LinkedHashMap<>();
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
        /** Tor 6.4: 95th percentile of the tick time in this wave (ms). */
        public double msptP95;
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
        /**
         * The life really lost (05.11.): after armour, shield, reductions and absorption. "erhalten" is
         * the hit before armour (Fabric's damage taken), so it was far above the healing.
         */
        public double verloren;
        /** What absorption hearts took off (golden apples, gains). */
        public double absorbiert;
        /** Life steal that the coupling to the damage taken held back (StealPool, 05.11.). */
        public double lebensraubGekappt;
        /** Life given back without a heal: the night totem, the second chance, refills (05.11.). */
        public double heilungSonst;
        public int drohneSekunden;
        public double drohneSchaden;
        public Map<String, Integer> faehigkeiten = new LinkedHashMap<>();
        /** Tor 4b (Auflage 5): uses per key, ABILITY_1 to ABILITY_4, ULT (X), ULT2 (R). */
        public Map<String, Integer> tasten = new LinkedHashMap<>();
        public int turmSekunden;
        /** Tor 4b (Auflage 5): seconds alive on foot (not seated in a tower, not a drone). */
        public int fussSekunden;
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
        /** Tor 7.3: an attempt during the playtest (Spieltest). */
        public boolean spieltest;
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
        /** 09.11. (night rules in the dungeon): abilities per player as in the night, name -> what -> uses. */
        public Map<String, Map<String, Integer>> faehigkeiten = new LinkedHashMap<>();
        /** The same per key (Q/E/F/C/X/R, gewinn:...), name -> key -> uses. */
        public Map<String, Map<String, Integer>> tasten = new LinkedHashMap<>();
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
    /** Curiosities: when each defender's last kills came (game time), kills in one tick, the last hit taken. */
    private final Map<UUID, java.util.ArrayDeque<Long>> killTimes = new HashMap<>();
    private final Map<UUID, long[]> killTick = new HashMap<>();
    private final Map<UUID, Long> lastHitAt = new HashMap<>();
    private final Map<UUID, Long> halfHeartAt = new HashMap<>();
    private final Map<UUID, Long> rescueAt = new HashMap<>();

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
        // The same safe write as the save (temp file, to the disk, one rename; 31.10.), without a .bak.
        WRITER.execute(() -> Json.writeNow(path, json, false));
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
            if (!s.profil.isEmpty()) {
                for (com.mki.nightsiege.core.SkillNode node : tree.ofClass(PlayerSkills.classOfKey(s.profil))) {
                    int level = skills.levelInProfile(s.profil, node.id());
                    if (level > 0) {
                        s.knoten.put(node.id(), level);
                    }
                }
            }
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
        String warrior = WarriorSpirit.statsSource();
        if (warrior != null) {
            return warrior;   // the warriors' finishers and F abilities (Simon 05.11.): "finisher:spalter" ...
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

    /** A step of the smart night (05.11.). */
    void smartStep(com.mki.nightsiege.core.SmartNight.Look look) {
        if (night == null || look == null) {
            return;
        }
        Schritt st = new Schritt();
        st.sekunde = (int) Math.max(0, (look.tick() - nightStartTick) / 20);
        st.stufe = look.step();
        st.grund = look.why();
        st.druck = round2(look.stress());
        st.leben = round2(look.life());
        st.minLeben = round2(look.lowest());
        st.tode = look.deaths();
        st.kills = look.kills();
        night.schlaueNacht.schritte.add(st);
    }

    /** Tor 6.6: the night's measure for the pair comparison, with and without the smart night. */
    void smartMeasure(boolean steering, boolean early, double elapsed, double peak) {
        if (night == null) {
            return;
        }
        SchlaueNacht s = night.schlaueNacht;
        s.steuert = steering;
        s.fruehEntschieden = early;
        s.zeitAnteil = round2(Math.max(0.0, Math.min(1.0, elapsed)));
        s.spitzenDruck = peak < 0 ? -1 : round2(peak);
    }

    /** Tor 6.6: the outer loop's offset before and after this real night. */
    void outerLoop(double before, double after, boolean on) {
        if (night == null) {
            return;
        }
        SchlaueNacht s = night.schlaueNacht;
        s.schleifeGezaehlt = true;
        s.schleifeAn = on;
        s.schleifeVorher = Math.round(before * 1000.0) / 1000.0;
        s.schleifeNachher = Math.round(after * 1000.0) / 1000.0;
    }

    /** The smart night at the end of the night. */
    void smartEnd(com.mki.nightsiege.core.SmartNight smart, int elites, int extras) {
        if (night == null || smart == null) {
            return;
        }
        SchlaueNacht s = night.schlaueNacht;
        s.an = smart.on();
        s.hoechste = smart.highest();
        s.tiefste = smart.lowest();
        s.ende = smart.step();
        s.eliten = elites;
        s.extraSondermonster = extras;
        s.pausen = smart.pauses();
    }

    private static double round2(double v) {
        return Double.isNaN(v) ? 0.0 : Math.round(v * 100.0) / 100.0;
    }

    void beginNight(long gameTime, int number, int level, boolean test, String event, boolean special, String fragment,
                    String boss, BaseScan base, List<ServerPlayer> players, long siegeDay) {
        if (!on() || server == null) {
            night = null;
            return;
        }
        night = new NightFile();
        nightLoad.clear();
        night.sim = simLabel == null ? "" : simLabel;
        night.version = version();
        night.build = build();
        night.start = now();
        night.test = test;
        night.playtest = test && m.inPlaytest() && !m.isWarRound();
        night.spieltest = night.playtest;
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
        clearCurios();
        mobSince.clear();
        mobType.clear();
        hitBy.clear();
        lifeLeft.clear();
        machineOf.clear();
        Path dir = folder();
        nightPath = dir == null ? null : dir.resolve(nightFileName(test, night.spieltest, LocalDateTime.now()));
        writeLater(nightPath, night);
    }

    /** Tor 7.3: the night's file name: night_, testnacht_ (/nightsiege test) or spieltest_ (the playtest). */
    static String nightFileName(boolean test, boolean spieltest, LocalDateTime time) {
        String prefix = spieltest ? "spieltest_" : test ? "testnacht_" : "night_";
        return prefix + time.format(FILE_TIME) + ".json";
    }

    /** Tor 9.1 (i): the build-up before the night that just began. */
    void setupSeconds(int seconds) {
        if (night != null) {
            night.aufbauSekunden = Math.max(0, seconds);
            writeLater(nightPath, night);
        }
    }

    /** The build-up of the running night (for the tests), or -1 without a night file. */
    public int setupSecondsForTests() {
        return night == null ? -1 : night.aufbauSekunden;
    }

    void addPlayer(ServerPlayer player) {
        lastHitAt.putIfAbsent(player.getUUID(), player.level().getGameTime());
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
        wave.msptP95 = Math.round(waveLoad.percentile(0.95) * 10.0) / 10.0;
        night.lebendMax = Math.max(night.lebendMax, Math.max(liveMax, liveNow));
        waveLoad.clear();
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
    private final com.mki.nightsiege.core.TickLoad waveLoad = new com.mki.nightsiege.core.TickLoad();
    private final com.mki.nightsiege.core.TickLoad nightLoad = new com.mki.nightsiege.core.TickLoad();
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
        waveLoad.add(ms);
        nightLoad.add(ms);
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
            } else if (p.isAlive() && !p.isSpectator() && (traps == null || !traps.isSeated(p))) {
                pw(p).fussSekunden++;
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
            if (!companion && (from.startsWith("nah:") || from.startsWith("fern:") || from.startsWith("geschoss:"))) {
                kurMax(attacker, "weitesterTreffer", Math.round(attacker.distanceTo(victim) * 10.0) / 10.0);
            }
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
            long now = defender.level().getGameTime();
            Long last = lastHitAt.put(defender.getUUID(), now);
            if (last != null) {
                kurMax(defender, "ohneTreffer", Math.floor((now - last) / 20.0));
            }
            if (defender.isAlive() && defender.getHealth() > 0f && defender.getHealth() <= 1.0f
                    && now - halfHeartAt.getOrDefault(defender.getUUID(), Long.MIN_VALUE / 2) > 100) {
                halfHeartAt.put(defender.getUUID(), now);
                kurAdd(defender, "halbesHerz", 1);
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
            killCurios(player, mob);
            night.opfer.computeIfAbsent(player.getScoreboardName(), k -> new LinkedHashMap<>()).merge(type, 1, Integer::sum);
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
        if (BossService.isBoss(mob) || mob.entityTags().contains(WardenNight.TAG)) {
            night.bosseBesiegt++;
        }
    }

    // ------------------------------------------------------------------ curiosities (Ruhmeshalle, 31.10.)

    private Map<String, Double> kur(ServerPlayer player) {
        return night.kuriositaeten.computeIfAbsent(player.getScoreboardName(), k -> new LinkedHashMap<>());
    }

    private void kurAdd(ServerPlayer player, String key, double value) {
        if (night != null && player != null) {
            kur(player).merge(key, value, Double::sum);
        }
    }

    private void kurMax(ServerPlayer player, String key, double value) {
        if (night != null && player != null) {
            kur(player).merge(key, value, Math::max);
        }
    }

    /** A mentor night (Tor 6.7): stood in a night with a latecomer catching up. */
    public void mentor(ServerPlayer player) {
        kurAdd(player, "mentor", 1);
    }

    private void clearCurios() {
        killTimes.clear();
        killTick.clear();
        lastHitAt.clear();
        halfHeartAt.clear();
        rescueAt.clear();
    }

    /** A kill of the night: frenzy (kills in 10 s), kills with one blow, creepers, climbers, bosses, nemeses. */
    private void killCurios(ServerPlayer player, Mob mob) {
        long now = mob.level().getGameTime();
        java.util.ArrayDeque<Long> times = killTimes.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > 200) {
            times.pollFirst();
        }
        kurMax(player, "blutrausch", times.size());
        long[] same = killTick.computeIfAbsent(player.getUUID(), k -> new long[] {Long.MIN_VALUE, 0});
        if (same[0] == now) {
            same[1]++;
        } else {
            same[0] = now;
            same[1] = 1;
        }
        kurMax(player, "mehrfach", same[1]);
        if (mob instanceof net.minecraft.world.entity.monster.Creeper) {
            kurAdd(player, "creeper", 1);
        }
        if (mob.onClimbable()) {
            kurAdd(player, "kletterer", 1);
        }
        if (BossService.isBoss(mob) || mob.entityTags().contains(WardenNight.TAG)) {
            kurAdd(player, "bossLetzterTreffer", 1);
        }
        if (mob.entityTags().contains(Nemesis.TAG)) {
            kurAdd(player, "nemesis", 1);
        }
    }

    /** A defender was killed: by what (for the arch-enemy of the Ruhmeshalle). */
    void playerKilled(ServerPlayer player, DamageSource source) {
        if (night == null) {
            return;
        }
        Entity raw = source == null ? null : source.getEntity();
        String type = raw != null && !(raw instanceof ServerPlayer)
                ? mobType.getOrDefault(raw.getUUID(), typeOf(raw))
                : "umgebung:" + (source == null ? "?" : damageType(source));
        night.todDurch.computeIfAbsent(player.getScoreboardName(), k -> new LinkedHashMap<>()).merge(type, 1, Integer::sum);
    }

    /** The last one standing of a group of at least two. */
    void lastStanding(ServerPlayer player) {
        kurMax(player, "letzterMann", 1);
    }

    /** A shot or a throw of a defender came into the world (arrows, bolts, tridents, potions). */
    void projectile(Entity entity) {
        if (night == null || !(entity instanceof net.minecraft.world.entity.projectile.Projectile proj)
                || !(proj.getOwner() instanceof ServerPlayer owner) || entity.getDeltaMovement().lengthSqr() < 0.01) {
            return;
        }
        if (!night.spieler.containsKey(owner.getScoreboardName())) {
            return;
        }
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        if (path.contains("potion")) {
            kurAdd(owner, "traenke", 1);
        } else if (path.contains("arrow") || path.contains("trident") || path.contains("bolt")) {
            kurAdd(owner, "geschosse", 1);
        }
    }

    /** A healer healed a teammate who had less than three hearts left (once per 10 s and teammate). */
    public static void rescue(ServerPlayer healer, ServerPlayer target) {
        try {
            SiegeManager manager = SiegeManager.get();
            if (manager == null || healer == null || target == null || healer == target) {
                return;
            }
            StatsRecorder r = manager.stats();
            if (r.night == null) {
                return;
            }
            long now = target.level().getGameTime();
            if (now - r.rescueAt.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2) > 200) {
                r.rescueAt.put(target.getUUID(), now);
                r.kurAdd(healer, "retter", 1);
            }
        } catch (RuntimeException e) {
            // The statistics never get in the way of a heal.
        }
    }

    /** A defender fell. */
    void playerFell(ServerPlayer player) {
        Long last = lastHitAt.remove(player.getUUID());
        if (night != null && last != null) {
            kurMax(player, "ohneTreffer", Math.floor((player.level().getGameTime() - last) / 20.0));
        }
        if (night != null) {
            pw(player).tode++;
            night.tode++;
            Spieler first = night.spieler.get(player.getScoreboardName());
            if (first != null && first.ersterTodSekunde < 0) {
                first.ersterTodSekunde = second(player.level().getGameTime());   // per player (6.0)
            }
            if (night.ersterTodSekunde < 0) {
                night.ersterTodSekunde = second(player.level().getGameTime());   // the night's first death (7.3)
                night.ersterTodWelle = wave == null ? 0 : wave.nummer;
            }
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

    /** From the hurt mixin (via StealPool): the life a defender really lost, and what absorption took. */
    static void onLost(ServerPlayer player, DamageSource source, float lost, float absorbed) {
        SiegeManager m = SiegeManager.get();
        if (m == null) {
            return;
        }
        StatsRecorder r = m.stats();
        if (r.night == null || !m.isDefending(player)) {
            return;
        }
        SpielerWelle w = r.pw(player);
        w.verloren += lost;
        w.absorbiert += absorbed;
    }

    /** A life-steal heal was asked for; what the coupling held back is counted (only of the missing life). */
    static void onStealCut(ServerPlayer player, float wanted, float allowed) {
        try {
            SiegeManager m = SiegeManager.get();
            if (m == null || m.stats().night == null || !m.isDefending(player)) {
                return;
            }
            double room = Math.max(0.0, player.getMaxHealth() - player.getHealth());
            double cut = Math.min(wanted, room) - allowed;
            if (cut > 1.0e-4) {
                m.stats().pw(player).lebensraubGekappt += cut;
            }
        } catch (RuntimeException e) {
            // The statistics never get in the way of a heal.
        }
    }

    /**
     * Tests: this night's sums for a player: erhalten, verloren, absorbiert, heilungErhalten, lebensraub,
     * lebensraubGekappt, heilungSonst (null without a night).
     */
    public double[] testTotals(ServerPlayer player) {
        if (night == null) {
            return null;
        }
        double[] out = new double[7];
        List<Welle> all = new ArrayList<>(night.wellen);
        if (wave != null && !all.contains(wave)) {
            all.add(wave);
        }
        for (Welle w : all) {
            SpielerWelle p = w.spieler.get(player.getScoreboardName());
            if (p == null) {
                continue;
            }
            out[0] += p.erhalten;
            out[1] += p.verloren;
            out[2] += p.absorbiert;
            out[3] += p.heilungErhalten;
            out[4] += p.lebensraub;
            out[5] += p.lebensraubGekappt;
            out[6] += p.heilungSonst;
        }
        return out;
    }

    /** Tests (Tor 4c.1): this night's uses per key of a player ("tasten"), 0 for a key not used. */
    public int testKeyUses(ServerPlayer player, String key) {
        if (night == null) {
            return 0;
        }
        List<Welle> all = new ArrayList<>(night.wellen);
        if (wave != null && !all.contains(wave)) {
            all.add(wave);
        }
        int sum = 0;
        for (Welle w : all) {
            SpielerWelle p = w.spieler.get(player.getScoreboardName());
            sum += p == null ? 0 : p.tasten.getOrDefault(key, 0);
        }
        return sum;
    }

    /** Depth of heal() on this thread (the heal mixin): its own setHealth is no extra heal. */
    public static final ThreadLocal<int[]> HEALING = ThreadLocal.withInitial(() -> new int[1]);

    /** From the setHealth mixin: life given back without heal() (totem, second chance, refill). */
    public static void onDirectHeal(LivingEntity entity, float amount) {
        if (amount <= 0f || !(entity instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        try {
            SiegeManager m = SiegeManager.get();
            if (m == null || m.stats().night == null || !m.isDefending(player)) {
                return;
            }
            m.stats().pw(player).heilungSonst += amount;
        } catch (RuntimeException e) {
            // The statistics never get in the way of a heal.
        }
    }

    /**
     * A monster's hit on a defender before any reduction, blocked or not (what a tank holds off). The
     * amount is the hit before the defender's own cuts (30.10.: {@code base} already had the subclass
     * damage taken, the bastion and the Wächter-Schutz in it, so the bulwark looked as if it held less).
     */
    void incoming(ServerPlayer player, DamageSource source, float base, boolean blocked) {
        float raw = GuardianEffects.rawHit(player.getUUID(), base);
        if (night == null || base <= 0) {
            return;
        }
        Entity hitter = source.getEntity();
        if (hitter == null || hitter instanceof ServerPlayer) {
            return;
        }
        SpielerWelle w = pw(player);
        w.abgefangen += raw;
        w.treffer++;
        if (blocked) {
            w.geblockt++;
        }
    }

    /** An ability key or menu used in the night. */
    void ability(ServerPlayer player, String what) {
        ability(player, what, null);
    }

    /** The same, also counted on its key (Tor 4b, Auflage 5); key null: only the old field. */
    void ability(ServerPlayer player, String what, String key) {
        if (night != null && m.isDefending(player)) {
            SpielerWelle w = pw(player);
            w.faehigkeiten.merge(what, 1, Integer::sum);
            if (key != null) {
                w.tasten.merge(key, 1, Integer::sum);
            }
        }
        AttemptFile f = attemptOf(player);
        if (f != null) {   // a dungeon or camp fight: the same counts in its file (09.11.)
            String name = player.getScoreboardName();
            f.faehigkeiten.computeIfAbsent(name, k -> new LinkedHashMap<>()).merge(what, 1, Integer::sum);
            if (key != null) {
                f.tasten.computeIfAbsent(name, k -> new LinkedHashMap<>()).merge(key, 1, Integer::sum);
            }
        }
    }

    private AttemptFile attemptOf(ServerPlayer player) {
        return m.expeditions().isFighter(player) ? dungeon : m.camp().isFighter(player) ? camp : null;
    }

    /** Tests (09.11.): uses per key of a player in the running dungeon or camp attempt, 0 for none. */
    public int testAttemptKeyUses(ServerPlayer player, String key) {
        AttemptFile f = attemptOf(player);
        Map<String, Integer> keys = f == null ? null : f.tasten.get(player.getScoreboardName());
        return keys == null ? 0 : keys.getOrDefault(key, 0);
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

    /** Ends the night's protocol; returns the finished night (null without one). */
    NightFile endNight(long gameTime, String result, int killGoal, int kills, Set<UUID> liveMobs) {
        if (night == null) {
            return null;
        }
        closeWave(gameTime, liveMobs == null ? 0 : liveMobs.size());
        night.ende = now();
        night.msptP95 = Math.round(nightLoad.percentile(0.95) * 10.0) / 10.0;
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
        for (Map.Entry<UUID, Long> e : lastHitAt.entrySet()) {
            ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                kurMax(p, "ohneTreffer", Math.floor((gameTime - e.getValue()) / 20.0));
            }
        }
        clearCurios();
        NightFile done = night;
        lastFinished = night;
        lastPath = nightPath;
        night = null;
        wave = null;
        mobSince.clear();
        mobType.clear();
        hitBy.clear();
        lifeLeft.clear();
        machineOf.clear();
        return done;
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
            camp.spieltest = m.inPlaytest();
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
        AttemptFile doneCamp = camp;
        camp = null;
        {   // the playtest counts too since 05.11. (Simon: "das soll auch die aus dem playtest übernehmen")
            HallService.addAttempt(m, server, doneCamp, true);   // the Ruhmeshalle (31.10.)
        }
    }

    // ------------------------------------------------------------------ the war leader's round

    /** One file per war leader's round (Kriegsführer, 29 October 2026): purchases, gold, orders, result. */
    void warRound(WarLeader.RoundFile round) {
        if (!on() || round == null) {
            return;
        }
        Path dir = folder();
        writeLater(dir == null ? null : dir.resolve("kriegsfuehrer_" + LocalDateTime.now().format(FILE_TIME) + ".json"), round);
    }

    /** A run of the Letzte Bastion (Update 7): one file, every wave with its kills, deaths and damage. */
    void bastionRun(Bastion.RunFile run) {
        if (!on() || run == null) {
            return;
        }
        Path dir = folder();
        writeLater(dir == null ? null : dir.resolve("bastion_" + LocalDateTime.now().format(FILE_TIME) + ".json"), run);
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
            dungeon.spieltest = m.inPlaytest();
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
        AttemptFile doneDungeon = dungeon;
        dungeon = null;
        {   // the playtest counts too since 05.11. (Simon: "das soll auch die aus dem playtest übernehmen")
            HallService.addAttempt(m, server, doneDungeon, true);   // the Ruhmeshalle (31.10.)
        }
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
        {   // the playtest counts too since 05.11. (Simon: "das soll auch die aus dem playtest übernehmen")
            HallService.addCrate(m, server, player.getUUID().toString(), player.getScoreboardName(), line.seltenheit, true);
        }
    }

    /** The logger's folder (the Ruhmeshalle reads the nights before it from there once). */
    Path statsFolder() {
        return folder();
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
