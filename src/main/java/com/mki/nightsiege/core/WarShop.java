package com.mki.nightsiege.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the war leader can buy (Kriegsführer, Simon 29.10.2026) and how much gold a round brings.
 *
 * <p>A purchase is a squad: a few monsters of one kind that appear together at a gathering point.
 * Squads unlock at the level the same monsters come in a normal night. Machines, the boss and the
 * event cards are bought apart (see {@link #MACHINE_PRICE}, {@link #BOSS_PRICE}, {@link Card}).</p>
 *
 * <p>The gold follows a normal night of the same level and team (Simon 20:24: "so schwer wie normale
 * Nächte, vielleicht 20 % einfacher"): its monsters, machines and boss in gold, times
 * {@code warGoldShare}, spread over the waves, the last one with half again as much.</p>
 */
public final class WarShop {

    /** One squad of the shop. */
    public record Squad(String id, int price, List<String> mobs, int fromLevel, boolean ownWill) {
        /** How many monsters it brings. */
        public int size() {
            return mobs.size();
        }
    }

    /** The event cards: one wave long, each once a round, one at a time. */
    public enum Card {
        BLOOD_MOON("blutmond", SiegeEvent.BLOOD_MOON, 20),
        FOG("nebel", SiegeEvent.FOG, 15),
        FROST("frost", SiegeEvent.FROST, 15),
        THUNDER("gewitter", SiegeEvent.THUNDER, 20);

        private final String id;
        private final SiegeEvent event;
        private final int price;

        Card(String id, SiegeEvent event, int price) {
            this.id = id;
            this.event = event;
            this.price = price;
        }

        public String id() {
            return id;
        }

        public SiegeEvent event() {
            return event;
        }

        public int price() {
            return price;
        }

        public static Card byId(String id) {
            for (Card c : values()) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
            return null;
        }
    }

    public static final int MACHINE_PRICE = 15;
    public static final int BOSS_PRICE = 40;
    /** How many bosses the leader may choose from in a round. */
    public static final int BOSS_OFFERS = 3;

    private static final String TAP = "takesapillage:";

    /** The squads, in the order of the shop. */
    public static final List<Squad> SQUADS = List.of(
            new Squad("zombies", 4, times("minecraft:zombie", 4), 1, false),
            new Squad("skelette", 5, times("minecraft:skeleton", 3), 1, false),
            new Squad("spinnen", 4, times("minecraft:spider", 3), 1, false),
            new Squad("creeper", 5, times("minecraft:creeper", 2), 1, false),
            new Squad("wiedergaenger", 6, times("nightsiege:revenant", 3), 2, false),
            new Squad("axtillager", 6, List.of("minecraft:vindicator", "minecraft:vindicator", TAP + "skirmisher"), 6, false),
            new Squad("enterhaken", 3, List.of("nightsiege:grappler"), 5, true),
            new Squad("tunnelgraeber", 4, List.of("nightsiege:tunneler"), 3, true),
            new Squad("standarte", 4, List.of("nightsiege:standard_bearer"), 5, true),
            new Squad("eisenritter", 5, List.of("nightsiege:iron_knight"), 8, false),
            new Squad("kameltrupp", 5, List.of("minecraft:camel_husk+minecraft:husk+minecraft:parched"), 5, false));

    private WarShop() {
    }

    private static List<String> times(String id, int n) {
        return Collections.nCopies(n, id);
    }

    public static Squad squad(String id) {
        for (Squad s : SQUADS) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** Waves of a normal night at this level (one at the start, then one per interval), as the night counts them. */
    public static int nightWaves(int level, SiegeConfig cfg) {
        int total = SiegeMath.durationSeconds(level, cfg) * 20;
        int interval = Math.max(20, cfg.batchIntervalSeconds * 20) + 1;
        return Math.max(1, (total - 1) / interval + 1);
    }

    /**
     * The whole gold of a round: a normal night of this level and team in gold, times the share.
     * {@code perPlayer} and {@code maxLive} as the night uses them (the playtest scales otherwise).
     */
    public static int roundGold(int level, int players, SiegeConfig cfg, double perPlayer, int maxLive) {
        int perWave = SiegeMath.mobsPerBatch(level, Math.max(1, players), cfg, perPlayer, maxLive);
        double mobs = (double) nightWaves(level, cfg) * perWave;
        double worth = mobs * cfg.warGoldPerMob
                + MACHINE_PRICE * SiegeMachineKind.countFor(level) * cfg.warMachineShare
                + BOSS_PRICE * cfg.warBossShare;
        return (int) Math.max(10, Math.round(worth * cfg.warGoldShare));
    }

    /** The gold of one wave (1-based) out of the round's: the last wave brings half again as much. */
    public static int waveGold(int roundGold, int wave, int waves) {
        if (waves <= 0 || wave < 1 || wave > waves) {
            return 0;
        }
        double weights = waves - 1 + 1.5;
        double share = (wave == waves ? 1.5 : 1.0) / weights;
        return (int) Math.round(roundGold * share);
    }

    /** What comes over into the next wave: half of what was left. */
    public static int carry(int left) {
        return Math.max(0, left) / 2;
    }

    /** The bosses offered this round: {@link #BOSS_OFFERS} different ones in a random order. */
    public static List<BossKind> bossOffers(java.util.Random random) {
        List<BossKind> all = new ArrayList<>(List.of(BossKind.values()));
        Collections.shuffle(all, random);
        return new ArrayList<>(all.subList(0, Math.min(BOSS_OFFERS, all.size())));
    }
}
