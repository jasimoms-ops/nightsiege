package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.HallBadges;
import com.mki.nightsiege.core.HallOfFame;
import com.mki.nightsiege.core.HallView;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The Ruhmeshalle on the server (Simon 30.10., concept claude/night-siege-ruhmeshalle-konzept.md):
 * fills {@link HallOfFame} from the statistics logger's nights, dungeon and war camp attempts and
 * crates, reads the nights before it in once, hands out the badges (gold: a message for everybody,
 * Simon 23:55) and sends the window its data.
 */
public final class HallService {

    /** How many nights a hero's page shows. */
    static final int RECENT = 10;
    /** How many monsters the page lists per hero (victims, killers, damage taken). */
    static final int TOP = 8;

    private HallService() {
    }

    private static HallOfFame hall(SiegeManager m) {
        if (m.save().hall == null) {
            m.save().hall = new HallOfFame();
        }
        return m.save().hall;
    }

    // ------------------------------------------------------------------ nights

    /** A real night ended (or one of the nights before, from its file). */
    public static void addNight(SiegeManager m, MinecraftServer server, StatsRecorder.NightFile n, boolean live) {
        if (n == null || n.test || (n.sim != null && !n.sim.isEmpty())) {
            return;
        }
        boolean won = "gewonnen".equals(n.ergebnis);
        if (!won && !"ziel_verfehlt".equals(n.ergebnis) && !"verloren".equals(n.ergebnis)) {
            return;   // aborted or unfinished
        }
        HallOfFame hall = hall(m);
        String date = n.ende != null && n.ende.length() >= 10 ? n.ende.substring(0, 10) : java.time.LocalDate.now().toString();
        int bosses = n.bosseBesiegt > 0 ? n.bosseBesiegt
                : won && n.boss != null && !n.boss.isEmpty() ? ("vierer".equals(n.boss) ? 4 : "doppel".equals(n.boss) ? 2 : 1) : 0;
        HallOfFame.LastNight last = new HallOfFame.LastNight();
        last.date = date;
        last.night = n.nacht;
        last.level = n.stufe;
        last.result = n.ergebnis;
        List<String> touched = new ArrayList<>();
        for (StatsRecorder.Spieler s : n.spieler.values()) {
            if (s.uuid == null || s.uuid.isEmpty()) {
                continue;
            }
            HallOfFame.Hero h = hall.hero(s.uuid, s.name);
            touched.add(s.uuid);
            double dealt = 0;
            double taken = 0;
            double healed = 0;
            double intercepted = 0;
            double droneDamage = 0;
            int kills = 0;
            int assists = 0;
            int deaths = 0;
            int droneSeconds = 0;
            int dashes = 0;
            for (StatsRecorder.Welle w : n.wellen) {
                StatsRecorder.SpielerWelle pw = w.spieler.get(s.name);
                if (pw == null) {
                    continue;
                }
                dealt += pw.schaden;
                taken += pw.erhalten;
                healed += pw.heilungGegeben;
                intercepted += pw.abgefangen;
                droneDamage += pw.drohneSchaden;
                kills += pw.kills;
                assists += pw.assists;
                deaths += pw.tode;
                droneSeconds += pw.drohneSekunden;
                for (Map.Entry<String, Double> e : pw.schadenNachQuelle.entrySet()) {
                    h.dealtBy.merge(sourceGroup(e.getKey()), e.getValue(), Double::sum);
                }
                for (Map.Entry<String, Double> e : pw.erhaltenNachGegner.entrySet()) {
                    h.takenFrom.merge(e.getKey(), e.getValue(), Double::sum);
                }
                Integer dash = pw.faehigkeiten.get("wahl:dash");
                if (dash != null) {
                    dashes += dash;
                }
            }
            h.nights++;
            if (won) {
                h.held++;
                h.streak++;
                h.bestStreak = Math.max(h.bestStreak, h.streak);
                h.maxCurses = Math.max(h.maxCurses, n.fluchsteine == null ? 0 : n.fluchsteine.size());
            } else {
                h.lost++;
                h.streak = 0;
            }
            if (deaths == 0) {
                h.nightsNoDeath++;
            }
            h.kills += kills;
            h.assists += assists;
            h.deaths += deaths;
            h.dealt += dealt;
            h.taken += taken;
            h.healed += healed;
            h.intercepted += intercepted;
            h.droneDamage += droneDamage;
            h.droneSeconds += droneSeconds;
            h.bosses += bosses;
            for (StatsRecorder.Turm t : n.tuerme.values()) {
                if (s.name.equals(t.besitzer)) {
                    h.towerKills += t.kills;
                }
            }
            String sub = s.unterklasse == null || s.unterklasse.isEmpty() ? s.klasse : s.unterklasse;
            HallOfFame.SubLine line = h.subs.computeIfAbsent(sub == null ? "" : sub, k -> new HallOfFame.SubLine());
            line.clazz = s.klasse == null ? "" : s.klasse;
            line.nights++;
            line.held += won ? 1 : 0;
            line.kills += kills;
            line.deaths += deaths;
            line.dealt += dealt;
            Map<String, Double> curios = n.kuriositaeten == null ? null : n.kuriositaeten.get(s.name);
            if (curios != null) {
                for (Map.Entry<String, Double> e : curios.entrySet()) {
                    if ("letzterMann".equals(e.getKey())) {
                        if (won && e.getValue() > 0) {
                            h.lastStandingHeld++;
                        }
                        continue;
                    }
                    hall.curio(s.uuid, h, e.getKey(), e.getValue(), date);
                }
            }
            if (dashes > 0) {
                hall.curio(s.uuid, h, "sturmschritte", dashes, date);
            }
            Map<String, Integer> by = n.todDurch == null ? null : n.todDurch.get(s.name);
            if (by != null) {
                by.forEach((type, times) -> h.killedBy.merge(type, times, Integer::sum));
            }
            Map<String, Integer> victims = n.opfer == null ? null : n.opfer.get(s.name);
            if (victims != null) {
                victims.forEach((type, times) -> h.victims.merge(type, times, Integer::sum));
            }
            HallOfFame.NightMini mini = new HallOfFame.NightMini();
            mini.date = date;
            mini.night = n.nacht;
            mini.level = n.stufe;
            mini.held = won;
            mini.dealt = Math.round(dealt);
            mini.kills = kills;
            mini.deaths = deaths;
            mini.sub = sub == null ? "" : sub;
            h.recent.add(mini);
            while (h.recent.size() > RECENT) {
                h.recent.remove(0);
            }
            HallOfFame.Entry entry = new HallOfFame.Entry();
            entry.uuid = s.uuid;
            entry.name = s.name;
            entry.clazz = s.klasse == null ? "" : s.klasse;
            entry.sub = sub == null ? "" : sub;
            entry.dealt = Math.round(dealt);
            entry.healed = Math.round(healed);
            entry.intercepted = Math.round(intercepted);
            entry.kills = kills;
            entry.deaths = deaths;
            entry.bosses = bosses;
            entry.held = won;
            last.entries.add(entry);
        }
        for (Map.Entry<String, StatsRecorder.Gegner> e : n.gegner.entrySet()) {
            HallOfFame.Beast b = hall.bestiary.computeIfAbsent(e.getKey(), k -> new HallOfFame.Beast());
            b.seen += e.getValue().gespawnt;
            b.killed += e.getValue().getoetet;
            b.damageToPlayers += e.getValue().schadenAnSpielern;
        }
        if (n.todDurch != null) {
            for (Map<String, Integer> by : n.todDurch.values()) {
                by.forEach((type, times) -> hall.bestiary.computeIfAbsent(type, k -> new HallOfFame.Beast()).killedPlayers += times);
            }
        }
        if (!last.entries.isEmpty()) {
            hall.lastNight = last;
        }
        badges(m, server, touched, live);
        if (live) {
            m.persist();
        }
    }

