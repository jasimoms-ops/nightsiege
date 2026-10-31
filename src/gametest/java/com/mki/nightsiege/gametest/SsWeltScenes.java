package com.mki.nightsiege.gametest;

import com.mki.nightsiege.core.TrapKind;
import com.mki.nightsiege.game.ModContent;
import com.mki.nightsiege.game.MobFactory;
import com.mki.nightsiege.game.NightDamage;
import com.mki.nightsiege.game.SiegeManager;
import com.mki.nightsiege.game.TempBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Survival safety, the world (Simon 31.10.): the mending after a REAL night on a small test base.
 * A breach with a barricade and a ladder in it is closed after the night; a player's TNT (inside and
 * outside the base radius) is not rebuilt; a siege creeper's blast is rebuilt and leaves no drops
 * (also no trap item, the chest beside it stands); a wall block a player took out stays out; sand a
 * player let fall is not doubled; the damage log on disk mends after a "crash"; a light arrow keeps
 * the water; a chest and a block a player set into the war camp survive its teardown.
 * Log lines start with {@code [ss-welt]}.
 */
final class SsWeltScenes {

    private SsWeltScenes() {
    }

    private static void log(String line) {
        System.out.println("[ss-welt] " + line);
    }

    private static int count(ServerLevel level, List<BlockPos> spots, Block block) {
        int n = 0;
        for (BlockPos p : spots) {
            if (level.getBlockState(p).is(block)) {
                n++;
            }
        }
        return n;
    }

