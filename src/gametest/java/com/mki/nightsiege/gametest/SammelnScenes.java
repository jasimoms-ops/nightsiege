package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.GatherPhase;
import com.mki.nightsiege.game.CoreRecord;
import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Scene "sammeln" (Tor 12, Simon 17.11.): at the horn every player who accepted is brought to the core, gets the kit
 * and presses ready; the clock stands until all are ready (countdown 3-2-1, then 15000) or the deadline is up.
 * One client: the own player is A (300 blocks away, then in the Nether, then a late-comer back to life) and C (declined);
 * B and D are test guests at the core (SiegeManager.gatherTestGuests). Pictures "sammeln_*". Log lines "[sammeln]".
 */
final class SammelnScenes {

    private static final List<String> FAILS = Collections.synchronizedList(new ArrayList<>());
    private static int checks;
    private static int shots;
    private static final UUID GUEST_B = UUID.nameUUIDFromBytes("t12-guest-B".getBytes());
    private static final UUID GUEST_D = UUID.nameUUIDFromBytes("t12-guest-D".getBytes());
    /** An accepted id so the horn comes while the own player declined (run C). */
    private static final String YES_ID = UUID.nameUUIDFromBytes("t12-accepts".getBytes()).toString();

    private SammelnScenes() {
    }

    private static void log(String line) {
        System.out.println("[sammeln] " + line);
    }