    /** The damage sources of the logger in the groups of the page. */
    static String sourceGroup(String key) {
        if (key.startsWith("nah:")) {
            return "nah";
        }
        if (key.startsWith("fern:") || key.startsWith("geschoss:")) {
            return "fern";
        }
        if (key.startsWith("turm:")) {
            return "turm";
        }
        if (key.startsWith("begleiter")) {
            return "begleiter";
        }
        if (key.startsWith("drohne")) {
            return "drohne";
        }
        return "faehigkeit";
    }

    // ------------------------------------------------------------------ dungeons, war camps, crates

    /** A dungeon or war camp attempt ended. */
    public static void addAttempt(SiegeManager m, MinecraftServer server, StatsRecorder.AttemptFile a, boolean live) {
        if (a == null || a.spieler == null || a.spieler.isEmpty()) {
            return;
        }
        HallOfFame hall = hall(m);
        boolean dungeon = "dungeon".equals(a.art);
        List<String> touched = new ArrayList<>();
        for (StatsRecorder.Spieler s : a.spieler) {
            if (s.uuid == null || s.uuid.isEmpty()) {
                continue;
            }
            HallOfFame.Hero h = hall.hero(s.uuid, s.name);
            touched.add(s.uuid);
            if (dungeon) {
                h.dungeons++;
                if ("gewonnen".equals(a.ergebnis)) {
                    h.dungeonsWon++;
                    h.bosses++;   // the champion
                }
            } else {
                h.camps++;
                if ("zerstoert".equals(a.ergebnis)) {
                    h.campsDestroyed++;
                }
            }
        }
        badges(m, server, touched, live);
        if (live) {
            m.persist();
        }
    }