    private static String drops(ServerLevel level, BlockPos at, double r) {
        Map<String, Integer> out = new HashMap<>();
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(r))) {
            out.merge(e.getItem().getItem().toString(), e.getItem().getCount(), Integer::sum);
        }
        return out.isEmpty() ? "keine" : out.toString();
    }

    private static List<BlockPos> box(int x0, int y0, int z0, int x1, int y1, int z1) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    out.add(new BlockPos(x, y, z));
                }
            }
        }
        return out;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext game) {
        EventLookTest.ensureCore(context, game);
        int[] c = context.computeOnClient(client -> {
            com.mki.nightsiege.game.CoreRecord core = SiegeManager.get().mainCore();
            return new int[]{core.x, core.y, core.z};
        });
        int cx = c[0];
        int cy = c[1];
        int cz = c[2];
        game.getServer().runCommand("gamemode creative @p");
        game.getServer().runCommand("gamerule mob_griefing true");
        game.getServer().runCommand("gamerule mobGriefing true");
        int far = cx + 120;   // outside the defended area (base radius + field), inside the old 192
        game.getServer().runCommand("forceload add %d %d %d %d".formatted(far - 8, cz - 8, far + 8, cz + 8));
        context.waitTicks(40);

        BlockPos w1 = new BlockPos(cx + 8, cy, cz);            // the breach (barricade, ladder above)
        BlockPos w1up = w1.above();
        BlockPos w2 = new BlockPos(cx - 8, cy + 1, cz);        // a player takes this one out
        List<BlockPos> tntIn = box(cx - 4, cy, cz + 3, cx - 2, cy + 1, cz + 5);   // a player's TNT, inside
        List<BlockPos> hut = box(cx + 2, cy, cz - 5, cx + 4, cy + 2, cz - 5);     // a siege creeper's blast
        BlockPos torch = new BlockPos(cx + 3, cy + 1, cz - 6);
        BlockPos creeperAt = new BlockPos(cx + 3, cy, cz - 4);
        BlockPos chest = new BlockPos(cx + 5, cy, cz - 4);
        BlockPos trap = new BlockPos(cx + 1, cy, cz - 4);
        BlockPos sandSupport = new BlockPos(cx - 5, cy, cz - 5);
        BlockPos water = new BlockPos(cx - 6, cy - 1, cz + 6);
        final int[] farY = new int[1];
        final int[] counts = new int[8];
        final Path[] backup = new Path[1];
        final List<BlockPos> hutLostSpots = new ArrayList<>();

        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
            for (int i = -8; i <= 8; i++) {
                for (int y = cy; y <= cy + 3; y++) {
                    level.setBlock(new BlockPos(cx + i, y, cz - 8), bricks, 2);
                    level.setBlock(new BlockPos(cx + i, y, cz + 8), bricks, 2);
                    level.setBlock(new BlockPos(cx - 8, y, cz + i), bricks, 2);
                    level.setBlock(new BlockPos(cx + 8, y, cz + i), bricks, 2);
                }
            }
            for (BlockPos p : tntIn) {
                level.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 2);
            }
            for (BlockPos p : hut) {
                level.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 2);
            }
            level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH), 3);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(chest) instanceof ChestBlockEntity box) {
                box.setItem(0, new ItemStack(Items.DIAMOND, 3));
            }
            level.setBlock(trap, ModContent.TRAP_BLOCKS.get(TrapKind.SPIKE).defaultBlockState(), 3);
            manager.trapService().onPlaced(level, trap, player, TrapKind.SPIKE);
            level.setBlock(sandSupport, Blocks.OAK_PLANKS.defaultBlockState(), 3);
            level.setBlock(sandSupport.above(), Blocks.SAND.defaultBlockState(), 3);
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
            farY[0] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far, cz);
            for (BlockPos p : box(far - 1, farY[0], cz - 1, far + 1, farY[0] + 1, cz + 1)) {
                level.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 2);
            }
            for (BlockPos p : box(far - 1, farY[0], cz + 5, far + 1, farY[0] + 1, cz + 7)) {
                level.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 2);
            }
            player.teleportTo(level, cx + 0.5, cy, cz + 1.5, java.util.Set.of(), 0f, 0f, true);
            log("setup core=" + cx + " " + cy + " " + cz + " farY=" + farY[0] + " trap=" + (manager.trapService().recordAt(level, trap) != null)
                    + " baseRadius=" + manager.config().baseRadius);
        });
        context.waitTicks(20);
        List<BlockPos> farTnt = box(far - 1, farY[0], cz - 1, far + 1, farY[0] + 1, cz + 1);
        List<BlockPos> farCreeper = box(far - 1, farY[0], cz + 5, far + 1, farY[0] + 1, cz + 7);

        // A real night (not a test night), started at the core.
        game.getServer().runCommand("execute as @p at @p run nightsiege start");
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            log("night active=" + manager.isActive() + " recording=" + NightDamage.recording(level) + " " + NightDamage.state());
            // The breach, broken as the mod's breakers do it (a sapper's crater), then a barricade and a ladder in it.
            for (BlockPos p : List.of(w1, w1up)) {
                NightDamage.record(level, p, level.getBlockState(p));
                NightDamage.asAttacker(() -> level.destroyBlock(p, false));
            }
            boolean fence = TempBlocks.place(level, w1, Blocks.OAK_FENCE.defaultBlockState());
            boolean ladder = TempBlocks.place(level, w1up, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
            log("breach: lost=" + NightDamage.isLost(w1) + "/" + NightDamage.isLost(w1up) + " barricade=" + fence + " ladder=" + ladder
                    + " now " + level.getBlockState(w1).getBlock() + " / " + level.getBlockState(w1up).getBlock());
            // A player opens the wall and knocks the support from under the sand (bare-handed: in creative a
            // kit sword breaks nothing).
            ItemStack held = player.getMainHandItem().copy();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            boolean took = player.gameMode.destroyBlock(w2);
            boolean support = player.gameMode.destroyBlock(sandSupport);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
            log("player took wall=" + took + " lost=" + NightDamage.isLost(w2) + " support=" + support + " mode=" + player.gameMode());
            // A player's TNT inside and outside the base, and a siege creeper inside and outside.
            for (BlockPos at : List.of(new BlockPos(cx - 3, cy + 2, cz + 4), new BlockPos(far, farY[0] + 2, cz))) {
                PrimedTnt tnt = new PrimedTnt(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player);
                tnt.setFuse(2);
                level.addFreshEntity(tnt);
            }
            for (BlockPos at : List.of(creeperAt, new BlockPos(far, farY[0] + 2, cz + 6))) {
                Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.TRIGGERED);
                if (creeper != null) {
                    creeper.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                    creeper.addTag(MobFactory.TAG);
                    creeper.setNoAi(true);
                    creeper.setPersistenceRequired();
                    level.addFreshEntity(creeper);
                    creeper.ignite();
                }
            }
            counts[0] = count(level, tntIn, Blocks.OAK_PLANKS);
        });
        context.waitTicks(60);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            counts[1] = count(level, tntIn, Blocks.OAK_PLANKS);
            counts[2] = count(level, hut, Blocks.OAK_PLANKS);
            counts[3] = count(level, farTnt, Blocks.OAK_PLANKS);
            counts[4] = count(level, farCreeper, Blocks.OAK_PLANKS);
            int hutLost = 0;
            for (BlockPos p : hut) {
                if (NightDamage.isLost(p)) {
                    hutLost++;
                    hutLostSpots.add(p);
                }
            }
            int tntLost = 0;
            for (BlockPos p : tntIn) {
                if (NightDamage.isLost(p)) {
                    tntLost++;
                }
            }
            log("after blasts: player TNT inside planks " + counts[0] + "->" + counts[1] + " lost=" + tntLost
                    + " drops " + drops(level, new BlockPos(cx - 3, cy, cz + 4), 4));
            log("after blasts: creeper inside planks 9->" + counts[2] + " lost=" + hutLost + " torch lost=" + NightDamage.isLost(torch)
                    + " drops " + drops(level, creeperAt, 4.5) + " chest=" + level.getBlockState(chest).getBlock()
                    + " trap=" + level.getBlockState(trap).getBlock());
            log("after blasts: outside TNT planks 18->" + counts[3] + " outside creeper planks 18->" + counts[4]
                    + " lost far=" + NightDamage.isLost(new BlockPos(far, farY[0], cz)) + "/" + NightDamage.isLost(new BlockPos(far, farY[0], cz + 6))
                    + " sand=" + count(level, box(sandSupport.getX(), cy - 1, sandSupport.getZ(), sandSupport.getX(), cy + 2, sandSupport.getZ()), Blocks.SAND));
            // The log on disk now (as every 30 s); a copy for the crash test below.
            NightDamage.flush(level.getServer());
            Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("nightsiege").resolve("night-damage.dat");
            try {
                if (Files.exists(file)) {
                    backup[0] = file.resolveSibling("ss-welt-backup.dat");
                    Files.copy(file, backup[0], StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (java.io.IOException e) {
                log("backup failed: " + e);
            }
            log("log on disk: exists=" + Files.exists(file) + " size=" + (Files.exists(file) ? file.toFile().length() : 0) + " " + NightDamage.state());
        });
        context.waitTicks(20);

        // Morning (as when the night is held).
        game.getServer().runCommand("nightsiege stop");
        context.waitTicks(60);
        EventLookTest.closeScreen(context);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("nightsiege").resolve("night-damage.dat");
            int sand = count(level, box(sandSupport.getX(), cy - 1, sandSupport.getZ(), sandSupport.getX(), cy + 2, sandSupport.getZ()), Blocks.SAND);
            boolean diamonds = level.getBlockEntity(chest) instanceof Container box && box.countItem(Items.DIAMOND) == 3;
            log("RESULT breach closed=" + (level.getBlockState(w1).is(Blocks.STONE_BRICKS) && level.getBlockState(w1up).is(Blocks.STONE_BRICKS))
                    + " (" + level.getBlockState(w1).getBlock() + " / " + level.getBlockState(w1up).getBlock() + ")"
                    + " fencesLeft=" + TempBlocks.all(level, Blocks.OAK_FENCE).size());
            log("RESULT player wall stays out=" + level.getBlockState(w2).isAir() + " (" + level.getBlockState(w2).getBlock() + ")");
            log("RESULT player TNT inside not rebuilt=" + (count(level, tntIn, Blocks.OAK_PLANKS) == counts[1])
                    + " planks " + counts[1] + "->" + count(level, tntIn, Blocks.OAK_PLANKS));
            log("RESULT player TNT outside not rebuilt=" + (count(level, farTnt, Blocks.OAK_PLANKS) == counts[3])
                    + " siege creeper outside not rebuilt=" + (count(level, farCreeper, Blocks.OAK_PLANKS) == counts[4]));
            log("RESULT siege creeper inside rebuilt=" + (count(level, hut, Blocks.OAK_PLANKS) == 9) + " planks " + counts[2] + "->"
                    + count(level, hut, Blocks.OAK_PLANKS) + " torch=" + level.getBlockState(torch).getBlock()
                    + " chest+diamonds=" + diamonds + " trap=" + level.getBlockState(trap).getBlock()
                    + " drops now " + drops(level, creeperAt, 4.5));
            log("RESULT sand not doubled=" + (sand <= 1) + " sand=" + sand + " log file gone=" + !Files.exists(file) + " " + NightDamage.state());
            // A light arrow into water: the water stays.
            boolean light = TempBlocks.place(level, water, Blocks.LIGHT.defaultBlockState());
            log("RESULT light into water refused=" + !light + " water=" + level.getBlockState(water).getBlock());
            // "Crash": the log of the night is back on disk while the craters are open again; the start mends them.
            if (backup[0] != null) {
                for (BlockPos p : hutLostSpots) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
                try {
                    Files.copy(backup[0], file, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(backup[0]);
                } catch (java.io.IOException e) {
                    log("restore copy failed: " + e);
                }
                NightDamage.recover(level.getServer());
                log("crash: recovered " + NightDamage.state());
            } else {
                log("crash: no log was on disk");
            }
        });
        context.waitTicks(60);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("nightsiege").resolve("night-damage.dat");
            log("RESULT crash mended=" + (count(level, hutLostSpots, Blocks.OAK_PLANKS) == hutLostSpots.size())
                    + " planks " + count(level, hutLostSpots, Blocks.OAK_PLANKS) + "/" + hutLostSpots.size()
                    + " file gone=" + !Files.exists(file) + " " + NightDamage.state());
        });
        context.waitTicks(10);
        campScene(context, game, cx - 200, cz + 150);
    }

    /** A chest with something in it, an empty chest and a cobblestone a player set into the war camp: they stay when it goes. */
    private static void campScene(ClientGameTestContext context, TestSingleplayerContext game, int x, int z) {
        int y = EventLookTest.yard(context, game, x, z, 36, 18);
        final Map<BlockPos, BlockState> before = new HashMap<>();
        BlockPos full = new BlockPos(x + 6, y + 1, z + 6);
        BlockPos empty = new BlockPos(x - 6, y + 1, z + 6);
        BlockPos cobble = new BlockPos(x - 6, y + 1, z - 6);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            for (BlockPos p : box(x - 30, y - 12, z - 30, x + 30, y + 16, z + 30)) {
                before.put(p, level.getBlockState(p));
            }
            manager.camp().testCamp(level, new BlockPos(x, y, z), 0, 999_993L);
            level.setBlock(full, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(full) instanceof ChestBlockEntity box) {
                box.setItem(0, new ItemStack(Items.EMERALD, 7));
            }
            level.setBlock(empty, Blocks.CHEST.defaultBlockState(), 3);
            level.setBlock(cobble, Blocks.COBBLESTONE.defaultBlockState(), 3);
            log("camp built: " + manager.camp().groundState());
        });
        context.waitTicks(20);
        EventLookTest.onServer(context, (player, manager) -> manager.camp().testRemove(player.level().getServer()));
        context.waitTicks(60);
        EventLookTest.onServer(context, (player, manager) -> {
            ServerLevel level = (ServerLevel) player.level();
            int diffs = 0;
            StringBuilder first = new StringBuilder();
            for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
                BlockPos p = e.getKey();
                if (p.equals(full) || p.equals(empty) || p.equals(cobble)) {
                    continue;
                }
                BlockState now = level.getBlockState(p);
                if (now != e.getValue() && diffs++ < 8) {
                    first.append(' ').append(p.getX() - x).append('/').append(p.getY() - y).append('/').append(p.getZ() - z)
                            .append(' ').append(e.getValue().getBlock()).append("->").append(now.getBlock());
                }
            }
            boolean emeralds = level.getBlockEntity(full) instanceof Container box && box.countItem(Items.EMERALD) == 7;
            log("RESULT camp: full chest stays=" + (level.getBlockState(full).is(Blocks.CHEST) && emeralds)
                    + " empty chest stays=" + level.getBlockState(empty).is(Blocks.CHEST)
                    + " cobblestone stays=" + level.getBlockState(cobble).is(Blocks.COBBLESTONE)
                    + " other diffs=" + diffs + first + " " + manager.camp().groundState());
        });
        context.waitTicks(20);
        game.getServer().runCommand("forceload remove all");
    }
}