    private static void check(boolean ok, String what) {
        checks++;
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) {
            FAILS.add(what);
        }
    }

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
        context.waitTicks(5);
    }

    /** Waits for a condition, at most maxTicks; returns the ticks waited, -1 when it never came. */
    private static int until(ClientGameTestContext context, int maxTicks, BooleanSupplier cond) {
        for (int t = 0; t <= maxTicks; t++) {
            if (context.computeOnClient(client -> cond.getAsBoolean())) {
                return t;
            }
            context.waitTicks(1);
        }
        return -1;
    }

    private static SiegeManager m() {
        return SiegeManager.get();
    }

    private static void shot(ClientGameTestContext context, String name) {
        int windowH = context.computeOnClient(client -> client.getWindow().getScreenHeight());
        context.getInput().setCursorPos(2, Math.max(2, windowH - 2));
        context.waitTicks(3);
        context.takeScreenshot(name);
        shots++;
    }

    private static void openCore(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try {
                java.lang.reflect.Field field = com.mki.nightsiege.client.CoreScreen.class.getDeclaredField("activeTab");
                field.setAccessible(true);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object value = Enum.valueOf((Class) field.getType(), "STATS");
                field.set(null, value);
            } catch (ReflectiveOperationException e) {
                log("could not set the tab: " + e);
            }
        });
        EventLookTest.onServer(context, (player, manager) -> manager.onCoreAction(player, "open"));
        context.waitTicks(30);
    }

    /** The core window in the four looks (EN/DE, GUI 2/3) with the card checks, then closed. */
    private static void card(ClientGameTestContext context, String name, boolean gathering) {
        openCore(context);
        shots += CoreCardCheck.matrix(context, name, gathering, SammelnScenes::check);
        EventLookTest.closeScreen(context);
    }

    private static CoreRecord core() {
        return m().mainCore();
    }

    private static ServerLevel coreLevel(MinecraftServer server) {
        return SiegeManager.levelByName(server, core().dimension);
    }

    /** Votes for day d at 2000, the own player accepts (or declines), the clock just before the horn. */
    private static void prepareNight(ClientGameTestContext context, TestSingleplayerContext game, long d, boolean yes,
                                     boolean invitePicture) {
        game.getServer().runCommand("time set " + (d * 24000L + 2000L));
        context.waitTicks(5);
        server(context, "votes " + d, (player, m) -> {
            m.resetRhythmForTests();
            m.save().pendingEvent = null;
            m.save().nextSiegeDay = d;
            m.save().voteDay = d;
            m.save().clearVotes();
            m.save().accepted.add(YES_ID);
            if (invitePicture) {
                m.sendInvitation(player);
            }
        });
        if (invitePicture) {
            card(context, "sammeln_einladung", false);   // Einladung offen
        }
        server(context, "vote " + d, (player, m) -> m.vote(player, yes));
    }

    private static void hornAndWaitReady(ClientGameTestContext context, TestSingleplayerContext game, long d, String what) {
        game.getServer().runCommand("time set " + (d * 24000L + 11990L));
        int horn = until(context, 120, () -> m().gathering());
        check(horn >= 0, what + ": the horn starts the gathering (" + horn + " ticks)");
        int ready = until(context, 140, () -> m().gatherForTests().phase() == GatherPhase.Phase.READY);
        check(ready >= 0, what + ": 5 s later the ready phase (" + ready + " ticks)");
    }

    /** A far spot (on the ground, 300 blocks east of the core). */
    private static void sendAway(ServerPlayer player) {
        ServerLevel level = coreLevel(player.level().getServer());
        CoreRecord c = core();
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(c.x + 300, 0, c.z));
        player.teleportTo(level, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, java.util.Set.of(), 0f, 0f, true);
    }

    private static double flatDistanceToCore(ServerPlayer player) {
        CoreRecord c = core();
        double dx = player.getX() - (c.x + 0.5);
        double dz = player.getZ() - (c.z + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** At the core on safe ground with the kit, no fall, shielded. */
    private static void checkArrived(ServerPlayer player, SiegeManager m, String what) {
        ServerLevel level = coreLevel(player.level().getServer());
        double dist = flatDistanceToCore(player);
        check(player.level() == level, what + ": in the core's dimension (" + player.level().dimension() + ")");
        check(dist >= 2.5 && dist <= 7.5, what + ": 3–6 blocks from the core (" + String.format("%.1f", dist) + ")");
        check(!player.isInWall() && !player.isInWater() && !player.isInLava(), what + ": not in a block, water or lava");
        check(SiegeManager.standable(level, player.blockPosition()), what + ": firm free ground (" + player.blockPosition() + ")");
        check(player.fallDistance == 0.0, what + ": no fall (" + player.fallDistance + ")");
        check(player.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE), what + ": 3 s shield");
        check(m.kits().hasStash(player), what + ": kit on");
        check(m.gatherForTests().isMember(player.getUUID()), what + ": a participant");
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        FAILS.clear();
        checks = 0;
        shots = 0;
        EventLookTest.ensureCore(context, game);
        game.getServer().runCommand("gamemode survival @p");
        int[] window = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(), client.getWindow().getHeight()});
        context.getInput().resizeWindow(1280, 720);
        context.waitTicks(10);
        final Object[] before = new Object[12];
        final long[] day = new long[1];
        server(context, "setup", (player, m) -> {
            if (!m.isRegistered(player)) {
                m.register(player);
            }
            var save = m.save();
            before[0] = save.siegesWon;
            before[1] = save.siegesLost;
            before[2] = save.bossNights;
            before[3] = save.nextSiegeDay;
            before[4] = m.config().gatherTeleport;
            before[5] = m.config().gatherReadyMaxSeconds;
            before[6] = m.config().requiredAccepts;
            before[7] = m.config().nightKits;
            before[8] = save.extraSiegeDay;
            m.config().gatherTeleport = true;
            m.config().gatherReadyMaxSeconds = 180;
            m.config().requiredAccepts = 1;
            m.config().nightKits = true;
            save.register(YES_ID);
            m.gatherTestGuests.clear();
            day[0] = m.clockDayFor(player.level().getServer()) + 1;
            check(core() != null, "a core (" + core() + ")");
        });

        // --- C: declined → stays where he is, no kit (the horn comes for another who accepted).
        long dC = day[0];
        prepareNight(context, game, dC, false, false);
        server(context, "C away", (player, m) -> sendAway(player));
        hornAndWaitReady(context, game, dC, "C");
        context.waitTicks(20);
        server(context, "C stays", (player, m) -> {
            check(flatDistanceToCore(player) > 200, "C (declined) stays where he is (" + (int) flatDistanceToCore(player) + ")");
            check(!m.kits().hasStash(player), "C has no kit");
            check(!m.gatherForTests().isMember(player.getUUID()), "C is no participant");
        });
        card(context, "sammeln_abgesagt", false);
        server(context, "C stop", (player, m) -> player.level().getServer().getCommands().performPrefixedCommand(
                player.level().getServer().createCommandSourceStack(), "nightsiege stop"));
        context.waitTicks(10);
        final int[] clock = new int[2];
        server(context, "C clock", (player, m) -> clock[0] = m.clockTimeFor(player.level().getServer()));
        context.waitTicks(40);
        server(context, "C clock runs", (player, m) -> {
            clock[1] = m.clockTimeFor(player.level().getServer());
            check(!m.gathering(), "stopped: the gathering is over");
            check(clock[1] > clock[0], "stopped: the clock runs again (" + clock[0] + " → " + clock[1] + ")");
        });

        // --- A 300 blocks away, B (guest) at the core: horn → A at the core with the kit within 1 s.
        long dA = day[0] + 1;
        prepareNight(context, game, dA, true, true);
        server(context, "A away", (player, m) -> {
            sendAway(player);
            m.gatherTestGuests.put(GUEST_B, "Brunhild");
        });
        game.getServer().runCommand("time set " + (dA * 24000L + 11990L));
        int horn = until(context, 120, () -> m().gathering());
        check(horn >= 0, "A: the horn starts the gathering (" + horn + " ticks)");
        context.waitTicks(20);
        shot(context, "sammeln_horn");   // title "To the core!" and the chat line
        card(context, "sammeln_horn_fenster", false);   // Horn/Sammeln
        int ready = until(context, 140, () -> m().gatherForTests().phase() == GatherPhase.Phase.READY);
        check(ready >= 0, "A: the ready phase (" + ready + " ticks)");
        int there = until(context, 20, () -> m().gatherForTests().isMember(context.computeOnClient(c -> c.player.getUUID())));
        check(there >= 0 && there <= 20, "A: at the core within 1 s (" + there + " ticks)");
        server(context, "A arrived", (player, m) -> checkArrived(player, m, "A"));
        shot(context, "sammeln_ankunft");   // chat with [Bereit], bar on top
        server(context, "clock", (player, m) -> clock[0] = m.clockTimeFor(player.level().getServer()));
        context.waitTicks(60);
        server(context, "A ready", (player, m) -> {
            clock[1] = m.clockTimeFor(player.level().getServer());
            check(clock[1] == clock[0], "the clock stands in the ready phase (" + clock[0] + " → " + clock[1] + ")");
            check(m.gatherForTests().memberCount() == 2, "A and B take part (" + m.gatherForTests().memberCount() + ")");
            m.toggleGatherReady(player);
        });
        context.waitTicks(40);
        server(context, "only A", (player, m) -> {
            check(!m.isActive(), "only A ready: no start");
            check(m.gatherForTests().phase() == GatherPhase.Phase.READY, "only A ready: still the ready phase");
            check(m.gatherForTests().readyCount() == 1, "1 of 2 ready (" + m.gatherForTests().readyCount() + ")");
            check(m.clockTimeFor(player.level().getServer()) == clock[0], "only A ready: the clock still stands");
        });
        card(context, "sammeln_bereit", true);   // Bereit (real server state)
        server(context, "B ready", (player, m) -> m.gatherForTests().toggle(GUEST_B));
        int countdown = until(context, 40, () -> m().gatherForTests().phase() == GatherPhase.Phase.COUNTDOWN);
        check(countdown >= 0, "B ready too: countdown (" + countdown + " ticks)");
        context.waitTicks(10);
        shot(context, "sammeln_countdown");   // 3-2-1 title, green bar
        int started = until(context, 100, () -> m().isActive());
        check(started >= 0, "after the countdown the night runs (" + started + " ticks)");
        server(context, "A night", (player, m) -> {
            int t = m.clockTimeFor(player.level().getServer());
            check(t >= m.config().startDayTime && t <= m.config().startDayTime + 200, "the clock jumped to the start (" + t + ")");
            check(m.isDefending(player), "A defends");
            check(!m.gathering(), "the gathering is over");
            check(m.kits().hasStash(player), "one kit (still on)");
        });
        card(context, "sammeln_nacht", false);   // Nacht läuft
        server(context, "A stop", (player, m) -> player.level().getServer().getCommands().performPrefixedCommand(
                player.level().getServer().createCommandSourceStack(), "nightsiege stop"));
        context.waitTicks(40);
        server(context, "A after", (player, m) -> {
            check(!m.isActive(), "A: night stopped");
            check(!m.kits().hasStash(player), "A: own things back after the night");
            m.gatherTestGuests.clear();
        });

        // --- Nether + deadline: A in the Nether, nobody presses ready → start after gatherReadyMaxSeconds (30 here).
        long dN = day[0] + 2;
        prepareNight(context, game, dN, true, false);
        server(context, "nether", (player, m) -> {
            m.config().gatherReadyMaxSeconds = 30;
            m.gatherTestGuests.put(GUEST_B, "Brunhild");
            ServerLevel nether = player.level().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            BlockPos base = new BlockPos(8, 100, 8);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    nether.setBlock(base.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState(), 3);
                    for (int dy = 0; dy < 3; dy++) {
                        nether.setBlock(base.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            player.teleportTo(nether, 8.5, 100, 8.5, java.util.Set.of(), 0f, 0f, true);
        });
        hornAndWaitReady(context, game, dN, "Nether");
        context.waitTicks(20);
        server(context, "nether arrived", (player, m) -> checkArrived(player, m, "B-Nether"));
        int deadline = until(context, 30 * 20 + 200, () -> m().isActive());
        check(deadline >= 0, "deadline: nobody ready, the night starts anyway (" + deadline + " ticks)");
        server(context, "deadline night", (player, m) -> {
            check(m.isDefending(player), "deadline: A (not ready) defends");
            int t = m.clockTimeFor(player.level().getServer());
            check(t >= m.config().startDayTime && t <= m.config().startDayTime + 200, "deadline: clock at the start (" + t + ")");
            m.config().gatherReadyMaxSeconds = 180;
        });
        server(context, "nether stop", (player, m) -> player.level().getServer().getCommands().performPrefixedCommand(
                player.level().getServer().createCommandSourceStack(), "nightsiege stop"));
        context.waitTicks(40);

        // --- Late-comer (back to life): spectating at the horn → stays; survival → fetched, kit, counter 3 with B and D.
        long dL = day[0] + 3;
        prepareNight(context, game, dL, true, false);
        server(context, "late setup", (player, m) -> {
            sendAway(player);
            m.save().extraSiegeDay = dL;   // an extra siege: gathers like a regular night
        });
        game.getServer().runCommand("gamemode spectator @p");
        context.waitTicks(5);
        hornAndWaitReady(context, game, dL, "late");
        context.waitTicks(25);
        server(context, "late away", (player, m) -> {
            check(flatDistanceToCore(player) > 200, "spectating at the teleport: not fetched (" + (int) flatDistanceToCore(player) + ")");
            check(m.gatherForTests().memberCount() == 1, "only B at the core (" + m.gatherForTests().memberCount() + ")");
        });
        game.getServer().runCommand("gamemode survival @p");
        int late = until(context, 40, () -> m().gatherForTests().isMember(context.computeOnClient(c -> c.player.getUUID())));
        check(late >= 0 && late <= 25, "late-comer fetched within a second (" + late + " ticks)");
        server(context, "late arrived", (player, m) -> {
            checkArrived(player, m, "late");
            m.gatherTestGuests.put(GUEST_D, "Dagmar");
        });
        context.waitTicks(25);
        server(context, "late count", (player, m) -> check(m.gatherForTests().memberCount() == 3,
                "the counter rises to 3 (" + m.gatherForTests().memberCount() + ")"));
        card(context, "sammeln_extra", true);   // Extra-Belagerung in the ready phase
        // Called off by the clock (/time set to the day before): kits back, the clock runs.
        game.getServer().runCommand("time set " + ((dL - 1) * 24000L + 1000L));
        context.waitTicks(40);
        server(context, "abort", (player, m) -> {
            check(!m.gathering(), "clock set back: the gathering ends");
            check(!m.kits().hasStash(player), "clock set back: kit back");
            clock[0] = m.clockTimeFor(player.level().getServer());
        });
        context.waitTicks(40);
        server(context, "abort clock", (player, m) -> {
            int t = m.clockTimeFor(player.level().getServer());
            check(t > clock[0], "after the abort the clock runs (" + clock[0] + " → " + t + ")");
        });

        server(context, "end", (player, m) -> {
            var save = m.save();
            m.gatherTestGuests.clear();
            save.siegesWon = (Integer) before[0];
            save.siegesLost = (Integer) before[1];
            save.bossNights = (Integer) before[2];
            save.nextSiegeDay = (Long) before[3];
            m.config().gatherTeleport = (Boolean) before[4];
            m.config().gatherReadyMaxSeconds = (Integer) before[5];
            m.config().requiredAccepts = (Integer) before[6];
            m.config().nightKits = (Boolean) before[7];
            save.extraSiegeDay = (Long) before[8];
            save.voteDay = -1L;
            save.clearVotes();
            save.unregister(YES_ID);
            m.resetRhythmForTests();
            m.persist();
        });
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("time set 6000");
        context.getInput().resizeWindow(window[0], window[1]);
        context.waitTicks(10);
        if (shots == 0) {
            FAILS.add("no pictures");
        }
        log("summary: " + checks + " checks, " + shots + " pictures, " + FAILS.size() + " failed" + (FAILS.isEmpty() ? "" : ": " + FAILS));
        System.out.println("[Night Siege test] sammeln: " + (checks - FAILS.size()) + " von " + checks);
        if (checks == 0 || !FAILS.isEmpty()) {
            throw new AssertionError("sammeln: " + (checks == 0 ? "no checks" : String.join("; ", FAILS)));
        }
    }
}