    /** A crate was opened. */
    public static void addCrate(SiegeManager m, MinecraftServer server, String uuid, String name, String rarity, boolean live) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        HallOfFame.Hero h = hall(m).hero(uuid, name);
        h.crates++;
        if (rarity != null && !rarity.isEmpty() && !"COMMON".equals(rarity) && !"UNCOMMON".equals(rarity)) {
            h.rareCrates++;
        }
        badges(m, server, List.of(uuid), live);
        if (live) {
            m.persist();
        }
    }

    // ------------------------------------------------------------------ badges

    private static void badges(SiegeManager m, MinecraftServer server, List<String> uuids, boolean announce) {
        HallOfFame hall = hall(m);
        Map<String, String> tiers = m.config() == null ? Map.of() : m.config().hallBadgeTiers;
        for (String uuid : uuids) {
            HallOfFame.Hero h = hall.heroes.get(uuid);
            if (h == null) {
                continue;
            }
            List<String> up = HallBadges.update(h, tiers);
            if (!announce || server == null || up.isEmpty()) {
                continue;
            }
            ServerPlayer player = null;
            try {
                player = server.getPlayerList().getPlayer(java.util.UUID.fromString(uuid));
            } catch (IllegalArgumentException ignored) {
                // not a UUID
            }
            for (String u : up) {
                String id = u.substring(0, u.indexOf(':'));
                int tier = Integer.parseInt(u.substring(u.indexOf(':') + 1));
                Component badge = Component.translatable("nightsiege.hall.badge." + id);
                Component tierName = Component.translatable("nightsiege.hall.tier." + tier);
                if (tier >= 3) {
                    // Gold: everybody hears of it (Simon 23:55).
                    Component all = Component.translatable("nightsiege.hall.gold_all", h.name, badge,
                            Component.translatable("nightsiege.hall.title." + id)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (!SimBots.isBot(p)) {
                            p.sendSystemMessage(all);
                        }
                    }
                    if (player != null) {
                        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                                SoundSource.PLAYERS, 1f, 1f);
                    }
                } else if (player != null) {
                    player.sendSystemMessage(Component.translatable("nightsiege.hall.badge_up", badge, tierName)
                            .withStyle(tier == 2 ? ChatFormatting.GRAY : ChatFormatting.GOLD));
                    player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
                }
                NightSiegeMod.LOGGER.info("[Night Siege] Abzeichen: {} {} Stufe {}", h.name, id, tier);
            }
        }
    }

    /** "hall:title:&lt;badge&gt;": a gold badge as the title, or none (empty). */
    public static void chooseTitle(SiegeManager m, ServerPlayer player, String badge) {
        HallOfFame.Hero h = hall(m).heroes.get(player.getUUID().toString());
        if (h == null) {
            return;
        }
        if (badge == null || badge.isEmpty()) {
            h.title = "";
        } else if (h.badges.getOrDefault(badge, 0) >= 3) {
            h.title = badge;
        }
        m.persist();
        send(m, player);
    }

    // ------------------------------------------------------------------ the window

    /** Sends the Ruhmeshalle to one player's window ("hall"). */
    public static void send(SiegeManager m, ServerPlayer player) {
        if (player == null || !ServerPlayNetworking.canSend(player, CoreNetworking.ScreenPayload.TYPE)) {
            return;
        }
        HallView view = view(m, player.getUUID().toString(), player.level().getServer());
        ServerPlayNetworking.send(player, new CoreNetworking.ScreenPayload("hall", Json.toJson(view)));
    }

    /** What the window gets: the hall with every list cut short, the thresholds and who is online. */
    static HallView view(SiegeManager m, String viewer, MinecraftServer server) {
        HallOfFame hall = hall(m);
        HallView v = new HallView();
        v.viewer = viewer;
        v.logger = m.config() == null || m.config().statsLogger;
        v.lastNight = hall.lastNight;
        v.curioBests = hall.curioBests;
        v.bestiary = hall.bestiary;
        Map<String, String> over = m.config() == null ? Map.of() : m.config().hallBadgeTiers;
        for (HallBadges.Badge b : HallBadges.ALL) {
            v.tiers.put(b.id(), HallBadges.tiers(b, over));
        }
        for (Map.Entry<String, HallOfFame.Hero> e : hall.heroes.entrySet()) {
            HallOfFame.Hero h = e.getValue();
            HallOfFame.Hero c = Json.fromJson(Json.toJson(h), HallOfFame.Hero.class);
            if (c == null) {
                continue;
            }
            c.victims = top(h.victims);
            c.killedBy = top(h.killedBy);
            c.takenFrom = topD(h.takenFrom);
            v.heroes.put(e.getKey(), c);
        }
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                v.online.add(p.getUUID().toString());
            }
        }
        java.util.Set<String> met = new java.util.HashSet<>();
        for (String type : hall.bestiary.keySet()) {
            String plain = plainType(type);
            met.add(plain);
            Foes.Role role = Foes.roleOf(plain);
            v.beastLooks.put(type, role != null ? role.base() : "maschine".equals(plain) ? "minecraft:ravager" : plain);
        }
        for (Foes.Role role : Foes.Role.values()) {
            if (!met.contains(role.entryId())) {
                v.unseen.add(role.entryId());
                v.beastLooks.put(role.entryId(), role.base());
            }
        }
        return v;
    }

    /** A logger monster type without its boss and elite marks ("boss:minecraft:zombie+elite" → "minecraft:zombie"). */
    static String plainType(String type) {
        String t = type == null ? "" : type;
        if (t.startsWith("boss:")) {
            t = t.substring(5);
        }
        if (t.endsWith("+elite")) {
            t = t.substring(0, t.length() - 6);
        }
        return t;
    }

    private static Map<String, Integer> top(Map<String, Integer> all) {
        Map<String, Integer> out = new LinkedHashMap<>();
        all.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(TOP)
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    private static Map<String, Double> topD(Map<String, Double> all) {
        Map<String, Double> out = new LinkedHashMap<>();
        all.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(TOP)
                .forEach(e -> out.put(e.getKey(), Math.round(e.getValue() * 10.0) / 10.0));
        return out;
    }

    // ------------------------------------------------------------------ admin

    /**
     * Empties the Ruhmeshalle (/nightsiege ruhmeshalle leeren bestaetigen); with rebuild it reads
     * every logger file in again (/nightsiege ruhmeshalle neu_einlesen), the chosen titles stay.
     * Returns how many defenders the hall had.
     */
    public static int reset(SiegeManager m, MinecraftServer server, boolean rebuild, String by) {
        HallOfFame old = hall(m);
        int heroes = old.heroes.size();
        HallOfFame fresh = new HallOfFame();
        if (rebuild) {
            old.heroes.forEach((uuid, h) -> {
                if (h.title != null && !h.title.isEmpty()) {
                    fresh.hero(uuid, h.name).title = h.title;
                }
            });
        }
        fresh.imported = !rebuild;
        m.save().hall = fresh;
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Ruhmeshalle {} von {} ({} Verteidiger vorher)", rebuild ? "neu eingelesen" : "geleert",
                by, heroes);
        if (rebuild) {
            importOnce(m, server);
        }
        return heroes;
    }

    // ------------------------------------------------------------------ the nights before

    /**
     * Once, at the first start with the Ruhmeshalle: the real nights, dungeon and war camp
     * attempts and crates the logger wrote before. Read on a thread of its own, counted on the
     * server thread.
     */
    public static void importOnce(SiegeManager m, MinecraftServer server) {
        HallOfFame hall = hall(m);
        if (hall.imported || server == null) {
            return;
        }
        Path dir = m.stats().statsFolder();
        hall.imported = true;
        if (dir == null || !Files.isDirectory(dir)) {
            m.persist();
            return;
        }
        Thread t = new Thread(() -> {
            List<Object> read = new ArrayList<>();
            int[] count = new int[4];
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : files.sorted().toList()) {
                    String name = f.getFileName().toString();
                    try {
                        if (name.startsWith("night_") && name.endsWith(".json")) {
                            {
                                StatsRecorder.NightFile n = Json.read(f, StatsRecorder.NightFile.class);
                                if (n != null && n.abgeschlossen) {
                                    read.add(n);
                                    count[0]++;
                                }
                            }
                        } else if ((name.startsWith("dungeon_") || name.startsWith("heerlager_")) && name.endsWith(".json")) {
                            {
                                StatsRecorder.AttemptFile a = Json.read(f, StatsRecorder.AttemptFile.class);
                                if (a != null && a.ergebnis != null && !a.ergebnis.isEmpty()) {
                                    read.add(a);
                                    count[1]++;
                                }
                            }
                        } else if (name.equals("kisten.jsonl")) {
                            for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                                if (!l.isBlank()) {
                                    StatsRecorder.CrateLine c = Json.fromJson(l, StatsRecorder.CrateLine.class);
                                    if (c != null) {
                                        read.add(c);
                                        count[2]++;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        count[3]++;
                    }
                }
            } catch (Exception e) {
                NightSiegeMod.LOGGER.warn("[Night Siege] Ruhmeshalle: alte Dateien nicht lesbar: {}", e.toString());
            }
            server.execute(() -> {
                Map<String, String> uuidOf = new LinkedHashMap<>();
                for (Object o : read) {
                    if (o instanceof StatsRecorder.NightFile n) {
                        addNight(m, server, n, false);
                        for (StatsRecorder.Spieler s : n.spieler.values()) {
                            uuidOf.put(s.name, s.uuid);
                        }
                    } else if (o instanceof StatsRecorder.AttemptFile a) {
                        addAttempt(m, server, a, false);
                        for (StatsRecorder.Spieler s : a.spieler) {
                            uuidOf.put(s.name, s.uuid);
                        }
                    }
                }
                for (Object o : read) {
                    if (o instanceof StatsRecorder.CrateLine c && uuidOf.containsKey(c.spieler)) {
                        addCrate(m, server, uuidOf.get(c.spieler), c.spieler, c.seltenheit, false);
                    }
                }
                m.persist();
                NightSiegeMod.LOGGER.info("[Night Siege] Ruhmeshalle: {} Nächte, {} Dungeons/Heerlager, {} Kisten übernommen ({} Dateien nicht lesbar), {} Spieler",
                        count[0], count[1], count[2], count[3], hall.heroes.size());
            });
        }, "nightsiege-hall-import");
        t.setDaemon(true);
        t.start();
    }
}
