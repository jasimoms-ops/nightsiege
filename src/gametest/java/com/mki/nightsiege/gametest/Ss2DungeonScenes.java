package com.mki.nightsiege.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;

/**
 * Survival safety 2, package "ss2-dungeon" (Simon 31 October 2026): the dungeon and the war camp.
 * <ul>
 *   <li>A fight ended by the horn, and by the server stopping: every fighter out of the sealed part,
 *       with his own things; a non-fighter logging in down in the hall goes out too.</li>
 *   <li>A fallen player on the waiting bench at the win is not buried by the hill that comes back.</li>
 *   <li>An offline fighter of a won dungeon logs in after the teardown in the rock where the hall
 *       was: he is put on top.</li>
 *   <li>A spawner where the dungeon goes keeps its monster through build and teardown.</li>
 *   <li>"dungeon bauen" over a player's building: the place is turned down and moved.</li>
 *   <li>The camp: a cow and a villager are put out of its ground; the hill that comes back with its
 *       ground does not bury the player standing there, nor one logging in in it later.</li>
 * </ul>
 * Lines start with "[ss2-dungeon]".
 */
final class Ss2DungeonScenes {

    private Ss2DungeonScenes() {
    }

    private static void log(String text) {
        System.out.println("[ss2-dungeon] " + text);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        try {
            dungeon(context, game);
        } catch (RuntimeException e) {
            log("FEHLER dungeon: " + e);
            e.printStackTrace();
        }
        try {
            buildSite(context, game);
        } catch (RuntimeException e) {
            log("FEHLER bauen: " + e);
            e.printStackTrace();
        }
        try {
            camp(context, game);
        } catch (RuntimeException e) {
            log("FEHLER lager: " + e);
            e.printStackTrace();
        }
        game.getServer().runCommand("forceload remove all");
        game.getServer().runCommand("gamemode creative @p");
        log("ende");
    }

