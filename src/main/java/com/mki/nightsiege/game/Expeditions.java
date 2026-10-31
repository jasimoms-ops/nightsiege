package com.mki.nightsiege.game;

import com.mki.nightsiege.NightSiegeMod;
import com.mki.nightsiege.core.CoreStats;
import com.mki.nightsiege.core.CrateKind;
import com.mki.nightsiege.core.ExpeditionKind;
import com.mki.nightsiege.core.SiegeConfig;
import com.mki.nightsiege.core.SiegeEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Night Siege's own dungeons (dungeon update, Simon 27 October 2026). They replace the dungeon
 * cores in the structures of Dungeons and Taverns.
 *
 * <ul>
 *   <li><b>Report:</b> on the morning after a siege night, with a chance of 50 %, the scouts
 *       report a band of besiegers 250–600 blocks from the core. The core window shows where
 *       (tab "Kundschaft"), with a map to take along. Only one is open at a time; it stays until
 *       it is won.</li>
 *   <li><b>Building:</b> once its ground is loaded and nobody stands close, its place is fixed;
 *       the chunks it needs are asked for (tickets, loaded in the background) and the blocks are
 *       set a few milliseconds every tick, from the far end of the arena to the gatehouse, which
 *       comes last (update 30.10.: before, everything was set in one tick and the server stood still
 *       for seconds). Only then may anyone go in.</li>
 *   <li><b>The fight:</b> a signed-up player going down the stairs fights with class, kit and
 *       skills like at the war camp. Guard rooms one after the other (the gate opens when the
 *       room's monsters are dead), then the arena: waves and the champion. Who falls is out until
 *       the fight ends; when the last one falls, the attempt is lost (cleared rooms stay open).</li>
 *   <li><b>Reward:</b> a class crate and skill points for every fighter, a chest with a night
 *       fragment for the group. The dungeon stays as a ruin whose blocks may be taken.</li>
 * </ul>
 */
public final class Expeditions {

    /** On every monster of a dungeon. */
    public static final String TAG = "nightsiege_exp";
    /** On the arena's champion: the engineer's towers reach it further and take it first (28 October 2026). */
    public static final String CHAMPION_TAG = "nightsiege_exp_champion";
    /** Tag of the scouts' map (its value is the dungeon's id). */
    private static final String MAP_TAG = "nightsiege_scout";
    /** Monsters of the dungeon of now; anything else with the tag goes when it loads. */
    private static final Set<UUID> LIVE = ConcurrentHashMap.newKeySet();

    private final SiegeManager m;
    private final Random random = new Random();

    private ExpeditionLayout.Plan plan;
    private int planId = -1;
    /** One spot the dungeon sets: where, what, and whether only where nothing solid is (the ground above). */
    private record Spot(BlockPos pos, BlockState want, boolean fill) {
    }
    private Map<Long, List<Spot>> byChunk = new HashMap<>();
    /** The chunks from the gatehouse outwards (built and taken down in this order). */
    private List<Long> chunkOrder = new ArrayList<>();
    private ExpeditionSnapshot snapshot;
    private int snapshotId = -1;
    /** The chunk being set now, and how far. */
    private long cursorChunk = Long.MIN_VALUE;
    private int cursorIndex;
    /**
     * How blocks are set in bulk (update 30.10.): to the players, but without shape updates of the
     * neighbours (fences, walls, bars and stairs are joined in one pass at the end) and so without
     * drops or loading the chunk next door.
     */
    static final int BULK = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The scouts on their way: the search for a place, a few milliseconds every tick (update 30.10.). */
    private Scouting scouting;
    /** The dungeon being built now. */
    private Build job;
    /** Dungeons whose ground is coming back (by id): the won one of now and the ones taken away. */
    private final Map<Integer, Teardown> teardowns = new java.util.LinkedHashMap<>();
    /** The playtest's dungeon is searched and built during the pause before it; it failed when no place was found. */
    private boolean prepping;
    private boolean prepFailed;
    /** The plan version: a dungeon of an older one (no snapshot) is closed and reported anew. */
    static final int LAYOUT = 3;   // 3: the fixes of 29 October 2026 (door, lights, stairs); snapshots know their places
    /** The way out after the win: 30 s, then the dungeon goes. */
    static final int COLLAPSE_TICKS = 600;

    /** Players fighting now, everyone of this attempt (for the reward), the fallen of it. */
    private final Set<UUID> fighters = new HashSet<>();
    private final Set<UUID> joined = new LinkedHashSet<>();
    private final Set<UUID> out = new HashSet<>();
    private final Map<UUID, Long> grace = new HashMap<>();
    /** Fighters who fell in a damage event, handled on the next tick. */
    private final List<UUID> falling = new ArrayList<>();
    /** The monsters of a guard room (by room), of the arena's waves. */
    private final Map<Integer, Set<UUID>> roomMobs = new HashMap<>();
    private final Set<UUID> arenaMobs = new HashSet<>();
    private boolean arenaClosed;
    /** The gate before the first hall is up (everyone stood before it); it goes up row by row from frontRiseAt. */
    private boolean frontOpen;
    private long frontRiseAt = -1L;
    private int wave;
    private long nextWaveAt = -1L;
    private UUID champion;
    /** How often a monster was kept off the stair (for the tests). */
    private int stairPushes;
    /** The champion's two skills (content update). */
    private ChampionBrains brains;
    /** The effects of the fight: half life reached, the champion's entrance, the guards waking. */
    private boolean phase2;
    private long championShownAt = -1L;
    private final Map<UUID, Long> wakeAt = new HashMap<>();
    private boolean roomAwake;
    /** The ring of light round the dome, lit once round in 3 s when the arena closes. */
    private long ringAt = -1L;
    private ServerBossEvent bar;
    private int buildRounds;
    /** Attempts lost (every fighter fell) since the server started: the playtest goes on after one. */
    private int lostAttempts;
    /**
     * The playtest's dungeon (Simon, 28 October 2026): a lost attempt is the end of it; the fallen
     * are not let in again (they were put back in at once and the second attempt broke off).
     */
    private boolean noRetry;

    /** When each player last took a map (game time). */
    private final Map<UUID, Long> mapTaken = new HashMap<>();

    Expeditions(SiegeManager manager) {
        this.m = manager;
    }

    // ------------------------------------------------------------------ queries

    public static boolean isExpeditionMob(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG);
    }

    public boolean isFighter(Player player) {
        return player != null && !fighters.isEmpty() && fighters.contains(player.getUUID());
    }

    /** The fighters of now (the playtest brings the late ones to them). */
    public List<UUID> fighterIds() {
        return new ArrayList<>(fighters);
    }

    /** True for a fighter who fell in this attempt (and waits outside). */
    public boolean hasFallen(UUID id) {
        return id != null && out.contains(id);
    }

    /** The playtest: one attempt only (see {@link #noRetry}). */
    public void noRetry(boolean value) {
        noRetry = value;
    }

    public SiegeSave.ExpeditionRecord record() {
        return m.save().expedition;
    }

    /** A dungeon is reported and not won yet. */
    public boolean open() {
        SiegeSave.ExpeditionRecord rec = record();
        return rec != null && !rec.won;
    }

    private static ExpeditionKind kind(SiegeSave.ExpeditionRecord rec) {
        ExpeditionKind kind = ExpeditionKind.byId(rec.kind);
        return kind == null ? ExpeditionKind.STONE_CRYPT : kind;
    }

    private ExpeditionLayout.Plan plan(SiegeSave.ExpeditionRecord rec) {
        if (plan == null || planId != rec.id) {
            usePlan(rec, ExpeditionLayout.plan(new BlockPos(rec.x, rec.y, rec.z), rec.facing, kind(rec), rec.rooms, rec.id));
        }
        return plan;
    }

    /** The plan of the dungeon of now (one worked out already, or a new one), with its spots per chunk. */
    private void usePlan(SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p) {
        usePlan(rec, p, spotsByChunk(p));
    }

    private void usePlan(SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p, Map<Long, List<Spot>> spots) {
        plan = p;
        planId = rec.id;
        byChunk = spots;
        chunkOrder = outwards(byChunk.keySet(), rec);
        cursorChunk = Long.MIN_VALUE;
        cursorIndex = 0;
    }

    /** A plan with its spots by chunk, worked out away from the server thread, and how long that took. */
    private record Prepared(BlockPos origin, ExpeditionLayout.Plan plan, Map<Long, List<Spot>> byChunk, long nanos) {
    }

    /**
     * Works out a dungeon's plan in the background (update 30.10.: 30–160 ms in one tick otherwise).
     * The plan only reads the layout and block states, nothing of the world.
     */
    private static java.util.concurrent.CompletableFuture<Prepared> workOut(BlockPos origin, int facing, ExpeditionKind kind,
                                                                           int rooms, int id) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            long t0 = System.nanoTime();
            ExpeditionLayout.Plan p = ExpeditionLayout.plan(origin, facing, kind, rooms, id);
            return new Prepared(origin, p, spotsByChunk(p), System.nanoTime() - t0);
        });
    }

    /** The plan of a future once it is there; null while it is still being worked out (or on an error: then at once). */
    private static Prepared ready(java.util.concurrent.CompletableFuture<Prepared> future, BlockPos origin, int facing,
                                  ExpeditionKind kind, int rooms, int id) {
        if (!future.isDone()) {
            return null;
        }
        try {
            return future.join();
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Dungeon-Plan im Hintergrund fehlgeschlagen ({}), jetzt im Tick", e.toString());
            long t0 = System.nanoTime();
            ExpeditionLayout.Plan p = ExpeditionLayout.plan(origin, facing, kind, rooms, id);
            return new Prepared(origin, p, spotsByChunk(p), System.nanoTime() - t0);
        }
    }

    /** Every spot of a plan by its chunk. */
    private static Map<Long, List<Spot>> spotsByChunk(ExpeditionLayout.Plan p) {
        Map<Long, List<Spot>> out = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : p.blocks.entrySet()) {
            out.computeIfAbsent(chunkKey(e.getKey().getX() >> 4, e.getKey().getZ() >> 4), k -> new ArrayList<>())
                    .add(new Spot(e.getKey(), e.getValue(), false));
        }
        for (Map.Entry<BlockPos, BlockState> e : p.fill.entrySet()) {
            out.computeIfAbsent(chunkKey(e.getKey().getX() >> 4, e.getKey().getZ() >> 4), k -> new ArrayList<>())
                    .add(new Spot(e.getKey(), e.getValue(), true));
        }
        return out;
    }

    /** The spots of a dungeon by chunk (its plan worked out if need be). */
    private Map<Long, List<Spot>> spotsOf(SiegeSave.ExpeditionRecord rec) {
        plan(rec);
        return byChunk;
    }

    /** The chunks from the gatehouse outwards. */
    private static List<Long> outwards(java.util.Collection<Long> chunks, SiegeSave.ExpeditionRecord rec) {
        int ocx = rec.x >> 4;
        int ocz = rec.z >> 4;
        List<Long> order = new ArrayList<>(chunks);
        order.sort(java.util.Comparator.comparingLong(k -> {
            long dx = (k >> 32) - ocx;
            long dz = (int) (long) k - ocz;
            return dx * dx + dz * dz;
        }));
        return order;
    }

    private ExpeditionSnapshot snapshot(MinecraftServer server, SiegeSave.ExpeditionRecord rec) {
        if (snapshot == null || snapshotId != rec.id) {
            snapshot = ExpeditionSnapshot.load(server, rec.id);
            snapshotId = rec.id;
        }
        return snapshot;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int chunkX(long key) {
        return (int) (key >> 32);
    }

    private static int chunkZ(long key) {
        return (int) key;
    }

    // ------------------------------------------------------------------ the report

    /** After a siege night (won or lost): the scouts may report a dungeon. */
    void afterNight(MinecraftServer server) {
        SiegeConfig cfg = m.config();
        if (!cfg.dungeonsEnabled || record() != null || scouting != null || m.playtest().running()
                || random.nextDouble() >= cfg.expeditionChance) {
            return;
        }
        scout(server, null, false, null);
    }

    /**
     * The scouts set out (update 30.10.): the search for a place runs a few milliseconds every tick
     * and reports when it is done (the world's generator is asked for the ground of up to 80
     * places, which took seconds in one tick on the server). {@code build}: the dungeon is built
     * right after (playtest, admin) without waiting for a player to come near. {@code asker} is told
     * the result. False when there is no core.
     */
    public boolean scout(MinecraftServer server, ExpeditionKind forced, boolean build, UUID asker) {
        Scouting s = newScouting(server, forced, build, asker);
        scouting = s;
        return s != null;
    }

    /**
     * Tests: the scouts report a dungeon now, all in this tick: a spot on land 250–600 blocks from
     * the core whose ground can carry the way down. Null when no spot was found (or no core).
     */
    public SiegeSave.ExpeditionRecord report(MinecraftServer server, ExpeditionKind forced) {
        Scouting s = newScouting(server, forced, false, null);
        if (s == null) {
            return null;
        }
        if (scouting != null) {
            scouting = null;   // this one instead
        }
        ServerLevel level = SiegeManager.levelByName(server, s.dimension);
        long t0 = System.nanoTime();
        while (!stepScouting(level, s, Long.MAX_VALUE)) {
            // all at once
        }
        s.work = s.longest = System.nanoTime() - t0;
        s.ticks = 1;
        return finishScouting(server, level, s);
    }

    /** The scouts' search, a step at a time (see {@link #stepScouting}). */
    private static final class Scouting {
        final ExpeditionKind forced;
        final boolean build;
        final UUID asker;
        final String dimension;
        final int coreX;
        final int coreZ;
        final int rooms;
        final int length;
        final int minDistance;
        final int maxDistance;
        final long born = System.nanoTime();
        long work;
        long longest;
        int ticks;
        /** Places looked at (of at most 80). */
        int attempt;
        /** The place being looked at: its ground is checked one column at a time. */
        boolean looking;
        int x;
        int z;
        int facing;
        double distance;
        int y0;
        String biome;
        int stage;
        /** The columns along the way down (local x, z), coarse ones first, and the next to look at. */
        final int[] colX;
        final int[] colZ;
        int col;
        /** Found: the record, its map drawn a row at a time. */
        SiegeSave.ExpeditionRecord found;
        int mapRow;

        Scouting(ExpeditionKind forced, boolean build, UUID asker, CoreRecord core, int rooms, SiegeConfig cfg) {
            this.forced = forced;
            this.build = build;
            this.asker = asker;
            this.dimension = core.dimension;
            this.coreX = core.x;
            this.coreZ = core.z;
            this.rooms = rooms;
            this.length = ExpeditionLayout.length(rooms);
            this.minDistance = cfg.expeditionMinDistance;
            this.maxDistance = cfg.expeditionMaxDistance;
            // Every 8 blocks along the way, 10 across: a few spread columns first (most places fail
            // somewhere, so a coarse look finds it sooner); a place passes only if every one fits.
            List<int[]> cols = new ArrayList<>();
            for (int lz = -8; lz <= length; lz += 8) {
                for (int lx = -30; lx <= 30; lx += 10) {
                    int rank = ((lz + 8) % 24 == 0 ? 0 : 2) + (lx % 30 == 0 ? 0 : 1);
                    cols.add(new int[] {lx, lz, rank});
                }
            }
            cols.sort(java.util.Comparator.comparingInt(c -> c[2]));
            colX = new int[cols.size()];
            colZ = new int[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                colX[i] = cols.get(i)[0];
                colZ[i] = cols.get(i)[1];
            }
        }
    }

    private Scouting newScouting(MinecraftServer server, ExpeditionKind forced, boolean build, UUID asker) {
        CoreRecord core = m.mainCore();
        if (core == null || SiegeManager.levelByName(server, core.dimension) == null) {
            return null;
        }
        return new Scouting(forced, build, asker, core, ExpeditionKind.rooms(m.currentLevel()), m.config());
    }

    /** Every tick while the scouts are out: on with the search until the tick's time is used. */
    private void tickScouting(MinecraftServer server, long deadline) {
        Scouting s = scouting;
        ServerLevel level = SiegeManager.levelByName(server, s.dimension);
        if (level == null) {
            scouting = null;
            return;
        }
        long t0 = System.nanoTime();
        boolean done = stepScouting(level, s, deadline);
        long spent = System.nanoTime() - t0;
        s.work += spent;
        s.longest = Math.max(s.longest, spent);
        s.ticks++;
        if (done && scouting == s) {
            scouting = null;
            finishScouting(server, level, s);
        }
    }

    /**
     * The search: up to 80 places 250–600 blocks from the core; for each, land (no sea, no river)
     * and, along the whole way down, ground above the vaults – from the world's generator, so it
     * works for ground nobody has seen yet (no chunk is loaded). One column a step, until the
     * deadline. Then the map, a row a step. True when done (found or not).
     */
    private boolean stepScouting(ServerLevel level, Scouting s, long deadline) {
        int sea = level.getSeaLevel();
        while (true) {
            if (s.found != null) {
                drawMapRow(level, s.found, s.mapRow++);
                if (s.mapRow >= s.found.mapSize) {
                    return true;
                }
            } else if (!s.looking) {
                if (s.attempt >= 80) {
                    return true;   // nothing found
                }
                s.attempt++;
                double angle = random.nextDouble() * Math.PI * 2.0;
                s.distance = s.minDistance + random.nextDouble() * Math.max(0, s.maxDistance - s.minDistance);
                s.x = s.coreX + (int) Math.round(Math.cos(angle) * s.distance);
                s.z = s.coreZ + (int) Math.round(Math.sin(angle) * s.distance);
                s.facing = CampLayout.facingTowards(s.x - s.coreX, s.z - s.coreZ);   // the way leads away from the core
                s.stage = 0;
                s.looking = true;
            } else if (s.stage == 0) {
                // The cheap look first: sea and rivers are out.
                s.looking = !wet(biomePath(level, s.x, sea + 4, s.z));
                s.stage = 1;
            } else if (s.stage == 1) {
                s.y0 = baseHeight(level, s.x, s.z);
                if (s.y0 <= sea + 1) {
                    s.looking = false;
                } else {
                    s.biome = biomePath(level, s.x, s.y0, s.z);
                    s.col = 0;
                    s.stage = 2;
                }
            } else {
                int lx = s.colX[s.col];
                int lz = s.colZ[s.col];
                BlockPos at = CampLayout.world(new BlockPos(s.x, s.y0, s.z), s.facing, lx, 0, lz);
                int h = baseHeight(level, at.getX(), at.getZ());
                // The vaults' roofs lie 8 below the gatehouse floor, two of ground over them; no water above.
                if (h < s.y0 - 6 || h <= sea || lz % 24 == 0 && wet(biomePath(level, at.getX(), h, at.getZ()))) {
                    s.looking = false;
                } else if (++s.col >= s.colX.length) {
                    s.looking = false;
                    s.found = found(level, s);
                    s.mapRow = 0;
                }
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
        }
    }

    /** The record of a place the scouts found (its map still to be drawn). */
    private SiegeSave.ExpeditionRecord found(ServerLevel level, Scouting s) {
        SiegeSave.ExpeditionRecord rec = new SiegeSave.ExpeditionRecord();
        rec.id = random.nextInt(1_000_000) + 1;
        rec.kind = (s.forced != null ? s.forced : ExpeditionKind.forBiome(s.biome, random.nextInt(10))).id();
        rec.dimension = s.dimension;
        rec.x = s.x;
        rec.y = Integer.MIN_VALUE;
        rec.z = s.z;
        rec.facing = s.facing;
        rec.rooms = s.rooms;
        rec.layout = LAYOUT;
        rec.reportedDay = m.dayNow(level.getServer());
        startMap(rec, s.coreX, s.coreZ);
        return rec;
    }

    /** The search is over: the report (or that there is no place), and for the playtest and admin the building. */
    private SiegeSave.ExpeditionRecord finishScouting(MinecraftServer server, ServerLevel level, Scouting s) {
        long ms = (System.nanoTime() - s.born) / 1_000_000L;
        SiegeSave.ExpeditionRecord rec = s.found;
        if (rec == null) {
            NightSiegeMod.LOGGER.info("[Night Siege] Kundschafter: kein Platz für einen Dungeon gefunden (Suche {} ms in {} Ticks, längster {} ms, {} Orte)",
                    s.work / 1_000_000L, s.ticks, s.longest / 1_000_000L, s.attempt);
            if (s.build && prepping) {
                prepping = false;
                prepFailed = true;
            }
            tell(server, s.asker, Component.translatable("nightsiege.cmd.expedition_no_spot"));
            return null;
        }
        SiegeSave.ExpeditionRecord old = record();
        if (old != null) {
            if (!fighters.isEmpty()) {
                endAll(server, null);
            }
            cancelJob(server);
            if (old.built && old.layout >= LAYOUT) {
                detach(server, old);   // a won one still being taken down goes on in the background
            }
        }
        m.save().expedition = rec;
        plan = null;
        buildRounds = 0;
        m.persist();
        CoreRecord core = m.mainCore();
        if (core != null) {
            announce(server, rec, core);
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Kundschafter melden {} bei {} {} ({} Blöcke vom Core, Biom {})",
                rec.kind, rec.x, rec.z, (int) s.distance, s.biome);
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon-Suche: {} ms in {} Ticks (längster {} ms), {} Orte geprüft, nach {} s gemeldet",
                s.work / 1_000_000L, s.ticks, s.longest / 1_000_000L, s.attempt, String.format(Locale.ROOT, "%.1f", ms / 1000.0));
        tell(server, s.asker, Component.translatable("nightsiege.cmd.expedition_reported",
                Component.translatable(kind(rec).translationKey()), rec.x, rec.z));
        if (s.build) {
            Build b = new Build(rec.id, rec.dimension);
            b.stage = Build.SITE;
            b.searchMs = s.work / 1_000_000L;
            b.searchTicks = s.ticks;
            job = b;
        }
        return rec;
    }

    /** A line for the admin who sent the scouts (if still online). */
    private static void tell(MinecraftServer server, UUID who, Component text) {
        ServerPlayer p = who == null ? null : server.getPlayerList().getPlayer(who);
        if (p != null) {
            p.sendSystemMessage(text);
        }
    }

    private static boolean wet(String biome) {
        return biome.contains("ocean") || biome.contains("river") || biome.contains("beach") || biome.contains("shore");
    }

    /** The ground as the world's generator makes it (no trees, no buildings; works for ground nobody has seen). */
    private static int baseHeight(ServerLevel level, int x, int z) {
        return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level,
                level.getChunkSource().randomState());
    }

    private static String biomePath(ServerLevel level, int x, int y, int z) {
        Holder<Biome> biome = level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2);
        return biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
    }

    /** The scouts' map: a square around the core and the dungeon, one terrain letter per cell. */
    private static void drawMap(ServerLevel level, SiegeSave.ExpeditionRecord rec, CoreRecord core) {
        startMap(rec, core.x, core.z);
        for (int row = 0; row < rec.mapSize; row++) {
            drawMapRow(level, rec, row);
        }
    }

    private static void startMap(SiegeSave.ExpeditionRecord rec, int coreX, int coreZ) {
        int size = 40;
        int cx = (coreX + rec.x) / 2;
        int cz = (coreZ + rec.z) / 2;
        int span = Math.max(Math.abs(rec.x - coreX), Math.abs(rec.z - coreZ)) + 160;
        int cell = Math.max(8, (span + size - 1) / size);
        rec.mapSize = size;
        rec.mapCell = cell;
        rec.mapX = cx - size * cell / 2;
        rec.mapZ = cz - size * cell / 2;
        rec.map = "";
    }

    /** One row of the map (the rows come in order). */
    private static void drawMapRow(ServerLevel level, SiegeSave.ExpeditionRecord rec, int row) {
        int y = level.getSeaLevel() + 6;
        StringBuilder out = new StringBuilder(rec.map);
        for (int col = 0; col < rec.mapSize; col++) {
            int x = rec.mapX + col * rec.mapCell + rec.mapCell / 2;
            int z = rec.mapZ + row * rec.mapCell + rec.mapCell / 2;
            out.append(terrain(biomePath(level, x, y, z)));
        }
        rec.map = out.toString();
    }

    /** One letter for the map's colour (the client knows the colours). */
    static char terrain(String b) {
        if (b.contains("deep") && b.contains("ocean")) {
            return 'o';
        }
        if (b.contains("ocean") || b.contains("river")) {
            return 'w';
        }
        if (b.contains("beach") || b.contains("shore")) {
            return 'b';
        }
        if (b.contains("frozen") || b.contains("snow") || b.contains("ice") || b.contains("grove")) {
            return 'n';
        }
        if (b.contains("peaks") || b.contains("slopes") || b.contains("stony") || b.contains("windswept")) {
            return 'm';
        }
        if (b.contains("badlands")) {
            return 'r';
        }
        if (b.contains("desert")) {
            return 'd';
        }
        if (b.contains("savanna")) {
            return 'a';
        }
        if (b.contains("swamp") || b.contains("mangrove")) {
            return 's';
        }
        if (b.contains("jungle") || b.contains("bamboo")) {
            return 'j';
        }
        if (b.contains("taiga")) {
            return 't';
        }
        if (b.contains("forest") || b.contains("cherry") || b.contains("pale_garden")) {
            return 'f';
        }
        if (b.contains("mushroom")) {
            return 'u';
        }
        return 'p';
    }

    private void announce(MinecraftServer server, SiegeSave.ExpeditionRecord rec, CoreRecord core) {
        ExpeditionKind kind = kind(rec);
        int dx = rec.x - core.x;
        int dz = rec.z - core.z;
        Component text = Component.translatable("nightsiege.expedition.report",
                Component.translatable(kind.translationKey() + ".at").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                (int) Math.round(Math.sqrt(dx * (double) dx + dz * (double) dz)), direction(dx, dz))
                .withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (m.isRegistered(p)) {
                p.sendSystemMessage(text);
                p.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.AMBIENT,
                        p.getX(), p.getY(), p.getZ(), 0.6f, 1.4f, random.nextLong()));
            }
        }
    }

    /** "Nordosten" and the like for a way from (0, 0) to (dx, dz). */
    static Component direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int index = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return Component.translatable("nightsiege.expedition.dir." + index);
    }

    // ------------------------------------------------------------------ every tick

    /** Every tick outside the nights. */
    void tick(MinecraftServer server) {
        if (!falling.isEmpty()) {
            for (UUID id : new ArrayList<>(falling)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    fallOut(server, p);
                }
            }
            falling.clear();
        }
        long now = server.overworld().getGameTime();
        work(server, now);   // the scouts, the building and the taking down: a few milliseconds of it
        SiegeSave.ExpeditionRecord rec = record();
        if (now % 10 == 0) {
            mapHints(server, rec);
        }
        if (rec != null && rec.layout < LAYOUT) {
            legacy(rec);
            return;
        }
        if (rec == null || rec.won) {
            if (!fighters.isEmpty()) {
                endAll(server, null);
            }
            ServerLevel level = rec == null ? null : SiegeManager.levelByName(server, rec.dimension);
            if (level != null && rec.built) {
                afterWin(level, rec, now);
            } else if (rec != null && !rec.built) {
                m.save().expedition = null;
                m.persist();
            }
            return;
        }
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level == null) {
            return;
        }
        if (rec.built && now % 20 == 7) {
            sweep(level, plan(rec));   // nothing from outside in the halls, at night too
        }
        if (now % 20 == 11) {
            tickPendingGates(level);
        }
        if (m.isActive() || m.testPending() || m.nightComing(server)) {
            if (!fighters.isEmpty()) {
                endAll(server, "nightsiege.expedition.night");   // the night calls everyone home
            }
            return;
        }
        if (!rec.built) {
            if (job == null && teardowns.isEmpty()) {
                checkSite(level, rec);
                if (now % 100 == 0) {
                    tryBuild(level, rec);
                }
            }
            return;
        }
        if (!rec.complete) {
            return;   // still being built (see work): nobody goes in yet
        }
        if (now % 5 == 0) {
            checkEntries(level, rec, now);
            tickRound(level, rec, now);
        }
        if (now % 200 == 0 && fighters.isEmpty() && !arenaClosed && rec.cleared >= rec.rooms) {
            // A gate left shut by a crash in the arena fight opens again.
            BlockPos gate = plan(rec).arenaGate.get(0);
            if (loaded(level, gate.getX() >> 4, gate.getZ() >> 4) && level.getBlockState(gate).is(Blocks.IRON_BARS)) {
                setGate(level, plan(rec).arenaGate, false);
            }
        }
        if (!fighters.isEmpty()) {
            List<ServerPlayer> inside = new ArrayList<>();
            for (UUID id : fighters) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    inside.add(p);
                }
            }
            DungeonFx.ambient(level, inside, kind(rec), now);
        }
        if (ringAt > 0) {
            int step = (int) (now - ringAt);
            if (step > 60) {
                ringAt = -1L;
            } else {
                ExpeditionLayout.Plan p = plan(rec);
                DungeonFx.ring(level, p.arenaCenter.above(13), step / 60.0);
            }
        }
        if (round != null && now % 5 == 0) {
            DungeonFx.runes(level, plan(rec).seal, round.ready.size(), round.members.size());
        }
        if (brains != null && !fighters.isEmpty()) {
            brains.tick(level, championHost(level, rec), m.currentLevel(), m.config(), now);
        }
        if (now % 2 == 0 && !fighters.isEmpty()) {
            keepOffStair(level, plan(rec));
        }
        if (now % 10 == 0 && (!fighters.isEmpty() || !joined.isEmpty())) {
            tickFight(server, level, rec, now);
        }
    }

    // ------------------------------------------------------------------ building

    /** A place that passed the first checks, its plan being worked out in the background. */
    private record SiteCheck(int id, BlockPos at, java.util.concurrent.CompletableFuture<Prepared> plan) {
    }

    private SiteCheck siteCheck;

    private void tryBuild(ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        if (!loaded(level, rec.x >> 4, rec.z >> 4)) {
            return;   // nobody near yet
        }
        if (siteCheck != null && siteCheck.id() == rec.id) {
            return;   // a place is being looked at (see checkSite)
        }
        SiegeCamp.Reasons why = new SiegeCamp.Reasons();
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = rec.x + (attempt == 0 ? 0 : random.nextInt(41) - 20);
            int z = rec.z + (attempt == 0 ? 0 : random.nextInt(41) - 20);
            Integer floor = SiegeCamp.site(level, x, z, 9, 12, false, false, why);
            if (floor == null) {
                continue;
            }
            if (someoneNear(level, x, z)) {
                buildRounds++;
                return;
            }
            BlockPos at = groundOrigin(level, new BlockPos(x, floor, z));
            Boolean free = freeOfPlayers(level, rec, at, null, why);
            if (free == null) {
                buildRounds++;
                return;   // not all its ground is loaded yet: again when it is
            }
            if (!free) {
                continue;
            }
            // Every spot of it is checked next, against its plan (worked out in the background).
            siteCheck = new SiteCheck(rec.id, at, workOut(at, rec.facing, kind(rec), rec.rooms, rec.id));
            return;
        }
        roundWithoutPlace(rec, why);
    }

    /** Nobody close sees it pop up; nobody at all stands where its walls go. */
    private boolean someoneNear(ServerLevel level, int x, int z) {
        double limit = buildRounds < 3 ? 32.0 : 12.0;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x + 0.5, p.getY(), z + 0.5) < limit * limit) {
                return true;
            }
        }
        return false;
    }

    private void roundWithoutPlace(SiegeSave.ExpeditionRecord rec, SiegeCamp.Reasons why) {
        if (++buildRounds % 12 == 0) {
            // A minute with the ground loaded and no place: a little further off.
            rec.x += random.nextInt(97) - 48;
            rec.z += random.nextInt(97) - 48;
            m.persist();
            NightSiegeMod.LOGGER.info("[Night Siege] Dungeon wartet auf einen Platz ({}), neu bei {} {}", why, rec.x, rec.z);
        }
    }

    /** Every tick while a place is looked at: once its plan is there, every spot is checked and the build begins. */
    private void checkSite(ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        SiteCheck c = siteCheck;
        if (c == null) {
            return;
        }
        if (c.id() != rec.id) {
            siteCheck = null;
            return;
        }
        Prepared ready = ready(c.plan(), c.at(), rec.facing, kind(rec), rec.rooms, rec.id);
        if (ready == null) {
            return;
        }
        siteCheck = null;
        SiegeCamp.Reasons why = new SiegeCamp.Reasons();
        if (someoneNear(level, c.at().getX(), c.at().getZ())) {
            buildRounds++;
            return;
        }
        long t0 = System.nanoTime();
        Boolean free = freeOfPlayers(level, rec, c.at(), ready.plan(), why);
        long checked = System.nanoTime() - t0;
        if (free == null) {
            buildRounds++;
            return;
        }
        if (!free) {
            roundWithoutPlace(rec, why);
            return;
        }
        Build b = new Build(rec.id, rec.dimension);
        b.careful = true;   // a player may come close while it goes up
        b.checkNanos = checked;
        beginBuild(level, rec, c.at(), ready, b);
    }

    /** The gatehouse floor no lower than the arena's pit allows (it lies 29 below it; flat worlds). */
    private static BlockPos groundOrigin(ServerLevel level, BlockPos at) {
        int lowest = level.getMinY() + 32;
        return at.getY() < lowest ? new BlockPos(at.getX(), lowest, at.getZ()) : at;
    }

    /**
     * Update 30.10. (Simon: "die dungeons … dürfen auf keinen fall in irgendwelchen von spieler
     * platzierten blöcken spawnen"): every spot the whole dungeon would set, down to the arena, is
     * nature, rock or water (no chest, no planks, no torch), and no chunk it reaches holds a
     * player's building. Null while some of its ground is not loaded. Without a plan only the
     * chunks are looked at (all loaded, none built on); with it, every spot.
     */
    private Boolean freeOfPlayers(ServerLevel level, SiegeSave.ExpeditionRecord rec, BlockPos origin,
                                  ExpeditionLayout.Plan candidate, SiegeCamp.Reasons why) {
        java.util.Set<Long> chunks = new HashSet<>();
        List<BlockPos> all = new ArrayList<>();
        if (candidate != null) {
            all.addAll(candidate.blocks.keySet());
            all.addAll(candidate.fill.keySet());
        }
        for (int lx = -8; lx <= 8; lx++) {
            for (int lz = -10; lz <= 7; lz++) {
                BlockPos column = CampLayout.world(origin, rec.facing, lx, 0, lz);
                for (int dy = -13; dy <= 14; dy++) {
                    all.add(column.above(dy));
                }
            }
        }
        if (candidate == null) {
            // The plan's reach without working it out: its bounds.
            int length = ExpeditionLayout.length(rec.rooms) + 8;
            for (int lz = -11; lz <= length; lz += 8) {
                for (int lx = -40; lx <= 40; lx += 8) {
                    all.add(CampLayout.world(origin, rec.facing, lx, 0, lz));
                }
            }
        }
        for (BlockPos pos : all) {
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            if (chunks.add(PlayerBuilt.key(cx, cz))) {
                if (!loaded(level, cx, cz)) {
                    return null;
                }
                if (PlayerBuilt.marked(level, cx, cz)) {
                    why.built++;
                    why.detail = "Spieler haben bei Chunk " + cx + " " + cz + " gebaut";
                    return false;
                }
            }
        }
        if (candidate == null) {
            return true;
        }
        for (BlockPos pos : all) {
            BlockState state = level.getBlockState(pos);
            if (state.hasBlockEntity() || !SiegeCamp.natural(state) && state.getFluidState().isEmpty() && !underground(state)) {
                why.built++;
                why.detail = "Dungeon-Raum " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ": " + state;
                return false;
            }
        }
        // Survival safety (Simon 31.10., D3/D6): no one's things where it goes (armor stands, frames, a
        // villager, someone's animal); wild animals and monsters are put out of the way when it is built.
        for (Entity e : level.getEntitiesOfClass(Entity.class, candidate.bounds.inflate(2.0), SafeGround::someones)) {
            why.built++;
            why.detail = "Dungeon-Raum: " + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())
                    + " bei " + e.blockPosition();
            return false;
        }
        return true;
    }

    private static final Set<String> UNDERGROUND = Set.of("deepslate", "tuff", "budding_amethyst", "amethyst_cluster",
            "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud", "sculk", "sculk_vein", "moss_carpet",
            "azalea", "flowering_azalea", "glow_lichen", "cave_vines", "cave_vines_plant", "spore_blossom", "hanging_roots",
            "big_dripleaf", "big_dripleaf_stem", "small_dripleaf", "raw_iron_block", "raw_copper_block", "bedrock", "lava",
            "water", "cobweb");

    /** Deep rock the camp's check does not know: ores, deepslate, cave growth. */
    private static boolean underground(BlockState state) {
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_ore") || path.startsWith("infested_") || UNDERGROUND.contains(path);
    }

    /**
     * A dungeon being set (update 30.10., the server stood still for 4–9 s while one was set in a
     * single tick): first the chunks it needs are asked for (tickets: loaded or generated in the
     * background), then its blocks are set a few milliseconds a tick, from the far end of the arena
     * towards the gatehouse; the ground round the gatehouse is levelled and the gatehouse set last,
     * then fences, walls, bars and stairs are joined. Only then is it complete.
     */
    private static final class Build {
        /** Only its spot is known: the gatehouse floor is found once the ground there is loaded. */
        static final int SITE = 0;
        /** The chunks away from the gatehouse, the arena's first. */
        static final int FAR = 1;
        /** The ground round the gatehouse is levelled. */
        static final int LEVEL = 2;
        /** The chunks of the gatehouse. */
        static final int NEAR = 3;
        /** Fences, walls, bars and stairs join their neighbours. */
        static final int SHAPE = 4;

        final int id;
        final String dimension;
        int stage = FAR;
        /** The lists below are made (once the plan is there). */
        boolean listed;
        /** Every chunk asked for: the dungeon's, the levelled ground's and one ring round them. */
        final Set<Long> want = new HashSet<>();
        final List<Long> far = new ArrayList<>();
        final List<Long> near = new ArrayList<>();
        /** The levelled ground's chunks. */
        final Set<Long> levelChunks = new HashSet<>();
        List<Spot> shaped;
        int shapeAt;
        long askedAt = Long.MIN_VALUE;
        /** A player may stand where the gatehouse goes (built by itself, not by the playtest or an admin). */
        boolean careful;
        long waitSince = -1L;
        /** Since when a player stands where the halls go (survival safety 31.10., D5). */
        long hallWaitSince = -1L;
        /** "dungeon hier": built where the admin says, no site check (31.10.: every other build is checked). */
        boolean anywhere;
        /** The playtest's and the admin's build: places tried for it (31.10., D3), the one looked at now. */
        int siteTries;
        int siteX = Integer.MIN_VALUE;
        int siteZ;
        /** Its plan, once worked out, while the chunks of the whole dungeon load for the check of every spot. */
        Prepared sitePlan;
        /** Whom to tell when no place is found (the admin of "dungeon bauen"). */
        UUID asker;
        /** Tests: all at once, the chunks loaded on the way. */
        boolean sync;
        boolean busy;
        boolean dirty;
        long savedAt;
        // The clock (for the log line).
        final long born = System.nanoTime();
        long work;
        long longest;
        long planNanos;
        /** Of that, in a tick (the rest in the background); and the check of every spot (built by itself). */
        long tickNanos;
        long checkNanos;
        int busyTicks;
        long searchMs = -1L;
        int searchTicks;
        /** The plan being worked out in the background (only its spot was known), and for where. */
        java.util.concurrent.CompletableFuture<Prepared> planning;
        BlockPos planAt;

        Build(int id, String dimension) {
            this.id = id;
            this.dimension = dimension;
        }
    }

    /**
     * The dungeon's place is fixed; it is built over the next ticks (see {@link #stepBuild}).
     * {@code ready}: its plan when it is worked out already (else it is worked out now, in this tick).
     */
    private void beginBuild(ServerLevel level, SiegeSave.ExpeditionRecord rec, BlockPos origin, Prepared ready, Build carry) {
        origin = groundOrigin(level, origin);
        rec.x = origin.getX();
        rec.y = origin.getY();
        rec.z = origin.getZ();
        rec.built = true;
        rec.complete = false;
        rec.layout = LAYOUT;
        rec.chunksDone.clear();
        unsavedChunks.clear();   // (survival safety 31.10.: set, not on the disk yet)
        unsavedChunksId = rec.id;
        rec.levelDone = false;   // its ground is levelled later (and counts once the world is saved)
        checkedComplete = rec.id;   // built in this run: nothing to look at after a restart
        Build b = carry != null ? carry : new Build(rec.id, rec.dimension);
        long t0 = System.nanoTime();
        if (ready != null && ready.origin().equals(origin)) {
            usePlan(rec, ready.plan(), ready.byChunk());
            b.planNanos += ready.nanos();   // in the background
            b.tickNanos += System.nanoTime() - t0;
        } else {
            plan = null;
            plan(rec);
            b.planNanos += System.nanoTime() - t0;
            b.tickNanos += System.nanoTime() - t0;
        }
        b.stage = Build.FAR;
        b.listed = false;
        job = b;
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon {} wird gebaut bei {} {} {} (Weg nach {}, {} Räume, {} Chunks)",
                rec.kind, rec.x, rec.y, rec.z, rec.facing, rec.rooms, byChunk.size());
    }

    /** The build's chunk lists: far ones first (the arena), the gatehouse's last; all of them asked for, with a ring. */
    private void list(ServerLevel level, SiegeSave.ExpeditionRecord rec, Build b) {
        plan(rec);
        b.levelChunks.clear();
        for (BlockPos pos : levelSpots(rec)) {
            b.levelChunks.add(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        }
        b.far.clear();
        b.near.clear();
        for (int i = chunkOrder.size() - 1; i >= 0; i--) {
            long key = chunkOrder.get(i);
            (b.levelChunks.contains(key) ? b.near : b.far).add(key);
        }
        Set<Long> want = new HashSet<>(byChunk.keySet());
        want.addAll(b.levelChunks);
        want = withRing(want);
        Set<Long> gone = new HashSet<>(b.want);
        gone.removeAll(want);
        release(level, gone);
        b.want.clear();
        b.want.addAll(want);
        b.askedAt = Long.MIN_VALUE;
        b.listed = true;
    }

    /** Every tick while a dungeon is being built. */
    private void tickBuild(MinecraftServer server, long now, long deadline) {
        Build b = job;
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null || rec.id != b.id || rec.won || rec.layout < LAYOUT) {
            cancelJob(server);   // its record went (taken away, or put aside by the playtest)
            return;
        }
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level == null) {
            return;
        }
        long t0 = System.nanoTime();
        b.busy = false;
        boolean done = stepBuild(level, rec, b, now, deadline);
        long spent = System.nanoTime() - t0;
        if (b.busy) {
            b.work += spent;
            b.longest = Math.max(b.longest, spent);
            b.busyTicks++;
        }
        if (done) {
            finishBuild(level, rec, b);
        } else if (b.dirty && now - b.savedAt >= 100) {
            b.dirty = false;
            b.savedAt = now;
            m.persist();   // which chunks stand (a restart goes on from there)
        }
        if (rec.built && b.stage != Build.SITE && !level.players().isEmpty()) {
            // Survival safety (31.10., D5): whoever its blocks closed round goes on top (before his next tick).
            SafeGround.liftStuck(level, area(rec, plan(rec)));
        }
    }

    /** How many places the playtest's and the admin's dungeon tries before it gives up (31.10., D3). */
    static final int SITE_TRIES = 8;

    /**
     * Survival safety (Simon 31.10., D3): the playtest's dungeon and the admin's ("dungeon bauen") get
     * the check of a dungeon built by itself; before, the reported spot was taken as it was, over
     * player bases, villages or a player in a cave. One place after the other, from the reported spot
     * outwards: its gatehouse ground (loaded for it), no player's building in any chunk along the way,
     * then its plan, the chunks of the whole dungeon loaded, every spot it sets nature (no chest, no
     * spawner, no planks), no one's things (armor stands, frames, pets, villagers). After
     * {@link #SITE_TRIES} places it gives up (the playtest goes on without it).
     */
    private boolean stepSite(ServerLevel level, SiegeSave.ExpeditionRecord rec, Build b, long now) {
        if (b.siteX == Integer.MIN_VALUE) {
            if (b.siteTries >= SITE_TRIES) {
                failSite(level, rec, b);
                return false;
            }
            nextSite(level, rec, b);
        }
        if (b.sitePlan == null && b.planning == null) {
            if (!asked(level, b.want, b, now)) {
                return false;   // the ground there is still loading
            }
            b.busy = true;
            SiegeCamp.Reasons why = new SiegeCamp.Reasons();
            Integer floor = SiegeCamp.site(level, b.siteX, b.siteZ, 9, 12, false, false, why);
            if (floor == null) {
                rejectSite(rec, b, why);
                return false;
            }
            BlockPos at = groundOrigin(level, new BlockPos(b.siteX, floor, b.siteZ));
            if (builtAlong(level, rec, at, why)) {
                rejectSite(rec, b, why);
                return false;
            }
            b.planning = workOut(at, rec.facing, kind(rec), rec.rooms, rec.id);   // its plan, in the background
            b.planAt = at;
            return false;
        }
        if (b.sitePlan == null) {
            asked(level, b.want, b, now);
            Prepared ready = ready(b.planning, b.planAt, rec.facing, kind(rec), rec.rooms, rec.id);
            if (ready == null) {
                return false;
            }
            b.planning = null;
            b.sitePlan = ready;
            b.planNanos += ready.nanos();
            // The chunks of the whole dungeon, for the check of every spot.
            Set<Long> all = new HashSet<>(ready.byChunk().keySet());
            AABB box = levelBoxAt(b.planAt, rec.facing);
            for (int cx = (int) Math.floor(box.minX) >> 4; cx <= (int) Math.floor(box.maxX) >> 4; cx++) {
                for (int cz = (int) Math.floor(box.minZ) >> 4; cz <= (int) Math.floor(box.maxZ) >> 4; cz++) {
                    all.add(chunkKey(cx, cz));
                }
            }
            want(level, b, withRing(all));
            return false;
        }
        if (!asked(level, b.want, b, now)) {
            return false;
        }
        b.busy = true;
        SiegeCamp.Reasons why = new SiegeCamp.Reasons();
        long t0 = System.nanoTime();
        Boolean free = freeOfPlayers(level, rec, b.planAt, b.sitePlan.plan(), why);
        b.checkNanos += System.nanoTime() - t0;
        if (free == null) {
            return false;   // (all asked for are loaded: not again)
        }
        if (!free) {
            rejectSite(rec, b, why);
            return false;
        }
        Prepared ready = b.sitePlan;
        b.sitePlan = null;
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon {}: Platz geprüft bei {} {} {} (Versuch {})", rec.kind,
                b.planAt.getX(), b.planAt.getY(), b.planAt.getZ(), b.siteTries + 1);
        beginBuild(level, rec, b.planAt, ready, b);
        return false;
    }

    /** The next place to try: the reported spot, then further and further round it. */
    private void nextSite(ServerLevel level, SiegeSave.ExpeditionRecord rec, Build b) {
        int x = rec.x;
        int z = rec.z;
        if (b.siteTries > 0) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double d = 32 + 24 * b.siteTries;
            x += (int) Math.round(Math.cos(angle) * d);
            z += (int) Math.round(Math.sin(angle) * d);
        }
        b.siteX = x;
        b.siteZ = z;
        b.sitePlan = null;
        b.planning = null;
        Set<Long> site = new HashSet<>();
        for (int cx = (x - 14) >> 4; cx <= (x + 14) >> 4; cx++) {
            for (int cz = (z - 14) >> 4; cz <= (z + 14) >> 4; cz++) {
                site.add(chunkKey(cx, cz));
            }
        }
        want(level, b, withRing(site));
    }

    /** The chunks a job asks for from now (those no longer needed are let go). */
    private static void want(ServerLevel level, Build b, Set<Long> want) {
        Set<Long> gone = new HashSet<>(b.want);
        gone.removeAll(want);
        release(level, gone);
        b.want.clear();
        b.want.addAll(want);
        b.askedAt = Long.MIN_VALUE;
    }

    private void rejectSite(SiegeSave.ExpeditionRecord rec, Build b, SiegeCamp.Reasons why) {
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon {}: Platz {} {} verworfen ({})", rec.kind, b.siteX, b.siteZ, why);
        b.siteTries++;
        b.siteX = Integer.MIN_VALUE;
        b.sitePlan = null;
        b.planning = null;
    }

    /** No place was safe: the job ends, the dungeon stays reported (built by itself later, with its checks). */
    private void failSite(ServerLevel level, SiegeSave.ExpeditionRecord rec, Build b) {
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon {}: kein sicherer Platz zum Bauen nach {} Orten", rec.kind, b.siteTries);
        cancelJob(level.getServer());
        if (prepping) {
            prepping = false;
            prepFailed = true;
        }
        tell(level.getServer(), b.asker, Component.translatable("nightsiege.cmd.expedition_no_spot"));
    }

    /** A player's building in any chunk along the way (the marks in the save, no chunk loaded for it). */
    private static boolean builtAlong(ServerLevel level, SiegeSave.ExpeditionRecord rec, BlockPos origin, SiegeCamp.Reasons why) {
        int length = ExpeditionLayout.length(rec.rooms) + 8;
        Set<Long> seen = new HashSet<>();
        for (int lz = -11; lz <= length; lz += 4) {
            for (int lx = -40; lx <= 40; lx += 4) {
                BlockPos at = CampLayout.world(origin, rec.facing, lx, 0, lz);
                int cx = at.getX() >> 4;
                int cz = at.getZ() >> 4;
                if (seen.add(PlayerBuilt.key(cx, cz)) && PlayerBuilt.marked(level, cx, cz)) {
                    why.built++;
                    why.detail = "Spieler haben bei Chunk " + cx + " " + cz + " gebaut";
                    return true;
                }
            }
        }
        return false;
    }

    /** {@link #levelBox} for a gatehouse at this spot. */
    private static AABB levelBoxAt(BlockPos origin, int facing) {
        SiegeSave.ExpeditionRecord at = new SiegeSave.ExpeditionRecord();
        at.x = origin.getX();
        at.y = origin.getY();
        at.z = origin.getZ();
        at.facing = facing;
        return levelBox(at);
    }

    /**
     * Survival safety (31.10., D4): a dungeon taken down whose ground is not on the disk yet lies near
     * this one (a crash would take it down again over the new one's blocks): the new one waits.
     */
    private boolean nearUnsaved(SiegeSave.ExpeditionRecord rec) {
        if (unsavedTeardowns.isEmpty()) {
            return false;
        }
        double reach = ExpeditionLayout.length(rec.rooms) + 16;   // as far as it can reach (see protects)
        AABB mine = new AABB(rec.x - reach, -4096, rec.z - reach, rec.x + reach + 1, 4096, rec.z + reach + 1);
        for (Teardown t : unsavedTeardowns.values()) {
            if (t.rec.dimension.equals(rec.dimension) && t.plan != null && area(t.rec, t.plan).intersects(mine)) {
                return true;   // only where the two would touch (the playtest's next one is not held up)
            }
        }
        return false;
    }

    /** Builds until the deadline. True when the dungeon is complete. */
    private boolean stepBuild(ServerLevel level, SiegeSave.ExpeditionRecord rec, Build b, long now, long deadline) {
        if (!teardowns.isEmpty() && !b.sync) {
            return false;   // the ground of the one before comes back first (it could lie where this one goes)
        }
        if (!b.sync && nearUnsaved(rec)) {
            return false;   // (31.10.) nor before the world is saved with it: a crash would take it down again
        }
        if (b.stage == Build.SITE && !b.anywhere && !b.sync) {
            return stepSite(level, rec, b, now);   // survival safety 31.10. (D3): its place is checked
        }
        if (b.stage == Build.SITE) {
            if (b.want.isEmpty()) {
                Set<Long> site = new HashSet<>();
                for (int cx = (rec.x - 14) >> 4; cx <= (rec.x + 14) >> 4; cx++) {
                    for (int cz = (rec.z - 14) >> 4; cz <= (rec.z + 14) >> 4; cz++) {
                        site.add(chunkKey(cx, cz));
                    }
                }
                b.want.addAll(withRing(site));
            }
            if (b.planning == null) {
                if (!asked(level, b.want, b, now)) {
                    return false;   // the ground there is still loading
                }
                Integer floor = SiegeCamp.site(level, rec.x, rec.z, 9, 12, false, false, null);
                if (floor == null) {
                    floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rec.x, rec.z);
                }
                b.busy = true;
                BlockPos at = groundOrigin(level, new BlockPos(rec.x, floor, rec.z));
                if (b.sync) {
                    beginBuild(level, rec, at, null, b);
                } else {
                    b.planning = workOut(at, rec.facing, kind(rec), rec.rooms, rec.id);   // its plan, in the background
                    b.planAt = at;
                }
                return false;
            }
            asked(level, b.want, b, now);
            Prepared ready = ready(b.planning, b.planAt, rec.facing, kind(rec), rec.rooms, rec.id);
            if (ready == null) {
                return false;
            }
            b.planning = null;
            b.busy = true;
            beginBuild(level, rec, b.planAt, ready, b);
            return false;
        }
        if (!b.listed) {
            list(level, rec, b);
            b.busy = true;
        }
        asked(level, b.want, b, now);
        ExpeditionLayout.Plan p = plan(rec);
        ExpeditionSnapshot snap = snapshot(level.getServer(), rec);
        Set<BlockPos> openGates = null;
        while (true) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            if (b.stage == Build.LEVEL) {
                for (long key : b.levelChunks) {
                    if (!loadedAround(level, key, b.sync)) {
                        return false;
                    }
                }
                // Survival safety (31.10., D4): levelled again after a crash that lost it (written down,
                // not on the disk): only where the ground is still as it was before the dungeon.
                boolean again = snap.has(LEVEL_CHUNK) && Boolean.FALSE.equals(rec.levelDone) && unsavedLevelId != rec.id;
                if (!snap.has(LEVEL_CHUNK) || again) {
                    if (!b.sync && !clearOfPlayers(level, rec, now, b)) {
                        return false;   // someone stands where the gatehouse goes: a little while
                    }
                    // No animal, villager or pet walled into the gatehouse or the earth (31.10., D6).
                    SafeGround.clearCreatures(level, levelBox(rec), front(level, rec, p), Expeditions::ours);
                    if (again) {
                        Map<BlockPos, BlockState> was = new HashMap<>();
                        List<BlockPos> where = snap.places(LEVEL_CHUNK) != null ? snap.places(LEVEL_CHUNK) : levelSpots(rec);
                        for (int i = 0; i < where.size(); i++) {
                            was.put(where.get(i), snap.old(LEVEL_CHUNK, i));
                        }
                        levelGround(level, rec, pos -> level.getBlockState(pos) == was.get(pos));
                        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon {}: Gelände nach einem Absturz erneut geebnet", rec.kind);
                    } else {
                        // What stands where the ground is levelled goes back last when the dungeon is gone.
                        snap.record(LEVEL_CHUNK, level, levelSpots(rec));
                        levelGround(level, rec, null);
                    }
                    unsavedLevelId = rec.id;
                }
                b.busy = true;
                b.stage = Build.NEAR;
                continue;
            }
            if (b.stage == Build.SHAPE) {
                return shapeStep(level, rec, b, deadline);
            }
            List<Long> list = b.stage == Build.FAR ? b.far : b.near;
            if (cursorChunk == Long.MIN_VALUE) {
                long next = Long.MIN_VALUE;
                boolean left = false;
                for (long key : list) {
                    if (chunkDone(rec, key)) {
                        continue;
                    }
                    left = true;
                    if (loadedAround(level, key, b.sync)) {
                        next = key;
                        break;
                    }
                }
                if (!left) {
                    b.stage = b.stage == Build.FAR ? Build.LEVEL : Build.SHAPE;
                    continue;
                }
                if (next == Long.MIN_VALUE) {
                    return false;   // the next chunks are still loading
                }
                if (!b.sync && !hallsClear(level, p, b, now)) {
                    return false;   // someone in a cave where the halls go: a little while (31.10., D5)
                }
                cursorChunk = next;
                cursorIndex = 0;
                if (!snap.has(next)) {
                    // Before the chunk is touched, what stands there is written down (the snapshot).
                    List<BlockPos> where = new ArrayList<>();
                    for (Spot s : byChunk.get(next)) {
                        where.add(s.pos());
                    }
                    snap.record(next, level, where);
                }
                clearChunk(level, p, next);
                b.busy = true;
            } else if (!loadedAround(level, cursorChunk, b.sync)) {
                cursorChunk = Long.MIN_VALUE;   // unloaded meanwhile: again from the start when it is back
                return false;
            }
            if (openGates == null) {
                openGates = new HashSet<>();
                for (int i = 0; i < Math.min(rec.cleared, p.roomGates.size()); i++) {
                    openGates.addAll(p.roomGates.get(i));
                }
                if (rec.cleared > 0) {
                    openGates.addAll(p.frontGate);
                }
            }
            List<Spot> spots = byChunk.get(cursorChunk);
            for (; cursorIndex < spots.size(); cursorIndex++) {
                if ((cursorIndex & 63) == 0 && System.nanoTime() >= deadline) {
                    return false;
                }
                b.busy = true;
                Spot s = spots.get(cursorIndex);
                BlockState there = level.getBlockState(s.pos());
                if (there.hasBlockEntity()) {
                    // Somebody's chest stays where it is. Survival safety (Simon 31.10., D3): a spawner too (the
                    // snapshot keeps only the block, it came back without its monster), and every other block
                    // entity with its data.
                    continue;
                }
                if (s.fill() && !(there.isAir() || there.canBeReplaced() || !there.getFluidState().isEmpty())) {
                    continue;   // the ground above the vaults only where there is none
                }
                BlockState want = openGates.contains(s.pos()) ? Blocks.AIR.defaultBlockState() : s.want();
                if (there != want) {
                    level.setBlock(s.pos(), want, BULK);
                }
            }
            // Survival safety (31.10., D4): into chunksDone only with the next world save (afterWorldSave);
            // before, it was saved every 5 s ahead of the world and a crash left holes that were never set.
            if (unsavedChunksId != rec.id) {
                unsavedChunks.clear();
                unsavedChunksId = rec.id;
            }
            unsavedChunks.add(cursorChunk);
            cursorChunk = Long.MIN_VALUE;
            b.dirty = true;
        }
    }

    /** The last pass: fences, walls, bars and stairs join their neighbours (they were set without it). */
    private boolean shapeStep(ServerLevel level, SiegeSave.ExpeditionRecord rec, Build b, long deadline) {
        if (b.shaped == null) {
            b.shaped = new ArrayList<>();
            for (long key : chunkOrder) {
                for (Spot s : byChunk.get(key)) {
                    if (!s.fill() && joins(s.want().getBlock())) {
                        b.shaped.add(s);
                    }
                }
            }
            b.shapeAt = 0;
        }
        long checked = Long.MIN_VALUE;
        for (; b.shapeAt < b.shaped.size(); b.shapeAt++) {
            if ((b.shapeAt & 63) == 0 && System.nanoTime() >= deadline) {
                return false;
            }
            Spot s = b.shaped.get(b.shapeAt);
            long key = chunkKey(s.pos().getX() >> 4, s.pos().getZ() >> 4);
            if (key != checked) {
                if (!loadedAround(level, key, b.sync)) {
                    return false;
                }
                checked = key;
            }
            b.busy = true;
            BlockState there = level.getBlockState(s.pos());
            if (there.is(s.want().getBlock())) {
                BlockState shaped = Block.updateFromNeighbourShapes(there, level, s.pos());
                if (shaped != there) {
                    level.setBlock(s.pos(), shaped, BULK);
                }
            }
        }
        return true;
    }

    private static boolean joins(Block b) {
        return b instanceof net.minecraft.world.level.block.CrossCollisionBlock || b instanceof net.minecraft.world.level.block.WallBlock
                || b instanceof net.minecraft.world.level.block.StairBlock;
    }

    /**
     * Before the gatehouse goes up where the dungeon was built by itself: nobody stands there.
     * After 30 s of waiting, whoever still does is put before its door.
     */
    private boolean clearOfPlayers(ServerLevel level, SiegeSave.ExpeditionRecord rec, long now, Build b) {
        BlockPos a = CampLayout.world(new BlockPos(rec.x, rec.y, rec.z), rec.facing, -9, -14, -11);
        BlockPos c = CampLayout.world(new BlockPos(rec.x, rec.y, rec.z), rec.facing, 9, 16, 8);
        AABB box = new AABB(Math.min(a.getX(), c.getX()), Math.min(a.getY(), c.getY()), Math.min(a.getZ(), c.getZ()),
                Math.max(a.getX(), c.getX()) + 1, Math.max(a.getY(), c.getY()) + 1, Math.max(a.getZ(), c.getZ()) + 1);
        List<ServerPlayer> there = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && box.contains(p.position())) {
                there.add(p);
            }
        }
        if (there.isEmpty()) {
            return true;
        }
        if (b.waitSince < 0) {
            b.waitSince = now;
        }
        // Survival safety (31.10., D3): the playtest's and the admin's dungeon too (before, no check at
        // all); they do not wait (nobody of them stands there), whoever does is put before the door at once.
        if (b.careful && now - b.waitSince < 600) {
            return false;
        }
        BlockPos door = plan(rec).door;
        for (ServerPlayer p : there) {
            p.teleportTo(level, door.getX() + 0.5, door.getY(), door.getZ() + 0.5, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
        }
        return true;
    }

    /**
     * Survival safety (Simon 31.10., D5): before the next chunk is set, nobody stands where the halls,
     * ways and arena go (with their walls), anywhere along the dungeon (it reaches 150–250 blocks; before,
     * only the gatehouse was looked at and a player in a cave by the halls was walled in). Built by itself
     * it waits 30 s for him to go; then, or at once for the playtest's and the admin's, he is put on top
     * of the ground. Creative players and spectators fly out by themselves.
     */
    private boolean hallsClear(ServerLevel level, ExpeditionLayout.Plan p, Build b, long now) {
        List<ServerPlayer> there = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && !player.getAbilities().instabuild && p.bounds.contains(player.position())
                    && inside(p, player, 3.0)) {
                there.add(player);
            }
        }
        if (there.isEmpty()) {
            b.hallWaitSince = -1L;
            return true;
        }
        if (b.hallWaitSince < 0) {
            b.hallWaitSince = now;
            for (ServerPlayer player : there) {
                player.sendSystemMessage(Component.translatable("nightsiege.expedition.build_leave").withStyle(ChatFormatting.GOLD));
            }
        }
        if (b.careful && now - b.hallWaitSince < 600) {
            return false;
        }
        for (ServerPlayer player : there) {
            BlockPos top = SafeGround.surface(level, player.getBlockX(), player.getBlockZ());
            if (top != null) {
                // On top of the ground above him (above whatever of the dungeon stands there already).
                player.teleportTo(level, player.getX(), top.getY(), player.getZ(), java.util.Set.of(), player.getYRot(),
                        player.getXRot(), true);
                player.resetFallDistance();
                player.sendSystemMessage(Component.translatable("nightsiege.expedition.build_moved").withStyle(ChatFormatting.GRAY));
                NightSiegeMod.LOGGER.info("[Night Siege] {} stand beim Bau des Dungeons in seinen Hallen und wurde nach oben gebracht",
                        player.getScoreboardName());
            }
        }
        b.hallWaitSince = -1L;
        return true;
    }

    /** The dungeon stands, every block of it. */
    private void finishBuild(ServerLevel level, SiegeSave.ExpeditionRecord rec, Build b) {
        rec.complete = true;
        checkedComplete = rec.id;   // (31.10.) complete in this run: on the disk with the next world save
        job = null;
        cursorChunk = Long.MIN_VALUE;
        release(level, b.want);
        m.persist();
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon {} steht bei {} {} {} (Weg nach {}, {} Räume)", rec.kind,
                rec.x, rec.y, rec.z, rec.facing, rec.rooms);
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon-Bau: {}Plan {} ms (davon im Tick {} ms){}, Bau {} ms in {} Ticks (längster Tick {} ms), {} Chunks, fertig nach {} s",
                b.searchMs >= 0 ? "Suche " + b.searchMs + " ms in " + b.searchTicks + " Ticks, " : "",
                b.planNanos / 1_000_000L, b.tickNanos / 1_000_000L,
                b.checkNanos > 0 ? ", Platzprüfung " + b.checkNanos / 1_000_000L + " ms" : "",
                b.work / 1_000_000L, b.busyTicks, b.longest / 1_000_000L, byChunk.size(),
                String.format(Locale.ROOT, "%.1f", (System.nanoTime() - b.born) / 1e9));
    }

    /** The job of now is dropped (its chunks are let go). */
    private void cancelJob(MinecraftServer server) {
        Build b = job;
        job = null;
        cursorChunk = Long.MIN_VALUE;
        if (b != null && !b.want.isEmpty()) {
            ServerLevel level = SiegeManager.levelByName(server, b.dimension);
            if (level != null) {
                release(level, b.want);
            }
        }
    }

    /** Tests: the job of now done in this tick, its chunks loaded (or generated) on the way. */
    private void finishJobNow(ServerLevel level) {
        Build b = job;
        if (b == null) {
            return;
        }
        b.sync = true;
        SiegeSave.ExpeditionRecord rec = record();
        for (int guard = 0; guard < 64 && job == b; guard++) {
            long t0 = System.nanoTime();
            boolean done = stepBuild(level, rec, b, level.getGameTime(), Long.MAX_VALUE);
            b.work += System.nanoTime() - t0;
            b.longest = b.work;
            b.busyTicks = 1;
            if (done) {
                finishBuild(level, rec, b);
            }
        }
    }

    /**
     * Levels the ground round the gatehouse: away with trees and hills, earth into dips, grass and the path.
     * {@code may}: only these spots (null: all); survival safety (31.10.): no block entity is ever replaced.
     */
    private static void levelGround(ServerLevel level, SiegeSave.ExpeditionRecord rec, java.util.function.Predicate<BlockPos> may) {
        BlockPos origin = new BlockPos(rec.x, rec.y, rec.z);
        for (int lx = -8; lx <= 8; lx++) {
            for (int lz = -10; lz <= 7; lz++) {
                BlockPos column = CampLayout.world(origin, rec.facing, lx, 0, lz);
                for (int up = 0; up <= 14; up++) {
                    BlockPos pos = column.above(up);
                    BlockState there = level.getBlockState(pos);
                    if (!there.isAir() && !there.hasBlockEntity() && SiegeCamp.natural(there) && (may == null || may.test(pos))) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                for (int down = 2; down <= 12; down++) {
                    BlockPos pos = column.below(down);
                    BlockState there = level.getBlockState(pos);
                    if (!there.isAir() && !there.canBeReplaced() && there.getFluidState().isEmpty() || there.hasBlockEntity()) {
                        break;
                    }
                    if (may != null && !may.test(pos)) {
                        continue;
                    }
                    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                BlockPos under = column.below();
                boolean path = Math.abs(lx) <= 1 && lz <= -6;
                BlockState floor = level.getBlockState(under);
                if ((path || floor.isAir() || floor.canBeReplaced() || !floor.getFluidState().isEmpty())
                        && !floor.hasBlockEntity() && (may == null || may.test(under))) {
                    level.setBlock(under, path ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The snapshot's key for the levelled ground round the gatehouse. */
    private static final long LEVEL_CHUNK = Long.MAX_VALUE;

    /** The spots {@link #levelGround} levels: round the gatehouse, from below the ground to the sky. */
    private static List<BlockPos> levelSpots(SiegeSave.ExpeditionRecord rec) {
        BlockPos origin = new BlockPos(rec.x, rec.y, rec.z);
        List<BlockPos> out = new ArrayList<>();
        for (int lx = -8; lx <= 8; lx++) {
            for (int lz = -10; lz <= 7; lz++) {
                BlockPos column = CampLayout.world(origin, rec.facing, lx, 0, lz);
                for (int dy = -13; dy <= 14; dy++) {
                    out.add(column.above(dy));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the chunks a job needs

    /** These chunks and the ring round them (whatever touches a neighbour finds it loaded). */
    private static Set<Long> withRing(Set<Long> chunks) {
        Set<Long> out = new HashSet<>();
        for (long key : chunks) {
            int cx = chunkX(key);
            int cz = chunkZ(key);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.add(chunkKey(cx + dx, cz + dz));
                }
            }
        }
        return out;
    }

    /**
     * Keeps the chunks asked for (once a second: a ticket each, which runs out by itself 2 s after
     * the last call, so nothing stays loaded when a job is dropped). True when all are loaded.
     */
    private static boolean asked(ServerLevel level, Set<Long> chunks, Build b, long now) {
        if (now - b.askedAt >= 20 || b.askedAt == Long.MIN_VALUE) {
            b.askedAt = now;
            ask(level, chunks);
        }
        for (long key : chunks) {
            if (level.getChunkSource().getChunkNow(chunkX(key), chunkZ(key)) == null) {
                return false;
            }
        }
        return true;
    }

    private static void ask(ServerLevel level, Set<Long> chunks) {
        try {
            for (long key : chunks) {
                level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(chunkX(key), chunkZ(key)), 0);
            }
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Dungeon: Chunks nicht angefordert: {}", e.toString());
        }
    }

    private static void release(ServerLevel level, Set<Long> chunks) {
        try {
            for (long key : chunks) {
                level.getChunkSource().removeTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(chunkX(key), chunkZ(key)), 0);
            }
        } catch (RuntimeException e) {
            NightSiegeMod.LOGGER.debug("[Night Siege] Dungeon: Chunks nicht freigegeben: {}", e.toString());
        }
    }

    /**
     * The chunk is there to read and set, now (update 30.10.: {@code hasChunk} is true as soon as a
     * chunk is asked for, before it is loaded or generated, and reading it then waits for it).
     */
    static boolean loaded(ServerLevel level, int cx, int cz) {
        return level.getChunkSource().getChunkNow(cx, cz) != null;
    }

    /**
     * The chunk and its eight neighbours are loaded (never loaded here in the middle of a tick:
     * that took seconds and broke C2ME's entity sections). {@code sync}: tests, loaded now.
     */
    private static boolean loadedAround(ServerLevel level, long key, boolean sync) {
        int cx = chunkX(key);
        int cz = chunkZ(key);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) {
                    if (!sync) {
                        return false;
                    }
                    level.getChunk(cx + dx, cz + dz);
                }
            }
        }
        return true;
    }

    /** A dungeon of the old plan (no snapshot): closed, its blocks stay; the scouts report anew. */
    private void legacy(SiegeSave.ExpeditionRecord rec) {
        if (rec.built) {
            NightSiegeMod.LOGGER.info("[Night Siege] Alter Dungeon {} ({}) geschlossen: neue Dungeons ab dem Content-Update",
                    rec.kind, rec.won ? "bezwungen" : "offen");
            m.save().expedition = null;
        } else {
            rec.layout = LAYOUT;
        }
        plan = null;
        m.persist();
    }

    // ------------------------------------------------------------------ taking it down

    /**
     * After the win: 30 s to get out (the dungeon shakes), then everyone and everything left
     * inside is put before the gatehouse and the dungeon goes, a few milliseconds a tick, from the
     * gatehouse outwards; the ground comes back as it was (see {@link Teardown}).
     */
    private void afterWin(ServerLevel level, SiegeSave.ExpeditionRecord rec, long now) {
        ExpeditionLayout.Plan p = plan(rec);
        if (!rec.restoring && rec.collapseAt > 0 && now < rec.collapseAt) {
            if (now % 20 == 0) {
                int left = (int) ((rec.collapseAt - now) / 20);
                Component text = Component.translatable("nightsiege.expedition.collapse_bar", left).withStyle(ChatFormatting.GOLD);
                for (ServerPlayer player : level.players()) {
                    if (p.bounds.contains(player.position())) {
                        player.connection.send(new ClientboundSetActionBarTextPacket(text));
                        player.connection.send(new ClientboundSoundPacket(SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT,
                                player.getX(), player.getY(), player.getZ(), 0.5f, 0.4f, random.nextLong()));
                        DungeonFx.debris(level, player, ExpeditionLayout.B.block(kind(rec).block(ExpeditionKind.Part.CEILING)));
                    }
                }
            }
            return;
        }
        if (!rec.restoring) {
            evacuate(level, rec, p);
            pendingGates.clear();   // no gate of it goes up in the ground that comes back
            rec.restoring = true;
            m.persist();   // from here the teardown takes it (see work)
        }
    }

    /**
     * Everyone inside out, items lying about there too, the dungeon's monsters gone. Survival safety
     * (Simon 31.10., D2): everyone in the whole place (the fallen on the waiting benches too, who stood
     * in the gatehouse when its hill came back) is put before it, past the levelled ground, where the
     * ground does not change; the place is remembered (see SafeGround: who stands in a block while the
     * ground comes back, or logs in in one later, goes on top).
     */
    private void evacuate(ServerLevel level, SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p) {
        AABB area = area(rec, p);
        AABB gatehouse = levelBox(rec);
        Vec3 front = front(level, rec, p);
        for (ServerPlayer player : new ArrayList<>(level.players())) {
            if (!player.isSpectator() && (sealed(p, rec, player) || gatehouse.contains(player.position()))) {
                player.teleportTo(level, front.x, front.y, front.z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                player.resetFallDistance();
            }
        }
        for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                area)) {
            item.teleportTo(front.x, front.y + 0.5, front.z);
        }
        for (Entity e : level.getEntitiesOfClass(Entity.class, area.inflate(4), Expeditions::isExpeditionMob)) {
            BossService.dropFoeLook(level.getServer(), e.getId());
            e.discard();
        }
        sweep(level, p, front);   // nothing else is walled in when the ground comes back
        SafeGround.clearCreatures(level, gatehouse, front, Expeditions::ours);   // the gatehouse's (31.10.)
        SafeGround.remember(m.save(), "dungeon", rec.id, rec.dimension, area, m.dayNow(level.getServer()));
    }

    /** The whole place of a dungeon: its bounds and the levelled ground round the gatehouse. */
    private static AABB area(SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p) {
        AABB level = levelBox(rec);
        AABB b = p.bounds;
        return new AABB(Math.min(b.minX, level.minX), Math.min(b.minY, level.minY), Math.min(b.minZ, level.minZ),
                Math.max(b.maxX, level.maxX), Math.max(b.maxY, level.maxY) + 2, Math.max(b.maxZ, level.maxZ)).inflate(1.0);
    }

    /** The levelled ground round the gatehouse (see {@link #levelSpots}), from below the floor to the sky. */
    private static AABB levelBox(SiegeSave.ExpeditionRecord rec) {
        BlockPos origin = new BlockPos(rec.x, rec.y, rec.z);
        BlockPos a = CampLayout.world(origin, rec.facing, -8, -13, -10);
        BlockPos c = CampLayout.world(origin, rec.facing, 8, 16, 7);
        return new AABB(Math.min(a.getX(), c.getX()), Math.min(a.getY(), c.getY()), Math.min(a.getZ(), c.getZ()),
                Math.max(a.getX(), c.getX()) + 1, Math.max(a.getY(), c.getY()) + 1, Math.max(a.getZ(), c.getZ()) + 1);
    }

    /**
     * Before the dungeon, past its levelled ground (Simon 31.10.): on top of the ground there, which the
     * dungeon never changed (the door itself lies on the levelled ground, which comes back last).
     */
    private static Vec3 front(ServerLevel level, SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p) {
        BlockPos at = CampLayout.world(new BlockPos(rec.x, rec.y, rec.z), rec.facing, 0, 0, -13);
        BlockPos top = SafeGround.surface(level, at.getX(), at.getZ());
        if (top == null) {
            return new Vec3(p.door.getX() + 0.5, p.door.getY(), p.door.getZ() + 0.5);
        }
        return new Vec3(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
    }

    /**
     * A dungeon going (update 30.10.: before, it went in one tick and the server stood still for
     * 2–5 s): its chunks are asked for and the old blocks put back a few milliseconds a tick, from
     * the gatehouse outwards, the levelled ground last. It has its own plan and snapshot, so the
     * next dungeon may be reported meanwhile.
     */
    private static final class Teardown {
        final SiegeSave.ExpeditionRecord rec;
        ExpeditionLayout.Plan plan;
        Map<Long, List<Spot>> byChunk;
        List<Long> order;
        ExpeditionSnapshot snap;
        final Set<Long> want = new HashSet<>();
        final Set<Long> levelChunks = new HashSet<>();
        List<BlockPos> levelPlaces;
        long cursor = Long.MIN_VALUE;
        List<BlockPos> where;
        int index;
        boolean levelling;
        long askedAt = Long.MIN_VALUE;
        boolean sync;
        boolean dirty;
        long savedAt;
        // The clock (for the log line).
        final long born = System.nanoTime();
        long work;
        long longest;
        int busyTicks;
        int chunks;

        Teardown(SiegeSave.ExpeditionRecord rec) {
            this.rec = rec;
        }
    }

    /** The teardown of a dungeon (made when first needed, also after a restart). */
    private Teardown teardownOf(MinecraftServer server, SiegeSave.ExpeditionRecord rec) {
        Teardown t = teardowns.get(rec.id);
        if (t != null) {
            return t;
        }
        t = new Teardown(rec);
        List<Long> outward;
        if (plan != null && planId == rec.id) {
            t.plan = plan;
            t.byChunk = byChunk;
            outward = chunkOrder;
        } else {
            t.plan = ExpeditionLayout.plan(new BlockPos(rec.x, rec.y, rec.z), rec.facing, kind(rec), rec.rooms, rec.id);
            t.byChunk = spotsByChunk(t.plan);
            outward = outwards(t.byChunk.keySet(), rec);
        }
        t.snap = snapshot != null && snapshotId == rec.id ? snapshot : ExpeditionSnapshot.load(server, rec.id);
        // The plan's chunks from the gatehouse outwards, then any other the snapshot knows.
        t.order = new ArrayList<>(outward);
        Set<Long> known = new HashSet<>(outward);
        for (long key : t.snap.keys()) {
            if (key != LEVEL_CHUNK && known.add(key)) {
                t.order.add(key);
            }
        }
        Set<Long> want = new HashSet<>();
        for (long key : t.order) {
            if (t.snap.has(key) || rec.chunksDone.contains(key)) {
                want.add(key);
            }
        }
        if (t.snap.has(LEVEL_CHUNK)) {
            t.levelPlaces = t.snap.places(LEVEL_CHUNK) != null ? t.snap.places(LEVEL_CHUNK) : levelSpots(rec);
            for (BlockPos pos : t.levelPlaces) {
                t.levelChunks.add(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
            }
            want.addAll(t.levelChunks);
        }
        t.want.addAll(withRing(want));
        teardowns.put(rec.id, t);
        return t;
    }

    /** The dungeons to take down: those taken away, and the won one of now once it collapses. */
    private void findTeardowns(MinecraftServer server) {
        List<SiegeSave.ExpeditionRecord> gone = m.save().expeditionTeardowns;
        if (gone != null) {
            for (SiegeSave.ExpeditionRecord rec : new ArrayList<>(gone)) {
                if (!unsavedTeardowns.containsKey(rec.id)) {   // (31.10.) done, waiting for the world save
                    teardownOf(server, rec);
                }
            }
        }
        SiegeSave.ExpeditionRecord rec = record();
        if (rec != null && rec.won && rec.built && rec.restoring && rec.layout >= LAYOUT && !unsavedTeardowns.containsKey(rec.id)) {
            teardownOf(server, rec);
        }
    }

    /**
     * A dungeon taken away (removed, replaced, the playtest's): everyone out at once, then its
     * ground comes back over the next ticks (kept in the save until it is done).
     */
    private void detach(MinecraftServer server, SiegeSave.ExpeditionRecord rec) {
        if (job != null && job.id == rec.id) {
            cancelJob(server);
        }
        rec.won = true;
        rec.restoring = true;
        pendingGates.clear();   // no gate of it goes up in the ground that comes back
        if (m.save().expeditionTeardowns == null) {
            m.save().expeditionTeardowns = new ArrayList<>();
        }
        if (!m.save().expeditionTeardowns.contains(rec)) {
            m.save().expeditionTeardowns.add(rec);
        }
        if (m.save().expedition == rec) {
            m.save().expedition = null;
        }
        Teardown t = teardownOf(server, rec);
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level != null) {
            evacuate(level, rec, t.plan);
        }
        m.persist();
    }

    /** Every tick while a dungeon goes. */
    private void tickTeardown(MinecraftServer server, Teardown t, long now, long deadline) {
        ServerLevel level = SiegeManager.levelByName(server, t.rec.dimension);
        if (level == null) {
            NightSiegeMod.LOGGER.warn("[Night Siege] Dungeon {}: Welt {} fehlt, Abbau aufgegeben", t.rec.kind, t.rec.dimension);
            dropTeardown(t);
            return;
        }
        if (!t.sync && (now - t.askedAt >= 20 || t.askedAt == Long.MIN_VALUE)) {
            t.askedAt = now;
            ask(level, t.want);
        }
        long t0 = System.nanoTime();
        boolean[] busy = {false};
        boolean done = stepTeardown(level, t, deadline, busy);
        long spent = System.nanoTime() - t0;
        if (busy[0]) {
            t.work += spent;
            t.longest = Math.max(t.longest, spent);
            t.busyTicks++;
        }
        if (done) {
            finishTeardown(level, t);
        } else if (t.dirty && now - t.savedAt >= 100) {
            t.dirty = false;
            t.savedAt = now;
            m.persist();
        }
    }

    /** Puts old blocks back until the deadline. True when all are back. */
    private boolean stepTeardown(ServerLevel level, Teardown t, long deadline, boolean[] busy) {
        while (true) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            if (t.levelling) {
                if (t.levelPlaces == null) {
                    return true;
                }
                for (long key : t.levelChunks) {
                    if (!loadedAround(level, key, t.sync)) {
                        return false;
                    }
                }
                for (; t.index < t.levelPlaces.size(); t.index++) {
                    if ((t.index & 63) == 0 && System.nanoTime() >= deadline) {
                        return false;
                    }
                    busy[0] = true;
                    BlockState old = t.snap.old(LEVEL_CHUNK, t.index);
                    BlockPos pos = t.levelPlaces.get(t.index);
                    // The levelled ground: earth, grass and the path were the dungeon's; a player's things stay (31.10.).
                    if (old != null && level.getBlockState(pos) != old && com.mki.nightsiege.game.Teardown.mayReset(level, pos, old, now -> now.is(Blocks.DIRT)
                            || now.is(Blocks.GRASS_BLOCK) || now.is(Blocks.DIRT_PATH) || dungeonOwn(t.plan, pos, now))) {
                        level.setBlock(pos, old, BULK);
                    }
                }
                return true;
            }
            if (t.cursor == Long.MIN_VALUE) {
                long next = Long.MIN_VALUE;
                boolean left = false;
                for (long key : t.order) {
                    if (!t.snap.has(key) && !t.rec.chunksDone.contains(key)) {
                        continue;
                    }
                    left = true;
                    if (loadedAround(level, key, t.sync)) {
                        next = key;
                        break;
                    }
                }
                if (!left) {
                    t.levelling = true;   // the plan's chunks are back: now the levelled ground
                    t.index = 0;
                    continue;
                }
                if (next == Long.MIN_VALUE) {
                    return false;   // the next chunks are still loading
                }
                t.cursor = next;
                t.index = 0;
                t.where = snapPlaces(t, next);
                if (t.where == null) {
                    t.rec.chunksDone.remove((Long) next);
                    t.snap.forget(next);
                    t.cursor = Long.MIN_VALUE;
                    continue;
                }
            }
            for (; t.index < t.where.size(); t.index++) {
                if ((t.index & 63) == 0 && System.nanoTime() >= deadline) {
                    return false;
                }
                busy[0] = true;
                BlockState old = t.snap.old(t.cursor, t.index);
                BlockPos pos = t.where.get(t.index);
                if (old != null && level.getBlockState(pos) != old
                        && com.mki.nightsiege.game.Teardown.mayReset(level, pos, old, now -> dungeonOwn(t.plan, pos, now))) {   // a player's things stay (31.10.)
                    level.setBlock(pos, old, BULK);
                }
            }
            t.rec.chunksDone.remove((Long) t.cursor);
            t.snap.forget(t.cursor);
            t.cursor = Long.MIN_VALUE;
            t.where = null;
            t.chunks++;
            t.dirty = true;
        }
    }

    /** Survival safety (31.10.): a block the dungeon set at this spot itself (its plan; the arena's bars). */
    private static boolean dungeonOwn(ExpeditionLayout.Plan p, BlockPos pos, BlockState now) {
        BlockState want = p.blocks.get(pos);
        if (want == null) {
            want = p.fill.get(pos);
        }
        return now.is(Blocks.IRON_BARS) || want != null && now.is(want.getBlock());
    }

    /**
     * Where the snapshot's spots of a chunk are: written in the snapshot since 29 October 2026
     * (so a changed plan cannot shift them), for older ones the plan's order. Null when unknown.
     */
    private static List<BlockPos> snapPlaces(Teardown t, long chunk) {
        if (!t.snap.has(chunk)) {
            return null;
        }
        List<BlockPos> where = t.snap.places(chunk);
        if (where != null) {
            return where;
        }
        List<Spot> spots = t.byChunk.get(chunk);
        if (spots == null) {
            return null;
        }
        where = new ArrayList<>(spots.size());
        for (Spot s : spots) {
            where.add(s.pos());
        }
        return where;
    }

    /**
     * All back: the snapshot and the record go. Survival safety (Simon 31.10.): whoever stands in a
     * block anywhere in the place now goes on top (D2: before, only who stood by the door); and the
     * snapshot and the record only go once the world is saved (D4, see {@link #afterWorldSave}): until
     * then a crash brings the dungeon's blocks back from the disk, and the teardown runs again.
     */
    private void finishTeardown(ServerLevel level, Teardown t) {
        SafeGround.liftStuck(level, area(t.rec, t.plan));
        SafeGround.done(level.getServer(), m.save(), "dungeon", t.rec.id);
        release(level, t.want);
        teardowns.remove(t.rec.id);
        unsavedTeardowns.put(t.rec.id, t);
        if (snapshot == t.snap) {
            snapshot = null;
            snapshotId = -1;
        }
        if (m.save().expedition == t.rec) {
            m.save().expedition = null;   // the next one may be reported; kept below until the world is saved
            plan = null;
        }
        if (m.save().expeditionTeardowns == null) {
            m.save().expeditionTeardowns = new ArrayList<>();
        }
        if (!m.save().expeditionTeardowns.contains(t.rec)) {
            m.save().expeditionTeardowns.add(t.rec);
        }
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon {} ist abgebaut, das Gelände wie vorher", t.rec.kind);
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon-Abbau: {} ms in {} Ticks (längster Tick {} ms), {} Chunks, fertig nach {} s",
                t.work / 1_000_000L, t.busyTicks, t.longest / 1_000_000L, t.chunks,
                String.format(Locale.ROOT, "%.1f", (System.nanoTime() - t.born) / 1e9));
        m.persist();
    }

    /** Teardowns done whose ground is not on the disk yet (by id): their snapshot and record go with the next world save. */
    private final Map<Integer, Teardown> unsavedTeardowns = new java.util.LinkedHashMap<>();
    /**
     * Survival safety (31.10., D4): chunks of the dungeon being built that are set but not on the disk
     * yet (they go into {@code chunksDone} with the next world save; after a crash they are set again),
     * and the dungeon whose ground was levelled since.
     */
    private final Set<Long> unsavedChunks = new HashSet<>();
    private int unsavedChunksId = -1;
    private int unsavedLevelId = -1;
    /** The complete dungeon of before a restart was looked at once (were all its chunks on the disk?). */
    private int checkedComplete = -1;

    /** A chunk of the dungeon of now is set (in this run, or on the disk). */
    private boolean chunkDone(SiegeSave.ExpeditionRecord rec, long key) {
        return rec.chunksDone.contains(key) || unsavedChunksId == rec.id && unsavedChunks.contains(key);
    }

    /**
     * The world is saved (Fabric's AFTER_SAVE: the autosave, /save-all, the stop): what was built and
     * taken down before it is on the disk now. The chunks set go into the record, the levelled ground
     * counts, the chunks put back are forgotten in their snapshots, a teardown done goes with its file.
     */
    void afterWorldSave(MinecraftServer server) {
        boolean changed = false;
        SiegeSave.ExpeditionRecord rec = record();
        if (rec != null && unsavedChunksId == rec.id && !unsavedChunks.isEmpty()) {
            for (long key : unsavedChunks) {
                if (!rec.chunksDone.contains(key)) {
                    rec.chunksDone.add(key);
                }
            }
            changed = true;
        }
        unsavedChunks.clear();
        if (rec != null && unsavedLevelId == rec.id) {
            rec.levelDone = true;
            changed = true;
        }
        unsavedLevelId = -1;
        for (Teardown t : teardowns.values()) {
            t.snap.commit();
        }
        for (Teardown t : new ArrayList<>(unsavedTeardowns.values())) {
            t.snap.delete();
            dropTeardown(t);
            changed = true;
        }
        unsavedTeardowns.clear();
        if (changed) {
            m.persist();
        }
    }

    /** The teardown is over (or given up): its record goes from wherever it is. */
    private void dropTeardown(Teardown t) {
        teardowns.remove(t.rec.id);
        if (snapshot == t.snap) {
            snapshot = null;
            snapshotId = -1;
        }
        if (m.save().expeditionTeardowns != null) {
            m.save().expeditionTeardowns.remove(t.rec);
        }
        if (m.save().expedition == t.rec) {
            m.save().expedition = null;
            plan = null;
        }
    }

    /** Tests: every dungeon still going is back now, all in this tick (chunks loaded on the way). */
    private void flushTeardowns(MinecraftServer server) {
        findTeardowns(server);
        for (int guard = 0; guard < 64 && !teardowns.isEmpty(); guard++) {
            Teardown t = teardowns.values().iterator().next();
            t.sync = true;
            tickTeardown(server, t, server.overworld().getGameTime(), Long.MAX_VALUE);
        }
    }

    // ------------------------------------------------------------------ the work of every tick

    /**
     * The heavy work, at most {@link SiegeConfig#dungeonWorkMillis} a tick (update 30.10.): first a
     * dungeon going, then the one being built, then the scouts' search.
     */
    private void work(MinecraftServer server, long now) {
        long deadline = System.nanoTime() + m.config().dungeonWorkMillis * 1_000_000L;
        findTeardowns(server);
        if (!teardowns.isEmpty()) {
            tickTeardown(server, teardowns.values().iterator().next(), now, deadline);
        }
        SiegeSave.ExpeditionRecord rec = record();
        if (rec != null && rec.built && rec.complete && !rec.won && rec.layout >= LAYOUT && checkedComplete != rec.id
                && fighters.isEmpty()) {
            // Survival safety (31.10., D4): a dungeon complete before a restart whose last chunks never reached
            // the disk (a crash) is set again where they are missing (only one built since this update).
            checkedComplete = rec.id;
            if (rec.levelDone != null && (!rec.levelDone || !rec.chunksDone.containsAll(spotsOf(rec).keySet()))) {
                rec.complete = false;
                m.persist();
                NightSiegeMod.LOGGER.warn("[Night Siege] Dungeon {}: nach einem Absturz nicht ganz auf der Platte, wird fertig gebaut", rec.kind);
            }
        }
        if (job == null && rec != null && rec.built && !rec.complete && !rec.won && rec.layout >= LAYOUT) {
            job = new Build(rec.id, rec.dimension);   // half built (a restart, or the playtest gave it back): on with it
        }
        if (job != null && System.nanoTime() < deadline) {
            tickBuild(server, now, deadline);
        }
        if (scouting != null && System.nanoTime() < deadline) {
            tickScouting(server, deadline);
        }
    }

    // ------------------------------------------------------------------ fighters

    private boolean inside(ExpeditionLayout.Plan p, Entity e, double margin) {
        Vec3 at = e.position();
        for (AABB box : p.inside) {
            if (box.inflate(margin).contains(at)) {
                return true;
            }
        }
        return false;
    }

    private void checkEntries(ServerLevel level, SiegeSave.ExpeditionRecord rec, long now) {
        ExpeditionLayout.Plan p = plan(rec);
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            if (fighters.contains(id) || player.isSpectator() || player.getAbilities().instabuild || !player.isAlive()
                    || Drones.isDrone(player)) {
                continue;
            }
            // Only who was in the ready round goes in (the tests and the playtest: anyone signed up).
            boolean may = !out.contains(id) && m.isRegistered(player) && (openEntry || allowed.contains(id));
            if (!may) {
                // Survival safety (Simon 31.10., D1): who ran down without a place in the round (or is
                // not signed up, or fell) would be shut in when the gate closes: back before the gatehouse.
                if (inside(p, player, 0.0) && putOut(level.getServer(), player, false)) {
                    player.sendSystemMessage(Component.translatable("nightsiege.expedition.not_yours").withStyle(ChatFormatting.GRAY));
                }
                continue;
            }
            if (inside(p, player, 0.0)) {
                join(level, rec, player, now);
            }
        }
    }

    // ------------------------------------------------------------------ the ready round (the seal stone)

    /** A ready round at the seal stone: who is in it, who is ready. */
    private static final class Round {
        UUID starter;
        final Set<UUID> members = new LinkedHashSet<>();
        final Set<UUID> ready = new HashSet<>();
        long startedAt;
    }

    private Round round;
    /** Those of the last round: only they may go down while the gate is open. */
    private final Set<UUID> allowed = new HashSet<>();
    /** After the round: the gate stays open until this time or until all are down. */
    private long gateOpenUntil = -1L;
    /** The gate goes up row by row from here (2 s). */
    private long gateRiseAt = -1L;
    /** Tests and the playtest: anyone signed up who goes down joins (no ready round, the gate open). */
    private boolean openEntry;
    /** Where each fighter last stood inside (who gets out is put back there). */
    private final Map<UUID, Vec3> lastInside = new HashMap<>();
    static final double ROUND_RADIUS = 20.0;
    /** After this, the starter may go without those not ready (Simon 29.10., 01:00). */
    static final int FORCE_SECONDS = 30;

    public void openEntry(boolean value) {
        openEntry = value;
    }

    /** The round a player is in (for the class window), or null. */
    public boolean inRound(ServerPlayer player) {
        return round != null && player != null && round.members.contains(player.getUUID());
    }

    public boolean roundStarter(ServerPlayer player) {
        return round != null && player != null && player.getUUID().equals(round.starter);
    }

    /** Seconds until the starter may go without the others (0 when he may now). */
    public int roundForceIn(long now) {
        return round == null ? 0 : (int) Math.max(0, FORCE_SECONDS - (now - round.startedAt) / 20);
    }

    public List<UUID> roundMembers() {
        return round == null ? List.of() : new ArrayList<>(round.members);
    }

    public boolean roundReady(UUID id) {
        return round != null && round.ready.contains(id);
    }

    /** A right click on the seal stone: starts the ready round, or ends it again (the starter). */
    public boolean onSeal(ServerPlayer player, BlockPos pos) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null || !rec.built || !rec.complete || rec.won || !(player.level() instanceof ServerLevel level)
                || !SiegeManager.dimensionName(level).equals(rec.dimension) || !pos.equals(plan(rec).seal)) {
            return false;   // (not complete: still being built, nobody goes in yet)
        }
        long now = level.getGameTime();
        if (!m.isRegistered(player)) {
            player.sendSystemMessage(Component.translatable("nightsiege.expedition.round_register").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (!fighters.isEmpty()) {
            player.sendSystemMessage(Component.translatable("nightsiege.expedition.round_running").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (m.isActive() || m.testPending() || m.nightComing(level.getServer())) {
            player.sendSystemMessage(Component.translatable("nightsiege.expedition.night").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (out.contains(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("nightsiege.expedition.round_fallen").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (round != null) {
            if (player.getUUID().equals(round.starter)) {
                endRound(level, "nightsiege.expedition.round_cancelled");
            } else if (round.members.contains(player.getUUID())) {
                m.campClassPick(player, 0);
            } else {
                player.sendSystemMessage(Component.translatable("nightsiege.expedition.round_other").withStyle(ChatFormatting.GRAY));
            }
            return true;
        }
        if (!m.save().skillsOf(player.getUUID().toString()).hasClass()) {
            player.sendSystemMessage(Component.translatable("nightsiege.expedition.round_noclass").withStyle(ChatFormatting.GRAY));
            return true;
        }
        Round r = new Round();
        r.starter = player.getUUID();
        r.startedAt = now;
        for (ServerPlayer other : level.players()) {
            if (m.isRegistered(other) && other.isAlive() && !other.isSpectator() && !Drones.isDrone(other)
                    && m.save().skillsOf(other.getUUID().toString()).hasClass()
                    && !out.contains(other.getUUID())
                    && other.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= ROUND_RADIUS * ROUND_RADIUS) {
                r.members.add(other.getUUID());
            }
        }
        r.members.add(player.getUUID());
        round = r;
        ExpeditionKind kind = kind(rec);
        Component text = Component.translatable("nightsiege.expedition.round_open", player.getDisplayName(),
                Component.translatable(kind.translationKey())).withStyle(ChatFormatting.GOLD);
        for (UUID id : r.members) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(text);
                m.campClassPick(p, 0);
            }
        }
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.7f);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 60, 1.5, 0.5, 1.5, 0.5);
        NightSiegeMod.LOGGER.info("[Night Siege] Bereit-Runde am Dungeon {} von {} mit {} Spielern", rec.kind,
                player.getScoreboardName(), r.members.size());
        return true;
    }

    /** A class chosen in the window: ready in the round. */
    public void onRoundReady(ServerPlayer player) {
        if (round == null || player == null || !round.members.contains(player.getUUID())) {
            return;
        }
        round.ready.add(player.getUUID());
        if (player.level() instanceof ServerLevel level) {
            BlockPos seal = record() == null ? player.blockPosition() : plan(record()).seal;
            level.playSound(null, seal.getX() + 0.5, seal.getY() + 0.5, seal.getZ() + 0.5, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2f,
                    1.0f + 0.1f * round.ready.size());
            Component text = Component.translatable("nightsiege.expedition.round_ready", player.getDisplayName(),
                    round.ready.size(), round.members.size()).withStyle(ChatFormatting.YELLOW);
            for (UUID id : round.members) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) {
                    p.connection.send(new ClientboundSetActionBarTextPacket(text));
                }
            }
        }
    }

    /** The starter's button: go without those not ready (after 30 s). */
    public void forceRound(ServerPlayer player) {
        if (round == null || player == null || !player.getUUID().equals(round.starter) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (roundForceIn(level.getGameTime()) > 0 || round.ready.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(round.members)) {
            if (!round.ready.contains(id)) {
                round.members.remove(id);
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) {
                    p.sendSystemMessage(Component.translatable("nightsiege.expedition.round_dropped").withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    private void endRound(ServerLevel level, String messageKey) {
        if (round == null) {
            return;
        }
        for (UUID id : round.members) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null && messageKey != null) {
                p.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GRAY));
            }
        }
        round = null;
    }

    /** The round: who walks off is out; when all are ready, the gate goes up. */
    private void tickRound(ServerLevel level, SiegeSave.ExpeditionRecord rec, long now) {
        ExpeditionLayout.Plan p = plan(rec);
        if (round != null) {
            BlockPos seal = p.seal;
            for (UUID id : new ArrayList<>(round.members)) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                boolean gone = player == null || player.level() != level || !player.isAlive()
                        || player.distanceToSqr(seal.getX() + 0.5, seal.getY(), seal.getZ() + 0.5) > ROUND_RADIUS * ROUND_RADIUS;
                if (gone) {
                    round.members.remove(id);
                    round.ready.remove(id);
                    if (player != null) {
                        player.sendSystemMessage(Component.translatable("nightsiege.expedition.round_left").withStyle(ChatFormatting.GRAY));
                    }
                    if (id.equals(round.starter)) {
                        endRound(level, "nightsiege.expedition.round_cancelled");
                        return;
                    }
                }
            }
            if (round.members.isEmpty()) {
                round = null;
            } else if (round.ready.containsAll(round.members)) {
                startRound(level, rec, now);
            }
        }
        if (gateRiseAt > 0) {
            int rows = (int) Math.min(4, (now - gateRiseAt) / 10 + 1);
            for (BlockPos pos : p.stairGate) {
                if (pos.getY() - p.seal.getY() < rows && level.getBlockState(pos).is(Blocks.IRON_BARS)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, p.stairGate.get(0), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.5f, 0.6f);
            if (rows >= 4) {
                gateRiseAt = -1L;
            }
        }
        if (gateOpenUntil > 0) {
            boolean allDown = true;
            for (UUID id : allowed) {
                if (!fighters.contains(id) && level.getServer().getPlayerList().getPlayer(id) != null) {
                    allDown = false;
                }
            }
            if (now >= gateOpenUntil || allDown && !fighters.isEmpty()) {
                closeStairGate(level, p);
                for (UUID id : allowed) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                    if (player != null && !fighters.contains(id)) {
                        player.sendSystemMessage(Component.translatable("nightsiege.expedition.round_late").withStyle(ChatFormatting.GRAY));
                    }
                }
                allowed.clear();
            }
        }
        if (openEntry && level.getBlockState(p.stairGate.get(0)).is(Blocks.IRON_BARS)) {
            setGate(level, p.stairGate, false);
        }
    }

    /** All ready: horn, the title, the gate goes up (2 s); they have 30 s to go down. */
    private void startRound(ServerLevel level, SiegeSave.ExpeditionRecord rec, long now) {
        ExpeditionLayout.Plan p = plan(rec);
        ExpeditionKind kind = kind(rec);
        allowed.clear();
        allowed.addAll(round.members);
        round = null;
        gateOpenUntil = now + 600;
        gateRiseAt = now;
        level.playSound(null, p.seal, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6f, 0.9f);
        for (UUID id : allowed) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(kind.translationKey())
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("nightsiege.expedition.round_go",
                        rec.rooms, WardenNight.name(kind)).withStyle(ChatFormatting.GOLD)));
            }
        }
    }

    private void closeStairGate(ServerLevel level, ExpeditionLayout.Plan p) {
        gateOpenUntil = -1L;
        if (openEntry) {
            return;
        }
        setGate(level, p.stairGate, true);
        level.playSound(null, p.stairGate.get(0), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
    }

    /** A signed-up player goes down the stairs: horn, class cards, kit, skills, grace. */
    void join(ServerLevel level, SiegeSave.ExpeditionRecord rec, ServerPlayer player, long now) {
        boolean first = fighters.isEmpty();
        UUID id = player.getUUID();
        if (joined.isEmpty()) {
            rec.attempts++;   // a new attempt
            if (rec.cleared == 0) {
                frontOpen = false;
                frontRiseAt = -1L;
                setGate(level, plan(rec).frontGate, true);   // it goes up when everyone stands before it
            }
        }
        fighters.add(id);
        joined.add(id);
        m.stats().dungeonJoin(player, rec.kind, m.currentLevel(), rec.rooms, rec.attempts, now);
        int seconds = m.config().expeditionGraceSeconds;
        grace.put(id, now + seconds * 20L);
        ExpeditionKind kind = kind(rec);
        if (first) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(rec.x, rec.y, rec.z) < 160.0 * 160.0) {
                    p.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.HOSTILE,
                            rec.x, rec.y, rec.z, 64f, 0.8f, random.nextLong()));
                }
            }
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(kind.translationKey())
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("nightsiege.expedition.subtitle", seconds)
                .withStyle(ChatFormatting.GOLD)));
        player.sendSystemMessage(Component.translatable("nightsiege.expedition.enter", seconds).withStyle(ChatFormatting.GOLD));
        KitService kits = m.kits();
        if (m.config().nightKits && !kits.hasStash(player)) {
            kits.equip(player, m.kitLevel());
            if (kits.hasStash(player)) {
                List<String> list = m.save().expeditionKits;
                if (list == null) {
                    m.save().expeditionKits = list = new ArrayList<>();
                }
                if (!list.contains(id.toString())) {
                    list.add(id.toString());
                }
                m.persist();
            }
        }
        if (!allowed.contains(id)) {
            // In the ready round the class is chosen already: no second window on the stairs, the
            // playtest's open entry too (Simon 29.10.: "beim Treppe runterlaufen kommt nochmal das Klassenfenster").
            m.campClassPick(player, seconds);
        }
        SkillService.apply(player, m.save().skillsOf(id.toString()), m.skillTree());
        showBar(rec, player);
        DungeonFx.look(player, kind, phase2);
        NightSiegeMod.LOGGER.info("[Night Siege] {} betritt den Dungeon {}", player.getScoreboardName(), rec.kind);
    }

    /** A fighter is out of the fight: own things back, skills off. */
    void leave(ServerPlayer player, String messageKey) {
        UUID id = player.getUUID();
        fighters.remove(id);
        grace.remove(id);
        if (record() != null) {
            DungeonFx.leave(player, kind(record()));
        }
        if (bar != null) {
            bar.removePlayer(player);
        }
        giveBack(player);
        if (messageKey != null) {
            player.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GRAY));
        }
        if (player.level() instanceof ServerLevel level) {
            for (UUID mobId : allMobs()) {
                if (level.getEntity(mobId) instanceof Mob mob && mob.getTarget() == player) {
                    mob.setTarget(null);
                }
            }
        }
    }

    private void giveBack(ServerPlayer player) {
        String id = player.getUUID().toString();
        List<String> list = m.save().expeditionKits;
        if (list != null && list.remove(id) && m.kits().hasStash(player)) {
            m.kits().restore(player);   // persists
        }
        SkillService.clear(player);
    }

    /** A logged-in player: a dungeon kit from before goes back. */
    void onJoin(ServerPlayer player) {
        List<String> list = m.save().expeditionKits;
        if (list != null && list.contains(player.getUUID().toString()) && !fighters.contains(player.getUUID())) {
            giveBack(player);
        }
        // Survival safety (31.10., D1/D2): where he stands is looked at once his chunk is loaded
        // (the dungeon's sealed part, ground of a dungeon or camp that came back meanwhile).
        SafeGround.expect(player.getUUID());
    }

    /**
     * Everyone out of the fight; the monsters of the open room and the arena go. Survival safety
     * (Simon 31.10., D1): an end without a win (the horn, the night, the server stopping, the dungeon
     * taken away) also puts every fighter out of the sealed part, before the gatehouse (before, he got
     * his things back down in the hall, the stair gate shut behind him).
     */
    void endAll(MinecraftServer server, String messageKey) {
        endAll(server, messageKey, true);
    }

    /** As above; {@code putOut} false only for the win (the fighters walk out in the 30 s before it collapses). */
    private void endAll(MinecraftServer server, String messageKey, boolean putOut) {
        for (UUID id : new ArrayList<>(fighters)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                leave(p, messageKey);
                if (putOut) {
                    putOut(server, p, false);
                }
            }
        }
        fighters.clear();
        resetAttempt(server, false);
    }

    /**
     * Survival safety (31.10., D1): a player in the dungeon's sealed part (the stair, the halls, the
     * arena) who does not fight (any more) is put before the gatehouse; {@code seat}: on a waiting bench
     * instead (a fighter counted as fallen while the others fight on). True when he was moved.
     */
    private boolean putOut(MinecraftServer server, ServerPlayer player, boolean seat) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null || !rec.built || rec.layout < LAYOUT) {
            return false;
        }
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level == null || player.level() != level || player.isSpectator()) {
            return false;
        }
        ExpeditionLayout.Plan p = plan(rec);
        if (!sealed(p, rec, player)) {
            return false;
        }
        BlockPos to = seat ? p.waiting.get(Math.floorMod(out.size() - 1, p.waiting.size())) : p.door;
        player.teleportTo(level, to.getX() + 0.5, to.getY() + (seat ? 0.5 : 0.0), to.getZ() + 0.5, java.util.Set.of(),
                player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
        NightSiegeMod.LOGGER.info("[Night Siege] {} aus dem verschlossenen Teil des Dungeons {} gebracht ({})",
                player.getScoreboardName(), rec.kind, seat ? "Wartebank" : "vor das Tor");
        return true;
    }

    /** In the sealed part: the stair, the ways, halls and arena, or below the gatehouse floor inside the dungeon's bounds. */
    private boolean sealed(ExpeditionLayout.Plan p, SiegeSave.ExpeditionRecord rec, Entity e) {
        return p.bounds.contains(e.position()) && (inside(p, e, 1.0) || e.getY() < rec.y - 1);
    }

    /**
     * Survival safety (31.10., D1): a player logged in (his chunk loaded, see SafeGround): one who stands
     * in the sealed part of the dungeon without fighting goes out (on a bench while the fight he left
     * goes on, else before the gatehouse). True when he was moved.
     */
    boolean arrive(ServerLevel level, ServerPlayer player) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null || !rec.built || rec.restoring || fighters.contains(player.getUUID())
                || !SiegeManager.dimensionName(level).equals(rec.dimension)) {
            return false;
        }
        boolean seat = !fighters.isEmpty() && out.contains(player.getUUID());
        if (!putOut(level.getServer(), player, seat)) {
            return false;
        }
        if (seat && bar != null) {
            bar.addPlayer(player);
        }
        player.sendSystemMessage(Component.translatable("nightsiege.expedition.put_out").withStyle(ChatFormatting.GRAY));
        return true;
    }

    /** The fallen fighter: whole again, own things back, put down before the gate. */
    private void fallOut(MinecraftServer server, ServerPlayer player) {
        SiegeSave.ExpeditionRecord rec = record();
        out.add(player.getUUID());
        m.stats().dungeonFell();
        leave(player, null);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        if (rec != null && rec.built) {
            ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
            ExpeditionLayout.Plan p = plan(rec);
            // The fallen wait in the gatehouse (content update): on the benches, with the bar of the fight.
            BlockPos seat = p.waiting.get(Math.floorMod(out.size() - 1, p.waiting.size()));
            if (level != null) {
                player.teleportTo(level, seat.getX() + 0.5, seat.getY() + 0.5, seat.getZ() + 0.5, java.util.Set.of(),
                        player.getYRot(), player.getXRot(), true);
            }
            if (bar != null) {
                bar.addPlayer(player);
            }
        }
        player.sendSystemMessage(Component.translatable("nightsiege.expedition.fallen").withStyle(ChatFormatting.RED));
        if (fighters.isEmpty()) {
            for (UUID id : joined) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) {
                    p.sendSystemMessage(Component.translatable("nightsiege.expedition.lost").withStyle(ChatFormatting.DARK_RED));
                }
            }
            resetAttempt(server, true);
        }
    }

    // ------------------------------------------------------------------ the fight

    private void tickFight(MinecraftServer server, ServerLevel level, SiegeSave.ExpeditionRecord rec, long now) {
        ExpeditionLayout.Plan p = plan(rec);
        for (UUID id : new ArrayList<>(fighters)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                fighters.remove(id);   // logged out: fallen; the own things come back when they log in again
                grace.remove(id);
                out.add(id);
                continue;
            }
            if (player.level() != level) {
                leave(player, "nightsiege.expedition.left");
            } else if (!inside(p, player, 1.5)) {
                // Locked in (content update): who gets out (pearl, hook, climbing arrow) is put back.
                Vec3 back = lastInside.get(id);
                if (back != null && !openEntry) {
                    player.teleportTo(level, back.x, back.y, back.z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                    player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("nightsiege.expedition.no_escape")
                            .withStyle(ChatFormatting.RED)));
                } else {
                    leave(player, "nightsiege.expedition.left");
                }
            } else if (player.onGround()) {
                lastInside.put(id, player.position());
            }
        }
        if (fighters.isEmpty()) {
            m.stats().dungeonEnd("verlassen", null);
            resetAttempt(server, false);
            return;
        }
        ExpeditionKind kind = kind(rec);
        int lvl = m.currentLevel();
        if (rec.cleared == 0 && !frontOpen) {
            tickFrontGate(server, level, p, now);
        }
        if (rec.cleared < rec.rooms) {
            int room = rec.cleared;
            Set<UUID> mobs = roomMobs.get(room);
            if (mobs == null && room == 0 && !frontOpen) {
                // The first hall's guards come when its gate goes up (Simon 29.10.: only once all are down).
            } else if (mobs == null) {
                AABB zone = p.roomZones.get(room);
                for (UUID id : fighters) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null && zone.contains(player.position())) {
                        spawnRoom(level, rec, p, kind, room, lvl);
                        break;
                    }
                }
            } else {
                mobs.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity l) || !l.isAlive());
                wakeRoom(level, p, room, mobs, now);
                if (mobs.isEmpty()) {
                    openRoom(level, rec, p, room);
                }
            }
        } else if (!arenaClosed) {
            boolean all = true;
            for (UUID id : fighters) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null || !p.arena.deflate(0.5, 0, 0.5).contains(player.position())) {
                    all = false;
                    break;
                }
            }
            if (all) {
                setGate(level, p.arenaGate, true);
                arenaClosed = true;
                wave = 0;
                nextWaveAt = now + 60;
                level.playSound(null, p.arenaGate.get(0), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.6f);
                List<ServerPlayer> inArena = new ArrayList<>();
                for (UUID id : fighters) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) {
                        inArena.add(player);
                    }
                }
                DungeonFx.arenaClosed(level, p.arenaGate, inArena, kind);
                ringAt = now;
                for (UUID id : fighters) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) {
                        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
                        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("nightsiege.expedition.arena")
                                .withStyle(ChatFormatting.DARK_RED)));
                        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
                                "nightsiege.expedition.arena_sub", rec.rooms).withStyle(ChatFormatting.GOLD)));
                    }
                }
            }
        } else if (champion == null) {
            arenaMobs.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity l) || !l.isAlive());
            if (arenaMobs.isEmpty()) {
                if (nextWaveAt < 0) {
                    nextWaveAt = now + 80;   // a breath between the waves
                } else if (now >= nextWaveAt) {
                    nextWaveAt = -1L;
                    if (wave < rec.rooms) {
                        spawnWave(level, rec, p, kind, lvl);
                    } else {
                        spawnChampion(level, rec, p, kind, lvl);
                    }
                }
            }
        } else {
            Entity c = level.getEntity(champion);
            if (!(c instanceof LivingEntity l) || !l.isAlive()) {
                if (c != null) {
                    BossService.dropFoeLook(server, c.getId());
                }
                win(server, level, rec);
                return;
            }
            if (championShownAt > 0 && c instanceof Mob cm) {
                if (now - championShownAt < 60) {
                    DungeonFx.pit(level, p.arenaCenter, kind, 1);
                } else {
                    cm.setInvulnerable(false);
                    cm.setNoAi(false);
                    championShownAt = -1L;
                }
            }
            if (!phase2 && c instanceof LivingEntity lc && lc.getHealth() < lc.getMaxHealth() * 0.5f) {
                phase2 = true;
                List<ServerPlayer> all = new ArrayList<>();
                for (UUID id : fighters) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) {
                        all.add(player);
                    }
                }
                DungeonFx.phase2(level, all.toArray(new ServerPlayer[0]), kind, c.position());
            }
            if (now % 20 == 0 && c instanceof Mob cm && !("champion_" + kind.id()).equals(BossService.foeLookOf(c.getId()))) {
                BossService.foeLook(cm, "champion_" + kind.id());   // again after a restart (new network id)
            }
        }
        updateBar(level, rec);
    }

    private void spawnRoom(ServerLevel level, SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p, ExpeditionKind kind,
                           int room, int lvl) {
        Set<UUID> mobs = new HashSet<>();
        boolean test = m.inPlaytest();   // the playtest: as many for each player as for one alone (Simon 28.10.)
        int count = more(kind, test ? ExpeditionKind.guards(lvl, fighters.size(), m.config().playtestPlayerScaling, 24)
                : ExpeditionKind.guards(lvl, fighters.size()));
        List<BlockPos> spots = p.roomSpots.get(room);
        for (int i = 0; i < count; i++) {
            BlockPos spot = spots.get(i % spots.size());
            Mob mob = spawn(level, rec, kind.mobs().get(random.nextInt(kind.mobs().size())), spot);
            if (mob != null) {
                toughen(mob, kind);
                mobs.add(mob.getUUID());
                mob.setNoAi(true);   // statues until someone reaches the middle or strikes one (content update)
            }
        }
        sendLooks(level);
        roomMobs.put(room, mobs);
        roomAwake = false;
        wakeAt.clear();
        // The hall's title: its theme, which hall of how many.
        String module = room < p.roomModules.size() ? p.roomModules.get(room) : "";
        for (UUID id : fighters) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("nightsiege.expedition.module." + module)
                        .withStyle(ChatFormatting.GOLD)));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("nightsiege.expedition.hall_of",
                        room + 1, rec.rooms).withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    /** The guards wake group by group, a second apart, when one comes close or strikes one. */
    private void wakeRoom(ServerLevel level, ExpeditionLayout.Plan p, int room, Set<UUID> mobs, long now) {
        if (!roomAwake) {
            BlockPos center = p.roomCenters.get(room);
            boolean near = false;
            for (UUID id : fighters) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null && player.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5) < 10.0 * 10.0) {
                    near = true;
                }
            }
            for (UUID id : mobs) {
                if (level.getEntity(id) instanceof Mob mob && mob.getHealth() < mob.getMaxHealth()) {
                    near = true;
                }
            }
            if (!near) {
                return;
            }
            roomAwake = true;
            int i = 0;
            for (UUID id : mobs) {
                wakeAt.put(id, now + 20L * (i++ % 4));
            }
            level.playSound(null, center, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2f, 0.7f);
        }
        for (Map.Entry<UUID, Long> e : new ArrayList<>(wakeAt.entrySet())) {
            if (now >= e.getValue() && level.getEntity(e.getKey()) instanceof Mob mob) {
                mob.setNoAi(false);
                level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1, mob.getZ(), 8, 0.3, 0.6, 0.3, 0.02);
                wakeAt.remove(e.getKey());
            }
        }
    }

    /**
     * The gate before the first hall (Simon 29.10.): it goes up, row by row, when every fighter
     * stands before it and nobody of the ready round is still on the way down. Until then the
     * fighters see how many are there.
     */
    private void tickFrontGate(MinecraftServer server, ServerLevel level, ExpeditionLayout.Plan p, long now) {
        if (p.frontGate.isEmpty()) {
            frontOpen = true;
            return;
        }
        if (frontRiseAt > 0) {
            int bottom = Integer.MAX_VALUE;
            for (BlockPos pos : p.frontGate) {
                bottom = Math.min(bottom, pos.getY());
            }
            int rows = (int) Math.min(5, (now - frontRiseAt) / 10 + 1);
            for (BlockPos pos : p.frontGate) {
                if (pos.getY() - bottom < rows && level.getBlockState(pos).is(Blocks.IRON_BARS)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, p.frontGate.get(0), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.5f, 0.6f);
            if (rows >= 5) {
                frontOpen = true;
                frontRiseAt = -1L;
            }
            return;
        }
        AABB zone = p.roomZones.get(0).inflate(1.0);
        int total = 0;
        int before = 0;
        for (UUID id : fighters) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            total++;
            if (zone.contains(player.position())) {
                before++;
            }
        }
        for (UUID id : allowed) {
            if (!fighters.contains(id) && !out.contains(id) && server.getPlayerList().getPlayer(id) != null) {
                total++;   // still on the way down
            }
        }
        if (total > 0 && before == total) {
            frontRiseAt = now;
            level.playSound(null, p.frontGate.get(0), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3f, 1.1f);
            Component text = Component.translatable("nightsiege.expedition.front_open").withStyle(ChatFormatting.GOLD);
            for (UUID id : fighters) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    player.connection.send(new ClientboundSetActionBarTextPacket(text));
                }
            }
        } else if (now % 40 == 0) {
            Component text = Component.translatable("nightsiege.expedition.front_wait", before, total).withStyle(ChatFormatting.YELLOW);
            for (UUID id : fighters) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    player.connection.send(new ClientboundSetActionBarTextPacket(text));
                }
            }
        }
    }

    private void openRoom(ServerLevel level, SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p, int room) {
        setGate(level, p.roomGates.get(room), false);
        rec.cleared = room + 1;
        roomMobs.remove(room);
        m.stats().dungeonProgress(rec.cleared, 0, false);
        m.persist();
        level.playSound(null, p.roomGates.get(room).get(0), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2f, 0.7f);
        BlockPos gate = p.roomGates.get(room).get(0);
        level.playSound(null, gate.getX() + 0.5, gate.getY() + 1, gate.getZ() + 0.5, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 2f, 0.8f);
        Component text = Component.translatable(rec.cleared >= rec.rooms ? "nightsiege.expedition.room_last"
                : "nightsiege.expedition.room_open", rec.cleared, rec.rooms).withStyle(ChatFormatting.GREEN);
        for (UUID id : joined) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(text));
            }
        }
    }

    private void spawnWave(ServerLevel level, SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p, ExpeditionKind kind,
                           int lvl) {
        wave++;
        if (wave == 1) {
            setGate(level, p.waveGates, false);   // the four monster gates open
            level.playSound(null, p.arenaCenter, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 3f, 0.5f);
        }
        m.stats().dungeonProgress(rec.cleared, wave, false);
        int count = more(kind, m.inPlaytest() ? ExpeditionKind.wave(lvl, fighters.size(), m.config().playtestPlayerScaling, 32)
                : ExpeditionKind.wave(lvl, fighters.size()));
        for (int i = 0; i < count; i++) {
            BlockPos spot = p.waveSpots.get(i % p.waveSpots.size());
            Mob mob = spawn(level, rec, kind.mobs().get(random.nextInt(kind.mobs().size())), spot);
            if (mob != null) {
                toughen(mob, kind);
                arenaMobs.add(mob.getUUID());
            }
        }
        sendLooks(level);
        level.playSound(null, p.arenaCenter, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 1.2f);
        Component text = Component.translatable("nightsiege.expedition.wave", wave, rec.rooms).withStyle(ChatFormatting.RED);
        for (UUID id : fighters) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(text));
            }
        }
    }

    private void spawnChampion(ServerLevel level, SiegeSave.ExpeditionRecord rec, ExpeditionLayout.Plan p, ExpeditionKind kind,
                               int lvl) {
        Mob mob = spawn(level, rec, kind.champion(), p.arenaCenter);
        if (mob == null) {
            win(level.getServer(), level, rec);   // nothing to fight (a missing monster): the way is free
            return;
        }
        double factor = ExpeditionKind.championLife(lvl) * kind.championLifeShare() * (1.0 + 0.5 * Math.max(0, fighters.size() - 1))
                * m.config().dungeonChampionLife;   // Simon 30.10.: "sehr leicht zu töten", 2.5 times the life
        double health = mob.getMaxHealth() * factor;
        // In the look of his dungeon and the colour of his fragment (Simon, 29 October 2026: "Dungeon-Wächter").
        WardenNight.dress(mob, kind, health);
        // Simon 30.10.: "die wächter kommen gefühlt nicht an spieler ran": a little faster, and his leap (ChampionBrains).
        net.minecraft.world.entity.ai.attributes.AttributeInstance speed = mob.getAttribute(
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * m.config().championSpeed);
        }
        mob.addTag(CHAMPION_TAG);
        champion = mob.getUUID();
        brains = new ChampionBrains(mob, kind, level.getGameTime() + 60);
        // He rises from his pit: 3 s of title card, untouchable and still meanwhile.
        mob.setInvulnerable(true);
        mob.setNoAi(true);
        championShownAt = level.getGameTime();
        DungeonFx.pit(level, p.arenaCenter, kind, 0);
        for (UUID id : fighters) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                // The lights go out for half a second.
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 25, 0, false, false));
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 10));
                player.connection.send(new ClientboundSetTitleTextPacket(WardenNight.name(kind)));
                player.connection.send(new ClientboundSetSubtitleTextPacket(WardenNight.epithet(kind).copy().withStyle(ChatFormatting.GRAY)));
            }
        }
        m.stats().dungeonProgress(rec.cleared, wave, true);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.arenaCenter.getX() + 0.5, p.arenaCenter.getY() + 1,
                p.arenaCenter.getZ() + 0.5, 1, 0, 0, 0, 0);
        level.playSound(null, p.arenaCenter, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 1.2f);
        Component text = Component.translatable("nightsiege.expedition.champion",
                Component.translatable(kind.championKey())).withStyle(ChatFormatting.GOLD);
        for (UUID id : fighters) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(text));
            }
        }
    }

    /** What the champion's skills may use of the fight. */
    private ChampionBrains.Host championHost(ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        return new ChampionBrains.Host() {
            @Override
            public List<ServerPlayer> fighters() {
                List<ServerPlayer> out = new ArrayList<>();
                for (UUID id : fighters) {
                    ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                    if (p != null && p.isAlive() && !p.isSpectator()) {
                        out.add(p);
                    }
                }
                return out;
            }

            @Override
            public Mob spawnAdd(String entityId, BlockPos at) {
                Mob mob = spawn(level, rec, entityId, at);
                if (mob != null) {
                    arenaMobs.add(mob.getUUID());
                }
                sendLooks(level);
                return mob;
            }

            @Override
            public List<Mob> arenaMobs() {
                List<Mob> out = new ArrayList<>();
                for (UUID id : arenaMobs) {
                    if (level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                        out.add(mob);
                    }
                }
                return out;
            }

            @Override
            public int floorY() {
                return plan(rec).arenaCenter.getY() + 3;
            }
        };
    }

    /** A monster of the dungeon: no siege monster (no night counting, its own drops), only for the fighters. */
    private Mob spawn(ServerLevel level, SiegeSave.ExpeditionRecord rec, String entityId, BlockPos spot) {
        SiegeConfig cfg = m.config();
        int lvl = m.currentLevel();
        Mob mob = MobFactory.spawnAt(level, spot, cfg.withoutEffects(entityId), lvl, cfg, random, SiegeEvent.NONE, false);
        if (mob == null) {
            mob = MobFactory.spawnAt(level, spot, "minecraft:zombie", lvl, cfg, random, SiegeEvent.NONE, false);
        }
        if (mob == null) {
            return null;
        }
        mark(mob, rec);
        for (Entity passenger : mob.getPassengers()) {
            if (passenger instanceof Mob rider) {
                mark(rider, rec);
            }
        }
        // The dungeon's guards (Simon, 29 October 2026, 22:06): in their warden's look, not the plain monsters.
        // The new attackers keep their own looks; the champion is dressed in his own right after.
        if (!Foes.isRole(entityId)) {
            String guard = "guard_" + kind(rec).id();
            mob.setBaby(false);   // guards are grown (a baby's texture has no guard look)
            Entity vehicle = mob.getVehicle();
            if (vehicle != null && !isExpeditionMob(vehicle)) {
                mob.stopRiding();   // no chicken jockeys in the halls
                vehicle.discard();
            }
            BossService.foeLookQuiet(mob, guard);
            for (Entity passenger : mob.getPassengers()) {
                if (passenger instanceof Mob rider) {
                    BossService.foeLookQuiet(rider, guard);
                }
            }
            lookPending = true;
        }
        return mob;
    }

    /** The life of the halls' and waves' monsters (not the illager keep; Simon 30.10.: "+50 % Leben"). */
    private static final net.minecraft.resources.Identifier GUARD_LIFE = ModContent.id("dungeon_guard_life");

    /** The dungeons are tougher (Simon 30.10.), all but the illager keep, which was hard enough. */
    private void toughen(Mob mob, ExpeditionKind kind) {
        double f = m.config().dungeonGuardLife;
        if (kind == ExpeditionKind.ILLAGER_KEEP || Math.abs(f - 1.0) < 1e-6) {
            return;
        }
        for (Mob one : riders(mob)) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance hp = one.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(GUARD_LIFE, f - 1.0,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                one.setHealth(one.getMaxHealth());
            }
        }
    }

    private static List<Mob> riders(Mob mob) {
        List<Mob> out = new ArrayList<>();
        out.add(mob);
        for (Entity passenger : mob.getPassengers()) {
            if (passenger instanceof Mob rider) {
                out.add(rider);
            }
        }
        return out;
    }

    /** A quarter more monsters in the halls and waves (not the illager keep; Simon 30.10.). */
    private int more(ExpeditionKind kind, int count) {
        return kind == ExpeditionKind.ILLAGER_KEEP ? count : (int) Math.round(count * m.config().dungeonGuardCount);
    }

    /** Guards were dressed: the clients learn it once for the whole room or wave. */
    private boolean lookPending;

    private void sendLooks(ServerLevel level) {
        if (lookPending) {
            lookPending = false;
            BossService.syncFoeLooks(level);
        }
    }

    private static void mark(Mob mob, SiegeSave.ExpeditionRecord rec) {
        LIVE.add(mob.getUUID());
        mob.removeTag(MobFactory.TAG);   // not a monster of the night: drops as ever, no night counting
        mob.addTag(TAG);
        mob.addTag(TAG + "_" + rec.id);
        mob.setPersistenceRequired();
    }

    /**
     * The stair keeps the dungeon's monsters in (Simon 30.10., 03:26: "sie können direkt angreifen
     * aber den raum nicht die treppe hoch verlassen"): one that sets foot on it is pushed back down,
     * one deeper in is put at the stair's foot.
     */
    private void keepOffStair(ServerLevel level, ExpeditionLayout.Plan p) {
        if (p.stairZone == null || p.stairFoot == null) {
            return;
        }
        Vec3 foot = Vec3.atBottomCenterOf(p.stairFoot);
        for (UUID id : allMobs()) {
            if (!(level.getEntity(id) instanceof Mob mob) || !mob.isAlive() || !p.stairZone.contains(mob.position())) {
                continue;
            }
            mob.getNavigation().stop();
            Vec3 back = foot.subtract(mob.position());
            if (back.lengthSqr() > 5.0 * 5.0) {
                mob.teleportTo(foot.x, foot.y, foot.z);
                stairPushes++;
            } else {
                Vec3 dir = back.normalize();
                mob.setDeltaMovement(dir.x * 0.6, Math.max(0.1, mob.getDeltaMovement().y), dir.z * 0.6);
                mob.hurtMarked = true;
                stairPushes++;
            }
        }
    }

    /** For the tests: how often the champion leapt at a fighter. */
    public int championLeaps() {
        return brains == null ? 0 : brains.leaps();
    }

    /** For the tests: how often a monster was kept off the stair. */
    public int stairPushes() {
        return stairPushes;
    }

    private Set<UUID> allMobs() {
        Set<UUID> all = new HashSet<>(arenaMobs);
        for (Set<UUID> s : roomMobs.values()) {
            all.addAll(s);
        }
        if (champion != null) {
            all.add(champion);
        }
        return all;
    }

    private void setGate(ServerLevel level, List<BlockPos> gate, boolean closed) {
        for (BlockPos pos : gate) {
            if (!loaded(level, pos.getX() >> 4, pos.getZ() >> 4)) {
                // Not loaded (everyone left): set when it loads again. Loading it here mid-tick broke
                // C2ME's entity sections (server log 29 October 2026, 18:09).
                pendingGates.put(pos.immutable(), closed);
                continue;
            }
            pendingGates.remove(pos);
            level.setBlock(pos, closed ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Gate blocks waiting for their chunk (see {@link #setGate}). */
    private final Map<BlockPos, Boolean> pendingGates = new HashMap<>();

    private void tickPendingGates(ServerLevel level) {
        if (pendingGates.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, Boolean> e : new ArrayList<>(pendingGates.entrySet())) {
            BlockPos pos = e.getKey();
            if (loaded(level, pos.getX() >> 4, pos.getZ() >> 4)) {
                pendingGates.remove(pos);
                level.setBlock(pos, e.getValue() ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL);
            }
        }
    }

    /** The attempt is over without a win: its monsters go, the arena opens, the fallen may come again. */
    private void resetAttempt(MinecraftServer server, boolean lost) {
        SiegeSave.ExpeditionRecord rec = record();
        ServerLevel level = rec == null ? null : SiegeManager.levelByName(server, rec.dimension);
        if (level != null) {
            for (UUID id : allMobs()) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    BossService.dropFoeLook(level.getServer(), e.getId());
                    e.discard();
                }
                LIVE.remove(id);
            }
            if (arenaClosed && rec.built && rec.complete && !rec.won) {
                setGate(level, plan(rec).arenaGate, false);
                setGate(level, plan(rec).waveGates, true);
            }
        }
        roomMobs.clear();
        arenaMobs.clear();
        champion = null;
        brains = null;
        phase2 = false;
        championShownAt = -1L;
        wakeAt.clear();
        roomAwake = false;
        lastInside.clear();
        allowed.clear();
        gateOpenUntil = -1L;
        // (Only a complete one: one still being built gets its gates with its blocks, and a gate set
        // before its chunk is written down would stay in the ground when it goes.)
        if (level != null && rec.built && rec.complete && !rec.won && !openEntry) {
            setGate(level, plan(rec).stairGate, true);   // the next attempt starts at the seal stone again
        }
        arenaClosed = false;
        if (level != null && rec.built && rec.complete && !rec.won && rec.cleared == 0) {
            setGate(level, plan(rec).frontGate, true);
        }
        frontOpen = false;
        frontRiseAt = -1L;
        wave = 0;
        nextWaveAt = -1L;
        if (!(lost && noRetry)) {
            out.clear();
        }
        joined.clear();
        grace.clear();
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        m.stats().dungeonEnd(lost ? "verloren" : "abgebrochen", null);   // nothing if the attempt is written already
        if (lost) {
            lostAttempts++;
            NightSiegeMod.LOGGER.info("[Night Siege] Dungeon-Versuch verloren ({} von {} Räumen offen)",
                    rec == null ? 0 : rec.cleared, rec == null ? 0 : rec.rooms);
        }
    }

    /** The champion fell: rewards, the night fragment straight into the inventory, then the dungeon goes. */
    private void win(MinecraftServer server, ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        ExpeditionLayout.Plan p = plan(rec);
        ExpeditionKind kind = kind(rec);
        rec.won = true;
        rec.collapseAt = level.getGameTime() + COLLAPSE_TICKS;
        setGate(level, p.arenaGate, false);
        BlockPos exit = p.arenaGate.get(p.arenaGate.size() / 2);
        DungeonFx.win(level, new Vec3(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5));
        // The group's night fragment goes to the first of them (content update: no chest, no ruin).
        Item fragment = DungeonContent.NIGHT_FRAGMENTS.get(kind.fragment());
        ServerPlayer taker = null;
        for (UUID id : joined) {
            taker = server.getPlayerList().getPlayer(id);
            if (taker != null) {
                break;
            }
        }
        final ServerPlayer fragmentTo = taker;
        // The signal fire goes out: nobody gathers here any more.
        BlockState fire = level.getBlockState(p.fire);
        if (fire.hasProperty(BlockStateProperties.LIT)) {
            level.setBlock(p.fire, fire.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
        }
        Set<UUID> who = new LinkedHashSet<>(joined);
        int lvl = m.currentLevel();
        SiegeConfig cfg = m.config();
        // (Simon 31.10.: survival safety 2) A playtest's dungeon pays nothing real: no night fragment (it
        // would turn the next real night into a special night) and no real crates (the playtest hands out
        // its own). The points go with the playtest's skills, which its end puts back.
        boolean playtest = m.inPlaytest();
        if (!playtest) {
            m.crates().awardEach(server, who, CrateKind.EVENT, lvl);
        }
        for (UUID id : who) {
            if (cfg.expeditionSkillPoints > 0) {
                m.save().skillsOf(id.toString()).award(cfg.expeditionSkillPoints);
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage((playtest
                        ? Component.translatable("nightsiege.expedition.reward_playtest", cfg.expeditionSkillPoints)
                        : Component.translatable("nightsiege.expedition.reward", cfg.expeditionSkillPoints,
                                Component.translatable("item.nightsiege.night_fragment_" + kind.fragment().id())))
                        .withStyle(ChatFormatting.AQUA));
                level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            }
        }
        List<String> names = new ArrayList<>();
        for (UUID id : who) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            names.add(player == null ? "?" : player.getScoreboardName());
        }
        m.broadcastRegistered(server, Component.translatable("nightsiege.expedition.won", String.join(", ", names),
                Component.translatable(kind.translationKey())).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        NightSiegeMod.LOGGER.info("[Night Siege] Dungeon {} bezwungen von {}", rec.kind, names);
        m.stats().dungeonEnd("gewonnen", playtest ? List.of("punkte_je_spieler:" + cfg.expeditionSkillPoints, "playtest")
                : List.of("klassenkiste_je_spieler", "punkte_je_spieler:" + cfg.expeditionSkillPoints,
                "fragment:" + kind.fragment().id()));
        for (UUID id : who) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("nightsiege.expedition.collapse")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("nightsiege.expedition.collapse_sub",
                        COLLAPSE_TICKS / 20).withStyle(ChatFormatting.YELLOW)));
            }
        }
        endAll(server, null, false);   // they walk out in the 30 s before it collapses
        // After the own things are back (the kit's stash would take it away again).
        if (fragmentTo != null && fragment != null && !playtest) {
            ItemStack stack = new ItemStack(fragment);
            if (!fragmentTo.getInventory().add(stack)) {
                fragmentTo.drop(stack, false);
            }
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, fragmentTo.getX(), fragmentTo.getY() + 1, fragmentTo.getZ(),
                    40, 0.6, 0.8, 0.6, 0.3);
        }
        m.persist();
    }

    private void showBar(SiegeSave.ExpeditionRecord rec, ServerPlayer player) {
        if (bar == null) {
            bar = new ServerBossEvent(com.mki.nightsiege.core.NightBarIds.make(com.mki.nightsiege.core.NightBarIds.Kind.DUNGEON), Component.translatable(kind(rec).translationKey()),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        }
        bar.addPlayer(player);
    }

    private void updateBar(ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        if (bar == null) {
            return;
        }
        ExpeditionKind kind = kind(rec);
        if (champion != null && level.getEntity(champion) instanceof LivingEntity c) {
            bar.setName(WardenNight.name(kind));
            bar.setColor(phase2 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
            float life = (float) Math.max(0.0, Math.min(1.0, c.getHealth() / c.getMaxHealth()));
            if (championShownAt > 0) {
                life = Math.min(life, (level.getGameTime() - championShownAt) / 40f);   // the bar fills in 2 s
            }
            bar.setProgress(Math.max(0f, life));
        } else if (arenaClosed) {
            bar.setName(Component.translatable("nightsiege.expedition.bar_wave", Component.translatable(kind.translationKey()),
                    Math.max(1, wave), rec.rooms));
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setProgress(1f);
        } else {
            bar.setName(Component.translatable("nightsiege.expedition.bar_room", Component.translatable(kind.translationKey()),
                    Math.min(rec.cleared + 1, rec.rooms), rec.rooms));
            bar.setColor(BossEvent.BossBarColor.YELLOW);
            bar.setProgress((float) Math.min(1.0, rec.cleared / (double) Math.max(1, rec.rooms)));
        }
    }

    // ------------------------------------------------------------------ damage and death

    /** Damage in a dungeon fight, for the statistics. */
    void afterDamage(LivingEntity victim, DamageSource source, float taken) {
        if (fighters.isEmpty() || taken <= 0) {
            return;
        }
        Entity raw = source.getEntity();
        Entity hitter = Companions.isCompanion(raw) ? Companions.ownerPlayer(raw) : raw;
        if (isExpeditionMob(victim) && hitter instanceof ServerPlayer p && fighters.contains(p.getUUID())) {
            m.stats().dungeonDamage(true, taken);
        } else if (victim instanceof ServerPlayer p && fighters.contains(p.getUUID()) && isExpeditionMob(raw)) {
            m.stats().dungeonDamage(false, taken);
        }
    }

    /** Whether a dungeon monster may pick this target: fighters past their grace and their companions. */
    public boolean mayTarget(Mob mob, LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            if (!fighters.contains(player.getUUID())) {
                return false;
            }
            Long until = grace.get(player.getUUID());
            return until == null || mob.level().getGameTime() >= until;
        }
        UUID owner = Companions.isCompanion(target) ? Companions.ownerOf(target) : null;
        return owner != null && fighters.contains(owner);
    }

    /** Whether a hit lands: the dungeon's monsters spare the fighters in their grace. */
    public boolean allowDamage(LivingEntity victim, DamageSource source) {
        if (victim instanceof ServerPlayer player && isExpeditionMob(source.getEntity())) {
            Long until = grace.get(player.getUUID());
            return fighters.contains(player.getUUID()) && (until == null || victim.level().getGameTime() >= until);
        }
        return true;
    }

    /** Before a death: a fighter does not die, they are out until the fight ends. False when called off. */
    public boolean allowDeath(LivingEntity entity) {
        if (entity instanceof ServerPlayer player && fighters.contains(player.getUUID())) {
            player.setHealth(player.getMaxHealth());
            player.removeAllEffects();
            player.clearFire();
            grace.put(player.getUUID(), Long.MAX_VALUE);   // spared until it is handled
            if (!falling.contains(player.getUUID())) {
                falling.add(player.getUUID());
            }
            return false;
        }
        return true;
    }

    /**
     * A creature that does not belong in a dungeon (Simon 29.10.: an enderman with a grass block,
     * a chicken): anything alive that is none of the mod's own, not named, not tamed, not on a
     * lead and carries no player.
     */
    static boolean intruder(Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.hasCustomName() || mob.isLeashed()) {
            return false;
        }
        for (String tag : mob.entityTags()) {
            if (tag.startsWith("nightsiege")) {
                return false;   // the dungeon's monsters, companions, bosses, the night's own
            }
        }
        if (mob instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwnerReference() != null) {
            return false;
        }
        for (Entity rider : mob.getPassengers()) {
            if (rider instanceof Player) {
                return false;
            }
        }
        return true;
    }

    /** The mod's own creatures (the dungeon's monsters, companions, bosses, the night's own) and whatever carries a player. */
    static boolean ours(Entity entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith("nightsiege")) {
                return true;
            }
        }
        for (Entity rider : entity.getPassengers()) {
            if (rider instanceof Player) {
                return true;
            }
        }
        return false;
    }

    /** Loose things in a dungeon's space before its walls go up: items, orbs, arrows stuck in the ground. */
    private static boolean litter(Entity entity) {
        return entity instanceof net.minecraft.world.entity.item.ItemEntity || entity instanceof net.minecraft.world.entity.ExperienceOrb
                || entity instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow;
    }

    /** The dungeon's spaces (halls, ways, arena) with their walls, cut to one chunk. */
    private static List<AABB> spaceIn(ExpeditionLayout.Plan p, long chunk) {
        double x0 = (double) ((int) (chunk >> 32) << 4);
        double z0 = (double) ((int) chunk << 4);
        List<AABB> out = new ArrayList<>();
        for (AABB box : p.inside) {
            AABB b = box.inflate(2.0);
            double minX = Math.max(b.minX, x0);
            double maxX = Math.min(b.maxX, x0 + 16);
            double minZ = Math.max(b.minZ, z0);
            double maxZ = Math.min(b.maxZ, z0 + 16);
            if (minX < maxX && minZ < maxZ) {
                out.add(new AABB(minX, b.minY, minZ, maxX, b.maxY, maxZ));
            }
        }
        return out;
    }

    /**
     * Before the dungeon is set in a chunk: what lives or lies where its halls and walls go is
     * taken away, quietly (no drops). Cave monsters walled in there died and their loot lay in
     * the halls (Simon 29.10.: "es liegen Items rum").
     */
    private static void clearChunk(ServerLevel level, ExpeditionLayout.Plan p, long chunk) {
        for (AABB box : spaceIn(p, chunk)) {
            // Survival safety (Simon 31.10., D3/D6): only monsters and stuck arrows go; animals, villagers and
            // pets are put on top of the ground, items and orbs too (a player's things from a death in a cave).
            SafeGround.clearCreatures(level, box, null, Expeditions::ours);
            for (Entity e : level.getEntitiesOfClass(Entity.class, box, Expeditions::litter)) {
                if (e instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow) {
                    e.discard();
                } else {
                    BlockPos top = SafeGround.surface(level, e.getBlockX(), e.getBlockZ());
                    if (top == null) {
                        e.discard();
                    } else {
                        e.teleportTo(e.getX(), top.getY() + 0.25, e.getZ());
                    }
                }
            }
        }
    }

    /** Every second while a dungeon stands: creatures that came in from outside go (endermen teleport in). */
    private static void sweep(ServerLevel level, ExpeditionLayout.Plan p) {
        sweep(level, p, null);
    }

    /**
     * As above. Survival safety (Simon 31.10., D3/D6): only monsters go; an animal, a villager or a
     * golem that got in is put out instead, at {@code out} (null: before the gatehouse door).
     */
    private static void sweep(ServerLevel level, ExpeditionLayout.Plan p, Vec3 out) {
        Vec3 to = out != null ? out : new Vec3(p.door.getX() + 0.5, p.door.getY(), p.door.getZ() + 0.5);
        for (AABB box : p.inside) {
            if (!level.hasChunkAt(BlockPos.containing(box.getCenter()))) {
                continue;
            }
            // With the walls: a cave monster walled in when the halls went up goes before it suffocates and drops its loot.
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box.inflate(2.0), Expeditions::intruder)) {
                if (mob instanceof net.minecraft.world.entity.monster.Enemy) {
                    mob.discard();
                } else {
                    SafeGround.moveTo(level, mob, to);
                }
            }
        }
    }

    /** A dungeon monster loaded from the world that belongs to no fight of now goes. */
    static boolean stray(Entity entity) {
        return isExpeditionMob(entity) && !LIVE.contains(entity.getUUID());
    }

    /** True for a block of a dungeon not won yet (players cannot break it). */
    public boolean protects(net.minecraft.world.level.Level level, BlockPos pos) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null || !rec.built || !(level instanceof ServerLevel server)
                || !SiegeManager.dimensionName(server).equals(rec.dimension)) {
            return false;
        }
        if (ModContent.trapOf(level.getBlockState(pos)) != null) {
            return false;   // the engineer takes his field towers along to the next room (28 October 2026)
        }
        int dx = pos.getX() - rec.x;
        int dz = pos.getZ() - rec.z;
        int reach = ExpeditionLayout.length(rec.rooms) + 16;
        if (dx * dx + dz * dz > reach * reach) {
            return false;
        }
        return plan(rec).blocks.containsKey(pos);
    }

    // ------------------------------------------------------------------ the map and the core window

    /** The core window's page "Kundschaft". */
    public CoreStats.ScoutLine scoutLine(ServerPlayer viewer) {
        SiegeSave.ExpeditionRecord rec = record();
        CoreRecord core = m.mainCore();
        if (rec == null || core == null) {
            return null;
        }
        ExpeditionKind kind = kind(rec);
        CoreStats.ScoutLine line = new CoreStats.ScoutLine();
        line.kind = kind.id();
        line.fragment = kind.fragment().id();
        for (String mob : new LinkedHashSet<>(kind.mobs())) {
            line.mobs.add(mob);
        }
        line.coreX = core.x;
        line.coreZ = core.z;
        line.targetX = rec.x;
        line.targetZ = rec.z;
        line.built = rec.built && rec.complete;
        line.won = rec.won;
        line.rooms = rec.rooms;
        line.cleared = rec.cleared;
        line.championLife = ExpeditionKind.championLife(m.currentLevel());
        line.points = m.config().expeditionSkillPoints;
        line.fighters = fighters.size();
        line.sameWorld = SiegeManager.dimensionName(viewer.level()).equals(rec.dimension);
        line.viewerX = (int) Math.floor(viewer.getX());
        line.viewerZ = (int) Math.floor(viewer.getZ());
        line.map = rec.map == null ? "" : rec.map;
        line.mapSize = rec.mapSize;
        line.mapCell = rec.mapCell;
        line.mapX = rec.mapX;
        line.mapZ = rec.mapZ;
        return line;
    }

    /** The button "Karte mitnehmen": a map with a cross on the dungeon. */
    public void giveMap(ServerPlayer player) {
        SiegeSave.ExpeditionRecord rec = record();
        MinecraftServer server = player.level().getServer();
        if (rec == null || server == null) {
            player.sendSystemMessage(Component.translatable("nightsiege.expedition.none").withStyle(ChatFormatting.GRAY));
            return;
        }
        long now = server.overworld().getGameTime();
        Long last = mapTaken.get(player.getUUID());
        if (last != null && now - last < 100) {
            return;   // one click, one map
        }
        mapTaken.put(player.getUUID(), now);
        ServerLevel level = SiegeManager.levelByName(server, rec.dimension);
        if (level == null) {
            return;
        }
        ItemStack map = scoutMap(level, rec);
        if (!player.getInventory().add(map)) {
            player.drop(map, false);
        }
        player.sendSystemMessage(Component.translatable("nightsiege.expedition.map_taken").withStyle(ChatFormatting.GOLD));
        level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1f);
    }

    static ItemStack scoutMap(ServerLevel level, SiegeSave.ExpeditionRecord rec) {
        BlockPos target = new BlockPos(rec.x, level.getSeaLevel(), rec.z);
        ItemStack map = MapItem.create(level, rec.x, rec.z, (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, target, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("nightsiege.expedition.map_name",
                Component.translatable(kind(rec).translationKey())));
        CustomData.update(DataComponents.CUSTOM_DATA, map, tag -> tag.putInt(MAP_TAG, rec.id));
        return map;
    }

    private static int mapId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        CompoundTag tag = data.copyTag();
        return tag.getIntOr(MAP_TAG, 0);
    }

    /** With the scouts' map in hand: how far and which way (action bar). */
    private void mapHints(MinecraftServer server, SiegeSave.ExpeditionRecord rec) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int id = mapId(player.getMainHandItem());
            if (id == 0) {
                id = mapId(player.getOffhandItem());
            }
            if (id == 0) {
                continue;
            }
            Component text;
            if (rec == null || rec.id != id) {
                text = Component.translatable("nightsiege.expedition.map_old").withStyle(ChatFormatting.GRAY);
            } else if (!SiegeManager.dimensionName(player.level()).equals(rec.dimension)) {
                text = Component.translatable("nightsiege.expedition.map_world").withStyle(ChatFormatting.GRAY);
            } else {
                double dx = rec.x + 0.5 - player.getX();
                double dz = rec.z + 0.5 - player.getZ();
                int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                Component name = Component.translatable(kind(rec).translationKey()).withStyle(ChatFormatting.GOLD);
                if (rec.won) {
                    text = Component.translatable("nightsiege.expedition.map_ruin", name, distance);
                } else if (distance < 12) {
                    text = Component.translatable("nightsiege.expedition.map_here", name);
                } else {
                    text = Component.translatable("nightsiege.expedition.map_hint", name, distance, direction(dx, dz));
                }
            }
            player.connection.send(new ClientboundSetActionBarTextPacket(text));
        }
    }

    // ------------------------------------------------------------------ admin and tests

    /** Lost attempts since the start (the playtest compares it). */
    public int lostAttempts() {
        return lostAttempts;
    }

    /**
     * The playtest's dungeon goes when its part is over: fight ended, monsters gone, the gatehouse
     * taken down even when it was won (no ruins of playtests), the record forgotten.
     */
    public boolean removeAll(MinecraftServer server) {
        noRetry = false;
        out.clear();
        return remove(server);
    }

    /**
     * Admin and the playtest: the dungeon goes (the fight ends, everyone inside is put out, its
     * ground comes back over the next seconds; update 30.10.: before, all of it in one tick). The
     * scouts' search, if one runs, is called off too.
     */
    public boolean remove(MinecraftServer server) {
        boolean searching = scouting != null || prepping;
        scouting = null;
        prepping = false;
        prepFailed = false;
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null) {
            cancelJob(server);
            return searching;
        }
        endAll(server, null);
        cancelJob(server);
        if (rec.built && rec.layout >= LAYOUT) {
            detach(server, rec);   // its ground comes back in the background
        }
        m.save().expedition = null;
        plan = null;
        m.persist();
        return true;
    }

    /**
     * Admin and the playtest: the reported dungeon is built at its spot without waiting for a
     * player to come near (update 30.10.: over the next seconds, its chunks loaded in the
     * background). Its door once it stands complete (or a won one), else null: see {@link #building}.
     */
    public BlockPos buildNow(MinecraftServer server) {
        return buildNow(server, null);
    }

    /** As above; {@code asker} is told when no safe place is found (survival safety 31.10.: its place is checked). */
    public BlockPos buildNow(MinecraftServer server, UUID asker) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null || rec.won && !rec.built) {
            return null;
        }
        if (rec.built && (rec.complete || rec.won)) {
            return plan(rec).door;   // a won one is not built again
        }
        if (job == null) {
            Build b = new Build(rec.id, rec.dimension);
            b.stage = rec.built ? Build.FAR : Build.SITE;
            b.asker = asker;
            job = b;
        }
        return null;
    }

    /** The scouts are out or a dungeon is being built now. */
    public boolean building() {
        return scouting != null || job != null;
    }

    /**
     * The playtest (update 30.10.): its dungeon is searched and built during the pause before it,
     * so the players are taken to a finished one and the server does not stand still meanwhile.
     */
    public void prepare(MinecraftServer server) {
        removeAll(server);
        boolean out = scout(server, null, true, null);
        prepping = out;
        prepFailed = !out;
    }

    /** The playtest's dungeon: "ready", "failed", "building" (searched or built still) or "" (none prepared). */
    public String preparation(MinecraftServer server) {
        if (!prepping) {
            return prepFailed ? "failed" : "";
        }
        if (scouting != null) {
            return "building";
        }
        SiegeSave.ExpeditionRecord rec = record();
        if (rec == null) {
            prepping = false;
            prepFailed = true;
            return "failed";
        }
        if (rec.built && rec.complete) {
            return "ready";
        }
        if (job == null) {
            buildNow(server);
        }
        return "building";
    }

    /** The playtest takes its prepared dungeon. */
    public void takePrepared() {
        prepping = false;
        prepFailed = false;
    }

    /** Tests: a dungeon of this kind at a given spot, built at once (no checks). */
    public SiegeSave.ExpeditionRecord testBuild(ServerLevel level, BlockPos origin, int facing, ExpeditionKind kind, int rooms) {
        MinecraftServer server = level.getServer();
        if (record() != null) {
            endAll(server, null);
        }
        scouting = null;
        prepping = false;
        cancelJob(server);
        flushTeardowns(server);   // a dungeon still going comes back first (it could lie where this one goes)
        SiegeSave.ExpeditionRecord old = record();
        if (old != null) {
            ExpeditionSnapshot.load(server, old.id).delete();   // the tests leave the old ones standing
        }
        SiegeSave.ExpeditionRecord rec = newRecord(level, origin, facing, kind, rooms);
        m.save().expedition = rec;
        plan = null;
        beginBuild(level, rec, origin, null, null);
        finishJobNow(level);
        return rec;
    }

    /**
     * Admin ("dungeon hier"): a dungeon of this kind at a given spot, no checks, built over the
     * next seconds (update 30.10.: before, in one tick). The one of before goes.
     */
    public SiegeSave.ExpeditionRecord buildHere(ServerLevel level, BlockPos origin, int facing, ExpeditionKind kind, int rooms) {
        MinecraftServer server = level.getServer();
        remove(server);
        SiegeSave.ExpeditionRecord rec = newRecord(level, origin, facing, kind, rooms);
        m.save().expedition = rec;
        plan = null;
        Build b = new Build(rec.id, rec.dimension);
        b.stage = Build.SITE;   // its spot is given: only the plan is worked out (in the background) first
        b.anywhere = true;   // where the admin stands, no site check (it still sets no block over a chest or spawner)
        b.planAt = groundOrigin(level, origin);
        b.planning = workOut(b.planAt, facing, kind, rooms, rec.id);
        job = b;
        return rec;
    }

    private SiegeSave.ExpeditionRecord newRecord(ServerLevel level, BlockPos origin, int facing, ExpeditionKind kind, int rooms) {
        SiegeSave.ExpeditionRecord rec = new SiegeSave.ExpeditionRecord();
        rec.id = random.nextInt(1_000_000) + 1;
        rec.kind = kind.id();
        rec.layout = LAYOUT;
        rec.dimension = SiegeManager.dimensionName(level);
        rec.x = origin.getX();
        rec.y = origin.getY();
        rec.z = origin.getZ();
        rec.facing = facing;
        rec.rooms = rooms;
        rec.reportedDay = m.dayNow(level.getServer());
        CoreRecord core = m.mainCore();
        if (core != null) {
            drawMap(level, rec, core);
        }
        return rec;
    }

    /** Tests: the plan of the dungeon of now. */
    public ExpeditionLayout.Plan testPlan() {
        SiegeSave.ExpeditionRecord rec = record();
        return rec == null || !rec.built ? null : plan(rec);
    }

    /** Tests: the player goes down now. */
    public void testJoin(ServerPlayer player) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec != null && rec.built && rec.complete && player.level() instanceof ServerLevel level
                && !fighters.contains(player.getUUID())) {
            join(level, rec, player, level.getGameTime());
        }
    }

    /** Tests: one step of the fight now (as every half second). */
    public void testTick(MinecraftServer server) {
        SiegeSave.ExpeditionRecord rec = record();
        ServerLevel level = rec == null ? null : SiegeManager.levelByName(server, rec.dimension);
        if (level != null && rec.built && !rec.won) {
            tickFight(server, level, rec, level.getGameTime());
        }
    }

    /** Tests: the monsters standing now (rooms, waves, champion). */
    public List<Mob> testMobs(ServerLevel level) {
        List<Mob> out = new ArrayList<>();
        for (UUID id : allMobs()) {
            if (level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                out.add(mob);
            }
        }
        return out;
    }

    /** Tests: a short state line. */
    public String testState() {
        SiegeSave.ExpeditionRecord rec = record();
        return rec == null ? "keiner" + (teardowns.isEmpty() ? "" : " (Abbau läuft: " + teardowns.size() + ")")
                : String.format(Locale.ROOT, "%s räume %d/%d arena %s welle %d champion %s gewonnen %s kämpfer %d abbau %s chunks %d fertig %s",
                rec.kind, rec.cleared, rec.rooms, arenaClosed, wave, champion != null, rec.won, fighters.size(), rec.restoring,
                rec.chunksDone.size(), rec.complete);
    }

    /** Tests: dungeons whose ground is still coming back (not those only waiting for the world save). */
    public int teardownsLeft() {
        Set<Integer> ids = new HashSet<>(teardowns.keySet());
        if (m.save().expeditionTeardowns != null) {
            for (SiegeSave.ExpeditionRecord rec : m.save().expeditionTeardowns) {
                ids.add(rec.id);
            }
        }
        ids.removeAll(unsavedTeardowns.keySet());
        return ids.size();
    }

    /** Tests: teardowns done whose snapshot and record wait for the next world save (31.10.). */
    public int teardownsUnsaved() {
        return unsavedTeardowns.size();
    }

    // Tests of the survival safety (Simon 31.10., scene "ss2-dungeon").

    /** Tests: the fight ends as at the horn ({@code horn}) or as when the server stops. */
    public void testEndAll(MinecraftServer server, boolean horn) {
        endAll(server, horn ? "nightsiege.expedition.night" : null);
    }

    /** Tests: the fighter falls (as when his life runs out in the fight). */
    public void testFall(ServerPlayer player) {
        if (fighters.contains(player.getUUID())) {
            fallOut(player.level().getServer(), player);
        }
    }

    /** Tests: the champion falls now (the dungeon is won; it collapses after its 30 s). */
    public void testWin(MinecraftServer server) {
        SiegeSave.ExpeditionRecord rec = record();
        ServerLevel level = rec == null ? null : SiegeManager.levelByName(server, rec.dimension);
        if (level != null && rec.built && !rec.won) {
            win(server, level, rec);
        }
    }

    /** Tests: the 30 s to get out are over (it collapses with the next tick). */
    public void testCollapseNow(MinecraftServer server) {
        SiegeSave.ExpeditionRecord rec = record();
        if (rec != null && rec.won) {
            rec.collapseAt = server.overworld().getGameTime();
        }
    }

    /** Tests: one tick of the dungeons now (the collapse, the teardown's first step). */
    public void testTickAll(MinecraftServer server) {
        tick(server);
    }

    /** Tests: the player logs in now, his chunk loaded (as SafeGround looks at a login). */
    public void testArrive(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            SafeGround.arrive(m, level, player);
        }
    }

    /** Tests: does the entity stand in a block? */
    public static boolean testStuck(Entity e) {
        return e.level() instanceof ServerLevel level && SafeGround.stuck(level, e);
    }

    /** Tests: the places remembered and how many were put on top. */
    public String testSafeGround() {
        return SafeGround.testState(m.save());
    }

    /** Tests: in the sealed part of the dungeon of now? */
    public boolean testSealed(Entity e) {
        SiegeSave.ExpeditionRecord rec = record();
        return rec != null && rec.built && sealed(plan(rec), rec, e);
    }
}
