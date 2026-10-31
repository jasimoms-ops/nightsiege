package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Blocks that stand for one night only: barricades, the war banner, the ladders of
 * climbing arrows, the light of light arrows and the shield lantern. They are kept in
 * the save, broken without drops, and taken away when the night ends (or at the next
 * start, after a crash).
 */
public final class TempBlocks {

    private static Supplier<SiegeSave> save = () -> null;
    private static Runnable persist = () -> { };
    /** "dimension|x|y|z" of every standing temporary block, for quick lookups. */
    private static final Set<String> INDEX = new HashSet<>();
    /** The save list changed and should be written (done every few seconds, not on every lantern step). */
    private static boolean dirty;

    private TempBlocks() {
    }

    public static void init(Supplier<SiegeSave> saveSupplier, Runnable persistAction) {
        save = saveSupplier;
        persist = persistAction;
        rebuildIndex();
    }

    /** Re-reads the index from the save (after a reload). */
    public static void rebuildIndex() {
        INDEX.clear();
        SiegeSave s = save.get();
        if (s == null || s.tempBlocks == null) {
            return;
        }
        for (String entry : s.tempBlocks) {
            int cut = entry.lastIndexOf('|');
            if (cut > 0) {
                INDEX.add(entry.substring(0, cut));
            }
        }
    }

    private static String key(Level level, BlockPos pos) {
        return SiegeManager.dimensionName(level) + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    /** Sets a block for the night if the spot is free. Returns true when it stands. */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState there = level.getBlockState(pos);
        if (!(there.isAir() || there.canBeReplaced()) || !there.getFluidState().isEmpty()) {
            return false;
        }
        if (state.is(Blocks.LIGHT) && !there.isAir()) {
            return false;   // light only into air: in water it took the source away for good (Simon 31.10.: survival safety)
        }
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        remember(level, pos, state);
        return true;
    }

    /** Records a block someone else already set (e.g. after its own shape update). */
    public static void remember(ServerLevel level, BlockPos pos, BlockState state) {
        SiegeSave s = save.get();
        if (s == null) {
            return;
        }
        if (s.tempBlocks == null) {
            s.tempBlocks = new ArrayList<>();
        }
        String key = key(level, pos);
        s.tempBlocks.removeIf(e -> e.startsWith(key + "|"));
        s.tempBlocks.add(key + "|" + BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        INDEX.add(key);
        dirty = true;
    }

    public static boolean isTemp(Level level, BlockPos pos) {
        return !INDEX.isEmpty() && INDEX.contains(key(level, pos));
    }

    /** Takes one temporary block away now (no drops). */
    public static void remove(ServerLevel level, BlockPos pos) {
        String key = key(level, pos);
        SiegeSave s = save.get();
        String blockId = null;
        if (s != null && s.tempBlocks != null) {
            for (String e : s.tempBlocks) {
                if (e.startsWith(key + "|")) {
                    blockId = e.substring(key.length() + 1);
                }
            }
            s.tempBlocks.removeIf(e -> e.startsWith(key + "|"));
        }
        INDEX.remove(key);
        if (blockId != null && matches(level.getBlockState(pos), blockId)) {
            NightDamage.quietly(() -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        }
        dirty = true;
    }

    /** Forgets a temporary block that is already gone. */
    public static void forget(Level level, BlockPos pos) {
        String key = key(level, pos);
        SiegeSave s = save.get();
        if (s != null && s.tempBlocks != null) {
            s.tempBlocks.removeIf(e -> e.startsWith(key + "|"));
        }
        INDEX.remove(key);
    }

    /** End of the night: every temporary block goes. Returns how many were taken away. */
    public static int clearAll(MinecraftServer server) {
        SiegeSave s = save.get();
        if (server == null || s == null || s.tempBlocks == null || s.tempBlocks.isEmpty()) {
            INDEX.clear();
            return 0;
        }
        int removed = 0;
        for (String entry : new ArrayList<>(s.tempBlocks)) {
            String[] parts = entry.split("\\|");
            if (parts.length < 5) {
                continue;
            }
            ServerLevel level = SiegeManager.levelByName(server, parts[0]);
            if (level == null) {
                continue;
            }
            try {
                BlockPos pos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if (matches(level.getBlockState(pos), parts[4])) {
                    NightDamage.quietly(() -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
                    removed++;
                }
            } catch (NumberFormatException e) {
                NightSiegeMod.LOGGER.warn("[Night Siege] Kaputter Nacht-Block im Save: {}", entry);
            }
        }
        s.tempBlocks.clear();
        INDEX.clear();
        dirty = false;
        persist.run();
        return removed;
    }

    /** Writes the save if temporary blocks changed since the last write. */
    public static void flush() {
        if (dirty) {
            dirty = false;
            persist.run();
        }
    }

    private static boolean matches(BlockState state, String blockId) {
        Identifier id = Identifier.tryParse(blockId);
        return id != null && BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
    }

    /** All temporary blocks of one kind of block, for the gametest and the banner. */
    public static List<BlockPos> all(Level level, Block block) {
        List<BlockPos> out = new ArrayList<>();
        SiegeSave s = save.get();
        if (s == null || s.tempBlocks == null) {
            return out;
        }
        String dim = SiegeManager.dimensionName(level) + "|";
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        for (String entry : s.tempBlocks) {
            String[] parts = entry.split("\\|");
            if (parts.length >= 5 && entry.startsWith(dim) && parts[4].equals(id)) {
                try {
                    out.add(new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
                } catch (NumberFormatException ignored) {
                    // skipped
                }
            }
        }
        return out;
    }
}
