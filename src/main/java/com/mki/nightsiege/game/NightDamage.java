package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * What the night's attackers break comes back after it, won or lost (Simon, 27 October 2026:
 * "the map should mend itself completely after every night, also when it is lost or things burn").
 *
 * <p>Survival safety (Simon 31.10.): only what Night Siege's attackers do is written down – siege
 * monsters, bosses, machines, sappers and diggers, their arrows, fireballs and blasts, and the fire
 * they light (also where it spreads). Everything else stays as it is after the night: what players
 * break, build or blow up (their TNT, crystals, beds, fireworks), pistons, falling sand, trampled
 * fields, melting ice, decaying leaves. And only within the defended area round the core. So
 * nothing comes back that also lay on the ground as an item: what will come back drops nothing
 * when it breaks (blasts and blocks that fall off with their wall), chests and other containers
 * do not break at all, and a trap that will stand again drops no trap.
 *
 * <p>Every block change in the siege world while a night runs passes {@link #changing} (a mixin on
 * {@code Level.setBlock}); who acts is known from {@link #actorBegin} (an entity's tick),
 * {@link #blastBegin} (an explosion), {@link #fireBegin} (the night's fire) and {@link #asAttacker}
 * (the mod's own breakers). After the night the fire it lit goes out first, then everything is put
 * back, full blocks first and from the bottom up. What lies in an unloaded chunk then comes back as
 * soon as the chunk loads.
 *
 * <p>The log lives on disk too ({@code <world>/nightsiege/night-damage.dat}, written every 30 s
 * when it changed and at the night's end, gone once all is mended): after a crash mid-night the
 * next start mends from it.
 */
public final class NightDamage {

    private static final int MAX = 300_000;
    /** How far from the core (in blocks, sideways) a night's damage is written down at most. */
    static final int REACH = 192;
    /** Beyond the base's radius: the ground in front of the walls where the attackers spawn and fight. */
    static final int FIELD_MARGIN = 24;
    private static final int UPDATE_MOVE_BY_PISTON = 64;
    /** The log goes to disk every 30 s when it changed. */
    private static final int SAVE_TICKS = 600;
    private static final String FILE = "night-damage.dat";
    /** Drops of a blast that are thrown away (the block comes back after the night). */
    public static final BiConsumer<ItemStack, BlockPos> NO_DROPS = (stack, at) -> { };

    private static ServerLevel world;
    private static BlockPos center;
    private static int reach = REACH;
    private static final Long2ObjectLinkedOpenHashMap<BlockState> LOST = new Long2ObjectLinkedOpenHashMap<>();
    /** The block-entity data of what was lost (signs, banners, heads), keyed like LOST. */
    private static final Long2ObjectOpenHashMap<CompoundTag> DATA = new Long2ObjectOpenHashMap<>();
    /** Every spot where the night's attackers lit fire, and where that fire spread to. */
    private static final LongOpenHashSet FIRES = new LongOpenHashSet();
    /** Blocks whose chunk was not loaded when the night ended: chunk key → their spots. */
    private static ServerLevel pendingWorld;
    private static final Long2ObjectOpenHashMap<List<Long>> PENDING = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<BlockState> PENDING_STATE = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<CompoundTag> PENDING_DATA = new Long2ObjectOpenHashMap<>();
    /** The fire of a night that ended in a crash: it goes out once its chunk is loaded. */
    private static final LongOpenHashSet PENDING_FIRES = new LongOpenHashSet();
    /** The core of a night that ended in a crash: the fire watch begins there once its ground is mended. */
    private static BlockPos pendingCenter;

    /** The mod's own building (the war camp, the mending itself, temporary blocks): not the night's. */
    private static int quiet;
    /** A player breaks, builds or runs a command: that stays as they made it. */
    private static int byPlayer;
    /** 1 while a siege attacker acts (its tick, its blast, its fire, the mod's breakers): only that is the night's. */
    private static int cause;
    /** Spots that are not the night's to mend (the war camp builds and burns on its own). */
    private static BiPredicate<Level, BlockPos> excluded = (level, pos) -> false;

    /** The log changed since it was last written to disk. */
    private static boolean dirty;
    private static int saveCountdown = SAVE_TICKS;
    private static Path file;

    private NightDamage() {
    }

    static void begin(ServerLevel level) {
        begin(level, null, REACH);
    }

    static void begin(ServerLevel level, BlockPos core) {
        begin(level, core, REACH);
    }

    /** A night begins: its damage within {@code fieldReach} blocks of the core is written down. */
    static void begin(ServerLevel level, BlockPos core, int fieldReach) {
        world = level;
        center = core == null ? null : core.immutable();
        reach = Math.max(16, Math.min(REACH, fieldReach));
        LOST.clear();
        DATA.clear();
        FIRES.clear();
        cause = 0;
        if (level.getServer() != null) {
            file = fileOf(level.getServer());
        }
        dirty = true;
        saveCountdown = SAVE_TICKS;
    }

    /** The night is over (after {@link #restore}): forgotten, and the file shows only what still waits. */
    static void end() {
        world = null;
        center = null;
        LOST.clear();
        DATA.clear();
        FIRES.clear();
        cause = 0;
        saveNow();
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

    // ------------------------------------------------------------------ who acts (Simon 31.10.: survival safety)

    /** Runs a siege attacker's own breaking (the machines' shots, the sappers, the diggers, a boss's charge). */
    public static void asAttacker(Runnable action) {
        int before = cause;
        cause = 1;
        try {
            action.run();
        } finally {
            cause = before;
        }
    }

    /** An entity's tick in the siege world begins: its block changes are the night's when it is an attacker. */
    public static int actorBegin(Entity entity) {
        int before = cause;
        cause = isAttacker(entity) ? 1 : 0;
        return before;
    }

    /**
     * A blast in the siege world begins: the night's when an attacker set it off. A blast without
     * any source (an end crystal hit by another blast) counts as what set it off.
     */
    public static int blastBegin(Entity direct, Entity indirect) {
        int before = cause;
        if (direct != null || indirect != null) {
            cause = isAttacker(indirect) || isAttacker(direct) ? 1 : 0;
        }
        return before;
    }

    /** True when the fire at this spot is the night's (lit by its attackers or spread from such a fire). */
    public static boolean nightFire(Level level, BlockPos pos) {
        return recording(level) && !FIRES.isEmpty() && FIRES.contains(pos.asLong());
    }

    /** The night's fire ticks: what it burns and where it spreads is the night's. */
    public static int fireBegin() {
        int before = cause;
        cause = 1;
        return before;
    }

    /** Ends {@link #actorBegin}, {@link #blastBegin} or {@link #fireBegin}. */
    public static void causeEnd(int before) {
        cause = before;
    }

    /**
     * A siege attacker: a monster of the night (also a camp's or a dungeon's), a boss and its parts,
     * a warden, a nemesis, a machine and its crew – or what one of them shot, threw or lit. Never a
     * player, a companion or what a player shot.
     */
    public static boolean isAttacker(Entity entity) {
        Entity e = entity;
        for (int hop = 0; hop < 4 && e != null; hop++) {
            if (e instanceof Player) {
                return false;
            }
            Set<String> tags = e.entityTags();
            if (!tags.isEmpty() && (tags.contains(MobFactory.TAG) || tags.contains(BossService.TAG_BOSS)
                    || tags.contains(WardenNight.TAG) || tags.contains(Nemesis.TAG) || tags.contains(Foes.TAG)
                    || tags.contains(Foes.TAG_RISEN) || tags.contains(SiegeMachines.TAG_MACHINE)
                    || tags.contains(SiegeMachines.TAG_CREW) || tags.contains(SiegeCamp.TAG))) {
                return !tags.contains(Companions.TAG);
            }
            e = e instanceof TraceableEntity traced ? traced.getOwner() : null;
        }
        return false;
    }

    /** True while a night's damage is being written down. */
    public static boolean recording(Level level) {
        return world != null && level == world;
    }

    /** True while a siege attacker acts in the night's world. */
    public static boolean attacking(Level level) {
        return recording(level) && cause > 0 && byPlayer == 0 && quiet == 0;
    }

    /** True for the spots of a chest, barrel or other container that an attacker would break: they do not break in a night. */
    public static boolean spared(Level level, BlockPos pos) {
        if (!attacking(level) || !near(pos) || !(level.getBlockEntity(pos) instanceof Container)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return ModContent.trapOf(state) == null && !ModContent.isCore(state) && !excluded.test(level, pos)
                && !TempBlocks.isTemp(level, pos);
    }

    /**
     * True when the block at a spot must not drop anything as it breaks now: it comes back after the
     * night (an attacker breaks it, or it falls off with the wall an attacker broke), it is a container
     * the attacker cannot break, or it is a trap that stands again after the night (TrapService).
     */
    public static boolean keepsDrops(Level level, BlockPos pos) {
        if (world == null || level != world || quiet > 0) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (ModContent.trapOf(state) != null) {
            SiegeManager m = SiegeManager.get();
            return m != null && level instanceof ServerLevel server && m.trapService().comesBack(server, pos);
        }
        if (byPlayer > 0 || cause == 0 || !near(pos) || LOST.containsKey(pos.asLong())) {
            return false;   // not the attackers' doing, or a block set into a gap that already comes back (it drops as usual)
        }
        if (state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container) {
            return !ModContent.isCore(state) && !excluded.test(level, pos) && !TempBlocks.isTemp(level, pos);
        }
        return recordable(level, pos, state);
    }

    /**
     * Called before every block change in a level (Level.setBlock). Returns false when the change
     * must not happen: a container that a night's attacker, blast or fire would break.
     */
    public static boolean changing(Level level, BlockPos pos, BlockState next, int flags) {
        if (world == null || level != world || quiet > 0 || byPlayer > 0 || cause == 0 || next == null) {
            return true;
        }
        if ((flags & UPDATE_MOVE_BY_PISTON) != 0 || !near(pos)) {
            return true;
        }
        BlockState old = level.getBlockState(pos);
        if (next.getBlock() instanceof BaseFireBlock && !(old.getBlock() instanceof BaseFireBlock) && FIRES.add(pos.asLong())) {
            dirty = true;
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
        return center == null || Math.abs(pos.getX() - center.getX()) <= reach && Math.abs(pos.getZ() - center.getZ()) <= reach;
    }

    /** The change takes the block away or leaves something softer in its place. */
    private static boolean weaker(BlockState old, BlockState next) {
        if (next.isAir() || next.canBeReplaced() || next.getBlock() instanceof BaseFireBlock
                || next.getBlock() instanceof LiquidBlock) {
            return true;
        }
        return old.is(Blocks.FARMLAND) && next.is(Blocks.DIRT);   // trampled fields
    }

    /** True for a block that comes back after the night when an attacker breaks it. */
    private static boolean recordable(Level level, BlockPos pos, BlockState old) {
        if (old == null || old.isAir() || old.getBlock() instanceof LiquidBlock || old.getBlock() instanceof BaseFireBlock
                || old.is(Blocks.MOVING_PISTON) || old.is(Blocks.PISTON_HEAD) || old.is(Blocks.FROSTED_ICE)
                || old.is(Blocks.TURTLE_EGG) || old.is(Blocks.SNIFFER_EGG) || old.is(Blocks.FROGSPAWN)
                || old.is(Blocks.TNT)   // set off, it is gone: back it would be free TNT
                || LOST.size() >= MAX || TempBlocks.isTemp(level, pos)
                || ModContent.trapOf(old) != null || ModContent.isCore(old) || excluded.test(level, pos)) {
            return false;
        }
        return !(old.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container);   // spared (see changing)
    }

    /**
     * A block is about to be broken by an attacker of the night (the mod's breakers call this
     * themselves). Only within the defended area. Returns true when it was written down now.
     */
    public static boolean record(Level level, BlockPos pos, BlockState old) {
        if (world == null || level != world || !near(pos) || !recordable(level, pos, old)) {
            return false;
        }
        long key = pos.asLong();
        if (LOST.containsKey(key)) {
            return false;   // the first state of the night counts
        }
        if (old.hasBlockEntity()) {
            BlockEntity be = level.getBlockEntity(pos);
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
        dirty = true;
        RaidPlan.blockBroken(level, pos);   // maybe a way into the base now (the raid's plan)
        return true;
    }

    /** How many blocks the night has broken so far (for the tests). */
    public static int lostCount() {
        return LOST.size();
    }

    /** True when the night has written this spot down (for the tests). */
    public static boolean isLost(BlockPos pos) {
        return LOST.containsKey(pos.asLong());
    }

    /** For the tests: a short line of the log's state. */
    public static String state() {
        return "lost=" + LOST.size() + " fires=" + FIRES.size() + " pending=" + PENDING_STATE.size()
                + " pendingFires=" + PENDING_FIRES.size() + " reach=" + reach + " file=" + (file != null && Files.exists(file));
    }

    /**
     * Puts every logged block back where nothing else stands now; returns how many. The night's
     * fire goes out first, so it cannot eat what comes back. (The night's temporary blocks are gone
     * before this, see SiegeManager.mendBase: a barricade in a gap would keep the gap open.)
     */
    static int restore(ServerLevel level) {
        if (level != world) {
            return 0;
        }
        int[] put = {0};
        BlockPos core = center;
        quietly(() -> {
            douse(level);
            startFireWatch(level, core);
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
        dirty = true;
        return put[0];
    }

    /**
     * The trowel (a guardian's crate gain): what the night's attackers broke within {@code radius}
     * of a spot comes back at once (where nobody stands). Returns the number put back.
     */
    static int restoreNear(ServerLevel level, BlockPos at, double radius) {
        if (level != world || at == null || LOST.isEmpty()) {
            return 0;
        }
        double r2 = radius * radius;
        List<Long> keys = new ArrayList<>();
        for (long key : LOST.keySet()) {
            BlockPos pos = BlockPos.of(key);
            if (pos.distSqr(at) <= r2 && level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                keys.add(key);
            }
        }
        sort(level, keys, LOST);
        int[] put = {0};
        quietly(() -> {
            for (long key : keys) {
                BlockPos pos = BlockPos.of(key);
                if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) {
                    continue;
                }
                if (putBack(level, pos, LOST.get(key), DATA.get(key))) {
                    LOST.remove(key);
                    DATA.remove(key);
                    put[0]++;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5,
                            pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
                }
            }
        });
        if (put[0] > 0) {
            dirty = true;
        }
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
        if (state == null) {
            return false;
        }
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
            PENDING_FIRES.clear();
            pendingWorld = level;
        }
        BlockPos pos = BlockPos.of(key);
        long chunk = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        List<Long> spots = PENDING.get(chunk);
        if (spots == null) {
            spots = new ArrayList<>();
            PENDING.put(chunk, spots);
        }
        if (!PENDING_STATE.containsKey(key)) {
            spots.add(key);
        }
        PENDING_STATE.put(key, state);
        if (data != null) {
            PENDING_DATA.put(key, data);
        }
        dirty = true;
    }

    private static long chunkKey(int cx, int cz) {
        return (long) cx << 32 | cz & 0xffffffffL;
    }

    // ------------------------------------------------------------------ the fire after the night

    /**
     * Simon 30.10.: "feuer brennt nach der Nacht weiter … und brennt damit leider die base nieder". The
     * fire the night wrote down goes out with the mending, but not what it missed: a fireball still in
     * the air, a fire in a chunk that was not loaded, a fire that spread after the night. So for two
     * minutes after a night every fire round the base goes out (every two seconds, only in sections
     * that hold fire at all); a fire on netherrack or soul soil (a fireplace) stays.
     */
    private static ServerLevel sweepWorld;
    private static BlockPos sweepCenter;
    private static long sweepUntil;
    /** How many fires went out after the nights (for the tests). */
    private static int sweptFires;
    static final int SWEEP_REACH = 144;

    /** The night is over: its fireballs and lightning go, and the fire watch begins. */
    static void startFireWatch(ServerLevel level, BlockPos core) {
        if (level == null || core == null) {
            return;
        }
        sweepWorld = level;
        sweepCenter = core.immutable();
        sweepUntil = level.getGameTime() + 2400;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(core).inflate(SWEEP_REACH, 96, SWEEP_REACH);
        for (net.minecraft.world.entity.Entity e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
                e -> e.getType() == net.minecraft.world.entity.EntityTypes.SMALL_FIREBALL
                        || e.getType() == net.minecraft.world.entity.EntityTypes.FIREBALL
                        || e instanceof net.minecraft.world.entity.LightningBolt)) {
            e.discard();
        }
        sweepFire(level, core, SWEEP_REACH);
    }

    /** Puts out every fire in the loaded chunks round a spot (not on netherrack and its kin); returns how many. */
    static int sweepFire(ServerLevel level, BlockPos center, int reach) {
        int[] out = {0};
        quietly(() -> {
            for (int cx = (center.getX() - reach) >> 4; cx <= (center.getX() + reach) >> 4; cx++) {
                for (int cz = (center.getZ() - reach) >> 4; cz <= (center.getZ() + reach) >> 4; cz++) {
                    if (!level.hasChunk(cx, cz)) {
                        continue;
                    }
                    net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
                    net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
                    for (int si = 0; si < sections.length; si++) {
                        net.minecraft.world.level.chunk.LevelChunkSection section = sections[si];
                        if (section == null || section.hasOnlyAir() || !section.maybeHas(st -> st.getBlock() instanceof BaseFireBlock)) {
                            continue;
                        }
                        int baseY = level.getSectionYFromSectionIndex(si) << 4;
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                for (int x = 0; x < 16; x++) {
                                    if (!(section.getBlockState(x, y, z).getBlock() instanceof BaseFireBlock)) {
                                        continue;
                                    }
                                    BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                                    if (fireplace(level, pos)) {
                                        continue;
                                    }
                                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                                    out[0]++;
                                }
                            }
                        }
                    }
                }
            }
        });
        sweptFires += out[0];
        if (out[0] > 0) {
            NightSiegeMod.LOGGER.info("[Night Siege] Feuer nach der Nacht gelöscht: {}", out[0]);
        }
        return out[0];
    }

    /** A fire on netherrack or soul soil: a fireplace, it stays. */
    private static boolean fireplace(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD) || below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL);
    }

    /** For the tests: fires put out after the nights so far. */
    public static int sweptFires() {
        return sweptFires;
    }

    /** For the tests: put out the fire round a spot now. */
    public static int sweepFireNow(ServerLevel level, BlockPos center) {
        return sweepFire(level, center, SWEEP_REACH);
    }

    /** Every tick: the log to disk now and then; once a second what waited for its chunk comes back. */
    static void tick(MinecraftServer server) {
        if (dirty && file != null && --saveCountdown <= 0) {
            saveNow();
        }
        if (sweepWorld != null) {
            long now = sweepWorld.getGameTime();
            if (now > sweepUntil || world == sweepWorld) {
                sweepWorld = null;   // over, or the next night has begun (it watches its own fire)
            } else if (now % 40 == 13) {
                sweepFire(sweepWorld, sweepCenter, SWEEP_REACH);
            }
        }
        if (PENDING.isEmpty() && PENDING_FIRES.isEmpty() || pendingWorld == null || server.overworld().getGameTime() % 20 != 7) {
            return;
        }
        ServerLevel level = pendingWorld;
        int doused = 0;
        if (!PENDING_FIRES.isEmpty()) {
            quiet++;
            try {
                LongIterator it = PENDING_FIRES.iterator();
                while (it.hasNext()) {
                    BlockPos pos = BlockPos.of(it.nextLong());
                    if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                        continue;
                    }
                    it.remove();
                    if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock && !fireplace(level, pos)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        doused++;
                    }
                }
            } finally {
                quiet--;
            }
        }
        List<Long> ready = new ArrayList<>();
        for (long chunk : PENDING.keySet()) {
            if (level.hasChunk((int) (chunk >> 32), (int) chunk)) {
                ready.add(chunk);
            }
        }
        if (ready.isEmpty() && doused == 0) {
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
        dirty = true;
        if (put[0] > 0 || doused > 0) {
            NightSiegeMod.LOGGER.info("[Night Siege] Reparatur nachgeholt: {} Blöcke ({} Chunks), {} Feuer gelöscht",
                    put[0], ready.size(), doused);
        }
        if (pendingCenter != null && level.hasChunk(pendingCenter.getX() >> 4, pendingCenter.getZ() >> 4)) {
            startFireWatch(level, pendingCenter);   // a crashed night: its fire round the base goes out from now
            pendingCenter = null;
        }
        if (PENDING.isEmpty() && PENDING_FIRES.isEmpty()) {
            saveNow();   // all mended: the file goes
        }
    }

    // ------------------------------------------------------------------ on disk (Simon 31.10.: a crash mid-night)

    private static Path fileOf(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("nightsiege").resolve(FILE).normalize();
    }

    /** The server stops: what changed since the last write goes to disk now. */
    public static void flush(MinecraftServer server) {
        if (file == null && server != null) {
            file = fileOf(server);
        }
        if (dirty) {
            saveNow();
        }
    }

    /**
     * Writes what the mending needs (the running night's log, the traps to stand again, what waits
     * for its chunk), atomically: a temporary file first, then moved over the old one. Nothing left
     * to mend: the file goes.
     */
    static void saveNow() {
        dirty = false;
        saveCountdown = SAVE_TICKS;
        Path path = file;
        if (path == null) {
            return;
        }
        List<TrapRecord> traps = world == null ? List.of() : recycledTraps();
        boolean night = world != null && (!LOST.isEmpty() || !FIRES.isEmpty() || !traps.isEmpty());
        boolean waiting = pendingWorld != null && (!PENDING_STATE.isEmpty() || !PENDING_FIRES.isEmpty());
        try {
            if (!night && !waiting) {
                Files.deleteIfExists(path);
                return;
            }
            CompoundTag root = new CompoundTag();
            root.putInt("v", 1);
            if (night) {
                CompoundTag tag = new CompoundTag();
                tag.putString("dim", SiegeManager.dimensionName(world));
                if (center != null) {
                    tag.putLong("core", center.asLong());
                }
                tag.put("lost", blocksTag(LOST, DATA));
                tag.putLongArray("fires", FIRES.toLongArray());
                ListTag trapList = new ListTag();
                for (TrapRecord record : traps) {
                    CompoundTag t = new CompoundTag();
                    t.putString("j", Json.toJson(record));
                    trapList.add(t);
                }
                tag.put("traps", trapList);
                root.put("night", tag);
            }
            if (waiting) {
                CompoundTag tag = new CompoundTag();
                tag.putString("dim", SiegeManager.dimensionName(pendingWorld));
                if (pendingCenter != null) {
                    tag.putLong("core", pendingCenter.asLong());
                }
                tag.put("lost", blocksTag(PENDING_STATE, PENDING_DATA));
                tag.putLongArray("fires", PENDING_FIRES.toLongArray());
                root.put("pending", tag);
            }
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Schadensliste nicht gespeichert: {}", e.toString());
        }
    }

    /** Something the mending needs changed elsewhere (a trap to stand again): written with the next save. */
    static void changed() {
        dirty = true;
    }

    private static List<TrapRecord> recycledTraps() {
        SiegeManager m = SiegeManager.get();
        return m == null ? List.of() : m.trapService().recycledNow();
    }

    /** The blocks as a palette of states (text, as the dungeon snapshot) and per spot its index; block data beside. */
    private static CompoundTag blocksTag(Long2ObjectMap<BlockState> states, Long2ObjectMap<CompoundTag> data) {
        java.util.Map<BlockState, Integer> index = new java.util.HashMap<>();
        ListTag palette = new ListTag();
        ListTag extra = new ListTag();
        long[] spots = new long[states.size()];
        int[] which = new int[states.size()];
        int i = 0;
        for (Long2ObjectMap.Entry<BlockState> e : states.long2ObjectEntrySet()) {
            if (i >= spots.length) {
                break;
            }
            Integer n = index.get(e.getValue());
            if (n == null) {
                n = index.size();
                index.put(e.getValue(), n);
                CompoundTag s = new CompoundTag();
                s.putString("s", ExpeditionSnapshot.write(e.getValue()));
                palette.add(s);
            }
            spots[i] = e.getLongKey();
            which[i] = n;
            i++;
            CompoundTag d = data.get(e.getLongKey());
            if (d != null) {
                CompoundTag t = new CompoundTag();
                t.putLong("p", e.getLongKey());
                t.put("d", d);
                extra.add(t);
            }
        }
        CompoundTag out = new CompoundTag();
        out.put("palette", palette);
        out.putLongArray("spots", java.util.Arrays.copyOf(spots, i));
        out.putIntArray("states", java.util.Arrays.copyOf(which, i));
        out.put("data", extra);
        return out;
    }

    /** Reads {@link #blocksTag} back into the blocks that wait for their chunk; returns how many. */
    private static int readBlocks(ServerLevel level, CompoundTag tag) {
        List<BlockState> palette = new ArrayList<>();
        for (Tag t : tag.getListOrEmpty("palette")) {
            palette.add(t instanceof CompoundTag c ? ExpeditionSnapshot.parse(c.getStringOr("s", "")) : Blocks.AIR.defaultBlockState());
        }
        Long2ObjectOpenHashMap<CompoundTag> data = new Long2ObjectOpenHashMap<>();
        for (Tag t : tag.getListOrEmpty("data")) {
            if (t instanceof CompoundTag c && c.contains("p")) {
                data.put(c.getLongOr("p", 0L), c.getCompoundOrEmpty("d"));
            }
        }
        long[] spots = tag.getLongArray("spots").orElse(new long[0]);
        int[] which = tag.getIntArray("states").orElse(new int[0]);
        int n = 0;
        for (int i = 0; i < spots.length && i < which.length; i++) {
            if (which[i] < 0 || which[i] >= palette.size()) {
                continue;
            }
            BlockState state = palette.get(which[i]);
            if (state.isAir()) {
                continue;
            }
            CompoundTag d = data.get(spots[i]);
            keep(level, spots[i], state, d == null || d.isEmpty() ? null : d);
            n++;
        }
        return n;
    }

    /**
     * Server start without a night: a log left on disk is from a night that ended in a crash (or
     * blocks that still waited for their chunk). Its blocks come back and its fire goes out once
     * their chunks are loaded, its traps stand again now; then the file goes.
     */
    public static void recover(MinecraftServer server) {
        // What a previous server of this game left in memory belongs to its own world (written at its stop).
        world = null;
        center = null;
        LOST.clear();
        DATA.clear();
        FIRES.clear();
        PENDING.clear();
        PENDING_STATE.clear();
        PENDING_DATA.clear();
        PENDING_FIRES.clear();
        pendingWorld = null;
        pendingCenter = null;
        cause = 0;
        dirty = false;
        file = fileOf(server);
        if (!Files.exists(file)) {
            return;
        }
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException | RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Schadensliste {} nicht lesbar: {}", file, e.toString());
            return;
        }
        int blocks = 0;
        int fires = 0;
        int traps = 0;
        for (String part : new String[]{"pending", "night"}) {
            CompoundTag tag = root.getCompoundOrEmpty(part);
            if (tag.isEmpty()) {
                continue;
            }
            ServerLevel level = SiegeManager.levelByName(server, tag.getStringOr("dim", ""));
            if (level == null) {
                continue;
            }
            blocks += readBlocks(level, tag.getCompoundOrEmpty("lost"));
            if (pendingWorld == null) {
                pendingWorld = level;
            }
            if (pendingWorld == level) {
                for (long f : tag.getLongArray("fires").orElse(new long[0])) {
                    if (PENDING_FIRES.add(f)) {
                        fires++;
                    }
                }
            }
            if (tag.contains("core")) {
                pendingCenter = BlockPos.of(tag.getLongOr("core", 0L));
            }
            if (part.equals("night")) {
                List<TrapRecord> list = new ArrayList<>();
                for (Tag t : tag.getListOrEmpty("traps")) {
                    TrapRecord record = t instanceof CompoundTag c ? Json.fromJson(c.getStringOr("j", ""), TrapRecord.class) : null;
                    if (record != null) {
                        list.add(record);
                    }
                }
                SiegeManager m = SiegeManager.get();
                if (m != null && !list.isEmpty()) {
                    traps = m.trapService().recycleAfterCrash(server, list);
                }
            }
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Nach einem Absturz: {} Blöcke kommen zurück, {} Feuer gehen aus, {} Fallen stehen wieder",
                blocks, fires, traps);
        dirty = true;
        saveNow();   // from now only what still waits for its chunk
    }
}
