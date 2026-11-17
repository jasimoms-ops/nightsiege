package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 12 (Simon 17.11.): the gathering at the core – phases, who is fetched, start times. */
class GatherPhaseTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID D = UUID.randomUUID();

    /** Horn at tick 0, teleport after 5 s; A and B at the core. */
    private static GatherPhase atCore(UUID... ids) {
        GatherPhase g = new GatherPhase();
        g.begin(0L);
        assertEquals(GatherPhase.Phase.ANNOUNCE, g.phase());
        assertEquals(GatherPhase.Step.NONE, g.tick(99L, 180));
        assertEquals(GatherPhase.Step.TELEPORT, g.tick(100L, 180));
        for (UUID id : ids) {
            assertTrue(g.join(id, id.toString().substring(0, 4)));
        }
        return g;
    }

    /** Runs ticks from..to and returns the steps that were not NONE. */
    private static List<GatherPhase.Step> run(GatherPhase g, long from, long to) {
        List<GatherPhase.Step> out = new java.util.ArrayList<>();
        for (long t = from; t <= to && g.active(); t++) {
            GatherPhase.Step s = g.tick(t, 180);
            if (s != GatherPhase.Step.NONE) {
                out.add(s);
            }
        }
        return out;
    }

    @Test
    void allReadyStartsAfterCountdown() {
        GatherPhase g = atCore(A, B);
        assertEquals(Boolean.TRUE, g.toggle(A));
        assertEquals(List.of(), run(g, 101L, 400L), "only A ready: no start");
        assertEquals(GatherPhase.Phase.READY, g.phase());
        assertEquals(Boolean.TRUE, g.toggle(B));
        assertEquals(GatherPhase.Step.ALL_READY, g.tick(401L, 180));
        assertEquals(GatherPhase.Phase.COUNTDOWN, g.phase());
        assertEquals(3, g.secondsLeft(401L));
        assertNull(g.toggle(A), "no toggling in the countdown");
        assertEquals(List.of(GatherPhase.Step.COUNT, GatherPhase.Step.COUNT, GatherPhase.Step.START), run(g, 402L, 1000L));
        assertFalse(g.active());
    }

    @Test
    void toggleTwiceIsNotReady() {
        GatherPhase g = atCore(A, B);
        assertEquals(Boolean.TRUE, g.toggle(A));
        assertEquals(Boolean.FALSE, g.toggle(A));
        assertEquals(0, g.readyCount());
        assertNull(g.toggle(D), "not a participant");
    }

    @Test
    void deadlineStartsWithoutAllReady() {
        GatherPhase g = atCore(A, B);
        g.toggle(A);
        List<GatherPhase.Step> steps = run(g, 101L, 100L + 180 * 20 + 5);
        assertEquals(List.of(GatherPhase.Step.WARN_60, GatherPhase.Step.WARN_10, GatherPhase.Step.TIME_UP), steps);
        assertEquals(2, g.memberCount(), "the night starts with both at the core");
    }

    @Test
    void aloneStartsWhenHePressesReady() {
        GatherPhase g = atCore(A);
        g.toggle(A);
        assertEquals(GatherPhase.Step.ALL_READY, g.tick(101L, 180));
    }

    @Test
    void nobodyAtCoreNeverCountsDownAndTimesOut() {
        GatherPhase g = atCore();
        assertFalse(g.allReady());
        List<GatherPhase.Step> steps = run(g, 101L, 100L + 180 * 20 + 5);
        assertEquals(GatherPhase.Step.TIME_UP, steps.get(steps.size() - 1));
        assertEquals(0, g.memberCount(), "nobody at the core: the manager calls the night off (nobody_at_core)");
    }

    @Test
    void whoLeavesLosesPlaceAndReady() {
        GatherPhase g = atCore(A, B);
        g.toggle(A);
        g.retain(Set.of(A));
        assertEquals(1, g.memberCount());
        assertTrue(g.allReady(), "B gone: A alone is all");
        assertEquals(GatherPhase.Step.ALL_READY, g.tick(101L, 180));
    }

    @Test
    void lateComerJoinsAndCounts() {
        GatherPhase g = atCore(A, B);
        g.toggle(A);
        g.toggle(B);
        g.retain(Set.of(A, B));
        // D logs in before the next tick: fetched and counted; not all ready any more
        assertTrue(g.join(D, "D"));
        assertFalse(g.join(D, "D"), "counted once");
        assertEquals(3, g.memberCount());
        assertFalse(g.allReady());
        assertEquals(GatherPhase.Step.NONE, g.tick(101L, 180));
    }

    @Test
    void shortDeadlineHasNoSixtyWarning() {
        GatherPhase g = new GatherPhase();
        g.begin(0L);
        g.tick(100L, 30);
        List<GatherPhase.Step> steps = new java.util.ArrayList<>();
        for (long t = 101L; g.active(); t++) {
            GatherPhase.Step s = g.tick(t, 30);
            if (s != GatherPhase.Step.NONE) {
                steps.add(s);
            }
        }
        assertEquals(List.of(GatherPhase.Step.WARN_10, GatherPhase.Step.TIME_UP), steps);
    }

    @Test
    void whoIsFetched() {
        assertTrue(GatherPhase.bringsToCore(true, true, false, false, false), "accepted");
        assertFalse(GatherPhase.bringsToCore(true, false, false, false, false), "declined or no answer");
        assertFalse(GatherPhase.bringsToCore(false, true, false, false, false), "not signed up");
        assertFalse(GatherPhase.bringsToCore(true, true, true, false, false), "playtest or another round");
        assertFalse(GatherPhase.bringsToCore(true, true, false, true, false), "dead (fetched after respawn)");
        assertFalse(GatherPhase.bringsToCore(true, true, false, false, true), "spectating");
    }

    @Test
    void switchOffMeansAsBefore() {
        SiegeConfig cfg = new SiegeConfig();
        assertTrue(cfg.gatherTeleport);
        assertEquals(180, cfg.gatherReadyMaxSeconds);
        assertTrue(GatherPhase.gathers(cfg, true));
        cfg.gatherTeleport = false;
        assertFalse(GatherPhase.gathers(cfg, true));
        assertFalse(GatherPhase.gathers(new SiegeConfig(), false), "test nights, playtest, rounds never gather");
    }

    @Test
    void startTimesEclipseAndExtraSiege() {
        SiegeConfig cfg = new SiegeConfig();
        assertEquals(cfg.startDayTime, GatherPhase.startTime(cfg, false), "normal night, boss night, finale, extra siege");
        assertEquals(15000, GatherPhase.startTime(cfg, false));
        assertEquals(cfg.eclipseStartDayTime, GatherPhase.startTime(cfg, true), "eclipse: its own start by day");
        assertTrue(GatherPhase.startTime(cfg, true) < 12000);
    }

    @Test
    void sanitizeClampsDeadline() {
        SiegeConfig cfg = new SiegeConfig();
        cfg.settingsVersion = SiegeConfig.SETTINGS_VERSION;
        cfg.gatherReadyMaxSeconds = 5;
        cfg.sanitize();
        assertEquals(30, cfg.gatherReadyMaxSeconds);
        cfg.gatherReadyMaxSeconds = 5000;
        cfg.sanitize();
        assertEquals(600, cfg.gatherReadyMaxSeconds);
    }

    @Test
    void ringSpotsAreThreeToSixAndDistinct() {
        List<int[]> ring = GatherPhase.ringOffsets(3, 6);
        Set<Long> seen = new HashSet<>();
        double last = 0;
        for (int[] o : ring) {
            double d = Math.sqrt(o[0] * o[0] + o[1] * o[1]);
            assertTrue(d >= 3 && d <= 6.5, "3–6 blocks: " + d);
            assertTrue(Math.round(d) >= Math.round(last), "nearest first");
            last = d;
            assertTrue(seen.add(((long) o[0] << 32) ^ (o[1] & 0xffffffffL)), "each spot once");
        }
        assertTrue(ring.size() > 40);
        assertEquals("2:41", GatherPhase.clock(161));
        assertEquals("0:05", GatherPhase.clock(5));
    }
}
