package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Everything that breaks in a night comes back after it, won or lost (Simon, 27 October 2026:
 * "the map should mend itself completely after every night, also when it is lost or things burn").
 *
 * <p>Every block change in the siege world while a night runs passes {@link #changing} (a mixin on
 * {@code Level.setBlock}): whatever turns into air, fire, water or another weaker block is written
 * down – broken by monsters, blown up, burnt, washed away, or fallen off because its wall went.
 * Left out are the players' own breaking and building (and their commands), pistons, the mod's own
 * building ({@link #quietly}), temporary blocks, traps, the core and the war camp.
 *
 * <p>Chests and other containers do not break in a night at all (explosions and fire spare them),
 * so their contents never fall out. Signs, banners and heads come back with their text and pattern.
 * After the night the fire it lit goes out first, then everything is put back, full blocks first and
 * from the bottom up. What lies in an unloaded chunk then comes back as soon as the chunk loads.
 */
public final class NightDamage {

    private static final int MAX = 300_000;
    /** How far from the core (in blocks, sideways) a night's damage is written down. */
    static final int REACH = 192;
    private static final int UPDATE_MOVE_BY_PISTON = 64;

    private static ServerLevel world;
    private static BlockPos center;
    private static final Long2ObjectLinkedOpenHashMap<BlockState> LOST = new Long2ObjectLinkedOpenHashMap<>();
    /** The block-entity data of what was lost (signs, banners, heads), keyed like LOST. */
    private static final Long2ObjectOpenHashMap<CompoundTag> DATA = new Long2ObjectOpenHashMap<>();
    /** Every spot where fire was lit during the night. */
    private static final LongOpenHashSet FIRES = new LongOpenHashSet();
    /** Blocks whose chunk was not loaded when the night ended: chunk key → their spots. */
    private static ServerLevel pendingWorld;
    private static final Long2ObjectOpenHashMap<List<Long>> PENDING = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<BlockState> PENDING_STATE = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<CompoundTag> PENDING_DATA = new Long2ObjectOpenHashMap<>();

    /** The mod's own building (the war camp, the mending itself, temporary blocks): not the night's. */
    private static int quiet;
    /** A player breaks, builds or runs a command: that stays as they made it. */
    private static int byPlayer;
    /** Spots that are not the night's to mend (the war camp builds and burns on its own). */
    private static BiPredicate<Level, BlockPos> excluded = (level, pos) -> false;

    private NightDamage() {
    }

    static void begin(ServerLevel level) {
        begin(level, null);
    }

    static void begin(ServerLevel level, BlockPos core) {
        world = level;
        center = core == null ? null : core.immutable();
        LOST.clear();
        DATA.clear();
        FIRES.clear();
    }

    static void end() {
        world = null;
        center = null;
        LOST.clear();
        DATA.clear();
        FIRES.clear();
    }

    /** Which spots the night leaves alone (set once by the siege manager). */
    static void exclude(BiPredicate<Level, BlockPos> test) {
        excluded = test == null ? (level, pos) -> false : test;
    }

    /** Runs the mod's own block changes without writing them down. */
    public static void quietly(Runnable action) {
        quiet++;
        try {
            action.run();
        } finally {
            quiet--;
        }
    }

    /** A player's own action begins (breaking, using an item, a command); see the game-mode mixin. */
    public static void playerBegin() {
        byPlayer++;
    }

    public static void playerEnd() {
        if (byPlayer > 0) {
            byPlayer--;
        }
    }

    /** True while a night's damage is being written down. */
    public static boolean recording(Level level) {
        return world != null && level == world;
    }

    /** True for the spots of a chest, barrel or other container: they do not break in a night. */
    public static boolean spared(Level level, BlockPos pos) {
        if (!recording(level) || byPlayer > 0 || quiet > 0 || !(level.getBlockEntity(pos) instanceof Container)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return ModContent.trapOf(state) == null && !ModContent.isCore(state) && !excluded.test(level, pos);
    }

    /**
     * Called before every block change in a level (Level.setBlock). Returns false when the change
     * must not happen: a container that a night's monster, blast or fire would break.
     */
    public static boolean changing(Level level, BlockPos pos, BlockState next, int flags) {
        if (world == null || level != world || quiet > 0 || byPlayer > 0 || next == null) {
            return true;
        }
        if ((flags & UPDATE_MOVE_BY_PISTON) != 0 || !near(pos)) {
            return true;
        }
        BlockState old = level.getBlockState(pos);
        if (next.getBlock() instanceof BaseFireBlock) {
            FIRES.add(pos.asLong());
        }
        if (old.isAir() || old.getBlock() == next.getBlock() || !weaker(old, next)) {
            return true;
        }
        if (old.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container && ModContent.trapOf(old) == null
                && !ModContent.isCore(old) && !excluded.test(level, pos) && !TempBlocks.isTemp(level, pos)) {
            return false;   // chests and their kin outlast the night, contents and all
        }
        record(level, pos, old);
        return true;
    }

    private static boolean near(BlockPos pos) {
        return center == null || Math.abs(pos.getX() - center.getX()) <= REACH && Math.abs(pos.getZ() - center.getZ()) <= REACH;
    }

    /** The change takes the block away or leaves something softer in its place. */
    private static boolean weaker(BlockState old, BlockState next) {
        if (next.isAir() || next.canBeReplaced() || next.getBlock() instanceof BaseFireBlock
                || next.getBlock() instanceof LiquidBlock) {
            return true;
        }
        return old.is(Blocks.FARMLAND) && next.is(Blocks.DIRT);   // trampled fields
    }

    /** A block is about to be broken by a monster or an explosion of the night. */
    public static void record(Level level, BlockPos pos, BlockState old) {
        if (world == null || level != world || old == null || old.isAir() || old.getBlock() instanceof LiquidBlock
                || old.getBlock() instanceof BaseFireBlock || old.is(Blocks.MOVING_PISTON) || old.is(Blocks.PISTON_HEAD)
                || old.is(Blocks.FROSTED_ICE) || old.is(Blocks.TURTLE_EGG) || old.is(Blocks.SNIFFER_EGG)
                || old.is(Blocks.FROGSPAWN) || LOST.size() >= MAX || TempBlocks.isTemp(level, pos)
                || ModContent.trapOf(old) != null || ModContent.isCore(old) || excluded.test(level, pos)) {
            return;
        }
        long key = pos.asLong();
        if (LOST.containsKey(key)) {
            return;   // the first state of the night counts
        }
        if (old.hasBlockEntity()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container) {
                return;   // spared anyway (see changing)
            }
            if (be != null && !(be instanceof Clearable)) {
                // Signs, banners, heads: their text and look come back too. Lecterns, jukeboxes and
                // campfires come back empty – what they held fell out.
                try {
                    DATA.put(key, be.saveWithFullMetadata(level.registryAccess()));
                } catch (RuntimeException e) {
                    NightSiegeMod.LOGGER.debug("[Night Siege] Blockdaten nicht gemerkt bei {}", pos, e);
                }
            }
        }
        LOST.put(key, old);
    }

    /** How many blocks the night has broken so far (for the tests). */
    public static int lostCount() {
        return LOST.size();
    }

    /**
     * Puts every logged block back where nothing else stands now; returns how many. The night's
     * fire goes out first, so it cannot eat what comes back.
     */
    static int restore(ServerLevel level) {
        if (level != world) {
            return 0;
        }
        int[] put = {0};
        quietly(() -> {
            douse(level);
            if (LOST.isEmpty()) {
                return;
            }
            List<Long> keys = new ArrayList<>(LOST.keySet());
            sort(level, keys, LOST);
            for (long key : keys) {
                BlockPos pos = BlockPos.of(key);
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                    keep(level, key, LOST.get(key), DATA.get(key));
                    continue;
                }
                if (putBack(level, pos, LOST.get(key), DATA.get(key))) {
                    put[0]++;
                }
            }
        });
        if (!PENDING.isEmpty()) {
            NightSiegeMod.LOGGER.info("[Night Siege] Reparatur: {} Blöcke in ungeladenen Chunks folgen, sobald die Chunks laden",
                    PENDING_STATE.size());
        }
        LOST.clear();
        DATA.clear();
        FIRES.clear();
        return put[0];
    }

    /** The fire the night lit goes out. */
    static void douse(ServerLevel level) {
        if (level != world) {
            return;
        }
        for (long key : FIRES) {
            BlockPos pos = BlockPos.of(key);
            if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    && level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                quietly(() -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
            }
        }
    }

    /** Full blocks first (walls before the torches on them), each from the bottom up. */
    private static void sort(ServerLevel level, List<Long> keys, Long2ObjectMap<BlockState> states) {
        keys.sort((a, b) -> {
            boolean fullA = states.get((long) a).isCollisionShapeFullBlock(level, BlockPos.of(a));
            boolean fullB = states.get((long) b).isCollisionShapeFullBlock(level, BlockPos.of(b));
            if (fullA != fullB) {
                return fullA ? -1 : 1;
            }
            return Integer.compare(BlockPos.getY(a), BlockPos.getY(b));
        });
    }

    private static boolean putBack(ServerLevel level, BlockPos pos, BlockState state, CompoundTag data) {
        BlockState now = level.getBlockState(pos);
        boolean trampled = state.is(Blocks.FARMLAND) && now.is(Blocks.DIRT);
        if (now == state || !now.isAir() && !now.canBeReplaced() && !trampled) {
            return false;   // something else stands there now (a player built over it)
        }
        if (!level.setBlock(pos, state, Block.UPDATE_CLIENTS)) {
            return false;
        }
        if (data != null) {
            try {
                BlockEntity be = BlockEntity.loadStatic(pos, state, data, level.registryAccess());
                if (be != null) {
                    level.setBlockEntity(be);
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                }
            } catch (RuntimeException e) {
                NightSiegeMod.LOGGER.debug("[Night Siege] Blockdaten nicht zurückgelegt bei {}", pos, e);
            }
        }
        return true;
    }

    private static void keep(ServerLevel level, long key, BlockState state, CompoundTag data) {
        if (pendingWorld != level) {
            PENDING.clear();
            PENDING_STATE.clear();
            PENDING_DATA.clear();
            pendingWorld = level;
        }
        BlockPos pos = BlockPos.of(key);
        long chunk = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        List<Long> spots = PENDING.get(chunk);
        if (spots == null) {
            spots = new ArrayList<>();
            PENDING.put(chunk, spots);
        }
        spots.add(key);
        PENDING_STATE.put(key, state);
        if (data != null) {
            PENDING_DATA.put(key, data);
        }
    }

    private static long chunkKey(int cx, int cz) {
        return (long) cx << 32 | cz & 0xffffffffL;
    }

    /** Every second: what waited for its chunk comes back once the chunk is loaded. */
    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty() || pendingWorld == null || server.overworld().getGameTime() % 20 != 7) {
            return;
        }
        ServerLevel level = pendingWorld;
        List<Long> ready = new ArrayList<>();
        for (long chunk : PENDING.keySet()) {
            if (level.hasChunk((int) (chunk >> 32), (int) chunk)) {
                ready.add(chunk);
            }
        }
        if (ready.isEmpty()) {
            return;
        }
        int[] put = {0};
        quietly(() -> {
            for (long chunk : ready) {
                List<Long> keys = PENDING.remove(chunk);
                sort(level, keys, PENDING_STATE);
                for (long key : keys) {
                    if (putBack(level, BlockPos.of(key), PENDING_STATE.remove(key), PENDING_DATA.remove(key))) {
                        put[0]++;
                    }
                }
            }
        });
        if (put[0] > 0) {
            NightSiegeMod.LOGGER.info("[Night Siege] Reparatur nachgeholt: {} Blöcke ({} Chunks)", put[0], ready.size());
        }
    }
}