    private static int shells(ServerPlayer player) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(net.minecraft.world.item.Items.NAUTILUS_SHELL)) {
                n += player.getInventory().getItem(i).getCount();
            }
        }
        return n;
    }

    private static String where(Entity e) {
        return e.blockPosition().getX() + " " + e.blockPosition().getY() + " " + e.blockPosition().getZ();
    }

    private static String spawnerData(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        return be == null ? "kein" : be.saveWithoutMetadata(level.registryAccess()).toString();
    }

    // ------------------------------------------------------------------ the dungeon

    private static void dungeon(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        game.getServer().runCommand("time set 1500");
        game.getServer().runCommand("weather clear");
        game.getServer().runCommand("gamemode creative @p");
        int rooms = 1;
        int fx = pos.getX() + 420;
        int bz = pos.getZ() - 40;
        int fy = EventLookTest.dungeonGround(context, game, fx, bz, rooms);
        // Rock round the first hall (it comes back after the teardown), a hill where the waiting benches go
        // (levelled away, it comes back last), a skeleton spawner in the hall.
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(fx - 17, fy - 22, bz + 30, fx + 17, fy - 7, bz + 63));
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(fx - 6, fy, bz - 1, fx + 6, fy + 3, bz + 5));
        BlockPos spawner = new BlockPos(fx + 4, fy - 18, bz + 50);
        game.getServer().runCommand("setblock %d %d %d minecraft:spawner{SpawnData:{entity:{id:\"minecraft:skeleton\"}}}"
                .formatted(spawner.getX(), spawner.getY(), spawner.getZ()));
        context.waitTicks(10);
        final String[] spawnerBefore = new String[1];
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!manager.isRegistered(player)) {
                manager.register(player);
            }
            if (manager.mainCore() == null) {
                manager.save().cores.add(new com.mki.nightsiege.game.CoreRecord(
                        com.mki.nightsiege.game.SiegeManager.dimensionName(level), pos.getX(), pos.getY(), pos.getZ()));
            }
            if (!manager.save().skillsOf(player.getUUID().toString()).hasClass()) {
                manager.onChooseClass(player, "WARRIOR");
            }
            spawnerBefore[0] = spawnerData(level, spawner);
            manager.expeditions().testBuild(level, new BlockPos(fx, fy, bz), 0, com.mki.nightsiege.core.ExpeditionKind.STONE_CRYPT, rooms);
            log("gebaut: " + manager.expeditions().testState() + " spawner=" + level.getBlockState(spawner).getBlock()
                    + " skelett=" + spawnerData(level, spawner).contains("skeleton") + " vorher=" + spawnerBefore[0].contains("skeleton"));
        });
        context.waitTicks(20);
        EventLookTest.closeScreen(context);

        // 1. The horn mid-fight: out of the hall, the own things back.
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("effect give @p minecraft:resistance 900 255 true");
        game.getServer().runCommand("clear @p minecraft:nautilus_shell");
        game.getServer().runCommand("give @p minecraft:nautilus_shell 3");
        context.waitTicks(10);
        for (int round = 0; round < 2; round++) {
            final boolean horn = round == 0;
            EventLookTest.onServer(context, (player, manager) -> {
                manager.expeditions().testJoin(player);
                log((horn ? "horn" : "stopp") + " vorher: kämpfer=" + manager.expeditions().isFighter(player)
                        + " kit=" + manager.kits().hasStash(player) + " muscheln=" + shells(player));
            });
            context.waitTicks(20);
            EventLookTest.closeScreen(context);
            game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(fx, fy - 20, bz + 45));
            context.waitTicks(20);
            EventLookTest.onServer(context, (player, manager) -> {
                boolean sealedBefore = manager.expeditions().testSealed(player);
                String at = where(player);
                manager.expeditions().testEndAll(player.level().getServer(), horn);
                log((horn ? "horn" : "stopp") + ": in der Halle=" + sealedBefore + " bei " + at + " -> kämpfer="
                        + manager.expeditions().isFighter(player) + " kit=" + manager.kits().hasStash(player)
                        + " muscheln=" + shells(player) + " eingeschlossen=" + manager.expeditions().testSealed(player)
                        + " bei " + where(player) + " (Tor " + fx + " " + fy + " " + (bz - 8) + ")");
            });
            context.waitTicks(20);
        }
        // A player who logs in down in the hall without fighting (logged out mid-fight, a crash): out.
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            player.teleportTo(level, fx + 0.5, fy - 20, bz + 45.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
            boolean before = manager.expeditions().testSealed(player);
            manager.expeditions().testArrive(player);
            log("anmeldung in der halle: vorher eingeschlossen=" + before + " nachher=" + manager.expeditions().testSealed(player)
                    + " bei " + where(player));
        });
        context.waitTicks(20);

        // 2. A fallen player on the waiting bench when the dungeon is won.
        EventLookTest.onServer(context, (player, manager) -> manager.expeditions().testJoin(player));
        context.waitTicks(20);
        EventLookTest.closeScreen(context);
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(fx, fy - 20, bz + 45));
        context.waitTicks(20);
        EventLookTest.onServer(context, (player, manager) -> {
            manager.expeditions().testFall(player);
            log("gefallen: kämpfer=" + manager.expeditions().isFighter(player) + " bei " + where(player)
                    + " (Bänke bei z " + bz + ".." + (bz + 3) + ")");
        });
        context.waitTicks(20);
        EventLookTest.onServer(context, (player, manager) -> {
            manager.expeditions().testWin(player.level().getServer());
            manager.expeditions().testCollapseNow(player.level().getServer());
        });
        context.waitTicks(10);
        EventLookTest.onServer(context, (player, manager) -> log("eingestürzt: spieler bei " + where(player) + " | "
                + manager.expeditions().testState() + " | " + manager.expeditions().testSafeGround()));
        // As one who walked back to the bench while it is taken down: the hill comes back round him.
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            player.teleportTo(level, fx - 3.5, fy, bz + 1.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        });
        boolean done = false;
        for (int i = 0; i < 40 && !done; i++) {
            context.waitTicks(40);
            done = context.computeOnClient(client -> {
                com.mki.nightsiege.game.Expeditions exp = com.mki.nightsiege.game.SiegeManager.get().expeditions();
                return exp.teardownsLeft() == 0 && com.mki.nightsiege.game.SiegeManager.get().save().expedition == null;
            });
        }
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            log("abgebaut=" + (manager.expeditions().teardownsLeft() == 0) + " bank-spieler bei " + where(player)
                    + " im Block=" + com.mki.nightsiege.game.Expeditions.testStuck(player)
                    + " oben=" + (player.getY() >= fy + 4) + " hügel=" + level.getBlockState(new BlockPos(fx - 4, fy + 1, bz + 1)).getBlock()
                    + " | " + manager.expeditions().testSafeGround());
            // 3. An offline fighter of the won dungeon logs in where the hall was (rock again).
            player.teleportTo(level, fx + 0.5, fy - 19, bz + 45.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
            boolean stuck = com.mki.nightsiege.game.Expeditions.testStuck(player);
            String block = level.getBlockState(player.blockPosition()).getBlock().toString();
            manager.expeditions().testArrive(player);
            log("offline-kämpfer: im Block " + block + "=" + stuck + " -> bei " + where(player) + " im Block="
                    + com.mki.nightsiege.game.Expeditions.testStuck(player) + " oben=" + (player.getY() >= fy - 7)
                    + " | " + manager.expeditions().testSafeGround());
            // 4. The spawner kept its monster.
            String after = spawnerData(level, spawner);
            log("spawner nach dem abbau: " + level.getBlockState(spawner).getBlock() + " skelett=" + after.contains("skeleton")
                    + " halle=" + level.getBlockState(new BlockPos(fx, fy - 15, bz + 45)).getBlock());
            log("vor dem speichern: wartend=" + manager.expeditions().teardownsUnsaved() + " einträge="
                    + manager.save().expeditionTeardowns.size());
        });
        context.waitTicks(10);
        // The world saved: the teardown's snapshot and record go now (D4).
        game.getServer().runCommand("save-all");
        context.waitTicks(40);
        EventLookTest.onServer(context, (player, manager) -> log("nach dem speichern: wartend=" + manager.expeditions().teardownsUnsaved()
                + " einträge=" + manager.save().expeditionTeardowns.size()));
        context.waitTicks(10);
        game.getServer().runCommand("gamemode creative @p");
    }

    // ------------------------------------------------------------------ "dungeon bauen" over a building

    private static void buildSite(ClientGameTestContext context, TestSingleplayerContext game) {
        final int[] spot = new int[3];
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            com.mki.nightsiege.game.SiegeSave.ExpeditionRecord rec = manager.expeditions().report(level.getServer(),
                    com.mki.nightsiege.core.ExpeditionKind.STONE_CRYPT);
            if (rec == null) {
                log("bauen: keine Meldung");
                return;
            }
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rec.x, rec.z);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    level.setBlock(new BlockPos(rec.x + dx, y, rec.z + dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }
            }
            spot[0] = rec.x;
            spot[1] = y;
            spot[2] = rec.z;
            log("bauen: gemeldet bei " + rec.x + " " + rec.z + ", Bretter bei y " + y);
        });
        context.waitTicks(20);
        if (spot[1] == 0 && spot[0] == 0) {
            return;
        }
        game.getServer().runCommand("nightsiege dungeon bauen");
        boolean built = false;
        for (int i = 0; i < 60 && !built; i++) {
            context.waitTicks(40);
            built = context.computeOnClient(client -> {
                com.mki.nightsiege.game.SiegeSave.ExpeditionRecord rec = com.mki.nightsiege.game.SiegeManager.get().save().expedition;
                return rec == null || rec.built || !com.mki.nightsiege.game.SiegeManager.get().expeditions().building();
            });
        }
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            com.mki.nightsiege.game.SiegeSave.ExpeditionRecord rec = manager.save().expedition;
            int planks = 0;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (level.getBlockState(new BlockPos(spot[0] + dx, spot[1], spot[2] + dz)).is(Blocks.OAK_PLANKS)) {
                        planks++;
                    }
                }
            }
            double moved = rec == null ? -1 : Math.sqrt(Math.pow(rec.x - spot[0], 2) + Math.pow(rec.z - spot[2], 2));
            log("bauen: " + (rec == null ? "kein Dungeon" : "gebaut=" + rec.built + " bei " + rec.x + " " + rec.y + " " + rec.z
                    + " verschoben um " + Math.round(moved)) + " bretter=" + planks + "/81 baut=" + manager.expeditions().building()
                    + " | " + manager.expeditions().testState());
            manager.expeditions().remove(level.getServer());   // its ground comes back in the background
        });
        context.waitTicks(20);
    }

    // ------------------------------------------------------------------ the war camp

    private static void camp(ClientGameTestContext context, TestSingleplayerContext game) {
        BlockPos pos = context.computeOnClient(client -> client.player.blockPosition());
        int cx = pos.getX() - 300;
        int cz = pos.getZ() + 260;
        int cy = EventLookTest.yard(context, game, cx, cz, 36, 18);
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(cx + 5, cy, cz - 2, cx + 9, cy + 3, cz + 2));
        game.getServer().runCommand("summon minecraft:cow %d %d %d {PersistenceRequired:1b}".formatted(cx + 7, cy + 4, cz));
        game.getServer().runCommand("summon minecraft:villager %d %d %d {NoAI:1b,PersistenceRequired:1b}".formatted(cx - 6, cy, cz + 6));
        context.waitTicks(20);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            manager.camp().testCamp(level, new BlockPos(cx, cy, cz), 0, 999_996L);
            StringBuilder sb = new StringBuilder();
            net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(new BlockPos(cx, cy, cz)).inflate(48, 24, 48);
            for (Entity e : level.getEntitiesOfClass(Entity.class, around, e -> {
                String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
                return type.equals("cow") || type.equals("villager");
            })) {
                double d = Math.sqrt(Math.pow(e.getX() - cx - 0.5, 2) + Math.pow(e.getZ() - cz - 0.5, 2));
                sb.append(' ').append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())
                        .append(" lebt=").append(e.isAlive()).append(" abstand=").append(Math.round(d))
                        .append(" im Block=").append(com.mki.nightsiege.game.Expeditions.testStuck(e));
            }
            log("lager gebaut:" + sb);
        });
        context.waitTicks(20);
        game.getServer().runCommand("gamemode survival @p");
        game.getServer().runCommand("tp @p %d.5 %d %d.5".formatted(cx + 7, cy, cz));
        context.waitTicks(20);
        EventLookTest.onServer(context, (player, manager) -> {
            log("lager: spieler auf dem boden bei " + where(player) + " im Block=" + com.mki.nightsiege.game.Expeditions.testStuck(player));
            manager.camp().testRemove(player.level().getServer());
        });
        context.waitTicks(120);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            log("lager weg: spieler bei " + where(player) + " im Block=" + com.mki.nightsiege.game.Expeditions.testStuck(player)
                    + " oben=" + (player.getY() >= cy + 4) + " hügel=" + level.getBlockState(new BlockPos(cx + 7, cy + 1, cz)).getBlock()
                    + " | " + manager.camp().groundState() + " | " + manager.expeditions().testSafeGround());
            // A camp fighter who logged out there logs in in the hill that came back.
            player.teleportTo(level, cx + 7.5, cy + 1, cz + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
            boolean stuck = com.mki.nightsiege.game.Expeditions.testStuck(player);
            manager.expeditions().testArrive(player);
            log("lager offline: im Block=" + stuck + " -> bei " + where(player) + " im Block="
                    + com.mki.nightsiege.game.Expeditions.testStuck(player));
        });
        context.waitTicks(10);
        game.getServer().runCommand("save-all");
        context.waitTicks(40);
        EventLookTest.onServer(context, (player, manager) -> log("lager gespeichert: " + manager.camp().groundState()));
        context.waitTicks(10);
    }
}
