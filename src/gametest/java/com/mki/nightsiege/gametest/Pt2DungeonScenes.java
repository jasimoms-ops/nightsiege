package com.mki.nightsiege.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Playtest 2, package "pt2-dungeon" (Simon 04.11.):
 * <ul>
 *   <li>2: a fighter in a water basin (the cellar's round well, the cells' well) is on the floor again
 *       within ~3 s, one in lava at once; the attempt goes on with its cleared rooms (in the playtest's
 *       open entry too); one teleported just out of the area is put back.</li>
 *   <li>7: the guards of a room stand still until a fighter steps into the room's zone, then all wake
 *       in the same tick.</li>
 *   <li>8: the dungeon's stair gate and the war camp's gate are shut before the ready round (walking
 *       there or being put behind them does not get one in) and open after it; in the playtest too.</li>
 * </ul>
 * Lines start with "[pt2-dungeon]".
 */
final class Pt2DungeonScenes {

    private Pt2DungeonScenes() {
    }

    private static void log(String text) {
        System.out.println("[pt2-dungeon] " + text);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        try {
            dungeon(context, game);
        } catch (RuntimeException e) {
            log("FEHLER dungeon: " + e);
            e.printStackTrace();
        }
        try {
            camp(context, game);
        } catch (RuntimeException e) {
            log("FEHLER lager: " + e);
            e.printStackTrace();
        }
        EventLookTest.onServer(context, (player, manager) -> {
            manager.expeditions().openEntry(false);
            manager.expeditions().testPlaytest(false);
            manager.camp().openEntry(false);
        });
        context.waitTicks(5);
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("effect clear @p");
        game.getServer().runCommand("gamemode creative @p");
        log("ende");
    }

    private static String where(Entity e) {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", e.getX(), e.getY(), e.getZ());
    }

    /** As holding W: the client's player goes (dx, dz) blocks a tick for so many ticks. */
    private static void walk(ClientGameTestContext context, double dx, double dz, int ticks) {
        for (int i = 0; i < ticks; i++) {
            context.runOnClient(client -> {
                if (client.player != null) {
                    client.player.setDeltaMovement(dx, client.player.getDeltaMovement().y, dz);
                }
            });
            context.waitTicks(1);
        }
    }

    private static void to(ServerPlayer player, double x, double y, double z, float yaw) {
        player.teleportTo((ServerLevel) player.level(), x, y, z, java.util.Set.of(), yaw, 0f, true);
    }

    private static String mobs(ServerLevel level, com.mki.nightsiege.game.SiegeManager manager) {
        int all = 0;
        int awake = 0;
        for (Mob mob : manager.expeditions().testMobs(level)) {
            all++;
            if (!mob.isNoAi()) {
                awake++;
            }
        }
        return "wächter=" + all + " wach=" + awake;
    }

    /** Each guard: its kind, life and whether it stands in a block. */
    private static String detail(ServerLevel level, com.mki.nightsiege.game.SiegeManager manager) {
        StringBuilder out = new StringBuilder();
        for (Mob mob : manager.expeditions().testMobs(level)) {
            out.append(' ').append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath())
                    .append(String.format(java.util.Locale.ROOT, " %.1f/%.1f", mob.getHealth(), mob.getMaxHealth()))
                    .append(mob.isInWall() ? " imBlock" : "").append(';');
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ the dungeon

    private static void dungeon(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode creative @p");
        int rooms = 4;   // a cellar with all four halls: the cells (channel and well) and the round well among them
        int fx = pos.getX() - 420;
        int bz = pos.getZ() + 60;
        int fy = EventLookTest.dungeonGround(context, game, fx, bz, rooms);
        int hy = fy - 20;   // the halls' floor
        final boolean[] fakeCore = new boolean[1];
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            if (manager.mainCore() == null) {
                manager.save().cores.add(new com.mki.nightsiege.game.CoreRecord(
                        com.mki.nightsiege.game.SiegeManager.dimensionName(level), pos.getX(), pos.getY(), pos.getZ()));
                fakeCore[0] = true;
            }
            if (!manager.save().skillsOf(player.getUUID().toString()).hasClass()) {
                manager.onChooseClass(player, "WARRIOR");
            }
            manager.expeditions().openEntry(false);
            manager.expeditions().testPlaytest(false);
            manager.expeditions().testBuild(level, new BlockPos(fx, fy, bz), 0, com.mki.nightsiege.core.ExpeditionKind.DEEP_CELLAR, rooms);
            com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
            log("gebaut: hallen=" + plan.roomModules + " | " + manager.expeditions().testState());
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 1200 255 true");
        game.getServer().runCommand("effect give @p minecraft:saturation 1200 10 true");
        game.getServer().runCommand("effect give @p minecraft:fire_resistance 1200 0 true");
        context.waitTicks(10);

        // 7. The first hall: in it, the front gate goes up, its guards come and are awake at once.
        EventLookTest.onServer(context, (player, manager) -> {
            to(player, fx + 0.5, hy, bz + 34.5, 0f);
            manager.expeditions().testJoin(player);
        });
        context.waitTicks(30);
        EventLookTest.closeScreen(context);
        context.waitTicks(70);
        EventLookTest.onServer(context, (player, manager) -> log("raum 1 (in der Zone): " + mobs((ServerLevel) player.level(), manager)
                + " | " + manager.expeditions().testWake() + " | " + manager.expeditions().testState()));
        EventLookTest.killDungeonMobs(context, "raum 1");
        context.waitTicks(30);

        // 7. The second hall's guards stand while the fighter is still in the first hall; one step into the second
        // room's zone (its corridor, 29 blocks from the middle of the hall): all awake in the same tick.
        EventLookTest.onServer(context, (player, manager) -> {
            int n = manager.expeditions().testSpawnRoom(player.level().getServer());
            log("raum 2 gerufen (Kämpfer noch in Halle 1): " + n + " | " + mobs((ServerLevel) player.level(), manager)
                    + " |" + detail((ServerLevel) player.level(), manager));
        });
        context.waitTicks(40);
        EventLookTest.onServer(context, (player, manager) -> log("raum 2 nach 2 s, Kämpfer bei " + where(player) + ": "
                + mobs((ServerLevel) player.level(), manager) + " | " + manager.expeditions().testWake()
                + " |" + detail((ServerLevel) player.level(), manager)));
        final long[] stepAt = new long[1];
        EventLookTest.onServer(context, (player, manager) -> {
            to(player, fx + 0.5, hy, bz + 64.5, 0f);
            stepAt[0] = player.level().getGameTime();
        });
        context.waitTicks(12);
        EventLookTest.onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
            BlockPos c = plan.roomCenters.get(1);
            log("raum 2 nach dem Schritt in die Zone (Tick " + stepAt[0] + ", Abstand zur Mitte "
                    + Math.round(Math.sqrt(player.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5))) + "): "
                    + mobs((ServerLevel) player.level(), manager) + " | " + manager.expeditions().testWake());
        });
        EventLookTest.killDungeonMobs(context, "raum 2");
        context.waitTicks(30);

        // 2. The round well (two deep behind a wall): back on the floor within ~3 s, the attempt goes on.
        final BlockPos[] well = new BlockPos[1];
        EventLookTest.onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
            int k = plan.roomModules.indexOf("brunnen");
            well[0] = k < 0 ? null : plan.roomCenters.get(k).below(2);
            log("brunnen in halle " + (k + 1) + " bei " + well[0]);
        });
        context.waitTicks(5);
        if (well[0] != null) {
            basin(context, "brunnen (normal)", well[0], hy);
        }

        // 2. In the playtest's open entry: the cells' well (one deep behind a wall), lava in the floor, a step out.
        EventLookTest.onServer(context, (player, manager) -> {
            manager.expeditions().openEntry(true);
            manager.expeditions().testPlaytest(true);
        });
        context.waitTicks(5);
        basin(context, "zellen-brunnen (playtest)", new BlockPos(fx, hy - 1, bz + 32 + 25), hy);
        final BlockPos lava = new BlockPos(fx + 6, hy - 1, bz + 32 + 2);
        final BlockState[] saved = new BlockState[2];
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            saved[0] = level.getBlockState(lava);
            saved[1] = level.getBlockState(lava.below());
            level.setBlock(lava.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(lava, Blocks.LAVA.defaultBlockState(), 2);
        });
        context.waitTicks(5);
        basin(context, "lava (playtest)", lava, hy);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            level.setBlock(lava, saved[0], 2);
            level.setBlock(lava.below(), saved[1], 2);
            player.clearFire();
        });
        context.waitTicks(5);
        final String[] before = new String[1];
        EventLookTest.onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
            before[0] = where(player) + " " + manager.expeditions().testState();
            to(player, plan.door.getX() + 0.5, plan.door.getY(), plan.door.getZ() + 0.5, 180f);   // out before the gatehouse
        });
        context.waitTicks(15);
        EventLookTest.onServer(context, (player, manager) -> log("hinaus (playtest) von " + before[0] + " -> kämpfer="
                + manager.expeditions().isFighter(player) + " drinnen=" + manager.expeditions().testInside(player) + " bei " + where(player)
                + " | " + manager.expeditions().testState() + " | " + manager.expeditions().testWake()));
        EventLookTest.onServer(context, (player, manager) -> {
            manager.expeditions().testEndAll(player.level().getServer(), false);
            manager.expeditions().openEntry(false);
            manager.expeditions().testPlaytest(false);
            log("kampf beendet: kämpfer=" + manager.expeditions().isFighter(player) + " bei " + where(player));
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);

        // 8. The stair gate is the barrier until the round at the seal stone is ready: normal, then the playtest's.
        for (int mode = 0; mode < 2; mode++) {
            final boolean playtest = mode == 1;
            final String tag = playtest ? "playtest" : "normal";
            EventLookTest.onServer(context, (player, manager) -> {
                manager.expeditions().openEntry(playtest);
                manager.expeditions().testPlaytest(playtest);
            });
            context.waitTicks(10);
            gateWalk(context, tag + " vor der Runde", fx, fy, bz);
            EventLookTest.onServer(context, (player, manager) -> to(player, fx + 0.5, fy - 4, bz + 3.5, 0f));   // behind the gate, on the stair
            context.waitTicks(15);
            EventLookTest.onServer(context, (player, manager) -> log("treppe hinter dem gitter (" + tag + "): kämpfer="
                    + manager.expeditions().isFighter(player) + " bei " + where(player) + " (Tor " + fx + " " + fy + " " + (bz - 8) + ")"));
            EventLookTest.onServer(context, (player, manager) -> {
                to(player, fx + 2.5, fy, bz - 3.5, 0f);
                com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
                boolean used = manager.expeditions().onSeal(player, plan.seal);
                log("siegelstein (" + tag + "): used=" + used + " inRound=" + manager.expeditions().inRound(player));
            });
            context.waitTicks(10);
            EventLookTest.onServer(context, (player, manager) -> manager.onPlayClass(player,
                    manager.save().skillsOf(player.getUUID().toString()).chosenClass));
            context.waitTicks(10);
            EventLookTest.closeScreen(context);
            context.waitTicks(50);
            gateWalk(context, tag + " nach der Runde", fx, fy, bz);
            context.waitTicks(10);
            EventLookTest.closeScreen(context);
            EventLookTest.onServer(context, (player, manager) -> {
                log("nach dem gang (" + tag + "): kämpfer=" + manager.expeditions().isFighter(player) + " | "
                        + manager.expeditions().testState());
                manager.expeditions().testEndAll(player.level().getServer(), false);
            });
            context.waitTicks(20);
            EventLookTest.closeScreen(context);
        }
        // A gate left open without a round (as after a restart in the 30 s after one): it shuts again.
        EventLookTest.onServer(context, (player, manager) -> {
            manager.expeditions().openEntry(false);
            manager.expeditions().testPlaytest(false);
            ServerLevel level = (ServerLevel) player.level();
            for (BlockPos p : manager.expeditions().testPlan().stairGate) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            to(player, fx + 0.5, fy, bz - 7.5, 0f);   // before the gatehouse
        });
        context.waitTicks(120);
        EventLookTest.onServer(context, (player, manager) -> log("offenes gitter ohne runde nach 6 s: " + bars(player, manager)));
        EventLookTest.onServer(context, (player, manager) -> {
            manager.expeditions().remove(player.level().getServer());
            if (fakeCore[0]) {
                manager.save().cores.removeIf(c -> c.x == pos.getX() && c.y == pos.getY() && c.z == pos.getZ());
            }
        });
        context.waitTicks(20);
    }

    private static String bars(ServerPlayer player, com.mki.nightsiege.game.SiegeManager manager) {
        com.mki.nightsiege.game.ExpeditionLayout.Plan plan = manager.expeditions().testPlan();
        if (plan == null) {
            return "kein plan";
        }
        int n = 0;
        for (BlockPos p : plan.stairGate) {
            if (player.level().getBlockState(p).is(Blocks.IRON_BARS)) {
                n++;
            }
        }
        return "gitter=" + n + "/" + plan.stairGate.size();
    }

    /** Before the stair gate in the gatehouse, then as walking south into it for 2 s: where one ends up. */
    private static void gateWalk(ClientGameTestContext context, String label, int fx, int fy, int bz) {
        final String[] gate = new String[1];
        EventLookTest.onServer(context, (player, manager) -> {
            gate[0] = bars(player, manager);
            to(player, fx + 0.5, fy, bz - 1.5, 0f);   // between the seal stone and the gate
        });
        context.waitTicks(30);
        walk(context, 0, 0.25, 40);
        context.waitTicks(15);
        EventLookTest.onServer(context, (player, manager) -> log("gitter-gang " + label + ": " + gate[0] + " -> bei " + where(player)
                + " hinter dem Gitter=" + (player.getZ() > bz - 0.3) + " auf der Treppe=" + (player.getY() < fy - 0.5)
                + " kämpfer=" + manager.expeditions().isFighter(player)));
    }

    /**
     * Into the basin at {@code at} (the feet there), then every 10 ticks a look: out of it within ~3 s on
     * the floor at {@code floor}, still fighting, the cleared rooms and the attempt kept.
     */
    private static void basin(ClientGameTestContext context, String label, BlockPos at, int floor) {
        final long[] start = new long[1];
        final int[] before = new int[2];
        final long[] outAfter = {-1L};
        final StringBuilder trail = new StringBuilder();
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            to(player, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f);
            start[0] = level.getGameTime();
            com.mki.nightsiege.game.SiegeSave.ExpeditionRecord rec = manager.save().expedition;
            before[0] = rec == null ? -1 : rec.cleared;
            before[1] = rec == null ? -1 : rec.attempts;
            log(label + ": hinein bei " + where(player) + " " + level.getBlockState(at).getBlock() + " | " + manager.expeditions().testWake());
        });
        for (int i = 0; i < 9; i++) {
            context.waitTicks(10);
            EventLookTest.onServer(context, (player, manager) -> {
                long t = player.level().getGameTime() - start[0];
                boolean wet = player.isInWater() || player.isInLava();
                trail.append(' ').append(t).append(':').append(wet ? "nass" : "trocken")
                        .append(String.format(java.util.Locale.ROOT, "@%.1f", player.getY() - floor));
                if (outAfter[0] < 0 && !wet && player.getY() > floor - 0.1) {
                    outAfter[0] = t;
                }
            });
        }
        context.waitTicks(5);
        EventLookTest.onServer(context, (player, manager) -> {
            com.mki.nightsiege.game.SiegeSave.ExpeditionRecord rec = manager.save().expedition;
            log(label + ": draußen nach " + outAfter[0] + " Ticks, bei " + where(player) + " (Boden " + floor + ") kämpfer="
                    + manager.expeditions().isFighter(player) + " drinnen=" + manager.expeditions().testInside(player)
                    + " brennt=" + player.isOnFire() + " räume " + before[0] + "->" + (rec == null ? -1 : rec.cleared)
                    + " versuche " + before[1] + "->" + (rec == null ? -1 : rec.attempts) + " | " + manager.expeditions().testWake()
                    + " | verlauf" + trail);
        });
    }

    // ------------------------------------------------------------------ the war camp

    private static void camp(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("gamemode creative @p");
        for (int mode = 0; mode < 2; mode++) {
            final boolean playtest = mode == 1;
            final String tag = playtest ? "playtest" : "normal";
            final int cx = pos.getX() - 200 + mode * 100;   // each on its own ground (the first one's comes back meanwhile)
            final int cz = pos.getZ() - 260;
            final int cy = EventLookTest.yard(context, game, cx, cz, 36, 18);
            EventLookTest.onServer(context, (player, manager) -> {
                ServerLevel level = (ServerLevel) player.level();
                if (!manager.isRegistered(player)) {
                    manager.register(player);
                }
                if (!manager.save().skillsOf(player.getUUID().toString()).hasClass()) {
                    manager.onChooseClass(player, "WARRIOR");
                }
                manager.camp().openEntry(playtest);
                manager.camp().testCamp(level, new BlockPos(cx, cy, cz), 0, playtest ? 999_989L : 999_990L);
                log("lager gebaut (" + tag + "): " + manager.camp().testRound(level));
            });
            context.waitTicks(20);
            EventLookTest.closeScreen(context);
            game.getServer().runCommand("gamemode survival @p");
            game.getServer().runCommand("effect give @p minecraft:resistance 600 255 true");
            context.waitTicks(10);
            EventLookTest.onServer(context, (player, manager) -> {
                log("lager vor der runde (" + tag + "): " + manager.camp().testRound((ServerLevel) player.level()));
                to(player, cx + 0.5, cy, cz + 22.5, 180f);   // on the forecourt's path before the gate
            });
            context.waitTicks(40);
            walk(context, 0, -0.25, 40);   // north, into the gate
            context.waitTicks(15);
            EventLookTest.onServer(context, (player, manager) -> log("lager-gang (" + tag + "): bei " + where(player)
                    + " im Lager=" + (player.getZ() < cz + 18.0) + " kämpfer=" + manager.camp().isFighter(player)
                    + " (Tor bei z " + (cz + 18) + ")"));
            EventLookTest.onServer(context, (player, manager) -> to(player, cx + 0.5, cy, cz + 14.5, 180f));   // inside the palisade
            context.waitTicks(20);
            EventLookTest.onServer(context, (player, manager) -> log("lager hineingesetzt (" + tag + "): bei " + where(player)
                    + " im Lager=" + (player.getZ() < cz + 18.0) + " kämpfer=" + manager.camp().isFighter(player)));
            EventLookTest.onServer(context, (player, manager) -> {
                BlockPos path = manager.camp().testForecourtPath();
                to(player, path.getX() + 0.5, path.getY(), path.getZ() + 0.5, 180f);
            });
            context.waitTicks(10);
            EventLookTest.onServer(context, (player, manager) -> {
                boolean struck = manager.camp().onDrum(player, manager.camp().testDrum());
                log("startstein (" + tag + "): " + struck + " inRound=" + manager.camp().inRound(player));
                manager.onPlayClass(player, manager.save().skillsOf(player.getUUID().toString()).chosenClass);
            });
            context.waitTicks(25);
            EventLookTest.closeScreen(context);
            EventLookTest.onServer(context, (player, manager) -> log("lager nach der runde (" + tag + "): bei " + where(player)
                    + " im Lager=" + (player.getZ() < cz + 18.0) + " kämpfer=" + manager.camp().isFighter(player)
                    + " | " + manager.camp().testRound((ServerLevel) player.level())));
            EventLookTest.onServer(context, (player, manager) -> {
                ServerLevel level = (ServerLevel) player.level();
                manager.camp().testRemove(level.getServer());
                int bars = 0;
                for (int lx = -2; lx <= 2; lx++) {
                    for (int ly = 0; ly <= 4; ly++) {
                        if (level.getBlockState(new BlockPos(cx + lx, cy + ly, cz + 18)).is(Blocks.IRON_BARS)) {
                            bars++;
                        }
                    }
                }
                int temp = 0;
                for (BlockPos p : com.mki.nightsiege.game.TempBlocks.all(level, Blocks.IRON_BARS)) {
                    if (Math.abs(p.getX() - cx) <= 40 && Math.abs(p.getZ() - cz) <= 40) {
                        temp++;
                    }
                }
                manager.camp().openEntry(false);
                log("lager weg (" + tag + "): gitter im Tor=" + bars + " gemerkte Nacht-Gitter=" + temp + " kämpfer="
                        + manager.camp().isFighter(player));
            });
            context.waitTicks(40);
            game.getServer().runCommand("gamemode creative @p");
        }
    }
}
